/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.Bukkit
 *  org.bukkit.attribute.Attribute
 *  org.bukkit.attribute.AttributeInstance
 *  org.bukkit.entity.CaveSpider
 *  org.bukkit.entity.Entity
 *  org.bukkit.entity.LivingEntity
 *  org.bukkit.entity.Player
 *  org.bukkit.event.EventHandler
 *  org.bukkit.event.EventPriority
 *  org.bukkit.event.Listener
 *  org.bukkit.event.entity.EntityDamageByEntityEvent
 *  org.bukkit.event.player.PlayerQuitEvent
 *  org.bukkit.inventory.EntityEquipment
 *  org.bukkit.plugin.Plugin
 *  org.bukkit.plugin.java.JavaPlugin
 *  org.bukkit.potion.PotionEffect
 *  org.bukkit.potion.PotionEffectType
 */
package org.jamhardcore.dungeons;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.CaveSpider;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.jamhardcore.dungeons.Engine;
import org.jamhardcore.dungeons.Messages;
import org.jamhardcore.dungeons.Model;

final class Combat
implements Listener {
    private final JavaPlugin plugin;
    private final Engine engine;
    private final Map<UUID, Long> viewers = new HashMap<UUID, Long>();

    Combat(JavaPlugin plugin, Engine engine) {
        this.plugin = plugin;
        this.engine = engine;
    }

    void watch(Player admin, boolean enabled) {
        if (enabled) {
            this.viewers.put(admin.getUniqueId(), 0L);
        } else {
            this.viewers.remove(admin.getUniqueId());
        }
        admin.sendMessage(Messages.color(enabled ? "&a\u0414\u0438\u0430\u0433\u043d\u043e\u0441\u0442\u0438\u043a\u0430 \u0431\u043e\u044f \u0432\u043a\u043b\u044e\u0447\u0435\u043d\u0430. \u0423\u0434\u0430\u0440\u044b \u043c\u043e\u0431\u043e\u0432 \u0434\u0430\u043d\u0436\u0430: \u043e\u0440\u0443\u0436\u0438\u0435, \u0443\u0440\u043e\u043d \u0434\u043e/\u043f\u043e\u0441\u043b\u0435 \u0437\u0430\u0449\u0438\u0442\u044b, \u043e\u0442\u043c\u0435\u043d\u0430." : "&e\u0414\u0438\u0430\u0433\u043d\u043e\u0441\u0442\u0438\u043a\u0430 \u0431\u043e\u044f \u0432\u044b\u043a\u043b\u044e\u0447\u0435\u043d\u0430."));
    }

    @EventHandler
    public void quit(PlayerQuitEvent event) {
        this.viewers.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority=EventPriority.MONITOR)
    public void hit(EntityDamageByEntityEvent event) {
        Player player;
        Model.Session session;
        block6: {
            block5: {
                Entity entity;
                session = this.engine.byEvent(this.engine.mobs.event(event.getDamager()));
                if (session == null || session.kind != Model.Kind.PVE) {
                    return;
                }
                this.trace(event, session);
                if (event.isCancelled() || event.getFinalDamage() <= 0.0 || !(event.getDamager() instanceof CaveSpider) || !((entity = event.getEntity()) instanceof Player)) break block5;
                player = (Player)entity;
                if (session.phase == Model.Phase.RUNNING && session.hasPlayer(player) && session.arena.playBox().contains(player.getLocation())) break block6;
            }
            return;
        }
        int ticks = this.engine.c.main().getInt("combat.cave-spider-poison-seconds", 7) * 20;
        int amplifier = this.engine.c.main().getInt("combat.cave-spider-poison-level", 1) - 1;
        if (ticks == 0) {
            return;
        }
        Bukkit.getScheduler().runTask((Plugin)this.plugin, () -> {
            if (event.isCancelled() || event.getFinalDamage() <= 0.0 || !player.isOnline() || player.isDead() || player.isInvulnerable() || this.engine.byEvent(session.id) != session || session.phase != Model.Phase.RUNNING || !session.hasPlayer(player) || !session.arena.playBox().contains(player.getLocation())) {
                return;
            }
            PotionEffect current = player.getPotionEffect(PotionEffectType.POISON);
            if (current != null && (current.getAmplifier() > amplifier || current.getAmplifier() == amplifier && (current.isInfinite() || current.getDuration() >= ticks))) {
                return;
            }
            player.addPotionEffect(new PotionEffect(PotionEffectType.POISON, ticks, amplifier));
        });
    }

    private void trace(EntityDamageByEntityEvent event, Model.Session session) {
        if (this.viewers.isEmpty()) {
            return;
        }
        String weapon = "\u2014";
        String attack = "\u2014";
        Entity entity = event.getDamager();
        if (entity instanceof LivingEntity) {
            AttributeInstance attribute;
            LivingEntity living = (LivingEntity)entity;
            EntityEquipment gear = living.getEquipment();
            if (gear != null) {
                weapon = gear.getItemInMainHand().getType().name();
            }
            if ((attribute = living.getAttribute(Attribute.ATTACK_DAMAGE)) != null) {
                attack = String.format(Locale.ROOT, "%.2f", attribute.getValue());
            }
        }
        String text = String.format(Locale.ROOT, "&7[JD %s] &f%s \u2192 %s | %s | \u0430\u0442\u0430\u043a\u0430 %s | \u0443\u0440\u043e\u043d %.2f / %.2f | \u043e\u0442\u043c\u0435\u043d\u0451\u043d: %s", session.arenaId, event.getDamager().getType(), event.getEntity().getName(), weapon, attack, event.getDamage(), event.getFinalDamage(), event.isCancelled() ? "\u0434\u0430" : "\u043d\u0435\u0442");
        long now = System.currentTimeMillis();
        for (UUID id : new ArrayList<UUID>(this.viewers.keySet())) {
            Player admin = Bukkit.getPlayer((UUID)id);
            if (admin == null || !admin.hasPermission("jamdungeons.admin")) {
                this.viewers.remove(id);
                continue;
            }
            if (now - this.viewers.get(id) < 1000L || !admin.getWorld().equals((Object)event.getEntity().getWorld()) || !(admin.getLocation().distanceSquared(event.getEntity().getLocation()) <= 4096.0)) continue;
            admin.sendMessage(Messages.color(text));
            this.viewers.put(id, now);
        }
    }
}

