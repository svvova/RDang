/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.Bukkit
 *  org.bukkit.Location
 *  org.bukkit.World
 *  org.bukkit.entity.Player
 */
package org.jamhardcore.dungeons;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.jamhardcore.dungeons.Json;

public final class Model {
    private Model() {
    }

    public static final class Pool {
        public int minCoins;
        public int maxCoins;
        public int picks;
        public List<ItemChoice> items = new ArrayList<ItemChoice>();
        public List<CaseChoice> cases = new ArrayList<CaseChoice>();
    }

    public record CaseChoice(String id, double weight) {
    }

    public static final class ItemChoice {
        public String id;
        public String material;
        public int min;
        public int max;
        public double weight;
        public List<Amount> amounts = new ArrayList<Amount>();
        public List<Book> books = new ArrayList<Book>();
    }

    public record Book(String enchantment, int level) {
    }

    public record Amount(int amount, double weight) {
    }

    public static final class Voucher {
        public String id = UUID.randomUUID().toString();
        public String reward;
        public String redeemer;
        public int amount;
        public Delivery state = Delivery.PENDING;
    }

    public static final class Reward {
        public String id = UUID.randomUUID().toString();
        public String event;
        public String owner;
        public String caseRole;
        public String caseId;
        public Kind kind;
        public int coins;
        public boolean claimed;
        public boolean forfeited;
        public Delivery coinState = Delivery.PENDING;
        public Delivery caseState = Delivery.PENDING;
        public List<GiftItem> items = new ArrayList<GiftItem>();
        public String voucherId;

        public boolean complete() {
            return !(this.coins != 0 && this.coinState != Delivery.DONE || this.caseRole != null && this.caseState != Delivery.DONE || !this.items.stream().allMatch(x -> x.remaining == 0));
        }
    }

    public static final class GiftItem {
        public String id = UUID.randomUUID().toString();
        public String data;
        public int remaining;
        public int planned;
        public Delivery state = Delivery.PENDING;
    }

    public record Combatant(String preset, Point spawn, int wave) {
    }

    public static final class Session {
        public String id = UUID.randomUUID().toString();
        public String arenaId;
        public Arena arena;
        public Theme theme;
        public Kind kind;
        public Phase phase = Phase.PREPARING;
        public long availableUntil;
        public long startedAt;
        public long endAt;
        public long claimUntil;
        public long cleanupAt;
        public long disconnectUntil;
        public long retryAfter;
        public long outsideSince;
        public long waveStartedAt;
        public int spawnedMobs;
        public int killedMobs;
        public Map<String, Combatant> combatants = new LinkedHashMap<String, Combatant>();
        public int wave;
        public int interval;
        public int duration;
        public String owner;
        public String activator;
        public String winner;
        public String rewardId;
        public String backup;
        public String regionId;
        public int reconnectSeconds;
        public int claimSeconds;
        public int lootSeconds;
        public int cleanupSeconds;
        public Pool rewardPool;
        public Map<String, String> caseMappings = new LinkedHashMap<String, String>();
        public Point lastOwner;
        public boolean disconnectedBefore;
        public boolean test;
        public boolean announced;
        public boolean cleanupAnnounced;
        public Set<String> offlinePlayers = new HashSet<String>();
        public Set<String> eligible = new LinkedHashSet<String>();
        public List<String> entities = new ArrayList<String>();

        public UUID ownerId() {
            return this.owner == null ? null : UUID.fromString(this.owner);
        }

        public Player ownerPlayer() {
            return this.owner == null ? null : Bukkit.getPlayer((UUID)this.ownerId());
        }

        public boolean hasPlayer(Player p) {
            return this.owner != null && this.owner.equals(p.getUniqueId().toString());
        }
    }

    public static final class Theme {
        public String id;
        public String name;
        public List<Map<String, Integer>> waves = new ArrayList<Map<String, Integer>>();
        public Map<String, MobSpec> mobs = new LinkedHashMap<String, MobSpec>();
    }

    public static final class MobSpec {
        public String entity;
        public String name;
        public String weapon;
        public boolean forceMelee;
        public boolean zombieEquipment;
    }

