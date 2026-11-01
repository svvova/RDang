/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.configuration.ConfigurationSection
 *  org.bukkit.configuration.file.YamlConfiguration
 *  org.bukkit.plugin.java.JavaPlugin
 */
package org.jamhardcore.dungeons;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.jamhardcore.dungeons.Configs;
import org.jamhardcore.dungeons.Json;
import org.jamhardcore.dungeons.Model;
import org.jamhardcore.dungeons.ThemeCatalog;

final class Migrations {
    Migrations() {
    }

    static boolean upgrade(JavaPlugin plugin, Map<String, YamlConfiguration> files) throws Exception {
        ConfigurationSection configurationSection;
        YamlConfiguration main = files.get("config");
        int previousSchema = main.getInt("schema", 1);
        int previousRevision = main.getInt("revision", 3);
        YamlConfiguration current = Migrations.resource(plugin, "config");
        boolean repaired = Migrations.mergeMissing((ConfigurationSection)main, (ConfigurationSection)current);
        repaired |= Migrations.completeDatabase(main, current, previousSchema);
        if (main.getStringList("zombie-equipment.armor").size() != 4) {
            main.set("zombie-equipment.armor", new ArrayList(current.getStringList("zombie-equipment.armor")));
            plugin.getLogger().warning("config.yml: zombie-equipment.armor \u0442\u0440\u0435\u0431\u0443\u0435\u0442 4 \u044d\u043b\u0435\u043c\u0435\u043d\u0442\u0430. \u0412\u043e\u0441\u0441\u0442\u0430\u043d\u043e\u0432\u043b\u0435\u043d \u0441\u0442\u0430\u043d\u0434\u0430\u0440\u0442\u043d\u044b\u0439 \u043a\u043e\u043c\u043f\u043b\u0435\u043a\u0442; \u043f\u0440\u0435\u0434\u044b\u0434\u0443\u0449\u0438\u0439 config.yml \u0431\u0443\u0434\u0435\u0442 \u0441\u043e\u0445\u0440\u0430\u043d\u0451\u043d \u0432 .bak.");
            repaired = true;
        }
        if (previousSchema >= 3) {
            repaired |= Migrations.upgradeBalance(plugin, files.get("themes"));
            repaired |= Migrations.mergeMissing((ConfigurationSection)files.get("messages"), (ConfigurationSection)Migrations.resource(plugin, "messages"));
            repaired |= Migrations.upgrade114(files, previousRevision);
            if (previousRevision < 5) {
                main.set("revision", (Object)5);
                repaired = true;
            }
            repaired |= Migrations.upgrade116(main, previousRevision);
            return repaired |= Migrations.upgrade117(files, previousRevision);
        }
        if (previousSchema < 2) {
            YamlConfiguration yamlConfiguration;
            ConfigurationSection arenaRoot;
            ConfigurationSection root;
            main.set("schema", (Object)2);
            main.set("timers.pve-seconds", (Object)900);
            main.set("timers.wave-interval-seconds", (Object)180);
            main.set("test", null);
            main.set("protection.pve-pushback-distance", (Object)3.0);
            main.set("protection.pve-pushback-strength", (Object)0.85);
            main.set("protection.owner-exit-grace-seconds", (Object)5);
            YamlConfiguration defaults = Migrations.resource(plugin, "themes");
            YamlConfiguration themes = files.get("themes");
            ConfigurationSection presets = themes.getConfigurationSection("mob-presets");
            if (presets != null) {
                for (String string : presets.getKeys(false)) {
                    if (!"CAMEL_HUSK".equals(themes.getString("mob-presets." + string + ".entity"))) continue;
                    themes.set("mob-presets." + string, null);
                }
            }
            if ((root = themes.getConfigurationSection("themes")) != null) {
                for (String id : root.getKeys(false)) {
                    List<String> allowed = ThemeCatalog.allowed(id);
                    if (id.equals("mining")) {
                        allowed = new ArrayList<String>(allowed);
                        allowed.add("creeper");
                    }
                    if (id.equals("water")) {
                        allowed = new ArrayList<String>(allowed);
                        allowed.addAll(List.of("guardian", "elder_guardian"));
                    }
                    if (allowed == null) {
                        allowed = new ArrayList<String>(Objects.requireNonNull(themes.getConfigurationSection("mob-presets")).getKeys(false));
                    }
                    ArrayList waves = new ArrayList();
                    List old = themes.getMapList("themes." + id + ".waves");
                    List baseline = defaults.getMapList("themes." + id + ".waves");
                    for (int i = 0; i < 5; ++i) {
                        LinkedHashMap row = new LinkedHashMap();
                        if (i < old.size()) {
                            for (Map.Entry<?, ?> entry : ((Map<?, ?>)old.get(i)).entrySet()) {
                                if (!allowed.contains(entry.getKey().toString())) continue;
                                row.put(entry.getKey().toString(), entry.getValue());
                            }
                        }
                        if (row.isEmpty()) {
                            if (i < baseline.size()) {
                                ((Map<?, ?>)baseline.get(i)).forEach((k, v) -> row.put(k.toString(), v));
                            } else if (!allowed.isEmpty()) {
                                row.put(allowed.getFirst(), 1);
                            }
                        }
                        waves.add(row);
                    }
                    themes.set("themes." + id + ".waves", waves);
                }
            }
            if ((arenaRoot = (yamlConfiguration = files.get("arenas")).getConfigurationSection("arenas")) != null) {
                for (String id : arenaRoot.getKeys(false)) {
                    Model.Arena arena = Json.decode(Json.encode(Configs.plain(arenaRoot.get(id))), Model.Arena.class);
                    List<String> allowed = ThemeCatalog.allowed(arena.theme);
                    if (arena.points != null) {
                        for (Model.Spawn point : arena.points) {
                            if ("water".equals(arena.theme)) {
                                point.mob = Migrations.shipMob(point.mob);
                            }
                            if (point.mob.equals("any") || (allowed == null || allowed.contains(point.mob)) && themes.contains("mob-presets." + point.mob)) continue;
                            point.mob = "any";
                        }
                    }
                    yamlConfiguration.set("arenas." + id, Json.GSON.fromJson(Json.encode(arena), Map.class));
                }
            }
            YamlConfiguration text = Migrations.resource(plugin, "messages");
            for (String key : List.of("pve-start", "wave", "pve-locked", "pve-complete", "hud-pve", "hud-pve-clear", "failed", "return-region")) {
                files.get("messages").set(key, text.get(key));
            }
        }
        main.set("integrations.cases.plugin", (Object)"PhoenixCrates");
        if (main.getInt("limits.placed-events") == 6) {
            main.set("limits.placed-events", (Object)8);
        }
        YamlConfiguration y = files.get("themes");
        HashSet<String> creepers = new HashSet<String>();
        ConfigurationSection all = y.getConfigurationSection("mob-presets");
        if (all != null) {
            for (String string : all.getKeys(false)) {
                if (!"CREEPER".equals(y.getString("mob-presets." + string + ".entity"))) continue;
                creepers.add(string);
            }
        }
        for (String string : creepers) {
            y.set("mob-presets." + string, null);
        }
        y.set("mob-presets.zombie.zombie-equipment", (Object)true);
        ConfigurationSection themeRoot = y.getConfigurationSection("themes");
        if (themeRoot != null) {
            for (Object id : themeRoot.getKeys(false)) {
                ArrayList rows = new ArrayList();
                for (Map row : y.getMapList("themes." + (String)id + ".waves")) {
                    LinkedHashMap<String, Integer> out = new LinkedHashMap<String, Integer>();
                    row.forEach((k, v) -> out.put(k.toString(), (Integer)v));
                    int replaceCount = 0;
                    for (String mob : creepers) {
                        Object n = out.remove(mob);
                        if (!(n instanceof Number)) continue;
                        Number num = (Number)n;
                        replaceCount += num.intValue();
                    }
                    if (replaceCount > 0) {
                        out.put("zombie", ((Number)out.getOrDefault("zombie", 0)).intValue() + replaceCount);
                    }
                    rows.add(out);
                }
                y.set("themes." + (String)id + ".waves", rows);
            }
        }
        if ((configurationSection = files.get("arenas").getConfigurationSection("arenas")) != null) {
            for (String id : configurationSection.getKeys(false)) {
                Model.Arena arena = Json.decode(Json.encode(Configs.plain(configurationSection.get(id))), Model.Arena.class);
                for (Model.Spawn point : arena.points) {
                    if (!creepers.contains(point.mob)) continue;
                    point.mob = "zombie";
                }
                configurationSection.set(id, Json.GSON.fromJson(Json.encode(arena), Map.class));
            }
        }
        YamlConfiguration messages = Migrations.resource(plugin, "messages");
        for (String key : messages.getKeys(false)) {
            if (files.get("messages").contains(key) && !List.of("pve-start", "wave", "pve-locked", "pve-complete", "hud-pve", "hud-pve-clear").contains(key)) continue;
            files.get("messages").set(key, messages.get(key));
        }
        main.set("schema", (Object)3);
        Migrations.upgradeBalance(plugin, files.get("themes"));
        Migrations.upgrade114(files, previousRevision);
        main.set("revision", (Object)5);
        Migrations.upgrade116(main, previousRevision);
        Migrations.upgrade117(files, previousRevision);
        return true;
    }

