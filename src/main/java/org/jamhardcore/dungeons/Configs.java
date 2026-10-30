/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.Bukkit
 *  org.bukkit.Material
 *  org.bukkit.NamespacedKey
 *  org.bukkit.Registry
 *  org.bukkit.World
 *  org.bukkit.configuration.ConfigurationSection
 *  org.bukkit.configuration.file.YamlConfiguration
 *  org.bukkit.enchantments.Enchantment
 *  org.bukkit.entity.EntityType
 *  org.bukkit.plugin.java.JavaPlugin
 */
package org.jamhardcore.dungeons;

import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EntityType;
import org.bukkit.plugin.java.JavaPlugin;
import org.jamhardcore.dungeons.Json;
import org.jamhardcore.dungeons.Migrations;
import org.jamhardcore.dungeons.Model;
import org.jamhardcore.dungeons.Rules;
import org.jamhardcore.dungeons.ThemeCatalog;

public final class Configs {
    private final JavaPlugin plugin;
    private Map<String, YamlConfiguration> files = new LinkedHashMap<String, YamlConfiguration>();
    public Map<String, Model.Theme> themes = Map.of();
    public Map<String, Model.Arena> arenas = Map.of();
    public Model.Pool pve;
    public Model.Pool pvp;
    private static final List<String> FILES = List.of("config", "themes", "rewards", "messages", "arenas");

    public Configs(JavaPlugin p) throws Exception {
        this.plugin = p;
        for (String f : FILES) {
            if (p.getDataFolder().toPath().resolve(f + ".yml").toFile().exists()) continue;
            p.saveResource(f + ".yml", false);
        }
        this.reload();
    }

    public YamlConfiguration main() {
        return this.files.get("config");
    }

    public YamlConfiguration file(String name) {
        if (!FILES.contains(name)) {
            throw new IllegalArgumentException("\u0424\u0430\u0439\u043b: " + String.valueOf(FILES));
        }
        return this.files.get(name);
    }

    public int timer(String name) {
        return this.main().getInt("timers." + name);
    }

    public int limit(String name) {
        return this.main().getInt("limits." + name);
    }

    public Model.Box protectionBox(Model.Arena arena) {
        Model.Box b = arena.box();
        int horizontal = this.main().getInt("protection.horizontal-padding", 3);
        int vertical = this.main().getInt("protection.vertical-padding", 8);
        if (arena.schematic != null || arena.template) {
            vertical = horizontal = this.main().getInt("schematics.protection-padding", 4);
        }
        World world = Bukkit.getWorld((String)b.world());
        return new Model.Box(b.world(), b.x1() - horizontal, Math.max(world == null ? Integer.MIN_VALUE : world.getMinHeight(), b.y1() - vertical), b.z1() - horizontal, b.x2() + horizontal, Math.min(world == null ? Integer.MAX_VALUE : world.getMaxHeight() - 1, b.y2() + vertical), b.z2() + horizontal);
    }

    List<Map<?, ?>> standardWaves(String theme) {
        return Migrations.standardWaves(this.plugin, theme);
    }

    public synchronized void reload() throws Exception {
        LinkedHashMap<String, YamlConfiguration> fresh = new LinkedHashMap<String, YamlConfiguration>();
        LinkedHashMap<String, String> before = new LinkedHashMap<String, String>();
        for (String f : FILES) {
            YamlConfiguration y = new YamlConfiguration();
            y.load(this.plugin.getDataFolder().toPath().resolve(f + ".yml").toFile());
            fresh.put(f, y);
            before.put(f, y.saveToString());
        }
        boolean migrated = Migrations.upgrade(this.plugin, fresh);
        this.install(fresh);
        if (migrated) {
            for (Map.Entry entry : fresh.entrySet()) {
                String text = ((YamlConfiguration)entry.getValue()).saveToString();
                if (text.equals(before.get(entry.getKey()))) continue;
                Configs.write(this.plugin.getDataFolder().toPath().resolve((String)entry.getKey() + ".yml"), text);
            }
            this.plugin.getLogger().info("\u041d\u0430\u0441\u0442\u0440\u043e\u0439\u043a\u0438 JamDungeons \u043e\u0431\u043d\u043e\u0432\u043b\u0435\u043d\u044b. \u041f\u0440\u0435\u0434\u044b\u0434\u0443\u0449\u0438\u0435 \u0432\u0435\u0440\u0441\u0438\u0438 \u0438\u0437\u043c\u0435\u043d\u0451\u043d\u043d\u044b\u0445 \u0444\u0430\u0439\u043b\u043e\u0432: *.bak");
        }
    }

