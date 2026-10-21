/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.kyori.adventure.util.TriState
 *  org.bukkit.Bukkit
 *  org.bukkit.Location
 *  org.bukkit.Material
 *  org.bukkit.NamespacedKey
 *  org.bukkit.World
 *  org.bukkit.entity.Entity
 *  org.bukkit.entity.EntityType
 *  org.bukkit.entity.LivingEntity
 *  org.bukkit.entity.Mob
 *  org.bukkit.entity.Player
 *  org.bukkit.entity.Raider
 *  org.bukkit.entity.Vex
 *  org.bukkit.entity.Zombie
 *  org.bukkit.inventory.EntityEquipment
 *  org.bukkit.inventory.EquipmentSlot
 *  org.bukkit.inventory.ItemStack
 *  org.bukkit.persistence.PersistentDataType
 *  org.bukkit.plugin.Plugin
 *  org.bukkit.plugin.java.JavaPlugin
 */
package org.jamhardcore.dungeons;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.kyori.adventure.util.TriState;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Raider;
import org.bukkit.entity.Vex;
import org.bukkit.entity.Zombie;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.jamhardcore.dungeons.Configs;
import org.jamhardcore.dungeons.Messages;
import org.jamhardcore.dungeons.Model;

public final class Spawner {
    private final JavaPlugin p;
    private final Configs c;
    public final NamespacedKey eventKey;
    public final NamespacedKey mobKey;
    private final Map<UUID, Long> attackAt = new HashMap<UUID, Long>();

    public Spawner(JavaPlugin p, Configs c) {
        this.p = p;
        this.c = c;
        this.eventKey = new NamespacedKey((Plugin)p, "event");
        this.mobKey = new NamespacedKey((Plugin)p, "preset");
    }

    public String event(Entity e) {
        return e == null ? null : (String)e.getPersistentDataContainer().get(this.eventKey, PersistentDataType.STRING);
    }

    public boolean tagged(Entity e) {
        return this.event(e) != null;
    }

    boolean ownsSpawn(Model.Session s, Entity entity) {
        if (s == null || s.kind != Model.Kind.PVE || s.phase != Model.Phase.RUNNING) {
            return false;
        }
        String uuid = entity.getUniqueId().toString();
        Model.Combatant combatant = s.combatants.get(uuid);
        String preset = (String)entity.getPersistentDataContainer().get(this.mobKey, PersistentDataType.STRING);
        Model.MobSpec spec = s.theme == null ? null : s.theme.mobs.get(preset);
        return s.entities.contains(uuid) && combatant != null && combatant.preset().equals(preset) && spec != null && spec.entity.equals(entity.getType().name());
    }

    public void tag(Entity e, Model.Session s, String mob) {
        e.getPersistentDataContainer().set(this.eventKey, PersistentDataType.STRING, s.id);
        e.getPersistentDataContainer().set(this.mobKey, PersistentDataType.STRING, mob);
        s.entities.add(e.getUniqueId().toString());
    }

    public void validate(Model.Arena a, Model.Theme theme) {
        if (a.kind == Model.Kind.PVP) {
            return;
        }
        for (int wave = 1; wave <= 5; ++wave) {
            for (String id : theme.waves.get(wave - 1).keySet()) {
                List<Model.Spawn> points = a.pointsFor(wave, id);
                if (points.isEmpty()) {
                    throw new IllegalArgumentException("\u041d\u0435\u0442 \u0442\u043e\u0447\u043a\u0438: \u0432\u043e\u043b\u043d\u0430 " + wave + ", \u043c\u043e\u0431 " + id);
                }
                for (Model.Spawn point : points) {
                    if (point.point == null || !a.box().contains(point.point)) {
                        throw new IllegalArgumentException("\u0422\u043e\u0447\u043a\u0430 \u0432\u043d\u0435 \u0440\u0435\u0433\u0438\u043e\u043d\u0430: " + point.id);
                    }
                    this.checkPoint(point.point, theme.mobs.get(id));
                }
            }
        }
    }