    private static boolean upgrade117(Map<String, YamlConfiguration> files, int previousRevision) {
        if (previousRevision >= 7) {
            return false;
        }
        YamlConfiguration main = files.get("config");
        YamlConfiguration messages = files.get("messages");
        if (messages != null) {
            String pve = messages.getString("pve-appearance");
            if (pve != null && pve.contains("Координаты:")) {
                messages.set("pve-appearance", pve.replaceAll("\\s*(&[0-9a-fk-orx])?Координаты:.*", ""));
            }
            String pvp = messages.getString("pvp-appearance");
            if (pvp != null && pvp.contains("Координаты:")) {
                messages.set("pvp-appearance", pvp.replaceAll("\\s*(&[0-9a-fk-orx])?Координаты:.*", ""));
            }
        }
        main.set("revision", (Object)7);
        return true;
    }

    private static boolean upgrade116(YamlConfiguration main, int previousRevision) {
        if (previousRevision >= 6) {
            return false;
        }
        if (main.getInt("limits.region-volume") == 262144) {
            main.set("limits.region-volume", (Object)0x400000);
        }
        if (main.getInt("limits.snapshot-chunks") == 64) {
            main.set("limits.snapshot-chunks", (Object)1024);
        }
        main.set("revision", (Object)6);
        return true;
    }

