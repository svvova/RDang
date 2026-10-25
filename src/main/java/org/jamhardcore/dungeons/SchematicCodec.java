/*
 * Decompiled with CFR 0.152.
 */
package org.jamhardcore.dungeons;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.TreeMap;
import java.util.TreeSet;
import org.jamhardcore.dungeons.Nbt;

final class SchematicCodec {
    SchematicCodec() {
    }

    static byte[] sponge(Image image, long decodedLimit, int currentDataVersion) throws Exception {
        Map<String, Object> structure = Nbt.read(new ByteArrayInputStream(image.structure()), decodedLimit);
        int count = Math.toIntExact((long)image.width() * (long)image.height() * (long)image.length());
        List<Object> states = Nbt.list(structure.get("palette"));
        LinkedHashMap<String, Object> palette = new LinkedHashMap<String, Object>();
        palette.put("minecraft:air", 0);
        int[] remap = new int[states.size()];
        for (int i = 0; i < states.size(); ++i) {
            Map map;
            Map<String, Object> state = Nbt.map(states.get(i));
            Object name = Objects.toString(state.get("Name"), "");
            if (((String)name).isBlank()) {
                throw new IllegalArgumentException("\u041f\u0443\u0441\u0442\u043e\u0435 \u0438\u043c\u044f \u0431\u043b\u043e\u043a\u0430 \u0432 \u043f\u0430\u043b\u0438\u0442\u0440\u0435");
            }
            Map<Object, Object> map2 = map = state.get("Properties") instanceof Map ? Nbt.map(state.get("Properties")) : Map.of();
            if (!map.isEmpty()) {
                StringJoiner joined = new StringJoiner(",", "[", "]");
                new TreeMap<String, Object>(map).forEach((key, value) -> joined.add(key + "=" + String.valueOf(value)));
                name = (String)name + String.valueOf(joined);
            }
            remap[i] = ((Number)palette.computeIfAbsent((String)name, unused -> palette.size())).intValue();
        }
        int[] blocks = new int[count];
        ArrayList<Object> tiles = new ArrayList<Object>();
        for (Object e : Nbt.list(structure.get("blocks"))) {
            Map<String, Object> cell = Nbt.map(e);
            List<Object> pos = Nbt.list(cell.get("pos"));
            int x = ((Number)pos.get(0)).intValue();
            int y = ((Number)pos.get(1)).intValue();
            int z = ((Number)pos.get(2)).intValue();
            int state = Nbt.integer(cell, "state");
            if (x < 0 || x >= image.width() || y < 0 || y >= image.height() || z < 0 || z >= image.length() || state < 0 || state >= remap.length) {
                throw new IllegalArgumentException("\u0411\u043b\u043e\u043a \u0432\u043d\u0435 \u0433\u0440\u0430\u043d\u0438\u0446 \u043f\u043e\u0434\u0433\u043e\u0442\u043e\u0432\u043b\u0435\u043d\u043d\u043e\u0439 \u0441\u0445\u0435\u043c\u0430\u0442\u0438\u043a\u0438");
            }
            blocks[(y * image.length() + z) * image.width() + x] = remap[state];
            if (!(cell.get("nbt") instanceof Map)) continue;
            LinkedHashMap<String, Object> data = new LinkedHashMap<String, Object>(Nbt.map(cell.get("nbt")));
            Object id = data.remove("id");
            data.remove("x");
            data.remove("y");
            data.remove("z");
            if (id == null) continue;
            tiles.add(Map.of("Id", id, "Pos", new int[]{x, y, z}, "Data", data));
        }
        ByteArrayOutputStream encoded = new ByteArrayOutputStream(count);
        for (int id : blocks) {
            while ((id & 0xFFFFFF80) != 0) {
                encoded.write(id & 0x7F | 0x80);
                id >>>= 7;
            }
            encoded.write(id);
        }
        int n = ((Number)structure.getOrDefault("DataVersion", currentDataVersion)).intValue();
        if (n <= 0) {
            n = currentDataVersion;
        }
        LinkedHashMap<String, Object> schematic = new LinkedHashMap<String, Object>();
        schematic.put("Version", 3);
        schematic.put("DataVersion", n);
        schematic.put("Width", (short)image.width());
        schematic.put("Height", (short)image.height());
        schematic.put("Length", (short)image.length());
        schematic.put("Offset", new int[]{0, 0, 0});
        schematic.put("Blocks", Map.of("Palette", palette, "Data", encoded.toByteArray(), "BlockEntities", new Nbt.Tags((byte)10, tiles)));
        schematic.put("Entities", new Nbt.Tags((byte)10, List.of()));
        return Nbt.write(Map.of("Schematic", schematic));
    }

