/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.Bukkit
 *  org.bukkit.Material
 *  org.bukkit.NamespacedKey
 *  org.bukkit.Registry
 *  org.bukkit.command.CommandSender
 *  org.bukkit.enchantments.Enchantment
 *  org.bukkit.entity.Player
 *  org.bukkit.event.Listener
 *  org.bukkit.inventory.EquipmentSlot
 *  org.bukkit.inventory.ItemStack
 *  org.bukkit.inventory.PlayerInventory
 *  org.bukkit.inventory.meta.EnchantmentStorageMeta
 *  org.bukkit.inventory.meta.ItemMeta
 *  org.bukkit.persistence.PersistentDataType
 *  org.bukkit.plugin.Plugin
 *  org.bukkit.plugin.java.JavaPlugin
 */
package org.jamhardcore.dungeons;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.jamhardcore.dungeons.Configs;
import org.jamhardcore.dungeons.Integrations;
import org.jamhardcore.dungeons.LootUI;
import org.jamhardcore.dungeons.Messages;
import org.jamhardcore.dungeons.Model;
import org.jamhardcore.dungeons.Rules;
import org.jamhardcore.dungeons.Store;

public final class Rewards {
    private final Configs c;
    private final Store db;
    private final Integrations hooks;
    private final Messages msg;
    private final SecureRandom random = new SecureRandom();
    private final NamespacedKey voucherKey;
    private final LootUI loot;
    private Consumer<Model.Reward> completed = reward -> {};

    public Rewards(JavaPlugin plugin, Configs c, Store db, Integrations h, Messages msg) {
        this.voucherKey = new NamespacedKey((Plugin)plugin, "coin_voucher");
        this.loot = new LootUI(plugin, this);
        Bukkit.getPluginManager().registerEvents((Listener)this.loot, (Plugin)plugin);
        this.c = c;
        this.db = db;
        this.hooks = h;
        this.msg = msg;
    }

    public Model.Reward generate(Model.Session s, UUID owner) {
        Model.Pool pool = s.rewardPool;
        Model.Reward r = new Model.Reward();
        r.event = s.id;
        r.owner = owner.toString();
        r.kind = s.kind;
        r.coins = this.random.nextInt(pool.minCoins, pool.maxCoins + 1);
        if (s.kind == Model.Kind.PVP) {
            r.caseRole = Rules.pick(pool.cases, 1, Model.CaseChoice::weight, this.random).getFirst().id();
            r.caseId = s.caseMappings.get(r.caseRole);
        } else {
            r.caseState = Model.Delivery.DONE;
        }
        for (Model.ItemChoice item : Rules.pick(pool.items, pool.picks, v -> v.weight, this.random)) {
            int amount = item.amounts.isEmpty() ? this.random.nextInt(item.min, item.max + 1) : Rules.pick(item.amounts, 1, Model.Amount::weight, this.random).getFirst().amount();
            ItemStack stack = new ItemStack(Material.valueOf((String)item.material));
            if (!item.books.isEmpty()) {
                Model.Book book = item.books.get(this.random.nextInt(item.books.size()));
                EnchantmentStorageMeta meta = (EnchantmentStorageMeta)stack.getItemMeta();
                meta.addStoredEnchant(Objects.requireNonNull((Enchantment)Registry.ENCHANTMENT.get(NamespacedKey.minecraft((String)book.enchantment()))), book.level(), false);
                stack.setItemMeta((ItemMeta)meta);
            }
            Model.GiftItem g = new Model.GiftItem();
            g.data = Base64.getEncoder().encodeToString(stack.serializeAsBytes());
            g.remaining = amount;
            r.items.add(g);
        }
        return r;
    }

    public static ItemStack stack(Model.GiftItem i) {
        return ItemStack.deserializeBytes((byte[])Base64.getDecoder().decode(i.data));
    }

