/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.Bukkit
 *  org.bukkit.GameMode
 *  org.bukkit.Location
 *  org.bukkit.World
 *  org.bukkit.block.BlockState
 *  org.bukkit.block.Container
 *  org.bukkit.block.data.BlockData
 *  org.bukkit.block.data.type.Chest
 *  org.bukkit.block.data.type.Chest$Type
 *  org.bukkit.command.CommandSender
 *  org.bukkit.entity.Entity
 *  org.bukkit.entity.LivingEntity
 *  org.bukkit.entity.Mob
 *  org.bukkit.entity.Player
 *  org.bukkit.event.Listener
 *  org.bukkit.plugin.Plugin
 *  org.bukkit.plugin.java.JavaPlugin
 *  org.bukkit.util.BoundingBox
 */
package org.jamhardcore.dungeons;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Chest;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.BoundingBox;
import org.jamhardcore.dungeons.Combat;
import org.jamhardcore.dungeons.Configs;
import org.jamhardcore.dungeons.Integrations;
import org.jamhardcore.dungeons.Json;
import org.jamhardcore.dungeons.Messages;
import org.jamhardcore.dungeons.Model;
import org.jamhardcore.dungeons.PlacementService;
import org.jamhardcore.dungeons.Rewards;
import org.jamhardcore.dungeons.Rules;
import org.jamhardcore.dungeons.Spawner;
import org.jamhardcore.dungeons.Store;
import org.jamhardcore.dungeons.Templates;
import org.jamhardcore.dungeons.WorldGuardRegions;

public final class Engine {
    public final Map<String, Model.Session> sessions = new LinkedHashMap<String, Model.Session>();
    private final Map<UUID, String> buildingEditors = new HashMap<UUID, String>();
    public final JavaPlugin p;
    public final Configs c;
    public final Store db;
    public final Messages msg;
    public final Integrations hooks;
    public final Rewards rewards;
    public final Spawner mobs;
    public final Templates structures;
    public final WorldGuardRegions regions;
    public final CoreProtectHook coreProtect;
    public final PlacementService placement;
    public EventSchedule schedule;
    public record ManualPaste(String id, String world, Model.Box box, String backupPath, long timestamp) {}
    public final java.util.Deque<ManualPaste> manualPastes = new java.util.ArrayDeque<>();
    final Combat combat;
    BiConsumer<Model.Session, Integer> spawnWave;

    void report(String message, Throwable error) {
        this.p.getLogger().log(Level.SEVERE, message, error);
    }

    public Engine(JavaPlugin p, Configs c, Store db, Messages msg, Integrations h, Rewards r, Spawner m, Templates t, CoreProtectHook cp) {
        this.p = p;
        this.c = c;
        this.db = db;
        this.msg = msg;
        this.hooks = h;
        this.rewards = r;
        this.mobs = m;
        this.structures = t;
        this.coreProtect = cp;
        this.regions = new WorldGuardRegions(c);
        this.placement = new PlacementService(p, this);
        this.combat = new Combat(p, this);
        Bukkit.getPluginManager().registerEvents((Listener)this.combat, (Plugin)p);
        this.spawnWave = (session, wave) -> this.mobs.wave((Model.Session)session, (int)wave, this.totalLiving());
        this.rewards.completion(reward -> {
            for (Model.Session session : new ArrayList<Model.Session>(this.sessions.values())) {
                if (!reward.id.equals(session.rewardId)) continue;
                session.cleanupSeconds = 60;
                this.beginCleanup(session, System.currentTimeMillis());
            }
        });
    }

    public Model.Session byEvent(String id) {
        return this.sessions.values().stream().filter(s -> s.id.equals(id)).findFirst().orElse(null);
    }

    public Model.Session at(Location l) {
        return this.sessions.values().stream().filter(s -> s.arena.playBox().contains(l)).findFirst().orElse(null);
    }

    boolean eventBuildProtected(Location location) {
        return location != null && this.sessions.values().stream().anyMatch(session -> this.c.protectionBox(session.arena).contains(location));
    }

    boolean buildProtected(Location location) {
        if (location == null) {
            return false;
        }
        if (this.eventBuildProtected(location)) {
            return true;
        }
        return this.c.arenas.values().stream().anyMatch(arena -> arena.pos1 != null && arena.pos2 != null && arena.pos1.world().equals(arena.pos2.world()) && this.c.protectionBox((Model.Arena)arena).contains(location));
    }

    boolean editingBuilding(Player player, String arenaId) {
        return arenaId.equals(this.buildingEditors.get(player.getUniqueId()));
    }

    void stopEditingBuilding(Player player) {
        this.buildingEditors.remove(player.getUniqueId());
    }

    void clearBuildingEditors() {
        this.buildingEditors.clear();
    }

    void editBuilding(Player player, Model.Arena arena, boolean enabled) {
        if (!enabled) {
            this.stopEditingBuilding(player);
            return;
        }
        if (player.getGameMode() != GameMode.CREATIVE || !player.hasPermission("jamdungeons.admin") || !player.hasPermission("jamdungeons.bypass")) {
            throw new IllegalArgumentException("\u0420\u0435\u0434\u0430\u043a\u0442\u0438\u0440\u043e\u0432\u0430\u043d\u0438\u0435: Creative \u0438 \u043f\u0440\u0430\u0432\u0430 jamdungeons.admin/bypass");
        }
        Model.Box box = this.c.protectionBox(arena);
        if (this.sessions.containsKey(arena.id) || this.sessions.values().stream().anyMatch(s -> box.intersects(this.c.protectionBox(s.arena)))) {
            throw new IllegalArgumentException("\u0421\u043d\u0430\u0447\u0430\u043b\u0430 \u043e\u0441\u0442\u0430\u043d\u043e\u0432\u0438\u0442\u0435 \u0441\u043e\u0431\u044b\u0442\u0438\u0435: /jd stop " + arena.id);
        }
        this.buildingEditors.put(player.getUniqueId(), arena.id);
    }

    boolean canEditBuilding(Player player, Location location) {
        String id = this.buildingEditors.get(player.getUniqueId());
        if (id == null) {
            return false;
        }
        Model.Arena arena = this.c.arenas.get(id);
        if (arena == null || player.getGameMode() != GameMode.CREATIVE || !player.hasPermission("jamdungeons.admin") || !player.hasPermission("jamdungeons.bypass") || this.sessions.containsKey(id) || this.eventBuildProtected(location) || !this.c.protectionBox(arena).contains(location)) {
            return false;
        }
        return this.c.arenas.values().stream().filter(a -> a.pos1 != null && a.pos2 != null).noneMatch(a -> !a.id.equals(id) && this.c.protectionBox((Model.Arena)a).contains(location));
    }

    public Model.Session repels(Player player, Location location) {
        if (this.bypass(player)) {
            return null;
        }
        double margin = this.c.main().getDouble("protection.pve-pushback-distance", 3.0);
        for (Model.Session s : this.sessions.values()) {
            Model.Box box;
            if (s.kind != Model.Kind.PVE || s.hasPlayer(player) || s.phase != Model.Phase.RUNNING && s.phase != Model.Phase.REWARD && s.phase != Model.Phase.CLAIMED || !(box = s.arena.playBox()).world().equals(location.getWorld().getName()) || !(location.getY() >= (double)box.y1()) || !(location.getY() <= (double)(box.y2() + 1)) || !(Rules.distanceSquared(box, location.getX(), location.getZ()) <= margin * margin)) continue;
            return s;
        }
        return null;
    }

    String version() {
        return this.p.getDescription().getVersion();
    }

