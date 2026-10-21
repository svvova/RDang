/*
 * Decompiled with CFR 0.152.
 */
package org.jamhardcore.dungeons;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

final class Nbt {
    Nbt() {
    }

    static Map<String, Object> read(InputStream stream, final long limit) throws IOException {
        InputStream uncompressed;
        FilterInputStream bounded;
        DataInputStream in;
        PushbackInputStream peek = new PushbackInputStream(stream, 2);
        int a = peek.read();
        int b = peek.read();
        if (b >= 0) {
            peek.unread(b);
        }
        if (a >= 0) {
            peek.unread(a);
        }
        if ((in = new DataInputStream(bounded = new FilterInputStream(uncompressed = a == 31 && b == 139 ? new GZIPInputStream(peek) : peek){
            long used;

            private void count(long n) throws IOException {
                if (n > 0L && (this.used += n) > limit) {
                    throw new IOException("NBT exceeds the uncompressed size limit");
                }
            }

            @Override
            public int read() throws IOException {
                int n = super.read();
                if (n >= 0) {
                    this.count(1L);
                }
                return n;
            }

            @Override
            public int read(byte[] data, int off, int len) throws IOException {
                int n = this.in.read(data, off, len);
                this.count(n);
                return n;
            }
        })).readUnsignedByte() != 10) {
            throw new IOException("NBT root must be a compound");
        }
        in.readUTF();
        return Nbt.map(Nbt.payload(in, 10, 0, limit));
    }

    private static int length(DataInputStream in, long limit, int bytes) throws IOException {
        int n = in.readInt();
        if (n < 0 || (long)n > limit / (long)bytes) {
            throw new IOException("Invalid NBT length");
        }
        return n;
    }

    private static Object payload(DataInputStream in, int type, int depth, long limit) throws IOException {
        if (depth > 64) {
            throw new IOException("NBT is too deep");
        }
        return switch (type) {
            case 1 -> in.readByte();
            case 2 -> in.readShort();
            case 3 -> in.readInt();
            case 4 -> in.readLong();
            case 5 -> Float.valueOf(in.readFloat());
            case 6 -> in.readDouble();
            case 7 -> {
                byte[] a = new byte[Nbt.length(in, limit, 1)];
                in.readFully(a);
                yield a;
            }
            case 8 -> in.readUTF();
            case 9 -> {
                byte child = in.readByte();
                int size = Nbt.length(in, limit, 8);
                if (child == 0 && size > 0) {
                    throw new IOException("Invalid NBT list");
                }
                ArrayList<Object> a = new ArrayList<Object>(size);
                for (int i = 0; i < size; ++i) {
                    a.add(Nbt.payload(in, child, depth + 1, limit));
                }
                yield new Tags(child, a);
            }
            case 10 -> {
                int child;
                LinkedHashMap<String, Object> a = new LinkedHashMap<String, Object>();
                int count = 0;
                while ((child = in.readUnsignedByte()) != 0) {
                    if (++count > 1000000) {
                        throw new IOException("Too many NBT tags");
                    }
                    a.put(in.readUTF(), Nbt.payload(in, child, depth + 1, limit));
                }
                yield a;
            }
            case 11 -> {
                int[] a = new int[Nbt.length(in, limit, 4)];
                for (int i = 0; i < a.length; ++i) {
                    a[i] = in.readInt();
                }
                yield a;
            }
            case 12 -> {
                long[] a = new long[Nbt.length(in, limit, 8)];
                for (int i = 0; i < a.length; ++i) {
                    a[i] = in.readLong();
                }
                yield a;
            }
            default -> throw new IOException("Unsupported NBT type: " + type);
        };
    }

    static byte[] write(Map<String, Object> root) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(new GZIPOutputStream(bytes));){
            out.writeByte(10);
            out.writeUTF("");
            Nbt.writePayload(out, root);
        }
        return bytes.toByteArray();
    }

    private static int type(Object value) throws IOException {
        if (value instanceof Byte) {
            return 1;
        }
        if (value instanceof Short) {
            return 2;
        }
        if (value instanceof Integer) {
            return 3;
        }
        if (value instanceof Long) {
            return 4;
        }
        if (value instanceof Float) {
            return 5;
        }
        if (value instanceof Double) {
            return 6;
        }
        if (value instanceof byte[]) {
            return 7;
        }
        if (value instanceof String) {
            return 8;
        }
        if (value instanceof Tags) {
            return 9;
        }
        if (value instanceof Map) {
            return 10;
        }
        if (value instanceof int[]) {
            return 11;
        }
        if (value instanceof long[]) {
            return 12;
        }
        throw new IOException("Unsupported NBT value");
    }

    private static void writePayload(DataOutputStream out, Object v) throws IOException {
        switch (Nbt.type(v)) {
            case 1: {
                out.writeByte(((Byte)v).byteValue());
                break;
            }
            case 2: {
                out.writeShort(((Short)v).shortValue());
                break;
            }
            case 3: {
                out.writeInt((Integer)v);
                break;
            }
            case 4: {
                out.writeLong((Long)v);
                break;
            }
            case 5: {
                out.writeFloat(((Float)v).floatValue());
                break;
            }
            case 6: {
                out.writeDouble((Double)v);
                break;
            }
            case 7: {
                byte[] a = (byte[])v;
                out.writeInt(a.length);
                out.write(a);
                break;
            }
            case 8: {
                out.writeUTF((String)v);
                break;
            }
            case 9: {
                Tags list = (Tags)v;
                out.writeByte(list.type);
                out.writeInt(list.values.size());
                for (Object item : list.values) {
                    Nbt.writePayload(out, item);
                }
                break;
            }
            case 10: {
                for (Map.Entry<String, Object> entry : Nbt.map(v).entrySet()) {
                    out.writeByte(Nbt.type(entry.getValue()));
                    out.writeUTF(entry.getKey());
                    Nbt.writePayload(out, entry.getValue());
                }
                out.writeByte(0);
                break;
            }
            case 11: {
                int[] a = (int[])v;
                out.writeInt(a.length);
                for (int n : a) {
                    out.writeInt(n);
                }
                break;
            }
            case 12: {
                long[] a = (long[])v;
                out.writeInt(a.length);
                for (long n : a) {
                    out.writeLong(n);
                }
                break;
            }
        }
    }

    static Map<String, Object> map(Object v) {
        if (!(v instanceof Map)) {
            throw new IllegalArgumentException("Expected NBT compound");
        }
        return (Map)v;
    }

    static List<Object> list(Object v) {
        List<Object> list;
        if (v instanceof Tags) {
            Tags tags = (Tags)v;
            list = tags.values;
        } else {
            list = List.of();
        }
        return list;
    }

    static int integer(Map<String, Object> m, String key) {
        Object n = m.get(key);
        if (!(n instanceof Number)) {
            throw new IllegalArgumentException("Missing NBT number: " + key);
        }
        Number num = (Number)n;
        return num.intValue();
    }

    static Tags ints(int ... values) {
        return new Tags((byte) 3, Arrays.stream(values).boxed().map(x -> (Object) x).toList());
    }

    record Tags(byte type, List<Object> values) {
    }
}

