package org.jamhardcore.dungeons;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extension.platform.Capability;
import com.sk89q.worldedit.extent.Extent;
import com.sk89q.worldedit.extent.clipboard.BlockArrayClipboard;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.BuiltInClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardWriter;
import com.sk89q.worldedit.function.mask.ExistingBlockMask;
import com.sk89q.worldedit.function.operation.ForwardExtentCopy;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.session.ClipboardHolder;
import com.sk89q.worldedit.util.SideEffect;
import com.sk89q.worldedit.util.SideEffectSet;
import com.sk89q.worldedit.world.World;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import org.bukkit.Bukkit;

final class WorldEditStructures implements StructureBackend {
    private final Configs c;
    private final Function<Model.Box, World> worlds;

    WorldEditStructures(Configs configs) {
        this(configs, box -> {
            org.bukkit.World world = Bukkit.getWorld(box.world());
            if (world == null) {
                throw new IllegalArgumentException("Мир не загружен: " + box.world());
            }
            return BukkitAdapter.adapt(world);
        });
    }

    WorldEditStructures(Configs configs, Function<Model.Box, World> worlds) {
        this.c = configs;
        this.worlds = worlds;
    }

    private World world(Model.Box box) {
        return this.worlds.apply(box);
    }

    public static boolean isFawe() {
        try {
            Class.forName("com.fastasyncworldedit.core.FaweAPI");
            return true;
        } catch (ClassNotFoundException e) {
            return Bukkit.getPluginManager().getPlugin("FastAsyncWorldEdit") != null;
        }
    }

    /**
     * Создает EditSession с оптимизациями:
     * - Полное отключение side effects (физика соседей, вода/лава, падение гравия, свет)
     * - Отключение истории изменений ChangeSet
     * - FastAsyncWorldEdit fastMode, отключение watchdog и per-block lighting
     * Это гарантирует максимальную производительность без просадок TPS до 1-2.
     */
    public EditSession createEditSession(World world) {
        var builder = WorldEdit.getInstance().newEditSessionBuilder().world(world).maxBlocks(-1);
        try {
            Method fastMode = builder.getClass().getMethod("fastMode", boolean.class);
            fastMode.invoke(builder, true);
        } catch (Throwable ignored) {}
        try {
            Method changeSetNull = builder.getClass().getMethod("changeSetNull");
            changeSetNull.invoke(builder);
        } catch (Throwable ignored) {}
        try {
            Method allowedRegions = builder.getClass().getMethod("allowedRegionsEverywhere");
            allowedRegions.invoke(builder);
        } catch (Throwable ignored) {}

        EditSession session = builder.build();
        try {
            Method setFastMode = session.getClass().getMethod("setFastMode", boolean.class);
            setFastMode.invoke(session, true);
        } catch (Throwable ignored) {}
        try {
            session.setSideEffectApplier(SideEffectSet.none()
                .with(SideEffect.NETWORK, SideEffect.State.ON)
                .with(SideEffect.UPDATE, SideEffect.State.ON));
        } catch (Throwable ignored) {
            try {
                session.setSideEffectApplier(SideEffectSet.none());
            } catch (Throwable ignored2) {}
        }
        try {
            Method setWatchdog = session.getClass().getMethod("setWatchdog", boolean.class);
            setWatchdog.invoke(session, false);
        } catch (Throwable ignored) {}
        try {
            Method setLighting = session.getClass().getMethod("setLightingMode", boolean.class);
            setLighting.invoke(session, false);
        } catch (Throwable ignored) {}
        return session;
    }

    private void flushEditSession(EditSession edit) {
        try {
            Method flushSession = edit.getClass().getMethod("flushSession");
            flushSession.invoke(edit);
        } catch (Throwable ignored) {
            try {
                Method flushQueue = edit.getClass().getMethod("flushQueue");
                flushQueue.invoke(edit);
            } catch (Throwable ignored2) {}
        }
    }

    private long decodedLimit(SchematicCodec.Image image) {
        return Math.addExact(this.c.main().getLong("schematics.max-inflated-bytes"), Math.multiplyExact((long)image.width() * (long)image.height() * (long)image.length(), 128));
    }

    private Clipboard read(SchematicCodec.Image image) throws Exception {
        if (image.clipboardData() != null) {
            BuiltInClipboardFormat format = BuiltInClipboardFormat.valueOf("SPONGE_V" + image.spongeVersion() + "_SCHEMATIC");
            try (ClipboardReader reader = format.getReader(new ByteArrayInputStream(image.clipboardData()))) {
                return reader.read();
            }
        }
        int dataVersion = WorldEdit.getInstance().getPlatformManager().queryCapability(Capability.WORLD_EDITING).getDataVersion();
        byte[] sponge = SchematicCodec.sponge(image, this.decodedLimit(image), dataVersion);
        try (ClipboardReader reader = BuiltInClipboardFormat.SPONGE_V3_SCHEMATIC.getReader(new ByteArrayInputStream(sponge))) {
            return reader.read();
        }
    }