    private void checkPoint(Model.Point point, Model.MobSpec spec) {
        Location l = point.location();
        if (l == null) {
            throw new IllegalArgumentException("\u041c\u0438\u0440 \u0442\u043e\u0447\u043a\u0438 \u043d\u0435 \u0437\u0430\u0433\u0440\u0443\u0436\u0435\u043d");
        }
        if (!l.getWorld().getWorldBorder().isInside(l)) {
            throw new IllegalArgumentException("\u0422\u043e\u0447\u043a\u0430 \u0437\u0430 \u0433\u0440\u0430\u043d\u0438\u0446\u0435\u0439 \u043c\u0438\u0440\u0430");
        }
        if (l.getBlock().getType().isSolid() || l.clone().add(0.0, 1.0, 0.0).getBlock().getType().isSolid()) {
            throw new IllegalArgumentException("\u0422\u043e\u0447\u043a\u0430 \u0437\u0430\u0431\u043b\u043e\u043a\u0438\u0440\u043e\u0432\u0430\u043d\u0430: " + point.x() + ", " + point.y() + ", " + point.z());
        }
        if ((spec.entity.equals("GUARDIAN") || spec.entity.equals("ELDER_GUARDIAN")) && l.getBlock().getType() != Material.WATER) {
            throw new IllegalArgumentException("\u0421\u0442\u0440\u0430\u0436\u0443 \u043d\u0443\u0436\u043d\u0430 \u0442\u043e\u0447\u043a\u0430 \u0432 \u0432\u043e\u0434\u0435");
        }
    }

    public void wave(Model.Session s, int wave, int globalLiving) {
        Map<String, Integer> row = s.theme.waves.get(wave - 1);
        int wanted = row.values().stream().mapToInt(Integer::intValue).sum();
        long living = s.entities.stream().map(UUID::fromString).map(Bukkit::getEntity).filter(e -> e instanceof LivingEntity && !e.isDead()).count();
        if (living + (long)wanted > (long)this.c.limit("mobs-per-event") || globalLiving + wanted > this.c.limit("total-mobs")) {
            throw new IllegalStateException("\u041d\u0435\u0434\u043e\u0441\u0442\u0430\u0442\u043e\u0447\u043d\u043e \u043e\u0431\u0449\u0435\u0433\u043e \u043b\u0438\u043c\u0438\u0442\u0430 \u043c\u043e\u0431\u043e\u0432 \u0434\u043b\u044f \u043f\u043e\u043b\u043d\u043e\u0439 \u0432\u043e\u043b\u043d\u044b");
        }
        ArrayList<Entity> created = new ArrayList<Entity>();
        HashSet<String> before = new HashSet<String>(s.entities);
        int beforeCount = s.spawnedMobs;
        HashSet<String> beforeCombat = new HashSet<String>(s.combatants.keySet());
        try {
            for (Map.Entry<String, Integer> group : row.entrySet()) {
                List<Model.Spawn> points = s.arena.pointsFor(wave, group.getKey());
                if (points.isEmpty()) {
                    throw new IllegalStateException("\u041d\u0435\u0442 \u0442\u043e\u0447\u0435\u043a \u0434\u043b\u044f \u0432\u043e\u043b\u043d\u044b " + wave + ", \u043c\u043e\u0431 " + group.getKey());
                }
                for (int i = 0; i < group.getValue(); ++i) {
                    created.add(this.spawnOne(s, group.getKey(), points.get((int)(i % points.size())).point, wave, i));
                }
            }
        }
        catch (Exception ex) {
            s.combatants.keySet().removeIf(id -> !beforeCombat.contains(id));
            s.spawnedMobs = beforeCount;
            created.forEach(Entity::remove);
            for (String id2 : new ArrayList<String>(s.entities)) {
                if (before.contains(id2)) continue;
                Entity entity = Bukkit.getEntity((UUID)UUID.fromString(id2));
                if (entity != null) {
                    entity.remove();
                }
                this.attackAt.remove(UUID.fromString(id2));
            }
            s.entities.removeIf(id -> !before.contains(id));
            throw ex;
        }
    }

    private Entity spawnOne(Model.Session s, String preset, Model.Point point, int wave, int ordinal) {
        Model.MobSpec spec = s.theme.mobs.get(preset);
        if (spec == null || List.of("CAMEL_HUSK", "CREEPER").contains(spec.entity)) {
            throw new IllegalStateException("\u041c\u043e\u0431 \u043d\u0435 \u0440\u0430\u0437\u0440\u0435\u0448\u0451\u043d \u0432 \u0442\u0435\u043c\u0435: " + preset);
        }
        this.checkPoint(point, spec);
        Entity entity = Objects.requireNonNull(point.location()).getWorld().spawn(point.location(), Objects.requireNonNull(EntityType.valueOf((String)spec.entity).getEntityClass()), false, mob -> this.configure((Entity)mob, s, spec, preset, point, wave, ordinal));
        if (!entity.isValid()) {
            throw new IllegalStateException("\u0421\u043f\u0430\u0432\u043d \u043e\u0442\u043c\u0435\u043d\u0451\u043d \u0434\u0440\u0443\u0433\u0438\u043c \u043f\u043b\u0430\u0433\u0438\u043d\u043e\u043c: " + preset);
        }
        return entity;
    }