    private void install(Map<String, YamlConfiguration> candidate) {
        YamlConfiguration c = candidate.get("config");
        if (c.getInt("schematics.protection-padding", 4) < 0 || c.getInt("schematics.protection-padding", 4) > 64) {
            throw new IllegalArgumentException("schematics.protection-padding: \u043e\u0442 0 \u0434\u043e 64 \u0431\u043b\u043e\u043a\u043e\u0432");
        }
        ZoneId.of(c.getString("timezone", "Europe/Moscow"));
        if (!List.of("VAULT", "SERVICE", "COMMAND").contains(c.getString("integrations.economy.mode"))) {
            throw new IllegalArgumentException("\u042d\u043a\u043e\u043d\u043e\u043c\u0438\u043a\u0430: VAULT, SERVICE \u0438\u043b\u0438 COMMAND");
        }
        if (!"MARIADB".equals(c.getString("database.type"))) {
            throw new IllegalArgumentException("config.yml: \u0443\u0441\u0442\u0430\u043d\u043e\u0432\u0438\u0442\u0435 database.type: MARIADB. JamDungeons \u0445\u0440\u0430\u043d\u0438\u0442 \u0434\u0430\u043d\u043d\u044b\u0435 \u0442\u043e\u043b\u044c\u043a\u043e \u0432 MariaDB; \u0434\u043b\u044f \u0441\u0442\u0430\u0440\u043e\u0433\u043e state.sqlite \u043f\u0440\u0435\u0434\u0443\u0441\u043c\u043e\u0442\u0440\u0435\u043d \u043e\u0444\u043b\u0430\u0439\u043d-\u043f\u0435\u0440\u0435\u043d\u043e\u0441.");
        }
        if (!c.getString("database.database", "").matches("[A-Za-z0-9_]+") || c.getInt("database.port") < 1 || c.getInt("database.port") > 65535) {
            throw new IllegalArgumentException("\u041d\u0435\u043a\u043e\u0440\u0440\u0435\u043a\u0442\u043d\u044b\u0435 \u043f\u0430\u0440\u0430\u043c\u0435\u0442\u0440\u044b MariaDB");
        }
        int duration = c.getInt("timers.pve-seconds");
        int interval = c.getInt("timers.wave-interval-seconds");
        if (duration != 900 || interval != 180) {
            throw new IllegalArgumentException("PvE: 5 \u0432\u043e\u043b\u043d, \u0434\u043e 180 \u0441\u0435\u043a\u0443\u043d\u0434 \u043d\u0430 \u0432\u043e\u043b\u043d\u0443 (\u0437\u0430\u0447\u0438\u0441\u0442\u043a\u0430 \u0443\u0441\u043a\u043e\u0440\u044f\u0435\u0442)");
        }
        double distance = c.getDouble("protection.pve-pushback-distance", 3.0);
        double strength = c.getDouble("protection.pve-pushback-strength", 0.85);
        if (!Double.isFinite(distance) || distance < 0.5 || distance > 16.0 || !Double.isFinite(strength) || strength < 0.1 || strength > 2.0 || c.getInt("protection.owner-exit-grace-seconds", 5) < 1) {
            throw new IllegalArgumentException("\u041d\u0435\u043a\u043e\u0440\u0440\u0435\u043a\u0442\u043d\u044b\u0435 \u043d\u0430\u0441\u0442\u0440\u043e\u0439\u043a\u0438 \u043e\u0442\u0442\u0430\u043b\u043a\u0438\u0432\u0430\u043d\u0438\u044f/\u0432\u0440\u0435\u043c\u0435\u043d\u0438 \u0432\u043e\u0437\u0432\u0440\u0430\u0442\u0430");
        }
        if (c.getInt("protection.horizontal-padding", 3) < 0 || c.getInt("protection.horizontal-padding", 3) > 32 || c.getInt("protection.vertical-padding", 8) < 0 || c.getInt("protection.vertical-padding", 8) > 64) {
            throw new IllegalArgumentException("\u0417\u0430\u043f\u0430\u0441 \u043f\u0440\u0438\u0432\u0430\u0442\u0430: \u043f\u043e \u0433\u043e\u0440\u0438\u0437\u043e\u043d\u0442\u0430\u043b\u0438 0..32, \u043f\u043e \u0432\u044b\u0441\u043e\u0442\u0435 0..64");
        }
        if (!List.of("disable", "trust", "verify-ca", "verify-full").contains(c.getString("database.ssl-mode", "disable"))) {
            throw new IllegalArgumentException("MariaDB ssl-mode: disable, trust, verify-ca \u0438\u043b\u0438 verify-full");
        }
        if (c.getDouble("random-placement.min-ground-coverage") < 0.0 || c.getDouble("random-placement.min-ground-coverage") > 1.0 || c.getInt("random-placement.max-ground-slope") < 0) {
            throw new IllegalArgumentException("\u041d\u0435\u043a\u043e\u0440\u0440\u0435\u043a\u0442\u043d\u044b\u0435 \u0443\u0441\u043b\u043e\u0432\u0438\u044f \u043f\u043e\u0432\u0435\u0440\u0445\u043d\u043e\u0441\u0442\u0438");
        }
        if (c.getStringList("zombie-equipment.armor").size() != 4) {
            throw new IllegalArgumentException("\u0423\u043a\u0430\u0436\u0438\u0442\u0435 4 \u0447\u0430\u0441\u0442\u0438 \u0431\u0440\u043e\u043d\u0438 \u0437\u043e\u043c\u0431\u0438: \u0448\u043b\u0435\u043c, \u043d\u0430\u0433\u0440\u0443\u0434\u043d\u0438\u043a, \u043f\u043e\u043d\u043e\u0436\u0438, \u0431\u043e\u0442\u0438\u043d\u043a\u0438");
        }
        if (c.getInt("combat.cave-spider-poison-seconds") < 0 || c.getInt("combat.cave-spider-poison-seconds") > 60 || c.getInt("combat.cave-spider-poison-level") < 1 || c.getInt("combat.cave-spider-poison-level") > 5) {
            throw new IllegalArgumentException("\u042f\u0434 \u043f\u0430\u0443\u043a\u0430: \u0434\u043b\u0438\u0442\u0435\u043b\u044c\u043d\u043e\u0441\u0442\u044c 0..60 \u0441\u0435\u043a\u0443\u043d\u0434, \u0443\u0440\u043e\u0432\u0435\u043d\u044c 1..5");
        }
        if (Material.matchMaterial((String)c.getString("coin-item.material", "")) == null || NamespacedKey.fromString((String)c.getString("coin-item.model", "")) == null) {
            throw new IllegalArgumentException("\u041d\u0435\u043a\u043e\u0440\u0440\u0435\u043a\u0442\u043d\u044b\u0439 \u043c\u0430\u0442\u0435\u0440\u0438\u0430\u043b \u0438\u043b\u0438 \u043c\u043e\u0434\u0435\u043b\u044c \u043c\u043e\u043d\u0435\u0442\u043a\u0438");
        }
        int radiusMin = c.getInt("random-placement.min-radius");
        int radiusMax = c.getInt("random-placement.max-radius");
        if (radiusMin < 0 || radiusMax < radiusMin || radiusMax > 29900000 || c.getInt("random-placement.attempts") < 1 || c.getInt("random-placement.attempts") > 512) {
            throw new IllegalArgumentException("\u041d\u0435\u043a\u043e\u0440\u0440\u0435\u043a\u0442\u043d\u044b\u0435 \u043e\u0433\u0440\u0430\u043d\u0438\u0447\u0435\u043d\u0438\u044f \u0441\u043b\u0443\u0447\u0430\u0439\u043d\u043e\u0433\u043e \u0440\u0430\u0437\u043c\u0435\u0449\u0435\u043d\u0438\u044f");
        }
        if (c.getInt("random-placement.air-min-height") < 1 || c.getInt("random-placement.air-max-height") < c.getInt("random-placement.air-min-height")) {
            throw new IllegalArgumentException("\u041d\u0435\u043a\u043e\u0440\u0440\u0435\u043a\u0442\u043d\u0430\u044f \u0432\u044b\u0441\u043e\u0442\u0430 \u0432\u043e\u0437\u0434\u0443\u0448\u043d\u044b\u0445 \u0430\u0440\u0435\u043d");
        }
        if (c.getInt("random-placement.water-submerge-blocks", 2) < 0 || c.getInt("random-placement.water-submerge-blocks", 2) > 64) {
            throw new IllegalArgumentException("random-placement.water-submerge-blocks: \u043e\u0442 0 \u0434\u043e 64");
        }
        double fraction = c.getDouble("zombie-equipment.armored-fraction", 0.5);
        if (!Double.isFinite(fraction) || fraction <= 0.0 || fraction > 1.0) {
            throw new IllegalArgumentException("\u0414\u043e\u043b\u044f \u044d\u043a\u0438\u043f\u0438\u0440\u043e\u0432\u0430\u043d\u043d\u044b\u0445 \u0437\u043e\u043c\u0431\u0438: \u0431\u043e\u043b\u044c\u0448\u0435 0 \u0438 \u043d\u0435 \u0431\u043e\u043b\u044c\u0448\u0435 1");
        }
        for (String key : List.of("armor", "weapons")) {
            for (String mat : c.getStringList("zombie-equipment." + key)) {
                if (Material.matchMaterial((String)mat) != null) continue;
                throw new IllegalArgumentException("\u041d\u0435\u0438\u0437\u0432\u0435\u0441\u0442\u043d\u044b\u0439 \u043c\u0430\u0442\u0435\u0440\u0438\u0430\u043b \u044d\u043a\u0438\u043f\u0438\u0440\u043e\u0432\u043a\u0438: " + mat);
            }
        }
        for (String k : List.of("pvp-seconds", "pve-available-seconds", "pvp-available-seconds", "reconnect-seconds", "winner-claim-seconds", "loot-seconds", "cleanup-seconds")) {
            if (c.getInt("timers." + k) >= 1) continue;
            throw new IllegalArgumentException("timers." + k + " \u0434\u043e\u043b\u0436\u0435\u043d \u0431\u044b\u0442\u044c \u043f\u043e\u043b\u043e\u0436\u0438\u0442\u0435\u043b\u044c\u043d\u044b\u043c");
        }
        for (String k : List.of("active-pve", "placed-events", "mobs-per-event", "total-mobs", "region-volume", "snapshot-chunks", "simultaneous-pvp", "max-points")) {
            if (c.getInt("limits." + k) >= 1) continue;
            throw new IllegalArgumentException("limits." + k + " \u0434\u043e\u043b\u0436\u0435\u043d \u0431\u044b\u0442\u044c \u043f\u043e\u043b\u043e\u0436\u0438\u0442\u0435\u043b\u044c\u043d\u044b\u043c");
        }
        LocalTime.ofSecondOfDay(LocalTime.parse(c.getString("scheduling.pve-window-start")).toSecondOfDay());
        if (!LocalTime.parse(c.getString("scheduling.pve-window-end")).isAfter(LocalTime.parse(c.getString("scheduling.pve-window-start")))) {
            throw new IllegalArgumentException("\u041e\u043a\u043d\u043e PvE \u0434\u043e\u043b\u0436\u043d\u043e \u0437\u0430\u043a\u0430\u043d\u0447\u0438\u0432\u0430\u0442\u044c\u0441\u044f \u043f\u043e\u0437\u0436\u0435 \u043d\u0430\u0447\u0430\u043b\u0430");
        }
        int perDay = c.getInt("scheduling.pve-per-day");
        int gap = c.getInt("scheduling.pve-gap-minutes");
        long span = Duration.between(LocalTime.parse(c.getString("scheduling.pve-window-start")), LocalTime.parse(c.getString("scheduling.pve-window-end"))).toMinutes();
        if (perDay < 0 || perDay > 24 || gap < 0 || (long)Math.max(0, perDay - 1) * (long)gap > span) {
            throw new IllegalArgumentException("\u0427\u0430\u0441\u0442\u043e\u0442\u0430 PvE \u043d\u0435 \u043f\u043e\u043c\u0435\u0449\u0430\u0435\u0442\u0441\u044f \u0432 \u043e\u043a\u043d\u043e \u0440\u0430\u0441\u043f\u0438\u0441\u0430\u043d\u0438\u044f");
        }
        LocalTime.parse(c.getString("scheduling.pvp-time"));
        for (String day : c.getStringList("scheduling.pvp-days")) {
            DayOfWeek.valueOf(day.toUpperCase(Locale.ROOT));
        }
        YamlConfiguration y = candidate.get("themes");
        LinkedHashMap<Object, Model.MobSpec> presets = new LinkedHashMap<Object, Model.MobSpec>();
        for (Object key : Configs.section((ConfigurationSection)y, "mob-presets").getKeys(false)) {
            if (!Rules.validId((String)key)) {
                throw new IllegalArgumentException("\u041d\u0435\u043a\u043e\u0440\u0440\u0435\u043a\u0442\u043d\u044b\u0439 id \u043c\u043e\u0431\u0430 " + (String)key);
            }
            ConfigurationSection m2 = Configs.section((ConfigurationSection)y, "mob-presets." + (String)key);
            Model.MobSpec spec = new Model.MobSpec();
            spec.entity = m2.getString("entity");
            spec.name = m2.getString("name", (String)key);
            spec.weapon = m2.getString("weapon");
            spec.forceMelee = m2.getBoolean("force-melee");
            spec.zombieEquipment = m2.getBoolean("zombie-equipment", "ZOMBIE".equals(spec.entity));
            EntityType entity = EntityType.valueOf((String)spec.entity);
            if (!entity.isAlive() || !entity.isSpawnable() || List.of(EntityType.ENDER_DRAGON, EntityType.WITHER, EntityType.PLAYER, EntityType.CREEPER, EntityType.CAMEL).contains(entity)) {
                throw new IllegalArgumentException("\u041d\u0435\u043f\u043e\u0434\u0434\u0435\u0440\u0436\u0438\u0432\u0430\u0435\u043c\u044b\u0439 \u043c\u043e\u0431 " + (String)key);
            }
            if (spec.weapon != null && Material.matchMaterial((String)spec.weapon) == null) {
                throw new IllegalArgumentException("\u041d\u0435\u0438\u0437\u0432\u0435\u0441\u0442\u043d\u043e\u0435 \u043e\u0440\u0443\u0436\u0438\u0435 " + spec.weapon);
            }
            presets.put(key, spec);
        }
        LinkedHashMap<String, Model.Theme> parsedThemes = new LinkedHashMap<String, Model.Theme>();
        for (String key : Configs.section((ConfigurationSection)y, "themes").getKeys(false)) {
            if (!Rules.validId(key)) {
                throw new IllegalArgumentException("\u041d\u0435\u043a\u043e\u0440\u0440\u0435\u043a\u0442\u043d\u044b\u0439 id \u0442\u0435\u043c\u044b " + key);
            }
            Model.Theme t = new Model.Theme();
            t.id = key;
            t.name = y.getString("themes." + key + ".name", key);
            t.biomes = y.getStringList("themes." + key + ".biomes");
            List<String> allowed = ThemeCatalog.allowed(key);
            t.mobs = new LinkedHashMap<String, Model.MobSpec>();
            for (Map.Entry preset : presets.entrySet()) {
                if (allowed != null && !allowed.contains(preset.getKey())) continue;
                t.mobs.put((String)preset.getKey(), (Model.MobSpec)preset.getValue());
            }
            for (Map<?, ?> row : y.getMapList("themes." + key + ".waves")) {
                LinkedHashMap<String, Integer> wave = new LinkedHashMap<String, Integer>();
                for (Map.Entry<?, ?> entry : row.entrySet()) {
                    String mob = entry.getKey().toString();
                    int n = ((Number)entry.getValue()).intValue();
                    if (!t.mobs.containsKey(mob) || n < 1 || n > 256) {
                        throw new IllegalArgumentException("\u041d\u0435\u043a\u043e\u0440\u0440\u0435\u043a\u0442\u043d\u0430\u044f \u0432\u043e\u043b\u043d\u0430: " + key + " / " + mob);
                    }
                    wave.put(mob, n);
                }
                if (wave.isEmpty()) {
                    throw new IllegalArgumentException("\u041f\u0443\u0441\u0442\u0430\u044f \u0432\u043e\u043b\u043d\u0430 " + key);
                }
                t.waves.add(wave);
            }
            if (t.waves.size() != 5) {
                throw new IllegalArgumentException("\u0414\u043b\u044f \u0442\u0435\u043c\u044b " + key + " \u0442\u0440\u0435\u0431\u0443\u0435\u0442\u0441\u044f \u0440\u043e\u0432\u043d\u043e 5 \u0432\u043e\u043b\u043d");
            }
            int total = t.waves.stream().flatMap(m -> m.values().stream()).mapToInt(Integer::intValue).sum();
            if (total > c.getInt("limits.mobs-per-event")) {
                throw new IllegalArgumentException(key + ": \u043f\u043e\u043b\u043d\u044b\u0439 \u0441\u043e\u0441\u0442\u0430\u0432 \u043f\u0440\u0435\u0432\u044b\u0448\u0430\u0435\u0442 \u043b\u0438\u043c\u0438\u0442 \u043c\u043e\u0431\u043e\u0432");
            }
            parsedThemes.put(key, t);
        }
        if (parsedThemes.isEmpty()) {
            throw new IllegalArgumentException("\u041d\u0435\u0442 PvE-\u0442\u0435\u043c");
        }
        Model.Pool a = Configs.pool(candidate.get("rewards"), "pve");
        Model.Pool b = Configs.pool(candidate.get("rewards"), "pvp");
        LinkedHashMap<String, Model.Arena> parsedArenas = new LinkedHashMap<String, Model.Arena>();
        ConfigurationSection arenaRoot = candidate.get("arenas").getConfigurationSection("arenas");
        if (arenaRoot != null) {
            for (String id : arenaRoot.getKeys(false)) {
                if (!Rules.validId(id)) {
                    throw new IllegalArgumentException("\u041d\u0435\u043a\u043e\u0440\u0440\u0435\u043a\u0442\u043d\u044b\u0439 id \u0430\u0440\u0435\u043d\u044b " + id);
                }
                Model.Arena arena = Json.decode(Json.encode(Configs.plain(arenaRoot.get(id))), Model.Arena.class);
                arena.id = id;
                if (arena.kind == null) {
                    throw new IllegalArgumentException("\u0422\u0438\u043f \u0430\u0440\u0435\u043d\u044b \u0434\u043e\u043b\u0436\u0435\u043d \u0431\u044b\u0442\u044c PVE \u0438\u043b\u0438 PVP");
                }
                if (arena.points == null) {
                    arena.points = new ArrayList<Model.Spawn>();
                }
                if (arena.sites == null) {
                    arena.sites = new LinkedHashMap<String, Model.Point>();
                }
                if (arena.placement == null) {
                    arena.placement = Model.Placement.GROUND;
                }
                if (arena.worlds == null) {
                    arena.worlds = new ArrayList<String>();
                }
                if (arena.biomes == null) {
                    arena.biomes = new ArrayList<String>();
                }
                if (arena.name == null) {
                    arena.name = id;
                }
                ConfigurationSection rawSec = arenaRoot.getConfigurationSection(id);
                if (rawSec != null) {
                    if (rawSec.contains("offset-x")) arena.offsetX = rawSec.getInt("offset-x");
                    if (rawSec.contains("offset-y")) arena.offsetY = rawSec.getInt("offset-y");
                    if (rawSec.contains("offset-z")) arena.offsetZ = rawSec.getInt("offset-z");
                    if (rawSec.contains("spawn-offset.x")) arena.offsetX = rawSec.getInt("spawn-offset.x");
                    if (rawSec.contains("spawn-offset.y")) arena.offsetY = rawSec.getInt("spawn-offset.y");
                    if (rawSec.contains("spawn-offset.z")) arena.offsetZ = rawSec.getInt("spawn-offset.z");
                    if (rawSec.contains("spawn-offset-y")) arena.offsetY = rawSec.getInt("spawn-offset-y");
                }
                if (arena.kind == Model.Kind.PVE && !parsedThemes.containsKey(arena.theme)) {
                    throw new IllegalArgumentException("\u041d\u0435\u0442 \u0442\u0435\u043c\u044b " + arena.theme);
                }
                if (arena.points.size() > c.getInt("limits.max-points")) {
                    throw new IllegalArgumentException("\u0421\u043b\u0438\u0448\u043a\u043e\u043c \u043c\u043d\u043e\u0433\u043e \u0442\u043e\u0447\u0435\u043a " + id);
                }
                HashSet<String> pointIds = new HashSet<String>();
                for (Model.Spawn point : arena.points) {
                    if (Rules.validId(point.id) && pointIds.add(point.id) && point.point != null && point.wave >= 0 && point.wave <= 5 && point.mob != null && (point.mob.equals("any") || presets.containsKey(point.mob)) && (arena.kind != Model.Kind.PVE || point.mob.equals("any") || ((Model.Theme)parsedThemes.get((Object)arena.theme)).mobs.containsKey(point.mob))) continue;
                    throw new IllegalArgumentException("\u041d\u0435\u043a\u043e\u0440\u0440\u0435\u043a\u0442\u043d\u0430\u044f \u0442\u043e\u0447\u043a\u0430 " + id);
                }
                ArrayList<Model.Point> arrayList = new ArrayList<Model.Point>();
                Collections.addAll(arrayList, arena.pos1, arena.pos2, arena.chest, arena.exit);
                arena.points.forEach(pt -> arrayList.add(pt.point));
                arrayList.addAll(arena.sites.values());
                for (Model.Point point : arrayList) {
                    if (point == null || point.world() != null && Double.isFinite(point.x()) && Double.isFinite(point.y()) && Double.isFinite(point.z()) && !(Math.abs(point.x()) > 3.0E7) && !(Math.abs(point.z()) > 3.0E7)) continue;
                    throw new IllegalArgumentException("\u041d\u0435\u043a\u043e\u0440\u0440\u0435\u043a\u0442\u043d\u044b\u0435 \u043a\u043e\u043e\u0440\u0434\u0438\u043d\u0430\u0442\u044b " + id);
                }
                parsedArenas.put(id, arena);
            }
        }
        this.files = candidate;
        this.themes = parsedThemes;
        this.arenas = parsedArenas;
        this.pve = a;
        this.pvp = b;
    }

