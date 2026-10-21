/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.destroystokyo.paper.event.entity.EntityAddToWorldEvent
 *  io.papermc.paper.event.entity.ElderGuardianAppearanceEvent
 *  org.bukkit.Bukkit
 *  org.bukkit.GameMode
 *  org.bukkit.Location
 *  org.bukkit.Material
 *  org.bukkit.block.Block
 *  org.bukkit.command.CommandSender
 *  org.bukkit.entity.AbstractArrow
 *  org.bukkit.entity.AbstractArrow$PickupStatus
 *  org.bukkit.entity.AreaEffectCloud
 *  org.bukkit.entity.ArmorStand
 *  org.bukkit.entity.EnderCrystal
 *  org.bukkit.entity.Entity
 *  org.bukkit.entity.EvokerFangs
 *  org.bukkit.entity.Hanging
 *  org.bukkit.entity.LivingEntity
 *  org.bukkit.entity.Mob
 *  org.bukkit.entity.Player
 *  org.bukkit.entity.Projectile
 *  org.bukkit.entity.Shulker
 *  org.bukkit.entity.Spellcaster$Spell
 *  org.bukkit.event.EventHandler
 *  org.bukkit.event.EventPriority
 *  org.bukkit.event.Listener
 *  org.bukkit.event.block.Action
 *  org.bukkit.event.block.BlockBreakEvent
 *  org.bukkit.event.block.BlockBurnEvent
 *  org.bukkit.event.block.BlockDamageEvent
 *  org.bukkit.event.block.BlockExplodeEvent
 *  org.bukkit.event.block.BlockFertilizeEvent
 *  org.bukkit.event.block.BlockFromToEvent
 *  org.bukkit.event.block.BlockIgniteEvent
 *  org.bukkit.event.block.BlockMultiPlaceEvent
 *  org.bukkit.event.block.BlockPistonExtendEvent
 *  org.bukkit.event.block.BlockPistonRetractEvent
 *  org.bukkit.event.block.BlockPlaceEvent
 *  org.bukkit.event.block.BlockSpreadEvent
 *  org.bukkit.event.block.SpongeAbsorbEvent
 *  org.bukkit.event.entity.AreaEffectCloudApplyEvent
 *  org.bukkit.event.entity.CreatureSpawnEvent
 *  org.bukkit.event.entity.EntityChangeBlockEvent
 *  org.bukkit.event.entity.EntityCombustByBlockEvent
 *  org.bukkit.event.entity.EntityCombustByEntityEvent
 *  org.bukkit.event.entity.EntityCombustEvent
 *  org.bukkit.event.entity.EntityDamageByEntityEvent
 *  org.bukkit.event.entity.EntityDamageEvent
 *  org.bukkit.event.entity.EntityDeathEvent
 *  org.bukkit.event.entity.EntityExplodeEvent
 *  org.bukkit.event.entity.EntityMountEvent
 *  org.bukkit.event.entity.EntityPlaceEvent
 *  org.bukkit.event.entity.EntityPotionEffectEvent
 *  org.bukkit.event.entity.EntityRemoveEvent
 *  org.bukkit.event.entity.EntityRemoveEvent$Cause
 *  org.bukkit.event.entity.EntitySpawnEvent
 *  org.bukkit.event.entity.EntitySpellCastEvent
 *  org.bukkit.event.entity.EntityTargetLivingEntityEvent
 *  org.bukkit.event.entity.EntityTeleportEvent
 *  org.bukkit.event.entity.EntityTransformEvent
 *  org.bukkit.event.entity.LingeringPotionSplashEvent
 *  org.bukkit.event.entity.PlayerDeathEvent
 *  org.bukkit.event.entity.PotionSplashEvent
 *  org.bukkit.event.entity.ProjectileLaunchEvent
 *  org.bukkit.event.hanging.HangingBreakEvent
 *  org.bukkit.event.inventory.InventoryMoveItemEvent
 *  org.bukkit.event.inventory.InventoryOpenEvent
 *  org.bukkit.event.player.PlayerArmorStandManipulateEvent
 *  org.bukkit.event.player.PlayerBedEnterEvent
 *  org.bukkit.event.player.PlayerBucketEmptyEvent
 *  org.bukkit.event.player.PlayerBucketFillEvent
 *  org.bukkit.event.player.PlayerGameModeChangeEvent
 *  org.bukkit.event.player.PlayerInteractAtEntityEvent
 *  org.bukkit.event.player.PlayerInteractEntityEvent
 *  org.bukkit.event.player.PlayerInteractEvent
 *  org.bukkit.event.player.PlayerJoinEvent
 *  org.bukkit.event.player.PlayerMoveEvent
 *  org.bukkit.event.player.PlayerPortalEvent
 *  org.bukkit.event.player.PlayerQuitEvent
 *  org.bukkit.event.player.PlayerTeleportEvent
 *  org.bukkit.event.vehicle.VehicleMoveEvent
 *  org.bukkit.event.world.PortalCreateEvent
 *  org.bukkit.event.world.StructureGrowEvent
 *  org.bukkit.event.world.WorldLoadEvent
 *  org.bukkit.inventory.EquipmentSlot
 *  org.bukkit.plugin.Plugin
 *  org.bukkit.plugin.java.JavaPlugin
 *  org.bukkit.potion.PotionEffect
 *  org.bukkit.potion.PotionEffectType
 *  org.bukkit.projectiles.ProjectileSource
 */
