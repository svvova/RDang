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
import java.util.function.BiConsumer;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
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
    private final JavaPlugin p;
    public final Configs c;
    public final Store db;
    public final Messages msg;
    public final Integrations hooks;
    public final Rewards rewards;
    public final Spawner mobs;
    public final Templates structures;
    public final WorldGuardRegions regions;
    public final PlacementService placement;
    final Combat combat;
    BiConsumer<Model.Session, Integer> spawnWave;

    void report(String message, Throwable error) {
        this.p.getLogger().log(Level.SEVERE, message, error);
    }

    public Engine(JavaPlugin p, Configs c, Store db, Messages msg, Integrations h, Rewards r, Spawner m, Templates t) {
        this.p = p;
        this.c = c;
        this.db = db;
        this.msg = msg;
        this.hooks = h;
        this.rewards = r;
        this.mobs = m;
        this.structures = t;
        this.regions = new WorldGuardRegions(c);
        this.placement = new PlacementService(p, this);
        this.combat = new Combat(p, this);
        Bukkit.getPluginManager().registerEvents((Listener)this.combat, (Plugin)p);
        this.spawnWave = (session, wave) -> this.mobs.wave((Model.Session)session, (int)wave, this.totalLiving());
        this.rewards.completion(reward -> {
            for (Model.Session session : new ArrayList<Model.Session>(this.sessions.values())) {
                if (!reward.id.equals(session.rewardId) || session.phase != Model.Phase.CLAIMED) continue;
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
                    if (a.kind == Model.Kind.PVP) {
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
        Container container;
        Chest chest;
        BlockData blockData;
        Location l = point.location();
        if (l == null || !((blockData = l.getBlock().getBlockData()) instanceof Chest) || (chest = (Chest)blockData).getType() != Chest.Type.SINGLE) {
            throw new IllegalArgumentException("\u041d\u0443\u0436\u0435\u043d \u043e\u0434\u0438\u043d\u043e\u0447\u043d\u044b\u0439 \u0441\u0443\u043d\u0434\u0443\u043a: /jd chest <id>");
        }
        if (l.clone().add(0.0, 1.0, 0.0).getBlock().getType().isOccluding()) {
            throw new IllegalArgumentException("\u041e\u0441\u0432\u043e\u0431\u043e\u0434\u0438\u0442\u0435 \u0431\u043b\u043e\u043a \u043d\u0430\u0434 \u0441\u0443\u043d\u0434\u0443\u043a\u043e\u043c");
        }
        BlockState blockState = l.getBlock().getState();
        if (blockState instanceof Container && !(container = (Container)blockState).getInventory().isEmpty()) {
            throw new IllegalArgumentException("\u0421\u0443\u043d\u0434\u0443\u043a \u0434\u043e\u043b\u0436\u0435\u043d \u0431\u044b\u0442\u044c \u043f\u0443\u0441\u0442\u044b\u043c");
        }
    }

    private void safeExit(Model.Point point) {
        Location l;
        Location location = l = point == null ? null : point.location();
        if (!(l != null && l.getWorld().getWorldBorder().isInside(l) && l.getBlock().isPassable() && l.clone().add(0.0, 1.0, 0.0).getBlock().isPassable() && l.clone().add(0.0, -1.0, 0.0).getBlock().getType().isSolid() && !l.getBlock().isLiquid())) {
            throw new IllegalArgumentException("\u0412\u044b\u0445\u043e\u0434 \u0434\u043e\u043b\u0436\u0435\u043d \u0431\u044b\u0442\u044c \u0441\u0443\u0445\u0438\u043c, \u0441 \u043f\u043e\u043b\u043e\u043c \u0438 \u0434\u0432\u0443\u043c\u044f \u0441\u0432\u043e\u0431\u043e\u0434\u043d\u044b\u043c\u0438 \u0431\u043b\u043e\u043a\u0430\u043c\u0438");
        }
    }

    public Model.Session spawn(String arenaId, String site, boolean test, String actor) throws Exception {
        return this.spawnAt(arenaId, site, test, actor, null);
    }

    Model.Session spawnAt(String arenaId, String site, boolean test, String actor, Model.Point anchor) throws Exception {
        Model.Arena definition = this.c.arenas.get(arenaId);
        if (definition == null) {
            throw new IllegalArgumentException("\u041d\u0435\u0442 \u0430\u0440\u0435\u043d\u044b " + arenaId);
        }
        this.checkSpawn(definition, test);
        Model.Arena a = Json.copy(definition, Model.Arena.class);
        if (anchor != null) {
            if (!definition.template) {
                throw new IllegalArgumentException("\u041d\u0435 \u0432\u044b\u0431\u0440\u0430\u043d \u0448\u0430\u0431\u043b\u043e\u043d");
            }
            a = definition.at(anchor);
            a.exit = PlacementService.exitNear(a.box());
        } else if (site != null) {
            Model.Point selected = definition.sites.get(site);
            if (selected == null) {
                throw new IllegalArgumentException("\u041d\u0435\u0442 \u043f\u043b\u043e\u0449\u0430\u0434\u043a\u0438 " + site);
            }
            if (!definition.template) {
                throw new IllegalArgumentException("\u0421\u043d\u0430\u0447\u0430\u043b\u0430 \u0441\u043e\u0445\u0440\u0430\u043d\u0438\u0442\u0435 \u0448\u0430\u0431\u043b\u043e\u043d");
            }
            a = definition.at(selected);
        } else {
            a.template = false;
        }
        this.structures.check(a.box());
        this.safeExit(a.exit);
        Model.Box box = a.box();
        for (Model.Session existing : this.sessions.values()) {
            if (!this.c.protectionBox(a).intersects(this.c.protectionBox(existing.arena))) continue;
            throw new IllegalArgumentException("\u041f\u0435\u0440\u0435\u0441\u0435\u0447\u0435\u043d\u0438\u0435 \u0441 " + existing.arenaId);
        }
        if (this.regions.overlaps(this.c.protectionBox(a), a.id)) {
            throw new IllegalArgumentException("\u041f\u0440\u0438\u0432\u0430\u0442 \u0434\u0430\u043d\u0436\u0430 \u043f\u0435\u0440\u0435\u0441\u0435\u043a\u0430\u0435\u0442 \u0434\u0440\u0443\u0433\u043e\u0439 \u0440\u0435\u0433\u0438\u043e\u043d");
        }
        Model.Point finalExit = a.exit;
        if (this.sessions.values().stream().anyMatch(s -> s.arena.playBox().contains(finalExit))) {
            throw new IllegalArgumentException("\u0412\u044b\u0445\u043e\u0434 \u043f\u0435\u0440\u0435\u0441\u0435\u043a\u0430\u0435\u0442 \u0434\u0440\u0443\u0433\u043e\u0439 \u0434\u0430\u043d\u0436");
        }
        if (Bukkit.getOnlinePlayers().stream().anyMatch(pl -> box.contains(pl.getLocation()) && !this.bypass((Player)pl))) {
            throw new IllegalArgumentException("\u041d\u0430 \u0442\u0435\u0440\u0440\u0438\u0442\u043e\u0440\u0438\u0438 \u043d\u0430\u0445\u043e\u0434\u044f\u0442\u0441\u044f \u0438\u0433\u0440\u043e\u043a\u0438");
        }
        World targetWorld = Objects.requireNonNull(Bukkit.getWorld((String)box.world()));
        if (targetWorld.getNearbyEntities(new BoundingBox((double)box.x1(), (double)box.y1(), (double)box.z1(), (double)(box.x2() + 1), (double)(box.y2() + 1), (double)(box.z2() + 1))).stream().anyMatch(entity -> entity instanceof Mob)) {
            throw new IllegalArgumentException("\u0422\u0435\u0440\u0440\u0438\u0442\u043e\u0440\u0438\u044f \u0437\u0430\u043d\u044f\u0442\u0430 \u0441\u0443\u0449\u0435\u0441\u0442\u0432\u0430\u043c\u0438; \u0432\u044b\u0431\u0435\u0440\u0438\u0442\u0435 \u0441\u0432\u043e\u0431\u043e\u0434\u043d\u043e\u0435 \u043c\u0435\u0441\u0442\u043e");
        }
        Model.Session s2 = this.createSession(a, test);
        this.buildingEditors.values().removeIf(arenaId::equals);
        this.sessions.put(arenaId, s2);
        this.db.save(s2);
        this.structures.tickets(s2, true);
        try {
            if (this.c.main().getBoolean("worldguard.enabled", true)) {
                s2.regionId = WorldGuardRegions.eventId(s2);
                this.db.save(s2);
                this.regions.create(s2);
            }
            if (a.template) {
                this.structures.backup(s2);
                this.db.save(s2);
                this.structures.place(s2);
            }
            this.checkChest(a.chest);
            if (s2.kind == Model.Kind.PVE) {
                this.mobs.validate(a, s2.theme);
            }
            s2.phase = Model.Phase.AVAILABLE;
            this.db.save(s2);
        }
        catch (Exception e) {
            this.cleanup(s2);
            throw e;
        }
        this.db.audit(actor, test ? "TEST" : "SPAWN", arenaId + " " + Objects.toString(site, "static"));
        if (!test && this.c.main().getBoolean("announcements." + (a.kind == Model.Kind.PVE ? "pve" : "pvp") + "-appearance")) {
            this.msg.broadcast(a.kind == Model.Kind.PVE ? "pve-appearance" : "pvp-appearance", Map.of("theme", s2.theme == null ? a.name : s2.theme.name, "world", box.world(), "x", Integer.toString((int)a.chest.x()), "z", Integer.toString((int)a.chest.z())));
        }
        return s2;
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
        if (!test && a.kind == Model.Kind.PVP) {
            for (String role : List.of("donation", "epic", "resource")) {
                s.caseMappings.put(role, this.hooks.mappedCase(role));
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
        if (s.phase == Model.Phase.CLEANUP && now >= s.cleanupAt) {
            this.cleanup(s);
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

    public void stop(String id, String actor) {
        Model.Reward r;
        Model.Session s = this.sessions.get(id);
        if (s == null) {
            throw new IllegalArgumentException("\u041d\u0435\u0442 \u0440\u0430\u0437\u043c\u0435\u0449\u0451\u043d\u043d\u043e\u0433\u043e \u0441\u043e\u0431\u044b\u0442\u0438\u044f");
        }
        this.db.audit(actor, "STOP", id);
        if (s.rewardId != null && (r = this.db.reward(s.rewardId)) != null && !r.claimed) {
            r.forfeited = true;
            this.db.reward(r);
        }
        this.cleanup(s);
    }

    public void cleanup(Model.Session s) {
        try {
            for (String id : s.offlinePlayers) {
                if (Bukkit.getPlayer((UUID)UUID.fromString(id)) != null) continue;
                this.rememberReturn(s, UUID.fromString(id));
            }
            if (s.owner != null && s.ownerPlayer() == null) {
                this.rememberReturn(s, s.ownerId());
            }
            this.mobs.clear(s);
            for (Player pl2 : Bukkit.getOnlinePlayers()) {
                if (!s.arena.playBox().contains(pl2.getLocation()) || pl2.isDead()) continue;
                this.eject(s, pl2);
            }
            if (Bukkit.getOnlinePlayers().stream().anyMatch(pl -> !pl.isDead() && s.arena.playBox().contains(pl.getLocation()))) {
                throw new IllegalStateException("\u0418\u0433\u0440\u043e\u043a\u0430 \u043d\u0435 \u0443\u0434\u0430\u043b\u043e\u0441\u044c \u0431\u0435\u0437\u043e\u043f\u0430\u0441\u043d\u043e \u0432\u044b\u0432\u0435\u0441\u0442\u0438; \u0432\u043e\u0441\u0441\u0442\u0430\u043d\u043e\u0432\u043b\u0435\u043d\u0438\u0435 \u043e\u0442\u043b\u043e\u0436\u0435\u043d\u043e");
            }
            this.structures.restore(s);
            this.regions.delete(s);
            this.db.delete(s);
            this.sessions.remove(s.arenaId);
            this.structures.tickets(s, false);
            this.structures.discardBackup(s);
        }
        catch (Exception e) {
            s.phase = Model.Phase.RESTORE_FAILED;
            this.db.save(s);
            this.p.getLogger().log(Level.SEVERE, "\u0412\u043e\u0441\u0441\u0442\u0430\u043d\u043e\u0432\u043b\u0435\u043d\u0438\u0435 " + s.arenaId + " \u0437\u0430\u0431\u043b\u043e\u043a\u0438\u0440\u043e\u0432\u0430\u043d\u043e. \u0418\u0441\u043f\u0440\u0430\u0432\u044c\u0442\u0435 \u0432\u044b\u0445\u043e\u0434/\u043c\u0438\u0440 \u0438 \u043f\u043e\u0432\u0442\u043e\u0440\u0438\u0442\u0435 /jd stop " + s.arenaId, e);
        }
    }

    public void eject(Model.Session s, Player pl) {
        if (pl.isDead()) {
            return;
        }
        try {
            this.safeExit(s.arena.exit);
            if (!pl.teleport(s.arena.exit.location())) {
                throw new IllegalStateException("\u0422\u0435\u043b\u0435\u043f\u043e\u0440\u0442 \u043e\u0442\u043c\u0435\u043d\u0451\u043d \u0434\u0440\u0443\u0433\u0438\u043c \u043f\u043b\u0430\u0433\u0438\u043d\u043e\u043c");
            }
        }
        catch (Exception e) {
            throw new IllegalStateException("\u041d\u0435\u0431\u0435\u0437\u043e\u043f\u0430\u0441\u043d\u044b\u0439 \u0432\u044b\u0445\u043e\u0434 " + s.arenaId + ": " + e.getMessage(), e);
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