    static Image read(Path file, long bytes, long inflated, long maxVolume) throws Exception {
        byte[] source;
        if (Files.size(file) > bytes) {
            throw new IllegalArgumentException("\u0424\u0430\u0439\u043b \u0441\u0445\u0435\u043c\u0430\u0442\u0438\u043a\u0438 \u043f\u0440\u0435\u0432\u044b\u0448\u0430\u0435\u0442 \u043b\u0438\u043c\u0438\u0442");
        }
        try (InputStream input = Files.newInputStream(file, new OpenOption[0]);){
            source = input.readNBytes((int)Math.min(bytes, 0x7FFFFFF7L) + 1);
        }
        if ((long)source.length > bytes || source.length > 0x7FFFFFF7) {
            throw new IllegalArgumentException("\u0424\u0430\u0439\u043b \u0441\u0445\u0435\u043c\u0430\u0442\u0438\u043a\u0438 \u043f\u0440\u0435\u0432\u044b\u0448\u0430\u0435\u0442 \u043b\u0438\u043c\u0438\u0442");
        }
        try (ByteArrayInputStream in = new ByteArrayInputStream(source);){
            Map<String, Object> root = Nbt.read(in, inflated);
            Image image = SchematicCodec.decode(root, maxVolume);
            Map<String, Object> schematic = root.get("Schematic") instanceof Map ? Nbt.map(root.get("Schematic")) : root;
            int version = ((Number)schematic.getOrDefault("Version", 0)).intValue();
            if ((schematic.containsKey("Palette") || schematic.get("Blocks") instanceof Map) && version >= 1 && version <= 3) {
                image = image.clipboard(version, source);
            }
            Image image2 = image;
            return image2;
        }
    }

    static Image decode(Map<String, Object> root, long maxVolume) throws Exception {
        if (root.containsKey("Schematic")) {
            root = Nbt.map(root.get("Schematic"));
        }
        if (root.containsKey("Regions")) {
            return SchematicCodec.litematic(root, maxVolume);
        }
        if (root.containsKey("Palette") || root.containsKey("Blocks") && root.get("Blocks") instanceof Map) {
            return SchematicCodec.sponge(root, maxVolume);
        }
        if (root.containsKey("palette") && root.containsKey("blocks")) {
            List<Object> dims = Nbt.list(root.get("size"));
            if (dims.size() != 3) {
                throw new IllegalArgumentException("\u041d\u0435\u0442 \u0440\u0430\u0437\u043c\u0435\u0440\u0430 NBT-\u0441\u0442\u0440\u0443\u043a\u0442\u0443\u0440\u044b");
            }
            int w = ((Number)dims.get(0)).intValue();
            int h = ((Number)dims.get(1)).intValue();
            int l = ((Number)dims.get(2)).intValue();
            SchematicCodec.volume(w, h, l, maxVolume);
            int[] chest = null;
            List<Object> palette = Nbt.list(root.get("palette"));
            List<Object> entries = Nbt.list(root.get("blocks"));
            if ((long)entries.size() > maxVolume) {
                throw new IllegalArgumentException("\u0421\u043b\u0438\u0448\u043a\u043e\u043c \u043c\u043d\u043e\u0433\u043e \u0431\u043b\u043e\u043a\u043e\u0432 \u0432 NBT");
            }
            for (Object raw : entries) {
                List<Object> pos = Nbt.list(Nbt.map(raw).get("pos"));
                if (pos.size() != 3) {
                    throw new IllegalArgumentException("\u041d\u0435\u0432\u0435\u0440\u043d\u0430\u044f \u043f\u043e\u0437\u0438\u0446\u0438\u044f \u0431\u043b\u043e\u043a\u0430 NBT");
                }
                int x = ((Number)pos.get(0)).intValue();
                int y = ((Number)pos.get(1)).intValue();
                int z = ((Number)pos.get(2)).intValue();
                if (x >= 0 && x < w && y >= 0 && y < h && z >= 0 && z < l) continue;
                throw new IllegalArgumentException("\u0411\u043b\u043e\u043a NBT \u0437\u0430 \u0433\u0440\u0430\u043d\u0438\u0446\u0435\u0439 \u0437\u0430\u044f\u0432\u043b\u0435\u043d\u043d\u043e\u0433\u043e \u0440\u0430\u0437\u043c\u0435\u0440\u0430");
            }
            for (Object item : Nbt.list(root.get("blocks"))) {
                Map<String, Object> block = Nbt.map(item);
                int state = Nbt.integer(block, "state");
                if (state < 0 || state >= palette.size()) {
                    throw new IllegalArgumentException("\u041d\u0435\u0432\u0435\u0440\u043d\u044b\u0439 \u0438\u043d\u0434\u0435\u043a\u0441 \u043f\u0430\u043b\u0438\u0442\u0440\u044b");
                }
                if (!SchematicCodec.isChest(Nbt.map(palette.get(state)))) continue;
                List<Object> pos = Nbt.list(block.get("pos"));
                chest = new int[]{((Number)pos.get(0)).intValue(), ((Number)pos.get(1)).intValue(), ((Number)pos.get(2)).intValue()};
                break;
            }
            root = new LinkedHashMap<String, Object>(root);
            root.put("entities", new Nbt.Tags((byte)10, List.of()));
            return new Image(w, h, l, Nbt.write(root), chest);
        }
        throw new IllegalArgumentException("LEGACY_SCHEMATIC: \u0434\u043b\u044f \u0441\u0442\u0430\u0440\u043e\u0433\u043e MCEdit .schematic \u0443\u0441\u0442\u0430\u043d\u043e\u0432\u0438\u0442\u0435 WorldEdit/FAWE");
    }