    private static Model.Pool pool(YamlConfiguration y, String path) {
        ConfigurationSection s = Configs.section((ConfigurationSection)y, path);
        Model.Pool p = new Model.Pool();
        p.minCoins = s.getInt("coins.min");
        p.maxCoins = s.getInt("coins.max");
        p.picks = s.getInt("item-picks");
        p.caseChance = s.getDouble("case-chance", 100.0);
        if (p.minCoins < 0 || p.maxCoins < p.minCoins || p.maxCoins > 100000000) {
            throw new IllegalArgumentException("\u041d\u0435\u043a\u043e\u0440\u0440\u0435\u043a\u0442\u043d\u044b\u0439 \u0434\u0438\u0430\u043f\u0430\u0437\u043e\u043d \u043c\u043e\u043d\u0435\u0442 " + path);
        }
        HashSet<String> ids = new HashSet<String>();
        for (Map row : s.getMapList("items")) {
            Map m;
            List list;
            Model.ItemChoice v = new Model.ItemChoice();
            v.id = Objects.toString(row.get("id"), "");
            v.material = Objects.toString(row.get("material"), "");
            v.min = Configs.num(row, "min");
            v.max = Configs.num(row, "max");
            v.weight = Configs.dbl(row, "weight");
            if (!(Rules.validId(v.id) && ids.add(v.id) && Material.matchMaterial((String)v.material) != null && Material.matchMaterial((String)v.material).isItem() && v.min >= 1 && v.max >= v.min && v.max <= 4096)) {
                throw new IllegalArgumentException("\u041d\u0435\u043a\u043e\u0440\u0440\u0435\u043a\u0442\u043d\u0430\u044f \u043d\u0430\u0433\u0440\u0430\u0434\u0430 " + path + " / " + v.id);
            }
            Rules.positiveWeight(v.weight);
            Object object = row.get("amounts");
            if (object instanceof List) {
                list = (List)object;
                for (Object o : list) {
                    m = (Map)o;
                    int n = Configs.num(m, "amount");
                    double w = Configs.dbl(m, "weight");
                    Rules.positiveWeight(w);
                    if (n < v.min || n > v.max) {
                        throw new IllegalArgumentException("\u041a\u043e\u043b\u0438\u0447\u0435\u0441\u0442\u0432\u043e \u0432\u043d\u0435 \u0434\u0438\u0430\u043f\u0430\u0437\u043e\u043d\u0430 " + v.id);
                    }
                    v.amounts.add(new Model.Amount(n, w));
                }
            }
            if ((object = row.get("books")) instanceof List) {
                list = (List)object;
                for (Object o : list) {
                    m = (Map)o;
                    String key = Objects.toString(m.get("enchantment"), "");
                    int level = Configs.num(m, "level");
                    if (Registry.ENCHANTMENT.get(NamespacedKey.minecraft((String)key)) == null || level < 1 || level > 10) {
                        throw new IllegalArgumentException("\u041d\u0435\u043a\u043e\u0440\u0440\u0435\u043a\u0442\u043d\u043e\u0435 \u0437\u0430\u0447\u0430\u0440\u043e\u0432\u0430\u043d\u0438\u0435 " + key);
                    }
                    v.books.add(new Model.Book(key, level));
                }
            }
            p.items.add(v);
        }
        if (p.picks < 1 || p.picks > p.items.size() || p.picks > 36) {
            throw new IllegalArgumentException("\u041d\u0435\u043a\u043e\u0440\u0440\u0435\u043a\u0442\u043d\u043e\u0435 \u043a\u043e\u043b\u0438\u0447\u0435\u0441\u0442\u0432\u043e \u043a\u0430\u0442\u0435\u0433\u043e\u0440\u0438\u0439 " + path);
        }
        for (Map row : s.getMapList("cases")) {
            String id = Objects.toString(row.get("id"), "");
            double w = Configs.dbl(row, "weight");
            if (!List.of("donation", "epic", "resource").contains(id)) {
                throw new IllegalArgumentException("\u041d\u0435\u0438\u0437\u0432\u0435\u0441\u0442\u043d\u0430\u044f \u0440\u043e\u043b\u044c \u043a\u0435\u0439\u0441\u0430 " + id);
            }
            Rules.positiveWeight(w);
            p.cases.add(new Model.CaseChoice(id, w));
        }
        if (path.equals("pvp") && !p.cases.isEmpty() && (p.cases.size() != 3 || p.cases.stream().map(Model.CaseChoice::id).distinct().count() != 3L)) {
            throw new IllegalArgumentException("PvP \u0442\u0440\u0435\u0431\u0443\u0435\u0442 \u0442\u0440\u0438 \u0440\u0430\u0437\u043d\u044b\u0445 \u0440\u043e\u043b\u0438 \u043a\u0435\u0439\u0441\u043e\u0432");
        }
        Rules.positiveWeight(p.items.stream().mapToDouble(i -> i.weight).sum());
        if (!p.cases.isEmpty()) {
            Rules.positiveWeight(p.cases.stream().mapToDouble(Model.CaseChoice::weight).sum());
        }
        ConfigurationSection petSec = s.getConfigurationSection("pets");
        if (petSec != null) {
            p.pets.enabled = petSec.getBoolean("enabled", true);
            p.pets.legendaryChance = petSec.getDouble("legendary-chance", 10.0);
            p.pets.epicChance = petSec.getDouble("epic-chance", 25.0);
            p.pets.rareChance = petSec.getDouble("rare-chance", 75.0);
            p.pets.command = petSec.getString("command", "petadmin giveegg {player} {tier}");
        }
        for (Model.ItemChoice item : p.items) {
            if (!item.books.isEmpty() && !item.material.equals("ENCHANTED_BOOK")) {
                throw new IllegalArgumentException("books \u0434\u043e\u043f\u0443\u0441\u0442\u0438\u043c \u0442\u043e\u043b\u044c\u043a\u043e \u0434\u043b\u044f ENCHANTED_BOOK");
            }
            for (Model.Book book : item.books) {
                if (book.level() <= Objects.requireNonNull((Enchantment)Registry.ENCHANTMENT.get(NamespacedKey.minecraft((String)book.enchantment()))).getMaxLevel()) continue;
                throw new IllegalArgumentException("\u0421\u043b\u0438\u0448\u043a\u043e\u043c \u0432\u044b\u0441\u043e\u043a\u0438\u0439 \u0443\u0440\u043e\u0432\u0435\u043d\u044c \u0437\u0430\u0447\u0430\u0440\u043e\u0432\u0430\u043d\u0438\u044f " + book.enchantment());
            }
        }
        return p;
    }

