/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.Bukkit
 *  org.bukkit.HeightMap
 *  org.bukkit.Location
 *  org.bukkit.Material
 *  org.bukkit.World
 *  org.bukkit.World$Environment
 *  org.bukkit.block.Block
 *  org.bukkit.entity.Player
 *  org.bukkit.plugin.Plugin
 *  org.bukkit.plugin.java.JavaPlugin
 */
package org.jamhardcore.dungeons;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.jamhardcore.dungeons.Engine;
import org.jamhardcore.dungeons.Json;
import org.jamhardcore.dungeons.Model;

public final class PlacementService
implements AutoCloseable {
    private final JavaPlugin plugin;
    private final Engine engine;
    private final Set<String> pending = new HashSet<String>();
    private final Set<CompletableFuture<Model.Session>> requests = new HashSet<CompletableFuture<Model.Session>>();
    private final Random random = new Random();
    private boolean closed;

    PlacementService(JavaPlugin plugin, Engine engine) {
        this.plugin = plugin;
        this.engine = engine;
    }

    public CompletableFuture<Model.Session> spawn(String id, String site, boolean test, String actor) {
        Model.Arena arena = this.engine.c.arenas.get(id);
        if (arena == null) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("\u041d\u0435\u0442 \u0430\u0440\u0435\u043d\u044b " + id));
        }
        boolean automatic = PlacementService.automatic(arena, site);
        if (!automatic) {
            try {
                return CompletableFuture.completedFuture(this.engine.spawn(id, "static".equalsIgnoreCase(site) ? null : site, test, actor));
            }
            catch (Exception ex2) {
                return CompletableFuture.failedFuture(ex2);
            }
        }
        if (!arena.template) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("\u0414\u043b\u044f \u0441\u043b\u0443\u0447\u0430\u0439\u043d\u043e\u0433\u043e \u0440\u0430\u0437\u043c\u0435\u0449\u0435\u043d\u0438\u044f \u0432\u044b\u0431\u0435\u0440\u0438\u0442\u0435 \u0441\u0445\u0435\u043c\u0443 \u0438\u043b\u0438 \u0441\u043e\u0445\u0440\u0430\u043d\u0438\u0442\u0435 \u0448\u0430\u0431\u043b\u043e\u043d"));
        }
        if (this.pending.contains(id) || this.engine.sessions.containsKey(id)) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("\u0410\u0440\u0435\u043d\u0430 \u0443\u0436\u0435 \u0440\u0430\u0437\u043c\u0435\u0449\u0430\u0435\u0442\u0441\u044f \u0438\u043b\u0438 \u0430\u043a\u0442\u0438\u0432\u043d\u0430"));
        }
        if (this.engine.sessions.size() + this.pending.size() >= this.engine.c.limit("placed-events")) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("\u041b\u0438\u043c\u0438\u0442 \u0440\u0430\u0437\u043c\u0435\u0449\u0451\u043d\u043d\u044b\u0445 \u0434\u0430\u043d\u0436\u0435\u0439"));
        }
        try {
            this.engine.checkSpawn(arena, test);
        }
        catch (Exception ex3) {
            return CompletableFuture.failedFuture(ex3);
        }
        CompletableFuture<Model.Session> result = new CompletableFuture<Model.Session>();
        this.pending.add(id);
        this.requests.add(result);
        result.whenComplete((s, ex) -> {
            this.pending.remove(id);
            this.requests.remove(result);
        });
        Model.Arena snapshot = Json.copy(arena, Model.Arena.class);
        this.attempt(snapshot, test, actor, result, 0, "\u043d\u0435\u0442 \u043f\u043e\u0434\u0445\u043e\u0434\u044f\u0449\u0435\u0439 \u0442\u0435\u0440\u0440\u0438\u0442\u043e\u0440\u0438\u0438");
        return result;
    }

    static boolean automatic(Model.Arena arena, String site) {
        return "random".equalsIgnoreCase(site) || site == null && arena.template && arena.placement != Model.Placement.STATIC;
    }

    private void fail(Model.Arena arena, CompletableFuture<Model.Session> result, Exception error) {
        if (error instanceof IllegalArgumentException) {
            result.completeExceptionally(error);
            return;
        }
        this.plugin.getLogger().log(Level.SEVERE, "\u041e\u0448\u0438\u0431\u043a\u0430 \u0440\u0430\u0437\u043c\u0435\u0449\u0435\u043d\u0438\u044f \u0430\u0440\u0435\u043d\u044b " + arena.id, error);
        result.completeExceptionally(new IllegalStateException("\u041e\u0448\u0438\u0431\u043a\u0430 \u0440\u0430\u0437\u043c\u0435\u0449\u0435\u043d\u0438\u044f " + arena.id + " (" + error.getClass().getSimpleName() + "). \u041f\u043e\u0434\u0440\u043e\u0431\u043d\u043e\u0441\u0442\u0438 \u0437\u0430\u043f\u0438\u0441\u0430\u043d\u044b \u0432 \u043a\u043e\u043d\u0441\u043e\u043b\u044c \u0441\u0435\u0440\u0432\u0435\u0440\u0430.", error));
    }

    private void attempt(Model.Arena arena, boolean test, String actor, CompletableFuture<Model.Session> result, int n, String last) {
        if (this.closed || result.isDone()) {
            return;
        }
        if (n >= this.engine.c.main().getInt("random-placement.attempts", 64)) {
            result.completeExceptionally(new IllegalStateException("\u041d\u0435 \u043d\u0430\u0439\u0434\u0435\u043d\u043e \u043c\u0435\u0441\u0442\u043e \u0434\u043b\u044f " + arena.id + " \u0437\u0430 " + n + " \u043f\u043e\u043f\u044b\u0442\u043e\u043a: " + last + ". \u041f\u0440\u043e\u0432\u0435\u0440\u044f\u044e\u0442\u0441\u044f \u0441\u043b\u0443\u0447\u0430\u0439\u043d\u044b\u0435 \u043a\u043e\u043e\u0440\u0434\u0438\u043d\u0430\u0442\u044b, \u0430 \u043d\u0435 \u0442\u043e\u0447\u043a\u0430 A. \u0414\u043b\u044f \u0432\u0441\u0442\u0430\u0432\u043a\u0438 \u0432 A: /jd schematic paste " + arena.id));
            return;
        }
        try {
            List selected = arena.worlds.isEmpty() ? this.engine.c.main().getStringList("random-placement.worlds") : arena.worlds;
            List excluded = this.engine.c.main().getStringList("random-placement.excluded-worlds");
            List<World> worlds = Bukkit.getWorlds().stream().filter(w -> !excluded.contains(w.getName()) && (selected.isEmpty() ? w.getEnvironment() == World.Environment.NORMAL : selected.contains(w.getName()))).toList();
            if (worlds.isEmpty()) {
                throw new IllegalArgumentException("\u041d\u0435\u0442 \u0437\u0430\u0433\u0440\u0443\u0436\u0435\u043d\u043d\u044b\u0445 \u043c\u0438\u0440\u043e\u0432 \u0434\u043b\u044f \u0440\u0430\u0437\u043c\u0435\u0449\u0435\u043d\u0438\u044f");
            }
            World world = worlds.get(this.random.nextInt(worlds.size()));
            int min = this.engine.c.main().getInt("random-placement.min-radius", 500);
            int max = this.engine.c.main().getInt("random-placement.max-radius", 10000);
            int[] offset = PlacementService.sample(this.random, min, max);
            int x = world.getSpawnLocation().getBlockX() + offset[0];
            int z = world.getSpawnLocation().getBlockZ() + offset[1];
            Model.Box shape = arena.box();
            int width = shape.x2() - shape.x1() + 1;
            int length = shape.z2() - shape.z1() + 1;
            if (!world.getWorldBorder().isInside(new Location(world, (double)x, 64.0, (double)z)) || !world.getWorldBorder().isInside(new Location(world, (double)(x + width), 64.0, (double)(z + length)))) {
                this.retry(arena, test, actor, result, n, "\u0433\u0440\u0430\u043d\u0438\u0446\u0430 \u043c\u0438\u0440\u0430");
                return;
            }
            double playerDistance = this.engine.c.main().getDouble("random-placement.player-distance", 128.0);
            for (Player player : world.getPlayers()) {
                if (!(Math.hypot(player.getX() - (double)x, player.getZ() - (double)z) < playerDistance + (double)Math.max(width, length))) continue;
                this.retry(arena, test, actor, result, n, "\u0440\u044f\u0434\u043e\u043c \u0438\u0433\u0440\u043e\u043a");
                return;
            }
            ArrayList<CompletableFuture> chunks = new ArrayList<CompletableFuture>();
            for (int cx = x - 16 >> 4; cx <= x + width + 16 >> 4; ++cx) {
                for (int cz = z - 16 >> 4; cz <= z + length + 16 >> 4; ++cz) {
                    chunks.add(world.getChunkAtAsync(cx, cz, true));
                }
            }
            CompletableFuture.allOf((CompletableFuture[])chunks.toArray(CompletableFuture[]::new)).whenComplete((unused, error) -> {
                if (this.closed) {
                    return;
                }
                Bukkit.getScheduler().runTask((Plugin)this.plugin, () -> {
                    if (this.closed || result.isDone()) {
                        return;
                    }
                    try {
                        if (error != null) {
                            throw new IllegalStateException("\u043e\u0448\u0438\u0431\u043a\u0430 \u0437\u0430\u0433\u0440\u0443\u0437\u043a\u0438 \u0447\u0430\u043d\u043a\u043e\u0432", (Throwable)error);
                        }
                        Model.Point anchor = this.candidate(arena, world, x, z);
                        Model.Session session = this.engine.spawnAt(arena.id, null, test, actor, anchor);
                        result.complete(session);
                    }
                    catch (Rejected ex) {
                        this.retry(arena, test, actor, result, n, ex.getMessage());
                    }
                    catch (Exception ex) {
                        this.fail(arena, result, ex);
                    }
                });
            });
        }
        catch (Exception ex) {
            this.fail(arena, result, ex);
        }
    }

    private void retry(Model.Arena a, boolean test, String actor, CompletableFuture<Model.Session> result, int n, String reason) {
        if (!this.closed) {
            Bukkit.getScheduler().runTaskLater((Plugin)this.plugin, () -> this.attempt(a, test, actor, result, n + 1, reason), 1L);
        }
    }

    static int[] sample(Random random, int min, int max) {
        if (min < 0 || max < min || max > 29900000) {
            throw new IllegalArgumentException("\u041d\u0435\u043a\u043e\u0440\u0440\u0435\u043a\u0442\u043d\u044b\u0439 \u0440\u0430\u0434\u0438\u0443\u0441 \u0440\u0430\u0437\u043c\u0435\u0449\u0435\u043d\u0438\u044f");
        }
        double angle = random.nextDouble() * Math.PI * 2.0;
        double r = Math.sqrt((double)min * (double)min + random.nextDouble() * ((double)max * (double)max - (double)min * (double)min));
        return new int[]{(int)Math.round(Math.cos(angle) * r), (int)Math.round(Math.sin(angle) * r)};
    }

    Model.Point candidate(Model.Arena a, World world, int x, int z) throws Exception {
        int y;
        Model.Box original = a.box();
        int width = original.x2() - original.x1() + 1;
        int height = original.y2() - original.y1() + 1;
        int length = original.z2() - original.z1() + 1;
        int low = Integer.MAX_VALUE;
        int high = Integer.MIN_VALUE;
        int dry = 0;
        int samples = 0;
        for (int dx : PlacementService.axisSamples(width)) {
            for (int dz : PlacementService.axisSamples(length)) {
                int y2 = world.getHighestBlockYAt(x + dx, z + dz, HeightMap.MOTION_BLOCKING_NO_LEAVES);
                Block ground = world.getBlockAt(x + dx, y2, z + dz);
                low = Math.min(low, y2);
                high = Math.max(high, y2);
                ++samples;
                if (!ground.getType().isSolid() || !PlacementService.natural(ground.getType())) continue;
                ++dry;
            }
        }
        switch (a.placement) {
            case AIR: {
                int min = this.engine.c.main().getInt("random-placement.air-min-height", 24);
                int max = this.engine.c.main().getInt("random-placement.air-max-height", 48);
                y = high + min + this.random.nextInt(max - min + 1);
                break;
            }
            case WATER: 
            case UNDERWATER: {
                int surfaceLow = Integer.MAX_VALUE;
                int surfaceHigh = Integer.MIN_VALUE;
                int bottomHigh = Integer.MIN_VALUE;
                for (int dx : PlacementService.axisSamples(width)) {
                    for (int dz : PlacementService.axisSamples(length)) {
                        int surface = world.getHighestBlockYAt(x + dx, z + dz, HeightMap.WORLD_SURFACE);
                        if (world.getBlockAt(x + dx, surface, z + dz).getType() != Material.WATER) {
                            throw new Rejected("\u0432\u0435\u0441\u044c \u0433\u0430\u0431\u0430\u0440\u0438\u0442 \u043f\u043e\u0441\u0442\u0440\u043e\u0439\u043a\u0438 \u0434\u043e\u043b\u0436\u0435\u043d \u043d\u0430\u0445\u043e\u0434\u0438\u0442\u044c\u0441\u044f \u043d\u0430\u0434 \u0432\u043e\u0434\u043e\u0451\u043c\u043e\u043c");
                        }
                        surfaceLow = Math.min(surfaceLow, surface);
                        surfaceHigh = Math.max(surfaceHigh, surface);
                        bottomHigh = Math.max(bottomHigh, world.getHighestBlockYAt(x + dx, z + dz, HeightMap.OCEAN_FLOOR));
                    }
                }
                if (surfaceHigh != surfaceLow) {
                    throw new Rejected("\u043d\u0435\u0440\u043e\u0432\u043d\u0430\u044f \u043f\u043e\u0432\u0435\u0440\u0445\u043d\u043e\u0441\u0442\u044c \u0432\u043e\u0434\u043e\u0451\u043c\u0430");
                }
                if (a.placement == Model.Placement.WATER) {
                    int depth = Math.min(height - 1, this.engine.c.main().getInt("random-placement.water-submerge-blocks", 2));
                    y = surfaceLow - depth;
                    if (y > bottomHigh) break;
                    throw new Rejected("\u0432\u043e\u0434\u043e\u0451\u043c \u0441\u043b\u0438\u0448\u043a\u043e\u043c \u043c\u0435\u043b\u043a\u0438\u0439 \u0434\u043b\u044f \u043a\u043e\u0440\u043f\u0443\u0441\u0430");
                }
                y = bottomHigh + 1;
                if (y + height - 1 < surfaceLow) break;
                throw new Rejected("\u0432\u043e\u0434\u043e\u0451\u043c \u0441\u043b\u0438\u0448\u043a\u043e\u043c \u043c\u0435\u043b\u043a\u0438\u0439 \u0434\u043b\u044f \u043f\u043e\u0434\u0432\u043e\u0434\u043d\u043e\u0433\u043e \u0434\u0430\u043d\u0436\u0430");
            }
            default: {
                if (high - low > this.engine.c.main().getInt("random-placement.max-ground-slope", 4) || (double)dry / (double)samples < this.engine.c.main().getDouble("random-placement.min-ground-coverage", 0.8)) {
                    throw new Rejected("\u043d\u0435\u0440\u043e\u0432\u043d\u0430\u044f \u0438\u043b\u0438 \u043d\u0435\u043f\u043e\u0434\u0445\u043e\u0434\u044f\u0449\u0430\u044f \u043f\u043e\u0432\u0435\u0440\u0445\u043d\u043e\u0441\u0442\u044c (\u043f\u0435\u0440\u0435\u043f\u0430\u0434 " + (high - low) + ", \u0434\u043e\u043f\u0443\u0441\u0442\u0438\u043c\u043e " + this.engine.c.main().getInt("random-placement.max-ground-slope", 4) + "; \u0441\u0443\u0445\u0430\u044f \u043e\u043f\u043e\u0440\u0430 " + Math.round(100.0 * (double)dry / (double)samples) + "%)");
                }
                y = high + 1;
            }
        }
        Model.Point anchor = new Model.Point(world.getName(), x, y, z, 0.0f, 0.0f);
        Model.Arena shifted = a.at(anchor);
        Model.Box box = shifted.box();
        this.engine.structures.check(box);
        if (Math.hypot((double)x - world.getSpawnLocation().getX(), (double)z - world.getSpawnLocation().getZ()) < (double)(this.engine.c.main().getInt("random-placement.spawn-distance", 256) + Math.max(width, length))) {
            throw new Rejected("\u0441\u043b\u0438\u0448\u043a\u043e\u043c \u0431\u043b\u0438\u0437\u043a\u043e \u043a \u0441\u043f\u0430\u0432\u043d\u0443");
        }
        Model.Box protectedBox = this.engine.c.protectionBox(shifted);
        if (this.engine.regions.overlaps(protectedBox, a.id)) {
            throw new Rejected("\u0437\u0430\u043d\u044f\u0442\u043e \u043f\u0440\u0438\u0432\u0430\u0442\u043e\u043c");
        }
        for (Model.Session s : this.engine.sessions.values()) {
            if (!protectedBox.intersects(this.engine.c.protectionBox(s.arena))) continue;
            throw new Rejected("\u043f\u0435\u0440\u0435\u0441\u0435\u0447\u0435\u043d\u0438\u0435 \u0434\u0430\u043d\u0436\u0435\u0439");
        }
        for (Model.Arena source : this.engine.c.arenas.values()) {
            if (source.pos1 == null || source.pos2 == null || !source.pos1.world().equals(source.pos2.world()) || !protectedBox.intersects(this.engine.c.protectionBox(source))) continue;
            throw new Rejected("\u043f\u0435\u0440\u0435\u0441\u0435\u0447\u0435\u043d\u0438\u0435 \u0438\u0441\u0445\u043e\u0434\u043d\u043e\u0439 \u043f\u043e\u0441\u0442\u0440\u043e\u0439\u043a\u0438 " + source.id);
        }
        for (int bx = box.x1(); bx <= box.x2(); ++bx) {
            for (int bz = box.z1(); bz <= box.z2(); ++bz) {
                for (int by = box.y1(); by <= box.y2(); ++by) {
                    Material material = world.getBlockAt(bx, by, bz).getType();
                    if (a.placement == Model.Placement.AIR && !material.isAir()) {
                        throw new Rejected("\u0432\u043e\u0437\u0434\u0443\u0448\u043d\u044b\u0439 \u043e\u0431\u044a\u0451\u043c \u0437\u0430\u043d\u044f\u0442");
                    }
                    if (PlacementService.natural(material)) continue;
                    throw new Rejected("\u043e\u0431\u043d\u0430\u0440\u0443\u0436\u0435\u043d\u044b \u0431\u043b\u043e\u043a\u0438 \u043f\u043e\u0441\u0442\u0440\u043e\u0439\u043a\u0438");
                }
            }
        }
        if (PlacementService.exitNear(shifted.box()) == null) {
            throw new Rejected("\u043d\u0435\u0442 \u0431\u0435\u0437\u043e\u043f\u0430\u0441\u043d\u043e\u0433\u043e \u0441\u0443\u0445\u043e\u0433\u043e \u0432\u044b\u0445\u043e\u0434\u0430 \u0440\u044f\u0434\u043e\u043c");
        }
        return anchor;
    }

    static int[] axisSamples(int size) {
        TreeSet<Integer> values = new TreeSet<Integer>();
        for (int i = 0; i < size; i += 4) {
            values.add(i);
        }
        values.add(size - 1);
        return values.stream().mapToInt(Integer::intValue).toArray();
    }

    static boolean natural(Material m) {
        String n = m.name();
        return m.isAir() || Set.of("STONE", "DIRT", "GRASS_BLOCK", "SAND", "RED_SAND", "GRAVEL", "CLAY", "WATER", "SNOW", "SNOW_BLOCK", "ICE", "PACKED_ICE", "BLUE_ICE", "DEEPSLATE", "TUFF", "CALCITE", "GRANITE", "ANDESITE", "DIORITE", "PODZOL", "MYCELIUM", "MUD", "MUDDY_MANGROVE_ROOTS", "MANGROVE_ROOTS", "ROOTED_DIRT", "COARSE_DIRT", "BEDROCK", "DRIPSTONE_BLOCK", "SHORT_GRASS", "TALL_GRASS", "FERN", "LARGE_FERN", "DEAD_BUSH", "SEAGRASS", "TALL_SEAGRASS", "KELP", "KELP_PLANT", "LILY_PAD", "VINE", "CACTUS", "SUGAR_CANE", "MOSS_BLOCK", "MOSS_CARPET", "NETHERRACK", "SOUL_SAND", "SOUL_SOIL", "BASALT", "BLACKSTONE", "END_STONE").contains(n) || n.endsWith("_ORE") || n.endsWith("_LEAVES") || n.endsWith("_LOG") || n.endsWith("_FLOWER") || n.endsWith("_CORAL") || n.endsWith("_CORAL_BLOCK") || n.endsWith("_CORAL_FAN");
    }

    static Model.Point exitNear(Model.Box box) {
        World world = Bukkit.getWorld((String)box.world());
        if (world == null) {
            return null;
        }
        for (int distance : new int[]{4, 8, 12, 16}) {
            for (int[] offset : new int[][]{{box.x2() + distance, box.z1()}, {box.x1() - distance, box.z1()}, {box.x1(), box.z2() + distance}, {box.x1(), box.z1() - distance}}) {
                int x = offset[0];
                int z = offset[1];
                int y = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES) + 1;
                if (y >= world.getMaxHeight() - 1) continue;
                Location l = new Location(world, (double)x + 0.5, (double)y, (double)z + 0.5);
                Material floor = l.clone().add(0.0, -1.0, 0.0).getBlock().getType();
                if (!world.getWorldBorder().isInside(l) || !floor.isSolid() || Set.of(Material.MAGMA_BLOCK, Material.CAMPFIRE, Material.SOUL_CAMPFIRE, Material.CACTUS).contains(floor) || !l.getBlock().isPassable() || l.getBlock().isLiquid() || !l.clone().add(0.0, 1.0, 0.0).getBlock().isPassable() || box.contains(l)) continue;
                return Model.Point.of(l);
            }
        }
        return null;
    }

    @Override
    public void close() {
        this.closed = true;
        for (CompletableFuture<Model.Session> request : new ArrayList<CompletableFuture<Model.Session>>(this.requests)) {
            request.completeExceptionally(new IllegalStateException("\u0421\u0435\u0440\u0432\u0435\u0440 \u043e\u0441\u0442\u0430\u043d\u0430\u0432\u043b\u0438\u0432\u0430\u0435\u0442\u0441\u044f"));
        }
        this.pending.clear();
    }

    static final class Rejected
    extends IllegalArgumentException {
        Rejected(String message) {
            super(message);
        }
    }
}