    public Model.Session chest(Location l) {
        return this.sessions.values().stream().filter(s -> s.arena.chest.sameBlock(l)).findFirst().orElse(null);
    }

    public boolean bypass(Player player) {
        return player.hasPermission("jamdungeons.bypass") && (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR);
    }

    public boolean permitted(Model.Session s, Player player) {
        return s.test ? player.hasPermission("jamdungeons.admin") && !player.isDead() : this.hooks.canPlay(player);
    }

    public Model.Point findChest(Model.Arena a) {
        if (a == null || a.pos1 == null || a.pos2 == null) {
            return null;
        }
        Model.Box b = a.box();
        World world = Bukkit.getWorld(b.world());
        if (world == null) {
            return null;
        }
        int minX = Math.min(b.x1(), b.x2()) - 1;
        int maxX = Math.max(b.x1(), b.x2()) + 1;
        int minY = Math.max(world.getMinHeight(), Math.min(b.y1(), b.y2()) - 2);
        int maxY = Math.min(world.getMaxHeight() - 1, Math.max(b.y1(), b.y2()) + 2);
        int minZ = Math.min(b.z1(), b.z2()) - 1;
        int maxZ = Math.max(b.z1(), b.z2()) + 1;

        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    Block block = world.getBlockAt(x, y, z);
                    Material mat = block.getType();
                    if (mat == Material.CHEST || mat == Material.TRAPPED_CHEST || mat == Material.BARREL || block.getBlockData() instanceof Chest) {
                        return Model.Point.of(block.getLocation());
                    }
                }
            }
        }
        return null;
    }

    public void ensureContainsChest(Model.Arena a) {
        if (a == null || a.chest == null || a.pos1 == null || a.pos2 == null) return;
        Model.Box b = a.box();
        if (!b.contains(a.chest)) {
            int cx = (int) Math.floor(a.chest.x());
            int cy = (int) Math.floor(a.chest.y());
            int cz = (int) Math.floor(a.chest.z());
            a.pos1 = new Model.Point(b.world(), Math.min(b.x1(), cx), Math.min(b.y1(), cy), Math.min(b.z1(), cz), a.pos1.yaw(), a.pos1.pitch());
            a.pos2 = new Model.Point(b.world(), Math.max(b.x2(), cx), Math.max(b.y2(), cy), Math.max(b.z2(), cz), a.pos2.yaw(), a.pos2.pitch());
            try {
                this.c.saveArena(a);
                this.regions.sync(a);
            } catch (Exception ignored) {}
        }
    }

    public List<String> validate(Model.Arena a, boolean integration) {
        ArrayList<String> issues;
        block9: {
            issues = new ArrayList<String>();
            try {
                this.structures.check(a.box());
                this.regions.validate(a.box().world());
                if (a.schematic != null) {
                    this.structures.load(a.schematic);
                }
                if (a.chest == null) {
                    Model.Point found = this.findChest(a);
                    if (found == null && this.structures != null) {
                        found = this.structures.findChestInTemplate(a);
                    }
                    if (found != null) {
                        a.chest = found;
                        this.ensureContainsChest(a);
                    }
                } else {
                    this.ensureContainsChest(a);
                }
                if (a.chest == null || !a.box().contains(a.chest)) {
                    throw new IllegalArgumentException("\u0421\u0443\u043d\u0434\u0443\u043a \u043d\u0435 \u0437\u0430\u0434\u0430\u043d \u0438\u043b\u0438 \u043d\u0430\u0445\u043e\u0434\u0438\u0442\u0441\u044f \u0432\u043d\u0435 \u0440\u0435\u0433\u0438\u043e\u043d\u0430. \u041d\u0430\u0432\u0435\u0434\u0438\u0442\u0435\u0441\u044c \u043d\u0430 \u043e\u0434\u0438\u043d\u043e\u0447\u043d\u044b\u0439 \u0441\u0443\u043d\u0434\u0443\u043a: /jd chest " + a.id);
                }
                if (a.exit == null || a.playBox().contains(a.exit)) {
                    throw new IllegalArgumentException("\u041d\u0435 \u0437\u0430\u0434\u0430\u043d \u0431\u0435\u0437\u043e\u043f\u0430\u0441\u043d\u044b\u0439 \u0432\u044b\u0445\u043e\u0434 \u0441\u043d\u0430\u0440\u0443\u0436\u0438 \u0440\u0435\u0433\u0438\u043e\u043d\u0430. \u0412\u0441\u0442\u0430\u043d\u044c\u0442\u0435 \u043d\u0430 \u0441\u0443\u0445\u043e\u0439 \u043f\u043e\u043b \u0437\u0430 \u0435\u0433\u043e \u0433\u0440\u0430\u043d\u0438\u0446\u0435\u0439: /jd exit " + a.id);
                }
                this.safeExit(a.exit);
                if (!a.template) {
                    this.checkChest(a.chest);
                }
                if (a.kind == Model.Kind.PVE) {
                    this.mobs.validate(a, this.c.themes.get(a.theme));
                }
                if (integration) {
                    this.hooks.checkEconomy();
                    if (a.kind == Model.Kind.PVP && this.c.pvp != null && !this.c.pvp.cases.isEmpty()) {
                        this.hooks.checkCases();
                    }
                }
            }
            catch (Exception e) {
                issues.add(Objects.toString(e.getMessage(), e.getClass().getSimpleName()));
                if (e instanceof IllegalArgumentException) break block9;
                this.p.getLogger().log(Level.SEVERE, "\u041e\u0448\u0438\u0431\u043a\u0430 \u043f\u0440\u043e\u0432\u0435\u0440\u043a\u0438 \u0430\u0440\u0435\u043d\u044b " + a.id, e);
            }
        }
        return issues;
    }

    private void checkChest(Model.Point point) {
        if (point == null) {
            throw new IllegalArgumentException("Сундук не установлен: /jd chest <id>");
        }
        Location l = point.location();
        if (l == null) {
            throw new IllegalArgumentException("Мир сундука не найден: /jd chest <id>");
        }
        if (!l.getChunk().isLoaded()) {
            l.getChunk().load(true);
        }
        Block block = l.getBlock();
        BlockData blockData = block.getBlockData();
        if (blockData instanceof Chest chest) {
            if (chest.getType() != Chest.Type.SINGLE) {
                block.setType(org.bukkit.Material.CHEST);
            }
        } else if (block.getType() != org.bukkit.Material.BARREL && block.getType() != org.bukkit.Material.CHEST && block.getType() != org.bukkit.Material.TRAPPED_CHEST) {
            block.setType(org.bukkit.Material.CHEST);
        }
        if (l.clone().add(0.0, 1.0, 0.0).getBlock().getType().isOccluding() && block.getType() != org.bukkit.Material.BARREL) {
            l.clone().add(0.0, 1.0, 0.0).getBlock().setType(org.bukkit.Material.AIR);
        }
        BlockState blockState = block.getState();
        if (blockState instanceof Container container) {
            container.getInventory().clear();
        }
    }

    public static Location findSafeExit(Model.Arena arena) {
        if (arena == null || arena.box() == null) {
            return Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0).getSpawnLocation();
        }
        Model.Box box = arena.box();
        World w = Bukkit.getWorld(box.world());
        if (w == null) {
            return Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0).getSpawnLocation();
        }
        int x = box.x1() - 3;
        int z = box.z1() - 3;
        int y = PlacementService.getGroundHeight(w, x, z);
        if (y < w.getMinHeight()) {
            y = Math.max(w.getMinHeight() + 1, box.y1());
        }
        return new Location(w, x + 0.5, y + 1.0, z + 0.5);
    }

    private void safeExit(Model.Point point) {
        Location l;
        Location location = l = point == null ? null : point.location();
        if (l == null) {
            throw new IllegalArgumentException("Выход не задан");
        }
        if (!(l.getWorld().getWorldBorder().isInside(l) && l.getBlock().isPassable() && l.clone().add(0.0, 1.0, 0.0).getBlock().isPassable() && l.clone().add(0.0, -1.0, 0.0).getBlock().getType().isSolid() && !l.getBlock().isLiquid())) {
            throw new IllegalArgumentException("Выход должен быть сухим, с полом и двумя свободными блоками");
        }
    }

    public CompletableFuture<Model.Session> spawnAsync(String arenaId, String site, boolean test, String actor) {
        return this.spawnAtAsync(arenaId, site, test, actor, null);
    }

    public Model.Session spawn(String arenaId, String site, boolean test, String actor) throws Exception {
        return this.spawnAt(arenaId, site, test, actor, null);
    }

    public CompletableFuture<Model.Session> spawnAtAsync(String arenaId, String site, boolean test, String actor, Model.Point anchor) {
        try {
            Model.Arena definition = this.c.arenas.get(arenaId);
            if (definition == null) {
                return CompletableFuture.failedFuture(new IllegalArgumentException("Нет арены " + arenaId));
            }
            this.checkSpawn(definition, test);
            Model.Arena a = Json.copy(definition, Model.Arena.class);
            if (anchor != null) {
                if (!definition.template) {
                    return CompletableFuture.failedFuture(new IllegalArgumentException("Не выбран шаблон"));
                }
                a = definition.at(anchor);
                a.exit = PlacementService.exitNear(a.box());
            } else if (site != null) {
                Model.Point selected = definition.sites.get(site);
                if (selected == null) {
                    return CompletableFuture.failedFuture(new IllegalArgumentException("Нет площадки " + site));
                }
                if (!definition.template) {
                    return CompletableFuture.failedFuture(new IllegalArgumentException("Сначала сохраните шаблон"));
                }
                int[] offset = this.c.getEffectiveOffset(definition);
                if (offset[0] != 0 || offset[1] != 0 || offset[2] != 0) {
                    selected = selected.shift(offset[0], offset[1], offset[2], selected.world());
                }
                a = definition.at(selected);
            } else {
                a.template = false;
            }
            this.structures.check(a.box());
            if (a.exit == null) {
                a.exit = PlacementService.exitNear(a.box());
            }
            if (a.exit == null) {
                Location fallbackLoc = Engine.findSafeExit(a);
                if (fallbackLoc != null) {
                    a.exit = Model.Point.of(fallbackLoc);
                }
            }
            try {
                this.safeExit(a.exit);
            } catch (Exception ex) {
                Location fallbackLoc = Engine.findSafeExit(a);
                if (fallbackLoc != null) {
                    a.exit = Model.Point.of(fallbackLoc);
                }
            }
            Model.Box box = a.box();
            for (Model.Session existing : this.sessions.values()) {
                if (!this.c.protectionBox(a).intersects(this.c.protectionBox(existing.arena))) continue;
                return CompletableFuture.failedFuture(new IllegalArgumentException("Пересечение с " + existing.arenaId));
            }
            if (this.regions.overlaps(this.c.protectionBox(a), a.id)) {
                return CompletableFuture.failedFuture(new IllegalArgumentException("Приват данжа пересекает другой регион"));
            }
            Model.Point finalExit = a.exit;
            if (this.sessions.values().stream().anyMatch(s -> s.arena.playBox().contains(finalExit))) {
                return CompletableFuture.failedFuture(new IllegalArgumentException("Выход пересекает другой данж"));
            }
            if (Bukkit.getOnlinePlayers().stream().anyMatch(pl -> box.contains(pl.getLocation()) && !this.bypass((Player)pl))) {
                return CompletableFuture.failedFuture(new IllegalArgumentException("На территории находятся игроки"));
            }
            World targetWorld = Objects.requireNonNull(Bukkit.getWorld((String)box.world()));
            targetWorld.getNearbyEntities(new BoundingBox((double)box.x1(), (double)box.y1(), (double)box.z1(), (double)(box.x2() + 1), (double)(box.y2() + 1), (double)(box.z2() + 1)))
                    .stream()
                    .filter(entity -> entity instanceof Mob && !(entity instanceof Player))
                    .forEach(Entity::remove);
            Model.Session s2 = this.createSession(a, test);
            s2.phase = Model.Phase.PREPARING;
            this.buildingEditors.values().removeIf(arenaId::equals);
            this.sessions.put(arenaId, s2);
            this.db.save(s2);
            this.structures.tickets(s2, true);

            if (this.c.main().getBoolean("worldguard.enabled", true)) {
                s2.regionId = WorldGuardRegions.eventId(s2);
                this.db.save(s2);
                try {
                    this.regions.create(s2);
                } catch (Exception ex) {
                    this.cleanup(s2);
                    return CompletableFuture.failedFuture(ex);
                }
            }

            final Model.Arena finalArena = a;
            CompletableFuture<Void> placeTask;
            if (finalArena.template) {
                placeTask = this.structures.backupAsync(s2)
                        .thenCompose(v -> {
                            this.db.save(s2);
                            return this.structures.placeAsync(s2);
                        });
            } else {
                placeTask = CompletableFuture.completedFuture(null);
            }

            CompletableFuture<Model.Session> result = new CompletableFuture<>();
            placeTask.whenComplete((v, err) -> {
                Bukkit.getScheduler().runTask(this.p, () -> {
                    if (err != null) {
                        this.cleanup(s2);
                        result.completeExceptionally(err);
                        return;
                    }
                    try {
                        this.checkChest(finalArena.chest);
                        if (s2.kind == Model.Kind.PVE) {
                            this.mobs.validate(finalArena, s2.theme);
                        }
                        s2.phase = Model.Phase.AVAILABLE;
                        this.db.save(s2);
                        this.db.audit(actor, test ? "TEST" : "SPAWN", arenaId + " " + Objects.toString(site, "static"));
                        if (!test && this.c.main().getBoolean("announcements." + (finalArena.kind == Model.Kind.PVE ? "pve" : "pvp") + "-appearance")) {
                            this.msg.broadcast(finalArena.kind == Model.Kind.PVE ? "pve-appearance" : "pvp-appearance", Map.of("theme", s2.theme == null ? finalArena.name : s2.theme.name, "world", box.world(), "x", Integer.toString((int)finalArena.chest.x()), "z", Integer.toString((int)finalArena.chest.z())));
                        }
                        result.complete(s2);
                    } catch (Exception ex) {
                        this.cleanup(s2);
                        result.completeExceptionally(ex);
                    }
                });
            });
            return result;
        } catch (Exception ex) {
            return CompletableFuture.failedFuture(ex);
        }
    }

    public Model.Session spawnAt(String arenaId, String site, boolean test, String actor, Model.Point anchor) throws Exception {
        try {
            return this.spawnAtAsync(arenaId, site, test, actor, anchor).join();
        } catch (java.util.concurrent.CompletionException e) {
            if (e.getCause() instanceof Exception ex) {
                throw ex;
            }
            throw e;
        }
    }

    void checkSpawn(Model.Arena definition, boolean test) {
        String arenaId = definition.id;
        if (!test && !definition.enabled) {
            throw new IllegalArgumentException("\u0410\u0440\u0435\u043d\u0430 \u0432\u044b\u043a\u043b\u044e\u0447\u0435\u043d\u0430: /jd enable " + arenaId + " on");
        }
        if (this.sessions.containsKey(arenaId)) {
            throw new IllegalArgumentException("\u0421\u043e\u0431\u044b\u0442\u0438\u0435 \u0443\u0436\u0435 \u0440\u0430\u0437\u043c\u0435\u0449\u0435\u043d\u043e. /jd stop " + arenaId);
        }
        if (this.sessions.size() >= this.c.limit("placed-events")) {
            throw new IllegalArgumentException("\u041b\u0438\u043c\u0438\u0442 \u0440\u0430\u0437\u043c\u0435\u0449\u0451\u043d\u043d\u044b\u0445 \u0434\u0430\u043d\u0436\u0435\u0439");
        }
        List<String> errors = this.validate(definition, !test);
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException(String.join((CharSequence)"; ", errors));
        }
    }

    Model.Session createSession(Model.Arena a, boolean test) {
        Model.Session s = new Model.Session();
        s.arenaId = a.id;
        s.arena = a;
        s.kind = a.kind;
        s.test = test;
        s.theme = Json.copy(this.c.themes.get(a.theme), Model.Theme.class);
        s.duration = a.kind == Model.Kind.PVE ? 900 : this.c.timer("pvp-seconds");
        s.interval = s.kind == Model.Kind.PVE ? 180 : 0;
        s.reconnectSeconds = this.c.timer("reconnect-seconds");
        s.claimSeconds = this.c.timer("winner-claim-seconds");
        s.lootSeconds = this.c.timer("loot-seconds");
        s.cleanupSeconds = this.c.timer("cleanup-seconds");
        s.rewardPool = Json.copy(a.kind == Model.Kind.PVE ? this.c.pve : this.c.pvp, Model.Pool.class);
        if (!test) {
            for (String role : List.of("donation", "epic", "resource")) {
                try {
                    s.caseMappings.put(role, this.hooks.mappedCase(role));
                } catch (Exception ignored) {
                }
            }
        }
        s.availableUntil = System.currentTimeMillis() + 1000L * (long)this.c.timer(a.kind == Model.Kind.PVE ? "pve-available-seconds" : "pvp-available-seconds");
        return s;
    }

    public void click(Model.Session s, Player player) {
        long now = System.currentTimeMillis();
        this.advance(s, now);
        if (!this.sessions.containsKey(s.arenaId)) {
            return;
        }
        if (!player.hasPermission("jamdungeons.play")) {
            this.msg.send((CommandSender)player, "no-permission");
            return;
        }
        if (player.isDead()) {
            this.msg.error((CommandSender)player, "\u041f\u043e\u0441\u043b\u0435 \u0441\u043c\u0435\u0440\u0442\u0438 \u0434\u043e\u0436\u0434\u0438\u0441\u044c \u043e\u0431\u044b\u0447\u043d\u043e\u0433\u043e \u0432\u043e\u0437\u0440\u043e\u0436\u0434\u0435\u043d\u0438\u044f");
            return;
        }
        if (!s.arena.playBox().contains(player.getLocation())) {
            this.msg.error((CommandSender)player, "\u0412\u0441\u0442\u0430\u043d\u044c \u0432\u043d\u0443\u0442\u0440\u044c \u0440\u0435\u0433\u0438\u043e\u043d\u0430 \u0434\u0430\u043d\u0436\u0430 \u0438 \u043d\u0430\u0436\u043c\u0438 \u043d\u0430 \u0441\u0443\u043d\u0434\u0443\u043a");
            return;
        }
        if (s.phase == Model.Phase.AVAILABLE && !this.permitted(s, player)) {
            this.msg.error((CommandSender)player, (String)(player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR ? "\u0414\u043b\u044f \u043e\u0431\u044b\u0447\u043d\u043e\u0433\u043e \u043f\u0440\u043e\u0445\u043e\u0436\u0434\u0435\u043d\u0438\u044f \u043d\u0443\u0436\u0435\u043d \u0440\u0435\u0436\u0438\u043c \u0432\u044b\u0436\u0438\u0432\u0430\u043d\u0438\u044f. \u0410\u0434\u043c\u0438\u043d\u0441\u043a\u0430\u044f \u043f\u0440\u043e\u0432\u0435\u0440\u043a\u0430: /jd test " + s.arenaId : "\u041d\u0435 \u0443\u0434\u0430\u043b\u043e\u0441\u044c \u043f\u043e\u0434\u0442\u0432\u0435\u0440\u0434\u0438\u0442\u044c \u0434\u043e\u0441\u0442\u0443\u043f \u043f\u043e \u0436\u0438\u0437\u043d\u044f\u043c. \u0410\u0434\u043c\u0438\u043d\u0438\u0441\u0442\u0440\u0430\u0442\u043e\u0440\u0443: /jd status"));
            return;
        }
        if (s.phase == Model.Phase.AVAILABLE) {
            int reserved;
            if (now < s.retryAfter) {
                this.msg.error((CommandSender)player, "\u041f\u043e\u0441\u043b\u0435 \u043e\u0448\u0438\u0431\u043a\u0438 \u0432\u043e\u043b\u043d\u044b \u043f\u043e\u0432\u0442\u043e\u0440\u043d\u044b\u0439 \u0437\u0430\u043f\u0443\u0441\u043a \u0434\u043e\u0441\u0442\u0443\u043f\u0435\u043d \u0447\u0435\u0440\u0435\u0437 " + Rules.remaining(now, s.retryAfter) + " \u0441\u0435\u043a.");
                return;
            }
            if (this.sessions.values().stream().anyMatch(x -> x.kind == Model.Kind.PVE && (x.phase == Model.Phase.RUNNING || x.phase == Model.Phase.REWARD) && x.hasPlayer(player))) {
                this.msg.error((CommandSender)player, "\u0422\u044b \u0443\u0436\u0435 \u043f\u0440\u043e\u0445\u043e\u0434\u0438\u0448\u044c \u0434\u0440\u0443\u0433\u043e\u0439 PvE-\u0434\u0430\u043d\u0436");
                return;
            }
            long active = this.sessions.values().stream().filter(x -> x.kind == s.kind && x.phase == Model.Phase.RUNNING).count();
            if (active >= (long)this.c.limit(s.kind == Model.Kind.PVE ? "active-pve" : "simultaneous-pvp")) {
                this.msg.error((CommandSender)player, "\u0414\u043e\u0441\u0442\u0438\u0433\u043d\u0443\u0442 \u043b\u0438\u043c\u0438\u0442 \u0430\u043a\u0442\u0438\u0432\u043d\u044b\u0445 \u0441\u043e\u0431\u044b\u0442\u0438\u0439");
                return;
            }
            if (s.kind == Model.Kind.PVE && (reserved = this.sessions.values().stream().filter(x -> x.kind == Model.Kind.PVE && x.phase == Model.Phase.RUNNING).mapToInt(this::fullComposition).sum()) + this.fullComposition(s) > this.c.limit("total-mobs")) {
                throw new IllegalArgumentException("\u041e\u0431\u0449\u0438\u0439 \u043b\u0438\u043c\u0438\u0442 \u043d\u0435 \u0432\u043c\u0435\u0449\u0430\u0435\u0442 \u0432\u0441\u0435 \u0432\u043e\u043b\u043d\u044b \u043d\u043e\u0432\u043e\u0439 \u043f\u043e\u043f\u044b\u0442\u043a\u0438");
            }
            s.activator = player.getUniqueId().toString();
            s.owner = s.kind == Model.Kind.PVE ? s.activator : null;
            s.phase = Model.Phase.RUNNING;
            s.startedAt = now;
            s.endAt = now + 1000L * (long)s.duration;
            s.wave = 0;
            s.waveStartedAt = 0L;
            s.disconnectedBefore = false;
            s.disconnectUntil = 0L;
            s.reconnectSeconds = this.c.timer("reconnect-seconds");
            s.lastOwner = Model.Point.of(player.getLocation());
            this.db.save(s);
            if (s.kind == Model.Kind.PVE) {
                for (Player other : Bukkit.getOnlinePlayers()) {
                    if (!s.arena.playBox().contains(other.getLocation()) || s.hasPlayer(other) || this.bypass(other)) continue;
                    this.eject(s, other);
                }
            }
            this.msg.send((CommandSender)player, s.kind == Model.Kind.PVE ? "pve-start" : "pvp-start", Map.of("time", Rules.time(s.duration)));
            this.advance(s, now);
            return;
        }
        if (s.phase == Model.Phase.RUNNING) {
            if (s.kind == Model.Kind.PVE) {
                this.msg.send((CommandSender)player, "pve-locked", Map.of("remaining", Integer.toString(s.combatants.size())));
            } else {
                this.msg.send((CommandSender)player, "locked", Map.of("time", Rules.time(Rules.remaining(now, s.endAt))));
            }
            return;
        }
        if (s.phase == Model.Phase.REWARD || s.phase == Model.Phase.CLAIMED) {
            Model.Reward r;
            if (s.kind == Model.Kind.PVE && !s.hasPlayer(player) || s.kind == Model.Kind.PVP && (s.winner != null ? !s.winner.equals(player.getUniqueId().toString()) : !s.eligible.contains(player.getUniqueId().toString()))) {
                this.msg.send((CommandSender)player, "not-eligible");
                return;
            }
            if (s.test) {
                this.rewards.openPreview(player, this.rewards.generate(s, player.getUniqueId()), s.arenaId);
                this.beginCleanup(s, now);
                return;
            }
            if (s.rewardId == null) {
                r = this.rewards.generate(s, player.getUniqueId());
                r.claimed = true;
                Model.Session committed = Json.copy(s, Model.Session.class);
                committed.winner = r.owner;
                committed.rewardId = r.id;
                committed.phase = Model.Phase.CLAIMED;
                committed.claimUntil = now + (long)s.lootSeconds * 1000L;
                this.db.award(committed, r, true);
                this.copyCommit(s, committed);
            } else {
                r = this.db.reward(s.rewardId);
                if (r == null) {
                    throw new IllegalStateException("\u041d\u0435\u0442 \u0437\u0430\u043a\u0440\u0435\u043f\u043b\u0451\u043d\u043d\u043e\u0439 \u043d\u0430\u0433\u0440\u0430\u0434\u044b");
                }
                if (!r.claimed) {
                    r.claimed = true;
                    s.phase = Model.Phase.CLAIMED;
                    this.db.award(s, r, false);
                }
            }
            if (s.kind == Model.Kind.PVP && !s.announced) {
                s.announced = true;
                this.db.save(s);
                if (this.c.main().getBoolean("announcements.pvp-winner")) {
                    this.msg.broadcast("winner", Map.of("nickname", player.getName()));
                }
            }
            this.rewards.open(player, r, false);
            return;
        }
        this.msg.error((CommandSender)player, (String)(s.test ? "\u0422\u0435\u0441\u0442 \u0437\u0430\u0432\u0435\u0440\u0448\u0430\u0435\u0442\u0441\u044f. \u0414\u043b\u044f \u043d\u0430\u0441\u0442\u043e\u044f\u0449\u0435\u0433\u043e \u0441\u043e\u0431\u044b\u0442\u0438\u044f: /jd stop " + s.arenaId + ", \u0437\u0430\u0442\u0435\u043c /jd spawn " + s.arenaId : "\u0421\u043e\u0431\u044b\u0442\u0438\u0435 \u0437\u0430\u0432\u0435\u0440\u0448\u0430\u0435\u0442\u0441\u044f \u0438\u043b\u0438 \u043e\u0436\u0438\u0434\u0430\u0435\u0442 \u0432\u043e\u0441\u0441\u0442\u0430\u043d\u043e\u0432\u043b\u0435\u043d\u0438\u044f"));
    }

    private void copyCommit(Model.Session s, Model.Session next) {
        s.winner = next.winner;
        s.rewardId = next.rewardId;
        s.phase = next.phase;
        s.claimUntil = next.claimUntil;
    }

    public void tick() {
        long now = System.currentTimeMillis();
        for (Model.Session s : new ArrayList<Model.Session>(this.sessions.values())) {
            try {
                this.advance(s, now);
            }
            catch (Exception e) {
                this.p.getLogger().log(Level.SEVERE, "\u041e\u0448\u0438\u0431\u043a\u0430 \u0441\u043e\u0431\u044b\u0442\u0438\u044f " + s.arenaId, e);
                try {
                    if (s.phase == Model.Phase.RUNNING && s.kind == Model.Kind.PVE) {
                        this.fail(s, "\u043e\u0448\u0438\u0431\u043a\u0430 \u0432\u043e\u043b\u043d\u044b");
                        continue;
                    }
                    s.phase = Model.Phase.RESTORE_FAILED;
                    this.db.save(s);
                }
                catch (Exception inner) {
                    this.p.getLogger().log(Level.SEVERE, "\u041e\u0448\u0438\u0431\u043a\u0430 \u0441\u043e\u0445\u0440\u0430\u043d\u0435\u043d\u0438\u044f \u0441\u043e\u0431\u044b\u0442\u0438\u044f", inner);
                }
            }
        }
    }

    void advance(Model.Session s, long now) {
        if (s.phase == Model.Phase.AVAILABLE && now >= s.availableUntil) {
            this.beginCleanup(s, now);
            return;
        }
        if (s.phase == Model.Phase.RUNNING) {
            if (s.kind == Model.Kind.PVE) {
                long waveStart;
                Player owner = s.ownerPlayer();
                if (owner == null) {
                    if (s.disconnectUntil == 0L || now >= s.disconnectUntil) {
                        this.fail(s, "\u043e\u0442\u043a\u043b\u044e\u0447\u0435\u043d\u0438\u0435");
                        return;
                    }
                } else {
                    if (owner.isDead()) {
                        this.fail(s, "\u0441\u043c\u0435\u0440\u0442\u044c");
                        return;
                    }
                    if (!s.test && owner.getGameMode() != GameMode.SURVIVAL && owner.getGameMode() != GameMode.ADVENTURE) {
                        this.fail(s, "\u0441\u043c\u0435\u043d\u0430 \u0438\u0433\u0440\u043e\u0432\u043e\u0433\u043e \u0440\u0435\u0436\u0438\u043c\u0430");
                        return;
                    }
                    if (!s.arena.playBox().contains(owner.getLocation())) {
                        if (s.outsideSince == 0L) {
                            s.outsideSince = now;
                            this.msg.send((CommandSender)owner, "return-region", Map.of("seconds", Integer.toString(this.c.main().getInt("protection.owner-exit-grace-seconds", 5))));
                            this.db.save(s);
                        }
                        if (now - s.outsideSince >= (long)this.c.main().getInt("protection.owner-exit-grace-seconds", 5) * 1000L) {
                            this.fail(s, "\u0432\u044b\u0445\u043e\u0434 \u0438\u0437 \u0440\u0435\u0433\u0438\u043e\u043d\u0430 \u0434\u043e\u043b\u044c\u0448\u0435 \u0434\u043e\u043f\u0443\u0441\u0442\u0438\u043c\u043e\u0433\u043e \u0432\u0440\u0435\u043c\u0435\u043d\u0438");
                            return;
                        }
                    } else if (s.outsideSince != 0L) {
                        s.outsideSince = 0L;
                        this.db.save(s);
                    }
                }
                boolean cleared = s.wave > 0 && s.combatants.isEmpty();
                long l = waveStart = s.waveStartedAt == 0L ? s.startedAt : s.waveStartedAt;
                if (s.wave < 5 && (s.wave == 0 || cleared || now >= waveStart + (long)s.interval * 1000L)) {
                    int next = s.wave + 1;
                    try {
                        this.spawnWave.accept(s, next);
                    }
                    catch (RuntimeException ex) {
                        this.p.getLogger().log(Level.SEVERE, "\u0414\u0430\u043d\u0436 " + s.arenaId + ": \u0432\u043e\u043b\u043d\u0430 " + next + " \u043e\u0442\u043c\u0435\u043d\u0435\u043d\u0430; \u0430\u0432\u0442\u043e\u043c\u0430\u0442\u0438\u0447\u0435\u0441\u043a\u0438\u0439 \u043f\u043e\u0432\u0442\u043e\u0440 \u043e\u0442\u043a\u043b\u044e\u0447\u0451\u043d", ex);
                        this.fail(s, "\u043e\u0448\u0438\u0431\u043a\u0430 \u0432\u043e\u043b\u043d\u044b " + next);
                        s.retryAfter = now + 10000L;
                        this.db.save(s);
                        if (owner != null) {
                            this.msg.error((CommandSender)owner, "\u0412\u043e\u043b\u043d\u0443 " + next + " \u043d\u0435 \u0443\u0434\u0430\u043b\u043e\u0441\u044c \u0437\u0430\u043f\u0443\u0441\u0442\u0438\u0442\u044c. \u0421\u043e\u043e\u0431\u0449\u0438\u0442\u0435 \u0430\u0434\u043c\u0438\u043d\u0438\u0441\u0442\u0440\u0430\u0442\u043e\u0440\u0443; \u043f\u043e\u0432\u0442\u043e\u0440 \u0434\u043e\u0441\u0442\u0443\u043f\u0435\u043d \u0447\u0435\u0440\u0435\u0437 10 \u0441\u0435\u043a.");
                        }
                        return;
                    }
                    s.wave = next;
                    s.waveStartedAt = now;
                    s.endAt = now + (long)s.interval * 1000L;
                    this.db.save(s);
                    if (owner != null) {
                        this.msg.send((CommandSender)owner, next == 5 ? "wave-final" : "wave", Map.of("wave", Integer.toString(next), "time", Rules.time(Rules.remaining(now, s.endAt))));
                    }
                }
                this.mobs.pulse(s, now);
                if (s.wave == 5 && s.combatants.isEmpty() && owner != null && s.arena.playBox().contains(owner.getLocation())) {
                    s.phase = Model.Phase.REWARD;
                    s.claimUntil = now + (long)s.lootSeconds * 1000L;
                    if (!s.test) {
                        Model.Reward r = this.rewards.generate(s, owner.getUniqueId());
                        s.rewardId = r.id;
                        this.db.award(s, r, false);
                    } else {
                        this.db.save(s);
                    }
                    this.msg.send((CommandSender)owner, s.test ? "test-complete" : "pve-complete");
                    return;
                }
            } else if (now >= s.endAt) {
                s.eligible.clear();
                for (Player candidate : Bukkit.getOnlinePlayers()) {
                    if (!s.arena.playBox().contains(candidate.getLocation()) || !this.permitted(s, candidate)) continue;
                    s.eligible.add(candidate.getUniqueId().toString());
                }
                s.phase = Model.Phase.REWARD;
                s.claimUntil = now + (long)s.claimSeconds * 1000L;
                this.db.save(s);
                for (String uuid : s.eligible) {
                    Player pl = Bukkit.getPlayer((UUID)UUID.fromString(uuid));
                    if (pl == null) continue;
                    this.msg.send((CommandSender)pl, "pvp-open");
                }
                if (s.eligible.isEmpty()) {
                    this.beginCleanup(s, now);
                }
                return;
            }
            for (Player pl : Bukkit.getOnlinePlayers()) {
                if (!s.arena.playBox().contains(pl.getLocation())) continue;
                pl.sendActionBar(Messages.color(this.msg.raw(s.kind == Model.Kind.PVE ? (s.wave == 5 ? "hud-pve-clear" : "hud-pve") : "hud-pvp", Map.of("theme", s.theme == null ? s.arena.name : s.theme.name, "wave", Integer.toString(s.wave), "next", Rules.time(Rules.remaining(now, s.endAt)), "remaining", Integer.toString(s.combatants.size()), "time", Rules.time(Rules.remaining(now, s.endAt))))));
            }
        }
        if ((s.phase == Model.Phase.REWARD || s.phase == Model.Phase.CLAIMED) && now >= s.claimUntil) {
            Model.Reward r;
            if (s.rewardId != null && (r = this.db.reward(s.rewardId)) != null && !r.claimed) {
                r.forfeited = true;
                this.db.reward(r);
            }
            this.beginCleanup(s, now);
        }
        if (s.phase == Model.Phase.CLEANUP) {
            long remaining = Rules.remaining(now, s.cleanupAt);
            for (Player pl : Bukkit.getOnlinePlayers()) {
                if (s.arena != null && s.arena.playBox() != null && s.arena.playBox().contains(pl.getLocation())) {
                    pl.sendActionBar(Messages.color("&eДанж исчезнет через: &f" + Rules.time(remaining)));
                }
            }
            if (now >= s.cleanupAt) {
                this.cleanup(s);
            }
        }
    }

    private int totalLiving() {
        int total = 0;
        for (Model.Session s : this.sessions.values()) {
            for (String id : s.entities) {
                Entity e = Bukkit.getEntity((UUID)UUID.fromString(id));
                if (!(e instanceof LivingEntity) || e.isDead()) continue;
                ++total;
            }
        }
        return total;
    }

    private int fullComposition(Model.Session s) {
        return s.theme.waves.stream().flatMap(row -> row.values().stream()).mapToInt(Integer::intValue).sum();
    }

    public void fail(Model.Session s, String reason) {
        Player owner = s.ownerPlayer();
        if (s.owner != null && (owner == null || s.disconnectUntil > 0L && !owner.isDead())) {
            this.rememberReturn(s, s.ownerId());
        }
        this.mobs.clear(s);
        s.phase = Model.Phase.AVAILABLE;
        s.owner = null;
        s.activator = null;
        s.wave = 0;
        s.outsideSince = 0L;
        s.spawnedMobs = 0;
        s.killedMobs = 0;
        s.disconnectUntil = 0L;
        s.disconnectedBefore = false;
        s.startedAt = 0L;
        s.endAt = 0L;
        s.rewardId = null;
        s.availableUntil = Math.max(s.availableUntil, System.currentTimeMillis() + 60000L);
        this.db.save(s);
        this.db.audit("SYSTEM", "PVE_RESET", s.arenaId + " " + reason);
        if (owner != null) {
            this.msg.send((CommandSender)owner, "failed", Map.of("reason", reason));
        }
    }

    public void beginCleanup(Model.Session s, long now) {
        if (s.phase == Model.Phase.CLEANUP || s.phase == Model.Phase.RESTORE_FAILED) {
            return;
        }
        this.mobs.clear(s);
        s.phase = Model.Phase.CLEANUP;
        s.cleanupAt = now + (long)s.cleanupSeconds * 1000L;
        this.db.save(s);
        for (Player pl : Bukkit.getOnlinePlayers()) {
            if (!s.arena.playBox().contains(pl.getLocation())) continue;
            this.msg.send((CommandSender)pl, "cleanup", Map.of("time", Rules.time(s.cleanupSeconds)));
        }
    }

    public Model.Session session(String id) {
        if (id == null) return null;
        Model.Session s = this.sessions.get(id);
        if (s != null) return s;
        for (Model.Session candidate : this.sessions.values()) {
            if (candidate.id.equalsIgnoreCase(id) || candidate.arenaId.equalsIgnoreCase(id)) {
                return candidate;
            }
            if (candidate.arena != null && candidate.arena.name != null) {
                if (candidate.arena.name.equalsIgnoreCase(id)
                        || Messages.stripColor(candidate.arena.name).trim().equalsIgnoreCase(id.trim())) {
                    return candidate;
                }
            }
        }
        return null;
    }

    public CompletableFuture<Void> stop(String id, String actor) {
        Model.Session s = this.session(id);
        if (s == null) {
            s = this.sessions.get(id);
        }
        if (s == null) {
            for (Model.Session candidate : this.sessions.values()) {
                if (candidate.id.equalsIgnoreCase(id) || candidate.arenaId.equalsIgnoreCase(id)) {
                    s = candidate;
                    break;
                }
            }
        }
        if (s == null) {
            throw new IllegalArgumentException("Нет размещённого события");
        }
        this.db.audit(actor, "STOP", s.arenaId);
        try {
            Model.Reward r;
            if (s.rewardId != null && (r = this.db.reward(s.rewardId)) != null && !r.claimed) {
                r.forfeited = true;
                this.db.reward(r);
            }
        } catch (Exception ex) {
            this.p.getLogger().log(Level.WARNING, "Ошибка обновления статуса награды при остановке: " + ex.getMessage());
        }
        return this.cleanupAsync(s);
    }

    public void cleanup(Model.Session s) {
        if (!this.p.isEnabled()) {
            this.cleanupSync(s);
            return;
        }
        this.cleanupAsync(s);
    }

    public void cleanupSync(Model.Session s) {
        try {
            for (String id : s.offlinePlayers) {
                try {
                    UUID u = UUID.fromString(id);
                    if (Bukkit.getPlayer(u) == null) {
                        this.rememberReturn(s, u);
                    }
                } catch (Exception ignored) {}
            }
            if (s.owner != null && s.ownerPlayer() == null) {
                try {
                    this.rememberReturn(s, s.ownerId());
                } catch (Exception ignored) {}
            }
            try {
                this.mobs.clear(s);
            } catch (Exception ex) {
                this.p.getLogger().log(Level.WARNING, "Ошибка очистки мобов при остановке " + s.arenaId + ": " + ex.getMessage());
            }
            for (Player pl2 : Bukkit.getOnlinePlayers()) {
                if (s.arena != null && s.arena.playBox() != null && s.arena.playBox().contains(pl2.getLocation()) && !pl2.isDead()) {
                    this.eject(s, pl2);
                }
            }
            try {
                this.structures.restore(s);
            } catch (Exception ex) {
                this.p.getLogger().log(Level.WARNING, "Ошибка восстановления блоков при остановке " + s.arenaId + ": " + ex.getMessage(), ex);
            }
            try {
                this.regions.delete(s);
            } catch (Exception ex) {
                this.p.getLogger().log(Level.WARNING, "Ошибка удаления привата при остановке " + s.arenaId + ": " + ex.getMessage(), ex);
            }
            try {
                this.db.delete(s);
            } catch (Exception ex) {
                this.p.getLogger().log(Level.WARNING, "Ошибка удаления сессии из БД: " + ex.getMessage());
            }
            this.sessions.remove(s.arenaId);
            this.sessions.remove(s.id);
            try {
                this.structures.tickets(s, false);
            } catch (Exception ignored) {}
            try {
                this.structures.discardBackup(s);
            } catch (Exception ignored) {}
        } catch (Exception e) {
            s.phase = Model.Phase.RESTORE_FAILED;
            try {
                this.db.save(s);
            } catch (Exception ignored) {}
            this.p.getLogger().log(Level.SEVERE, "Остановка " + s.arenaId + " завершилась с ошибкой", e);
        }
    }

    public CompletableFuture<Void> cleanupAsync(Model.Session s) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        try {
            for (String id : s.offlinePlayers) {
                try {
                    UUID u = UUID.fromString(id);
                    if (Bukkit.getPlayer(u) == null) {
                        this.rememberReturn(s, u);
                    }
                } catch (Exception ignored) {}
            }
            if (s.owner != null && s.ownerPlayer() == null) {
                try {
                    this.rememberReturn(s, s.ownerId());
                } catch (Exception ignored) {}
            }
            try {
                this.mobs.clear(s);
            } catch (Exception ex) {
                this.p.getLogger().log(Level.WARNING, "Ошибка очистки мобов при остановке " + s.arenaId + ": " + ex.getMessage());
            }
            for (Player pl2 : Bukkit.getOnlinePlayers()) {
                if (s.arena != null && s.arena.playBox() != null && s.arena.playBox().contains(pl2.getLocation()) && !pl2.isDead()) {
                    this.eject(s, pl2);
                }
            }
            s.phase = Model.Phase.CLEANUP;

            CompletableFuture<Void> restoreTask;
            if (s.backup != null) {
                restoreTask = this.structures.restoreAsync(s);
            } else if (s.arena != null && s.arena.template && s.arena.box() != null) {
                restoreTask = this.structures.clearAreaAsync(s.arena.box());
            } else {
                restoreTask = CompletableFuture.completedFuture(null);
            }

            restoreTask.whenComplete((v, restoreErr) -> {
                Bukkit.getScheduler().runTask(this.p, () -> {
                    if (restoreErr != null) {
                        this.p.getLogger().log(Level.WARNING, "Ошибка восстановления блоков при остановке " + s.arenaId + ": " + restoreErr.getMessage(), restoreErr);
                    }
                    try {
                        this.regions.delete(s);
                    } catch (Exception ex) {
                        this.p.getLogger().log(Level.WARNING, "Ошибка удаления привата при остановке " + s.arenaId + ": " + ex.getMessage(), ex);
                    }
                    try {
                        this.db.delete(s);
                    } catch (Exception ex) {
                        this.p.getLogger().log(Level.WARNING, "Ошибка удаления сессии из БД: " + ex.getMessage());
                    }
                    this.sessions.remove(s.arenaId);
                    this.sessions.remove(s.id);
                    try {
                        this.structures.tickets(s, false);
                    } catch (Exception ignored) {}
                    try {
                        this.structures.discardBackup(s);
                    } catch (Exception ignored) {}

                    if (restoreErr != null) {
                        future.completeExceptionally(restoreErr);
                    } else {
                        future.complete(null);
                    }
                });
            });
        } catch (Exception e) {
            s.phase = Model.Phase.RESTORE_FAILED;
            try {
                this.db.save(s);
            } catch (Exception ignored) {}
            this.p.getLogger().log(Level.SEVERE, "Остановка " + s.arenaId + " завершилась с ошибкой", e);
            future.completeExceptionally(e);
        }
        return future;
    }

    public void eject(Model.Session s, Player pl) {
        if (pl == null || pl.isDead() || !pl.isOnline()) {
            return;
        }
        Location target = null;
        if (s.arena != null && s.arena.exit != null && s.arena.exit.location() != null) {
            try {
                this.safeExit(s.arena.exit);
                target = s.arena.exit.location();
            } catch (Exception ignored) {}
        }
        if (target == null) {
            target = Engine.findSafeExit(s.arena);
        }
        if (target != null) {
            pl.teleport(target);
        }
    }

    public void died(Player pl) {
        for (Model.Session s : new ArrayList<Model.Session>(this.sessions.values())) {
            if (s.kind == Model.Kind.PVE && s.hasPlayer(pl)) {
                this.msg.send((CommandSender)pl, "death");
                if (s.phase == Model.Phase.RUNNING) {
                    this.fail(s, "\u0441\u043c\u0435\u0440\u0442\u044c");
                } else if (s.phase == Model.Phase.REWARD) {
                    Model.Reward r;
                    if (s.rewardId != null && (r = this.db.reward(s.rewardId)) != null) {
                        r.forfeited = true;
                        this.db.reward(r);
                    }
                    this.beginCleanup(s, System.currentTimeMillis());
                }
            }
            if (s.kind != Model.Kind.PVP || !s.eligible.remove(pl.getUniqueId().toString())) continue;
            this.db.save(s);
        }
    }

    public void quit(Player pl) {
        for (Model.Session s : this.sessions.values()) {
            if (!pl.isDead() && s.arena.playBox().contains(pl.getLocation())) {
                s.offlinePlayers.add(pl.getUniqueId().toString());
                this.db.save(s);
            }
            if (s.kind == Model.Kind.PVE && s.phase == Model.Phase.RUNNING && s.hasPlayer(pl)) {
                if (s.disconnectedBefore) {
                    this.rememberReturn(s, pl.getUniqueId());
                    this.fail(s, "\u043f\u043e\u0432\u0442\u043e\u0440\u043d\u043e\u0435 \u043e\u0442\u043a\u043b\u044e\u0447\u0435\u043d\u0438\u0435");
                    continue;
                }
                s.disconnectedBefore = true;
                s.disconnectUntil = System.currentTimeMillis() + (long)s.reconnectSeconds * 1000L;
                s.lastOwner = Model.Point.of(pl.getLocation());
                this.db.save(s);
            }
            if (s.kind != Model.Kind.PVP || !s.eligible.remove(pl.getUniqueId().toString())) continue;
            this.db.save(s);
        }
    }

    private void rememberReturn(Model.Session session, UUID player) {
        if (session.arena.exit != null) {
            this.db.rememberReturn(player, session.arenaId, session.arena.exit);
        }
    }

    void joined(Player player, long now) {
        for (Model.Session session : this.sessions.values()) {
            if (session.phase != Model.Phase.RUNNING || session.kind != Model.Kind.PVE || !session.hasPlayer(player) || session.disconnectUntil <= 0L) continue;
            if (now >= session.disconnectUntil || player.isDead()) {
                this.fail(session, "\u043f\u043e\u0437\u0434\u043d\u0438\u0439 \u0432\u043e\u0437\u0432\u0440\u0430\u0442");
                continue;
            }
            session.disconnectUntil = 0L;
            if (session.arena.playBox().contains(player.getLocation())) {
                session.outsideSince = 0L;
            }
            this.db.save(session);
            this.msg.send((CommandSender)player, "reconnected");
        }
        Store.ReturnPoint pending = this.db.pendingReturn(player.getUniqueId());
        if (pending != null && !player.isDead()) {
            Location destination;
            try {
                this.safeExit(pending.exit());
                destination = pending.exit().location();
            }
            catch (Exception unavailable) {
                World world = pending.exit() == null ? null : Bukkit.getWorld((String)pending.exit().world());
                destination = (world == null ? player.getWorld() : world).getSpawnLocation();
            }
            if (!player.teleport(destination)) {
                throw new IllegalStateException("\u0412\u043e\u0437\u0432\u0440\u0430\u0442 \u0438\u0437 \u0434\u0430\u043d\u0436\u0430 \u043e\u0442\u043c\u0435\u043d\u0451\u043d \u0434\u0440\u0443\u0433\u0438\u043c \u043f\u043b\u0430\u0433\u0438\u043d\u043e\u043c");
            }
            player.saveData();
            this.db.clearReturn(player.getUniqueId());
            this.msg.send((CommandSender)player, "reconnect-expired");
        }
        for (Model.Session session : this.sessions.values()) {
            if (!session.offlinePlayers.remove(player.getUniqueId().toString())) continue;
            this.db.save(session);
        }
    }

    public void moved(Player pl, Location from, Location to) {
        for (Model.Session s : this.sessions.values()) {
            if (s.kind == Model.Kind.PVE && s.phase == Model.Phase.RUNNING && s.hasPlayer(pl) && s.arena.playBox().contains(to) && s.outsideSince != 0L) {
                s.outsideSince = 0L;
                this.db.save(s);
            }
            if (!s.arena.playBox().contains(from) || s.arena.playBox().contains(to) || s.kind != Model.Kind.PVP || !s.eligible.remove(pl.getUniqueId().toString())) continue;
            this.db.save(s);
        }
    }

    public void recover() {
        this.db.recoverDeliveries();
        for (Model.Session s : this.db.sessions()) {
            this.sessions.put(s.arenaId, s);
            if (s.kind == Model.Kind.PVE) {
                s.duration = 900;
                s.interval = 180;
                s.reconnectSeconds = this.c.timer("reconnect-seconds");
                if (this.c.themes.containsKey(s.arena.theme)) {
                    s.theme = Json.copy(this.c.themes.get(s.arena.theme), Model.Theme.class);
                }
                for (Model.Spawn point : s.arena.points) {
                    if (point.mob.equals("any") || s.theme.mobs.containsKey(point.mob)) continue;
                    point.mob = "any";
                }
            }
            try {
                this.regions.create(s);
                this.structures.tickets(s, true);
                if (s.phase == Model.Phase.PREPARING) {
                    this.cleanup(s);
                    continue;
                }
                if (s.kind == Model.Kind.PVE && s.phase == Model.Phase.RUNNING) {
                    this.fail(s, "\u043f\u0435\u0440\u0435\u0437\u0430\u043f\u0443\u0441\u043a");
                    continue;
                }
                if (s.kind != Model.Kind.PVP || s.phase != Model.Phase.RUNNING || System.currentTimeMillis() < s.endAt) continue;
                this.beginCleanup(s, System.currentTimeMillis());
            }
            catch (Exception e) {
                s.phase = Model.Phase.RESTORE_FAILED;
                this.db.save(s);
                this.p.getLogger().warning("\u0412\u043e\u0441\u0441\u0442\u0430\u043d\u043e\u0432\u043b\u0435\u043d\u0438\u0435 \u043e\u0442\u043b\u043e\u0436\u0435\u043d\u043e: " + s.arenaId + ": " + e.getMessage());
            }
        }
        for (World w : Bukkit.getWorlds()) {
            for (Entity e : w.getEntities()) {
                Model.Session s;
                if (!this.mobs.tagged(e) || (s = this.byEvent(this.mobs.event(e))) != null && s.phase == Model.Phase.RUNNING) continue;
                e.remove();
            }
        }
    }

    public void close() {
        for (Model.Session s : this.sessions.values()) {
            this.db.save(s);
            this.structures.tickets(s, false);
        }
    }
}

