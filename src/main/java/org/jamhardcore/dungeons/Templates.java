/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.Bukkit
 *  org.bukkit.World
 *  org.bukkit.block.BlockState
 *  org.bukkit.block.Container
 *  org.bukkit.loot.Lootable
 *  org.bukkit.plugin.Plugin
 *  org.bukkit.plugin.java.JavaPlugin
 */
package org.jamhardcore.dungeons;

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.loot.Lootable;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.jamhardcore.dungeons.Configs;
import org.jamhardcore.dungeons.LegacySchematics;
import org.jamhardcore.dungeons.Model;
import org.jamhardcore.dungeons.SchematicCodec;
import org.jamhardcore.dungeons.StructureBackend;
import org.jamhardcore.dungeons.WorldEditStructures;

public final class Templates {
    private final JavaPlugin p;
    private final Configs c;
    private final Path root;
    private StructureBackend backend;

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
        return installed == null ? "\u0421\u0445\u0435\u043c\u0430\u0442\u0438\u043a\u0438: \u043d\u0443\u0436\u0435\u043d \u0432\u043a\u043b\u044e\u0447\u0451\u043d\u043d\u044b\u0439 WorldEdit 7 / FastAsyncWorldEdit; \u0432\u0441\u0442\u0430\u0432\u043a\u0430 \u043d\u0435\u0434\u043e\u0441\u0442\u0443\u043f\u043d\u0430" : "\u0421\u0445\u0435\u043c\u0430\u0442\u0438\u043a\u0438: WorldEdit API \u2014 " + installed.getName() + " " + installed.getDescription().getVersion();
    }

    private StructureBackend backend() {
        if (this.backend != null) {
            return this.backend;
        }
        if (this.worldEditPlugin() == null) {
            throw new IllegalArgumentException("\u0414\u043b\u044f \u0432\u0441\u0442\u0430\u0432\u043a\u0438 \u0438 \u0441\u043e\u0445\u0440\u0430\u043d\u0435\u043d\u0438\u044f \u0441\u0445\u0435\u043c \u043d\u0443\u0436\u0435\u043d \u0432\u043a\u043b\u044e\u0447\u0451\u043d\u043d\u044b\u0439 WorldEdit 7 / FastAsyncWorldEdit. \u041f\u0440\u043e\u0432\u0435\u0440\u044c\u0442\u0435 /plugins \u0438 /jd status, \u0437\u0430\u0442\u0435\u043c \u043f\u043e\u043b\u043d\u043e\u0441\u0442\u044c\u044e \u043f\u0435\u0440\u0435\u0437\u0430\u043f\u0443\u0441\u0442\u0438\u0442\u0435 \u0441\u0435\u0440\u0432\u0435\u0440");
        }
        this.backend = new WorldEditStructures(this.c);
        return this.backend;
    }

    private void blocks(BlockOperation operation) throws Exception {
        try {
            operation.run(this.backend());
        }
        catch (LinkageError error) {
            throw new IllegalStateException("WorldEdit API \u043d\u0435\u0441\u043e\u0432\u043c\u0435\u0441\u0442\u0438\u043c \u0441 \u0443\u0441\u0442\u0430\u043d\u043e\u0432\u043b\u0435\u043d\u043d\u043e\u0439 \u0441\u0431\u043e\u0440\u043a\u043e\u0439. \u041d\u0443\u0436\u0435\u043d WorldEdit/FAWE \u0434\u043b\u044f \u0432\u0435\u0440\u0441\u0438\u0438 \u0441\u0435\u0440\u0432\u0435\u0440\u0430", error);
        }
    }

    public void check(Model.Box box) {
        World w = Bukkit.getWorld((String)box.world());
        if (w == null) {
            throw new IllegalArgumentException("\u041c\u0438\u0440 \u043d\u0435 \u0437\u0430\u0433\u0440\u0443\u0436\u0435\u043d: " + box.world());
        }
        if (box.volume() > (long)this.c.limit("region-volume")) {
            throw new IllegalArgumentException("\u041e\u0431\u043b\u0430\u0441\u0442\u044c " + (box.x2() - box.x1() + 1) + "\u00d7" + (box.y2() - box.y1() + 1) + "\u00d7" + (box.z2() - box.z1() + 1) + " = " + box.volume() + " \u0431\u043b\u043e\u043a\u043e\u0432; limits.region-volume=" + this.c.limit("region-volume"));
        }
        if (box.y1() < w.getMinHeight() || box.y2() >= w.getMaxHeight()) {
            throw new IllegalArgumentException("\u0420\u0435\u0433\u0438\u043e\u043d \u0432\u043d\u0435 \u0432\u044b\u0441\u043e\u0442\u044b \u043c\u0438\u0440\u0430");
        }
        if ((long)((box.x2() >> 4) - (box.x1() >> 4) + 1) * (long)((box.z2() >> 4) - (box.z1() >> 4) + 1) > (long)this.c.limit("snapshot-chunks")) {
            throw new IllegalArgumentException("\u041e\u0431\u043b\u0430\u0441\u0442\u044c \u043f\u0440\u0435\u0432\u044b\u0448\u0430\u0435\u0442 limits.snapshot-chunks=" + this.c.limit("snapshot-chunks"));
        }
    }

    public void tickets(Model.Session s, boolean add) {
        World w = Bukkit.getWorld((String)s.arena.box().world());
        if (w == null) {
            return;
        }
        Model.Box b = s.arena.box();
        for (int x = b.x1() >> 4; x <= b.x2() >> 4; ++x) {
            for (int z = b.z1() >> 4; z <= b.z2() >> 4; ++z) {
                if (add) {
                    w.addPluginChunkTicket(x, z, (Plugin)this.p);
                    continue;
                }
                w.removePluginChunkTicket(x, z, (Plugin)this.p);
            }
        }
    }

    public void capture(Model.Arena a) throws Exception {
        this.check(a.box());
        this.blocks(io -> io.capture(a.box(), this.root.resolve("templates/" + a.id + ".schem")));
        a.template = true;
        a.schematic = null;
        this.c.saveArena(a);
    }

    public void backup(Model.Session s) throws Exception {
        this.check(s.arena.box());
        String path = "backups/" + s.id + ".schem";
        this.blocks(io -> io.capture(s.arena.box(), this.root.resolve(path)));
        s.backup = path;
    }

    public void place(Model.Session s) throws Exception {
        if (s.arena.schematic != null) {
            this.paste(s.arena);
            Templates.clearRewardChest(s.arena);
            return;
        }
        Path path = this.template(s.arena);
        if (!Files.isRegularFile(path, new LinkOption[0])) {
            throw new IllegalArgumentException("\u0428\u0430\u0431\u043b\u043e\u043d \u043d\u0435 \u0437\u0430\u043f\u0438\u0441\u0430\u043d: /jd template save " + s.arenaId);
        }
        Model.Box b = s.arena.box();
        this.check(b);
        this.blocks(io -> io.paste(path, b));
        Templates.clearRewardChest(s.arena);
    }

    private static void clearRewardChest(Model.Arena arena) {
        if (arena.chest == null || arena.chest.location() == null) {
            return;
        }
        BlockState state = arena.chest.location().getBlock().getState();
        if (state instanceof Lootable) {
            Lootable lootable = (Lootable)state;
            lootable.setLootTable(null);
        }
        if (state instanceof Container) {
            Container container = (Container)state;
            container.getInventory().clear();
            state.update(true, false);
        }
    }

    public List<String> schematics() throws Exception {
        try (Stream<Path> paths = Files.list(this.root.resolve("schematics"));){
            List<String> list = paths.filter(x$0 -> Files.isRegularFile(x$0, new LinkOption[0])).map(f -> f.getFileName().toString()).filter(n -> n.matches("(?i).+\\.(litematic|schem|schematic|nbt)")).sorted().toList();
            return list;
        }
    }

    SchematicCodec.Image load(String name) throws Exception {
        Path dir = this.root.resolve("schematics").toRealPath(new LinkOption[0]);
        Path file = dir.resolve(name).normalize();
        if (!(file.startsWith(dir) && Files.isRegularFile(file, new LinkOption[0]) && file.toRealPath(new LinkOption[0]).startsWith(dir))) {
            throw new IllegalArgumentException("\u041d\u0435\u0442 \u0444\u0430\u0439\u043b\u0430 \u0432 \u043f\u0430\u043f\u043a\u0435 schematics: " + name);
        }
        if (!name.matches("(?i).+\\.(litematic|schem|schematic|nbt)")) {
            throw new IllegalArgumentException("\u0424\u043e\u0440\u043c\u0430\u0442: .litematic, .schem, .schematic \u0438\u043b\u0438 .nbt");
        }
        try {
            return SchematicCodec.read(file, this.c.main().getLong("schematics.max-file-bytes"), this.c.main().getLong("schematics.max-inflated-bytes"), this.c.limit("region-volume"));
        }
        catch (IllegalArgumentException ex) {
            if (!ex.getMessage().startsWith("LEGACY_SCHEMATIC")) {
                throw ex;
            }
            return LegacySchematics.read(file, this.c.main().getLong("schematics.max-inflated-bytes"), this.c.limit("region-volume"));
        }
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
            throw new IllegalArgumentException("\u0421\u043d\u0430\u0447\u0430\u043b\u0430 \u0432\u044b\u0431\u0435\u0440\u0438\u0442\u0435 \u0444\u0430\u0439\u043b \u0441\u0445\u0435\u043c\u0430\u0442\u0438\u043a\u0438");
        }
        this.paste(arena, this.load(arena.schematic));
    }

    SchematicCodec.Image fit(Model.Arena arena) throws Exception {
        Model.Point origin;
        if (arena.schematic == null) {
            throw new IllegalArgumentException("\u0421\u043d\u0430\u0447\u0430\u043b\u0430 \u0432\u044b\u0431\u0435\u0440\u0438\u0442\u0435 \u0444\u0430\u0439\u043b \u0441\u0445\u0435\u043c\u0430\u0442\u0438\u043a\u0438");
        }
        SchematicCodec.Image image = this.load(arena.schematic);
        arena.pos1 = origin = arena.box().origin();
        arena.pos2 = origin.shift(image.width() - 1, image.height() - 1, image.length() - 1, origin.world());
        this.check(arena.box());
        if (arena.chest == null || !arena.box().contains(arena.chest)) {
            Model.Point point = arena.chest = image.chest() == null ? null : origin.shift(image.chest()[0], image.chest()[1], image.chest()[2], origin.world());
        }
        if (arena.exit != null && arena.playBox().contains(arena.exit)) {
            arena.exit = null;
        }
        return image;
    }

    void paste(Model.Arena arena, SchematicCodec.Image image) throws Exception {
        this.check(arena.box());
        Model.Box b = arena.box();
        if (image.width() != b.x2() - b.x1() + 1 || image.height() != b.y2() - b.y1() + 1 || image.length() != b.z2() - b.z1() + 1) {
            throw new IllegalArgumentException("\u0420\u0430\u0437\u043c\u0435\u0440 \u0444\u0430\u0439\u043b\u0430 \u0438\u0437\u043c\u0435\u043d\u0438\u043b\u0441\u044f \u043f\u043e\u0441\u043b\u0435 \u0440\u0430\u0437\u043c\u0435\u0449\u0435\u043d\u0438\u044f. \u041e\u0441\u0442\u0430\u043d\u043e\u0432\u0438\u0442\u0435 \u0441\u043e\u0431\u044b\u0442\u0438\u0435 \u0438 \u0432\u044b\u043f\u043e\u043b\u043d\u0438\u0442\u0435 /jd schematic fit " + arena.id);
        }
        this.blocks(io -> io.paste(image, b));
    }

    public void restore(Model.Session s) throws Exception {
        if (s.backup == null) {
            return;
        }
        Path f = this.root.resolve(s.backup).normalize();
        if (!f.startsWith(this.root.resolve("backups")) || !Files.isRegularFile(f, new LinkOption[0])) {
            throw new IllegalStateException("\u041d\u0435\u0442 \u0440\u0435\u0437\u0435\u0440\u0432\u043d\u043e\u0433\u043e \u0441\u043d\u0438\u043c\u043a\u0430 " + s.backup);
        }
        this.check(s.arena.box());
        this.blocks(io -> io.paste(f, s.arena.box()));
        Objects.requireNonNull(Bukkit.getWorld((String)s.arena.box().world())).save();
    }

    public void discardBackup(Model.Session s) throws Exception {
        if (s.backup != null) {
            Files.deleteIfExists(this.root.resolve(s.backup));
        }
    }

    @FunctionalInterface
    private static interface BlockOperation {
        public void run(StructureBackend var1) throws Exception;
    }
}

