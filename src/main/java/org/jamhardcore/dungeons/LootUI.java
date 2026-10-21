/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.kyori.adventure.text.Component
 *  org.bukkit.Bukkit
 *  org.bukkit.Material
 *  org.bukkit.entity.HumanEntity
 *  org.bukkit.entity.Player
 *  org.bukkit.event.EventHandler
 *  org.bukkit.event.EventPriority
 *  org.bukkit.event.Listener
 *  org.bukkit.event.block.Action
 *  org.bukkit.event.inventory.InventoryClickEvent
 *  org.bukkit.event.inventory.InventoryDragEvent
 *  org.bukkit.event.player.PlayerInteractEvent
 *  org.bukkit.inventory.Inventory
 *  org.bukkit.inventory.InventoryHolder
 *  org.bukkit.inventory.ItemStack
 *  org.bukkit.inventory.meta.ItemMeta
 *  org.bukkit.plugin.Plugin
 *  org.bukkit.plugin.java.JavaPlugin
 */
package org.jamhardcore.dungeons;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.jamhardcore.dungeons.Messages;
import org.jamhardcore.dungeons.Model;
import org.jamhardcore.dungeons.Rewards;

final class LootUI
implements Listener {
    private final JavaPlugin plugin;
    private final Rewards rewards;

    LootUI(JavaPlugin plugin, Rewards rewards) {
        this.plugin = plugin;
        this.rewards = rewards;
    }

    void open(Player player, Model.Reward reward, boolean preview) {
        this.open(player, reward, preview, null);
    }

    void open(Player player, Model.Reward reward, boolean preview, String arena) {
        ItemMeta meta;
        Chest chest = new Chest(player, reward, preview, arena);
        int slot = 0;
        if (reward.coins > 0 && reward.coinState != Model.Delivery.DONE) {
            Model.Voucher voucher = new Model.Voucher();
            voucher.amount = reward.coins;
            voucher.id = reward.voucherId == null ? "preview" : reward.voucherId;
            this.put(chest, slot, this.rewards.coin(voucher));
            chest.parts.put(slot++, "coins");
        }
        if (reward.caseRole != null && reward.caseState != Model.Delivery.DONE) {
            ItemStack key = new ItemStack(Material.TRIPWIRE_HOOK);
            meta = key.getItemMeta();
            meta.displayName(Messages.color("&d\u041a\u043b\u044e\u0447 PhoenixCrates: " + Objects.toString(reward.caseId, reward.caseRole)));
            meta.lore(List.of(Messages.color("&7\u041d\u0430\u0436\u043c\u0438, \u0447\u0442\u043e\u0431\u044b \u043f\u043e\u043b\u0443\u0447\u0438\u0442\u044c \u043a\u043b\u044e\u0447 \u0432\u044b\u0431\u0440\u0430\u043d\u043d\u043e\u0433\u043e \u043a\u0435\u0439\u0441\u0430")));
            key.setItemMeta(meta);
            this.put(chest, slot, key);
            chest.parts.put(slot++, "case");
        }
        for (Model.GiftItem gift : reward.items) {
            if (gift.remaining <= 0 || slot >= 45) continue;
            ItemStack item = Rewards.stack(gift);
            item.setAmount(Math.min(gift.remaining, item.getMaxStackSize()));
            this.put(chest, slot, item);
            chest.parts.put(slot++, gift.id);
        }
        ItemStack all = new ItemStack(Material.CHEST);
        meta = all.getItemMeta();
        meta.displayName(Messages.color(preview ? "&e\u0422\u0435\u0441\u0442: \u0440\u0435\u0430\u043b\u044c\u043d\u044b\u0435 \u043d\u0430\u0433\u0440\u0430\u0434\u044b \u043d\u0435 \u0432\u044b\u0434\u0430\u044e\u0442\u0441\u044f" : "&a\u0417\u0430\u0431\u0440\u0430\u0442\u044c \u0432\u0441\u0451"));
        if (preview) {
            meta.lore(List.of(Messages.color(this.previewHint(chest))));
        }
        all.setItemMeta(meta);
        chest.inventory.setItem(49, all);
        player.openInventory(chest.inventory);
    }

    private void put(Chest chest, int slot, ItemStack item) {
        if (chest.preview) {
            ItemMeta meta = item.getItemMeta();
            ArrayList<Component> lore = new ArrayList<Component>(Objects.requireNonNullElse(meta.lore(), List.of()));
            lore.add(Messages.color("&e\u0422\u0435\u0441\u0442\u043e\u0432\u044b\u0439 \u043e\u0431\u0440\u0430\u0437\u0435\u0446: \u043f\u043e\u043b\u0443\u0447\u0435\u043d\u0438\u0435 \u043e\u0442\u043a\u043b\u044e\u0447\u0435\u043d\u043e"));
            meta.lore(lore);
            item.setItemMeta(meta);
        }
        chest.inventory.setItem(slot, item);
    }

    private String previewHint(Chest chest) {
        return chest.arena == null ? "&e\u0422\u0435\u0441\u0442\u043e\u0432\u044b\u0439 \u043f\u0440\u043e\u0441\u043c\u043e\u0442\u0440. \u0414\u043b\u044f \u043d\u0430\u0441\u0442\u043e\u044f\u0449\u0435\u0433\u043e \u0441\u043e\u0431\u044b\u0442\u0438\u044f: /jd spawn <ID \u0430\u0440\u0435\u043d\u044b>." : "&e\u0422\u0435\u0441\u0442\u043e\u0432\u044b\u0439 \u043f\u0440\u043e\u0441\u043c\u043e\u0442\u0440. \u0414\u043b\u044f \u043d\u0430\u0441\u0442\u043e\u044f\u0449\u0435\u0433\u043e \u0441\u043e\u0431\u044b\u0442\u0438\u044f: /jd stop " + chest.arena + ", \u0437\u0430\u0442\u0435\u043c /jd spawn " + chest.arena + ".";
    }

    private void failed(Player player, String id, RuntimeException error) {
        this.plugin.getLogger().log(Level.WARNING, "\u041d\u0435 \u043f\u043e\u0434\u0442\u0432\u0435\u0440\u0436\u0434\u0435\u043d\u0430 \u0432\u044b\u0434\u0430\u0447\u0430 " + id, error);
        player.sendMessage(Messages.color("&c\u041d\u0435 \u0443\u0434\u0430\u043b\u043e\u0441\u044c \u043f\u043e\u0434\u0442\u0432\u0435\u0440\u0434\u0438\u0442\u044c \u0432\u044b\u0434\u0430\u0447\u0443. \u041f\u043e\u043f\u0440\u043e\u0431\u0443\u0439 /jd rewards. \u0415\u0441\u043b\u0438 \u043d\u0430\u0433\u0440\u0430\u0434\u0430 \u043d\u0435\u0434\u043e\u0441\u0442\u0443\u043f\u043d\u0430, \u0441\u043e\u043e\u0431\u0449\u0438 \u0430\u0434\u043c\u0438\u043d\u0438\u0441\u0442\u0440\u0430\u0442\u043e\u0440\u0443."));
    }

    @EventHandler(priority=EventPriority.HIGHEST)
    public void click(InventoryClickEvent event) {
        boolean all;
        Player player;
        InventoryHolder inventoryHolder = event.getView().getTopInventory().getHolder();
        if (!(inventoryHolder instanceof Chest)) {
            return;
        }
        Chest chest = (Chest)inventoryHolder;
        event.setCancelled(true);
        HumanEntity humanEntity = event.getWhoClicked();
        if (!(humanEntity instanceof Player) || !chest.player.equals((player = (Player)humanEntity).getUniqueId()) || event.getRawSlot() < 0 || event.getRawSlot() >= 54) {
            return;
        }
        if (chest.preview) {
            player.sendMessage(Messages.color(this.previewHint(chest)));
            return;
        }
        String part = chest.parts.get(event.getRawSlot());
        boolean bl = all = event.getRawSlot() == 49;
        if (part == null && !all) {
            return;
        }
        boolean shift = event.isShiftClick();
        Bukkit.getScheduler().runTask((Plugin)this.plugin, () -> {
            if (!player.isOnline() || player.isDead() || player.getOpenInventory().getTopInventory().getHolder() != chest) {
                return;
            }
            try {
                Model.Reward reward = this.rewards.fresh(chest.reward);
                if (reward == null || !reward.owner.equals(player.getUniqueId().toString())) {
                    return;
                }
                if (all) {
                    this.rewards.deliver(player, reward);
                } else {
                    this.rewards.take(player, reward, part, shift ? Integer.MAX_VALUE : 64);
                }
                Model.Reward fresh = this.rewards.fresh(chest.reward);
                if (fresh != null) {
                    this.rewards.notifyComplete(fresh);
                    this.open(player, fresh, false);
                }
            }
            catch (RuntimeException error) {
                this.failed(player, chest.reward, error);
            }
        });
    }

    @EventHandler(priority=EventPriority.HIGHEST)
    public void drag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Chest) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority=EventPriority.LOWEST)
    public void redeem(PlayerInteractEvent event) {
        if (event.getHand() == null || !this.rewards.isCoin(event.getItem())) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        event.setCancelled(true);
        try {
            this.rewards.redeem(event.getPlayer(), event.getHand());
        }
        catch (RuntimeException error) {
            this.failed(event.getPlayer(), "coin", error);
        }
    }

    static final class Chest
    implements InventoryHolder {
        final UUID player;
        final String reward;
        final boolean preview;
        final String arena;
        final Inventory inventory;
        final Map<Integer, String> parts = new HashMap<Integer, String>();

        Chest(Player player, Model.Reward reward, boolean preview, String arena) {
            this.player = player.getUniqueId();
            this.reward = reward.id;
            this.preview = preview;
            this.arena = arena;
            this.inventory = Bukkit.createInventory((InventoryHolder)this, (int)54, (Component)Messages.color(preview ? "&6\u041d\u0430\u0433\u0440\u0430\u0434\u0430 \u2014 \u0442\u0435\u0441\u0442\u043e\u0432\u044b\u0439 \u043f\u0440\u043e\u0441\u043c\u043e\u0442\u0440" : "&2\u041d\u0430\u0433\u0440\u0430\u0434\u0430 \u0434\u0430\u043d\u0436\u0430"));
        }

        public Inventory getInventory() {
            return this.inventory;
        }
    }
}