    public static final class Arena {
        public String id;
        public String name;
        public String theme = "mining";
        public Kind kind = Kind.PVE;
        public boolean enabled = false;
        public Point pos1;
        public Point pos2;
        public Point chest;
        public Point exit;
        public List<Spawn> points = new ArrayList<Spawn>();
        public Map<String, Point> sites = new LinkedHashMap<String, Point>();
        public boolean template = false;
        public String schematic;
        public Placement placement = Placement.GROUND;
        public List<String> worlds = new ArrayList<String>();

        public Box box() {
            return Box.of(this.pos1, this.pos2);
        }

        public Box playBox() {
            Box b = this.box();
            return this.kind == Kind.PVE ? new Box(b.world(), b.x1(), b.y1() - 2, b.z1(), b.x2(), b.y2() + 8, b.z2()) : b;
        }

        public List<Spawn> pointsFor(int wave, String mob) {
            List<Spawn> exact = this.points.stream().filter(p -> p.wave == wave && p.mob.equals(mob)).toList();
            if (!exact.isEmpty()) {
                return exact;
            }
            exact = this.points.stream().filter(p -> p.wave == 0 && p.mob.equals(mob)).toList();
            if (!exact.isEmpty()) {
                return exact;
            }
            exact = this.points.stream().filter(p -> p.wave == wave && p.mob.equals("any")).toList();
            if (!exact.isEmpty()) {
                return exact;
            }
            return this.points.stream().filter(p -> p.wave == 0 && p.mob.equals("any")).toList();
        }

        public Arena at(Point anchor) {
            Arena a = Json.copy(this, Arena.class);
            Point o = this.box().origin();
            double dx = anchor.x - o.x;
            double dy = anchor.y - o.y;
            double dz = anchor.z - o.z;
            a.pos1 = a.pos1.shift(dx, dy, dz, anchor.world);
            a.pos2 = a.pos2.shift(dx, dy, dz, anchor.world);
            if (a.chest != null) {
                a.chest = a.chest.shift(dx, dy, dz, anchor.world);
            }
            if (a.exit != null) {
                a.exit = a.exit.shift(dx, dy, dz, anchor.world);
            }
            a.points.forEach(p -> {
                if (p.point != null) {
                    p.point = p.point.shift(dx, dy, dz, anchor.world);
                }
            });
            return a;
        }
    }

    public static final class Spawn {
        public String id;
        public Point point;
        public int wave;
        public String mob = "any";

        public Spawn() {
        }

        public Spawn(String id, Point point, int wave, String mob) {
            this.id = id;
            this.point = point;
            this.wave = wave;
            this.mob = mob;
        }
    }

    public record Box(String world, int x1, int y1, int z1, int x2, int y2, int z2) {
        public static Box of(Point a, Point b) {
            if (a == null || b == null || !a.world.equals(b.world)) {
                throw new IllegalArgumentException("\u041e\u0431\u0435 \u0433\u0440\u0430\u043d\u0438\u0446\u044b \u0434\u043e\u043b\u0436\u043d\u044b \u0431\u044b\u0442\u044c \u0432 \u043e\u0434\u043d\u043e\u043c \u043c\u0438\u0440\u0435");
            }
            return new Box(a.world, (int)Math.floor(Math.min(a.x, b.x)), (int)Math.floor(Math.min(a.y, b.y)), (int)Math.floor(Math.min(a.z, b.z)), (int)Math.floor(Math.max(a.x, b.x)), (int)Math.floor(Math.max(a.y, b.y)), (int)Math.floor(Math.max(a.z, b.z)));
        }

        public boolean contains(Location p) {
            return p != null && this.world.equals(p.getWorld().getName()) && this.contains(p.getX(), p.getY(), p.getZ());
        }

        public boolean contains(double x, double y, double z) {
            return x >= (double)this.x1 && x < (double)(this.x2 + 1) && y >= (double)this.y1 && y < (double)(this.y2 + 1) && z >= (double)this.z1 && z < (double)(this.z2 + 1);
        }

        public boolean contains(Point p) {
            return p != null && this.world.equals(p.world) && this.contains(p.x, p.y, p.z);
        }

        public boolean intersects(Box b) {
            return this.world.equals(b.world) && this.x1 <= b.x2 && this.x2 >= b.x1 && this.y1 <= b.y2 && this.y2 >= b.y1 && this.z1 <= b.z2 && this.z2 >= b.z1;
        }