    private static int num(Map<?, ?> m, String k) {
        Object obj = m.get(k);
        if (!(obj instanceof Number)) {
            throw new IllegalArgumentException("\u0422\u0440\u0435\u0431\u0443\u0435\u0442\u0441\u044f \u0447\u0438\u0441\u043b\u043e " + k);
        }
        Number n = (Number)obj;
        return n.intValue();
    }

    private static double dbl(Map<?, ?> m, String k) {
        Object obj = m.get(k);
        if (!(obj instanceof Number)) {
            throw new IllegalArgumentException("\u0422\u0440\u0435\u0431\u0443\u0435\u0442\u0441\u044f \u0432\u0435\u0441 " + k);
        }
        Number n = (Number)obj;
        return n.doubleValue();
    }

    private static ConfigurationSection section(ConfigurationSection y, String p) {
        ConfigurationSection c = y.getConfigurationSection(p);
        if (c == null) {
            throw new IllegalArgumentException("\u041e\u0442\u0441\u0443\u0442\u0441\u0442\u0432\u0443\u0435\u0442 \u0440\u0430\u0437\u0434\u0435\u043b " + p);
        }
        return c;
    }

    public static Object plain(Object v) {
        if (v instanceof ConfigurationSection) {
            ConfigurationSection s = (ConfigurationSection)v;
            LinkedHashMap<String, Object> out = new LinkedHashMap<String, Object>();
            for (String key : s.getKeys(false)) {
                out.put(key, Configs.plain(s.get(key)));
            }
            return out;
        }
        if (v instanceof Map) {
            Map m = (Map)v;
            LinkedHashMap out = new LinkedHashMap();
            m.forEach((k, a) -> out.put(k.toString(), Configs.plain(a)));
            return out;
        }
        if (v instanceof List) {
            List l = (List)v;
            return l.stream().map(Configs::plain).toList();
        }
        return v;
    }