    private static boolean upgrade114(Map<String, YamlConfiguration> files, int previousRevision) {
        ConfigurationSection arenas;
        if (previousRevision >= 4) {
            return false;
        }
        YamlConfiguration main = files.get("config");
        main.set("timers.reconnect-seconds", (Object)180);
        main.set("revision", (Object)4);
        YamlConfiguration themes = files.get("themes");
        ArrayList waves = new ArrayList();
        for (Map old : themes.getMapList("themes.water.waves")) {
            LinkedHashMap<String, Integer> row = new LinkedHashMap<String, Integer>();
            for (Map.Entry<?, ?> entry : ((Map<?, ?>)old).entrySet()) {
                String mob = Migrations.shipMob(entry.getKey().toString());
                int count = ((Number)entry.getValue()).intValue();
                row.put(mob, ((Number)row.getOrDefault(mob, 0)).intValue() + count);
            }
            waves.add(row);
        }
        if (!waves.isEmpty()) {
            themes.set("themes.water.waves", waves);
        }
        if ((arenas = files.get("arenas").getConfigurationSection("arenas")) != null) {
            for (String id : arenas.getKeys(false)) {
                Model.Arena arena = Json.decode(Json.encode(Configs.plain(arenas.get(id))), Model.Arena.class);
                if (!"water".equals(arena.theme)) continue;
                boolean changed = false;
                for (Model.Spawn point : arena.points) {
                    String mob = Migrations.shipMob(point.mob);
                    if (mob.equals(point.mob)) continue;
                    point.mob = mob;
                    changed = true;
                }
                if (!changed) continue;
                arenas.set(id, Json.GSON.fromJson(Json.encode(arena), Map.class));
            }
        }
        return true;
    }

    private static String shipMob(String mob) {
        return switch (mob) {
            case "guardian" -> "pillager";
            case "elder_guardian" -> "drowned_trident";
            default -> mob;
        };
    }

