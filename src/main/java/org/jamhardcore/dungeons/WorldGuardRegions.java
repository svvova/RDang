/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.Bukkit
 *  org.bukkit.World
 *  org.bukkit.plugin.Plugin
 */
package org.jamhardcore.dungeons;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.jamhardcore.dungeons.Configs;
import org.jamhardcore.dungeons.Model;

public final class WorldGuardRegions {
    private final Configs c;
    private static final List<String> DENIED_FLAGS = List.of("BUILD", "BLOCK_BREAK", "BLOCK_PLACE", "TNT", "CREEPER_EXPLOSION", "OTHER_EXPLOSION", "FIRE_SPREAD", "LAVA_FIRE");

    WorldGuardRegions(Configs c) {
        this.c = c;
    }

    private boolean enabled() {
        return this.c.main().getBoolean("worldguard.enabled", true);
    }

    private Class<?> type(String name) throws Exception {
        Plugin p = Bukkit.getPluginManager().getPlugin("WorldGuard");
        if (p == null || !p.isEnabled()) {
            throw new IllegalStateException("\u0423\u0441\u0442\u0430\u043d\u043e\u0432\u0438\u0442\u0435 WorldGuard 7 \u0438 WorldEdit/FAWE \u0434\u043b\u044f \u0430\u0432\u0442\u043e\u043c\u0430\u0442\u0438\u0447\u0435\u0441\u043a\u0438\u0445 \u043f\u0440\u0438\u0432\u0430\u0442\u043e\u0432");
        }
        return Class.forName(name, true, p.getClass().getClassLoader());
    }

    private Object manager(String world) throws Exception {
        World w = Bukkit.getWorld((String)world);
        if (w == null) {
            throw new IllegalArgumentException("\u041c\u0438\u0440 \u043d\u0435 \u0437\u0430\u0433\u0440\u0443\u0436\u0435\u043d: " + world);
        }
        Object wg = this.type("com.sk89q.worldguard.WorldGuard").getMethod("getInstance", new Class[0]).invoke(null, new Object[0]);
        Object platform = wg.getClass().getMethod("getPlatform", new Class[0]).invoke(wg, new Object[0]);
        Object container = platform.getClass().getMethod("getRegionContainer", new Class[0]).invoke(platform, new Object[0]);
        Object adapted = this.type("com.sk89q.worldedit.bukkit.BukkitAdapter").getMethod("adapt", World.class).invoke(null, w);
        Object manager = container.getClass().getMethod("get", this.type("com.sk89q.worldedit.world.World")).invoke(container, adapted);
        if (manager == null) {
            throw new IllegalStateException("WorldGuard \u043d\u0435 \u0437\u0430\u0433\u0440\u0443\u0437\u0438\u043b \u0440\u0435\u0433\u0438\u043e\u043d\u044b \u043c\u0438\u0440\u0430 " + world);
        }
        return manager;
    }

    static String definitionId(String id) {
        return "jd_arena_" + id.toLowerCase(Locale.ROOT);
    }

    static String eventId(Model.Session s) {
        return "jd_event_" + s.id.replace("-", "");
    }

    public void sync(Model.Arena arena) throws Exception {
        if (!this.enabled() || arena.pos1 == null || arena.pos2 == null) {
            return;
        }
        if (this.overlaps(this.c.protectionBox(arena), arena.id)) {
            throw new IllegalArgumentException("\u0420\u0435\u0433\u0438\u043e\u043d \u043f\u0435\u0440\u0435\u0441\u0435\u043a\u0430\u0435\u0442 \u0434\u0440\u0443\u0433\u043e\u0439 \u043f\u0440\u0438\u0432\u0430\u0442");
        }
        this.repair(arena);
    }

    public void repair(Model.Arena arena) throws Exception {
        if (this.enabled()) {
            this.define(WorldGuardRegions.definitionId(arena.id), this.c.protectionBox(arena), arena.kind);
        }
    }

    public void validate(String world) throws Exception {
        if (this.enabled()) {
            this.manager(world);
        }
    }

    public void create(Model.Session s) throws Exception {
        if (!this.enabled()) {
            return;
        }
        s.regionId = WorldGuardRegions.eventId(s);
        this.define(s.regionId, this.c.protectionBox(s.arena), s.kind);
    }

    public void refreshDefinitions() throws Exception {
        if (!this.enabled()) {
            return;
        }
        ArrayList<String> failures = new ArrayList<String>();
        for (Model.Arena arena : this.c.arenas.values()) {
            if (arena.pos1 == null || arena.pos2 == null || !arena.pos1.world().equals(arena.pos2.world())) continue;
            try {
                this.repair(arena);
            }
            catch (Exception error) {
                failures.add(arena.id + ": " + WorldGuardRegions.reason(error));
            }
        }
        if (!failures.isEmpty()) {
            throw new IllegalStateException(String.join((CharSequence)"; ", failures));
        }
    }