    private static long volume(int w, int h, int l, long limit) {
        long n = (long)w * (long)h * (long)l;
        if (w < 1 || h < 1 || l < 1 || w > 4096 || h > 4096 || l > 4096) {
            throw new IllegalArgumentException("\u041d\u0435\u0434\u043e\u043f\u0443\u0441\u0442\u0438\u043c\u044b\u0439 \u0440\u0430\u0437\u043c\u0435\u0440 \u0441\u0445\u0435\u043c\u0430\u0442\u0438\u043a\u0438: " + w + "\u00d7" + h + "\u00d7" + l + "; \u043a\u0430\u0436\u0434\u0430\u044f \u0441\u0442\u043e\u0440\u043e\u043d\u0430 1..4096");
        }
        if (n > limit) {
            throw new IllegalArgumentException("\u0421\u0445\u0435\u043c\u0430\u0442\u0438\u043a\u0430 " + w + "\u00d7" + h + "\u00d7" + l + " = " + n + " \u0431\u043b\u043e\u043a\u043e\u0432 \u043f\u0440\u0435\u0432\u044b\u0448\u0430\u0435\u0442 limits.region-volume=" + limit + ". \u0418\u0437\u043c\u0435\u043d\u0438\u0442\u0435 \u043b\u0438\u043c\u0438\u0442 \u0447\u0435\u0440\u0435\u0437 /jd config set config limits.region-volume <\u0447\u0438\u0441\u043b\u043e>");
        }
        return n;
    }

