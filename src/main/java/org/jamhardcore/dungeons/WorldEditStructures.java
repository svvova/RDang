/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.sk89q.worldedit.EditSession
 *  com.sk89q.worldedit.WorldEdit
 *  com.sk89q.worldedit.bukkit.BukkitAdapter
 *  com.sk89q.worldedit.extension.platform.Capability
 *  com.sk89q.worldedit.extent.Extent
 *  com.sk89q.worldedit.extent.clipboard.BlockArrayClipboard
 *  com.sk89q.worldedit.extent.clipboard.Clipboard
 *  com.sk89q.worldedit.extent.clipboard.io.BuiltInClipboardFormat
 *  com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat
 *  com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats
 *  com.sk89q.worldedit.extent.clipboard.io.ClipboardReader
 *  com.sk89q.worldedit.extent.clipboard.io.ClipboardWriter
 *  com.sk89q.worldedit.function.operation.ForwardExtentCopy
 *  com.sk89q.worldedit.function.operation.Operation
 *  com.sk89q.worldedit.function.operation.Operations
 *  com.sk89q.worldedit.math.BlockVector3
 *  com.sk89q.worldedit.regions.CuboidRegion
 *  com.sk89q.worldedit.regions.Region
 *  com.sk89q.worldedit.session.ClipboardHolder
 *  com.sk89q.worldedit.world.World
 *  org.bukkit.Bukkit
 *  org.bukkit.World
 */
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
import com.sk89q.worldedit.function.operation.ForwardExtentCopy;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.session.ClipboardHolder;
import com.sk89q.worldedit.world.World;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.function.Function;
import org.bukkit.Bukkit;
import org.jamhardcore.dungeons.Configs;
import org.jamhardcore.dungeons.Model;
import org.jamhardcore.dungeons.SchematicCodec;
import org.jamhardcore.dungeons.StructureBackend;

