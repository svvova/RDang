package org.jamhardcore.dungeons;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.World;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.regions.Region;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.block.data.type.Chest;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.CompassMeta;
import org.bukkit.inventory.meta.ItemMeta;

public final class Commands implements CommandExecutor, TabCompleter {
    private final Engine e;
    private final AdminUI ui;

    public Commands(Engine e, AdminUI ui) {
        this.e = e;
        this.ui = ui;
    }

    public static boolean isAdmin(CommandSender s) {
        return s.isOp() || s.hasPermission("jamdungeons.admin") || s.hasPermission("rdang.admin");
    }

    private Player player(CommandSender sender) {
        if (!(sender instanceof Player)) {
            throw new IllegalArgumentException("Нужно выполнить от игрока");
        }
        return (Player) sender;
    }

    private String arg(String[] a, int n) {
        if (n >= a.length) {
            throw new IllegalArgumentException("Недостаточно аргументов. /rdang help");
        }
        return a[n];
    }

    public Model.Arena arena(String idOrName) {
        Model.Arena a = this.e.c.findArena(idOrName);
        if (a == null) {
            throw new IllegalArgumentException("Арена не найдена по ID или названию: " + idOrName);
        }
        return Json.copy(a, Model.Arena.class);
    }

    private void change(String idOrName, Consumer<Model.Arena> fn) throws Exception {
        boolean regionChanged;
        Model.Arena previous = this.arena(idOrName);
        String id = previous.id;
        Model.Arena a = Json.copy(previous, Model.Arena.class);
        fn.accept(a);
        regionChanged = a.kind != previous.kind || !Objects.equals(a.pos1, previous.pos1) || !Objects.equals(a.pos2, previous.pos2);
        if (regionChanged && this.e.sessions.containsKey(id)) {
            throw new IllegalArgumentException("Сначала остановите событие: /jd stop " + id);
        }
        this.e.c.saveArena(a);
        if (regionChanged) {
            try {
                this.e.regions.sync(a);
                if (previous.pos1 != null && a.pos1 != null && !previous.pos1.world().equals(a.pos1.world())) {
                    this.e.regions.deleteDefinition(previous);
                }
            } catch (Exception ex) {
                this.e.c.saveArena(previous);
                throw ex;
            }
        }
    }