    public void edit(String file, Consumer<YamlConfiguration> edit) {
        if (!FILES.contains(file)) {
            throw new IllegalArgumentException("\u041d\u0435\u0438\u0437\u0432\u0435\u0441\u0442\u043d\u044b\u0439 \u0444\u0430\u0439\u043b");
        }
        Map<String, YamlConfiguration> previous = this.files;
        LinkedHashMap<String, YamlConfiguration> next = new LinkedHashMap<String, YamlConfiguration>(this.files);
        YamlConfiguration y = new YamlConfiguration();
        try {
            y.loadFromString(this.files.get(file).saveToString());
            edit.accept(y);
            next.put(file, y);
            this.install(next);
            Configs.write(this.plugin.getDataFolder().toPath().resolve(file + ".yml"), y.saveToString());
        }
        catch (Exception e) {
            this.install(previous);
            throw new IllegalArgumentException(e.getMessage(), e);
        }
    }

    public List<String> themeBiomes(String theme) {
        if (theme == null) return List.of();
        Model.Theme t = this.themes.get(theme);
        if (t != null && t.biomes != null && !t.biomes.isEmpty()) {
            return t.biomes;
        }
        List<String> list = this.main().getStringList("random-placement.theme-biomes." + theme);
        if (!list.isEmpty()) {
            return list;
        }
        return switch (theme.toLowerCase(Locale.ROOT)) {
            case "forest" -> List.of("FOREST", "BIRCH_FOREST", "DARK_FOREST", "FLOWER_FOREST", "CHERRY_GROVE", "OLD_GROWTH_BIRCH_FOREST", "TAIGA", "GROVE");
            case "desert" -> List.of("DESERT", "BADLANDS", "ERODED_BADLANDS", "WOODED_BADLANDS");
            case "mining" -> List.of("PLAINS", "SUNFLOWER_PLAINS", "SAVANNA", "STONY_PEAKS", "WINDSWEPT_HILLS", "MEADOW");
            case "flying" -> List.of("PLAINS", "SUNFLOWER_PLAINS", "MEADOW", "WINDSWEPT_HILLS", "SAVANNA", "JAGGED_PEAKS");
            case "water" -> List.of("OCEAN", "DEEP_OCEAN", "WARM_OCEAN", "LUKEWARM_OCEAN", "COLD_OCEAN", "DEEP_LUKEWARM_OCEAN", "DEEP_COLD_OCEAN");
            case "raiders" -> List.of("PLAINS", "SUNFLOWER_PLAINS", "SAVANNA", "FOREST", "BIRCH_FOREST", "MEADOW");
            default -> List.of();
        };
    }