    private static Image sponge(Map<String, Object> root, long max) throws Exception {
        int w = Nbt.integer(root, "Width");
        int h = Nbt.integer(root, "Height");
        int l = Nbt.integer(root, "Length");
        int count = (int)SchematicCodec.volume(w, h, l, max);
        Map<String, Object> blocks = root.get("Blocks") instanceof Map ? Nbt.map(root.get("Blocks")) : root;
        Map<String, Object> palette = Nbt.map(blocks.get("Palette"));
        HashMap<Integer, Map<String, Object>> states = new HashMap<Integer, Map<String, Object>>();
        for (Map.Entry<String, Object> entry : palette.entrySet()) {
            states.put(((Number)entry.getValue()).intValue(), SchematicCodec.state(entry.getKey()));
        }
        Object raw = blocks.getOrDefault("Data", blocks.get("BlockData"));
        if (!(raw instanceof byte[])) {
            throw new IllegalArgumentException("\u041d\u0435\u0442 BlockData \u0441\u0445\u0435\u043c\u0430\u0442\u0438\u043a\u0438");
        }
        byte[] data = (byte[])raw;
        HashMap<Integer, LinkedHashMap<String, Object>> tiles = new HashMap<Integer, LinkedHashMap<String, Object>>();
        for (Object item : Nbt.list(blocks.getOrDefault("BlockEntities", root.getOrDefault("BlockEntities", root.get("TileEntities"))))) {
            int[] nArray;
            Map<String, Object> tile = Nbt.map(item);
            Object object = tile.get("Pos");
            if (object instanceof int[]) {
                int[] v;
                nArray = v = (int[])object;
            } else {
                int[] nArray2 = new int[3];
                nArray2[0] = Nbt.integer(tile, "x");
                nArray2[1] = Nbt.integer(tile, "y");
                nArray = nArray2;
                nArray2[2] = Nbt.integer(tile, "z");
            }
            int[] pos = nArray;
            if (pos.length != 3 || pos[0] < 0 || pos[0] >= w || pos[1] < 0 || pos[1] >= h || pos[2] < 0 || pos[2] >= l) {
                throw new IllegalArgumentException("Block entity \u0432\u043d\u0435 \u0441\u0445\u0435\u043c\u0430\u0442\u0438\u043a\u0438");
            }
            LinkedHashMap<String, Object> nbt = new LinkedHashMap<String, Object>(tile.get("Data") instanceof Map ? Nbt.map(tile.get("Data")) : tile);
            nbt.put("id", tile.getOrDefault("Id", tile.getOrDefault("id", nbt.get("id"))));
            nbt.remove("Pos");
            tiles.put((pos[1] * l + pos[2]) * w + pos[0], nbt);
        }
        ArrayList<Cell> out = new ArrayList<Cell>(count);
        int cursor = 0;
        for (int i = 0; i < count; ++i) {
            int b;
            int id = 0;
            int shift = 0;
            do {
                if (cursor >= data.length || shift >= 35) {
                    throw new IllegalArgumentException("\u041f\u043e\u0432\u0440\u0435\u0436\u0434\u0451\u043d\u043d\u044b\u0439 VarInt \u0441\u0445\u0435\u043c\u0430\u0442\u0438\u043a\u0438");
                }
                b = data[cursor++] & 0xFF;
                id |= (b & 0x7F) << shift;
                shift += 7;
            } while ((b & 0x80) != 0);
            Map state = (Map)states.get(id);
            if (state == null) {
                throw new IllegalArgumentException("\u0418\u043d\u0434\u0435\u043a\u0441 \u0432\u043d\u0435 \u043f\u0430\u043b\u0438\u0442\u0440\u044b");
            }
            out.add(new Cell(i % w, i / (w * l), i / w % l, state, (Map)tiles.get(i)));
        }
        return SchematicCodec.image(w, h, l, out, ((Number)root.getOrDefault("DataVersion", 0)).intValue());
    }