    public boolean defeated(Model.Session s, Entity entity) {
        if (s.combatants.remove(entity.getUniqueId().toString()) == null) {
            return false;
        }
        ++s.killedMobs;
        return true;
    }

    public void restoreCombatant(Model.Session s, String id) {
        Model.Combatant combatant = s.combatants.get(id);
        if (s.phase != Model.Phase.RUNNING || combatant == null) {
            return;
        }
        Entity original = Bukkit.getEntity((UUID)UUID.fromString(id));
        if (original != null && original.isValid() && !original.isDead()) {
            return;
        }
        int beforeCount = s.spawnedMobs;
        HashSet<String> before = new HashSet<String>(s.combatants.keySet());
        try {
            Entity replacement = this.spawnOne(s, combatant.preset(), combatant.spawn(), combatant.wave(), 0);
            s.combatants.remove(id);
            s.entities.remove(id);
            s.spawnedMobs = beforeCount;
            this.p.getLogger().warning("\u0414\u0430\u043d\u0436 " + s.arenaId + ": \u0432\u043e\u0441\u0441\u0442\u0430\u043d\u043e\u0432\u043b\u0435\u043d \u043f\u0440\u0435\u0436\u0434\u0435\u0432\u0440\u0435\u043c\u0435\u043d\u043d\u043e \u0443\u0434\u0430\u043b\u0451\u043d\u043d\u044b\u0439 \u043c\u043e\u0431 " + combatant.preset());
        }
        catch (RuntimeException ex) {
            for (String fresh : new ArrayList<String>(s.combatants.keySet())) {
                if (before.contains(fresh)) continue;
                s.combatants.remove(fresh);
                Entity entity = Bukkit.getEntity((UUID)UUID.fromString(fresh));
                if (entity != null) {
                    entity.remove();
                }
                s.entities.remove(fresh);
            }
            s.spawnedMobs = beforeCount;
            this.p.getLogger().warning("\u041d\u0435 \u0443\u0434\u0430\u043b\u043e\u0441\u044c \u0432\u043e\u0441\u0441\u0442\u0430\u043d\u043e\u0432\u0438\u0442\u044c \u043c\u043e\u0431\u0430 \u0434\u0430\u043d\u0436\u0430 " + s.arenaId + ": " + ex.getMessage());
        }
    }

    private void configure(Entity e, Model.Session s, Model.MobSpec spec, String id, Model.Point spawn, int wave, int ordinal) {
        this.tag(e, s, id);
        s.combatants.put(e.getUniqueId().toString(), new Model.Combatant(id, spawn, wave));
        ++s.spawnedMobs;
        e.customName(Messages.color(spec.name));
        e.setCustomNameVisible(false);
        e.setPersistent(true);
        if (e instanceof LivingEntity) {
            LivingEntity l = (LivingEntity)e;
            l.setRemoveWhenFarAway(false);
            l.setCanPickupItems(false);
            EntityEquipment gear = l.getEquipment();
            if (gear != null) {
                gear.clear();
                if (spec.weapon != null) {
                    gear.setItemInMainHand(new ItemStack(Material.valueOf((String)spec.weapon)));
                } else if (List.of("SKELETON", "BOGGED", "PARCHED").contains(spec.entity)) {
                    gear.setItemInMainHand(new ItemStack(Material.BOW));
                } else if (spec.entity.equals("PILLAGER")) {
                    gear.setItemInMainHand(new ItemStack(Material.CROSSBOW));
                } else if (spec.entity.equals("VINDICATOR")) {
                    gear.setItemInMainHand(new ItemStack(Material.IRON_AXE));
                }
                if (spec.entity.equals("ZOMBIE") && spec.zombieEquipment) {
                    this.equipZombie(gear, ordinal);
                }
                for (EquipmentSlot slot : EquipmentSlot.values()) {
                    try {
                        gear.setDropChance(slot, 0.0f);
                    }
                    catch (IllegalArgumentException illegalArgumentException) {
                        // empty catch block
                    }
                }
            }
        }
        this.configureMob(e, s, spec);
    }

