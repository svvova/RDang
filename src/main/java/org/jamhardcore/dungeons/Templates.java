package org.jamhardcore.dungeons;

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.loot.Lootable;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public final class Templates {
    private final JavaPlugin p;
    private final Configs c;
    private final Path root;
    private StructureBackend backend;

    private static final class CachedSchematic {
        final SchematicCodec.Image image;
        final long lastModified;
        final long fileSize;

        CachedSchematic(SchematicCodec.Image image, long lastModified, long fileSize) {
            this.image = image;
            this.lastModified = lastModified;
            this.fileSize = fileSize;
        }
    }

    private final Map<Path, CachedSchematic> schematicCache = new ConcurrentHashMap<>();

    public Templates(JavaPlugin p, Configs c) throws Exception {
        this(p, c, null);
    }

    Templates(JavaPlugin p, Configs c, StructureBackend backend) throws Exception {
        this.p = p;
        this.c = c;
        this.backend = backend;
        this.root = p.getDataFolder().toPath();
        Files.createDirectories(this.root.resolve("templates"), new FileAttribute[0]);
        Files.createDirectories(this.root.resolve("backups"), new FileAttribute[0]);
        Files.createDirectories(this.root.resolve("schematics"), new FileAttribute[0]);
    }

    public void invalidateCache(String name) {
        if (name == null) {
            this.schematicCache.clear();
            return;
        }
        this.schematicCache.keySet().removeIf(path -> path.getFileName().toString().contains(name));
    }

    private Path template(Model.Arena a) {
        Path modern = this.root.resolve("templates/" + a.id + ".schem");
        return Files.isRegularFile(modern, new LinkOption[0]) ? modern : this.root.resolve("templates/" + a.id + ".nbt");
    }

    private Plugin worldEditPlugin() {
        for (String name : List.of("FastAsyncWorldEdit", "WorldEdit")) {
            Plugin installed = Bukkit.getPluginManager().getPlugin(name);
            if (installed == null || !installed.isEnabled()) continue;
            return installed;
        }
        return null;
    }

    String status() {
        Plugin installed = this.worldEditPlugin();
        return installed == null ? "Схематики: нужен включённый WorldEdit 7 / FastAsyncWorldEdit; вставка недоступна" : "Схематики: WorldEdit API — " + installed.getName() + " " + installed.getDescription().getVersion();
    }

    private StructureBackend backend() {
        if (this.backend != null) {
            return this.backend;
        }
        if (this.worldEditPlugin() == null) {
            throw new IllegalArgumentException("Для вставки и сохранения схем нужен включённый WorldEdit 7 / FastAsyncWorldEdit. Проверьте /plugins и /jd status, затем полностью перезапустите сервер");
        }
        this.backend = new WorldEditStructures(this.c);
        return this.backend;
    }

    private void blocks(BlockOperation operation) throws Exception {
        try {
            operation.run(this.backend());
        }
        catch (LinkageError error) {
            throw new IllegalStateException("WorldEdit API несовместим с установленной сборкой. Нужен WorldEdit/FAWE для версии сервера", error);
        }
    }

    public void check(Model.Box box) {
        World w = Bukkit.getWorld(box.world());
        if (w == null) {
            throw new IllegalArgumentException("Мир не загружен: " + box.world());
        }
        if (box.volume() > (long)this.c.limit("region-volume")) {
            throw new IllegalArgumentException("Область " + (box.x2() - box.x1() + 1) + "×" + (box.y2() - box.y1() + 1) + "×" + (box.z2() - box.z1() + 1) + " = " + box.volume() + " блоков; limits.region-volume=" + this.c.limit("region-volume"));
        }
        if (box.y1() < w.getMinHeight() || box.y2() >= w.getMaxHeight()) {
            throw new IllegalArgumentException("Регион вне высоты мира");
        }
        if ((long)((box.x2() >> 4) - (box.x1() >> 4) + 1) * (long)((box.z2() >> 4) - (box.z1() >> 4) + 1) > (long)this.c.limit("snapshot-chunks")) {
            throw new IllegalArgumentException("Область превышает limits.snapshot-chunks=" + this.c.limit("snapshot-chunks"));
        }
    }

    public void tickets(Model.Box b, boolean add) {
        if (b == null) return;
        World w = Bukkit.getWorld(b.world());
        if (w == null) return;
        for (int x = b.x1() >> 4; x <= b.x2() >> 4; ++x) {
            for (int z = b.z1() >> 4; z <= b.z2() >> 4; ++z) {
                if (add) {
                    w.addPluginChunkTicket(x, z, this.p);
                } else {
                    w.removePluginChunkTicket(x, z, this.p);
                }
            }
        }
    }

    public void tickets(Model.Session s, boolean add) {
        if (s != null && s.arena != null) {
            this.tickets(s.arena.box(), add);
        }
    }

    /**
     * Предварительная асинхронная загрузка и удержание чанков Paper перед операциями с блоками.
     * Предотвращает синхронные дисковые зависания и падение TPS до 1-2.
     */
    public CompletableFuture<Void> prepareChunksAsync(Model.Box box) {
        if (box == null) {
            return CompletableFuture.completedFuture(null);
        }
        World world = Bukkit.getWorld(box.world());
        if (world == null) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Мир не загружен: " + box.world()));
        }
        this.tickets(box, true);

        int minCx = box.x1() >> 4;
        int maxCx = box.x2() >> 4;
        int minCz = box.z1() >> 4;
        int maxCz = box.z2() >> 4;

        List<CompletableFuture<Chunk>> futures = new ArrayList<>();
        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
                futures.add(world.getChunkAtAsync(cx, cz, true));
            }
        }
        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
    }

    public void capture(Model.Box box, Path target) throws Exception {
        this.check(box);
        this.blocks(io -> io.capture(box, target));
    }

    public CompletableFuture<Void> captureAsync(Model.Box box, Path target) {
        this.check(box);
        return this.prepareChunksAsync(box)
                .thenCompose(v -> this.backend().captureAsync(box, target))
                .whenComplete((v, err) -> {
                    Bukkit.getScheduler().runTask(this.p, () -> {
                        this.tickets(box, false);
                    });
                });
    }

    public void paste(SchematicCodec.Image image, Model.Box box) throws Exception {
        this.paste(image, box, true);
    }

    public void paste(SchematicCodec.Image image, Model.Box box, boolean pasteAir) throws Exception {
        this.pasteAsync(image, box, pasteAir).join();
    }

    public CompletableFuture<Void> pasteAsync(SchematicCodec.Image image, Model.Box box) {
        return this.pasteAsync(image, box, true);
    }

    public CompletableFuture<Void> pasteAsync(SchematicCodec.Image image, Model.Box box, boolean pasteAir) {
        this.check(box);
        return this.prepareChunksAsync(box)
                .thenCompose(v -> this.backend().pasteAsync(image, box, pasteAir))
                .whenComplete((v, err) -> {
                    Bukkit.getScheduler().runTask(this.p, () -> {
                        this.tickets(box, false);
                    });
                });
    }

    public void paste(Path file, Model.Box box) throws Exception {
        this.paste(file, box, true);
    }

    public void paste(Path file, Model.Box box, boolean pasteAir) throws Exception {
        this.pasteAsync(file, box, pasteAir).join();
    }

    public CompletableFuture<Void> pasteAsync(Path file, Model.Box box) {
        return this.pasteAsync(file, box, true);
    }

    public CompletableFuture<Void> pasteAsync(Path file, Model.Box box, boolean pasteAir) {
        this.check(box);
        return this.prepareChunksAsync(box)
                .thenCompose(v -> this.backend().pasteAsync(file, box, pasteAir))
                .whenComplete((v, err) -> {
                    Bukkit.getScheduler().runTask(this.p, () -> {
                        this.tickets(box, false);
                    });
                });
    }

    public void capture(Model.Arena a) throws Exception {
        this.check(a.box());
        this.blocks(io -> io.capture(a.box(), this.root.resolve("templates/" + a.id + ".schem")));
        a.template = true;
        a.schematic = null;
        this.invalidateCache(a.id);
        this.c.saveArena(a);
    }

    public CompletableFuture<Void> captureAsync(Model.Arena a) {
        this.check(a.box());
        Path target = this.root.resolve("templates/" + a.id + ".schem");
        return this.prepareChunksAsync(a.box())
                .thenCompose(v -> this.backend().captureAsync(a.box(), target))
                .whenComplete((v, err) -> {
                    Bukkit.getScheduler().runTask(this.p, () -> {
                        this.tickets(a.box(), false);
                        if (err == null) {
                            a.template = true;
                            a.schematic = null;
                            this.invalidateCache(a.id);
                            try {
                                this.c.saveArena(a);
                            } catch (Exception ex) {
                                this.p.getLogger().warning("Не удалось сохранить арену " + a.id + ": " + ex.getMessage());
                            }
                        }
                    });
                });
    }

    public void backup(Model.Session s) throws Exception {
        this.backupAsync(s).join();
    }

    public CompletableFuture<Void> backupAsync(Model.Session s) {
        this.check(s.arena.box());
        String path = "backups/" + s.id + ".schem";
        s.backup = path;
        return this.prepareChunksAsync(s.arena.box())
                .thenCompose(v -> this.backend().captureAsync(s.arena.box(), this.root.resolve(path)));
    }

    public void place(Model.Session s) throws Exception {
        this.placeAsync(s).join();
    }

    public CompletableFuture<Void> clearRewardChestAsync(Model.Arena arena) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        Bukkit.getScheduler().runTask(this.p, () -> {
            try {
                clearRewardChest(arena);
            } catch (Throwable t) {
                this.p.getLogger().warning("Предупреждение при очистке сундука: " + t.getMessage());
            } finally {
                future.complete(null);
            }
        });
        return future;
    }

    public CompletableFuture<Void> placeAsync(Model.Session s) {
        CompletableFuture<Void> pasteFuture;
        if (s.arena.schematic != null) {
            pasteFuture = CompletableFuture.supplyAsync(() -> {
                try {
                    return this.load(s.arena.schematic);
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
            }).thenCompose(image -> this.pasteAsync(s.arena, image));
        } else {
            Path path = this.template(s.arena);
            if (!Files.isRegularFile(path, new LinkOption[0])) {
                CompletableFuture<Void> failed = new CompletableFuture<>();
                failed.completeExceptionally(new IllegalArgumentException("Шаблон не записан: /jd template save " + s.arenaId));
                return failed;
            }
            Model.Box b = s.arena.box();
            this.check(b);
            pasteFuture = this.pasteAsync(path, b, s.arena.pasteAir);
        }
        return pasteFuture.thenCompose(v -> this.clearRewardChestAsync(s.arena));
    }

    public static void clearRewardChest(Model.Arena arena) {
        if (arena == null || arena.chest == null || arena.chest.location() == null) {
            return;
        }
        org.bukkit.Location loc = arena.chest.location();
        if (loc.getWorld() == null) {
            return;
        }
        if (!loc.getChunk().isLoaded()) {
            loc.getChunk().load(true);
        }
        BlockState state = loc.getBlock().getState();
        if (state instanceof Lootable lootable) {
            lootable.setLootTable(null);
        }
        if (state instanceof Container container) {
            container.getInventory().clear();
            state.update(true, false);
        }
    }

    public List<String> schematics() throws Exception {
        try (Stream<Path> paths = Files.list(this.root.resolve("schematics"))) {
            return paths.filter(x$0 -> Files.isRegularFile(x$0, new LinkOption[0]))
                    .map(f -> f.getFileName().toString())
                    .filter(n -> n.matches("(?i).+\\.(litematic|schem|schematic|nbt)"))
                    .sorted()
                    .toList();
        }
    }

    public Path root() {
        return this.root;
    }

    /**
     * Оптимизированная загрузка схематик с кэшированием в памяти.
     * Если файл не изменился на диске, возвращается закэшированное изображение без парсинга NBT.
     */
    public SchematicCodec.Image load(String name) throws Exception {
        Path dir = this.root.resolve("schematics").toRealPath(new LinkOption[0]);
        Path file = dir.resolve(name).normalize();
        if (!Files.isRegularFile(file, new LinkOption[0])) {
            for (String ext : List.of(".schem", ".schematic", ".litematic", ".nbt")) {
                Path candidate = dir.resolve(name + ext).normalize();
                if (Files.isRegularFile(candidate, new LinkOption[0])) {
                    file = candidate;
                    name = name + ext;
                    break;
                }
            }
        }
        if (!(file.startsWith(dir) && Files.isRegularFile(file, new LinkOption[0]) && file.toRealPath(new LinkOption[0]).startsWith(dir))) {
            throw new IllegalArgumentException("Нет файла в папке schematics: " + name);
        }
        if (!name.matches("(?i).+\\.(litematic|schem|schematic|nbt)")) {
            throw new IllegalArgumentException("Формат: .litematic, .schem, .schematic или .nbt");
        }

        long lastMod = Files.getLastModifiedTime(file).toMillis();
        long fileSize = Files.size(file);
        CachedSchematic cached = this.schematicCache.get(file);
        if (cached != null && cached.lastModified == lastMod && cached.fileSize == fileSize) {
            return cached.image;
        }

        SchematicCodec.Image loaded;
        try {
            loaded = SchematicCodec.read(file, this.c.main().getLong("schematics.max-file-bytes"), this.c.main().getLong("schematics.max-inflated-bytes"), this.c.limit("region-volume"));
        } catch (IllegalArgumentException ex) {
            if (!ex.getMessage().startsWith("LEGACY_SCHEMATIC")) {
                throw ex;
            }
            loaded = LegacySchematics.read(file, this.c.main().getLong("schematics.max-inflated-bytes"), this.c.limit("region-volume"));
        }

        this.schematicCache.put(file, new CachedSchematic(loaded, lastMod, fileSize));
        return loaded;
    }

    public Model.Arena bind(Model.Arena arena, String file, Model.Point origin) throws Exception {
        SchematicCodec.Image image = this.load(file);
        if (arena.pos1 != null && arena.pos2 != null) {
            Model.Point before = arena.box().origin();
            double dx = origin.x() - before.x();
            double dy = origin.y() - before.y();
            double dz = origin.z() - before.z();
            for (Model.Spawn point : arena.points) {
                point.point = point.point.shift(dx, dy, dz, origin.world());
            }
        }
        arena.schematic = file;
        arena.template = true;
        arena.pos1 = origin;
        arena.pos2 = origin.shift(image.width() - 1, image.height() - 1, image.length() - 1, origin.world());
        arena.chest = image.chest() == null ? null : origin.shift(image.chest()[0], image.chest()[1], image.chest()[2], origin.world());
        this.check(arena.box());
        return arena;
    }

    public void paste(Model.Arena arena) throws Exception {
        if (arena.schematic == null) {
            throw new IllegalArgumentException("Сначала выберите файл схематики");
        }
        this.paste(arena, this.load(arena.schematic));
    }

    public Model.Point findChestInTemplate(Model.Arena arena) {
        if (arena == null || arena.pos1 == null || arena.pos2 == null) {
            return null;
        }
        try {
            Path path = this.template(arena);
            if (Files.isRegularFile(path)) {
                SchematicCodec.Image img = SchematicCodec.read(path, this.c.main().getLong("schematics.max-file-bytes"), this.c.main().getLong("schematics.max-inflated-bytes"), this.c.limit("region-volume"));
                if (img != null && img.chest() != null) {
                    Model.Point o = arena.box().origin();
                    return o.shift(img.chest()[0], img.chest()[1], img.chest()[2], o.world());
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    SchematicCodec.Image fit(Model.Arena arena) throws Exception {
        Model.Point origin;
        if (arena.schematic == null) {
            throw new IllegalArgumentException("Сначала выберите файл схематики");
        }
        SchematicCodec.Image image = this.load(arena.schematic);
        arena.pos1 = origin = arena.box().origin();
        arena.pos2 = origin.shift(image.width() - 1, image.height() - 1, image.length() - 1, origin.world());
        this.check(arena.box());
        if (arena.chest == null || !arena.box().contains(arena.chest)) {
            arena.chest = image.chest() == null ? null : origin.shift(image.chest()[0], image.chest()[1], image.chest()[2], origin.world());
        }
        if (arena.exit != null && arena.playBox().contains(arena.exit)) {
            arena.exit = null;
        }
        return image;
    }

    void paste(Model.Arena arena, SchematicCodec.Image image) throws Exception {
        this.pasteAsync(arena, image).join();
    }

    public CompletableFuture<Void> pasteAsync(Model.Arena arena, SchematicCodec.Image image) {
        this.check(arena.box());
        Model.Box b = arena.box();
        if (image.width() != b.x2() - b.x1() + 1 || image.height() != b.y2() - b.y1() + 1 || image.length() != b.z2() - b.z1() + 1) {
            CompletableFuture<Void> failed = new CompletableFuture<>();
            failed.completeExceptionally(new IllegalArgumentException("Размер файла изменился после размещения. Остановите событие и выполните /jd schematic fit " + arena.id));
            return failed;
        }
        return this.prepareChunksAsync(b)
                .thenCompose(v -> this.backend().pasteAsync(image, b, arena.pasteAir))
                .whenComplete((v, err) -> {
                    Bukkit.getScheduler().runTask(this.p, () -> {
                        this.tickets(b, false);
                    });
                });
    }

    public void restore(Model.Session s) throws Exception {
        this.restoreAsync(s).join();
    }

    public CompletableFuture<Void> restoreAsync(Model.Session s) {
        if (s.backup == null) {
            return CompletableFuture.completedFuture(null);
        }
        Path f = this.root.resolve(s.backup).normalize();
        if (!f.startsWith(this.root.resolve("backups")) || !Files.isRegularFile(f, new LinkOption[0])) {
            CompletableFuture<Void> failed = new CompletableFuture<>();
            failed.completeExceptionally(new IllegalStateException("Нет резервного снимка " + s.backup));
            return failed;
        }
        this.check(s.arena.box());
        // Восстановление бэкапа должно полностью вернуть исходное состояние мира (включая воздух)
        return this.prepareChunksAsync(s.arena.box())
                .thenCompose(v -> this.backend().pasteAsync(f, s.arena.box(), true))
                .whenComplete((v, err) -> {
                    Bukkit.getScheduler().runTask(this.p, () -> {
                        this.tickets(s.arena.box(), false);
                    });
                });
    }

    public void clearArea(Model.Box box) throws Exception {
        this.check(box);
        this.backend().clear(box);
    }

    public CompletableFuture<Void> clearAreaAsync(Model.Box box) {
        this.check(box);
        return this.prepareChunksAsync(box)
                .thenCompose(v -> this.backend().clearAsync(box))
                .whenComplete((v, err) -> {
                    Bukkit.getScheduler().runTask(this.p, () -> {
                        this.tickets(box, false);
                    });
                });
    }

    public void discardBackup(Model.Session s) throws Exception {
        if (s.backup != null) {
            Files.deleteIfExists(this.root.resolve(s.backup));
        }
    }

    @FunctionalInterface
    private static interface BlockOperation {
        void run(StructureBackend var1) throws Exception;
    }
}