    public List<Model.Reward> pending(UUID who) {
        return this.db.rewards(who.toString()).stream().filter(r -> r.claimed && !r.forfeited && !r.complete()).toList();
    }

    public void all(Player p) {
        for (Model.Reward r : this.pending(p.getUniqueId())) {
            this.deliver(p, r);
        }
    }

    public void open(Player p, Model.Reward r, boolean preview) {
        this.loot.open(p, r, preview);
    }

    public void openPreview(Player player, Model.Reward reward, String arena) {
        this.loot.open(player, reward, true, arena);
    }

    void completion(Consumer<Model.Reward> callback) {
        this.completed = callback;
    }

    void notifyComplete(Model.Reward reward) {
        if (reward.complete()) {
            this.completed.accept(reward);
        }
    }

    Model.Reward fresh(String id) {
        return this.db.reward(id);
    }

    public void deliver(Player p, Model.Reward r) {
        this.take(p, r, "coins", Integer.MAX_VALUE);
        this.take(p, r, "case", Integer.MAX_VALUE);
        for (Model.GiftItem item : r.items) {
            this.take(p, r, item.id, Integer.MAX_VALUE);
        }
        this.notifyComplete(r);
    }

    public void take(Player p, Model.Reward r, String part, int maximum) {
        if (!r.claimed || r.forfeited || !r.owner.equals(p.getUniqueId().toString()) || p.isDead()) {
            return;
        }
        if (part.equals("coins")) {
            this.giveCoin(p, r);
            return;
        }
        if (part.equals("case")) {
            if (r.caseRole == null || r.caseState != Model.Delivery.PENDING) {
                return;
            }
            if (!Rewards.storageSlot(p)) {
                this.msg.send((CommandSender)p, "pending");
                return;
            }
            r.caseState = Model.Delivery.IN_FLIGHT;
            this.db.reward(r);
            try {
                this.hooks.giveCase(p.getUniqueId(), r.caseId);
                p.saveData();
                r.caseState = Model.Delivery.DONE;
                this.db.reward(r);
            }
            catch (Exception ex) {
                r.caseState = Model.Delivery.REVIEW;
                this.db.reward(r);
                this.db.audit("SYSTEM", "REWARD_REVIEW", r.id + " case: " + ex.getClass().getSimpleName());
            }
            return;
        }
        Model.GiftItem item = r.items.stream().filter(i -> i.id.equals(part)).findFirst().orElse(null);
        if (item == null || item.state != Model.Delivery.PENDING || item.remaining == 0) {
            return;
        }
        ItemStack sample = Rewards.stack(item);
        int amount = Math.min(maximum, Math.min(item.remaining, Rewards.capacity(p.getInventory(), sample)));
        if (amount <= 0) {
            this.msg.send((CommandSender)p, "pending");
            return;
        }
        item.planned = amount;
        item.state = Model.Delivery.IN_FLIGHT;
        this.db.reward(r);
        try {
            ItemStack next;
            for (int left = amount; left > 0; left -= next.getAmount()) {
                next = sample.clone();
                next.setAmount(Math.min(left, next.getMaxStackSize()));
                if (p.getInventory().addItem(new ItemStack[]{next}).isEmpty()) continue;
                throw new IllegalStateException("\u0418\u0437\u043c\u0435\u043d\u0438\u043b\u043e\u0441\u044c \u043c\u0435\u0441\u0442\u043e \u0432 \u0438\u043d\u0432\u0435\u043d\u0442\u0430\u0440\u0435");
            }
            p.saveData();
            item.remaining -= amount;
            item.planned = 0;
            item.state = item.remaining == 0 ? Model.Delivery.DONE : Model.Delivery.PENDING;
            this.db.reward(r);
        }
        catch (Exception ex) {
            item.state = Model.Delivery.REVIEW;
            this.db.reward(r);
            this.db.audit("SYSTEM", "REWARD_REVIEW", r.id + " item " + item.id + ": " + ex.getClass().getSimpleName());
        }
    }