    public void execute(CommandSender sender, String ... args) {
        this.onCommand(sender, null, "jd", args);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] a) {
        block109: {
            String sub = a.length == 0 ? "menu" : a[0].toLowerCase(Locale.ROOT);
            try {
                if (sub.equals("rewards")) {
                    if (!sender.hasPermission("jamdungeons.claim") && !sender.hasPermission("rdang.claim") && !isAdmin(sender)) {
                        throw new IllegalArgumentException("Нет права jamdungeons.claim");
                    }
                    if (a.length > 1 && a[1].equals("take")) {
                        this.e.rewards.all(this.player(sender));
                    } else {
                        this.ui.pending(this.player(sender));
                    }
                    return true;
                }
                if (sub.equals("list")) {
                    if (this.e.sessions.isEmpty()) {
                        sender.sendMessage(Messages.color("&7Сейчас нет активных данжей."));
                    } else {
                        sender.sendMessage(Messages.color("&6=== Активные данжи (" + this.e.sessions.size() + ") ==="));
                        for (Model.Session s : this.e.sessions.values()) {
                            sender.sendMessage(Messages.color("&a" + s.arenaId + " &7[" + s.id + "] &e" + s.kind + " / " + s.phase + " &f" + s.arena.box().world() + " " + (int)s.arena.chest.x() + " " + (int)s.arena.chest.y() + " " + (int)s.arena.chest.z()));
                        }
                    }
                    return true;
                }
                if (sub.equals("help")) {
                    this.help(sender);
                    return true;
                }
                if (!isAdmin(sender)) {
                    this.e.msg.send(sender, "no-permission");
                    return true;
                }
                switch (sub) {
                    case "build": {
                        Player editor = this.player(sender);
                        Model.Arena selected = this.arena(this.arg(a, 1));
                        boolean enabled = Commands.parseBool(this.arg(a, 2));
                        this.e.editBuilding(editor, selected, enabled);
                        sender.sendMessage(Messages.color(enabled ? "&eРедактирование исходной постройки " + selected.id + " включено. Выключить: /jd build " + selected.id + " off" : "&aРедактирование выключено. Постройка защищена."));
                        break;
                    }
                    case "protection": {
                        this.protection(sender, a);
                        break;
                    }
                    case "combat": {
                        this.e.combat.watch(this.player(sender), Commands.parseBool(this.arg(a, 1)));
                        break;
                    }
                    case "menu": {
                        this.ui.main(this.player(sender), 0);
                        break;
                    }
                    case "create": {
                        String id = this.arg(a, 1);
                        if (!Rules.validId(id) || this.e.c.arenas.containsKey(id)) {
                            throw new IllegalArgumentException("ID уже занят или некорректен: a-z, 0-9, _, -; до 40 знаков");
                        }
                        Model.Arena ar2 = new Model.Arena();
                        ar2.id = id;
                        ar2.name = id;
                        ar2.kind = Model.Kind.valueOf(this.arg(a, 2).toUpperCase(Locale.ROOT));
                        ar2.theme = a.length > 3 ? a[3] : "mining";
                        ar2.placement = ar2.theme.equals("flying") ? Model.Placement.AIR : (ar2.theme.equals("water") ? Model.Placement.WATER : Model.Placement.GROUND);
                        this.e.c.saveArena(ar2);
                        sender.sendMessage(Messages.color("&aАрена создана. /jd edit " + id));
                        break;
                    }
                    case "edit": {
                        this.ui.arena(this.player(sender), this.arg(a, 1));
                        break;
                    }
                    case "schematic": {
                        this.schematic(sender, a);
                        break;
                    }
                    case "schem": {
                        this.schem(sender, a);
                        break;
                    }
                    case "undo": {
                        this.undo(sender, a);
                        break;
                    }
                    case "schedule": {
                        if (this.e.schedule == null) {
                            throw new IllegalStateException("Модуль расписания (EventSchedule) не инициализирован");
                        }
                        this.e.schedule.handleCommand(sender, a);
                        break;
                    }
                    case "autospawn": {
                        this.autospawn(sender, a);
                        break;
                    }
                    case "give": {
                        this.give(sender, a);
                        break;
                    }
                    case "type": {
                        this.change(this.arg(a, 1), ar -> {
                            ar.kind = Model.Kind.valueOf(this.arg(a, 2).toUpperCase(Locale.ROOT));
                        });
                        break;
                    }
                    case "placement": {
                        this.change(this.arg(a, 1), ar -> {
                            ar.placement = Model.Placement.valueOf(this.arg(a, 2).toUpperCase(Locale.ROOT));
                        });
                        break;
                    }
                    case "worlds": {
                        this.change(this.arg(a, 1), ar -> {
                            String value = this.arg(a, 2);
                            ar.worlds = value.equalsIgnoreCase("all") ? new ArrayList<>() : new ArrayList<>(Arrays.asList(value.split(",")));
                            for (String world : ar.worlds) {
                                if (Bukkit.getWorld(world) != null) continue;
                                throw new IllegalArgumentException("Мир не загружен: " + world);
                            }
                        });
                        break;
                    }
                    case "pasteair":
                    case "air": {
                        String id = this.arg(a, 1);
                        Model.Arena current = this.arena(id);
                        boolean newValue = a.length > 2 ? Commands.parseBool(this.arg(a, 2)) : !current.pasteAir;
                        this.change(id, ar -> {
                            ar.pasteAir = newValue;
                        });
                        sender.sendMessage(Messages.color("&aВставка воздуха для арены &e" + id + "&a: " + (newValue ? "&aВКЛ (с воздухом)" : "&cВЫКЛ (без воздуха)")));
                        break;
                    }
                    case "move":
                    case "shift": {
                        this.handleMove(sender, a);
                        break;
                    }
                    case "offset":
                    case "schemoffset": {
                        this.schematicOffset(sender, a);
                        break;
                    }
                    case "launch": {
                        String kind = this.arg(a, 1).toUpperCase(Locale.ROOT);
                        if (!List.of("ALL", "PVE", "PVP").contains(kind)) {
                            throw new IllegalArgumentException("/jd launch all|pve|pvp");
                        }
                        for (Model.Arena ar3 : this.e.c.arenas.values()) {
                            if (!ar3.enabled || !kind.equals("ALL") && !ar3.kind.name().equals(kind)) continue;
                            this.spawn(sender, ar3.id, "random", false);
                        }
                        break;
                    }
                    case "voucher": {
                        if (!this.arg(a, 1).equals("review") || !List.of("delivered", "retry").contains(this.arg(a, 3))) {
                            throw new IllegalArgumentException("/jd voucher review <id> delivered|retry");
                        }
                        this.e.rewards.reconcileVoucher(this.arg(a, 2), this.arg(a, 3).equals("delivered"), sender.getName());
                        break;
                    }
                    case "delete": {
                        String id = this.arg(a, 1);
                        this.arena(id);
                        if (!this.arg(a, 2).equals("confirm")) {
                            throw new IllegalArgumentException("Удаление: /jd delete " + id + " confirm");
                        }
                        if (this.e.sessions.containsKey(id)) {
                            throw new IllegalArgumentException("Сначала /jd stop " + id);
                        }
                        this.e.regions.deleteDefinition(this.arena(id));
                        this.e.c.removeArena(id);
                        this.e.clearBuildingEditors();
                        break;
                    }
                    case "rename": {
                        this.change(this.arg(a, 1), ar -> {
                            ar.name = String.join(" ", Arrays.copyOfRange(a, 2, a.length));
                        });
                        break;
                    }
                    case "theme": {
                        this.change(this.arg(a, 1), ar -> {
                            String theme = this.arg(a, 2);
                            Model.Theme chosen = this.e.c.themes.get(theme);
                            if (chosen == null) {
                                throw new IllegalArgumentException("Нет темы " + theme);
                            }
                            ar.theme = theme;
                            for (Model.Spawn point : ar.points) {
                                if (point.mob.equals("any") || chosen.mobs.containsKey(point.mob)) continue;
                                point.mob = "any";
                            }
                        });
                        break;
                    }
                    case "enable": {
                        List<String> issues;
                        String id = this.arg(a, 1);
                        boolean on = Commands.parseBool(this.arg(a, 2));
                        if (on && !(issues = this.e.validate(this.arena(id), false)).isEmpty()) {
                            throw new IllegalArgumentException(String.join("; ", issues));
                        }
                        this.change(id, ar -> {
                            ar.enabled = on;
                        });
                        break;
                    }
                    case "pos1": 
                    case "pos2": 
                    case "exit": {
                        Model.Point loc = Model.Point.of(this.player(sender).getLocation());
                        this.change(this.arg(a, 1), ar -> {
                            switch (sub) {
                                case "pos1": {
                                    ar.pos1 = loc;
                                    break;
                                }
                                case "pos2": {
                                    ar.pos2 = loc;
                                    break;
                                }
                                default: {
                                    ar.exit = loc;
                                }
                            }
                        });
                        break;
                    }
                    case "chest": {
                        Block b = this.player(sender).getTargetBlockExact(6);
                        if (b == null || (!(b.getBlockData() instanceof Chest) && b.getType() != Material.CHEST && b.getType() != Material.TRAPPED_CHEST && b.getType() != Material.BARREL)) {
                            throw new IllegalArgumentException("Посмотрите на одиночный сундук или бочку в пределах 6 блоков");
                        }
                        Location loc = b.getLocation();
                        String arenaId = this.arg(a, 1);
                        this.change(arenaId, ar -> {
                            ar.chest = Model.Point.of(loc);
                            if (ar.pos1 != null && ar.pos2 != null) {
                                Model.Box box = ar.box();
                                int cx = b.getX();
                                int cy = b.getY();
                                int cz = b.getZ();
                                if (!box.contains(ar.chest)) {
                                    ar.pos1 = new Model.Point(box.world(), Math.min(box.x1(), cx), Math.min(box.y1(), cy), Math.min(box.z1(), cz), ar.pos1.yaw(), ar.pos1.pitch());
                                    ar.pos2 = new Model.Point(box.world(), Math.max(box.x2(), cx), Math.max(box.y2(), cy), Math.max(box.z2(), cz), ar.pos2.yaw(), ar.pos2.pitch());
                                }
                            }
                        });
                        sender.sendMessage(Messages.color("&aСундук для арены &e" + arenaId + " &aуспешно установлен: &f[" + b.getX() + ", " + b.getY() + ", " + b.getZ() + "]"));
                        break;
                    }
                    case "point": {
                        this.point(sender, a);
                        break;
                    }
                    case "site": {
                        this.site(sender, a);
                        break;
                    }
                    case "template": {
                        if (!this.arg(a, 1).equals("save")) {
                            throw new IllegalArgumentException("/jd template save <id>");
                        }
                        String id = this.arg(a, 2);
                        if (this.e.sessions.containsKey(id)) {
                            throw new IllegalArgumentException("Сначала остановите событие");
                        }
                        Model.Arena saved = this.arena(id);
                        if (saved.chest == null) {
                            Model.Point found = this.e.findChest(saved);
                            if (found != null) {
                                saved.chest = found;
                                this.e.ensureContainsChest(saved);
                            }
                        } else {
                            this.e.ensureContainsChest(saved);
                        }
                        sender.sendMessage(Messages.color("&e[FAWE] Сохранение шаблона " + id + "..."));
                        this.e.structures.captureAsync(saved).whenComplete((v, err) -> {
                            Bukkit.getScheduler().runTask(this.e.p, () -> {
                                if (err != null) {
                                    Throwable cause = err.getCause() != null ? err.getCause() : err;
                                    this.e.msg.error(sender, "Ошибка сохранения шаблона: " + cause.getMessage());
                                } else {
                                    try {
                                        this.e.regions.sync(saved);
                                    } catch (Exception ignored) {}
                                    sender.sendMessage(Messages.color("&aШаблон сохранён через FAWE: templates/" + id + ".schem. Исходная площадь постройки очищена из мира. Сундук и точки мобов привязаны к шаблону. Размещение: " + saved.placement.label + ". /jd spawn " + id));
                                }
                            });
                        });
                        break;
                    }
                    case "spawn": 
                    case "test": {
                        this.handleSpawn(sender, sub, a);
                        break;
                    }
                    case "stop": {
                        this.handleStop(sender, a);
                        break;
                    }
                    case "wave": {
                        this.wave(a);
                        break;
                    }
                    case "timer": {
                        String type = this.arg(a, 1).toLowerCase(Locale.ROOT);
                        int n = Integer.parseInt(this.arg(a, 2));
                        if (!List.of("pve", "pvp").contains(type)) {
                            throw new IllegalArgumentException("pve или pvp");
                        }
                        if (type.equals("pve") && n % 5 != 0) {
                            throw new IllegalArgumentException("PvE должно делиться на 5");
                        }
                        if (type.equals("pve") && n != 900) {
                            throw new IllegalArgumentException("PvE фиксирован: 900 секунд, пять волн по 180 секунд");
                        }
                        this.e.c.edit("config", y -> {
                            y.set("timers." + type + "-seconds", n);
                            if (type.equals("pve")) {
                                y.set("timers.wave-interval-seconds", (n / 5));
                            }
                        });
                        break;
                    }
                    case "case": {
                        if (this.arg(a, 1).equals("list")) {
                            sender.sendMessage(String.join(", ", this.e.hooks.caseIds()));
                            break;
                        }
                        if (!this.arg(a, 1).equals("map") || !List.of("donation", "epic", "resource").contains(this.arg(a, 2))) {
                            throw new IllegalArgumentException("/jd case map donation|epic|resource <существующий ID>");
                        }
                        String id = this.arg(a, 3);
                        if (!this.e.hooks.caseIds().contains(id)) {
                            throw new IllegalArgumentException("Нет PhoenixCrates ID " + id);
                        }
                        this.e.c.edit("config", y -> y.set("integrations.cases." + a[2], id));
                        break;
                    }
                    case "config": {
                        if (!this.arg(a, 1).equals("set")) {
                            throw new IllegalArgumentException("/jd config set config|themes|rewards|messages <path> <YAML значение>");
                        }
                        String file = this.arg(a, 2);
                        String path = this.arg(a, 3);
                        if (file.equals("arenas")) {
                            throw new IllegalArgumentException("Для арен используйте /jd edit");
                        }
                        String value = String.join(" ", Arrays.copyOfRange(a, 4, a.length));
                        YamlConfiguration parsed = new YamlConfiguration();
                        parsed.loadFromString("value: " + value);
                        Object v = Configs.plain(parsed.get("value"));
                        if (this.e.c.file(file).get(path) == null) {
                            throw new IllegalArgumentException("Нет такого существующего параметра");
                        }
                        this.e.c.edit(file, y -> y.set(path, v));
                        break;
                    }
                    case "reload": {
                        this.e.c.reload();
                        if (this.e.schedule != null) {
                            this.e.schedule.reload();
                        }
                        this.e.clearBuildingEditors();
                        this.e.regions.refreshDefinitions();
                        for (Model.Session session : this.e.sessions.values()) {
                            this.e.regions.create(session);
                        }
                        sender.sendMessage(Messages.color("&aНастройки перечитаны. Активные события используют сохранённые параметры."));
                        break;
                    }
                    case "validate": {
                        Collection<Model.Arena> arenas = a.length > 1 ? List.of(this.arena(a[1])) : this.e.c.arenas.values();
                        for (Model.Arena ar4 : arenas) {
                            List<String> issues = this.e.validate(ar4, true);
                            sender.sendMessage(ar4.id + ": " + (issues.isEmpty() ? "OK" : String.join("; ", issues)));
                        }
                        break;
                    }
                    case "status": {
                        for (String line : this.e.hooks.status()) {
                            sender.sendMessage(line);
                        }
                        sender.sendMessage(this.e.structures.status());
                        for (Model.Session s : this.e.sessions.values()) {
                            sender.sendMessage(s.arenaId + " " + s.kind + " " + s.phase + " event=" + s.id + " owner=" + s.owner + " wave=" + s.wave + " remaining=" + Rules.remaining(System.currentTimeMillis(), s.endAt));
                        }
                        sender.sendMessage("Расписание: " + this.e.c.main().getBoolean("scheduling.enabled"));
                        break;
                    }
                    case "audit": {
                        this.e.db.audit(a.length > 1 ? Integer.parseInt(a[1]) : 15).forEach(sender::sendMessage);
                        break;
                    }
                    case "review": {
                        if (a.length < 4) {
                            for (Model.Reward r : this.e.db.rewards(null)) {
                                if (r.coinState != Model.Delivery.REVIEW && r.caseState != Model.Delivery.REVIEW && r.petEggState != Model.Delivery.REVIEW && r.items.stream().noneMatch(i -> i.state == Model.Delivery.REVIEW)) continue;
                                sender.sendMessage(r.id + " owner=" + r.owner + " coins=" + r.coinState + " case=" + r.caseState + (r.petEgg != null ? " egg=" + r.petEgg + ":" + r.petEggState : "") + " items=" + r.items.stream().map(i -> i.state.toString()).toList());
                            }
                            break;
                        }
                        String action = this.arg(a, 3);
                        if (!List.of("delivered", "retry").contains(action)) {
                            throw new IllegalArgumentException("delivered или retry");
                        }
                        this.e.rewards.reconcile(a[1], a[2], action.equals("delivered"), sender.getName());
                        break;
                    }
                    default: {
                        throw new IllegalArgumentException("Неизвестная команда. /rdang help");
                    }
                }
                if (!List.of("menu", "edit", "status", "validate", "audit", "review", "spawn", "test", "launch", "undo", "schem", "autospawn", "give", "stop", "schedule", "move", "shift", "offset", "schemoffset").contains(sub)) {
                    this.e.db.audit(sender.getName(), "ADMIN_COMMAND", sub.equals("config") && a.length > 3 && a[3].startsWith("database.") ? "config database.[hidden]" : String.join(" ", a));
                    sender.sendMessage(Messages.color("&aГотово."));
                }
            }
            catch (Exception ex) {
                this.e.msg.error(sender, Objects.toString(ex.getMessage(), ex.getClass().getSimpleName()));
                if (!sub.equals("schematic") && !sub.equals("schem") && !sub.equals("template") || ex instanceof IllegalArgumentException) break block109;
                this.e.report("Ошибка WorldEdit при работе со схемой", ex);
            }
        }
        return true;
    }

    private void handleSpawn(CommandSender sender, String sub, String[] a) {
        if (a.length < 2) {
            throw new IllegalArgumentException("/rdang " + sub + " self | <ID арены> [site] | <кол-во> [мир]. Доступные ID: " + String.join(", ", this.e.c.arenas.keySet()));
        }
        boolean isTest = sub.equals("test");
        if (a[1].equalsIgnoreCase("self")) {
            Player p = this.player(sender);
            List<Model.Arena> available = this.e.c.arenas.values().stream()
                .filter(ar -> ar.enabled && ar.template)
                .toList();
            if (available.isEmpty()) {
                available = this.e.c.arenas.values().stream().filter(ar -> ar.template).toList();
            }
            if (available.isEmpty()) {
                throw new IllegalArgumentException("Нет доступных арен/схем для размещения");
            }
            Model.Arena chosen = available.get(new Random().nextInt(available.size()));
            sender.sendMessage(Messages.color("&eПоиск места рядом с вами (30-150 блоков) для " + chosen.id + "..."));
            this.e.placement.spawnNear(chosen.id, p.getLocation(), 30, 150, isTest, p.getName()).whenComplete((session, error) -> {
                if (error != null) {
                    this.e.msg.error(sender, Objects.toString(error.getMessage(), "Ошибка размещения рядом"));
                } else {
                    sender.sendMessage(Messages.color("&aАрена " + chosen.id + " успешно создана рядом: " + session.arena.box().world() + " [" + (int)session.arena.chest.x() + ", " + (int)session.arena.chest.y() + ", " + (int)session.arena.chest.z() + "]"));
                }
            });
            return;
        }

        if (a[1].equalsIgnoreCase("all")) {
            List<Model.Arena> targets = this.e.c.arenas.values().stream()
                .filter(ar -> ar.template)
                .toList();
            if (targets.isEmpty()) {
                throw new IllegalArgumentException("Нет доступных арен с шаблонами или схематиками (сохраните шаблон или выберите схему в /jd)");
            }
            for (Model.Arena ar : targets) {
                if (!ar.enabled) {
                    ar.enabled = true;
                    try {
                        this.e.c.saveArena(ar);
                    } catch (Exception ignored) {}
                }
            }
            List<Model.Arena> toSpawn = targets.stream()
                .filter(ar -> !this.e.sessions.containsKey(ar.id) && !this.e.placement.isPending(ar.id))
                .toList();
            if (toSpawn.isEmpty()) {
                throw new IllegalArgumentException("Все доступные арены (" + targets.size() + " шт.) уже заспавнены или размещаются!");
            }
            int limit = this.e.c.limit("placed-events");
            int availableSlots = limit - this.e.sessions.size();
            if (availableSlots <= 0) {
                throw new IllegalArgumentException("Достигнут лимит активных данжей: " + limit + " (limits.placed-events)");
            }
            List<Model.Arena> finalToSpawn = toSpawn.stream().limit(availableSlots).toList();
            sender.sendMessage(Messages.color("&e[JamDungeons] Запуск спавна &6" + finalToSpawn.size() + " &eданжей в разных точках мира..."));
            if (toSpawn.size() > finalToSpawn.size()) {
                sender.sendMessage(Messages.color("&7(Будет заспавнено " + finalToSpawn.size() + " из " + toSpawn.size() + " из-за лимита limits.placed-events = " + limit + ")"));
            }
            long delayTicks = 0L;
            for (Model.Arena ar : finalToSpawn) {
                final Model.Arena currentArena = ar;
                Bukkit.getScheduler().runTaskLater(this.e.p, () -> {
                    if (this.e.sessions.containsKey(currentArena.id) || this.e.placement.isPending(currentArena.id)) return;
                    sender.sendMessage(Messages.color("&7Поиск точки и размещение данжа &a" + currentArena.id + "&7..."));
                    this.e.placement.spawn(currentArena.id, "random", isTest, sender.getName()).whenComplete((session, error) -> {
                        if (error != null) {
                            Throwable cause = error.getCause() != null ? error.getCause() : error;
                            this.e.msg.error(sender, "Ошибка спавна " + currentArena.id + ": " + cause.getMessage());
                        } else {
                            sender.sendMessage(Messages.color("&a✔ Данж &f" + session.arenaId + " &aуспешно размещён! Координаты: &e" + session.arena.box().world() + " [" + (int)session.arena.chest.x() + ", " + (int)session.arena.chest.y() + ", " + (int)session.arena.chest.z() + "]"));
                        }
                    });
                }, delayTicks);
                delayTicks += 10L;
            }
            return;
        }

        Model.Arena foundArena = this.e.c.findArena(a[1]);
        if (foundArena == null) {
            try {
                int count = Integer.parseInt(a[1]);
                if (count < 1 || count > 20) {
                    throw new IllegalArgumentException("Количество должно быть от 1 до 20");
                }
                List<Model.Arena> available = this.e.c.arenas.values().stream().filter(ar -> ar.enabled && ar.template).toList();
                if (available.isEmpty()) {
                    throw new IllegalArgumentException("Нет включенных арен со схемами");
                }
                sender.sendMessage(Messages.color("&eЗапуск генерации &6" + count + " &eданжей..."));
                Random rnd = new Random();
                for (int i = 0; i < count; i++) {
                    Model.Arena ar = available.get(rnd.nextInt(available.size()));
                    this.e.placement.spawn(ar.id, "random", isTest, sender.getName()).whenComplete((session, error) -> {
                        if (error != null) {
                            this.e.msg.error(sender, error.getMessage());
                        } else {
                            sender.sendMessage(Messages.color("&aДанж " + session.arenaId + " размещен: " + session.arena.box().world() + " " + (int)session.arena.chest.x() + " " + (int)session.arena.chest.y() + " " + (int)session.arena.chest.z()));
                        }
                    });
                }
                return;
            } catch (NumberFormatException ignored) {
                // Not a number, proceed to normal spawn
            }
        }

        this.spawn(sender, foundArena != null ? foundArena.id : a[1], a.length > 2 ? a[2] : null, isTest);
    }

    private void handleStop(CommandSender sender, String[] a) {
        if (a.length > 1 && a[1].equalsIgnoreCase("all")) {
            if (this.e.sessions.isEmpty()) {
                throw new IllegalArgumentException("Нет активных событий для остановки.");
            }
            int count = this.e.sessions.size();
            for (Model.Session s : new ArrayList<>(this.e.sessions.values())) {
                this.e.stop(s.arenaId, sender.getName());
            }
            sender.sendMessage(Messages.color("&aВсе активные данжи (" + count + " шт.) остановлены, территории восстанавливаются."));
            return;
        }

        if (a.length > 1) {
            String targetId = a[1];
            Model.Session session = this.e.session(targetId);
            if (session != null) {
                this.e.stop(session.arenaId, sender.getName());
                sender.sendMessage(Messages.color("&aСобытие " + session.arenaId + " остановлено, территория восстанавливается."));
                return;
            }
            throw new IllegalArgumentException("Активная сессия для " + targetId + " не найдена");
        }

        if (sender instanceof Player p) {
            Location loc = p.getLocation();
            for (Model.Session s : this.e.sessions.values()) {
                if (s.arena != null && s.arena.box() != null && s.arena.box().world().equals(loc.getWorld().getName()) && s.arena.box().contains(Model.Point.of(loc))) {
                    this.e.stop(s.arenaId, sender.getName());
                    sender.sendMessage(Messages.color("&aДанж " + s.arenaId + " в вашей локации остановлен."));
                    return;
                }
            }
        }
        if (!this.e.sessions.isEmpty()) {
            Model.Session s = this.e.sessions.values().iterator().next();
            this.e.stop(s.arenaId, sender.getName());
            sender.sendMessage(Messages.color("&aАктивный данж " + s.arenaId + " остановлен."));
            return;
        }
        throw new IllegalArgumentException("Нет активных событий для остановки. /rdang list");
    }

    private void undo(CommandSender sender, String[] a) {
        if (a.length > 1) {
            String targetId = a[1];
            Model.Session session = this.e.session(targetId);
            if (session != null) {
                this.e.stop(session.arenaId, sender.getName());
                sender.sendMessage(Messages.color("&aДанж " + session.arenaId + " остановлен и удален, ландшафт восстановлен."));
                return;
            }
            Engine.ManualPaste targetManual = null;
            for (Engine.ManualPaste mp : this.e.manualPastes) {
                if (mp.id().equalsIgnoreCase(targetId)) {
                    targetManual = mp;
                    break;
                }
            }
            if (targetManual != null) {
                this.e.manualPastes.remove(targetManual);
                this.undoManualPaste(sender, targetManual);
                return;
            }
            Model.Arena arena = this.e.c.arenas.get(targetId);
            if (arena != null && arena.box() != null) {
                this.clearArea(sender, arena.box(), arena.id);
                return;
            }
            throw new IllegalArgumentException("Данж, ручная вставка или арена с ID '" + targetId + "' не найдены");
        }

        if (sender instanceof Player p) {
            Location loc = p.getLocation();
            for (Model.Session s : this.e.sessions.values()) {
                if (s.arena != null && s.arena.box() != null && s.arena.box().world().equals(loc.getWorld().getName()) && s.arena.box().contains(Model.Point.of(loc))) {
                    this.e.stop(s.arenaId, sender.getName());
                    sender.sendMessage(Messages.color("&aДанж " + s.arenaId + " в вашей локации удален и ландшафт восстановлен."));
                    return;
                }
            }
            for (Engine.ManualPaste mp : this.e.manualPastes) {
                if (mp.box().world().equals(loc.getWorld().getName()) && mp.box().contains(Model.Point.of(loc))) {
                    this.e.manualPastes.remove(mp);
                    this.undoManualPaste(sender, mp);
                    return;
                }
            }
            for (Model.Arena arena : this.e.c.arenas.values()) {
                if (arena.box() != null && arena.box().world().equals(loc.getWorld().getName()) && arena.box().contains(Model.Point.of(loc))) {
                    this.clearArea(sender, arena.box(), arena.id);
                    return;
                }
            }
        }
        if (!this.e.manualPastes.isEmpty()) {
            Engine.ManualPaste last = this.e.manualPastes.pop();
            this.undoManualPaste(sender, last);
            return;
        }
        if (!this.e.sessions.isEmpty()) {
            Model.Session last = this.e.sessions.values().iterator().next();
            this.e.stop(last.arenaId, sender.getName());
            sender.sendMessage(Messages.color("&aАктивный данж " + last.arenaId + " остановлен и удален."));
            return;
        }
        throw new IllegalArgumentException("Нет активных данжей или недавних вставок для отката");
    }

    private void clearArea(CommandSender sender, Model.Box box, String label) {
        if (sender != null) {
            sender.sendMessage(Messages.color("&e[FAWE] Плавная очистка области " + label + "..."));
        }
        this.e.structures.clearAreaAsync(box).whenComplete((v, err) -> {
            Bukkit.getScheduler().runTask(this.e.p, () -> {
                if (err != null) {
                    if (sender != null) this.e.msg.error(sender, "Ошибка при очистке области: " + err.getMessage());
                    this.e.report("Ошибка при очистке области " + box, err);
                } else if (sender != null) {
                    sender.sendMessage(Messages.color("&aОбласть " + label + " очищена (установлен воздух через FAWE)."));
                }
            });
        });
    }

    private void undoManualPaste(CommandSender sender, Engine.ManualPaste mp) {
        if (sender != null) {
            sender.sendMessage(Messages.color("&e[FAWE] Откат ручной вставки " + mp.id() + "..."));
        }
        Path backup = this.e.structures.root().resolve(mp.backupPath()).normalize();
        CompletableFuture<Void> task;
        if (Files.isRegularFile(backup)) {
            task = this.e.structures.pasteAsync(backup, mp.box()).thenRun(() -> {
                try {
                    Files.deleteIfExists(backup);
                } catch (Exception ignored) {}
            });
        } else {
            task = this.e.structures.clearAreaAsync(mp.box());
        }
        task.whenComplete((v, err) -> {
            Bukkit.getScheduler().runTask(this.e.p, () -> {
                if (err != null) {
                    if (sender != null) this.e.msg.error(sender, "Ошибка при откате вставки " + mp.id() + ": " + err.getMessage());
                    this.e.report("Ошибка при откате ручной вставки " + mp.id(), err);
                } else if (sender != null) {
                    sender.sendMessage(Messages.color("&aРучная вставка " + mp.id() + " успешно отменена, ландшафт восстановлен через FAWE!"));
                }
            });
        });
    }

    private void schem(CommandSender sender, String[] a) throws Exception {
        Player p = this.player(sender);
        String name = this.arg(a, 1);
        if (name.equalsIgnoreCase("list")) {
            sender.sendMessage(Messages.color("&6Доступные схемы: &f" + String.join(", ", this.e.structures.schematics())));
            return;
        }
        SchematicCodec.Image image = this.e.structures.load(name);
        Location baseLoc = p.getLocation().getBlock().getLocation();
        int[] offset = this.e.c.schematicOffset(name);
        final Location loc = (offset[0] != 0 || offset[1] != 0 || offset[2] != 0)
                ? baseLoc.clone().add(offset[0], offset[1], offset[2])
                : baseLoc;
        if (offset[0] != 0 || offset[1] != 0 || offset[2] != 0) {
            sender.sendMessage(Messages.color("&7Применён настроенный сдвиг схематики: &f" + formatDelta(offset[0], offset[1], offset[2])));
        }
        Model.Box box = new Model.Box(
            loc.getWorld().getName(),
            loc.getBlockX(),
            loc.getBlockY(),
            loc.getBlockZ(),
            loc.getBlockX() + image.width() - 1,
            loc.getBlockY() + image.height() - 1,
            loc.getBlockZ() + image.length() - 1
        );
        this.e.structures.check(box);

        sender.sendMessage(Messages.color("&e[FAWE] Создание резервной копии и плавная вставка схемы &a" + name + "&e..."));
        String backupId = "manual_" + System.currentTimeMillis();
        Path backupFile = this.e.structures.root().resolve("backups/" + backupId + ".schem");

        this.e.structures.captureAsync(box, backupFile)
            .thenCompose(v -> {
                this.e.manualPastes.push(new Engine.ManualPaste(backupId, loc.getWorld().getName(), box, "backups/" + backupId + ".schem", System.currentTimeMillis()));
                return this.e.structures.pasteAsync(image, box);
            })
            .whenComplete((v, err) -> {
                Bukkit.getScheduler().runTask(this.e.p, () -> {
                    if (err != null) {
                        Throwable cause = err.getCause() != null ? err.getCause() : err;
                        this.e.msg.error(sender, "Ошибка вставки: " + cause.getMessage());
                        this.e.report("Ошибка при вставке схемы " + name, cause);
                    } else {
                        sender.sendMessage(Messages.color("&aСхема &e" + name + " &a(" + image.width() + "x" + image.height() + "x" + image.length() + ") успешно установлена через FAWE!"));
                        sender.sendMessage(Messages.color("&7Для отмены и восстановления ландшафта: &f/rdang undo " + backupId));
                    }
                });
            });
    }

    private void autospawn(CommandSender sender, String[] a) {
        if (this.e.schedule == null) {
            throw new IllegalStateException("Модуль расписания (EventSchedule) не инициализирован");
        }
        if (a.length < 2 || a[1].equalsIgnoreCase("status")) {
            this.e.schedule.sendStatus(sender);
            return;
        }
        String action = a[1].toLowerCase(Locale.ROOT);
        switch (action) {
            case "on":
            case "true":
            case "enable": {
                this.e.schedule.setEnabled(true);
                sender.sendMessage(Messages.color("&aАвтоспавн данжей включен."));
                break;
            }
            case "off":
            case "false":
            case "disable": {
                this.e.schedule.setEnabled(false);
                sender.sendMessage(Messages.color("&cАвтоспавн данжей выключен."));
                break;
            }
            case "interval": {
                int minutes = Integer.parseInt(this.arg(a, 2));
                if (minutes < 1 || minutes > 1440) {
                    throw new IllegalArgumentException("Интервал должен быть от 1 до 1440 минут");
                }
                this.e.schedule.setIntervalMinutes(minutes);
                sender.sendMessage(Messages.color("&aИнтервал автоспавна установлен на &e" + minutes + " &aмин."));
                break;
            }
            case "spawn":
            case "trigger":
            case "run": {
                this.e.schedule.triggerNow(sender);
                break;
            }
            case "schedule": {
                this.e.schedule.handleCommand(sender, a);
                break;
            }
            default: {
                throw new IllegalArgumentException("Использование: /rdang autospawn on|off|interval <мин>|status|spawn|schedule");
            }
        }
    }

    private void give(CommandSender sender, String[] a) {
        String itemType = this.arg(a, 1).toLowerCase(Locale.ROOT);
        Player target = Bukkit.getPlayer(this.arg(a, 2));
        if (target == null) {
            throw new IllegalArgumentException("Игрок " + a[2] + " не найден в сети");
        }
        int count = a.length > 3 ? Integer.parseInt(a[3]) : 1;
        if (count < 1 || count > 64) {
            throw new IllegalArgumentException("Количество должно быть от 1 до 64");
        }
        if (itemType.equals("key")) {
            ItemStack key = new ItemStack(Material.TRIPWIRE_HOOK, count);
            ItemMeta meta = key.getItemMeta();
            meta.displayName(Messages.color("&dКлюч от данжа"));
            meta.lore(List.of(
                Messages.color("&7Используется для открытия"),
                Messages.color("&7наградного сундука в данже")
            ));
            key.setItemMeta(meta);
            for (ItemStack drop : target.getInventory().addItem(key).values()) {
                target.getWorld().dropItem(target.getLocation(), drop);
            }
            sender.sendMessage(Messages.color("&aВыдано &e" + count + " &aключей игроку &e" + target.getName()));
            target.sendMessage(Messages.color("&aВы получили &e" + count + " &aключей от данжа!"));
        } else if (itemType.equals("compass")) {
            ItemStack compass = new ItemStack(Material.COMPASS, count);
            CompassMeta meta = (CompassMeta) compass.getItemMeta();
            meta.displayName(Messages.color("&6Компас данжа"));
            meta.lore(List.of(
                Messages.color("&7Указывает на ближайший"),
                Messages.color("&7активный данж")
            ));
            if (!this.e.sessions.isEmpty()) {
                Model.Session active = this.e.sessions.values().iterator().next();
                if (active.arena.chest != null && active.arena.chest.location() != null) {
                    meta.setLodestone(active.arena.chest.location());
                    meta.setLodestoneTracked(false);
                }
            }
            compass.setItemMeta(meta);
            for (ItemStack drop : target.getInventory().addItem(compass).values()) {
                target.getWorld().dropItem(target.getLocation(), drop);
            }
            sender.sendMessage(Messages.color("&aВыдано &e" + count + " &aкомпасов игроку &e" + target.getName()));
            target.sendMessage(Messages.color("&aВы получили &e" + count + " &aкомпас данжа!"));
        } else {
            throw new IllegalArgumentException("Использование: /rdang give key|compass <игрок> [количество]");
        }
    }

    private void spawn(CommandSender sender, String idOrName, String site, boolean test) {
        Model.Arena definition = this.arena(idOrName);
        String id = definition.id;
        if (test) {
            sender.sendMessage(Messages.color("&eТЕСТ: волны и бой работают, выдача наград отключена. Для настоящего события: /jd stop " + id + ", затем /jd spawn " + id + "."));
        }
        sender.sendMessage(Messages.color(PlacementService.automatic(definition, site) ? "&eИщу случайное место для " + definition.name + " (" + id + ") (это не вставка в точке A)…" : "&eПодготавливаю арену " + definition.name + " (" + id + ")…"));
        java.util.concurrent.CompletableFuture<Void> prepareTask;
        if (this.e.sessions.containsKey(id)) {
            sender.sendMessage(Messages.color("&eОстанавливаем предыдущую сессию арены " + id + "..."));
            prepareTask = this.e.stop(id, sender.getName());
        } else {
            prepareTask = java.util.concurrent.CompletableFuture.completedFuture(null);
        }
        prepareTask.whenComplete((v, stopErr) -> {
            Bukkit.getScheduler().runTask(this.e.p, () -> {
                this.e.placement.spawn(id, site, test, sender.getName()).whenComplete((session, error) -> {
                    if (error != null) {
                        this.e.msg.error(sender, Objects.toString(error.getMessage(), "Ошибка размещения"));
                    } else {
                        sender.sendMessage(Messages.color("&aАрена " + definition.name + " (" + id + ") размещена: " + session.kind + " / " + session.arena.box().world() + " " + (int)session.arena.chest.x() + ", " + (int)session.arena.chest.y() + ", " + (int)session.arena.chest.z()));
                    }
                });
            });
        });
    }

    private void schematic(CommandSender sender, String[] args) throws Exception {
        String action = this.arg(args, 1);
        if (action.equals("list")) {
            this.e.structures.schematics().forEach(sender::sendMessage);
            return;
        }
        Model.Arena arena = this.arena(this.arg(args, 2));
        Model.Arena before = Json.copy(arena, Model.Arena.class);
        if (this.e.sessions.containsKey(arena.id)) {
            throw new IllegalArgumentException("Сначала остановите событие");
        }
        if (action.equals("use")) {
            Model.Point origin;
            String file = String.join(" ", Arrays.copyOfRange(args, 3, args.length));
            origin = Model.Point.of(this.player(sender).getLocation().getBlock().getLocation());
            arena = this.e.structures.bind(arena, file, origin);
            Model.Point exit = PlacementService.exitNear(arena.box());
            if (exit != null) {
                arena.exit = exit;
            }
            this.saveSchematic(before, arena);
            sender.sendMessage(Messages.color("&aСхема выбрана, A/B заданы по размерам. Для вставки: /jd schematic paste " + arena.id + ". Затем настройте сундук, выход и точки мобов."));
            this.schematicBounds(sender, arena);
        } else if (action.equals("paste") || action.equals("fit")) {
            SchematicCodec.Image image = this.e.structures.fit(arena);
            this.saveSchematic(before, arena);
            if (action.equals("paste")) {
                sender.sendMessage(Messages.color("&e[FAWE] Создание резервной копии и плавная вставка схемы &a" + arena.id + "&e... (воздух: " + (arena.pasteAir ? "&aВКЛ" : "&cВЫКЛ") + "&e)"));
                String backupId = "arena_" + arena.id;
                Path backupFile = this.e.structures.root().resolve("backups/" + backupId + ".schem");
                Model.Box box = arena.box();
                final Model.Arena finalArena = arena;
                this.e.structures.captureAsync(box, backupFile)
                    .thenCompose(v -> {
                        this.e.manualPastes.push(new Engine.ManualPaste(finalArena.id, finalArena.box().world(), finalArena.box(), "backups/" + backupId + ".schem", System.currentTimeMillis()));
                        return this.e.structures.pasteAsync(finalArena, image);
                    })
                    .whenComplete((v, err) -> {
                        Bukkit.getScheduler().runTask(this.e.p, () -> {
                            if (err != null) {
                                Throwable cause = err.getCause() != null ? err.getCause() : err;
                                this.e.msg.error(sender, "Ошибка вставки схемы: " + cause.getMessage());
                                this.e.report("Ошибка асинхронной вставки схемы " + finalArena.id, cause);
                            } else {
                                sender.sendMessage(Messages.color("&aСхема успешно вставлена через FastAsyncWorldEdit! Границы и приват обновлены. Мобы и PvP не запущены."));
                                sender.sendMessage(Messages.color("&7Для отмены и восстановления: &f/rdang undo " + finalArena.id + " &7или &f/jd schematic clear " + finalArena.id));
                            }
                            this.schematicBounds(sender, finalArena);
                        });
                    });
            } else {
                sender.sendMessage(Messages.color("&aГраницы и приват пересчитаны по файлу схематики."));
                this.schematicBounds(sender, arena);
            }
        } else if (action.equals("air") || action.equals("pasteair")) {
            boolean newValue = args.length > 3 ? Commands.parseBool(this.arg(args, 3)) : !arena.pasteAir;
            this.change(arena.id, ar -> {
                ar.pasteAir = newValue;
            });
            sender.sendMessage(Messages.color("&aВставка воздуха для арены &e" + arena.id + "&a: " + (newValue ? "&aВКЛ (с воздухом)" : "&cВЫКЛ (без воздуха)")));
        } else if (action.equals("clear")) {
            Engine.ManualPaste mp = null;
            for (Engine.ManualPaste p : this.e.manualPastes) {
                if (p.id().equalsIgnoreCase(arena.id)) {
                    mp = p;
                    break;
                }
            }
            if (mp != null) {
                this.e.manualPastes.remove(mp);
                this.undoManualPaste(sender, mp);
            } else {
                this.clearArea(sender, arena.box(), arena.id);
            }
        } else if (action.equals("move") || action.equals("shift")) {
            this.handleMove(sender, Arrays.copyOfRange(args, 1, args.length));
        } else if (action.equals("offset") || action.equals("schemoffset")) {
            this.schematicOffset(sender, Arrays.copyOfRange(args, 1, args.length));
        } else {
            throw new IllegalArgumentException("/jd schematic list|use <arena> <file>|paste|fit|clear|air <arena> [on|off]|move <arena> <dir> [blocks]|offset <arena|file> [dir|dx] [blocks|dy] [dz]");
        }
    }

    private void saveSchematic(Model.Arena previous, Model.Arena arena) throws Exception {
        this.e.regions.sync(arena);
        try {
            this.e.c.saveArena(arena);
        } catch (Exception error) {
            try {
                this.e.regions.deleteDefinition(arena);
                if (previous.pos1 != null && previous.pos2 != null) {
                    this.e.regions.repair(previous);
                }
            } catch (Exception rollback) {
                error.addSuppressed(rollback);
            }
            throw error;
        }
        if (previous.pos1 != null && !previous.pos1.world().equals(arena.pos1.world())) {
            this.e.regions.deleteDefinition(previous);
        }
    }

    private void schematicBounds(CommandSender sender, Model.Arena arena) {
        Model.Box b = arena.box();
        Model.Box region = this.e.c.protectionBox(arena);
        sender.sendMessage(Messages.color("&aСхематика: " + (b.x2() - b.x1() + 1) + "×" + (b.y2() - b.y1() + 1) + "×" + (b.z2() - b.z1() + 1) + "; приват +" + this.e.c.main().getInt("schematics.protection-padding", 4) + " со всех сторон: [" + region.x1() + ", " + region.y1() + ", " + region.z1() + "] — [" + region.x2() + ", " + region.y2() + ", " + region.z2() + "]"));
        sender.sendMessage(Messages.color("&7Вставка воздуха: " + (arena.pasteAir ? "&aВКЛ (с воздухом)" : "&cВЫКЛ (без воздуха)")));
    }

    public static int[] getDirectionDelta(Player player, String direction, int amount) {
        String dir = direction.toLowerCase(Locale.ROOT).trim();
        if (dir.equals("up") || dir.equals("вверх") || dir.equals("upward") || dir.equals("+y") || dir.equals("u")) {
            return new int[]{0, amount, 0};
        }
        if (dir.equals("down") || dir.equals("вниз") || dir.equals("downward") || dir.equals("-y") || dir.equals("d")) {
            return new int[]{0, -amount, 0};
        }
        if (dir.equals("north") || dir.equals("север") || dir.equals("n") || dir.equals("-z")) {
            return new int[]{0, 0, -amount};
        }
        if (dir.equals("south") || dir.equals("юг") || dir.equals("s") || dir.equals("+z")) {
            return new int[]{0, 0, amount};
        }
        if (dir.equals("east") || dir.equals("восток") || dir.equals("e") || dir.equals("+x")) {
            return new int[]{amount, 0, 0};
        }
        if (dir.equals("west") || dir.equals("запад") || dir.equals("w") || dir.equals("-x")) {
            return new int[]{-amount, 0, 0};
        }

        if (player == null) {
            throw new IllegalArgumentException("Направления влево/вправо/вперед/назад доступны только игроку (или используйте север/юг/восток/запад/вверх/вниз)");
        }

        float yaw = (player.getLocation().getYaw() % 360 + 360) % 360;
        int fDx = 0, fDz = 0;
        if (yaw >= 45 && yaw < 135) {
            fDx = -1; fDz = 0; // West
        } else if (yaw >= 135 && yaw < 225) {
            fDx = 0; fDz = -1; // North
        } else if (yaw >= 225 && yaw < 315) {
            fDx = 1; fDz = 0;  // East
        } else {
            fDx = 0; fDz = 1;  // South
        }

        int rDx = -fDz;
        int rDz = fDx;

        if (dir.equals("forward") || dir.equals("вперед") || dir.equals("вперёд") || dir.equals("front") || dir.equals("f")) {
            return new int[]{fDx * amount, 0, fDz * amount};
        }
        if (dir.equals("back") || dir.equals("назад") || dir.equals("backward") || dir.equals("b")) {
            return new int[]{-fDx * amount, 0, -fDz * amount};
        }
        if (dir.equals("right") || dir.equals("вправо") || dir.equals("r")) {
            return new int[]{rDx * amount, 0, rDz * amount};
        }
        if (dir.equals("left") || dir.equals("влево") || dir.equals("l")) {
            return new int[]{-rDx * amount, 0, -rDz * amount};
        }

        throw new IllegalArgumentException("Неизвестное направление: " + direction + ". Доступны: вверх, вниз, влево, вправо, вперед, назад, север, юг, восток, запад");
    }

    public static String formatDelta(int dx, int dy, int dz) {
        List<String> parts = new ArrayList<>();
        if (dy > 0) parts.add("вверх +" + dy);
        else if (dy < 0) parts.add("вниз -" + (-dy));
        if (dx > 0) parts.add("восток +" + dx + " X");
        else if (dx < 0) parts.add("запад " + dx + " X");
        if (dz > 0) parts.add("юг +" + dz + " Z");
        else if (dz < 0) parts.add("север " + dz + " Z");
        return parts.isEmpty() ? "0 блоков" : String.join(", ", parts);
    }

    private static boolean isInteger(String s) {
        if (s == null || s.isEmpty()) return false;
        try {
            Integer.parseInt(s);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public void handleMove(CommandSender sender, String[] a) {
        String[] args = a.length > 1 && (a[0].equalsIgnoreCase("move") || a[0].equalsIgnoreCase("shift"))
                ? Arrays.copyOfRange(a, 1, a.length)
                : a;
        if (args.length < 2) {
            throw new IllegalArgumentException("Использование: /jd move <арена: id|название> <вверх|вниз|влево|вправо|вперед|назад|север|юг|восток|запад|dx> [кол-во|dy] [dz]");
        }

        Model.Arena target = null;
        int dirIndex = -1;

        for (int i = args.length - 1; i >= 1; i--) {
            String candidateName = String.join(" ", Arrays.copyOfRange(args, 0, i));
            Model.Arena found = this.e.c.findArena(candidateName);
            if (found != null) {
                target = found;
                dirIndex = i;
                break;
            }
        }

        if (target == null) {
            for (int i = args.length - 1; i >= 1; i--) {
                String candidateName = String.join("_", Arrays.copyOfRange(args, 0, i));
                Model.Arena found = this.e.c.findArena(candidateName);
                if (found != null) {
                    target = found;
                    dirIndex = i;
                    break;
                }
            }
        }

        if (target == null) {
            throw new IllegalArgumentException("Арена не найдена по ID или названию: " + args[0]);
        }

        Player pl = sender instanceof Player ? (Player) sender : null;
        int dx = 0, dy = 0, dz = 0;

        int remainingArgs = args.length - dirIndex;
        if (remainingArgs >= 3 && isInteger(args[dirIndex]) && isInteger(args[dirIndex + 1]) && isInteger(args[dirIndex + 2])) {
            dx = Integer.parseInt(args[dirIndex]);
            dy = Integer.parseInt(args[dirIndex + 1]);
            dz = Integer.parseInt(args[dirIndex + 2]);
        } else {
            String dirStr = args[dirIndex];
            int amount = 1;
            if (remainingArgs >= 2) {
                try {
                    amount = Integer.parseInt(args[dirIndex + 1]);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Количество блоков должно быть числом: " + args[dirIndex + 1]);
                }
                if (amount <= 0 || amount > 100) {
                    throw new IllegalArgumentException("Количество блоков должно быть от 1 до 100");
                }
            }
            int[] d = getDirectionDelta(pl, dirStr, amount);
            dx = d[0];
            dy = d[1];
            dz = d[2];
        }

        this.moveArena(sender, target, dx, dy, dz, true);
    }

    public CompletableFuture<Void> moveArena(CommandSender sender, Model.Arena arena, int dx, int dy, int dz, boolean moveBlocks) {
        if (arena.pos1 == null || arena.pos2 == null) {
            throw new IllegalArgumentException("У арены '" + arena.name + "' не заданы границы pos1/pos2. Сначала задайте границы или выберите схематику.");
        }
        if (dx == 0 && dy == 0 && dz == 0) {
            throw new IllegalArgumentException("Смещение равно 0 блоков");
        }

        Model.Session session = this.e.session(arena.id);
        Model.Arena targetArena = (session != null && session.arena != null) ? session.arena : arena;

        Model.Box oldBox = targetArena.box();
        Model.Box oldPlayBox = targetArena.playBox();
        Model.Point newPos1 = targetArena.pos1.shift(dx, dy, dz, targetArena.pos1.world());
        Model.Point newPos2 = targetArena.pos2.shift(dx, dy, dz, targetArena.pos2.world());
        Model.Box newBox = Model.Box.of(newPos1, newPos2);

        World world = Bukkit.getWorld(newBox.world());
        if (world == null) {
            throw new IllegalArgumentException("Мир арены не загружен: " + newBox.world());
        }
        if (newBox.y1() < world.getMinHeight() || newBox.y2() >= world.getMaxHeight()) {
            throw new IllegalArgumentException("Новое положение выходит за пределы высоты мира: Y " + newBox.y1() + ".." + newBox.y2() + " (лимиты мира: " + world.getMinHeight() + ".." + world.getMaxHeight() + ")");
        }
        this.e.structures.check(newBox);

        Model.Arena newArena = targetArena.shift(dx, dy, dz);

        if (sender != null) {
            String eventNote = session != null ? " (активное событие, фаза: " + session.phase + ")" : "";
            sender.sendMessage(Messages.color("&e[FAWE] Смещение схематики &a" + targetArena.name + " &7(" + targetArena.id + ")" + eventNote + " &eна &f" + formatDelta(dx, dy, dz) + "&e..."));
        }

        // Сохраняем содержимое сундука, если событие уже идет или в сундуке есть лут
        ItemStack[] initialChestItems = null;
        if (targetArena.chest != null && targetArena.chest.location() != null) {
            try {
                Location oldChestLoc = targetArena.chest.location();
                if (oldChestLoc != null && oldChestLoc.getWorld() != null && oldChestLoc.getBlock().getState() instanceof Container container) {
                    initialChestItems = container.getInventory().getContents().clone();
                }
            } catch (Exception ignored) {}
        }
        final ItemStack[] savedChestItems = initialChestItems;

        Path backupFile;
        if (session != null) {
            if (session.backup == null) {
                session.backup = "backups/" + session.id + ".schem";
            }
            backupFile = this.e.structures.root().resolve(session.backup);
        } else {
            backupFile = this.e.structures.root().resolve("backups/arena_" + arena.id + ".schem");
        }

        boolean hasBackup = Files.isRegularFile(backupFile);
        boolean hasSchematic = targetArena.schematic != null;
        boolean hasTemplate = targetArena.template && Files.isRegularFile(this.e.structures.root().resolve("templates/" + targetArena.id + ".schem"));

        CompletableFuture<Void> undoTask;
        if (moveBlocks && hasBackup) {
            undoTask = this.e.structures.pasteAsync(backupFile, oldBox, true);
        } else if (moveBlocks && (hasSchematic || hasTemplate)) {
            undoTask = this.e.structures.clearAreaAsync(oldBox);
        } else {
            undoTask = CompletableFuture.completedFuture(null);
        }

        CompletableFuture<Void> future = undoTask.thenCompose(v -> {
            if (!moveBlocks) {
                return CompletableFuture.completedFuture(null);
            }
            return this.e.structures.captureAsync(newBox, backupFile);
        }).thenCompose(v -> {
            if (!moveBlocks) {
                return CompletableFuture.completedFuture(null);
            }
            if (session == null) {
                this.e.manualPastes.removeIf(mp -> mp.id().equalsIgnoreCase(arena.id));
                this.e.manualPastes.push(new Engine.ManualPaste(newArena.id, newBox.world(), newBox, "backups/arena_" + arena.id + ".schem", System.currentTimeMillis()));
            }

            if (newArena.schematic != null) {
                SchematicCodec.Image image;
                try {
                    image = this.e.structures.load(newArena.schematic);
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
                return this.e.structures.pasteAsync(newArena, image);
            } else if (hasTemplate) {
                Path templatePath = this.e.structures.root().resolve("templates/" + newArena.id + ".schem");
                return this.e.structures.pasteAsync(templatePath, newBox, newArena.pasteAir);
            }
            return CompletableFuture.completedFuture(null);
        });

        future.whenComplete((v, err) -> {
            Bukkit.getScheduler().runTask(this.e.p, () -> {
                if (err != null) {
                    Throwable cause = err.getCause() != null ? err.getCause() : err;
                    if (sender != null) this.e.msg.error(sender, "Ошибка перемещения: " + cause.getMessage());
                    this.e.report("Ошибка перемещения арены " + targetArena.id, cause);
                    return;
                }

                // Восстанавливаем сохраненные предметы в сундуке на новой позиции
                if (savedChestItems != null && newArena.chest != null) {
                    try {
                        Location newChestLoc = newArena.chest.location();
                        if (newChestLoc != null && newChestLoc.getWorld() != null && newChestLoc.getBlock().getState() instanceof Container container) {
                            container.getInventory().setContents(savedChestItems);
                        }
                    } catch (Exception ignored) {}
                }

                if (session != null) {
                    session.arena = newArena;
                    session.outsideSince = 0L;
                    if (session.lastOwner != null) {
                        session.lastOwner = session.lastOwner.shift(dx, dy, dz, session.lastOwner.world());
                    }

                    // Перемещаем регион WorldGuard для активной сессии
                    if (session.regionId != null) {
                        try {
                            this.e.regions.remove(oldBox.world(), session.regionId);
                        } catch (Exception ignored) {}
                        try {
                            this.e.regions.create(session);
                        } catch (Exception ex) {
                            this.e.p.getLogger().warning("Ошибка обновления региона WorldGuard для сессии " + session.id + ": " + ex.getMessage());
                        }
                    }

                    // Перемещаем живых мобов активной сессии
                    for (String uuidStr : new ArrayList<>(session.entities)) {
                        try {
                            UUID u = UUID.fromString(uuidStr);
                            org.bukkit.entity.Entity ent = Bukkit.getEntity(u);
                            if (ent != null && ent.isValid()) {
                                ent.teleport(ent.getLocation().add(dx, dy, dz));
                            }
                        } catch (Exception ignored) {}
                    }
                    // Смещаем точки привязки и возрождения мобов
                    for (Map.Entry<String, Model.Combatant> entry : session.combatants.entrySet()) {
                        Model.Combatant c = entry.getValue();
                        if (c != null && c.spawn() != null) {
                            entry.setValue(new Model.Combatant(c.preset(), c.spawn().shift(dx, dy, dz, c.spawn().world()), c.wave()));
                        }
                    }

                    // Сохраняем обновленную сессию в БД MariaDB
                    try {
                        this.e.db.save(session);
                    } catch (Exception ex) {
                        this.e.p.getLogger().warning("Ошибка сохранения сессии " + session.id + " в БД: " + ex.getMessage());
                    }

                    // Если арена со статическим размещением, обновляем и исходное описание
                    if (arena.placement == Model.Placement.STATIC) {
                        try {
                            this.e.c.saveArena(newArena);
                            this.e.regions.sync(newArena);
                        } catch (Exception ignored) {}
                    }
                } else {
                    // Сессия не запущена — обновляем шаблон арены и приват
                    try {
                        this.e.c.saveArena(newArena);
                        this.e.regions.sync(newArena);
                    } catch (Exception ex) {
                        if (sender != null) this.e.msg.error(sender, "Ошибка обновления региона: " + ex.getMessage());
                        this.e.report("Ошибка сохранения региона после сдвига " + arena.id, ex);
                        return;
                    }
                }

                // Перемещаем всех игроков внутри арены
                World bukkitWorld = Bukkit.getWorld(oldBox.world());
                if (bukkitWorld != null) {
                    for (Player pl : bukkitWorld.getPlayers()) {
                        if (oldPlayBox.contains(pl.getLocation())) {
                            pl.teleport(pl.getLocation().add(dx, dy, dz));
                        }
                    }
                }

                if (sender != null) {
                    String note = session != null ? " (активное событие, фаза: " + session.phase + ")" : "";
                    sender.sendMessage(Messages.color("&aСхематика арены &e" + newArena.name + " &7(" + newArena.id + ")" + note + " &aуспешно смещена на &f" + formatDelta(dx, dy, dz) + "&a!"));
                    sender.sendMessage(Messages.color("&7Новые границы: &f[" + newBox.x1() + ", " + newBox.y1() + ", " + newBox.z1() + "] — [" + newBox.x2() + ", " + newBox.y2() + ", " + newBox.z2() + "]"));
                    if (newArena.chest != null) {
                        sender.sendMessage(Messages.color("&7Сундук: &f[" + (int)newArena.chest.x() + ", " + (int)newArena.chest.y() + ", " + (int)newArena.chest.z() + "]"));
                    }
                }
            });
        });

        return future;
    }

    public void schematicOffset(CommandSender sender, String[] a) {
        String[] args = a.length > 1 && (a[0].equalsIgnoreCase("offset") || a[0].equalsIgnoreCase("schemoffset") || a[0].equalsIgnoreCase("schematic"))
                ? (a[0].equalsIgnoreCase("schematic") && a.length > 1 && a[1].equalsIgnoreCase("offset")
                    ? Arrays.copyOfRange(a, 2, a.length)
                    : Arrays.copyOfRange(a, 1, a.length))
                : a;
        if (args.length < 1) {
            throw new IllegalArgumentException("Использование: /jd offset <арена|файл_схемы> [вверх|вниз|влево|вправо|вперед|назад|север|юг|восток|запад|dx] [блоки|dy] [dz]");
        }

        Model.Arena targetArena = null;
        String schemFile = null;
        int subArgIndex = -1;

        for (int i = args.length; i >= 1; i--) {
            String candidateName = String.join(" ", Arrays.copyOfRange(args, 0, i));
            Model.Arena found = this.e.c.findArena(candidateName);
            if (found != null) {
                targetArena = found;
                subArgIndex = i;
                break;
            }
        }
        if (targetArena == null) {
            for (int i = args.length; i >= 1; i--) {
                String candidateName = String.join("_", Arrays.copyOfRange(args, 0, i));
                Model.Arena found = this.e.c.findArena(candidateName);
                if (found != null) {
                    targetArena = found;
                    subArgIndex = i;
                    break;
                }
            }
        }

        if (targetArena == null) {
            try {
                List<String> schems = this.e.structures.schematics();
                for (int i = args.length; i >= 1; i--) {
                    String candidateFile = String.join(" ", Arrays.copyOfRange(args, 0, i)).trim();
                    for (String s : schems) {
                        if (s.equalsIgnoreCase(candidateFile) || s.equalsIgnoreCase(candidateFile + ".schem")) {
                            schemFile = s;
                            subArgIndex = i;
                            break;
                        }
                    }
                    if (schemFile != null) break;
                }
            } catch (Exception ignored) {}
            if (schemFile == null && (args[0].endsWith(".schem") || args[0].endsWith(".schematic") || args[0].endsWith(".litematic") || args[0].endsWith(".nbt"))) {
                schemFile = args[0];
                subArgIndex = 1;
            }
        }

        if (targetArena == null && schemFile == null) {
            throw new IllegalArgumentException("Арена или файл схематики не найдены: " + args[0]);
        }

        Player pl = sender instanceof Player ? (Player) sender : null;

        if (subArgIndex >= args.length || args[subArgIndex].equalsIgnoreCase("info")) {
            this.sendOffsetInfo(sender, targetArena, schemFile);
            return;
        }

        String firstSub = args[subArgIndex].toLowerCase(Locale.ROOT);
        if (firstSub.equals("menu") || firstSub.equals("gui")) {
            if (pl == null) {
                throw new IllegalArgumentException("Меню доступно только игрокам в игре");
            }
            this.ui.spawnOffsetMenu(pl, targetArena != null ? targetArena.id : schemFile, 1);
            return;
        }

        int curX, curY, curZ;
        if (targetArena != null) {
            int[] eff = this.e.c.getEffectiveOffset(targetArena);
            curX = eff[0]; curY = eff[1]; curZ = eff[2];
        } else {
            int[] eff = this.e.c.schematicOffset(schemFile);
            curX = eff[0]; curY = eff[1]; curZ = eff[2];
        }

        int newX = curX, newY = curY, newZ = curZ;

        if (firstSub.equals("reset") || firstSub.equals("clear") || firstSub.equals("0")) {
            newX = 0; newY = 0; newZ = 0;
        } else {
            int remaining = args.length - subArgIndex;
            if (remaining >= 3 && isInteger(args[subArgIndex]) && isInteger(args[subArgIndex + 1]) && isInteger(args[subArgIndex + 2])) {
                newX = Integer.parseInt(args[subArgIndex]);
                newY = Integer.parseInt(args[subArgIndex + 1]);
                newZ = Integer.parseInt(args[subArgIndex + 2]);
            } else {
                int amount = 1;
                if (remaining >= 2) {
                    try {
                        amount = Integer.parseInt(args[subArgIndex + 1]);
                    } catch (NumberFormatException e) {
                        throw new IllegalArgumentException("Количество блоков должно быть числом: " + args[subArgIndex + 1]);
                    }
                    if (amount <= 0 || amount > 100) {
                        throw new IllegalArgumentException("Количество блоков должно быть от 1 до 100");
                    }
                }
                int[] delta = getDirectionDelta(pl, firstSub, amount);
                if (delta[1] != 0) {
                    newY = delta[1];
                } else {
                    newX = delta[0];
                    newZ = delta[2];
                }
            }
        }

        if (targetArena != null) {
            targetArena.offsetX = newX;
            targetArena.offsetY = newY;
            targetArena.offsetZ = newZ;
            this.e.c.saveArena(targetArena);
        } else {
            this.e.c.setSchematicOffset(schemFile, newX, newY, newZ);
        }

        String yDesc;
        if (newY < 0) yDesc = "Вниз на " + Math.abs(newY) + " бл.";
        else if (newY > 0) yDesc = "Вверх на " + newY + " бл.";
        else yDesc = "0 (на уровне поверхности)";

        String targetName = targetArena != null ? "арены &e" + targetArena.name + " &7(" + targetArena.id + ")" : "файла схематики &e" + schemFile;
        sender.sendMessage(Messages.color("&aСдвиг спавна для " + targetName + " &aустановлен: &fX: " + (newX >= 0 ? "+" : "") + newX + ", Y: " + (newY >= 0 ? "+" : "") + newY + " &7(" + yDesc + ")&f, Z: " + (newZ >= 0 ? "+" : "") + newZ));
        sender.sendMessage(Messages.color("&7При каждом спавне постройка будет автоматически смещаться на эти координаты."));

        if (targetArena != null) {
            Model.Session active = this.e.session(targetArena.id);
            if (active != null) {
                sender.sendMessage(Messages.color("&e[!] Арена сейчас активна в мире. Чтобы сдвинуть текущую постройку прямо сейчас: &f/jd move " + targetArena.id + " <направление> <блоки>"));
            }
        }
    }

    private void sendOffsetInfo(CommandSender sender, Model.Arena ar, String schemFile) {
        int[] eff = ar != null ? this.e.c.getEffectiveOffset(ar) : this.e.c.schematicOffset(schemFile);
        String yDesc;
        if (eff[1] < 0) yDesc = "Вниз на " + Math.abs(eff[1]) + " бл.";
        else if (eff[1] > 0) yDesc = "Вверх на " + eff[1] + " бл.";
        else yDesc = "0 (на уровне поверхности)";

        String title = ar != null ? "Арена: " + ar.name + " (" + ar.id + ")" : "Схематика: " + schemFile;
        sender.sendMessage(Messages.color("&6=== Преднастройка сдвига спавна: &f" + title + " &6==="));
        if (ar != null && ar.schematic != null) {
            sender.sendMessage(Messages.color("&7Файл схематики: &f" + ar.schematic));
        }
        sender.sendMessage(Messages.color("&7Текущий сдвиг спавна: &eX: " + (eff[0] >= 0 ? "+" : "") + eff[0] + ", Y: " + (eff[1] >= 0 ? "+" : "") + eff[1] + " &7(" + yDesc + ")&e, Z: " + (eff[2] >= 0 ? "+" : "") + eff[2]));
        if (ar != null && ar.offsetX == 0 && ar.offsetY == 0 && ar.offsetZ == 0 && (eff[0] != 0 || eff[1] != 0 || eff[2] != 0)) {
            sender.sendMessage(Messages.color("&8(унаследовано из настроек файла схематики)"));
        }
        sender.sendMessage(Messages.color("&7Команды:"));
        String cmdTarget = ar != null ? ar.id : schemFile;
        sender.sendMessage(Messages.color("  &f/jd offset " + cmdTarget + " вниз <блоки> &7— сдвинуть спавн вниз"));
        sender.sendMessage(Messages.color("  &f/jd offset " + cmdTarget + " вверх <блоки> &7— сдвинуть спавн вверх"));
        sender.sendMessage(Messages.color("  &f/jd offset " + cmdTarget + " <dx> <dy> <dz> &7— точный вектор"));
        sender.sendMessage(Messages.color("  &f/jd offset " + cmdTarget + " reset &7— сбросить в 0"));
        if (sender instanceof Player) {
            sender.sendMessage(Messages.color("  &e/jd offset " + cmdTarget + " menu &7— открыть GUI настройки"));
        }
    }

    private void point(CommandSender sender, String[] a) {
        String op = this.arg(a, 1);
        String id = this.arg(a, 2);
        Model.Arena ar = this.arena(id);
        if (op.equals("list")) {
            for (Model.Spawn point2 : ar.points) {
                sender.sendMessage(point2.id + " wave=" + point2.wave + " mob=" + point2.mob + " " + point2.point);
            }
            return;
        }
        if (op.equals("show")) {
            Player pl = this.player(sender);
            for (Model.Spawn point3 : ar.points) {
                Location loc = point3.point.location();
                if (loc == null || !loc.getWorld().equals(pl.getWorld())) continue;
                pl.spawnParticle(Particle.HAPPY_VILLAGER, loc, 12, 0.3, 0.5, 0.3);
            }
            return;
        }
        if (op.equals("clear")) {
            if (!this.arg(a, 3).equals("confirm")) {
                throw new IllegalArgumentException("Для очистки добавьте confirm");
            }
            ar.points.clear();
        } else if (op.equals("add")) {
            String pointId;
            int wave = a.length > 3 ? Integer.parseInt(a[3]) : 0;
            String mob = a.length > 4 ? a[4] : "any";
            pointId = a.length > 5 ? a[5] : "p" + (ar.points.stream().map(x -> x.id).toList().hashCode() & Integer.MAX_VALUE) + "_" + ar.points.size();
            if (wave < 0 || wave > 5 || !mob.equals("any") && !this.e.c.themes.get(ar.theme).mobs.containsKey(mob) || !Rules.validId(pointId)) {
                throw new IllegalArgumentException("Волна 0..5, моб any или существующий preset, корректный ID точки");
            }
            if (ar.points.stream().anyMatch(pt -> pt.id.equals(pointId))) {
                throw new IllegalArgumentException("ID точки занят");
            }
            ar.points.add(new Model.Spawn(pointId, Model.Point.of(this.player(sender).getLocation()), wave, mob));
        } else {
            Model.Spawn pt2 = ar.points.stream().filter(point -> point.id.equals(this.arg(a, 3))).findFirst().orElseThrow(() -> new IllegalArgumentException("Нет точки"));
            switch (op) {
                case "remove": {
                    ar.points.remove(pt2);
                    break;
                }
                case "move": {
                    pt2.point = Model.Point.of(this.player(sender).getLocation());
                    break;
                }
                case "tp": {
                    this.player(sender).teleport(Objects.requireNonNull(pt2.point.location()));
                    return;
                }
                default: {
                    throw new IllegalArgumentException("point add|list|show|remove|move|tp|clear");
                }
            }
        }
        this.e.c.saveArena(ar);
    }

    private void site(CommandSender sender, String[] a) {
        String op = this.arg(a, 1);
        String id = this.arg(a, 2);
        Model.Arena ar = this.arena(id);
        if (op.equals("list")) {
            ar.sites.forEach((name, point) -> sender.sendMessage(name + " " + point));
            return;
        }
        String name2 = this.arg(a, 3);
        if (!Rules.validId(name2)) {
            throw new IllegalArgumentException("Некорректное имя площадки");
        }
        if (op.equals("add")) {
            Location l = this.player(sender).getLocation().getBlock().getLocation();
            ar.sites.put(name2, Model.Point.of(l));
        } else if (op.equals("remove")) {
            ar.sites.remove(name2);
        } else {
            throw new IllegalArgumentException("site add|remove|list");
        }
        this.e.c.saveArena(ar);
    }

    public void setWave(String theme, int wave, String mob, int count) {
        if (wave < 1 || wave > 5 || count < 0 || count > 256) {
            throw new IllegalArgumentException("Волна 1..5, количество 0..256");
        }
        Model.Theme t = this.e.c.themes.get(theme);
        if (t == null || !t.mobs.containsKey(mob)) {
            throw new IllegalArgumentException("Нет темы/моба");
        }
        this.e.c.edit("themes", y -> {
            ArrayList<Map<String, Object>> rows = new ArrayList<>();
            for (Map<?, ?> row : y.getMapList("themes." + theme + ".waves")) {
                LinkedHashMap<String, Object> copy = new LinkedHashMap<>();
                row.forEach((k, v) -> copy.put(k.toString(), v));
                rows.add(copy);
            }
            if (count == 0) {
                rows.get(wave - 1).remove(mob);
            } else {
                rows.get(wave - 1).put(mob, count);
            }
            y.set("themes." + theme + ".waves", rows);
        });
    }

    private void wave(String[] a) {
        if (this.arg(a, 1).equals("reset")) {
            if (a.length < 4 || !a[3].equals("confirm")) {
                throw new IllegalArgumentException("Заменить все 5 волн стандартными: /jd wave reset " + this.arg(a, 2) + " confirm");
            }
            this.resetWaves(this.arg(a, 2));
            return;
        }
        if (!this.arg(a, 1).equals("set")) {
            throw new IllegalArgumentException("/jd wave set <theme> <1..5> <mob> <count>; /jd wave reset <theme> confirm");
        }
        this.setWave(this.arg(a, 2), Integer.parseInt(this.arg(a, 3)), this.arg(a, 4), Integer.parseInt(this.arg(a, 5)));
    }

    public void resetWaves(String theme) {
        if (!this.e.c.themes.containsKey(theme)) {
            throw new IllegalArgumentException("Нет темы " + theme);
        }
        List<Map<?, ?>> rows = this.e.c.standardWaves(theme);
        this.e.c.edit("themes", yaml -> yaml.set("themes." + theme + ".waves", rows));
    }

    public static boolean parseBool(String value) {
        if (List.of("on", "true", "1").contains(value.toLowerCase(Locale.ROOT))) {
            return true;
        }
        if (List.of("off", "false", "0").contains(value.toLowerCase(Locale.ROOT))) {
            return false;
        }
        throw new IllegalArgumentException("Укажите on или off");
    }

    private void protection(CommandSender sender, String[] args) throws Exception {
        Model.Arena source = this.arena(this.arg(args, 1));
        if (args.length > 2 && !args[2].equalsIgnoreCase("repair")) {
            throw new IllegalArgumentException("/jd protection <id> [repair]");
        }
        Model.Session session = this.e.sessions.get(source.id);
        if (args.length > 2) {
            this.e.regions.repair(source);
            if (session != null) {
                this.e.regions.create(session);
            }
        }
        sender.sendMessage("JamDungeons " + this.e.version() + ": встроенная защита включена; OP/Creative сами её не обходят");
        this.describeProtection(sender, "Исходная постройка", WorldGuardRegions.definitionId(source.id), source);
        if (session != null) {
            this.describeProtection(sender, "Событие " + session.phase, WorldGuardRegions.eventId(session), session.arena);
        }
        if (sender instanceof Player player) {
            sender.sendMessage("Ваш блок: " + player.getWorld().getName() + " " + player.getLocation().getBlockX() + " " + player.getLocation().getBlockY() + " " + player.getLocation().getBlockZ() + "; защищён=" + this.e.buildProtected(player.getLocation()) + "; явное редактирование=" + this.e.editingBuilding(player, source.id));
            Block target = player.getTargetBlockExact(8);
            if (target != null) {
                sender.sendMessage("Блок под прицелом: " + target.getX() + " " + target.getY() + " " + target.getZ() + "; защищён=" + this.e.buildProtected(target.getLocation()));
            }
        }
    }

    private void describeProtection(CommandSender sender, String title, String regionId, Model.Arena arena) {
        Model.Box box = this.e.c.protectionBox(arena);
        sender.sendMessage(title + ": " + box.world() + " [" + box.x1() + ", " + box.y1() + ", " + box.z1() + "] — [" + box.x2() + ", " + box.y2() + ", " + box.z2() + "]");
        sender.sendMessage(this.e.regions.status(regionId, arena));
    }

    private void help(CommandSender s) {
        s.sendMessage(Messages.color("&6===== &eRDang / JamDungeons &6====="));
        s.sendMessage(Messages.color("&a/rdang menu &7— открыть главное меню управления"));
        s.sendMessage(Messages.color("&a/rdang list &7— список активных данжей и сессий"));
        s.sendMessage(Messages.color("&a/rdang rewards &7— забрать награды за прохождение"));
        if (!isAdmin(s)) {
            return;
        }
        s.sendMessage(Messages.color("&a/rdang spawn self &7— заспавнить данж рядом с собой (30–150 блоков)"));
        s.sendMessage(Messages.color("&a/rdang spawn <id> [site] &7— спавн конкретной арены"));
        s.sendMessage(Messages.color("&a/rdang spawn <кол-во> [мир] &7— спавн N случайных данжей"));
        s.sendMessage(Messages.color("&a/rdang stop [id] &7— остановить данж и восстановить территорию"));
        s.sendMessage(Messages.color("&a/rdang undo [id] &7— откат данжа или ручной вставки схемы"));
        s.sendMessage(Messages.color("&a/rdang schem <файл> [ignoreAir] &7— установить схему на позиции игрока"));
        s.sendMessage(Messages.color("&a/rdang autospawn on|off|interval|status|spawn|schedule &7— управление автоспавном"));
        s.sendMessage(Messages.color("&a/jd schedule [list|add|remove|toggle|clear] &7— отложенные спавны (до 10 слотов)"));
        s.sendMessage(Messages.color("&a/rdang give key|compass <игрок> [кол-во] &7— выдать ключ или компас"));
        s.sendMessage(Messages.color("&a/rdang reload &7— перезагрузить конфигурации плагина"));
        s.sendMessage(Messages.color("&a/rdang status &7— статус подключений, WorldEdit и баз данных"));
        s.sendMessage(Messages.color("&7--- Администрирование арен (/jd) ---"));
        s.sendMessage(Messages.color("&7/jd create <id> pve|pvp [theme]; /jd edit <id>; /jd delete <id> confirm"));
        s.sendMessage(Messages.color("&7/jd build <id> on|off; /jd protection <id> [repair]"));
        s.sendMessage(Messages.color("&7/jd schematic list; /jd schematic use <id> <file>; /jd schematic paste <id>"));
        s.sendMessage(Messages.color("&7/jd pos1|pos2|chest|exit <id>; /jd enable <id> on|off"));
        s.sendMessage(Messages.color("&7/jd point add <id> [wave] [mob] [point-id]; /jd point list|show <id>"));
        s.sendMessage(Messages.color("&7/jd wave set <theme> <wave> <mob> <count>; /jd timer pve|pvp <sec>"));
        s.sendMessage(Messages.color("&7/jd case list; /jd case map donation|epic|resource <key-id>"));
        s.sendMessage(Messages.color("&7/jd config set <file> <path> <val>; /jd validate [id]; /jd audit [count]"));
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command command, String alias, String[] a) {
        ArrayList<String> options = new ArrayList<>();
        boolean admin = isAdmin(s);
        if (a.length == 1) {
            options.addAll(List.of("help", "list", "rewards"));
            if (admin) {
                options.addAll(List.of(
                    "menu", "spawn", "stop", "undo", "schem", "autospawn", "give", "reload", "status", "test",
                    "build", "protection", "create", "edit", "delete", "rename", "theme", "enable",
                    "schematic", "template", "pos1", "pos2", "chest", "exit", "point", "site",
                    "wave", "combat", "timer", "case", "config", "validate", "audit", "review",
                    "type", "placement", "worlds", "launch", "voucher", "schedule", "move", "shift", "offset", "schemoffset"
                ));
            }
        } else if (admin) {
            String sub = a[0].toLowerCase(Locale.ROOT);
            if (a.length == 2) {
                switch (sub) {
                    case "schedule": {
                        options.addAll(List.of("list", "add", "remove", "toggle", "clear"));
                        break;
                    }
                    case "undo": {
                        options.addAll(this.e.sessions.keySet());
                        for (Model.Session sess : this.e.sessions.values()) {
                            options.add(sess.arenaId);
                        }
                        for (Engine.ManualPaste mp : this.e.manualPastes) {
                            options.add(mp.id());
                        }
                        break;
                    }
                    case "stop": {
                        options.add("all");
                        options.addAll(this.e.sessions.keySet());
                        for (Model.Session sess : this.e.sessions.values()) {
                            options.add(sess.arenaId);
                        }
                        break;
                    }
                    case "schem": {
                        options.add("list");
                        try {
                            options.addAll(this.e.structures.schematics());
                        } catch (Exception ignored) {}
                        break;
                    }
                    case "autospawn": {
                        options.addAll(List.of("on", "off", "interval", "status", "spawn", "schedule"));
                        break;
                    }
                    case "give": {
                        options.addAll(List.of("key", "compass"));
                        break;
                    }
                    case "spawn":
                    case "test": {
                        options.add("all");
                        options.add("self");
                        options.addAll(List.of("1", "2", "3", "5"));
                        options.addAll(this.e.c.arenas.keySet());
                        break;
                    }
                    case "offset":
                    case "schemoffset":
                    case "move":
                    case "shift":
                    case "edit":
                    case "protection":
                    case "build":
                    case "delete":
                    case "rename":
                    case "theme":
                    case "enable":
                    case "pos1":
                    case "pos2":
                    case "chest":
                    case "exit":
                    case "validate":
                    case "type":
                    case "placement":
                    case "pasteair":
                    case "air":
                    case "worlds": {
                        options.addAll(this.e.c.arenas.keySet());
                        for (Model.Arena ar : this.e.c.arenas.values()) {
                            if (ar.name != null && !ar.name.equalsIgnoreCase(ar.id)) {
                                options.add(Messages.stripColor(ar.name).trim().replace(' ', '_'));
                            }
                        }
                        if (sub.equals("offset") || sub.equals("schemoffset")) {
                            try {
                                options.addAll(this.e.structures.schematics());
                            } catch (Exception ignored) {}
                        }
                        break;
                    }
                    case "point": {
                        options.addAll(List.of("add", "list", "show", "move", "remove", "tp", "clear"));
                        break;
                    }
                    case "site": {
                        options.addAll(List.of("add", "remove", "list"));
                        break;
                    }
                    case "template": {
                        options.add("save");
                        break;
                    }
                    case "schematic": {
                        options.addAll(List.of("list", "use", "paste", "fit", "clear", "air", "move", "shift", "offset"));
                        break;
                    }
                    case "wave": {
                        options.addAll(List.of("set", "reset"));
                        break;
                    }
                    case "timer": {
                        options.addAll(List.of("pve", "pvp"));
                        break;
                    }
                    case "combat": {
                        options.addAll(List.of("on", "off"));
                        break;
                    }
                    case "launch": {
                        options.addAll(List.of("all", "pve", "pvp"));
                        break;
                    }
                    case "case": {
                        options.addAll(List.of("list", "map"));
                        break;
                    }
                }
            } else if (a.length == 3) {
                switch (sub) {
                    case "schedule": {
                        String schedAction = a[1].toLowerCase(Locale.ROOT);
                        if (schedAction.equals("add")) {
                            options.addAll(List.of("12:00", "15:00", "18:00", "21:00", "+30m", "+60m"));
                        } else if (schedAction.equals("remove") || schedAction.equals("toggle")) {
                            if (this.e.schedule != null) {
                                for (int i = 1; i <= this.e.schedule.getDeferredSpawns().size(); i++) {
                                    options.add(Integer.toString(i));
                                }
                            }
                        }
                        break;
                    }
                    case "give": {
                        for (Player p : Bukkit.getOnlinePlayers()) {
                            options.add(p.getName());
                        }
                        break;
                    }
                    case "schem": {
                        options.addAll(List.of("true", "false"));
                        break;
                    }
                    case "autospawn": {
                        if (a[1].equalsIgnoreCase("interval")) {
                            options.addAll(List.of("15", "30", "45", "60", "120"));
                        } else if (a[1].equalsIgnoreCase("schedule")) {
                            options.addAll(List.of("list", "add", "remove", "toggle", "clear"));
                        }
                        break;
                    }
                    case "point":
                    case "site":
                    case "template": {
                        options.addAll(this.e.c.arenas.keySet());
                        break;
                    }
                    case "build":
                    case "enable": {
                        options.addAll(List.of("on", "off"));
                        break;
                    }
                    case "protection": {
                        options.add("repair");
                        break;
                    }
                    case "placement": {
                        options.addAll(List.of("static", "ground", "air", "water", "underwater"));
                        break;
                    }
                    case "type": {
                        options.addAll(List.of("pve", "pvp"));
                        break;
                    }
                    case "theme":
                    case "wave": {
                        options.addAll(this.e.c.themes.keySet());
                        break;
                    }
                    case "case": {
                        if (a[1].equals("map")) {
                            options.addAll(List.of("donation", "epic", "resource"));
                        }
                        break;
                    }
                    case "pasteair":
                    case "air": {
                        options.addAll(List.of("on", "off", "true", "false"));
                        break;
                    }
                    case "offset":
                    case "schemoffset":
                    case "move":
                    case "shift": {
                        options.addAll(List.of(
                            "вверх", "вниз", "влево", "вправо", "вперед", "назад",
                            "север", "юг", "восток", "запад",
                            "up", "down", "left", "right", "forward", "back",
                            "north", "south", "east", "west"
                        ));
                        if (sub.equals("offset") || sub.equals("schemoffset")) {
                            options.addAll(List.of("reset", "menu", "info", "0"));
                        }
                        break;
                    }
                    case "schematic": {
                        if (List.of("use", "paste", "fit", "clear", "air", "move", "shift", "offset").contains(a[1])) {
                            options.addAll(this.e.c.arenas.keySet());
                            for (Model.Arena ar : this.e.c.arenas.values()) {
                                if (ar.name != null && !ar.name.equalsIgnoreCase(ar.id)) {
                                    options.add(Messages.stripColor(ar.name).trim().replace(' ', '_'));
                                }
                            }
                            if (a[1].equalsIgnoreCase("offset") || a[1].equalsIgnoreCase("use")) {
                                try {
                                    options.addAll(this.e.structures.schematics());
                                } catch (Exception ignored) {}
                            }
                        }
                        break;
                    }
                }
            } else if (a.length == 4) {
                if (sub.equals("move") || sub.equals("shift") || sub.equals("offset") || sub.equals("schemoffset")) {
                    options.addAll(List.of("1", "2", "3", "5", "10", "0"));
                } else if (sub.equals("schematic") && (a[1].equalsIgnoreCase("offset") || a[1].equalsIgnoreCase("schemoffset"))) {
                    options.addAll(List.of(
                        "вверх", "вниз", "влево", "вправо", "вперед", "назад",
                        "север", "юг", "восток", "запад",
                        "up", "down", "left", "right", "forward", "back",
                        "north", "south", "east", "west",
                        "reset", "menu", "info", "0"
                    ));
                } else if (sub.equals("schematic") && (a[1].equalsIgnoreCase("move") || a[1].equalsIgnoreCase("shift"))) {
                    options.addAll(List.of(
                        "вверх", "вниз", "влево", "вправо", "вперед", "назад",
                        "север", "юг", "восток", "запад",
                        "up", "down", "left", "right", "forward", "back",
                        "north", "south", "east", "west"
                    ));
                } else if (sub.equals("schedule") && a[1].equalsIgnoreCase("add")) {
                    options.add("any");
                    options.addAll(this.e.c.arenas.keySet());
                } else if (sub.equals("schematic") && a[1].equalsIgnoreCase("air")) {
                    options.addAll(List.of("on", "off", "true", "false"));
                } else if (sub.equals("autospawn") && a[1].equalsIgnoreCase("schedule")) {
                    String schedAction = a[2].toLowerCase(Locale.ROOT);
                    if (schedAction.equals("add")) {
                        options.addAll(List.of("12:00", "15:00", "18:00", "21:00", "+30m", "+60m"));
                    } else if (schedAction.equals("remove") || schedAction.equals("toggle")) {
                        if (this.e.schedule != null) {
                            for (int i = 1; i <= this.e.schedule.getDeferredSpawns().size(); i++) {
                                options.add(Integer.toString(i));
                            }
                        }
                    }
                } else if (sub.equals("give")) {
                    options.addAll(List.of("1", "2", "3", "5", "10"));
                } else if (sub.equals("schematic") && a[1].equals("use")) {
                    try {
                        options.addAll(this.e.structures.schematics());
                    } catch (Exception ignored) {}
                } else if (sub.equals("wave") && a[1].equals("reset")) {
                    options.add("confirm");
                } else if (sub.equals("case") && a[1].equals("map")) {
                    try {
                        options.addAll(this.e.hooks.caseIds());
                    } catch (Exception ignored) {}
                }
            } else if (a.length == 5) {
                if (sub.equals("schematic") && (a[1].equalsIgnoreCase("move") || a[1].equalsIgnoreCase("shift") || a[1].equalsIgnoreCase("offset") || a[1].equalsIgnoreCase("schemoffset"))) {
                    options.addAll(List.of("1", "2", "3", "5", "10", "0"));
                } else if (sub.equals("autospawn") && a[1].equalsIgnoreCase("schedule") && a[2].equalsIgnoreCase("add")) {
                    options.add("any");
                    options.addAll(this.e.c.arenas.keySet());
                } else if (sub.equals("point") && a[1].equals("add")) {
                    options.add("any");
                    Model.Arena selected = this.e.c.arenas.get(a[2]);
                    if (selected != null && this.e.c.themes.containsKey(selected.theme)) {
                        options.addAll(this.e.c.themes.get(selected.theme).mobs.keySet());
                    }
                }
            }
        }
        String prefix = a[a.length - 1].toLowerCase(Locale.ROOT);
        return options.stream().filter(x -> x.toLowerCase(Locale.ROOT).startsWith(prefix)).distinct().sorted().toList();
    }
}