    public void saveArena(Model.Arena arena) {
        this.edit("arenas", y -> y.set("arenas." + arena.id, Json.GSON.fromJson(Json.encode(arena), Map.class)));
    }

    public void removeArena(String id) {
        this.edit("arenas", y -> y.set("arenas." + id, null));
    }

    public Model.Arena findArena(String idOrName) {
        if (idOrName == null || idOrName.isBlank()) {
            return null;
        }
        String clean = idOrName.trim();
        Model.Arena exact = this.arenas.get(clean);
        if (exact != null) {
            return exact;
        }
        for (Model.Arena candidate : this.arenas.values()) {
            if (candidate.id.equalsIgnoreCase(clean)) {
                return candidate;
            }
        }
        for (Model.Arena candidate : this.arenas.values()) {
            if (candidate.name != null) {
                if (candidate.name.equalsIgnoreCase(clean)) {
                    return candidate;
                }
                String stripped = Messages.stripColor(candidate.name).trim();
                if (stripped.equalsIgnoreCase(clean)
                        || stripped.replace(' ', '_').equalsIgnoreCase(clean)
                        || stripped.equalsIgnoreCase(clean.replace('_', ' '))) {
                    return candidate;
                }
            }
        }
        return null;
    }

    public int[] schematicOffset(String schemFile) {
        if (schemFile == null || schemFile.isBlank()) {
            return new int[]{0, 0, 0};
        }
        String clean = schemFile.trim();
        ConfigurationSection sec = this.main().getConfigurationSection("schematics.offsets." + clean);
        if (sec == null && clean.endsWith(".schem")) {
            sec = this.main().getConfigurationSection("schematics.offsets." + clean.substring(0, clean.length() - 6));
        }
        if (sec == null && !clean.contains(".")) {
            sec = this.main().getConfigurationSection("schematics.offsets." + clean + ".schem");
        }
        if (sec != null) {
            return new int[]{sec.getInt("x", 0), sec.getInt("y", 0), sec.getInt("z", 0)};
        }
        return new int[]{0, 0, 0};
    }