        public long volume() {
            return ((long)this.x2 - (long)this.x1 + 1L) * (long)(this.y2 - this.y1 + 1) * (long)(this.z2 - this.z1 + 1);
        }

        public Point origin() {
            return new Point(this.world, this.x1, this.y1, this.z1, 0.0f, 0.0f);
        }
    }

    public record Point(String world, double x, double y, double z, float yaw, float pitch) {
        public static Point of(Location l) {
            return new Point(l.getWorld().getName(), l.getX(), l.getY(), l.getZ(), l.getYaw(), l.getPitch());
        }

        public Location location() {
            World w = Bukkit.getWorld((String)this.world);
            return w == null ? null : new Location(w, this.x, this.y, this.z, this.yaw, this.pitch);
        }

        public Point shift(double dx, double dy, double dz, String w) {
            return new Point(w, this.x + dx, this.y + dy, this.z + dz, this.yaw, this.pitch);
        }

        public boolean sameBlock(Location l) {
            return l != null && this.world.equals(l.getWorld().getName()) && Point.floor(this.x) == l.getBlockX() && Point.floor(this.y) == l.getBlockY() && Point.floor(this.z) == l.getBlockZ();
        }

        private static int floor(double d) {
            return (int)Math.floor(d);
        }
    }

    public static enum Delivery {
        PENDING,
        IN_FLIGHT,
        DONE,
        REVIEW;

    }

    public static enum Phase {
        PREPARING,
        AVAILABLE,
        RUNNING,
        REWARD,
        CLAIMED,
        CLEANUP,
        RESTORE_FAILED;

    }

    public static enum Placement {
        STATIC("\u041d\u0430 \u0438\u0441\u0445\u043e\u0434\u043d\u043e\u043c \u043c\u0435\u0441\u0442\u0435", "\u0417\u0430\u043f\u0443\u0441\u043a \u0443 \u0438\u0441\u0445\u043e\u0434\u043d\u043e\u0439 \u043f\u043e\u0441\u0442\u0440\u043e\u0439\u043a\u0438 \u0438\u043b\u0438 \u043d\u0430 \u0437\u0430\u0434\u0430\u043d\u043d\u043e\u0439 \u043f\u043b\u043e\u0449\u0430\u0434\u043a\u0435"),
        GROUND("\u041d\u0430 \u0437\u0435\u043c\u043b\u0435", "\u0421\u043b\u0443\u0447\u0430\u0439\u043d\u0430\u044f \u0434\u043e\u0441\u0442\u0430\u0442\u043e\u0447\u043d\u043e \u0440\u043e\u0432\u043d\u0430\u044f \u0441\u0443\u0445\u0430\u044f \u043f\u043e\u0432\u0435\u0440\u0445\u043d\u043e\u0441\u0442\u044c"),
        AIR("\u0412 \u0432\u043e\u0437\u0434\u0443\u0445\u0435", "\u0421\u0432\u043e\u0431\u043e\u0434\u043d\u043e\u0435 \u043c\u0435\u0441\u0442\u043e \u043d\u0430\u0434 \u043f\u043e\u0432\u0435\u0440\u0445\u043d\u043e\u0441\u0442\u044c\u044e \u043c\u0438\u0440\u0430"),
        WATER("\u041d\u0430 \u0432\u043e\u0434\u0435", "\u041f\u043e\u0432\u0435\u0440\u0445\u043d\u043e\u0441\u0442\u044c \u0432\u043e\u0434\u043e\u0451\u043c\u0430; \u043f\u043e\u0434\u0445\u043e\u0434\u0438\u0442 \u0434\u043b\u044f \u043a\u043e\u0440\u0430\u0431\u043b\u044f"),
        UNDERWATER("\u041f\u043e\u0434 \u0432\u043e\u0434\u043e\u0439", "\u0414\u043d\u043e \u0434\u043e\u0441\u0442\u0430\u0442\u043e\u0447\u043d\u043e \u0433\u043b\u0443\u0431\u043e\u043a\u043e\u0433\u043e \u0432\u043e\u0434\u043e\u0451\u043c\u0430");

        public final String label;
        public final String description;

        private Placement(String label, String description) {
            this.label = label;
            this.description = description;
        }
    }

    public static enum Kind {
        PVE,
        PVP;

    }
}