    public void deleteDefinition(Model.Arena arena) throws Exception {
        if (!this.enabled() || arena.pos1 == null) {
            return;
        }
        this.remove(arena.pos1.world(), WorldGuardRegions.definitionId(arena.id));
    }

    public void delete(Model.Session s) throws Exception {
        if (s.regionId != null) {
            this.remove(s.arena.box().world(), s.regionId);
        }
    }

    private void remove(String world, String id) throws Exception {
        if (!id.startsWith("jd_arena_") && !id.startsWith("jd_event_")) {
            throw new IllegalArgumentException("\u0427\u0443\u0436\u043e\u0439 ID \u0440\u0435\u0433\u0438\u043e\u043d\u0430");
        }
        Object manager = this.manager(world);
        Class<?> strategy = this.type("com.sk89q.worldguard.protection.managers.RemovalStrategy");
        @SuppressWarnings({"rawtypes", "unchecked"})
        Object unset = Enum.valueOf((Class<Enum>) (Class<?>) strategy, "UNSET_PARENT_IN_CHILDREN");
        manager.getClass().getMethod("removeRegion", String.class, strategy).invoke(manager, id, unset);
        manager.getClass().getMethod("saveChanges", new Class[0]).invoke(manager, new Object[0]);
    }

    private void define(String id, Model.Box box, Model.Kind kind) throws Exception {
        Class<?> vector = this.type("com.sk89q.worldedit.math.BlockVector3");
        Method at = vector.getMethod("at", Integer.TYPE, Integer.TYPE, Integer.TYPE);
        Class<?> regionType = this.type("com.sk89q.worldguard.protection.regions.ProtectedRegion");
        Object region = this.type("com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion").getConstructor(String.class, vector, vector).newInstance(id, at.invoke(null, box.x1(), box.y1(), box.z1()), at.invoke(null, box.x2(), box.y2(), box.z2()));
        regionType.getMethod("setPriority", Integer.TYPE).invoke(region, this.c.main().getInt("worldguard.priority", 100));
        for (String flag : DENIED_FLAGS) {
            this.set(region, flag, false);
        }
        for (String flag : List.of("CHEST_ACCESS", "USE", "INTERACT", "ENTRY")) {
            this.set(region, flag, true);
        }
        this.set(region, "MOB_SPAWNING", kind == Model.Kind.PVE);
        this.set(region, "PVP", kind == Model.Kind.PVP);
        Object manager = this.manager(box.world());
        manager.getClass().getMethod("addRegion", regionType).invoke(manager, region);
        manager.getClass().getMethod("saveChanges", new Class[0]).invoke(manager, new Object[0]);
        this.verify(id, box, kind);
    }

    private void verify(String id, Model.Box expected, Model.Kind kind) throws Exception {
        Object flag;
        Object manager = this.manager(expected.world());
        Object region = manager.getClass().getMethod("getRegion", String.class).invoke(manager, id);
        if (region == null) {
            throw new IllegalStateException("WorldGuard: \u0440\u0435\u0433\u0438\u043e\u043d " + id + " \u043e\u0442\u0441\u0443\u0442\u0441\u0442\u0432\u0443\u0435\u0442");
        }
        Object min = region.getClass().getMethod("getMinimumPoint", new Class[0]).invoke(region, new Object[0]);
        Object max = region.getClass().getMethod("getMaximumPoint", new Class[0]).invoke(region, new Object[0]);
        Model.Box actual = new Model.Box(expected.world(), WorldGuardRegions.coord(min, "X"), WorldGuardRegions.coord(min, "Y"), WorldGuardRegions.coord(min, "Z"), WorldGuardRegions.coord(max, "X"), WorldGuardRegions.coord(max, "Y"), WorldGuardRegions.coord(max, "Z"));
        if (!expected.equals(actual)) {
            throw new IllegalStateException("WorldGuard: \u0433\u0440\u0430\u043d\u0438\u0446\u044b " + id + " \u043d\u0435 \u0441\u043e\u0432\u043f\u0430\u043b\u0438");
        }
        Method getFlag = this.type("com.sk89q.worldguard.protection.regions.ProtectedRegion").getMethod("getFlag", this.type("com.sk89q.worldguard.protection.flags.Flag"));
        for (String name : DENIED_FLAGS) {
            Object flag2 = this.type("com.sk89q.worldguard.protection.flags.Flags").getField(name).get(null);
            if ("DENY".equals(Objects.toString(getFlag.invoke(region, flag2)))) continue;
            throw new IllegalStateException("WorldGuard: " + id + " " + name + " \u043d\u0435 DENY");
        }
        if (!(kind == Model.Kind.PVP ? "ALLOW" : "DENY").equals(Objects.toString(getFlag.invoke(region, flag = this.type("com.sk89q.worldguard.protection.flags.Flags").getField("PVP").get(null))))) {
            throw new IllegalStateException("WorldGuard: \u043d\u0435\u0432\u0435\u0440\u043d\u044b\u0439 \u0444\u043b\u0430\u0433 PVP " + id);
        }
        int priority = ((Number)region.getClass().getMethod("getPriority", new Class[0]).invoke(region, new Object[0])).intValue();
        if (priority != this.c.main().getInt("worldguard.priority", 100)) {
            throw new IllegalStateException("WorldGuard: \u043d\u0435\u0432\u0435\u0440\u043d\u044b\u0439 \u043f\u0440\u0438\u043e\u0440\u0438\u0442\u0435\u0442 " + id);
        }
    }