    private Clipboard read(Path file, Model.Box box) throws Exception {
        if (file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".nbt")) {
            return this.read(SchematicCodec.read(file, Files.size(file), Math.addExact(this.c.main().getLong("schematics.max-inflated-bytes"), Math.multiplyExact(box.volume(), 128)), this.c.limit("region-volume")));
        }
        ClipboardFormat format = ClipboardFormats.findByFile(file.toFile());
        if (format == null) {
            throw new IllegalArgumentException("WorldEdit не распознал файл: " + file.getFileName());
        }
        try (InputStream input = Files.newInputStream(file, new OpenOption[0])) {
            Clipboard clipboard;
            ClipboardReader reader = format.getReader(input);
            try {
                clipboard = reader.read();
            } catch (Throwable throwable) {
                if (reader != null) {
                    try {
                        reader.close();
                    } catch (Throwable throwable2) {
                        throwable.addSuppressed(throwable2);
                    }
                }
                throw throwable;
            }
            if (reader != null) {
                reader.close();
            }
            return clipboard;
        }
    }

    static void dimensions(Clipboard clipboard, Model.Box box) {
        BlockVector3 size = clipboard.getDimensions();
        if (size.x() != box.x2() - box.x1() + 1 || size.y() != box.y2() - box.y1() + 1 || size.z() != box.z2() - box.z1() + 1) {
            throw new IllegalArgumentException("WorldEdit: размер файла не совпал с сохранённой областью. Пересохраните шаблон или выполните /jd schematic fit <арена> после остановки события");
        }
    }

    private void pasteInternal(Clipboard clipboard, Model.Box box, boolean pasteAir) throws Exception {
        WorldEditStructures.dimensions(clipboard, box);
        clipboard.setOrigin(clipboard.getMinimumPoint());
        try (EditSession edit = this.createEditSession(this.world(box))) {
            Operations.complete(new ClipboardHolder(clipboard)
                    .createPaste((Extent)edit)
                    .to(BlockVector3.at(box.x1(), box.y1(), box.z1()))
                    .ignoreAirBlocks(!pasteAir)
                    .copyEntities(false)
                    .copyBiomes(false)
                    .build());
            this.flushEditSession(edit);
        }
    }

    private static void release(Clipboard clipboard) throws Exception {
        if (clipboard instanceof AutoCloseable closeable) {
            closeable.close();
        }
    }

    @Override
    public void paste(SchematicCodec.Image image, Model.Box box) throws Exception {
        this.paste(image, box, true);
    }

    @Override
    public void paste(Path file, Model.Box box) throws Exception {
        this.paste(file, box, true);
    }

    @Override
    public void paste(SchematicCodec.Image image, Model.Box box, boolean pasteAir) throws Exception {
        Clipboard clipboard = this.read(image);
        try {
            this.pasteInternal(clipboard, box, pasteAir);
        } finally {
            WorldEditStructures.release(clipboard);
        }
    }

    @Override
    public void paste(Path file, Model.Box box, boolean pasteAir) throws Exception {
        Clipboard clipboard = this.read(file, box);
        try {
            this.pasteInternal(clipboard, box, pasteAir);
        } finally {
            WorldEditStructures.release(clipboard);
        }
    }

    @Override
    public CompletableFuture<Void> pasteAsync(SchematicCodec.Image image, Model.Box box) {
        return this.pasteAsync(image, box, true);
    }

    @Override
    public CompletableFuture<Void> pasteAsync(Path file, Model.Box box) {
        return this.pasteAsync(file, box, true);
    }

    @Override
    public CompletableFuture<Void> pasteAsync(SchematicCodec.Image image, Model.Box box, boolean pasteAir) {
        return CompletableFuture.runAsync(() -> {
            try {
                this.paste(image, box, pasteAir);
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        });
    }

    @Override
    public CompletableFuture<Void> pasteAsync(Path file, Model.Box box, boolean pasteAir) {
        return CompletableFuture.runAsync(() -> {
            try {
                this.paste(file, box, pasteAir);
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        });
    }

    @Override
    public CompletableFuture<Void> captureAsync(Model.Box box, Path file) {
        return CompletableFuture.runAsync(() -> {
            try {
                this.capture(box, file);
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        });
    }

    @Override
    public void clear(Model.Box box) throws Exception {
        World weWorld = this.world(box);
        BlockVector3 min = BlockVector3.at(box.x1(), box.y1(), box.z1());
        BlockVector3 max = BlockVector3.at(box.x2(), box.y2(), box.z2());
        CuboidRegion region = new CuboidRegion(weWorld, min, max);
        try (EditSession editSession = this.createEditSession(weWorld)) {
            editSession.setBlocks((Region)region, com.sk89q.worldedit.world.block.BlockTypes.AIR.getDefaultState());
            this.flushEditSession(editSession);
        }
    }

    @Override
    public CompletableFuture<Void> clearAsync(Model.Box box) {
        return CompletableFuture.runAsync(() -> {
            try {
                this.clear(box);
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        });
    }

    @Override
    public void capture(Model.Box box, Path file) throws Exception {
        CuboidRegion region = new CuboidRegion(BlockVector3.at(box.x1(), box.y1(), box.z1()), BlockVector3.at(box.x2(), box.y2(), box.z2()));
        BlockArrayClipboard clipboard = new BlockArrayClipboard((Region)region);
        clipboard.setOrigin(region.getMinimumPoint());
        try {
            try (EditSession sourceSession = this.createEditSession(this.world(box))) {
                ForwardExtentCopy copy = new ForwardExtentCopy((Extent)sourceSession, (Region)region, (Extent)clipboard, region.getMinimumPoint());
                copy.setCopyingEntities(false);
                copy.setCopyingBiomes(false);
                Operations.complete(copy);
            }
            Path temp = file.resolveSibling(String.valueOf(file.getFileName()) + ".tmp");
            try {
                try (OutputStream output = Files.newOutputStream(temp, new OpenOption[0]);
                     ClipboardWriter writer = BuiltInClipboardFormat.SPONGE_V3_SCHEMATIC.getWriter(output)) {
                    writer.write(clipboard);
                }
                try {
                    Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException error) {
                    Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(temp);
            }
        } finally {
            WorldEditStructures.release(clipboard);
        }
    }
}