    public void setSchematicOffset(String schemFile, int x, int y, int z) {
        if (schemFile == null || schemFile.isBlank()) return;
        String clean = schemFile.trim();
        this.edit("config", yml -> {
            String path = "schematics.offsets." + clean;
            if (x == 0 && y == 0 && z == 0) {
                yml.set(path, null);
            } else {
                yml.set(path + ".x", x);
                yml.set(path + ".y", y);
                yml.set(path + ".z", z);
            }
        });
    }

    public int[] getEffectiveOffset(Model.Arena arena) {
        if (arena == null) return new int[]{0, 0, 0};
        if (arena.offsetX != 0 || arena.offsetY != 0 || arena.offsetZ != 0) {
            return new int[]{arena.offsetX, arena.offsetY, arena.offsetZ};
        }
        if (arena.schematic != null) {
            return this.schematicOffset(arena.schematic);
        }
        return new int[]{0, 0, 0};
    }

    public static void write(Path path, String text) throws Exception {
        Path tmp = path.resolveSibling(String.valueOf(path.getFileName()) + ".tmp");
        Files.writeString(tmp, (CharSequence)text, StandardCharsets.UTF_8, new OpenOption[0]);
        try (FileChannel ch = FileChannel.open(tmp, StandardOpenOption.WRITE);){
            ch.force(true);
        }
        if (Files.exists(path, new LinkOption[0])) {
            Files.copy(path, path.resolveSibling(String.valueOf(path.getFileName()) + ".bak"), StandardCopyOption.REPLACE_EXISTING);
        }
        try {
            Files.move(tmp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        }
        catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}

