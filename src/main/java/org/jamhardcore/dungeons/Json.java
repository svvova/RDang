/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.google.gson.Gson
 */
package org.jamhardcore.dungeons;

import com.google.gson.Gson;

public final class Json {
    public static final Gson GSON = new Gson();

    private Json() {
    }

    public static String encode(Object o) {
        return GSON.toJson(o);
    }

    public static <T> T decode(String s, Class<T> c) {
        return (T)GSON.fromJson(s, c);
    }

    public static <T> T copy(T o, Class<T> c) {
        return Json.decode(Json.encode(o), c);
    }
}