    private void giveCoin(Player p, Model.Reward r) {
        if (r.coinState != Model.Delivery.PENDING) {
            return;
        }
        if (r.coins == 0) {
            r.coinState = Model.Delivery.DONE;
            this.db.reward(r);
            return;
        }
        if (!Rewards.storageSlot(p)) {
            this.msg.send((CommandSender)p, "pending");
            return;
        }
        r.coinState = Model.Delivery.IN_FLIGHT;
        this.db.reward(r);
        try {
            Model.Voucher voucher;
            Model.Voucher voucher2 = voucher = r.voucherId == null ? this.db.voucherForReward(r.id) : this.db.voucher(r.voucherId);
            if (voucher == null) {
                voucher = new Model.Voucher();
                voucher.reward = r.id;
                voucher.amount = r.coins;
                this.db.createVoucher(voucher);
            }
            r.voucherId = voucher.id;
            this.db.reward(r);
            if (!p.getInventory().addItem(new ItemStack[]{this.coin(voucher)}).isEmpty()) {
                throw new IllegalStateException("\u041d\u0435\u0442 \u043c\u0435\u0441\u0442\u0430 \u0434\u043b\u044f \u043c\u043e\u043d\u0435\u0442\u043a\u0438");
            }
            p.saveData();
            r.coinState = Model.Delivery.DONE;
            this.db.reward(r);
        }
        catch (Exception ex) {
            r.coinState = Model.Delivery.REVIEW;
            this.db.reward(r);
            this.db.audit("SYSTEM", "REWARD_REVIEW", r.id + " coin item: " + ex.getClass().getSimpleName());
        }
    }

