/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.kyori.adventure.text.Component
 *  net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
 *  org.bukkit.Bukkit
 *  org.bukkit.command.CommandSender
 */
package org.jamhardcore.dungeons;

import java.util.Map;
import java.util.Objects;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.jamhardcore.dungeons.Configs;

public final class Messages {
    private final Configs config;

    public Messages(Configs c) {
        this.config = c;
    }

    public static Component color(String s) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(s);
    }

    public static String stripColor(String s) {
        if (s == null) return "";
        return s.replaceAll("(?i)[&§][0-9a-fk-orx]", "");
    }

    public String raw(String key, Map<String, String> vars) {
        String s = this.config.file("messages").getString(key, key);
        for (Map.Entry<String, String> v : vars.entrySet()) {
            s = s.replace("{" + v.getKey() + "}", v.getValue());
        }
        return s;
    }

    public void send(CommandSender target, String key, Map<String, String> vars) {
        target.sendMessage(Messages.color(this.config.file("messages").getString("prefix", "") + this.raw(key, vars)));
    }

    public void send(CommandSender target, String key) {
        this.send(target, key, Map.of());
    }

    public void broadcast(String key, Map<String, String> vars) {
        Bukkit.broadcast((Component)Messages.color(this.config.file("messages").getString("prefix", "") + this.raw(key, vars)));
    }

    public void error(CommandSender target, String error) {
        String msg = Objects.toString(error, "Ошибка");
        while (msg.startsWith("Ошибка: ") || msg.startsWith("ошибка: ")) {
            msg = msg.substring(8);
        }
        this.send(target, "error", Map.of("error", msg));
    }
}