    void equipZombie(EntityEquipment gear, int ordinal) {
        int period = (int)Math.max(1L, Math.round(1.0 / Math.max(0.01, this.c.main().getDouble("zombie-equipment.armored-fraction", 0.5))));
        if (ordinal % period == 0) {
            List weapons;
            List armor = this.c.main().getStringList("zombie-equipment.armor");
            if (armor.size() == 4) {
                gear.setHelmet(new ItemStack(Material.valueOf((String)((String)armor.get(0)))));
                gear.setChestplate(new ItemStack(Material.valueOf((String)((String)armor.get(1)))));
                gear.setLeggings(new ItemStack(Material.valueOf((String)((String)armor.get(2)))));
                gear.setBoots(new ItemStack(Material.valueOf((String)((String)armor.get(3)))));
            }
            if (!(weapons = this.c.main().getStringList("zombie-equipment.weapons")).isEmpty()) {
                gear.setItemInMainHand(new ItemStack(Material.valueOf((String)((String)weapons.get(ordinal / period % weapons.size())))));
            }
        }
    }

    private void configureMob(Entity e, Model.Session s, Model.MobSpec spec) {
        if (e instanceof Zombie) {
            Zombie zombie = (Zombie)e;
            zombie.setAdult();
        }
        if (e instanceof Raider) {
            Raider r = (Raider)e;
            r.setCanJoinRaid(false);
            r.setPatrolLeader(false);
            r.setPatrolTarget(null);
        }
        if (e instanceof Mob) {
            Mob mob = (Mob)e;
            try {
                Mob.class.getMethod("setDespawnInPeacefulOverride", TriState.class).invoke(mob, TriState.FALSE);
            } catch (Throwable ignored) {
            }
            if (spec.forceMelee) {
                Bukkit.getMobGoals().removeAllGoals(mob);
            }
            mob.setTarget((LivingEntity)s.ownerPlayer());
            mob.setAggressive(true);
        }
        if (e instanceof Vex) {
            Vex vex = (Vex)e;
            vex.setLimitedLifetime(false);
        }
    }

    public void pulse(Model.Session s, long now) {
        for (String id : new ArrayList<String>(s.entities)) {
            Entity entity = Bukkit.getEntity((UUID)UUID.fromString(id));
            if (entity == null || !entity.isValid()) {
                s.entities.remove(id);
                this.attackAt.remove(UUID.fromString(id));
                continue;
            }
            if (!(entity instanceof LivingEntity) && !s.arena.box().contains(entity.getLocation())) {
                entity.remove();
                continue;
            }
            if (entity instanceof LivingEntity && !s.arena.playBox().contains(entity.getLocation()) && entity.getVehicle() == null) {
                Model.Point home;
                Model.Combatant record = s.combatants.get(id);
                Model.Point point = home = record == null ? (Model.Point)s.arena.points.stream().findFirst().map(pt -> pt.point).orElse(null) : record.spawn();
                if (home != null && home.location() != null) {
                    entity.teleport(home.location());
                }
            }
            if (!(entity instanceof Mob)) continue;
            Mob mob = (Mob)entity;
            Player owner = s.ownerPlayer();
            if (owner == null || owner.isDead() || !s.arena.playBox().contains(owner.getLocation())) continue;
            mob.setTarget((LivingEntity)owner);
            String preset = (String)entity.getPersistentDataContainer().get(this.mobKey, PersistentDataType.STRING);
            Model.MobSpec spec = s.theme.mobs.get(preset);
            if (spec == null || !spec.forceMelee) continue;
            mob.getPathfinder().moveTo((LivingEntity)owner, 1.1);
            if (!(mob.getLocation().distanceSquared(owner.getLocation()) < 5.0) || !mob.hasLineOfSight((Entity)owner) || now < this.attackAt.getOrDefault(mob.getUniqueId(), 0L)) continue;
            mob.attack((Entity)owner);
            this.attackAt.put(mob.getUniqueId(), now + 1000L);
        }
    }

    public void clear(Model.Session s) {
        s.combatants.clear();
        for (World w : Bukkit.getWorlds()) {
            for (Entity e : w.getEntities()) {
                if (!s.id.equals(this.event(e))) continue;
                this.attackAt.remove(e.getUniqueId());
                e.remove();
            }
        }
        s.entities.clear();
    }
}

