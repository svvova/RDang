/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  io.papermc.paper.event.player.AsyncChatEvent
 *  net.kyori.adventure.text.Component
 *  net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
 *  org.bukkit.Bukkit
 *  org.bukkit.Material
 *  org.bukkit.command.CommandSender
 *  org.bukkit.entity.HumanEntity
 *  org.bukkit.entity.Player
 *  org.bukkit.event.EventHandler
 *  org.bukkit.event.EventPriority
 *  org.bukkit.event.Listener
 *  org.bukkit.event.inventory.InventoryClickEvent
 *  org.bukkit.event.inventory.InventoryDragEvent
 *  org.bukkit.event.player.PlayerQuitEvent
 *  org.bukkit.inventory.Inventory
 *  org.bukkit.inventory.InventoryHolder
 *  org.bukkit.inventory.ItemStack
 *  org.bukkit.inventory.meta.ItemMeta
 *  org.bukkit.plugin.Plugin
 *  org.bukkit.plugin.java.JavaPlugin
 */
package org.jamhardcore.dungeons;

import io.papermc.paper.event.player.AsyncChatEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.jamhardcore.dungeons.Commands;
import org.jamhardcore.dungeons.Configs;
import org.jamhardcore.dungeons.Engine;
import org.jamhardcore.dungeons.Json;
import org.jamhardcore.dungeons.Messages;
import org.jamhardcore.dungeons.Model;
import org.jamhardcore.dungeons.PlacementService;
import org.jamhardcore.dungeons.ThemeCatalog;