    private static boolean upgradeBalance(JavaPlugin plugin, YamlConfiguration themes) throws Exception {
        if (themes.getInt("balance-version", 1) >= 2) {
            return false;
        }
        YamlConfiguration old = Migrations.resource(plugin, "migrations/theme-balance-v1");
        YamlConfiguration current = Migrations.resource(plugin, "themes");
        for (String id : Objects.requireNonNull(current.getConfigurationSection("themes")).getKeys(false)) {
            String path = "themes." + id + ".waves";
            if (!themes.getMapList(path).equals(old.getMapList(path))) continue;
            themes.set(path, (Object)current.getMapList(path));
            plugin.getLogger().info("\u041f\u043b\u0430\u0432\u043d\u044b\u0439 \u0440\u043e\u0441\u0442 \u0432\u043e\u043b\u043d: \u043e\u0431\u043d\u043e\u0432\u043b\u0435\u043d\u0430 \u0441\u0442\u0430\u043d\u0434\u0430\u0440\u0442\u043d\u0430\u044f \u0442\u0435\u043c\u0430 " + id);
        }
        themes.set("balance-version", (Object)2);
        return true;
    }

    static List<Map<?, ?>> standardWaves(JavaPlugin plugin, String theme) {
        try {
            YamlConfiguration defaults = Migrations.resource(plugin, "themes");
            String path = "themes." + theme + ".waves";
            if (!defaults.contains(path)) {
                throw new IllegalArgumentException("\u041d\u0435\u0442 \u0441\u0442\u0430\u043d\u0434\u0430\u0440\u0442\u043d\u043e\u0439 \u0442\u0435\u043c\u044b " + theme);
            }
            return defaults.getMapList(path);
        }
        catch (RuntimeException ex) {
            throw ex;
        }
        catch (Exception ex) {
            throw new IllegalStateException("\u041d\u0435 \u0443\u0434\u0430\u043b\u043e\u0441\u044c \u0437\u0430\u0433\u0440\u0443\u0437\u0438\u0442\u044c \u0441\u0442\u0430\u043d\u0434\u0430\u0440\u0442\u043d\u044b\u0435 \u0432\u043e\u043b\u043d\u044b", ex);
        }
    }

    private static boolean mergeMissing(ConfigurationSection target, ConfigurationSection defaults) {
        boolean changed = false;
        for (String key : defaults.getKeys(false)) {
            if (defaults.isConfigurationSection(key)) {
                if (!target.contains(key)) {
                    target.createSection(key);
                    changed = true;
                } else if (!target.isConfigurationSection(key)) {
                    String parent = target.getCurrentPath();
                    String path = parent == null || parent.isEmpty() ? key : parent + "." + key;
                    throw new IllegalArgumentException("config.yml: " + path + " \u0434\u043e\u043b\u0436\u0435\u043d \u0431\u044b\u0442\u044c YAML-\u0440\u0430\u0437\u0434\u0435\u043b\u043e\u043c");
                }
                changed |= Migrations.mergeMissing(Objects.requireNonNull(target.getConfigurationSection(key)), Objects.requireNonNull(defaults.getConfigurationSection(key)));
                continue;
            }
            if (target.contains(key)) continue;
            target.set(key, Configs.plain(defaults.get(key)));
            changed = true;
        }
        return changed;
    }

    private static boolean completeDatabase(YamlConfiguration main, YamlConfiguration defaults, int previousSchema) {
        if (main.contains("database") && !main.isConfigurationSection("database")) {
            throw new IllegalArgumentException("config.yml: database \u0434\u043e\u043b\u0436\u0435\u043d \u0431\u044b\u0442\u044c YAML-\u0440\u0430\u0437\u0434\u0435\u043b\u043e\u043c \u0441 \u043f\u043e\u043b\u044f\u043c\u0438 type, host, port, database, username, password; \u043f\u0440\u0438\u043c\u0435\u0440: database.type: MARIADB");
        }
        boolean changed = false;
        ConfigurationSection database = Objects.requireNonNull(defaults.getConfigurationSection("database"));
        for (String key : database.getKeys(false)) {
            String path = "database." + key;
            if (main.contains(path)) continue;
            main.set(path, database.get(key));
            changed = true;
        }
        String original = main.getString("database.type", "");
        String type = original.strip().toUpperCase(Locale.ROOT);
        if (type.equals("MYSQL") || previousSchema < 3 && type.equals("SQLITE")) {
            type = "MARIADB";
        }
        if (type.equals("MARIADB") && !type.equals(original)) {
            main.set("database.type", (Object)type);
            changed = true;
        }
        return changed;
    }

    private static YamlConfiguration resource(JavaPlugin plugin, String name) throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        try (InputStream in = Objects.requireNonNull(plugin.getResource(name + ".yml"));){
            yaml.load((Reader)new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        return yaml;
    }
}