    ItemStack coin(Model.Voucher voucher) {
        ItemStack item = new ItemStack(Material.valueOf((String)this.c.main().getString("coin-item.material", "PAPER")));
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Messages.color(this.c.main().getString("coin-item.name", "&6\u041c\u043e\u043d\u0435\u0442\u043a\u0430 +{amount}").replace("{amount}", Integer.toString(voucher.amount))));
        meta.lore(this.c.main().getStringList("coin-item.lore").stream().map(line -> Messages.color(line.replace("{amount}", Integer.toString(voucher.amount)))).toList());
        meta.setItemModel(NamespacedKey.fromString((String)this.c.main().getString("coin-item.model", "jamdungeons:coin")));
        meta.setMaxStackSize(Integer.valueOf(1));
        meta.getPersistentDataContainer().set(this.voucherKey, PersistentDataType.STRING, voucher.id);
        item.setItemMeta(meta);
        return item;
    }

    boolean isCoin(ItemStack item) {
        return item != null && item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer().has(this.voucherKey, PersistentDataType.STRING);
    }

    public void redeem(Player player, EquipmentSlot hand) {
        ItemStack held = player.getInventory().getItem(hand);
        if (!this.isCoin(held) || player.isDead()) {
            return;
        }
        String id = (String)held.getItemMeta().getPersistentDataContainer().get(this.voucherKey, PersistentDataType.STRING);
        Model.Voucher voucher = this.db.voucher(id);
        if (voucher == null || voucher.state != Model.Delivery.PENDING) {
            this.msg.send((CommandSender)player, "coin-used");
            return;
        }
        try {
            this.hooks.checkEconomy();
            if (!this.db.lockVoucher(id, player.getUniqueId())) {
                this.msg.send((CommandSender)player, "coin-used");
                return;
            }
            this.hooks.deposit(player.getUniqueId(), voucher.amount, "voucher:" + id);
            this.db.voucherState(id, Model.Delivery.DONE);
            ItemStack current = player.getInventory().getItem(hand);
            if (this.isCoin(current) && id.equals(current.getItemMeta().getPersistentDataContainer().get(this.voucherKey, PersistentDataType.STRING))) {
                current.setAmount(current.getAmount() - 1);
                player.getInventory().setItem(hand, current);
            }
            player.saveData();
            this.msg.send((CommandSender)player, "coin-success", Map.of("amount", Integer.toString(voucher.amount)));
            this.db.audit(player.getUniqueId().toString(), "COIN_REDEEM", id + " amount=" + voucher.amount);
        }
        catch (Exception ex) {
            Model.Voucher current = this.db.voucher(id);
            if (current != null && current.state == Model.Delivery.IN_FLIGHT) {
                this.db.voucherState(id, Model.Delivery.REVIEW);
            }
            this.msg.send((CommandSender)player, "coin-review", Map.of("id", id));
            this.db.audit("SYSTEM", "COIN_REVIEW", id + " " + ex.getClass().getSimpleName());
        }
    }

    public void reconcileVoucher(String id, boolean delivered, String actor) {
        Model.Voucher voucher = this.db.voucher(id);
        if (voucher == null || voucher.state != Model.Delivery.REVIEW) {
            throw new IllegalArgumentException("\u041c\u043e\u043d\u0435\u0442\u043a\u0430 \u043d\u0435 \u043e\u0436\u0438\u0434\u0430\u0435\u0442 \u0441\u0432\u0435\u0440\u043a\u0438");
        }
        this.db.voucherState(id, delivered ? Model.Delivery.DONE : Model.Delivery.PENDING);
        this.db.audit(actor, "COIN_RECONCILE", id + " " + delivered);
    }

    static int capacity(PlayerInventory inv, ItemStack sample) {
        int n = 0;
        for (ItemStack current : inv.getStorageContents()) {
            if (current == null || current.getType().isAir()) {
                n += sample.getMaxStackSize();
                continue;
            }
            if (!current.isSimilar(sample)) continue;
            n += Math.max(0, Math.min(sample.getMaxStackSize(), inv.getMaxStackSize()) - current.getAmount());
        }
        return n;
    }

    private static boolean storageSlot(Player player) {
        return Arrays.stream(player.getInventory().getStorageContents()).anyMatch(item -> item == null || item.getType().isAir());
    }

    public void reconcile(String id, String part, boolean delivered, String actor) {
        Model.Reward r = this.db.reward(id);
        if (r == null) {
            throw new IllegalArgumentException("\u041d\u0435\u0442 \u0442\u0430\u043a\u043e\u0439 \u043d\u0430\u0433\u0440\u0430\u0434\u044b");
        }
        if (part.equals("coins")) {
            Rewards.requireReview(r.coinState);
            r.coinState = delivered ? Model.Delivery.DONE : Model.Delivery.PENDING;
        } else if (part.equals("case")) {
            Rewards.requireReview(r.caseState);
            r.caseState = delivered ? Model.Delivery.DONE : Model.Delivery.PENDING;
        } else {
            int i = Integer.parseInt(part) - 1;
            if (i < 0 || i >= r.items.size()) {
                throw new IllegalArgumentException("\u041d\u043e\u043c\u0435\u0440 \u043f\u0440\u0435\u0434\u043c\u0435\u0442\u0430: 1.." + r.items.size());
            }
            Model.GiftItem g = r.items.get(i);
            Rewards.requireReview(g.state);
            if (delivered) {
                g.remaining = Math.max(0, g.remaining - g.planned);
            }
            g.planned = 0;
            g.state = g.remaining == 0 ? Model.Delivery.DONE : Model.Delivery.PENDING;
        }
        this.db.reward(r);
        this.db.audit(actor, "RECONCILE", id + " " + part + " " + (delivered ? "delivered" : "retry"));
    }

    private static void requireReview(Model.Delivery d) {
        if (d != Model.Delivery.REVIEW) {
            throw new IllegalArgumentException("\u0421\u0432\u0435\u0440\u044f\u0442\u044c \u043c\u043e\u0436\u043d\u043e \u0442\u043e\u043b\u044c\u043a\u043e \u0441\u0442\u0430\u0442\u0443\u0441 REVIEW");
        }
    }
}