package org.jamhardcore.dungeons;

import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent;
import io.papermc.paper.event.entity.ElderGuardianAppearanceEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EvokerFangs;
import org.bukkit.entity.Hanging;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Shulker;
import org.bukkit.entity.Spellcaster;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFertilizeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockMultiPlaceEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.block.SpongeAbsorbEvent;
import org.bukkit.event.entity.AreaEffectCloudApplyEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityCombustByBlockEvent;
import org.bukkit.event.entity.EntityCombustByEntityEvent;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityMountEvent;
import org.bukkit.event.entity.EntityPlaceEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.entity.EntitySpellCastEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.EntityTeleportEvent;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.event.entity.LingeringPotionSplashEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.vehicle.VehicleMoveEvent;
import org.bukkit.event.world.PortalCreateEvent;
import org.bukkit.event.world.StructureGrowEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.projectiles.ProjectileSource;
import org.jamhardcore.dungeons.Engine;
import org.jamhardcore.dungeons.Model;
import org.jamhardcore.dungeons.Rules;

public final class Guards
implements Listener {
    private final Engine e;
    private final JavaPlugin p;
    private final Map<UUID, Fatigue> fatigue = new HashMap<UUID, Fatigue>();
    private final Map<UUID, Long> pushedAt = new HashMap<UUID, Long>();

    public Guards(JavaPlugin p, Engine e) {
        this.p = p;
        this.e = e;
    }

    private boolean protectedAt(Location l) {
        return this.e.buildProtected(l);
    }

    private boolean canEditSource(Player player, Location location) {
        return this.e.canEditBuilding(player, location);
    }

    @EventHandler(priority=EventPriority.LOWEST, ignoreCancelled=true)
    public void earlyBreak(BlockBreakEvent event) {
        this.blockBreak(event);
    }

    @EventHandler(priority=EventPriority.LOWEST, ignoreCancelled=true)
    public void damageBlock(BlockDamageEvent event) {
        if (this.protectedAt(event.getBlock().getLocation()) && !this.canEditSource(event.getPlayer(), event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void damageBlockLate(BlockDamageEvent event) {
        this.damageBlock(event);
    }

    @EventHandler(priority=EventPriority.LOWEST, ignoreCancelled=true)
    public void earlyPlace(BlockPlaceEvent event) {
        this.blockPlace(event);
    }

    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
    public void gameMode(PlayerGameModeChangeEvent event) {
        if (event.getNewGameMode() != GameMode.CREATIVE) {
            this.e.stopEditingBuilding(event.getPlayer());
        }
    }

    @EventHandler(priority=EventPriority.MONITOR)
    public void worldLoaded(WorldLoadEvent event) {
        String world = event.getWorld().getName();
        Bukkit.getScheduler().runTask((Plugin)this.p, () -> {
            for (Model.Arena arena : this.e.c.arenas.values()) {
                if (arena.pos1 == null || arena.pos2 == null || !world.equals(arena.pos1.world()) || !world.equals(arena.pos2.world())) continue;
                try {
                    this.e.regions.repair(arena);
                }
                catch (Exception error) {
                    this.p.getLogger().log(Level.WARNING, "\u041f\u0440\u0438\u0432\u0430\u0442 " + arena.id + " \u0432 \u043c\u0438\u0440\u0435 " + world, error);
                }
            }
            for (Model.Session session : this.e.sessions.values()) {
                if (!world.equals(session.arena.pos1.world())) continue;
                try {
                    this.e.regions.create(session);
                }
                catch (Exception error) {
                    this.p.getLogger().log(Level.WARNING, "\u041f\u0440\u0438\u0432\u0430\u0442 \u0441\u043e\u0431\u044b\u0442\u0438\u044f " + session.arenaId + " \u0432 \u043c\u0438\u0440\u0435 " + world, error);
                }
            }
        });
    }

    private boolean exclusive(Model.Session s) {
        return s != null && s.kind == Model.Kind.PVE && (s.phase == Model.Phase.RUNNING || s.phase == Model.Phase.REWARD || s.phase == Model.Phase.CLAIMED);
    }

    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void click(PlayerInteractEvent ev) {
        Block b = ev.getClickedBlock();
        if (b == null) {
            return;
        }
        Model.Session s = this.e.chest(b.getLocation());
        if (s == null) {
            return;
        }
        ev.setCancelled(true);
        if (ev.getHand() == EquipmentSlot.HAND && ev.getAction() == Action.RIGHT_CLICK_BLOCK) {
            try {
                this.e.click(s, ev.getPlayer());
            }
            catch (Exception ex) {
                this.e.msg.error((CommandSender)ev.getPlayer(), ex.getMessage());
                this.p.getLogger().log(Level.WARNING, "\u041d\u0430\u0436\u0430\u0442\u0438\u0435 \u0441\u0443\u043d\u0434\u0443\u043a\u0430 " + s.arenaId, ex);
            }
        }
    }

    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void move(PlayerMoveEvent ev) {
        Location to = ev.getTo();
        if (to == null || ev.getFrom().getWorld().equals((Object)to.getWorld()) && ev.getFrom().distanceSquared(to) < 1.0E-4) {
            return;
        }
        Model.Session target = this.e.at(to);
        if (!(target == null || target.phase != Model.Phase.PREPARING && target.phase != Model.Phase.RESTORE_FAILED || target.arena.playBox().contains(ev.getFrom()) || this.e.bypass(ev.getPlayer()))) {
            ev.setCancelled(true);
            return;
        }
        Model.Session wall = this.e.repels(ev.getPlayer(), to);
        if (wall != null) {
            if (wall.arena.playBox().contains(to)) {
                ev.setCancelled(true);
            }
            this.repel(wall, ev.getPlayer());
        }
    }

    private void repel(Model.Session s, Player player) {
        long now = System.currentTimeMillis();
        if (now - this.pushedAt.getOrDefault(player.getUniqueId(), 0L) < 250L) {
            return;
        }
        this.pushedAt.put(player.getUniqueId(), now);
        Bukkit.getScheduler().runTask((Plugin)this.p, () -> {
            if (!player.isOnline() || player.isDead() || this.e.repels(player, player.getLocation()) != s) {
                return;
            }
            player.leaveVehicle();
            player.setVelocity(Rules.pushDirection(s.arena.playBox(), player.getX(), player.getZ(), this.e.c.main().getDouble("protection.pve-pushback-strength", 0.85)));
            if (s.arena.playBox().contains(player.getLocation())) {
                this.e.eject(s, player);
            }
        });
    }

    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void teleport(PlayerTeleportEvent ev) {
        this.move((PlayerMoveEvent)ev);
    }

    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void portal(PlayerPortalEvent ev) {
        this.move((PlayerMoveEvent)ev);
    }

    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
    public void moved(PlayerMoveEvent ev) {
        if (ev.getTo() != null) {
            this.e.moved(ev.getPlayer(), ev.getFrom(), ev.getTo());
            this.removeFatigue(ev.getPlayer(), ev.getTo());
        }
    }

    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
    public void teleported(PlayerTeleportEvent ev) {
        this.moved((PlayerMoveEvent)ev);
    }

    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
    public void portalled(PlayerPortalEvent ev) {
        this.moved((PlayerMoveEvent)ev);
    }

    @EventHandler(priority=EventPriority.MONITOR)
    public void death(PlayerDeathEvent ev) {
        this.e.died(ev.getEntity());
        this.fatigue.remove(ev.getEntity().getUniqueId());
    }

    @EventHandler(priority=EventPriority.MONITOR)
    public void quit(PlayerQuitEvent ev) {
        this.e.stopEditingBuilding(ev.getPlayer());
        this.pushedAt.remove(ev.getPlayer().getUniqueId());
        this.e.quit(ev.getPlayer());
        this.removeFatigue(ev.getPlayer(), null);
    }

    @EventHandler(priority=EventPriority.MONITOR)
    public void join(PlayerJoinEvent ev) {
        long joinedAt = System.currentTimeMillis();
        Bukkit.getScheduler().runTask((Plugin)this.p, () -> {
            Player pl = ev.getPlayer();
            if (!pl.isOnline()) {
                return;
            }
            try {
                this.e.joined(pl, joinedAt);
            }
            catch (RuntimeException error) {
                this.p.getLogger().log(Level.WARNING, "\u0412\u043e\u0437\u0432\u0440\u0430\u0442 \u0438\u0433\u0440\u043e\u043a\u0430 " + String.valueOf(pl.getUniqueId()), error);
                this.e.msg.error((CommandSender)pl, "\u041d\u0435 \u0443\u0434\u0430\u043b\u043e\u0441\u044c \u043f\u043e\u0434\u0442\u0432\u0435\u0440\u0434\u0438\u0442\u044c \u0432\u043e\u0437\u0432\u0440\u0430\u0442 \u0438\u0437 \u0434\u0430\u043d\u0436\u0430. \u0421\u043e\u043e\u0431\u0449\u0438 \u0430\u0434\u043c\u0438\u043d\u0438\u0441\u0442\u0440\u0430\u0442\u043e\u0440\u0443.");
            }
            Model.Session at = this.e.at(pl.getLocation());
            if (this.exclusive(at) && !at.hasPlayer(pl) && !this.e.bypass(pl)) {
                this.e.eject(at, pl);
            }
            try {
                if (!this.e.rewards.pending(pl.getUniqueId()).isEmpty()) {
                    this.e.msg.send((CommandSender)pl, "pending");
                }
            }
            catch (RuntimeException error) {
                this.p.getLogger().log(Level.WARNING, "\u041d\u0435 \u0443\u0434\u0430\u043b\u043e\u0441\u044c \u043f\u0440\u043e\u0432\u0435\u0440\u0438\u0442\u044c \u043d\u0430\u0433\u0440\u0430\u0434\u044b \u043f\u0440\u0438 \u0432\u0445\u043e\u0434\u0435 " + String.valueOf(pl.getUniqueId()), error);
            }
        });
    }

    private Entity source(Entity entity) {
        AreaEffectCloud cloud;
        EvokerFangs f;
        Projectile projectile;
        ProjectileSource projectileSource;
        if (entity instanceof Projectile && (projectileSource = (projectile = (Projectile)entity).getShooter()) instanceof Entity) {
            Entity shooter = (Entity)projectileSource;
            return shooter;
        }
        if (entity instanceof EvokerFangs && (f = (EvokerFangs)entity).getOwner() != null) {
            return f.getOwner();
        }
        if (entity instanceof AreaEffectCloud && (projectileSource = (cloud = (AreaEffectCloud)entity).getSource()) instanceof Entity) {
            Entity owner = (Entity)projectileSource;
            return owner;
        }
        return entity;
    }

    private boolean allowHit(Entity victim, Entity actual) {
        Entity attacker = this.source(actual);
        Model.Session vs = this.e.at(victim.getLocation());
        Model.Session as = this.e.byEvent(this.e.mobs.event(attacker));
        if (as != null && as.kind == Model.Kind.PVE) {
            Player pl;
            return as.phase == Model.Phase.RUNNING && victim instanceof Player && as.hasPlayer(pl = (Player)victim) && as.arena.playBox().contains(pl.getLocation());
        }
        Model.Session attackerRegion = this.e.at(attacker.getLocation());
        if (attacker instanceof Player && this.exclusive(attackerRegion) && (!this.e.mobs.tagged(victim) || !attackerRegion.id.equals(this.e.mobs.event(victim)))) {
            return false;
        }
        if (this.exclusive(vs)) {
            if (victim instanceof Player) {
                return false;
            }
            if (this.e.mobs.tagged(victim)) {
                Player pl;
                return attacker instanceof Player && vs.hasPlayer(pl = (Player)attacker) && vs.arena.playBox().contains(pl.getLocation());
            }
        }
        return true;
    }

    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void damage(EntityDamageByEntityEvent ev) {
        if (!this.allowHit(ev.getEntity(), ev.getDamager())) {
            ev.setCancelled(true);
        }
    }

    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void target(EntityTargetLivingEntityEvent ev) {
        Player pl;
        LivingEntity livingEntity;
        Model.Session s = this.e.byEvent(this.e.mobs.event(ev.getEntity()));
        if (!(s == null || s.kind != Model.Kind.PVE || ev.getTarget() != null && (livingEntity = ev.getTarget()) instanceof Player && s.hasPlayer(pl = (Player)livingEntity) && s.arena.playBox().contains(pl.getLocation()))) {
            ev.setCancelled(true);
        }
    }

    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void splash(PotionSplashEvent ev) {
        for (LivingEntity target : ev.getAffectedEntities()) {
            if (this.allowHit((Entity)target, (Entity)ev.getPotion())) continue;
            ev.setIntensity(target, 0.0);
        }
    }

    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void cloud(AreaEffectCloudApplyEvent ev) {
        ev.getAffectedEntities().removeIf(target -> !this.allowHit((Entity)target, (Entity)ev.getEntity()));
    }

    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
    public void projectile(ProjectileLaunchEvent ev) {
        Entity source = this.source((Entity)ev.getEntity());
        Model.Session s = this.e.byEvent(this.e.mobs.event(source));
        if (s != null) {
            this.e.mobs.tag((Entity)ev.getEntity(), s, "projectile");
            Projectile projectile = ev.getEntity();
            if (projectile instanceof AbstractArrow) {
                AbstractArrow arrow = (AbstractArrow)projectile;
                arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
            }
            this.e.db.save(s);
        }
    }

    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
    public void lingering(LingeringPotionSplashEvent ev) {
        Model.Session s = this.e.byEvent(this.e.mobs.event(this.source((Entity)ev.getEntity())));
        if (s != null) {
            this.e.mobs.tag((Entity)ev.getAreaEffectCloud(), s, "cloud");
            this.e.db.save(s);
        }
    }

    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
    public void fangs(EntitySpawnEvent ev) {
        Entity entity = ev.getEntity();
        if (entity instanceof EvokerFangs) {
            EvokerFangs f = (EvokerFangs)entity;
            Bukkit.getScheduler().runTask((Plugin)this.p, () -> {
                Model.Session s = this.e.byEvent(this.e.mobs.event((Entity)f.getOwner()));
                if (s != null && f.isValid()) {
                    this.e.mobs.tag((Entity)f, s, "fangs");
                    this.e.db.save(s);
                }
            });
        }
    }

    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void blockBreak(BlockBreakEvent ev) {
        if (this.protectedAt(ev.getBlock().getLocation()) && !this.canEditSource(ev.getPlayer(), ev.getBlock().getLocation())) {
            ev.setCancelled(true);
        }
    }

    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void blockPlace(BlockPlaceEvent ev) {
        if (this.protectedAt(ev.getBlock().getLocation()) && !this.canEditSource(ev.getPlayer(), ev.getBlock().getLocation())) {
            ev.setCancelled(true);
        }
    }

    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void multiPlace(BlockMultiPlaceEvent ev) {
        if (ev.getReplacedBlockStates().stream().anyMatch(b -> this.protectedAt(b.getLocation()) && !this.canEditSource(ev.getPlayer(), b.getLocation()))) {
            ev.setCancelled(true);
        }
    }

    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void bucket(PlayerBucketEmptyEvent ev) {
        if (this.protectedAt(ev.getBlock().getLocation()) || this.protectedAt(ev.getBlockClicked().getLocation()) || this.protectedAt(ev.getBlockClicked().getRelative(ev.getBlockFace()).getLocation())) {
            ev.setCancelled(true);
        }
    }

    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void bucketFill(PlayerBucketFillEvent ev) {
        if (this.protectedAt(ev.getBlock().getLocation())) {
            ev.setCancelled(true);
        }
    }

    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void entityChange(EntityChangeBlockEvent ev) {
        if (this.e.mobs.tagged(ev.getEntity()) || this.protectedAt(ev.getBlock().getLocation())) {
            ev.setCancelled(true);
        }
    }

    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void explosion(EntityExplodeEvent ev) {
        if (this.e.mobs.tagged(ev.getEntity())) {
            ev.blockList().clear();
        } else {
            ev.blockList().removeIf(b -> this.protectedAt(b.getLocation()));
        }
    }

    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void blockExplosion(BlockExplodeEvent ev) {
        ev.blockList().removeIf(b -> this.protectedAt(b.getLocation()));
    }

    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void fluid(BlockFromToEvent ev) {
        if (this.protectedAt(ev.getBlock().getLocation()) || this.protectedAt(ev.getToBlock().getLocation())) {
            ev.setCancelled(true);
        }
    }

    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void fire(BlockIgniteEvent ev) {
        if (this.protectedAt(ev.getBlock().getLocation())) {
            ev.setCancelled(true);
        }
    }

    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void burn(BlockBurnEvent ev) {
        if (this.protectedAt(ev.getBlock().getLocation())) {
            ev.setCancelled(true);
        }
    }

    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void spread(BlockSpreadEvent ev) {
        if (this.protectedAt(ev.getBlock().getLocation())) {
            ev.setCancelled(true);
        }
    }

    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void extend(BlockPistonExtendEvent ev) {
        if (this.protectedAt(ev.getBlock().getLocation()) || ev.getBlocks().stream().anyMatch(b -> this.protectedAt(b.getLocation()) || this.protectedAt(b.getRelative(ev.getDirection()).getLocation()))) {
            ev.setCancelled(true);
        }
    }

    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void retract(BlockPistonRetractEvent ev) {
        if (this.protectedAt(ev.getBlock().getLocation()) || ev.getBlocks().stream().anyMatch(b -> this.protectedAt(b.getLocation()) || this.protectedAt(b.getRelative(ev.getDirection().getOppositeFace()).getLocation()))) {
            ev.setCancelled(true);
        }
    }

    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void container(InventoryOpenEvent ev) {
        if (ev.getInventory().getLocation() != null && this.protectedAt(ev.getInventory().getLocation())) {
            ev.setCancelled(true);
        }
    }

    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void hopper(InventoryMoveItemEvent ev) {
        if (this.protectedAt(ev.getSource().getLocation()) || this.protectedAt(ev.getDestination().getLocation())) {
            ev.setCancelled(true);
        }
    }

    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void interactEntity(PlayerInteractEntityEvent ev) {
        if (this.e.mobs.tagged(ev.getRightClicked()) || this.protectedAt(ev.getRightClicked().getLocation()) && (ev.getRightClicked() instanceof Hanging || ev.getRightClicked() instanceof ArmorStand)) {
            ev.setCancelled(true);
        }
    }

    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void interactAtEntity(PlayerInteractAtEntityEvent event) {
        this.interactEntity((PlayerInteractEntityEvent)event);
    }

    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void armorStand(PlayerArmorStandManipulateEvent event) {
        if (this.protectedAt(event.getRightClicked().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void decorativeDamage(EntityDamageEvent event) {
        Entity entity = event.getEntity();
        if (this.protectedAt(entity.getLocation()) && (entity instanceof Hanging || entity instanceof ArmorStand || entity instanceof EnderCrystal)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void hanging(HangingBreakEvent event) {
        if (this.protectedAt(event.getEntity().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void alterBlock(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (block == null || !this.protectedAt(block.getLocation()) || this.canEditSource(event.getPlayer(), block.getLocation()) || this.e.chest(block.getLocation()) != null) {
            return;
        }
        if (event.getAction() == Action.PHYSICAL) {
            if (block.getType() == Material.FARMLAND || block.getType() == Material.TURTLE_EGG) {
                event.setCancelled(true);
            }
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getItem() == null) {
            return;
        }
        String tool = event.getItem().getType().name();
        if (tool.endsWith("_AXE") || tool.endsWith("_SHOVEL") || tool.endsWith("_HOE") || List.of("FLINT_AND_STEEL", "FIRE_CHARGE", "BONE_MEAL", "HONEYCOMB", "SHEARS").contains(tool)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void grow(StructureGrowEvent event) {
        if (this.protectedAt(event.getLocation()) || event.getBlocks().stream().anyMatch(b -> this.protectedAt(b.getLocation()))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void fertilize(BlockFertilizeEvent event) {
        if (this.protectedAt(event.getBlock().getLocation()) || event.getBlocks().stream().anyMatch(b -> this.protectedAt(b.getLocation()))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void absorb(SpongeAbsorbEvent event) {
        if (event.getBlocks().stream().anyMatch(b -> this.protectedAt(b.getLocation()))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void portalBlocks(PortalCreateEvent event) {
        if (event.getBlocks().stream().anyMatch(b -> this.protectedAt(b.getLocation()))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void mount(EntityMountEvent ev) {
        if (ev.getEntity() instanceof Player && this.e.mobs.tagged(ev.getMount())) {
            ev.setCancelled(true);
        }
    }

    @EventHandler(priority=EventPriority.MONITOR)
    public void vehicle(VehicleMoveEvent ev) {
        for (Entity passenger : this.passengers((Entity)ev.getVehicle())) {
            if (!(passenger instanceof Player)) continue;
            Player pl = (Player)passenger;
            Model.Session to = this.e.at(ev.getTo());
            if (this.exclusive(to) && !to.hasPlayer(pl) && !this.e.bypass(pl)) {
                pl.leaveVehicle();
                this.e.eject(to, pl);
                continue;
            }
            this.e.moved(pl, ev.getFrom(), ev.getTo());
        }
    }

    private List<Entity> passengers(Entity vehicle) {
        ArrayList<Entity> all = new ArrayList<Entity>();
        for (Entity passenger : vehicle.getPassengers()) {
            all.add(passenger);
            all.addAll(this.passengers(passenger));
        }
        return all;
    }

    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void placeEntity(EntityPlaceEvent ev) {
        if (this.protectedAt(ev.getEntity().getLocation())) {
            ev.setCancelled(true);
        }
    }

    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void bed(PlayerBedEnterEvent ev) {
        if (this.protectedAt(ev.getBed().getLocation())) {
            ev.setCancelled(true);
        }
    }

    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void teleportEntity(EntityTeleportEvent ev) {
        Model.Session s = this.e.byEvent(this.e.mobs.event(ev.getEntity()));
        if (s != null) {
            if (ev.getEntity() instanceof Shulker) {
                List<Model.Spawn> pts = s.arena.pointsFor(Math.max(1, s.wave), "shulker");
                if (pts.isEmpty()) {
                    ev.setCancelled(true);
                } else {
                    ev.setTo(pts.get((int)ThreadLocalRandom.current().nextInt((int)pts.size())).point.location());
                }
            } else if (!s.arena.playBox().contains(ev.getTo())) {
                ev.setCancelled(true);
            }
        }
    }

    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void combust(EntityCombustEvent ev) {
        if (this.e.mobs.tagged(ev.getEntity()) && !(ev instanceof EntityCombustByEntityEvent) && !(ev instanceof EntityCombustByBlockEvent)) {
            ev.setCancelled(true);
        }
    }

    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void spell(EntitySpellCastEvent ev) {
        if (this.e.mobs.tagged((Entity)ev.getEntity()) && ev.getSpell() == Spellcaster.Spell.SUMMON_VEX) {
            ev.setCancelled(true);
        }
    }

    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void transform(EntityTransformEvent ev) {
        if (this.e.mobs.tagged(ev.getEntity())) {
            ev.setCancelled(true);
        }
    }

    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void spawn(CreatureSpawnEvent ev) {
        if (!(ev.getEntity() instanceof Mob) || !this.protectedAt(ev.getLocation())) {
            return;
        }
        Model.Session s = this.e.byEvent(this.e.mobs.event((Entity)ev.getEntity()));
        if (!this.e.mobs.ownsSpawn(s, (Entity)ev.getEntity()) || !this.e.c.protectionBox(s.arena).contains(ev.getLocation())) {
            ev.setCancelled(true);
        }
    }

    @EventHandler(priority=EventPriority.HIGHEST)
    public void mobDeath(EntityDeathEvent ev) {
        Model.Session s = this.e.byEvent(this.e.mobs.event((Entity)ev.getEntity()));
        if (s != null && this.e.mobs.defeated(s, (Entity)ev.getEntity())) {
            this.e.db.save(s);
            Bukkit.getScheduler().runTask((Plugin)this.p, () -> {
                if (this.e.byEvent(s.id) == s && s.phase == Model.Phase.RUNNING) {
                    this.e.advance(s, System.currentTimeMillis());
                }
            });
        }
        if (this.e.mobs.tagged((Entity)ev.getEntity()) && !this.e.c.main().getBoolean("mob-drops")) {
            ev.getDrops().clear();
            ev.setDroppedExp(0);
        }
    }

    @EventHandler(priority=EventPriority.MONITOR)
    public void removed(EntityRemoveEvent ev) {
        Model.Session s = this.e.byEvent(this.e.mobs.event(ev.getEntity()));
        if (s == null || s.phase != Model.Phase.RUNNING || !(ev.getEntity() instanceof LivingEntity)) {
            return;
        }
        if (ev.getCause() == EntityRemoveEvent.Cause.DEATH || ev.getCause() == EntityRemoveEvent.Cause.EXPLODE || ev.getCause() == EntityRemoveEvent.Cause.OUT_OF_WORLD) {
            if (this.e.mobs.defeated(s, ev.getEntity())) {
                this.e.db.save(s);
            }
        } else if (ev.getCause() != EntityRemoveEvent.Cause.UNLOAD) {
            String id = ev.getEntity().getUniqueId().toString();
            Bukkit.getScheduler().runTaskLater((Plugin)this.p, () -> {
                if (this.e.byEvent(s.id) == s && s.combatants.containsKey(id)) {
                    this.e.mobs.restoreCombatant(s, id);
                    this.e.db.save(s);
                }
            }, 2L);
        }
    }

    @EventHandler
    public void loaded(EntityAddToWorldEvent ev) {
        if (this.e.mobs.tagged(ev.getEntity())) {
            Bukkit.getScheduler().runTask((Plugin)this.p, () -> {
                Model.Session s = this.e.byEvent(this.e.mobs.event(ev.getEntity()));
                if (s == null || s.phase != Model.Phase.RUNNING) {
                    ev.getEntity().remove();
                }
            });
        }
    }

    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void elderVisual(ElderGuardianAppearanceEvent ev) {
        Model.Session s = this.e.byEvent(this.e.mobs.event((Entity)ev.getEntity()));
        if (s != null && !s.arena.playBox().contains(ev.getAffectedPlayer().getLocation())) {
            ev.setCancelled(true);
        }
    }

    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void effect(EntityPotionEffectEvent ev) {
        if (ev.getEntity() instanceof Player) {
            Model.Session s;
            Player pl = (Player)ev.getEntity();
            if (ev.getNewEffect() != null && ev.getModifiedType() == PotionEffectType.MINING_FATIGUE && (s = this.e.at(pl.getLocation())) != null) {
                if (!s.hasPlayer(pl) || !s.arena.playBox().contains(pl.getLocation())) {
                    ev.setCancelled(true);
                } else {
                    this.fatigue.put(pl.getUniqueId(), new Fatigue(s.id, System.currentTimeMillis() + (long)ev.getNewEffect().getDuration() * 50L, ev.getNewEffect().getAmplifier(), ev.getOldEffect()));
                }
            }
        }
    }

    private void removeFatigue(Player player, Location to) {
        Fatigue f = this.fatigue.get(player.getUniqueId());
        if (f == null) {
            return;
        }
        Model.Session s = this.e.byEvent(f.session);
        if (to != null && s != null && s.arena.playBox().contains(to) && s.phase == Model.Phase.RUNNING) {
            return;
        }
        this.fatigue.remove(player.getUniqueId());
        PotionEffect current = player.getPotionEffect(PotionEffectType.MINING_FATIGUE);
        if (current != null && current.getAmplifier() == f.amp && (long)current.getDuration() <= Math.max(0L, (f.until - System.currentTimeMillis()) / 50L) + 10L) {
            player.removePotionEffect(PotionEffectType.MINING_FATIGUE);
            if (f.before != null) {
                player.addPotionEffect(f.before);
            }
        }
    }

    public void pulse() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            Model.Session wall = this.e.repels(player, player.getLocation());
            if (wall == null) continue;
            this.repel(wall, player);
        }
        for (UUID id : new ArrayList<UUID>(this.fatigue.keySet())) {
            Player pl = Bukkit.getPlayer((UUID)id);
            if (pl == null) continue;
            this.removeFatigue(pl, pl.getLocation());
        }
    }

    public void close() {
        for (UUID id : new ArrayList<UUID>(this.fatigue.keySet())) {
            Player pl = Bukkit.getPlayer((UUID)id);
            if (pl == null) continue;
            this.removeFatigue(pl, null);
        }
    }

    private record Fatigue(String session, long until, int amp, PotionEffect before) {
    }
}