public final class AdminUI
implements Listener {
    private final JavaPlugin p;
    private final Engine e;
    private Commands commands;
    private final Map<UUID, Prompt> prompts = new HashMap<UUID, Prompt>();
    private final Set<UUID> prompting = ConcurrentHashMap.newKeySet();

    public AdminUI(JavaPlugin p, Engine e) {
        this.p = p;
        this.e = e;
    }

    public void commands(Commands c) {
        this.commands = c;
    }

    private void command(Player pl, String ... args) {
        this.commands.execute((CommandSender)pl, args);
    }

    private void prompt(Player pl, String question, Consumer<String> accept) {
        pl.closeInventory();
        this.prompts.put(pl.getUniqueId(), new Prompt(System.currentTimeMillis() + 120000L, accept));
        pl.sendMessage(Messages.color("&e" + question + " &7(\u0432 \u0447\u0430\u0442; \u00ab\u043e\u0442\u043c\u0435\u043d\u0430\u00bb \u2014 \u0432\u044b\u0439\u0442\u0438, 120 \u0441\u0435\u043a\u0443\u043d\u0434)"));
    }

    @EventHandler(priority=EventPriority.LOWEST)
    public void chat(AsyncChatEvent ev) {
        UUID id = ev.getPlayer().getUniqueId();
        if (!this.prompting.contains(id)) {
            return;
        }
        ev.setCancelled(true);
        String input = PlainTextComponentSerializer.plainText().serialize(ev.message());
        Bukkit.getScheduler().runTask((Plugin)this.p, () -> this.answer(ev.getPlayer(), input));
    }

    private void ask(Player pl, String question, Consumer<String> accept) {
        this.prompting.add(pl.getUniqueId());
        this.prompt(pl, question, accept);
    }

    private void answer(Player pl, String input) {
        Prompt prompt = this.prompts.remove(pl.getUniqueId());
        this.prompting.remove(pl.getUniqueId());
        if (prompt == null) {
            return;
        }
        if (!pl.hasPermission("jamdungeons.admin")) {
            return;
        }
        if (input.equalsIgnoreCase("\u043e\u0442\u043c\u0435\u043d\u0430") || input.equalsIgnoreCase("cancel")) {
            pl.sendMessage(Messages.color("&7\u0412\u0432\u043e\u0434 \u043e\u0442\u043c\u0435\u043d\u0451\u043d."));
            return;
        }
        if (System.currentTimeMillis() > prompt.expires) {
            this.e.msg.error((CommandSender)pl, "\u0412\u0440\u0435\u043c\u044f \u0432\u0432\u043e\u0434\u0430 \u0438\u0441\u0442\u0435\u043a\u043b\u043e");
            return;
        }
        try {
            prompt.accept.accept(input.strip());
        }
        catch (Exception ex) {
            this.e.msg.error((CommandSender)pl, ex.getMessage());
        }
    }

    @EventHandler
    public void quit(PlayerQuitEvent ev) {
        this.prompts.remove(ev.getPlayer().getUniqueId());
        this.prompting.remove(ev.getPlayer().getUniqueId());
    }

    @EventHandler(priority=EventPriority.HIGHEST)
    public void click(InventoryClickEvent ev) {
        Player pl;
        InventoryHolder inventoryHolder = ev.getView().getTopInventory().getHolder();
        if (!(inventoryHolder instanceof Menu)) {
            return;
        }
        Menu menu = (Menu)inventoryHolder;
        ev.setCancelled(true);
        HumanEntity humanEntity = ev.getWhoClicked();
        if (!(humanEntity instanceof Player) || !menu.owner.equals((pl = (Player)humanEntity).getUniqueId()) || menu.admin && !pl.hasPermission("jamdungeons.admin") || !menu.admin && !pl.hasPermission("jamdungeons.claim")) {
            return;
        }
        Consumer<InventoryClickEvent> action = menu.actions.get(ev.getRawSlot());
        if (action != null) {
            try {
                action.accept(ev);
            }
            catch (Exception ex) {
                this.e.msg.error((CommandSender)pl, ex.getMessage());
            }
        }
    }

    @EventHandler(priority=EventPriority.HIGHEST)
    public void drag(InventoryDragEvent ev) {
        if (ev.getView().getTopInventory().getHolder() instanceof Menu) {
            ev.setCancelled(true);
        }
    }

    private Menu menu(Player pl, String title) {
        return new Menu(pl, title, true);
    }

    private void back(Menu m, Player pl, Runnable action) {
        m.button(45, Material.ARROW, "&e\u041d\u0430\u0437\u0430\u0434", List.of(), click -> action.run());
        m.button(53, Material.BARRIER, "&c\u0417\u0430\u043a\u0440\u044b\u0442\u044c", List.of(), click -> pl.closeInventory());
    }

    public void main(Player pl, int page) {
        Menu m = this.menu(pl, "JamDungeons \u2022 \u0443\u043f\u0440\u0430\u0432\u043b\u0435\u043d\u0438\u0435");
        ArrayList<Model.Arena> all = new ArrayList<Model.Arena>(this.e.c.arenas.values());
        for (int i = page * 36; i < Math.min(all.size(), (page + 1) * 36); ++i) {
            Model.Arena ar = (Model.Arena)all.get(i);
            Model.Session s = this.e.sessions.get(ar.id);
            m.button(i % 36, ar.kind == Model.Kind.PVE ? Material.SPAWNER : Material.IRON_SWORD, "&a" + ar.name, List.of("&7ID: " + ar.id + " | " + String.valueOf((Object)ar.kind), "&7\u0422\u0435\u043c\u0430: " + ar.theme, "&7\u0420\u0430\u0437\u0440\u0435\u0448\u0451\u043d: " + ar.enabled, "&e\u0421\u043e\u0441\u0442\u043e\u044f\u043d\u0438\u0435: " + String.valueOf(s == null ? "\u043d\u0435 \u0440\u0430\u0437\u043c\u0435\u0449\u0451\u043d" : s.phase)), click -> this.arena(pl, ar.id));
        }
        m.button(36, Material.LIME_DYE, "&a\u0421\u043e\u0437\u0434\u0430\u0442\u044c PvE-\u0430\u0440\u0435\u043d\u0443", List.of(), click -> this.ask(pl, "\u0412\u0432\u0435\u0434\u0438\u0442\u0435 ID \u043d\u043e\u0432\u043e\u0439 \u0430\u0440\u0435\u043d\u044b: a-z, 0-9, _, -", id -> {
            this.command(pl, "create", (String)id, "pve", "mining");
            if (this.e.c.arenas.containsKey(id)) {
                this.arena(pl, (String)id);
            }
        }));
        m.button(37, Material.RED_DYE, "&c\u0421\u043e\u0437\u0434\u0430\u0442\u044c PvP-\u0430\u0440\u0435\u043d\u0443", List.of(), click -> this.ask(pl, "\u0412\u0432\u0435\u0434\u0438\u0442\u0435 ID \u043d\u043e\u0432\u043e\u0439 PvP-\u0430\u0440\u0435\u043d\u044b", id -> {
            this.command(pl, "create", (String)id, "pvp", "mining");
            if (this.e.c.arenas.containsKey(id)) {
                this.arena(pl, (String)id);
            }
        }));
        m.button(38, Material.CLOCK, "&e\u0422\u0430\u0439\u043c\u0435\u0440\u044b \u0438 \u0440\u0430\u0441\u043f\u0438\u0441\u0430\u043d\u0438\u0435", List.of("&7\u0412\u0441\u0435 \u043f\u0430\u0440\u0430\u043c\u0435\u0442\u0440\u044b config.yml"), click -> this.yaml(pl, "config", List.of(), 0));
        m.button(39, Material.ZOMBIE_HEAD, "&e\u0422\u0435\u043c\u044b \u0438 \u0432\u043e\u043b\u043d\u044b", List.of(), click -> this.themes(pl));
        m.button(40, Material.DIAMOND, "&e\u041d\u0430\u0433\u0440\u0430\u0434\u044b, \u0448\u0430\u043d\u0441\u044b, \u043a\u043e\u043b\u0438\u0447\u0435\u0441\u0442\u0432\u0430", List.of(), click -> this.yaml(pl, "rewards", List.of(), 0));
        m.button(41, Material.PAPER, "&e\u0422\u0435\u043a\u0441\u0442\u044b \u0441\u043e\u043e\u0431\u0449\u0435\u043d\u0438\u0439", List.of(), click -> this.yaml(pl, "messages", List.of(), 0));
        m.button(42, Material.TRIPWIRE_HOOK, "&e\u041a\u0435\u0439\u0441\u044b \u0438 \u0438\u043d\u0442\u0435\u0433\u0440\u0430\u0446\u0438\u0438", this.e.hooks.status().stream().map(x -> "&7" + x).toList(), click -> this.cases(pl, null, 0));
        m.button(43, Material.WRITABLE_BOOK, "&e\u0421\u0432\u0435\u0440\u043a\u0430 \u0432\u044b\u0434\u0430\u0447", List.of("&7\u0422\u043e\u043b\u044c\u043a\u043e \u0441\u043f\u043e\u0440\u043d\u044b\u0435 \u043d\u0430\u0447\u0438\u0441\u043b\u0435\u043d\u0438\u044f REVIEW"), click -> this.review(pl, 0));
        m.button(44, Material.COMPARATOR, "&e\u041f\u0440\u043e\u0432\u0435\u0440\u0438\u0442\u044c \u0438 \u043f\u0435\u0440\u0435\u0447\u0438\u0442\u0430\u0442\u044c", List.of("&7\u041b\u041a\u041c: \u043f\u0440\u043e\u0432\u0435\u0440\u0438\u0442\u044c \u0432\u0441\u0435 \u0430\u0440\u0435\u043d\u044b", "&7\u041f\u041a\u041c: reload"), click -> this.command(pl, click.isRightClick() ? "reload" : "validate"));
        if (page > 0) {
            m.button(45, Material.ARROW, "\u041f\u0440\u0435\u0434\u044b\u0434\u0443\u0449\u0430\u044f \u0441\u0442\u0440\u0430\u043d\u0438\u0446\u0430", List.of(), click -> this.main(pl, page - 1));
        }
        if ((page + 1) * 36 < all.size()) {
            m.button(52, Material.ARROW, "\u0421\u043b\u0435\u0434\u0443\u044e\u0449\u0430\u044f \u0441\u0442\u0440\u0430\u043d\u0438\u0446\u0430", List.of(), click -> this.main(pl, page + 1));
        }
        int activeCount = this.e.sessions.size();
        int maxLimit = this.e.c.limit("placed-events");
        m.button(47, Material.NETHER_STAR, "&a&lЗаспавнить ВСЕ данжи", List.of(
            "&7Запускает автоматический спавн всех",
            "&7созданных данжей в разных точках мира!",
            "",
            "&7Всего создано арен: &f" + all.size(),
            "&7Активно сейчас: &f" + activeCount + "&7/&f" + maxLimit,
            "",
            "&eНажми, чтобы заспавнить все данжи"
        ), click -> this.confirm(pl, "Заспавнить ВСЕ данжи?", () -> {
            pl.closeInventory();
            this.command(pl, "spawn", "all");
        }));
        if (activeCount > 0) {
            m.button(48, Material.REDSTONE_BLOCK, "&c&lОстановить все данжи", List.of(
                "&7Останавливает все активные события",
                "&7и восстанавливает территорию мира.",
                "",
                "&7Активно событий: &c" + activeCount,
                "",
                "&eНажми для остановки всех данжей"
            ), click -> this.confirm(pl, "Остановить ВСЕ активные данжи?", () -> {
                pl.closeInventory();
                this.command(pl, "stop", "all");
            }));
        }
        m.button(49, Material.CHEST, "&a\u041c\u043e\u0438 \u043d\u0430\u0433\u0440\u0430\u0434\u044b", List.of(), click -> this.pending(pl));
        m.button(53, Material.BARRIER, "\u0417\u0430\u043a\u0440\u044b\u0442\u044c", List.of(), click -> pl.closeInventory());
        pl.openInventory(m.inventory);
    }

    public void arena(Player pl, String id) {
        Model.Arena ar = this.e.c.arenas.get(id);
        if (ar == null) {
            this.main(pl, 0);
            return;
        }
        Menu m = this.menu(pl, "\u0410\u0440\u0435\u043d\u0430 " + id);
        List<String> errors = this.e.validate(ar, false);
        Model.Session session = this.e.sessions.get(id);
        m.button(34, Material.SHIELD, "&a\u041f\u0440\u0438\u0432\u0430\u0442 \u043f\u043e\u0441\u0442\u0440\u043e\u0439\u043a\u0438", List.of("&7\u041b\u041a\u041c: \u0433\u0440\u0430\u043d\u0438\u0446\u044b \u0438 \u0441\u043e\u0441\u0442\u043e\u044f\u043d\u0438\u0435 WorldGuard", "&7\u041f\u041a\u041c: \u043f\u0435\u0440\u0435\u0441\u043e\u0437\u0434\u0430\u0442\u044c \u0438 \u043f\u0440\u043e\u0432\u0435\u0440\u0438\u0442\u044c \u043f\u0440\u0438\u0432\u0430\u0442"), click -> {
            if (click.isRightClick()) {
                this.command(pl, "protection", id, "repair");
            } else {
                this.command(pl, "protection", id);
            }
        });
        m.button(35, Material.IRON_PICKAXE, "&e\u0420\u0435\u0434\u0430\u043a\u0442\u0438\u0440\u043e\u0432\u0430\u0442\u044c \u043f\u043e\u0441\u0442\u0440\u043e\u0439\u043a\u0443", List.of("&7\u0421\u0435\u0439\u0447\u0430\u0441: " + this.e.editingBuilding(pl, id), "&7\u0422\u043e\u043b\u044c\u043a\u043e \u0438\u0441\u0445\u043e\u0434\u043d\u0438\u043a, Creative, \u0431\u0435\u0437 \u0441\u043e\u0431\u044b\u0442\u0438\u044f"), click -> {
            this.command(pl, "build", id, this.e.editingBuilding(pl, id) ? "off" : "on");
            this.arena(pl, id);
        });
        m.button(4, Material.MAP, "&a" + ar.name, List.of("&7" + String.valueOf((Object)ar.kind) + " / " + ar.theme, "&7" + String.valueOf(session == null ? "\u041d\u0435\u0442 \u0441\u043e\u0431\u044b\u0442\u0438\u044f" : session.phase), errors.isEmpty() ? "&a\u0413\u0435\u043e\u043c\u0435\u0442\u0440\u0438\u044f \u043f\u0440\u043e\u0432\u0435\u0440\u0435\u043d\u0430" : "&c" + errors.getFirst()), null);
        m.button(10, Material.WOODEN_AXE, "&e\u0413\u0440\u0430\u043d\u0438\u0446\u0430 1 \u2014 \u0437\u0434\u0435\u0441\u044c", List.of("&7\u0412\u0441\u0442\u0430\u043d\u044c\u0442\u0435 \u0432 \u043f\u0435\u0440\u0432\u044b\u0439 \u0443\u0433\u043e\u043b \u0440\u0435\u0433\u0438\u043e\u043d\u0430"), click -> {
            this.command(pl, "pos1", id);
            this.arena(pl, id);
        });
        m.button(11, Material.STONE_AXE, "&e\u0413\u0440\u0430\u043d\u0438\u0446\u0430 2 \u2014 \u0437\u0434\u0435\u0441\u044c", List.of("&7\u0412\u0441\u0442\u0430\u043d\u044c\u0442\u0435 \u0432 \u043f\u0440\u043e\u0442\u0438\u0432\u043e\u043f\u043e\u043b\u043e\u0436\u043d\u044b\u0439 \u0443\u0433\u043e\u043b"), click -> {
            this.command(pl, "pos2", id);
            this.arena(pl, id);
        });
        m.button(12, Material.CHEST, "&eСундук — под прицелом", List.of(
            ar.chest != null ? "&aКоординаты: &f[" + (int)ar.chest.x() + ", " + (int)ar.chest.y() + ", " + (int)ar.chest.z() + "]" : "&cСундук не задан",
            "&7Посмотрите на одиночный пустой сундук/бочку",
            "&7Нажмите для привязки под прицелом"
        ), click -> {
            this.command(pl, "chest", id);
            this.arena(pl, id);
        });
        m.button(13, Material.OAK_DOOR, "&e\u0412\u044b\u0445\u043e\u0434 \u2014 \u0437\u0434\u0435\u0441\u044c", List.of("&7\u0421\u0443\u0445\u0430\u044f \u0442\u043e\u0447\u043a\u0430 \u0432\u043d\u0435 \u0440\u0435\u0433\u0438\u043e\u043d\u0430 \u0441 \u043f\u043e\u043b\u043e\u043c"), click -> {
            this.command(pl, "exit", id);
            this.arena(pl, id);
        });
        m.button(14, Material.SPAWNER, "&e\u0422\u043e\u0447\u043a\u0438 \u043c\u043e\u0431\u043e\u0432: " + ar.points.size(), List.of("&7\u0420\u0435\u0434\u0430\u043a\u0442\u043e\u0440, \u043f\u0440\u0438\u0432\u044f\u0437\u043a\u0430 \u043a \u0432\u043e\u043b\u043d\u0430\u043c \u0438 \u043c\u043e\u0431\u0430\u043c"), click -> this.points(pl, id, 0));
        m.button(15, Material.GRASS_BLOCK, "&e\u0422\u0435\u043c\u0430: " + ar.theme, List.of(), click -> this.chooseTheme(pl, id));
        m.button(16, Material.NAME_TAG, "&e\u041d\u0430\u0437\u0432\u0430\u043d\u0438\u0435", List.of(), click -> this.ask(pl, "\u041d\u043e\u0432\u043e\u0435 \u043d\u0430\u0437\u0432\u0430\u043d\u0438\u0435", text -> {
            this.command(pl, "rename", id, (String)text);
            this.arena(pl, id);
        }));
        m.button(19, ar.enabled ? Material.LIME_CONCRETE : Material.RED_CONCRETE, "&e\u0414\u043e\u0441\u0442\u0443\u043f: " + ar.enabled, List.of("&7\u041d\u0430\u0436\u043c\u0438\u0442\u0435 \u0434\u043b\u044f \u043f\u0435\u0440\u0435\u043a\u043b\u044e\u0447\u0435\u043d\u0438\u044f"), click -> {
            this.command(pl, "enable", id, ar.enabled ? "off" : "on");
            this.arena(pl, id);
        });
        m.button(20, Material.BEACON, "&aРазместить событие", List.of(
            PlacementService.automatic(ar, null) ? "&eПоиск случайного места в мире" : "&eЗапуск у исходной постройки",
            "&7Режим: " + ar.placement.label,
            "&7Воздух схемы: " + (ar.pasteAir ? "&aВКЛ (с воздухом)" : "&cВЫКЛ (без воздуха)"),
            "&7ЛКМ: Разместить событие",
            ar.template ? "&7ПКМ: Разместить рядом с вами (30-150 блоков)" : "",
            "&7Для вставки в A: кнопка «Вставить схему»",
            "&7Реальные награды после победы"
        ).stream().filter(s -> !s.isEmpty()).toList(), click -> {
            if (click.isRightClick() && ar.template) {
                pl.sendMessage(Messages.color("&eПоиск места рядом с вами (30-150 блоков) для &a" + id + "&e..."));
                this.e.placement.spawnNear(id, pl.getLocation(), 30, 150, false, pl.getName()).whenComplete((res, error) -> {
                    if (error != null) {
                        this.e.msg.error(pl, error.getMessage());
                    } else {
                        pl.sendMessage(Messages.color("&aАрена " + id + " успешно размещена рядом!"));
                    }
                });
            } else {
                this.command(pl, "spawn", id);
            }
            this.arena(pl, id);
        });
        m.button(21, Material.CLOCK, "&eПроверочный запуск", List.of(
            "&7Пять волн: зачистка или 3 минуты",
            PlacementService.automatic(ar, null) ? "&eПоиск случайного места в мире" : "&eЗапуск у исходной постройки",
            "&7ЛКМ: Тестовый запуск",
            ar.template ? "&7ПКМ: Тест рядом с вами (30-150 блоков)" : "",
            "&7Без наград и проверки жизней",
            "&cОбычная смерть сервера всё равно реальна!"
        ).stream().filter(s -> !s.isEmpty()).toList(), click -> {
            if (click.isRightClick() && ar.template) {
                pl.sendMessage(Messages.color("&eПоиск места рядом с вами для теста &a" + id + "&e..."));
                this.e.placement.spawnNear(id, pl.getLocation(), 30, 150, true, pl.getName()).whenComplete((res, error) -> {
                    if (error != null) {
                        this.e.msg.error(pl, error.getMessage());
                    } else {
                        pl.sendMessage(Messages.color("&aТестовая арена " + id + " успешно размещена рядом!"));
                    }
                });
            } else {
                this.command(pl, "test", id);
            }
            this.arena(pl, id);
        });
        m.button(22, Material.BARRIER, "&c\u041e\u0441\u0442\u0430\u043d\u043e\u0432\u0438\u0442\u044c \u0438 \u0432\u043e\u0441\u0441\u0442\u0430\u043d\u043e\u0432\u0438\u0442\u044c", List.of("&7\u0421 \u0441\u043e\u0445\u0440\u0430\u043d\u0435\u043d\u0438\u0435\u043c \u0443\u0436\u0435 \u0437\u0430\u043a\u0440\u0435\u043f\u043b\u0451\u043d\u043d\u044b\u0445 \u043d\u0430\u0433\u0440\u0430\u0434"), click -> this.confirm(pl, "\u041e\u0441\u0442\u0430\u043d\u043e\u0432\u0438\u0442\u044c " + id, () -> {
            this.command(pl, "stop", id);
            this.arena(pl, id);
        }));
        m.button(23, Material.STRUCTURE_BLOCK, "&eСохранить шаблон постройки", List.of("&7Сохраняет схематику в templates/", "&7Сундук и точки мобов привязываются", "&aИсходная площадь постройки очищается", "&7Плавная запись и очистка через FAWE", "&7Не сохраняет мобов", "&7Режим: " + ar.placement.label), click -> this.confirm(pl, "Записать шаблон " + id, () -> {
            pl.closeInventory();
            this.command(pl, "template", "save", id);
        }));
        m.button(24, Material.COMPASS, "&e\u041f\u043b\u043e\u0449\u0430\u0434\u043a\u0438 \u0434\u043b\u044f \u0448\u0430\u0431\u043b\u043e\u043d\u0430", List.of("&7\u0423\u043a\u0430\u0437\u0430\u043d\u043d\u044b\u0435 \u0432\u0440\u0443\u0447\u043d\u0443\u044e \u043c\u0435\u0441\u0442\u0430 \u0440\u0430\u0437\u043c\u0435\u0449\u0435\u043d\u0438\u044f"), click -> this.sites(pl, id, 0));
        m.button(25, Material.COMPARATOR, "&e\u041f\u0440\u043e\u0432\u0435\u0440\u0438\u0442\u044c \u0430\u0440\u0435\u043d\u0443", List.of(), click -> this.command(pl, "validate", id));
        m.button(31, Material.TNT, "&c\u0423\u0434\u0430\u043b\u0438\u0442\u044c \u043e\u043f\u0438\u0441\u0430\u043d\u0438\u0435 \u0430\u0440\u0435\u043d\u044b", List.of("&7\u041f\u043e\u0441\u0442\u0440\u043e\u0439\u043a\u0430 \u0432 \u043c\u0438\u0440\u0435 \u0441\u043e\u0445\u0440\u0430\u043d\u044f\u0435\u0442\u0441\u044f"), click -> this.confirm(pl, "\u0423\u0434\u0430\u043b\u0438\u0442\u044c " + id, () -> {
            this.command(pl, "delete", id, "confirm");
            this.main(pl, 0);
        }));
        m.button(28, Material.FILLED_MAP, "&e\u0412\u044b\u0431\u0440\u0430\u0442\u044c \u0441\u0445\u0435\u043c\u0430\u0442\u0438\u043a\u0443", List.of("&7\u0424\u0430\u0439\u043b: " + Objects.toString(ar.schematic, "\u0432\u043d\u0443\u0442\u0440\u0435\u043d\u043d\u0438\u0439 \u0448\u0430\u0431\u043b\u043e\u043d / \u043d\u0435\u0442"), "&7.litematic, .schem, .schematic, .nbt", "&7\u0422\u043e\u0447\u043a\u0438 A/B \u043e\u043f\u0440\u0435\u0434\u0435\u043b\u044f\u044e\u0442\u0441\u044f \u0430\u0432\u0442\u043e\u043c\u0430\u0442\u0438\u0447\u0435\u0441\u043a\u0438"), click -> this.schematics(pl, id, 0));
        m.button(29, Material.ENDER_EYE, "&e\u0420\u0430\u0437\u043c\u0435\u0449\u0435\u043d\u0438\u0435: " + ar.placement.label, List.of("&7" + ar.placement.description, "&7\u041d\u0430\u0436\u043c\u0438\u0442\u0435 \u0438 \u0432\u044b\u0431\u0435\u0440\u0438\u0442\u0435 \u0441\u043f\u043e\u0441\u043e\u0431 \u0440\u0430\u0437\u043c\u0435\u0449\u0435\u043d\u0438\u044f", "&7\u0417\u0435\u043c\u043b\u044f / \u0432\u043e\u0437\u0434\u0443\u0445 / \u0432\u043e\u0434\u0430 / \u043f\u043e\u0434 \u0432\u043e\u0434\u043e\u0439", "&8STATIC \u2014 \u0437\u0430\u043f\u0443\u0441\u043a \u0443 \u0438\u0441\u0445\u043e\u0434\u043d\u043e\u0439 \u043f\u043e\u0441\u0442\u0440\u043e\u0439\u043a\u0438"), click -> this.placement(pl, id));
        m.button(30, Material.COMPASS, "&e\u041c\u0438\u0440\u044b \u0434\u043b\u044f \u0441\u043b\u0443\u0447\u0430\u0439\u043d\u043e\u0433\u043e \u0440\u0430\u0437\u043c\u0435\u0449\u0435\u043d\u0438\u044f", List.of("&7" + (ar.worlds.isEmpty() ? "\u041e\u0431\u0449\u0438\u0439 \u0441\u043f\u0438\u0441\u043e\u043a / \u0432\u0441\u0435 \u043f\u043e\u0434\u0445\u043e\u0434\u044f\u0449\u0438\u0435 \u043c\u0438\u0440\u044b" : String.join((CharSequence)", ", ar.worlds)), "&7\u041d\u0430\u0437\u0432\u0430\u043d\u0438\u044f \u0447\u0435\u0440\u0435\u0437 \u0437\u0430\u043f\u044f\u0442\u0443\u044e \u0438\u043b\u0438 all"), click -> this.prompt(pl, "\u041c\u0438\u0440\u044b \u0447\u0435\u0440\u0435\u0437 \u0437\u0430\u043f\u044f\u0442\u0443\u044e \u043b\u0438\u0431\u043e all", input -> {
            this.command(pl, "worlds", id, (String)input);
            this.arena(pl, id);
        }));
        m.button(32, Material.IRON_SWORD, "&e\u0422\u0438\u043f: " + String.valueOf((Object)ar.kind), List.of("&7PvE \u2014 \u043c\u043e\u0431\u044b, PvP \u2014 \u0438\u0433\u0440\u043e\u043a\u0438"), click -> {
            this.command(pl, "type", id, ar.kind == Model.Kind.PVE ? "pvp" : "pve");
            this.arena(pl, id);
        });
        if (ar.schematic != null) {
            m.button(33, Material.STRUCTURE_BLOCK, "&eВставить схему для настройки", List.of(
                "&7Плавная вставка через FastAsyncWorldEdit",
                "&7Режим воздуха: " + (ar.pasteAir ? "&aВКЛ (с воздухом)" : "&cВЫКЛ (без воздуха)"),
                "&7Размеры и приват обновятся по файлу",
                "&7Запас привата: +" + this.e.c.main().getInt("schematics.protection-padding", 4) + " со всех сторон",
                "&aСервер не зависает — процесс идет в фоне"
            ), click -> this.confirm(pl, "Вставить " + id, () -> {
                pl.closeInventory();
                this.command(pl, "schematic", "paste", id);
            }));
        }
        if (ar.schematic != null) {
            m.button(36, Material.MAP, "&aПодогнать регион под схематику", List.of("&7Перечитать размеры файла", "&7Обновить A/B и приват без вставки блоков"), click -> {
                this.command(pl, "schematic", "fit", id);
                this.arena(pl, id);
            });
        }
        m.button(37, ar.pasteAir ? Material.CYAN_STAINED_GLASS : Material.TINTED_GLASS,
            "&eВставка воздуха: " + (ar.pasteAir ? "&aВКЛ (с воздухом)" : "&cВЫКЛ (без воздуха)"),
            List.of(
                "&7Текущий режим: " + (ar.pasteAir ? "&aВставлять блоки воздуха" : "&cИгнорировать воздух"),
                "&7ЛКМ: переключить для этой арены",
                "&7Без воздуха: ускоряет вставку, не срезает",
                "&7окружающий рельеф и сохраняет пустоты.",
                "&7С воздухом: полная замена блоков из схемы."
            ), click -> {
                this.command(pl, "pasteair", id, ar.pasteAir ? "off" : "on");
                this.arena(pl, id);
            });
        m.button(38, Material.PISTON, "&6Смещение схематики / постройки", List.of(
            "&7Сдвинуть схематику и приват в мире",
            "&7Вверх, вниз, влево, вправо, вперед, назад",
            "&7на указанное количество блоков.",
            session != null ? "&eСобытие сейчас: &aАКТИВНО (" + session.phase + ")" : "&7Событие: не размещено",
            "&aРаботает даже во время активного боя!",
            "&aЛандшафт старого места восстанавливается,",
            "&aа схема плавно переносится на новое место.",
            "&eНажмите, чтобы открыть меню управления"
        ), click -> {
            this.moveMenu(pl, id, 1);
        });
        int[] effOffset = this.e.c.getEffectiveOffset(ar);
        String yDesc;
        if (effOffset[1] < 0) {
            yDesc = "Вниз на " + Math.abs(effOffset[1]) + " бл.";
        } else if (effOffset[1] > 0) {
            yDesc = "Вверх на " + effOffset[1] + " бл.";
        } else {
            yDesc = "По умолчанию (0)";
        }
        List<String> offsetLore = new ArrayList<>();
        offsetLore.add("&7Настройка постоянного сдвига схематики");
        offsetLore.add("&7при каждом её спавне в мире.");
        offsetLore.add("&7Файл: &f" + (ar.schematic != null ? ar.schematic : "внутренний шаблон"));
        offsetLore.add("&7Текущий сдвиг спавна:");
        offsetLore.add("  &7• Y (высота): &e" + (effOffset[1] >= 0 ? "+" : "") + effOffset[1] + " &7(" + yDesc + ")");
        offsetLore.add("  &7• X: &f" + (effOffset[0] >= 0 ? "+" : "") + effOffset[0]);
        offsetLore.add("  &7• Z: &f" + (effOffset[2] >= 0 ? "+" : "") + effOffset[2]);
        if (ar.offsetX == 0 && ar.offsetY == 0 && ar.offsetZ == 0 && (effOffset[0] != 0 || effOffset[1] != 0 || effOffset[2] != 0)) {
            offsetLore.add("&8(унаследовано из настроек файла схематики)");
        }
        offsetLore.add("&aСхематика будет спавниться строго с этим смещением!");
        offsetLore.add("&eНажмите, чтобы открыть меню настройки");

        m.button(39, Material.HOPPER, "&6Преднастройка сдвига спавна", offsetLore, click -> {
            this.spawnOffsetMenu(pl, id, 1);
        });
        this.back(m, pl, () -> this.main(pl, 0));
        pl.openInventory(m.inventory);
    }

    public static String getFacingName(Player player) {
        if (player == null) return "Неизвестно";
        float yaw = (player.getLocation().getYaw() % 360 + 360) % 360;
        if (yaw >= 45 && yaw < 135) return "Запад (-X)";
        if (yaw >= 135 && yaw < 225) return "Север (-Z)";
        if (yaw >= 225 && yaw < 315) return "Восток (+X)";
        return "Юг (+Z)";
    }

    public void moveMenu(Player pl, String id, int step) {
        Model.Arena ar = this.e.c.findArena(id);
        if (ar == null) {
            this.main(pl, 0);
            return;
        }
        Model.Session session = this.e.session(ar.id);
        Model.Arena targetArena = (session != null && session.arena != null) ? session.arena : ar;
        int validStep = Math.max(1, Math.min(step, 100));
        Menu m = this.menu(pl, "Смещение • " + targetArena.name);
        Model.Box box = targetArena.box();

        String facing = getFacingName(pl);
        List<String> infoLore = new ArrayList<>();
        if (box != null) {
            infoLore.add("&7Координаты: &f[" + box.x1() + ", " + box.y1() + ", " + box.z1() + "] — [" + box.x2() + ", " + box.y2() + ", " + box.z2() + "]");
            infoLore.add("&7Размер: &f" + (box.x2() - box.x1() + 1) + "×" + (box.y2() - box.y1() + 1) + "×" + (box.z2() - box.z1() + 1));
        }
        infoLore.add("&7Схематика: &f" + (targetArena.schematic != null ? targetArena.schematic : (targetArena.template ? "внутренний шаблон" : "не задана")));
        infoLore.add("&7Сундук: &f" + (targetArena.chest != null ? "[" + (int)targetArena.chest.x() + ", " + (int)targetArena.chest.y() + ", " + (int)targetArena.chest.z() + "]" : "не задан"));
        if (session != null) {
            infoLore.add("&eСтатус события: &aАКТИВНО (" + session.phase + ")");
            if (session.kind == Model.Kind.PVE) {
                infoLore.add("&7Волна: &e" + session.wave + " &7| Мобы: &e" + session.entities.size());
            }
        } else {
            infoLore.add("&7Статус события: &fНе запущено");
        }
        infoLore.add("&7Взгляд игрока: &e" + facing);
        infoLore.add("&7Текущий шаг: &a" + validStep + " &7блок(ов)");
        m.button(4, Material.MAP, "&a" + targetArena.name + " &7(" + targetArena.id + ")", infoLore, null);

        // Slot 11: ВВЕРХ (+Y)
        m.button(11, Material.SLIME_BLOCK, "&a⬆ ВВЕРХ (+" + validStep + " Y)", List.of(
            "&7Поднять схематику на &a" + validStep + " &7блок(ов) вверх",
            "&7Координаты Y увеличатся на &a+" + validStep,
            "&eНажмите для применения"
        ), click -> {
            this.commands.moveArena(pl, targetArena, 0, validStep, 0, true).whenComplete((v, err) -> {
                Bukkit.getScheduler().runTask(this.p, () -> {
                    if (pl.isOnline() && pl.getOpenInventory().getTopInventory().getHolder() instanceof Menu) {
                        this.moveMenu(pl, ar.id, validStep);
                    }
                });
            });
        });

        // Slot 29: ВНИЗ (-Y)
        m.button(29, Material.REDSTONE_BLOCK, "&c⬇ ВНИЗ (-" + validStep + " Y)", List.of(
            "&7Опустить схематику на &c" + validStep + " &7блок(ов) вниз",
            "&7Координаты Y уменьшатся на &c-" + validStep,
            "&eНажмите для применения"
        ), click -> {
            this.commands.moveArena(pl, targetArena, 0, -validStep, 0, true).whenComplete((v, err) -> {
                Bukkit.getScheduler().runTask(this.p, () -> {
                    if (pl.isOnline() && pl.getOpenInventory().getTopInventory().getHolder() instanceof Menu) {
                        this.moveMenu(pl, ar.id, validStep);
                    }
                });
            });
        });

        // Slot 13: ВПЕРЁД
        int[] fDelta = Commands.getDirectionDelta(pl, "forward", validStep);
        m.button(13, Material.EMERALD_BLOCK, "&a🔼 ВПЕРЁД (" + validStep + " бл.)", List.of(
            "&7Сдвинуть схематику вперёд по вашему взгляду",
            "&7Направление: &e" + facing,
            "&7Смещение: &f[dx=" + fDelta[0] + ", dz=" + fDelta[2] + "]",
            "&eНажмите для применения"
        ), click -> {
            int[] d = Commands.getDirectionDelta(pl, "forward", validStep);
            this.commands.moveArena(pl, targetArena, d[0], d[1], d[2], true).whenComplete((v, err) -> {
                Bukkit.getScheduler().runTask(this.p, () -> {
                    if (pl.isOnline() && pl.getOpenInventory().getTopInventory().getHolder() instanceof Menu) {
                        this.moveMenu(pl, ar.id, validStep);
                    }
                });
            });
        });

        // Slot 21: ВЛЕВО
        int[] lDelta = Commands.getDirectionDelta(pl, "left", validStep);
        m.button(21, Material.LAPIS_BLOCK, "&b⬅ ВЛЕВО (" + validStep + " бл.)", List.of(
            "&7Сдвинуть схематику влево от вашего взгляда",
            "&7Смещение: &f[dx=" + lDelta[0] + ", dz=" + lDelta[2] + "]",
            "&eНажмите для применения"
        ), click -> {
            int[] d = Commands.getDirectionDelta(pl, "left", validStep);
            this.commands.moveArena(pl, targetArena, d[0], d[1], d[2], true).whenComplete((v, err) -> {
                Bukkit.getScheduler().runTask(this.p, () -> {
                    if (pl.isOnline() && pl.getOpenInventory().getTopInventory().getHolder() instanceof Menu) {
                        this.moveMenu(pl, ar.id, validStep);
                    }
                });
            });
        });

        // Slot 22: Текущий шаг / Центр
        m.button(22, Material.COMPASS, "&eТекущий шаг: &6" + validStep + " &eблок(ов)", List.of(
            "&7Смещение при каждом клике: &6" + validStep + " &7бл.",
            "&7ЛКМ: ввести свой шаг в чат"
        ), click -> {
            this.prompt(pl, "Введите количество блоков для шага (1-100)", input -> {
                try {
                    int customStep = Integer.parseInt(input.trim());
                    if (customStep >= 1 && customStep <= 100) {
                        this.moveMenu(pl, ar.id, customStep);
                        return;
                    }
                    pl.sendMessage(Messages.color("&cЧисло должно быть от 1 до 100"));
                } catch (Exception ex) {
                    pl.sendMessage(Messages.color("&cНекорректное число: " + input));
                }
                this.moveMenu(pl, ar.id, validStep);
            });
        });

        // Slot 23: ВПРАВО
        int[] rDelta = Commands.getDirectionDelta(pl, "right", validStep);
        m.button(23, Material.LAPIS_BLOCK, "&b➡ ВПРАВО (" + validStep + " бл.)", List.of(
            "&7Сдвинуть схематику вправо от вашего взгляда",
            "&7Смещение: &f[dx=" + rDelta[0] + ", dz=" + rDelta[2] + "]",
            "&eНажмите для применения"
        ), click -> {
            int[] d = Commands.getDirectionDelta(pl, "right", validStep);
            this.commands.moveArena(pl, targetArena, d[0], d[1], d[2], true).whenComplete((v, err) -> {
                Bukkit.getScheduler().runTask(this.p, () -> {
                    if (pl.isOnline() && pl.getOpenInventory().getTopInventory().getHolder() instanceof Menu) {
                        this.moveMenu(pl, ar.id, validStep);
                    }
                });
            });
        });

        // Slot 31: НАЗАД
        int[] bDelta = Commands.getDirectionDelta(pl, "back", validStep);
        m.button(31, Material.EMERALD_BLOCK, "&a🔽 НАЗАД (" + validStep + " бл.)", List.of(
            "&7Сдвинуть схематику назад против вашего взгляда",
            "&7Смещение: &f[dx=" + bDelta[0] + ", dz=" + bDelta[2] + "]",
            "&eНажмите для применения"
        ), click -> {
            int[] d = Commands.getDirectionDelta(pl, "back", validStep);
            this.commands.moveArena(pl, targetArena, d[0], d[1], d[2], true).whenComplete((v, err) -> {
                Bukkit.getScheduler().runTask(this.p, () -> {
                    if (pl.isOnline() && pl.getOpenInventory().getTopInventory().getHolder() instanceof Menu) {
                        this.moveMenu(pl, ar.id, validStep);
                    }
                });
            });
        });

        // Step presets: Row 4
        m.button(37, validStep == 1 ? Material.LIME_DYE : Material.GRAY_DYE, (validStep == 1 ? "&a✔ " : "&7") + "Шаг: 1 блок", List.of("&7Сдвиг по 1 блоку"), click -> this.moveMenu(pl, ar.id, 1));
        m.button(38, validStep == 2 ? Material.LIME_DYE : Material.GRAY_DYE, (validStep == 2 ? "&a✔ " : "&7") + "Шаг: 2 блока", List.of("&7Сдвиг по 2 блока"), click -> this.moveMenu(pl, ar.id, 2));
        m.button(39, validStep == 5 ? Material.LIME_DYE : Material.GRAY_DYE, (validStep == 5 ? "&a✔ " : "&7") + "Шаг: 5 блоков", List.of("&7Сдвиг по 5 блоков"), click -> this.moveMenu(pl, ar.id, 5));
        m.button(40, validStep == 10 ? Material.LIME_DYE : Material.GRAY_DYE, (validStep == 10 ? "&a✔ " : "&7") + "Шаг: 10 блоков", List.of("&7Сдвиг по 10 блоков"), click -> this.moveMenu(pl, ar.id, 10));
        m.button(41, Material.NAME_TAG, "&eСвой шаг (в чат)", List.of("&7Задать точное количество блоков"), click -> {
            this.prompt(pl, "Введите шаг сдвига в блоках (1-100)", input -> {
                try {
                    int s = Integer.parseInt(input.trim());
                    if (s >= 1 && s <= 100) {
                        this.moveMenu(pl, ar.id, s);
                        return;
                    }
                } catch (Exception ignored) {}
                this.moveMenu(pl, ar.id, validStep);
            });
        });

        // Pre-configured spawn offset: Slot 48
        int[] effOff = this.e.c.getEffectiveOffset(ar);
        m.button(48, Material.HOPPER, "&6Преднастройка сдвига спавна", List.of(
            "&7Текущий сдвиг спавна:",
            "&7• X: " + (effOff[0] >= 0 ? "+" : "") + effOff[0] + ", Y: " + (effOff[1] >= 0 ? "+" : "") + effOff[1] + ", Z: " + (effOff[2] >= 0 ? "+" : "") + effOff[2],
            "&7Задаёт, на сколько блоков смещать",
            "&7схематику при каждом её спавне в мире.",
            "&eНажмите, чтобы настроить сдвиг спавна"
        ), click -> {
            this.spawnOffsetMenu(pl, ar.id, validStep);
        });

        // Re-paste on current location: Slot 49
        m.button(49, Material.STRUCTURE_BLOCK, "&eПеревставить схему здесь", List.of(
            "&7Принудительно обновить блоки схемы",
            "&7на текущих координатах" + (session != null ? " события" : " региона"),
            "&7Режим воздуха: " + (targetArena.pasteAir ? "&aВКЛ" : "&cВЫКЛ")
        ), click -> {
            if (session != null) {
                pl.sendMessage(Messages.color("&e[FAWE] Перевставка схемы активного события " + session.arenaId + "..."));
                this.e.structures.placeAsync(session).whenComplete((v, err) -> {
                    Bukkit.getScheduler().runTask(this.p, () -> {
                        if (err != null) {
                            this.e.msg.error(pl, "Ошибка перевставки: " + err.getMessage());
                        } else {
                            pl.sendMessage(Messages.color("&aСхема успешно обновлена!"));
                        }
                        this.moveMenu(pl, ar.id, validStep);
                    });
                });
            } else {
                this.command(pl, "schematic", "paste", ar.id);
                this.moveMenu(pl, ar.id, validStep);
            }
        });

        // Back to Arena: Slot 45
        this.back(m, pl, () -> this.arena(pl, ar.id));
        pl.openInventory(m.inventory);
    }

    public void spawnOffsetMenu(Player pl, String targetStr, int step) {
        Model.Arena ar = this.e.c.findArena(targetStr);
        String schemFile = (ar != null && ar.schematic != null) ? ar.schematic : (ar == null ? targetStr : null);
        int validStep = Math.max(1, Math.min(step, 100));

        int[] currentOffset = ar != null ? this.e.c.getEffectiveOffset(ar) : this.e.c.schematicOffset(schemFile);
        int curX = currentOffset[0];
        int curY = currentOffset[1];
        int curZ = currentOffset[2];

        String targetTitle = ar != null ? ar.name : schemFile;
        Menu m = this.menu(pl, "Сдвиг спавна • " + targetTitle);

        String yDesc;
        if (curY < 0) yDesc = "Вниз на " + Math.abs(curY) + " бл.";
        else if (curY > 0) yDesc = "Вверх на " + curY + " бл.";
        else yDesc = "0 (по уровню земли)";

        String facing = getFacingName(pl);
        List<String> infoLore = new ArrayList<>();
        infoLore.add("&7Настройка сдвига при автоматическом, плановом");
        infoLore.add("&7или ручном спавне этой схематики.");
        if (ar != null) {
            infoLore.add("&7Арена: &f" + ar.name + " &7(" + ar.id + ")");
            if (ar.schematic != null) {
                infoLore.add("&7Схематика: &f" + ar.schematic);
            }
        } else {
            infoLore.add("&7Файл схематики: &f" + schemFile);
        }
        infoLore.add("&6Текущий сдвиг спавна:");
        infoLore.add("  &7• Y (высота): &e" + (curY >= 0 ? "+" : "") + curY + " &7(" + yDesc + ")");
        infoLore.add("  &7• X: &f" + (curX >= 0 ? "+" : "") + curX);
        infoLore.add("  &7• Z: &f" + (curZ >= 0 ? "+" : "") + curZ);
        infoLore.add("&7Взгляд игрока: &e" + facing);
        infoLore.add("&7Текущий шаг кнопок: &6" + validStep + " &7бл.");

        m.button(4, Material.PAPER, "&eСдвиг спавна схематики", infoLore, click -> {});

        // Slot 11: ВВЕРХ (+Y)
        m.button(11, Material.LIME_CONCRETE, "&a⬆ ВВЕРХ (+" + validStep + " Y)", List.of(
            "&7Сдвинуть точку спавна на &a+" + validStep + " &7бл. вверх",
            "&7Новый сдвиг Y: &a" + (curY + validStep),
            "&eНажмите для применения"
        ), click -> {
            int newY = curY + validStep;
            if (ar != null) {
                ar.offsetX = curX;
                ar.offsetY = newY;
                ar.offsetZ = curZ;
                this.e.c.saveArena(ar);
            } else {
                this.e.c.setSchematicOffset(schemFile, curX, newY, curZ);
            }
            this.spawnOffsetMenu(pl, targetStr, validStep);
        });

        // Slot 29: ВНИЗ (-Y)
        m.button(29, Material.RED_CONCRETE, "&c⬇ ВНИЗ (-" + validStep + " Y)", List.of(
            "&7Опустить точку спавна на &c-" + validStep + " &7бл. вниз (в грунт)",
            "&7Новый сдвиг Y: &c" + (curY - validStep),
            "&eНажмите для применения"
        ), click -> {
            int newY = curY - validStep;
            if (ar != null) {
                ar.offsetX = curX;
                ar.offsetY = newY;
                ar.offsetZ = curZ;
                this.e.c.saveArena(ar);
            } else {
                this.e.c.setSchematicOffset(schemFile, curX, newY, curZ);
            }
            this.spawnOffsetMenu(pl, targetStr, validStep);
        });

        // Slot 13: ВПЕРЁД
        int[] fDelta = Commands.getDirectionDelta(pl, "forward", validStep);
        m.button(13, Material.EMERALD_BLOCK, "&a🔼 ВПЕРЁД (" + validStep + " бл.)", List.of(
            "&7Сдвинуть спавн вперёд по вашему взгляду",
            "&7Направление: &e" + facing,
            "&7Дельта: &f[dx=" + fDelta[0] + ", dz=" + fDelta[2] + "]",
            "&eНажмите для применения"
        ), click -> {
            int[] d = Commands.getDirectionDelta(pl, "forward", validStep);
            int newX = curX + d[0];
            int newZ = curZ + d[2];
            if (ar != null) {
                ar.offsetX = newX;
                ar.offsetY = curY;
                ar.offsetZ = newZ;
                this.e.c.saveArena(ar);
            } else {
                this.e.c.setSchematicOffset(schemFile, newX, curY, newZ);
            }
            this.spawnOffsetMenu(pl, targetStr, validStep);
        });

        // Slot 21: ВЛЕВО
        int[] lDelta = Commands.getDirectionDelta(pl, "left", validStep);
        m.button(21, Material.LAPIS_BLOCK, "&b⬅ ВЛЕВО (" + validStep + " бл.)", List.of(
            "&7Сдвинуть спавн влево от вашего взгляда",
            "&7Дельта: &f[dx=" + lDelta[0] + ", dz=" + lDelta[2] + "]",
            "&eНажмите для применения"
        ), click -> {
            int[] d = Commands.getDirectionDelta(pl, "left", validStep);
            int newX = curX + d[0];
            int newZ = curZ + d[2];
            if (ar != null) {
                ar.offsetX = newX;
                ar.offsetY = curY;
                ar.offsetZ = newZ;
                this.e.c.saveArena(ar);
            } else {
                this.e.c.setSchematicOffset(schemFile, newX, curY, newZ);
            }
            this.spawnOffsetMenu(pl, targetStr, validStep);
        });

        // Slot 22: Сбросить в 0
        m.button(22, Material.BARRIER, "&cСбросить сдвиг в 0", List.of(
            "&7Сбросить все смещения (X=0, Y=0, Z=0)",
            "&7Текущие: X:" + curX + " Y:" + curY + " Z:" + curZ,
            "&eНажмите для сброса"
        ), click -> {
            if (ar != null) {
                ar.offsetX = 0;
                ar.offsetY = 0;
                ar.offsetZ = 0;
                this.e.c.saveArena(ar);
            } else {
                this.e.c.setSchematicOffset(schemFile, 0, 0, 0);
            }
            this.spawnOffsetMenu(pl, targetStr, validStep);
        });

        // Slot 23: ВПРАВО
        int[] rDelta = Commands.getDirectionDelta(pl, "right", validStep);
        m.button(23, Material.LAPIS_BLOCK, "&b➡ ВПРАВО (" + validStep + " бл.)", List.of(
            "&7Сдвинуть спавн вправо от вашего взгляда",
            "&7Дельта: &f[dx=" + rDelta[0] + ", dz=" + rDelta[2] + "]",
            "&eНажмите для применения"
        ), click -> {
            int[] d = Commands.getDirectionDelta(pl, "right", validStep);
            int newX = curX + d[0];
            int newZ = curZ + d[2];
            if (ar != null) {
                ar.offsetX = newX;
                ar.offsetY = curY;
                ar.offsetZ = newZ;
                this.e.c.saveArena(ar);
            } else {
                this.e.c.setSchematicOffset(schemFile, newX, curY, newZ);
            }
            this.spawnOffsetMenu(pl, targetStr, validStep);
        });

        // Slot 31: НАЗАД
        int[] bDelta = Commands.getDirectionDelta(pl, "back", validStep);
        m.button(31, Material.EMERALD_BLOCK, "&a🔽 НАЗАД (" + validStep + " бл.)", List.of(
            "&7Сдвинуть спавн назад против вашего взгляда",
            "&7Дельта: &f[dx=" + bDelta[0] + ", dz=" + bDelta[2] + "]",
            "&eНажмите для применения"
        ), click -> {
            int[] d = Commands.getDirectionDelta(pl, "back", validStep);
            int newX = curX + d[0];
            int newZ = curZ + d[2];
            if (ar != null) {
                ar.offsetX = newX;
                ar.offsetY = curY;
                ar.offsetZ = newZ;
                this.e.c.saveArena(ar);
            } else {
                this.e.c.setSchematicOffset(schemFile, newX, curY, newZ);
            }
            this.spawnOffsetMenu(pl, targetStr, validStep);
        });

        // Step presets: Row 4
        m.button(37, validStep == 1 ? Material.LIME_DYE : Material.GRAY_DYE, (validStep == 1 ? "&a✔ " : "&7") + "Шаг: 1 блок", List.of("&7Сдвиг по 1 блоку"), click -> this.spawnOffsetMenu(pl, targetStr, 1));
        m.button(38, validStep == 2 ? Material.LIME_DYE : Material.GRAY_DYE, (validStep == 2 ? "&a✔ " : "&7") + "Шаг: 2 блока", List.of("&7Сдвиг по 2 блока"), click -> this.spawnOffsetMenu(pl, targetStr, 2));
        m.button(39, validStep == 5 ? Material.LIME_DYE : Material.GRAY_DYE, (validStep == 5 ? "&a✔ " : "&7") + "Шаг: 5 блоков", List.of("&7Сдвиг по 5 блоков"), click -> this.spawnOffsetMenu(pl, targetStr, 5));
        m.button(40, validStep == 10 ? Material.LIME_DYE : Material.GRAY_DYE, (validStep == 10 ? "&a✔ " : "&7") + "Шаг: 10 блоков", List.of("&7Сдвиг по 10 блоков"), click -> this.spawnOffsetMenu(pl, targetStr, 10));
        m.button(41, Material.NAME_TAG, "&eСвой шаг (в чат)", List.of("&7Задать точное количество блоков"), click -> {
            this.prompt(pl, "Введите шаг сдвига в блоках (1-100)", input -> {
                try {
                    int s = Integer.parseInt(input.trim());
                    if (s >= 1 && s <= 100) {
                        this.spawnOffsetMenu(pl, targetStr, s);
                        return;
                    }
                } catch (Exception ignored) {}
                this.spawnOffsetMenu(pl, targetStr, validStep);
            });
        });

        // Row 5 extra options
        if (schemFile != null) {
            m.button(47, Material.WRITABLE_BOOK, "&bСохранить для файла схематики", List.of(
                "&7Файл: &f" + schemFile,
                "&7Сделать этот сдвиг стандартным для всех арен,",
                "&7которые будут использовать файл " + schemFile,
                "&eНажмите для сохранения в config.yml"
            ), click -> {
                this.e.c.setSchematicOffset(schemFile, curX, curY, curZ);
                pl.sendMessage(Messages.color("&aСдвиг спавна [X:" + curX + ", Y:" + curY + ", Z:" + curZ + "] сохранён по умолчанию для файла схематики &e" + schemFile));
                this.spawnOffsetMenu(pl, targetStr, validStep);
            });
        }

        if (ar != null) {
            m.button(49, Material.PISTON, "&6Перемещение в мире", List.of(
                "&7Открыть меню динамического перемещения постройки,",
                "&7размещенной в мире прямо сейчас",
                "&eНажмите для перехода в меню перемещения"
            ), click -> {
                this.moveMenu(pl, ar.id, validStep);
            });

            m.button(51, Material.ENDER_PEARL, "&dТестовый спавн со сдвигом", List.of(
                "&7Запустить тестовый спавн арены &f" + ar.name,
                "&7чтобы проверить, как постройка ложится на рельеф",
                "&eНажмите для запуска теста"
            ), click -> {
                pl.closeInventory();
                this.command(pl, "test", ar.id);
            });
        }

        this.back(m, pl, () -> {
            if (ar != null) {
                this.arena(pl, ar.id);
            } else {
                this.schematics(pl, "", 0);
            }
        });

        pl.openInventory(m.inventory);
    }

    private void placement(Player pl, String id) {
        Model.Arena ar = this.e.c.arenas.get(id);
        Menu m = this.menu(pl, "\u0420\u0430\u0437\u043c\u0435\u0449\u0435\u043d\u0438\u0435: " + id);
        Model.Placement[] options = new Model.Placement[]{Model.Placement.GROUND, Model.Placement.AIR, Model.Placement.WATER, Model.Placement.UNDERWATER, Model.Placement.STATIC};
        Material[] icons = new Material[]{Material.GRASS_BLOCK, Material.FEATHER, Material.OAK_BOAT, Material.PRISMARINE, Material.COMPASS};
        for (int i = 0; i < options.length; ++i) {
            Model.Placement mode = options[i];
            ArrayList<String> lore = new ArrayList<String>();
            lore.add("&7" + mode.description);
            if (mode != Model.Placement.STATIC) {
                lore.add("&7\u0421\u043e\u0437\u0434\u0430\u0451\u0442\u0441\u044f \u043e\u0442\u0434\u0435\u043b\u044c\u043d\u0430\u044f \u043a\u043e\u043f\u0438\u044f \u0448\u0430\u0431\u043b\u043e\u043d\u0430");
            }
            if (mode == Model.Placement.AIR) {
                lore.add("&7\u041d\u0430\u0434 \u0440\u0435\u043b\u044c\u0435\u0444\u043e\u043c: " + this.e.c.main().getInt("random-placement.air-min-height", 24) + "\u2013" + this.e.c.main().getInt("random-placement.air-max-height", 48) + " \u0431\u043b\u043e\u043a\u043e\u0432");
            }
            if (mode == Model.Placement.WATER || mode == Model.Placement.UNDERWATER) {
                lore.add("&7\u0420\u044f\u0434\u043e\u043c \u043d\u0443\u0436\u0435\u043d \u0431\u0435\u0437\u043e\u043f\u0430\u0441\u043d\u044b\u0439 \u0441\u0443\u0445\u043e\u0439 \u0432\u044b\u0445\u043e\u0434");
            }
            lore.add(ar.placement == mode ? "&a\u0412\u044b\u0431\u0440\u0430\u043d\u043e" : "&e\u041d\u0430\u0436\u043c\u0438\u0442\u0435, \u0447\u0442\u043e\u0431\u044b \u0432\u044b\u0431\u0440\u0430\u0442\u044c");
            m.button(20 + i, icons[i], (ar.placement == mode ? "&a\u2714 " : "&e") + mode.label, lore, click -> {
                this.command(pl, "placement", id, mode.name());
                this.arena(pl, id);
            });
        }
        this.back(m, pl, () -> this.arena(pl, id));
        pl.openInventory(m.inventory);
    }

    private void confirm(Player pl, String title, Runnable yes) {
        Menu m = this.menu(pl, title);
        m.button(21, Material.LIME_CONCRETE, "&a\u041f\u043e\u0434\u0442\u0432\u0435\u0440\u0434\u0438\u0442\u044c", List.of(), click -> yes.run());
        m.button(23, Material.RED_CONCRETE, "&c\u041e\u0442\u043c\u0435\u043d\u0430", List.of(), click -> this.main(pl, 0));
        pl.openInventory(m.inventory);
    }

    private void schematics(Player pl, String id, int page) {
        Menu m = this.menu(pl, "\u0421\u0445\u0435\u043c\u0430\u0442\u0438\u043a\u0438: " + id);
        try {
            List<String> files = this.e.structures.schematics();
            for (int i = page * 36; i < Math.min(files.size(), (page + 1) * 36); ++i) {
                String file = files.get(i);
                int[] fileOff = this.e.c.schematicOffset(file);
                List<String> fileLore = new ArrayList<>();
                fileLore.add("&7A/B: по размеру схемы");
                fileLore.add("&7Начало: ваше положение сейчас");
                if (fileOff[0] != 0 || fileOff[1] != 0 || fileOff[2] != 0) {
                    String yD = fileOff[1] < 0 ? "Вниз " + Math.abs(fileOff[1]) : (fileOff[1] > 0 ? "Вверх " + fileOff[1] : "0");
                    fileLore.add("&6Сдвиг спавна: &eX:" + fileOff[0] + " Y:" + fileOff[1] + " (" + yD + ") Z:" + fileOff[2]);
                } else {
                    fileLore.add("&7Сдвиг спавна: 0 (по умолчанию)");
                }
                fileLore.add("&eЛКМ: &7выбрать для арены");
                fileLore.add("&bПКМ: &7настроить сдвиг спавна схематики");
                m.button(i % 36, Material.MAP, file, fileLore, click -> {
                    if (click.isRightClick()) {
                        this.spawnOffsetMenu(pl, file, 1);
                    } else {
                        this.command(pl, "schematic", "use", id, file);
                        this.arena(pl, id);
                    }
                });
            }
            if (files.isEmpty()) {
                m.button(13, Material.PAPER, "\u0414\u043e\u0431\u0430\u0432\u044c\u0442\u0435 \u0444\u0430\u0439\u043b\u044b", List.of("&7plugins/JamDungeons/schematics/"), click -> {});
            }
            if (page > 0) {
                m.button(46, Material.ARROW, "\u041f\u0440\u0435\u0434\u044b\u0434\u0443\u0449\u0430\u044f", List.of(), click -> this.schematics(pl, id, page - 1));
            }
            if ((page + 1) * 36 < files.size()) {
                m.button(52, Material.ARROW, "\u0421\u043b\u0435\u0434\u0443\u044e\u0449\u0430\u044f", List.of(), click -> this.schematics(pl, id, page + 1));
            }
        }
        catch (Exception ex) {
            this.e.msg.error((CommandSender)pl, ex.getMessage());
        }
        this.back(m, pl, () -> this.arena(pl, id));
        pl.openInventory(m.inventory);
    }

    private void chooseTheme(Player pl, String id) {
        Menu m = this.menu(pl, "\u0422\u0435\u043c\u0430 \u0430\u0440\u0435\u043d\u044b");
        int slot = 0;
        for (Model.Theme t : this.e.c.themes.values()) {
            m.button(slot++, Material.SPAWNER, "&a" + t.name, List.of("&7" + t.id), click -> {
                this.command(pl, "theme", id, t.id);
                this.arena(pl, id);
            });
        }
        this.back(m, pl, () -> this.arena(pl, id));
        pl.openInventory(m.inventory);
    }

    public void points(Player pl, String id, int page) {
        Model.Arena ar = this.e.c.arenas.get(id);
        Menu m = this.menu(pl, "\u0422\u043e\u0447\u043a\u0438 " + id);
        for (int i = page * 36; i < Math.min(ar.points.size(), (page + 1) * 36); ++i) {
            Model.Spawn pt = ar.points.get(i);
            m.button(i % 36, Material.ENDER_PEARL, "&a" + pt.id, List.of("&7\u0412\u043e\u043b\u043d\u0430: " + String.valueOf(pt.wave == 0 ? "\u043b\u044e\u0431\u0430\u044f" : Integer.valueOf(pt.wave)) + " / \u043c\u043e\u0431: " + pt.mob, "&7" + (int)pt.point.x() + ", " + (int)pt.point.y() + ", " + (int)pt.point.z(), "&7\u041b\u041a\u041c: \u043f\u0435\u0440\u0435\u043d\u0435\u0441\u0442\u0438 \u0441\u044e\u0434\u0430; \u041f\u041a\u041c: \u0442\u0435\u043b\u0435\u043f\u043e\u0440\u0442", "&7Shift + \u041b\u041a\u041c: \u0443\u0434\u0430\u043b\u0438\u0442\u044c"), click -> {
                if (click.isShiftClick()) {
                    this.confirm(pl, "\u0423\u0434\u0430\u043b\u0438\u0442\u044c \u0442\u043e\u0447\u043a\u0443 " + pt.id, () -> {
                        this.command(pl, "point", "remove", id, pt.id);
                        this.points(pl, id, page);
                    });
                } else if (click.isRightClick()) {
                    this.command(pl, "point", "tp", id, pt.id);
                    pl.closeInventory();
                } else {
                    this.command(pl, "point", "move", id, pt.id);
                    this.points(pl, id, page);
                }
            });
        }
        m.button(36, Material.LIME_DYE, "&a\u0414\u043e\u0431\u0430\u0432\u0438\u0442\u044c \u0442\u043e\u0447\u043a\u0443 \u0437\u0434\u0435\u0441\u044c", List.of("&7\u0412\u0432\u043e\u0434: <\u0432\u043e\u043b\u043d\u0430 0..5> <mob \u0438\u043b\u0438 any> [ID]", "&7\u041f\u0440\u0438\u043c\u0435\u0440: 0 any | 5 elder_guardian boss"), click -> this.ask(pl, "\u0412\u0432\u0435\u0434\u0438\u0442\u0435: \u0432\u043e\u043b\u043d\u0430 \u043c\u043e\u0431 [ID]", text -> {
            ArrayList<String> args = new ArrayList<String>(List.of("point", "add", id));
            args.addAll(Arrays.asList(text.split("\\s+")));
            this.command(pl, (String[])args.toArray(String[]::new));
            this.points(pl, id, page);
        }));
        m.button(37, Material.FIREWORK_STAR, "&e\u041f\u043e\u043a\u0430\u0437\u0430\u0442\u044c \u0447\u0430\u0441\u0442\u0438\u0446\u044b \u0442\u043e\u0447\u0435\u043a", List.of(), click -> {
            this.command(pl, "point", "show", id);
            pl.closeInventory();
        });
        if (page > 0) {
            m.button(46, Material.ARROW, "\u041f\u0440\u0435\u0434\u044b\u0434\u0443\u0449\u0430\u044f", List.of(), click -> this.points(pl, id, page - 1));
        }
        if ((page + 1) * 36 < ar.points.size()) {
            m.button(52, Material.ARROW, "\u0421\u043b\u0435\u0434\u0443\u044e\u0449\u0430\u044f", List.of(), click -> this.points(pl, id, page + 1));
        }
        m.button(38, Material.TNT, "&c\u0423\u0434\u0430\u043b\u0438\u0442\u044c \u0442\u043e\u0447\u043a\u0443", List.of("&7\u041e\u0442\u043a\u0440\u044b\u0442\u044c \u0441\u043f\u0438\u0441\u043e\u043a \u0442\u043e\u0447\u0435\u043a \u0434\u043b\u044f \u0443\u0434\u0430\u043b\u0435\u043d\u0438\u044f"), click -> this.deletePoints(pl, id, 0));
        m.button(39, Material.RED_DYE, "&c\u0423\u0434\u0430\u043b\u0438\u0442\u044c \u0432\u0441\u0435 \u0442\u043e\u0447\u043a\u0438", List.of("&7\u041f\u043e\u0442\u0440\u0435\u0431\u0443\u0435\u0442\u0441\u044f \u043f\u043e\u0434\u0442\u0432\u0435\u0440\u0436\u0434\u0435\u043d\u0438\u0435"), click -> this.confirm(pl, "\u041e\u0447\u0438\u0441\u0442\u0438\u0442\u044c \u0442\u043e\u0447\u043a\u0438 " + id, () -> {
            this.command(pl, "point", "clear", id, "confirm");
            this.points(pl, id, 0);
        }));
        this.back(m, pl, () -> this.arena(pl, id));
        pl.openInventory(m.inventory);
    }

    private void deletePoints(Player pl, String id, int page) {
        Model.Arena arena = this.e.c.arenas.get(id);
        Menu m = this.menu(pl, "\u0423\u0434\u0430\u043b\u0438\u0442\u044c \u0442\u043e\u0447\u043a\u0443 \u2022 " + id);
        for (int i = page * 36; i < Math.min(arena.points.size(), (page + 1) * 36); ++i) {
            Model.Spawn point = arena.points.get(i);
            m.button(i % 36, Material.TNT, "&c\u0423\u0434\u0430\u043b\u0438\u0442\u044c " + point.id, List.of("&7\u0412\u043e\u043b\u043d\u0430 " + point.wave + " \u2022 " + point.mob, "&e\u041d\u0430\u0436\u043c\u0438 \u0438 \u043f\u043e\u0434\u0442\u0432\u0435\u0440\u0434\u0438 \u0443\u0434\u0430\u043b\u0435\u043d\u0438\u0435"), click -> this.confirm(pl, "\u0423\u0434\u0430\u043b\u0438\u0442\u044c " + point.id, () -> {
                this.command(pl, "point", "remove", id, point.id);
                this.deletePoints(pl, id, Math.min(page, Math.max(0, (this.e.c.arenas.get((Object)id).points.size() - 1) / 36)));
            }));
        }
        if (page > 0) {
            m.button(46, Material.ARROW, "\u041f\u0440\u0435\u0434\u044b\u0434\u0443\u0449\u0430\u044f", List.of(), click -> this.deletePoints(pl, id, page - 1));
        }
        if ((page + 1) * 36 < arena.points.size()) {
            m.button(52, Material.ARROW, "\u0421\u043b\u0435\u0434\u0443\u044e\u0449\u0430\u044f", List.of(), click -> this.deletePoints(pl, id, page + 1));
        }
        this.back(m, pl, () -> this.points(pl, id, 0));
        pl.openInventory(m.inventory);
    }

    private void sites(Player pl, String id, int page) {
        Model.Arena ar = this.e.c.arenas.get(id);
        Menu m = this.menu(pl, "\u041f\u043b\u043e\u0449\u0430\u0434\u043a\u0438 " + id);
        ArrayList<String> names = new ArrayList<String>(ar.sites.keySet());
        for (int i = page * 36; i < Math.min(names.size(), (page + 1) * 36); ++i) {
            String name = (String)names.get(i);
            m.button(i % 36, Material.GRASS_BLOCK, "&a" + name, List.of("&7\u042f\u043a\u043e\u0440\u044c: " + String.valueOf(ar.sites.get(name)), "&7\u041b\u041a\u041c: \u0440\u0430\u0437\u043c\u0435\u0441\u0442\u0438\u0442\u044c; \u041f\u041a\u041c: \u0442\u0435\u0441\u0442", "&7Shift: \u0443\u0434\u0430\u043b\u0438\u0442\u044c \u043f\u0440\u0438\u0432\u044f\u0437\u043a\u0443"), click -> {
                if (click.isShiftClick()) {
                    this.confirm(pl, "\u0423\u0434\u0430\u043b\u0438\u0442\u044c \u043f\u043b\u043e\u0449\u0430\u0434\u043a\u0443", () -> {
                        this.command(pl, "site", "remove", id, name);
                        this.sites(pl, id, page);
                    });
                } else {
                    this.command(pl, click.isRightClick() ? "test" : "spawn", id, name);
                    this.arena(pl, id);
                }
            });
        }
        m.button(36, Material.LIME_DYE, "&a\u0414\u043e\u0431\u0430\u0432\u0438\u0442\u044c \u043f\u043b\u043e\u0449\u0430\u0434\u043a\u0443 \u0437\u0434\u0435\u0441\u044c", List.of("&7\u0422\u0435\u043a\u0443\u0449\u0438\u0439 \u0431\u043b\u043e\u043a = \u043c\u0438\u043d\u0438\u043c\u0430\u043b\u044c\u043d\u044b\u0439 \u0443\u0433\u043e\u043b \u0440\u0435\u0433\u0438\u043e\u043d\u0430", "&7\u0410\u0434\u043c\u0438\u043d\u0438\u0441\u0442\u0440\u0430\u0442\u043e\u0440 \u0432\u044b\u0431\u0438\u0440\u0430\u0435\u0442 \u0441\u0432\u043e\u0431\u043e\u0434\u043d\u0443\u044e \u0442\u0435\u0440\u0440\u0438\u0442\u043e\u0440\u0438\u044e"), click -> this.ask(pl, "\u0418\u043c\u044f \u043d\u043e\u0432\u043e\u0439 \u043f\u043b\u043e\u0449\u0430\u0434\u043a\u0438", name -> {
            this.command(pl, "site", "add", id, (String)name);
            this.sites(pl, id, page);
        }));
        if (page > 0) {
            m.button(46, Material.ARROW, "\u041f\u0440\u0435\u0434\u044b\u0434\u0443\u0449\u0430\u044f", List.of(), click -> this.sites(pl, id, page - 1));
        }
        if ((page + 1) * 36 < names.size()) {
            m.button(52, Material.ARROW, "\u0421\u043b\u0435\u0434\u0443\u044e\u0449\u0430\u044f", List.of(), click -> this.sites(pl, id, page + 1));
        }
        this.back(m, pl, () -> this.arena(pl, id));
        pl.openInventory(m.inventory);
    }

    private void themes(Player pl) {
        Menu m = this.menu(pl, "\u0422\u0435\u043c\u044b \u0438 \u0432\u043e\u043b\u043d\u044b");
        int slot = 0;
        for (Model.Theme t : this.e.c.themes.values()) {
            m.button(slot++, Material.SPAWNER, "&a" + t.name, List.of("&7\u0412\u0441\u0435\u0433\u043e \u043c\u043e\u0431\u043e\u0432: " + t.waves.stream().flatMap(r -> r.values().stream()).mapToInt(Integer::intValue).sum(), "&7\u041b\u041a\u041c: \u0432\u043e\u043b\u043d\u044b; \u041f\u041a\u041c: \u043f\u0430\u0440\u0430\u043c\u0435\u0442\u0440\u044b \u0442\u0435\u043c\u044b"), click -> {
                if (click.isRightClick()) {
                    this.yaml(pl, "themes", List.of("themes", t.id), 0);
                } else {
                    this.waves(pl, t.id);
                }
            });
        }
        m.button(40, Material.WRITABLE_BOOK, "&e\u0412\u0441\u0435 \u043f\u0440\u0435\u0441\u0435\u0442\u044b \u043c\u043e\u0431\u043e\u0432", List.of("&7\u0422\u0438\u043f \u0441\u0443\u0449\u043d\u043e\u0441\u0442\u0438, \u0438\u043c\u044f, \u043e\u0440\u0443\u0436\u0438\u0435"), click -> this.yaml(pl, "themes", List.of("mob-presets"), 0));
        this.back(m, pl, () -> this.main(pl, 0));
        pl.openInventory(m.inventory);
    }

    private void waves(Player pl, String theme) {
        Menu m = this.menu(pl, "\u0412\u043e\u043b\u043d\u044b " + theme);
        Model.Theme t = this.e.c.themes.get(theme);
        for (int i = 1; i <= 5; ++i) {
            int wave = i;
            Map<String, Integer> row = t.waves.get(i - 1);
            m.button(18 + i, Material.ZOMBIE_HEAD, "&e\u0412\u043e\u043b\u043d\u0430 " + i, row.entrySet().stream().map(pair -> "&7" + (String)pair.getKey() + ": " + String.valueOf(pair.getValue())).toList(), click -> this.wave(pl, theme, wave));
        }
        if (ThemeCatalog.allowed(theme) != null) {
            m.button(40, Material.CLOCK, "&a\u0421\u0442\u0430\u043d\u0434\u0430\u0440\u0442\u043d\u044b\u0439 \u0440\u043e\u0441\u0442 \u0432\u043e\u043b\u043d", List.of("&7\u0417\u0430\u043c\u0435\u043d\u0438\u0442\u044c \u0432\u0441\u0435 5 \u0432\u043e\u043b\u043d \u0441\u0442\u0430\u043d\u0434\u0430\u0440\u0442\u043d\u044b\u043c \u0441\u043e\u0441\u0442\u0430\u0432\u043e\u043c", "&7\u0418\u0437\u043c\u0435\u043d\u0435\u043d\u0438\u0435 \u0434\u0435\u0439\u0441\u0442\u0432\u0443\u0435\u0442 \u043d\u0430 \u043d\u043e\u0432\u044b\u0435 \u0441\u043e\u0431\u044b\u0442\u0438\u044f"), click -> this.confirm(pl, "\u0421\u0431\u0440\u043e\u0441\u0438\u0442\u044c \u0432\u043e\u043b\u043d\u044b " + theme + "?", () -> {
                this.commands.resetWaves(theme);
                this.e.db.audit(pl.getName(), "WAVE_RESET", theme);
                this.waves(pl, theme);
            }));
        }
        this.back(m, pl, () -> this.themes(pl));
        pl.openInventory(m.inventory);
    }

    private void wave(Player pl, String theme, int wave) {
        Menu m = this.menu(pl, theme + " \u2022 \u0432\u043e\u043b\u043d\u0430 " + wave);
        Model.Theme t = this.e.c.themes.get(theme);
        int slot = 0;
        for (Map.Entry<String, Model.MobSpec> pair : t.mobs.entrySet()) {
            String mob = pair.getKey();
            int amount = t.waves.get(wave - 1).getOrDefault(mob, 0);
            m.button(slot++, Material.SPAWNER, "&e" + pair.getValue().name + ": " + amount, List.of("&7" + mob, "&7\u041b\u041a\u041c +1, \u041f\u041a\u041c \u22121, Shift: \u0432\u0432\u043e\u0434 \u0447\u0438\u0441\u043b\u0430"), click -> {
                if (click.isShiftClick()) {
                    this.ask(pl, "\u041d\u043e\u0432\u043e\u0435 \u043a\u043e\u043b\u0438\u0447\u0435\u0441\u0442\u0432\u043e (0 \u2014 \u0443\u0431\u0440\u0430\u0442\u044c)", text -> {
                        this.commands.setWave(theme, wave, mob, Integer.parseInt(text));
                        this.e.db.audit(pl.getName(), "WAVE_EDIT", theme + " " + wave + " " + mob + " " + text);
                        this.wave(pl, theme, wave);
                    });
                } else {
                    this.commands.setWave(theme, wave, mob, Math.max(0, amount + (click.isRightClick() ? -1 : 1)));
                    this.e.db.audit(pl.getName(), "WAVE_EDIT", theme + " " + wave + " " + mob);
                    this.wave(pl, theme, wave);
                }
            });
        }
        this.back(m, pl, () -> this.waves(pl, theme));
        pl.openInventory(m.inventory);
    }

    private void cases(Player pl, String role, int page) {
        Menu m = this.menu(pl, "\u041a\u0435\u0439\u0441\u044b PhoenixCrates");
        if (role == null) {
            int i = 20;
            for (String r : List.of("donation", "epic", "resource")) {
                m.button(i++, Material.TRIPWIRE_HOOK, "&e" + r, List.of("&7ID: " + this.e.c.main().getString("integrations.cases." + r, "\u043d\u0435 \u0432\u044b\u0431\u0440\u0430\u043d")), click -> this.cases(pl, r, 0));
            }
            m.button(40, Material.COMPARATOR, "&e\u041d\u0430\u0441\u0442\u0440\u043e\u0439\u043a\u0438 \u0438\u043d\u0442\u0435\u0433\u0440\u0430\u0446\u0438\u0439", List.of(), click -> this.yaml(pl, "config", List.of("integrations"), 0));
        } else {
            ArrayList<String> ids = new ArrayList<String>(this.e.hooks.caseIds());
            for (int i = page * 36; i < Math.min(ids.size(), (page + 1) * 36); ++i) {
                String id = (String)ids.get(i);
                m.button(i % 36, Material.TRIPWIRE_HOOK, "&a" + id, List.of("&7\u041d\u0430\u0437\u043d\u0430\u0447\u0438\u0442\u044c \u0440\u043e\u043b\u0438 " + role), click -> {
                    this.command(pl, "case", "map", role, id);
                    this.cases(pl, null, 0);
                });
            }
            if (page > 0) {
                m.button(46, Material.ARROW, "\u041f\u0440\u0435\u0434\u044b\u0434\u0443\u0449\u0430\u044f", List.of(), click -> this.cases(pl, role, page - 1));
            }
            if ((page + 1) * 36 < ids.size()) {
                m.button(52, Material.ARROW, "\u0421\u043b\u0435\u0434\u0443\u044e\u0449\u0430\u044f", List.of(), click -> this.cases(pl, role, page + 1));
            }
        }
        this.back(m, pl, () -> this.main(pl, 0));
        pl.openInventory(m.inventory);
    }

    public void pending(Player pl) {
        Menu m = new Menu(pl, "\u0421\u043e\u0445\u0440\u0430\u043d\u0451\u043d\u043d\u044b\u0435 \u043d\u0430\u0433\u0440\u0430\u0434\u044b", false);
        List<Model.Reward> all = this.e.rewards.pending(pl.getUniqueId());
        for (int i = 0; i < Math.min(45, all.size()); ++i) {
            String id = all.get((int)i).id;
            Model.Reward r = all.get(i);
            m.button(i, Material.CHEST, "&a" + String.valueOf((Object)r.kind) + " \u2022 " + id.substring(0, 8), List.of(
                "&7\u041c\u043e\u043d\u0435\u0442\u044b: " + r.coins + " / " + String.valueOf((Object)r.coinState),
                "&7\u041a\u0435\u0439\u0441: " + Objects.toString(r.caseId, "\u2014") + " / " + String.valueOf((Object)r.caseState),
                (r.petEgg != null ? "&7\u042f\u0439\u0446\u043e: " + r.petEgg + " / " + String.valueOf((Object)r.petEggState) : "&7\u042f\u0439\u0446\u043e: \u2014"),
                "&7\u041f\u0440\u0435\u0434\u043c\u0435\u0442\u043e\u0432 \u043e\u0441\u0442\u0430\u043b\u043e\u0441\u044c: " + r.items.stream().mapToInt(x -> x.remaining).sum(),
                "&e\u041d\u0430\u0436\u043c\u0438 \u0434\u043b\u044f \u0432\u044b\u0434\u0430\u0447\u0438 \u0434\u043e\u0441\u0442\u0443\u043f\u043d\u043e\u0433\u043e \u043e\u0441\u0442\u0430\u0442\u043a\u0430"
            ), click -> {
                Model.Reward fresh = this.e.db.reward(id);
                if (fresh != null) {
                    this.e.rewards.deliver(pl, fresh);
                }
                this.pending(pl);
            });
        }
        m.button(49, Material.HOPPER, "&a\u041f\u043e\u043b\u0443\u0447\u0438\u0442\u044c \u0432\u0441\u0451", List.of(), click -> {
            this.e.rewards.all(pl);
            this.pending(pl);
        });
        m.button(53, Material.BARRIER, "\u0417\u0430\u043a\u0440\u044b\u0442\u044c", List.of(), click -> pl.closeInventory());
        pl.openInventory(m.inventory);
    }

    private void review(Player pl, int page) {
        Menu m = this.menu(pl, "\u0421\u0432\u0435\u0440\u043a\u0430 \u043d\u0430\u0447\u0438\u0441\u043b\u0435\u043d\u0438\u0439 REVIEW");
        List<Model.Reward> all = this.e.db.rewards(null).stream().filter(r -> r.coinState == Model.Delivery.REVIEW || r.caseState == Model.Delivery.REVIEW || r.petEggState == Model.Delivery.REVIEW || r.items.stream().anyMatch(i -> i.state == Model.Delivery.REVIEW)).toList();
        for (int i = page * 36; i < Math.min(all.size(), (page + 1) * 36); ++i) {
            Model.Reward r2 = all.get(i);
            m.button(i % 36, Material.WRITABLE_BOOK, "&e" + r2.id.substring(0, 8), List.of(
                "&7UUID: " + r2.owner,
                "&7coins=" + String.valueOf((Object)r2.coinState) + ", case=" + String.valueOf((Object)r2.caseState) + (r2.petEgg != null ? ", egg=" + r2.petEggState : ""),
                "&7\u041f\u0440\u0435\u0434\u043c\u0435\u0442\u044b: " + String.valueOf(r2.items.stream().map(x -> String.valueOf((Object)x.state) + ":" + x.remaining + "/" + x.planned).toList()),
                "&e\u041e\u0442\u043a\u0440\u043e\u0439 \u0434\u043b\u044f \u0441\u0432\u0435\u0440\u043a\u0438 \u0441 \u0431\u0430\u043b\u0430\u043d\u0441\u043e\u043c/\u0438\u043d\u0432\u0435\u043d\u0442\u0430\u0440\u0451\u043c"
            ), click -> this.reviewParts(pl, r2.id));
        }
        if (page > 0) {
            m.button(46, Material.ARROW, "\u041f\u0440\u0435\u0434\u044b\u0434\u0443\u0449\u0430\u044f", List.of(), click -> this.review(pl, page - 1));
        }
        if ((page + 1) * 36 < all.size()) {
            m.button(52, Material.ARROW, "\u0421\u043b\u0435\u0434\u0443\u044e\u0449\u0430\u044f", List.of(), click -> this.review(pl, page + 1));
        }
        this.back(m, pl, () -> this.main(pl, 0));
        pl.openInventory(m.inventory);
    }

    private void reviewParts(Player pl, String id) {
        Model.Reward r = this.e.db.reward(id);
        Menu m = this.menu(pl, "\u0421\u0432\u0435\u0440\u043a\u0430 " + id.substring(0, 8));
        ArrayList<String> parts = new ArrayList<String>();
        if (r.coinState == Model.Delivery.REVIEW) {
            parts.add("coins");
        }
        if (r.caseState == Model.Delivery.REVIEW) {
            parts.add("case");
        }
        if (r.petEggState == Model.Delivery.REVIEW) {
            parts.add("pet_egg");
        }
        for (int i = 0; i < r.items.size(); ++i) {
            if (r.items.get((int)i).state != Model.Delivery.REVIEW) continue;
            parts.add(Integer.toString(i + 1));
        }
        int slot = 0;
        for (String part : parts) {
            m.button(slot++, Material.PAPER, "&e" + part, List.of("&7\u041b\u041a\u041c: \u043f\u043e\u0434\u0442\u0432\u0435\u0440\u0436\u0434\u0430\u044e, \u0447\u0442\u043e \u0443\u0436\u0435 \u0432\u044b\u0434\u0430\u043d\u043e", "&7\u041f\u041a\u041c: \u043f\u0440\u043e\u0432\u0435\u0440\u0435\u043d\u043e, \u0432\u044b\u0434\u0430\u0447\u0438 \u041d\u0415 \u0431\u044b\u043b\u043e; \u043f\u043e\u0432\u0442\u043e\u0440\u0438\u0442\u044c"), click -> this.confirm(pl, "\u0421\u0432\u0435\u0440\u0438\u0442\u044c " + part, () -> {
                this.command(pl, "review", id, part, click.isRightClick() ? "retry" : "delivered");
                this.reviewParts(pl, id);
            }));
        }
        this.back(m, pl, () -> this.review(pl, 0));
        pl.openInventory(m.inventory);
    }

    private Object value(String file, List<String> path) {
        Object value = Configs.plain(this.e.c.file(file));
        for (String segment : path) {
            Object object;
            if (value instanceof Map) {
                Map map = (Map)value;
                object = map.get(segment);
            } else {
                object = ((List)value).get(Integer.parseInt(segment));
            }
            value = object;
        }
        return value;
    }

    public void yaml(Player pl, String file, List<String> path, int page) {
        List<String> list;
        Object val = this.value(file, path);
        Menu m = this.menu(pl, file + " \u2022 " + (path.isEmpty() ? "\u043d\u0430\u0441\u0442\u0440\u043e\u0439\u043a\u0438" : path.getLast()));
        if (val instanceof Map) {
            Map map = (Map)val;
            list = map.keySet().stream().map(Object::toString).toList();
        } else {
            list = IntStream.range(0, ((List)val).size()).mapToObj(Integer::toString).toList();
        }
        List<String> keys = list;
        for (int i = page * 36; i < Math.min(keys.size(), (page + 1) * 36); ++i) {
            boolean fixedPve;
            boolean branch;
            String key = keys.get(i);
            ArrayList<String> next = new ArrayList<String>(path);
            next.add(key);
            Object field = this.value(file, next);
            boolean bl = branch = field instanceof Map || field instanceof List;
            String display = branch ? "\u0420\u0430\u0437\u0434\u0435\u043b \u043d\u0430\u0441\u0442\u0440\u043e\u0435\u043a" : (key.equalsIgnoreCase("password") ? "\u2022\u2022\u2022\u2022\u2022\u2022\u2022\u2022" : String.valueOf(field));
            boolean bl2 = fixedPve = file.equals("config") && next.size() == 2 && ((String)next.getFirst()).equals("timers") && List.of("pve-seconds", "wave-interval-seconds").contains(key);
            m.button(i % 36, branch ? Material.BOOK : (field instanceof Boolean ? Material.LEVER : Material.PAPER), "&e" + key, List.of("&7" + display.substring(0, Math.min(150, display.length())), fixedPve ? "&7\u0414\u043e 3 \u043c\u0438\u043d\u0443\u0442 \u043d\u0430 \u0432\u043e\u043b\u043d\u0443; \u0437\u0430\u0447\u0438\u0441\u0442\u043a\u0430 \u0443\u0441\u043a\u043e\u0440\u044f\u0435\u0442" : (branch ? "&e\u041e\u0442\u043a\u0440\u044b\u0442\u044c" : "&e\u041d\u0430\u0436\u043c\u0438\u0442\u0435, \u0447\u0442\u043e\u0431\u044b \u0438\u0437\u043c\u0435\u043d\u0438\u0442\u044c")), click -> {
                if (fixedPve) {
                    return;
                }
                if (branch) {
                    this.yaml(pl, file, next, 0);
                } else if (field instanceof Boolean) {
                    Boolean b = (Boolean)field;
                    this.editPath(file, next, b == false);
                    this.yaml(pl, file, path, page);
                } else {
                    this.ask(pl, String.join((CharSequence)".", next) + ": \u0432\u0432\u0435\u0434\u0438\u0442\u0435 " + (field instanceof Number ? "\u0447\u0438\u0441\u043b\u043e" : "\u0441\u0442\u0440\u043e\u043a\u0443 (\u0431\u0435\u0437 YAML-\u043a\u0430\u0432\u044b\u0447\u0435\u043a)"), input -> {
                        Object replacement = field instanceof Number ? Double.valueOf(input) : input;
                        this.editPath(file, next, replacement);
                        this.e.db.audit(pl.getName(), "CONFIG_EDIT", file + " " + String.join((CharSequence)".", next));
                        this.yaml(pl, file, path, page);
                    });
                }
            });
        }
        if (page > 0) {
            m.button(46, Material.ARROW, "\u041f\u0440\u0435\u0434\u044b\u0434\u0443\u0449\u0430\u044f", List.of(), click -> this.yaml(pl, file, path, page - 1));
        }
        if ((page + 1) * 36 < keys.size()) {
            m.button(52, Material.ARROW, "\u0421\u043b\u0435\u0434\u0443\u044e\u0449\u0430\u044f", List.of(), click -> this.yaml(pl, file, path, page + 1));
        }
        this.back(m, pl, () -> {
            if (path.isEmpty()) {
                this.main(pl, 0);
            } else {
                this.yaml(pl, file, path.subList(0, path.size() - 1), 0);
            }
        });
        pl.openInventory(m.inventory);
    }

    private void editPath(String file, List<String> path, Object newValue) {
        this.e.c.edit(file, y -> {
            Map<?, ?> root = (Map<?, ?>)Json.GSON.fromJson(Json.encode(Configs.plain(y)), Map.class);
            Object parent = root;
            for (String segment : path.subList(0, path.size() - 1)) {
                Object object;
                if (parent instanceof Map) {
                    Map<?, ?> map = (Map<?, ?>) parent;
                    object = map.get(segment);
                } else {
                    object = ((List<?>)parent).get(Integer.parseInt(segment));
                }
                parent = object;
            }
            if (parent instanceof Map) {
                ((Map)parent).put((String)path.getLast(), newValue);
            } else {
                ((List)parent).set(Integer.parseInt((String)path.getLast()), newValue);
            }
            if (file.equals("config") && path.equals(List.of("timers", "pve-seconds"))) {
                int n = ((Number)newValue).intValue();
                if (n < 5 || n % 5 != 0) {
                    throw new IllegalArgumentException("PvE \u0434\u043e\u043b\u0436\u043d\u043e \u0434\u0435\u043b\u0438\u0442\u044c\u0441\u044f \u043d\u0430 5");
                }
                ((Map)root.get("timers")).put("wave-interval-seconds", n / 5);
            }
            for (Map.Entry<?, ?> pair : root.entrySet()) {
                y.set((String)pair.getKey(), pair.getValue());
            }
        });
    }

    public void close() {
        for (Player pl : Bukkit.getOnlinePlayers()) {
            Inventory top = pl.getOpenInventory().getTopInventory();
            if (top == null || !(top.getHolder() instanceof Menu)) continue;
            pl.closeInventory();
        }
        this.prompts.clear();
        this.prompting.clear();
    }

    private record Prompt(long expires, Consumer<String> accept) {
    }

    private static final class Menu
    implements InventoryHolder {
        final UUID owner;
        final boolean admin;
        Inventory inventory;
        final Map<Integer, Consumer<InventoryClickEvent>> actions = new HashMap<Integer, Consumer<InventoryClickEvent>>();

        Menu(Player player, String title, boolean admin) {
            this.owner = player.getUniqueId();
            this.admin = admin;
            this.inventory = Bukkit.createInventory((InventoryHolder)this, (int)54, (Component)Messages.color("&2" + title));
        }

        public Inventory getInventory() {
            return this.inventory;
        }

        void button(int slot, Material material, String title, List<String> lore, Consumer<InventoryClickEvent> action) {
            ItemStack item = new ItemStack(material);
            ItemMeta meta = item.getItemMeta();
            meta.displayName(Messages.color(title));
            meta.lore(lore.stream().map(Messages::color).toList());
            item.setItemMeta(meta);
            this.inventory.setItem(slot, item);
            if (action != null) {
                this.actions.put(slot, action);
            }
        }
    }
}