final class WorldEditStructures
implements StructureBackend {
    private final Configs c;
    private final Function<Model.Box, World> worlds;

    WorldEditStructures(Configs configs) {
        this(configs, box -> {
            org.bukkit.World world = Bukkit.getWorld((String)box.world());
            if (world == null) {
                throw new IllegalArgumentException("\u041c\u0438\u0440 \u043d\u0435 \u0437\u0430\u0433\u0440\u0443\u0436\u0435\u043d: " + box.world());
            }
            return BukkitAdapter.adapt((org.bukkit.World)world);
        });
    }

    WorldEditStructures(Configs configs, Function<Model.Box, World> worlds) {
        this.c = configs;
        this.worlds = worlds;
    }

    private World world(Model.Box box) {
        return this.worlds.apply(box);
    }

    private long decodedLimit(SchematicCodec.Image image) {
        return Math.addExact(this.c.main().getLong("schematics.max-inflated-bytes"), Math.multiplyExact((long)image.width() * (long)image.height() * (long)image.length(), 128));
    }

    private Clipboard read(SchematicCodec.Image image) throws Exception {
        if (image.clipboardData() != null) {
            BuiltInClipboardFormat format = BuiltInClipboardFormat.valueOf((String)("SPONGE_V" + image.spongeVersion() + "_SCHEMATIC"));
            try (ClipboardReader reader = format.getReader((InputStream)new ByteArrayInputStream(image.clipboardData()));){
                Clipboard clipboard = reader.read();
                return clipboard;
            }
        }
        int dataVersion = WorldEdit.getInstance().getPlatformManager().queryCapability(Capability.WORLD_EDITING).getDataVersion();
        byte[] sponge = SchematicCodec.sponge(image, this.decodedLimit(image), dataVersion);
        try (ClipboardReader reader = BuiltInClipboardFormat.SPONGE_V3_SCHEMATIC.getReader((InputStream)new ByteArrayInputStream(sponge));){
            Clipboard clipboard = reader.read();
            return clipboard;
        }
    }

    private Clipboard read(Path file, Model.Box box) throws Exception {
        if (file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".nbt")) {
            return this.read(SchematicCodec.read(file, Files.size(file), Math.addExact(this.c.main().getLong("schematics.max-inflated-bytes"), Math.multiplyExact(box.volume(), 128)), this.c.limit("region-volume")));
        }
        ClipboardFormat format = ClipboardFormats.findByFile((File)file.toFile());
        if (format == null) {
            throw new IllegalArgumentException("WorldEdit \u043d\u0435 \u0440\u0430\u0441\u043f\u043e\u0437\u043d\u0430\u043b \u0444\u0430\u0439\u043b: " + String.valueOf(file.getFileName()));
        }
        try (InputStream input = Files.newInputStream(file, new OpenOption[0]);){
            Clipboard clipboard;
            block14: {
                ClipboardReader reader = format.getReader(input);
                try {
                    clipboard = reader.read();
                    if (reader == null) break block14;
                }
                catch (Throwable throwable) {
                    if (reader != null) {
                        try {
                            reader.close();
                        }
                        catch (Throwable throwable2) {
                            throwable.addSuppressed(throwable2);
                        }
                    }
                    throw throwable;
                }
                reader.close();
            }
            return clipboard;
        }
    }

    static void dimensions(Clipboard clipboard, Model.Box box) {
        BlockVector3 size = clipboard.getDimensions();
        if (size.x() != box.x2() - box.x1() + 1 || size.y() != box.y2() - box.y1() + 1 || size.z() != box.z2() - box.z1() + 1) {
            throw new IllegalArgumentException("WorldEdit: \u0440\u0430\u0437\u043c\u0435\u0440 \u0444\u0430\u0439\u043b\u0430 \u043d\u0435 \u0441\u043e\u0432\u043f\u0430\u043b \u0441 \u0441\u043e\u0445\u0440\u0430\u043d\u0451\u043d\u043d\u043e\u0439 \u043e\u0431\u043b\u0430\u0441\u0442\u044c\u044e. \u041f\u0435\u0440\u0435\u0441\u043e\u0445\u0440\u0430\u043d\u0438\u0442\u0435 \u0448\u0430\u0431\u043b\u043e\u043d \u0438\u043b\u0438 \u0432\u044b\u043f\u043e\u043b\u043d\u0438\u0442\u0435 /jd schematic fit <\u0430\u0440\u0435\u043d\u0430> \u043f\u043e\u0441\u043b\u0435 \u043e\u0441\u0442\u0430\u043d\u043e\u0432\u043a\u0438 \u0441\u043e\u0431\u044b\u0442\u0438\u044f");
        }
    }

    private void paste(Clipboard clipboard, Model.Box box) throws Exception {
        WorldEditStructures.dimensions(clipboard, box);
        clipboard.setOrigin(clipboard.getMinimumPoint());
        try (EditSession edit = WorldEdit.getInstance().newEditSessionBuilder().world(this.world(box)).maxBlocks(-1).build();){
            Operations.complete((Operation)new ClipboardHolder(clipboard).createPaste((Extent)edit).to(BlockVector3.at((int)box.x1(), (int)box.y1(), (int)box.z1())).ignoreAirBlocks(false).copyEntities(false).copyBiomes(false).build());
        }
    }

    private static void release(Clipboard clipboard) throws Exception {
        if (clipboard instanceof AutoCloseable) {
            AutoCloseable closeable = (AutoCloseable)clipboard;
            closeable.close();
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @Override
    public void paste(SchematicCodec.Image image, Model.Box box) throws Exception {
        Clipboard clipboard = this.read(image);
        try {
            this.paste(clipboard, box);
        }
        finally {
            WorldEditStructures.release(clipboard);
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @Override
    public void paste(Path file, Model.Box box) throws Exception {
        Clipboard clipboard = this.read(file, box);
        try {
            this.paste(clipboard, box);
        }
        finally {
            WorldEditStructures.release(clipboard);
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @Override
    public void capture(Model.Box box, Path file) throws Exception {
        CuboidRegion region = new CuboidRegion(BlockVector3.at((int)box.x1(), (int)box.y1(), (int)box.z1()), BlockVector3.at((int)box.x2(), (int)box.y2(), (int)box.z2()));
        BlockArrayClipboard clipboard = new BlockArrayClipboard((Region)region);
        clipboard.setOrigin(region.getMinimumPoint());
        try {
            ForwardExtentCopy copy = new ForwardExtentCopy((Extent)this.world(box), (Region)region, (Extent)clipboard, region.getMinimumPoint());
            copy.setCopyingEntities(false);
            copy.setCopyingBiomes(false);
            Operations.complete((Operation)copy);
            Path temp = file.resolveSibling(String.valueOf(file.getFileName()) + ".tmp");
            try {
                try (OutputStream output = Files.newOutputStream(temp, new OpenOption[0]);
                     ClipboardWriter writer = BuiltInClipboardFormat.SPONGE_V3_SCHEMATIC.getWriter(output);){
                    writer.write((Clipboard)clipboard);
                }
                try {
                    Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                }
                catch (AtomicMoveNotSupportedException error) {
                    Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
                }
            }
            finally {
                Files.deleteIfExists(temp);
            }
        }
        finally {
            WorldEditStructures.release((Clipboard)clipboard);
        }
    }
}