    private static Image litematic(Map<String, Object> root, long max) throws Exception {
        Map<String, Object> regions = Nbt.map(root.get("Regions"));
        if (regions.isEmpty()) {
            throw new IllegalArgumentException("\u041f\u0443\u0441\u0442\u0430\u044f Litematica");
        }
        int[] min = new int[]{Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE};
        int[] maxima = new int[]{Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
        long cellCount = 0L;
        for (Object raw : regions.values()) {
            Map<String, Object> r = Nbt.map(raw);
            Map<String, Object> p = Nbt.map(r.get("Position"));
            Map<String, Object> s = Nbt.map(r.get("Size"));
            int[] sizes = new int[]{Nbt.integer(s, "x"), Nbt.integer(s, "y"), Nbt.integer(s, "z")};
            cellCount += SchematicCodec.volume(Math.abs(sizes[0]), Math.abs(sizes[1]), Math.abs(sizes[2]), max);
            for (int i = 0; i < 3; ++i) {
                int v = Nbt.integer(p, (new String[]{"x", "y", "z"})[i]);
                int end = Math.addExact(v, sizes[i] > 0 ? sizes[i] - 1 : sizes[i] + 1);
                min[i] = Math.min(min[i], Math.min(v, end));
                maxima[i] = Math.max(maxima[i], Math.max(v, end));
            }
        }
        int w = Math.addExact(Math.subtractExact(maxima[0], min[0]), 1);
        int h = Math.addExact(Math.subtractExact(maxima[1], min[1]), 1);
        int l = Math.addExact(Math.subtractExact(maxima[2], min[2]), 1);
        SchematicCodec.volume(w, h, l, max);
        if (cellCount > max * 4L) {
            throw new IllegalArgumentException("\u0421\u043b\u0438\u0448\u043a\u043e\u043c \u043c\u043d\u043e\u0433\u043e \u043f\u0435\u0440\u0435\u043a\u0440\u044b\u0432\u0430\u044e\u0449\u0438\u0445\u0441\u044f \u0440\u0435\u0433\u0438\u043e\u043d\u043e\u0432 Litematica");
        }
        LinkedHashMap<Long, Cell> out = new LinkedHashMap<Long, Cell>();
        for (String name : new TreeSet<String>(regions.keySet())) {
            long[] lArray;
            Map<String, Object> r = Nbt.map(regions.get(name));
            Map<String, Object> p = Nbt.map(r.get("Position"));
            Map<String, Object> s = Nbt.map(r.get("Size"));
            int sx = Nbt.integer(s, "x");
            int sy = Nbt.integer(s, "y");
            int sz = Nbt.integer(s, "z");
            int rw = Math.abs(sx);
            int rh = Math.abs(sy);
            int rl = Math.abs(sz);
            int ox = Math.addExact(Nbt.integer(p, "x"), Math.min(0, sx + 1)) - min[0];
            int oy = Math.addExact(Nbt.integer(p, "y"), Math.min(0, sy + 1)) - min[1];
            int oz = Math.addExact(Nbt.integer(p, "z"), Math.min(0, sz + 1)) - min[2];
            List<Object> palette = Nbt.list(r.get("BlockStatePalette"));
            if (palette.isEmpty()) {
                throw new IllegalArgumentException("\u041f\u0443\u0441\u0442\u0430\u044f \u043f\u0430\u043b\u0438\u0442\u0440\u0430 Litematica");
            }
            int bits = Math.max(2, 32 - Integer.numberOfLeadingZeros(palette.size() - 1));
            Object storage = r.get("BlockStates");
            if (storage instanceof long[]) {
                long[] v = (long[])storage;
                lArray = v;
            } else {
                lArray = new long[]{};
            }
            long[] packed = lArray;
            long n = (long)rw * (long)rh * (long)rl;
            if ((n * (long)bits + 63L) / 64L > (long)packed.length) {
                throw new IllegalArgumentException("\u041d\u0435\u043f\u043e\u043b\u043d\u044b\u0435 BlockStates Litematica");
            }
            HashMap<Integer, LinkedHashMap<String, Object>> tiles = new HashMap<Integer, LinkedHashMap<String, Object>>();
            for (Object tileRaw : Nbt.list(r.get("TileEntities"))) {
                int[] tp;
                int[] nArray;
                Map<String, Object> tile = Nbt.map(tileRaw);
                Object object = tile.get("Pos");
                if (object instanceof int[]) {
                    int[] v;
                    nArray = v = (int[])object;
                } else {
                    int[] nArray2 = new int[3];
                    nArray2[0] = Nbt.integer(tile, "x");
                    nArray2[1] = Nbt.integer(tile, "y");
                    nArray = nArray2;
                    nArray2[2] = Nbt.integer(tile, "z");
                }
                if ((tp = nArray).length != 3) continue;
                tiles.put((tp[1] * rl + tp[2]) * rw + tp[0], new LinkedHashMap<String, Object>(tile));
            }
            int i = 0;
            while ((long)i < n) {
                int index;
                long bit = (long)i * (long)bits;
                int word = (int)(bit >>> 6);
                int offset = (int)(bit & 0x3FL);
                long value = packed[word] >>> offset;
                if (offset + bits > 64) {
                    value |= packed[word + 1] << 64 - offset;
                }
                if ((index = (int)(value & (1L << bits) - 1L)) >= palette.size()) {
                    throw new IllegalArgumentException("Litematica: \u0438\u043d\u0434\u0435\u043a\u0441 \u0432\u043d\u0435 \u043f\u0430\u043b\u0438\u0442\u0440\u044b");
                }
                int x = ox + i % rw;
                int y = oy + i / (rw * rl);
                int z = oz + i / rw % rl;
                out.put(((long)y * (long)l + (long)z) * (long)w + (long)x, new Cell(x, y, z, Nbt.map(palette.get(index)), (Map)tiles.get(i)));
                ++i;
            }
        }
        int version = ((Number)root.getOrDefault("MinecraftDataVersion", 0)).intValue();
        return SchematicCodec.image(w, h, l, new ArrayList<Cell>(out.values()), version);
    }

    private static Image image(int w, int h, int l, List<Cell> cells, int dataVersion) throws Exception {
        ArrayList<Object> palette = new ArrayList<Object>();
        ArrayList<Object> blocks = new ArrayList<Object>();
        LinkedHashMap<Map, Integer> ids = new LinkedHashMap<Map, Integer>();
        int[] chest = null;
        for (Cell c : cells) {
            int id = ids.computeIfAbsent(c.state, k -> {
                palette.add(k);
                return palette.size() - 1;
            });
            LinkedHashMap<String, Object> b = new LinkedHashMap<String, Object>();
            b.put("pos", Nbt.ints(c.x, c.y, c.z));
            b.put("state", id);
            if (c.nbt != null) {
                b.put("nbt", c.nbt);
            }
            blocks.add(b);
            if (chest != null || !SchematicCodec.isChest(c.state)) continue;
            chest = new int[]{c.x, c.y, c.z};
        }
        LinkedHashMap<String, Object> structure = new LinkedHashMap<String, Object>();
        if (dataVersion > 0) {
            structure.put("DataVersion", dataVersion);
        }
        structure.put("size", Nbt.ints(w, h, l));
        structure.put("palette", new Nbt.Tags((byte)10, palette));
        structure.put("blocks", new Nbt.Tags((byte)10, blocks));
        structure.put("entities", new Nbt.Tags((byte)10, List.of()));
        return new Image(w, h, l, Nbt.write(structure), chest);
    }

    private static boolean isChest(Map<String, Object> state) {
        if (state == null) return false;
        String name = Objects.toString(state.get("Name"), "").toLowerCase(Locale.ROOT);
        if (name.startsWith("minecraft:")) {
            name = name.substring("minecraft:".length());
        }
        return name.equals("chest") || name.equals("trapped_chest") || name.equals("barrel");
    }

    private static Map<String, Object> state(String text) {
        int start = text.indexOf(91);
        LinkedHashMap<String, Object> out = new LinkedHashMap<String, Object>();
        out.put("Name", start < 0 ? text : text.substring(0, start));
        if (start >= 0) {
            if (!text.endsWith("]")) {
                throw new IllegalArgumentException("\u041d\u0435\u043a\u043e\u0440\u0440\u0435\u043a\u0442\u043d\u043e\u0435 \u0441\u043e\u0441\u0442\u043e\u044f\u043d\u0438\u0435 \u0431\u043b\u043e\u043a\u0430");
            }
            LinkedHashMap<String, String> props = new LinkedHashMap<String, String>();
            for (String value : text.substring(start + 1, text.length() - 1).split(",")) {
                String[] pair = value.split("=", 2);
                if (pair.length != 2) {
                    throw new IllegalArgumentException("\u041d\u0435\u043a\u043e\u0440\u0440\u0435\u043a\u0442\u043d\u043e\u0435 \u0441\u0432\u043e\u0439\u0441\u0442\u0432\u043e \u0431\u043b\u043e\u043a\u0430");
                }
                props.put(pair[0], pair[1]);
            }
            out.put("Properties", props);
        }
        return out;
    }

    record Image(int width, int height, int length, byte[] structure, int[] chest, int spongeVersion, byte[] clipboardData) {
        Image(int width, int height, int length, byte[] structure, int[] chest) {
            this(width, height, length, structure, chest, 0, null);
        }

        Image clipboard(int version, byte[] data) {
            return new Image(this.width, this.height, this.length, this.structure, this.chest, version, data);
        }
    }

    private record Cell(int x, int y, int z, Map<String, Object> state, Map<String, Object> nbt) {
    }
}

