/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.Bukkit
 *  org.bukkit.plugin.Plugin
 */
package org.jamhardcore.dungeons;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.jamhardcore.dungeons.Nbt;
import org.jamhardcore.dungeons.SchematicCodec;

final class LegacySchematics {
    LegacySchematics() {
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    static SchematicCodec.Image read(Path file, long inflated, long volume) throws Exception {
        Object clipboard;
        Plugin plugin = Bukkit.getPluginManager().getPlugin("WorldEdit");
        if (plugin == null) {
            plugin = Bukkit.getPluginManager().getPlugin("FastAsyncWorldEdit");
        }
        if (plugin == null || !plugin.isEnabled()) {
            throw new IllegalArgumentException("\u0414\u043b\u044f \u0441\u0442\u0430\u0440\u043e\u0433\u043e .schematic \u043d\u0443\u0436\u0435\u043d WorldEdit/FAWE; .schem \u0438 .litematic \u0447\u0438\u0442\u0430\u044e\u0442\u0441\u044f \u043d\u0430\u043f\u0440\u044f\u043c\u0443\u044e");
        }
        ClassLoader loader = plugin.getClass().getClassLoader();
        Class<?> formats = Class.forName("com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats", true, loader);
        Class<?> formatType = Class.forName("com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat", true, loader);
        Class<?> readerType = Class.forName("com.sk89q.worldedit.extent.clipboard.io.ClipboardReader", true, loader);
        Class<?> writerType = Class.forName("com.sk89q.worldedit.extent.clipboard.io.ClipboardWriter", true, loader);
        Object format = formats.getMethod("findByFile", File.class).invoke(null, file.toFile());
        if (format == null) {
            throw new IllegalArgumentException("WorldEdit \u043d\u0435 \u0440\u0430\u0441\u043f\u043e\u0437\u043d\u0430\u043b \u0444\u0430\u0439\u043b");
        }
        try (InputStream in = Files.newInputStream(file, new OpenOption[0]);
             AutoCloseable reader = (AutoCloseable) formatType.getMethod("getReader", InputStream.class).invoke(format, in);){
            clipboard = readerType.getMethod("read", new Class[0]).invoke(reader, new Object[0]);
        }
        Class<?> builtIn = Class.forName("com.sk89q.worldedit.extent.clipboard.io.BuiltInClipboardFormat", true, loader);
        Object sponge = null;
        int spongeVersion = 3;
        for (String name : new String[]{"SPONGE_V3_SCHEMATIC", "SPONGE_SCHEMATIC"}) {
            try {
                sponge = builtIn.getField(name).get(null);
                spongeVersion = name.equals("SPONGE_SCHEMATIC") ? 2 : 3;
                break;
            }
            catch (NoSuchFieldException noSuchFieldException) {
            }
        }
        if (sponge == null) {
            throw new IllegalArgumentException("\u041e\u0431\u043d\u043e\u0432\u0438\u0442\u0435 WorldEdit \u0434\u043b\u044f \u044d\u043a\u0441\u043f\u043e\u0440\u0442\u0430 Sponge");
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (AutoCloseable writer = (AutoCloseable) formatType.getMethod("getWriter", OutputStream.class).invoke(sponge, bytes);){
            writerType.getMethod("write", Class.forName("com.sk89q.worldedit.extent.clipboard.Clipboard", true, loader)).invoke(writer, clipboard);
        }
        byte[] prepared = bytes.toByteArray();
        return SchematicCodec.decode(Nbt.read(new ByteArrayInputStream(prepared), inflated), volume).clipboard(spongeVersion, prepared);
    }
}