    public String status(String id, Model.Arena arena) {
        if (!this.enabled()) {
            return "WorldGuard: \u043e\u0442\u043a\u043b\u044e\u0447\u0451\u043d \u0432 config.yml; \u0432\u0441\u0442\u0440\u043e\u0435\u043d\u043d\u0430\u044f \u0437\u0430\u0449\u0438\u0442\u0430 \u0432\u043a\u043b\u044e\u0447\u0435\u043d\u0430";
        }
        try {
            this.verify(id, this.c.protectionBox(arena), arena.kind);
            return "WorldGuard: OK " + id + "; \u0433\u0440\u0430\u043d\u0438\u0446\u044b \u0438 \u0437\u0430\u0449\u0438\u0442\u043d\u044b\u0435 \u0444\u043b\u0430\u0433\u0438 \u043f\u0440\u043e\u0432\u0435\u0440\u0435\u043d\u044b";
        }
        catch (Exception error) {
            return "WorldGuard: \u041e\u0428\u0418\u0411\u041a\u0410 " + id + ": " + WorldGuardRegions.reason(error);
        }
    }

    static String reason(Throwable error) {
        while (error.getCause() != null && error.getCause() != error) {
            error = error.getCause();
        }
        return Objects.toString(error.getMessage(), error.getClass().getSimpleName());
    }

    private void set(Object region, String name, boolean allow) throws Exception {
        Object flag = this.type("com.sk89q.worldguard.protection.flags.Flags").getField(name).get(null);
        Class<?> state = this.type("com.sk89q.worldguard.protection.flags.StateFlag$State");
        @SuppressWarnings({"rawtypes", "unchecked"})
        Object value = Enum.valueOf((Class<Enum>) (Class<?>) state, allow ? "ALLOW" : "DENY");
        this.type("com.sk89q.worldguard.protection.regions.ProtectedRegion").getMethod("setFlag", this.type("com.sk89q.worldguard.protection.flags.Flag"), Object.class).invoke(region, flag, value);
    }

    public boolean overlaps(Model.Box box, String allowedDefinition) throws Exception {
        if (!this.enabled()) {
            return false;
        }
        Object manager = this.manager(box.world());
        Object result = manager.getClass().getMethod("getRegions", new Class[0]).invoke(manager, new Object[0]);
        for (Map.Entry<?, ?> entry : ((Map<?, ?>)result).entrySet()) {
            String id = entry.getKey().toString();
            if (id.equals("__global__") || id.equals(WorldGuardRegions.definitionId(allowedDefinition))) continue;
            Object region = entry.getValue();
            Object a = region.getClass().getMethod("getMinimumPoint", new Class[0]).invoke(region, new Object[0]);
            Object b = region.getClass().getMethod("getMaximumPoint", new Class[0]).invoke(region, new Object[0]);
            Model.Box other = new Model.Box(box.world(), WorldGuardRegions.coord(a, "X"), WorldGuardRegions.coord(a, "Y"), WorldGuardRegions.coord(a, "Z"), WorldGuardRegions.coord(b, "X"), WorldGuardRegions.coord(b, "Y"), WorldGuardRegions.coord(b, "Z"));
            if (!box.intersects(other)) continue;
            return true;
        }
        return false;
    }

    private static int coord(Object vector, String axis) throws Exception {
        try {
            return ((Number)vector.getClass().getMethod("getBlock" + axis, new Class[0]).invoke(vector, new Object[0])).intValue();
        }
        catch (NoSuchMethodException ex) {
            return ((Number)vector.getClass().getMethod(axis.toLowerCase(Locale.ROOT), new Class[0]).invoke(vector, new Object[0])).intValue();
        }
    }
}

