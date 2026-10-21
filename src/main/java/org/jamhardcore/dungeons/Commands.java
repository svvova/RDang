/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.Bukkit
 *  org.bukkit.Location
 *  org.bukkit.Particle
 *  org.bukkit.block.Block
 *  org.bukkit.block.data.type.Chest
 *  org.bukkit.command.Command
 *  org.bukkit.command.CommandExecutor
 *  org.bukkit.command.CommandSender
 *  org.bukkit.command.TabCompleter
 *  org.bukkit.configuration.file.YamlConfiguration
 *  org.bukkit.entity.Player
 */
package org.jamhardcore.dungeons;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Chest;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.jamhardcore.dungeons.AdminUI;
import org.jamhardcore.dungeons.Configs;
import org.jamhardcore.dungeons.Engine;
import org.jamhardcore.dungeons.Json;
import org.jamhardcore.dungeons.Messages;
import org.jamhardcore.dungeons.Model;
import org.jamhardcore.dungeons.PlacementService;
import org.jamhardcore.dungeons.Rules;
import org.jamhardcore.dungeons.SchematicCodec;
import org.jamhardcore.dungeons.WorldGuardRegions;

public final class Commands
implements CommandExecutor,
TabCompleter {
    private final Engine e;
    private final AdminUI ui;

    public Commands(Engine e, AdminUI ui) {
        this.e = e;
        this.ui = ui;
    }

    private Player player(CommandSender sender) {
        if (!(sender instanceof Player)) {
            throw new IllegalArgumentException("\u041d\u0443\u0436\u043d\u043e \u0432\u044b\u043f\u043e\u043b\u043d\u0438\u0442\u044c \u043e\u0442 \u0438\u0433\u0440\u043e\u043a\u0430");
        }
        Player pl = (Player)sender;
        return pl;
    }

    private String arg(String[] a, int n) {
        if (n >= a.length) {
            throw new IllegalArgumentException("\u041d\u0435\u0434\u043e\u0441\u0442\u0430\u0442\u043e\u0447\u043d\u043e \u0430\u0440\u0433\u0443\u043c\u0435\u043d\u0442\u043e\u0432. /jd help");
        }
        return a[n];
    }

    private Model.Arena arena(String id) {
        Model.Arena a = this.e.c.arenas.get(id);
        if (a == null) {
            throw new IllegalArgumentException("\u041d\u0435\u0442 \u0430\u0440\u0435\u043d\u044b " + id);
        }
        return Json.copy(a, Model.Arena.class);
    }

    private void change(String id, Consumer<Model.Arena> fn) throws Exception {
        boolean regionChanged;
        Model.Arena previous = this.arena(id);
        Model.Arena a = Json.copy(previous, Model.Arena.class);
        fn.accept(a);
        boolean bl = regionChanged = a.kind != previous.kind || !Objects.equals(a.pos1, previous.pos1) || !Objects.equals(a.pos2, previous.pos2);
        if (regionChanged && this.e.sessions.containsKey(id)) {
            throw new IllegalArgumentException("\u0421\u043d\u0430\u0447\u0430\u043b\u0430 \u043e\u0441\u0442\u0430\u043d\u043e\u0432\u0438\u0442\u0435 \u0441\u043e\u0431\u044b\u0442\u0438\u0435: /jd stop " + id);
        }
        this.e.c.saveArena(a);
        if (regionChanged) {
            try {
                this.e.regions.sync(a);
                if (previous.pos1 != null && a.pos1 != null && !previous.pos1.world().equals(a.pos1.world())) {
                    this.e.regions.deleteDefinition(previous);
                }
            }
            catch (Exception ex) {
                this.e.c.saveArena(previous);
                throw ex;
            }
        }
    }

    public void execute(CommandSender sender, String ... args) {
        this.onCommand(sender, null, "jd", args);
    }

    public boolean onCommand(CommandSender sender, Command command, String label, String[] a) {
        block109: {
            String sub = a.length == 0 ? "menu" : a[0].toLowerCase(Locale.ROOT);
            try {
                if (sub.equals("rewards")) {
                    if (!sender.hasPermission("jamdungeons.claim")) {
                        throw new IllegalArgumentException("\u041d\u0435\u0442 \u043f\u0440\u0430\u0432\u0430 jamdungeons.claim");
                    }
                    if (a.length > 1 && a[1].equals("take")) {
                        this.e.rewards.all(this.player(sender));
                    } else {
                        this.ui.pending(this.player(sender));
                    }
                    return true;
                }
                if (sub.equals("list")) {
                    for (Model.Session s : this.e.sessions.values()) {
                        sender.sendMessage(Messages.color("&a" + s.arenaId + " &7" + String.valueOf((Object)s.kind) + " / " + String.valueOf((Object)s.phase) + " &f" + s.arena.box().world() + " " + (int)s.arena.chest.x() + " " + (int)s.arena.chest.y() + " " + (int)s.arena.chest.z()));
                    }
                    return true;
                }
                if (sub.equals("help")) {
                    this.help(sender);
                    return true;
                }
                if (!sender.hasPermission("jamdungeons.admin")) {
                    this.e.msg.send(sender, "no-permission");
                    return true;
                }
                switch (sub) {
                    case "build": {
                        Player editor = this.player(sender);
                        Model.Arena selected = this.arena(this.arg(a, 1));
                        boolean enabled = Commands.parseBool(this.arg(a, 2));
                        this.e.editBuilding(editor, selected, enabled);
                        sender.sendMessage(Messages.color((String)(enabled ? "&e\u0420\u0435\u0434\u0430\u043a\u0442\u0438\u0440\u043e\u0432\u0430\u043d\u0438\u0435 \u0438\u0441\u0445\u043e\u0434\u043d\u043e\u0439 \u043f\u043e\u0441\u0442\u0440\u043e\u0439\u043a\u0438 " + selected.id + " \u0432\u043a\u043b\u044e\u0447\u0435\u043d\u043e. \u0412\u044b\u043a\u043b\u044e\u0447\u0438\u0442\u044c: /jd build " + selected.id + " off" : "&a\u0420\u0435\u0434\u0430\u043a\u0442\u0438\u0440\u043e\u0432\u0430\u043d\u0438\u0435 \u0432\u044b\u043a\u043b\u044e\u0447\u0435\u043d\u043e. \u041f\u043e\u0441\u0442\u0440\u043e\u0439\u043a\u0430 \u0437\u0430\u0449\u0438\u0449\u0435\u043d\u0430.")));
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
                            throw new IllegalArgumentException("ID \u0443\u0436\u0435 \u0437\u0430\u043d\u044f\u0442 \u0438\u043b\u0438 \u043d\u0435\u043a\u043e\u0440\u0440\u0435\u043a\u0442\u0435\u043d: a-z, 0-9, _, -; \u0434\u043e 40 \u0437\u043d\u0430\u043a\u043e\u0432");
                        }
                        Model.Arena ar2 = new Model.Arena();
                        ar2.id = id;
                        ar2.name = id;
                        ar2.kind = Model.Kind.valueOf(this.arg(a, 2).toUpperCase(Locale.ROOT));
                        String string = ar2.theme = a.length > 3 ? a[3] : "mining";
                        ar2.placement = ar2.theme.equals("flying") ? Model.Placement.AIR : (ar2.theme.equals("water") ? Model.Placement.WATER : Model.Placement.GROUND);
                        this.e.c.saveArena(ar2);
                        sender.sendMessage(Messages.color("&a\u0410\u0440\u0435\u043d\u0430 \u0441\u043e\u0437\u0434\u0430\u043d\u0430. /jd edit " + id));
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
                            ar.worlds = value.equalsIgnoreCase("all") ? new ArrayList<String>() : new ArrayList<String>(Arrays.asList(value.split(",")));
                            for (String world : ar.worlds) {
                                if (Bukkit.getWorld((String)world) != null) continue;
                                throw new IllegalArgumentException("\u041c\u0438\u0440 \u043d\u0435 \u0437\u0430\u0433\u0440\u0443\u0436\u0435\u043d: " + world);
                            }
                        });
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
                            throw new IllegalArgumentException("\u0423\u0434\u0430\u043b\u0435\u043d\u0438\u0435: /jd delete " + id + " confirm");
                        }
                        if (this.e.sessions.containsKey(id)) {
                            throw new IllegalArgumentException("\u0421\u043d\u0430\u0447\u0430\u043b\u0430 /jd stop " + id);
                        }
                        this.e.regions.deleteDefinition(this.arena(id));
                        this.e.c.removeArena(id);
                        this.e.clearBuildingEditors();
                        break;
                    }
                    case "rename": {
                        this.change(this.arg(a, 1), ar -> {
                            ar.name = String.join((CharSequence)" ", Arrays.copyOfRange(a, 2, a.length));
                        });
                        break;
                    }
                    case "theme": {
                        this.change(this.arg(a, 1), ar -> {
                            String theme = this.arg(a, 2);
                            Model.Theme chosen = this.e.c.themes.get(theme);
                            if (chosen == null) {
                                throw new IllegalArgumentException("\u041d\u0435\u0442 \u0442\u0435\u043c\u044b " + theme);
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
                            throw new IllegalArgumentException(String.join((CharSequence)"; ", issues));
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
                        if (b == null || !(b.getBlockData() instanceof Chest)) {
                            throw new IllegalArgumentException("\u041f\u043e\u0441\u043c\u043e\u0442\u0440\u0438\u0442\u0435 \u043d\u0430 \u043e\u0434\u0438\u043d\u043e\u0447\u043d\u044b\u0439 \u0441\u0443\u043d\u0434\u0443\u043a \u0432 \u043f\u0440\u0435\u0434\u0435\u043b\u0430\u0445 6 \u0431\u043b\u043e\u043a\u043e\u0432");
                        }
                        this.change(this.arg(a, 1), ar -> {
                            ar.chest = Model.Point.of(b.getLocation());
                        });
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
                            throw new IllegalArgumentException("\u0421\u043d\u0430\u0447\u0430\u043b\u0430 \u043e\u0441\u0442\u0430\u043d\u043e\u0432\u0438\u0442\u0435 \u0441\u043e\u0431\u044b\u0442\u0438\u0435");
                        }
                        Model.Arena saved = this.arena(id);
                        this.e.structures.capture(saved);
                        this.e.regions.sync(saved);
                        sender.sendMessage(Messages.color("&a\u0428\u0430\u0431\u043b\u043e\u043d \u0441\u043e\u0445\u0440\u0430\u043d\u0451\u043d: templates/" + id + ".schem. \u0421\u0443\u043d\u0434\u0443\u043a \u0438 \u0442\u043e\u0447\u043a\u0438 \u043c\u043e\u0431\u043e\u0432 \u043f\u0440\u0438\u0432\u044f\u0437\u0430\u043d\u044b \u043a \u043f\u043e\u0441\u0442\u0440\u043e\u0439\u043a\u0435. &7\u0418\u0441\u0445\u043e\u0434\u043d\u0430\u044f \u043f\u043e\u0441\u0442\u0440\u043e\u0439\u043a\u0430 \u043e\u0441\u0442\u0430\u0451\u0442\u0441\u044f \u0434\u043b\u044f \u0440\u0435\u0434\u0430\u043a\u0442\u0438\u0440\u043e\u0432\u0430\u043d\u0438\u044f. \u0420\u0430\u0437\u043c\u0435\u0449\u0435\u043d\u0438\u0435: " + saved.placement.label + ". /jd spawn " + id));
                        break;
                    }
                    case "spawn": 
                    case "test": {
                        if (a.length < 2) {
                            throw new IllegalArgumentException("/jd " + sub + " <ID \u0430\u0440\u0435\u043d\u044b> [site]. \u0414\u043e\u0441\u0442\u0443\u043f\u043d\u044b\u0435 ID: " + String.join((CharSequence)", ", this.e.c.arenas.keySet()));
                        }
                        this.spawn(sender, a[1], a.length > 2 ? a[2] : null, sub.equals("test"));
                        break;
                    }
                    case "stop": {
                        this.e.stop(this.arg(a, 1), sender.getName());
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
                            throw new IllegalArgumentException("pve \u0438\u043b\u0438 pvp");
                        }
                        if (type.equals("pve") && n % 5 != 0) {
                            throw new IllegalArgumentException("PvE \u0434\u043e\u043b\u0436\u043d\u043e \u0434\u0435\u043b\u0438\u0442\u044c\u0441\u044f \u043d\u0430 5");
                        }
                        if (type.equals("pve") && n != 900) {
                            throw new IllegalArgumentException("PvE \u0444\u0438\u043a\u0441\u0438\u0440\u043e\u0432\u0430\u043d: 900 \u0441\u0435\u043a\u0443\u043d\u0434, \u043f\u044f\u0442\u044c \u0432\u043e\u043b\u043d \u043f\u043e 180 \u0441\u0435\u043a\u0443\u043d\u0434");
                        }
                        this.e.c.edit("config", y -> {
                            y.set("timers." + type + "-seconds", (Object)n);
                            if (type.equals("pve")) {
                                y.set("timers.wave-interval-seconds", (Object)(n / 5));
                            }
                        });
                        break;
                    }
                    case "case": {
                        if (this.arg(a, 1).equals("list")) {
                            sender.sendMessage(String.join((CharSequence)", ", this.e.hooks.caseIds()));
                            break;
                        }
                        if (!this.arg(a, 1).equals("map") || !List.of("donation", "epic", "resource").contains(this.arg(a, 2))) {
                            throw new IllegalArgumentException("/jd case map donation|epic|resource <\u0441\u0443\u0449\u0435\u0441\u0442\u0432\u0443\u044e\u0449\u0438\u0439 ID>");
                        }
                        String id = this.arg(a, 3);
                        if (!this.e.hooks.caseIds().contains(id)) {
                            throw new IllegalArgumentException("\u041d\u0435\u0442 PhoenixCrates ID " + id);
                        }
                        this.e.c.edit("config", y -> y.set("integrations.cases." + a[2], (Object)id));
                        break;
                    }
                    case "config": {
                        if (!this.arg(a, 1).equals("set")) {
                            throw new IllegalArgumentException("/jd config set config|themes|rewards|messages <path> <YAML \u0437\u043d\u0430\u0447\u0435\u043d\u0438\u0435>");
                        }
                        String file = this.arg(a, 2);
                        String path = this.arg(a, 3);
                        if (file.equals("arenas")) {
                            throw new IllegalArgumentException("\u0414\u043b\u044f \u0430\u0440\u0435\u043d \u0438\u0441\u043f\u043e\u043b\u044c\u0437\u0443\u0439\u0442\u0435 /jd edit");
                        }
                        String value = String.join((CharSequence)" ", Arrays.copyOfRange(a, 4, a.length));
                        YamlConfiguration parsed = new YamlConfiguration();
                        parsed.loadFromString("value: " + value);
                        Object v = Configs.plain(parsed.get("value"));
                        if (this.e.c.file(file).get(path) == null) {
                            throw new IllegalArgumentException("\u041d\u0435\u0442 \u0442\u0430\u043a\u043e\u0433\u043e \u0441\u0443\u0449\u0435\u0441\u0442\u0432\u0443\u044e\u0449\u0435\u0433\u043e \u043f\u0430\u0440\u0430\u043c\u0435\u0442\u0440\u0430");
                        }
                        this.e.c.edit(file, y -> y.set(path, v));
                        break;
                    }
                    case "reload": {
                        this.e.c.reload();
                        this.e.clearBuildingEditors();
                        this.e.regions.refreshDefinitions();
                        for (Model.Session session : this.e.sessions.values()) {
                            this.e.regions.create(session);
                        }
                        sender.sendMessage(Messages.color("&a\u041d\u0430\u0441\u0442\u0440\u043e\u0439\u043a\u0438 \u043f\u0435\u0440\u0435\u0447\u0438\u0442\u0430\u043d\u044b. \u0410\u043a\u0442\u0438\u0432\u043d\u044b\u0435 \u0441\u043e\u0431\u044b\u0442\u0438\u044f \u0438\u0441\u043f\u043e\u043b\u044c\u0437\u0443\u044e\u0442 \u0441\u043e\u0445\u0440\u0430\u043d\u0451\u043d\u043d\u044b\u0435 \u043f\u0430\u0440\u0430\u043c\u0435\u0442\u0440\u044b."));
                        break;
                    }
                    case "validate": {
                        Collection<Model.Arena> arenas = a.length > 1 ? List.of(this.arena(a[1])) : this.e.c.arenas.values();
                        for (Model.Arena ar4 : arenas) {
                            List<String> issues = this.e.validate(ar4, true);
                            sender.sendMessage(ar4.id + ": " + (issues.isEmpty() ? "OK" : String.join((CharSequence)"; ", issues)));
                        }
                        break;
                    }
                    case "status": {
                        for (String line : this.e.hooks.status()) {
                            sender.sendMessage(line);
                        }
                        sender.sendMessage(this.e.structures.status());
                        for (Model.Session s : this.e.sessions.values()) {
                            sender.sendMessage(s.arenaId + " " + String.valueOf((Object)s.kind) + " " + String.valueOf((Object)s.phase) + " event=" + s.id + " owner=" + s.owner + " wave=" + s.wave + " remaining=" + Rules.remaining(System.currentTimeMillis(), s.endAt));
                        }
                        sender.sendMessage("\u0420\u0430\u0441\u043f\u0438\u0441\u0430\u043d\u0438\u0435: " + this.e.c.main().getBoolean("scheduling.enabled"));
                        break;
                    }
                    case "audit": {
                        this.e.db.audit(a.length > 1 ? Integer.parseInt(a[1]) : 15).forEach(arg_0 -> ((CommandSender)sender).sendMessage(arg_0));
                        break;
                    }
                    case "review": {
                        if (a.length < 4) {
                            for (Model.Reward r : this.e.db.rewards(null)) {
                                if (r.coinState != Model.Delivery.REVIEW && r.caseState != Model.Delivery.REVIEW && !r.items.stream().anyMatch(i -> i.state == Model.Delivery.REVIEW)) continue;
                                sender.sendMessage(r.id + " owner=" + r.owner + " coins=" + String.valueOf((Object)r.coinState) + " case=" + String.valueOf((Object)r.caseState) + " items=" + String.valueOf(r.items.stream().map(i -> i.state.toString()).toList()));
                            }
                            break;
                        }
                        String action = this.arg(a, 3);
                        if (!List.of("delivered", "retry").contains(action)) {
                            throw new IllegalArgumentException("delivered \u0438\u043b\u0438 retry");
                        }
                        this.e.rewards.reconcile(a[1], a[2], action.equals("delivered"), sender.getName());
                        break;
                    }
                    default: {
                        throw new IllegalArgumentException("\u041d\u0435\u0438\u0437\u0432\u0435\u0441\u0442\u043d\u0430\u044f \u043a\u043e\u043c\u0430\u043d\u0434\u0430. /jd help");
                    }
                }
                if (!List.of("menu", "edit", "status", "validate", "audit", "review", "spawn", "test", "launch").contains(sub)) {
                    this.e.db.audit(sender.getName(), "ADMIN_COMMAND", sub.equals("config") && a.length > 3 && a[3].startsWith("database.") ? "config database.[hidden]" : String.join((CharSequence)" ", a));
                    sender.sendMessage(Messages.color("&a\u0413\u043e\u0442\u043e\u0432\u043e."));
                }
            }
            catch (Exception ex) {
                this.e.msg.error(sender, Objects.toString(ex.getMessage(), ex.getClass().getSimpleName()));
                if (!sub.equals("schematic") && !sub.equals("template") || ex instanceof IllegalArgumentException) break block109;
                this.e.report("\u041e\u0448\u0438\u0431\u043a\u0430 WorldEdit \u043f\u0440\u0438 \u0440\u0430\u0431\u043e\u0442\u0435 \u0441\u043e \u0441\u0445\u0435\u043c\u043e\u0439", ex);
            }
        }
        return true;
    }

    private void spawn(CommandSender sender, String id, String site, boolean test) {
        Model.Arena definition;
        if (test) {
            sender.sendMessage(Messages.color("&e\u0422\u0415\u0421\u0422: \u0432\u043e\u043b\u043d\u044b \u0438 \u0431\u043e\u0439 \u0440\u0430\u0431\u043e\u0442\u0430\u044e\u0442, \u0432\u044b\u0434\u0430\u0447\u0430 \u043d\u0430\u0433\u0440\u0430\u0434 \u043e\u0442\u043a\u043b\u044e\u0447\u0435\u043d\u0430. \u0414\u043b\u044f \u043d\u0430\u0441\u0442\u043e\u044f\u0449\u0435\u0433\u043e \u0441\u043e\u0431\u044b\u0442\u0438\u044f: /jd stop " + id + ", \u0437\u0430\u0442\u0435\u043c /jd spawn " + id + "."));
        }
        sender.sendMessage(Messages.color((definition = this.e.c.arenas.get(id)) != null && PlacementService.automatic(definition, site) ? "&e\u0418\u0449\u0443 \u0441\u043b\u0443\u0447\u0430\u0439\u043d\u043e\u0435 \u043c\u0435\u0441\u0442\u043e \u0434\u043b\u044f " + id + " (\u044d\u0442\u043e \u043d\u0435 \u0432\u0441\u0442\u0430\u0432\u043a\u0430 \u0432 \u0442\u043e\u0447\u043a\u0435 A)\u2026" : "&e\u041f\u043e\u0434\u0433\u043e\u0442\u0430\u0432\u043b\u0438\u0432\u0430\u044e \u0430\u0440\u0435\u043d\u0443 " + id + "\u2026"));
        this.e.placement.spawn(id, site, test, sender.getName()).whenComplete((session, error) -> {
            if (error != null) {
                this.e.msg.error(sender, Objects.toString(error.getMessage(), "\u041e\u0448\u0438\u0431\u043a\u0430 \u0440\u0430\u0437\u043c\u0435\u0449\u0435\u043d\u0438\u044f"));
            } else {
                sender.sendMessage(Messages.color("&a\u0410\u0440\u0435\u043d\u0430 " + id + " \u0440\u0430\u0437\u043c\u0435\u0449\u0435\u043d\u0430: " + String.valueOf((Object)session.kind) + " / " + session.arena.box().world() + " " + (int)session.arena.chest.x() + ", " + (int)session.arena.chest.y() + ", " + (int)session.arena.chest.z()));
            }
        });
    }

    private void schematic(CommandSender sender, String[] args) throws Exception {
        String action = this.arg(args, 1);
        if (action.equals("list")) {
            this.e.structures.schematics().forEach(arg_0 -> ((CommandSender)sender).sendMessage(arg_0));
            return;
        }
        Model.Arena arena = this.arena(this.arg(args, 2));
        Model.Arena before = Json.copy(arena, Model.Arena.class);
        if (this.e.sessions.containsKey(arena.id)) {
            throw new IllegalArgumentException("\u0421\u043d\u0430\u0447\u0430\u043b\u0430 \u043e\u0441\u0442\u0430\u043d\u043e\u0432\u0438\u0442\u0435 \u0441\u043e\u0431\u044b\u0442\u0438\u0435");
        }
        if (action.equals("use")) {
            Model.Point origin;
            String file = String.join((CharSequence)" ", Arrays.copyOfRange(args, 3, args.length));
            Model.Point exit = PlacementService.exitNear((arena = this.e.structures.bind(arena, file, origin = Model.Point.of(this.player(sender).getLocation().getBlock().getLocation()))).box());
            if (exit != null) {
                arena.exit = exit;
            }
            this.saveSchematic(before, arena);
            sender.sendMessage(Messages.color("&a\u0421\u0445\u0435\u043c\u0430 \u0432\u044b\u0431\u0440\u0430\u043d\u0430, A/B \u0437\u0430\u0434\u0430\u043d\u044b \u043f\u043e \u0440\u0430\u0437\u043c\u0435\u0440\u0430\u043c. \u0414\u043b\u044f \u0432\u0441\u0442\u0430\u0432\u043a\u0438: /jd schematic paste " + arena.id + ". \u0417\u0430\u0442\u0435\u043c \u043d\u0430\u0441\u0442\u0440\u043e\u0439\u0442\u0435 \u0441\u0443\u043d\u0434\u0443\u043a, \u0432\u044b\u0445\u043e\u0434 \u0438 \u0442\u043e\u0447\u043a\u0438 \u043c\u043e\u0431\u043e\u0432."));
            this.schematicBounds(sender, arena);
        } else if (action.equals("paste") || action.equals("fit")) {
            SchematicCodec.Image image = this.e.structures.fit(arena);
            this.saveSchematic(before, arena);
            if (action.equals("paste")) {
                this.e.structures.paste(arena, image);
                sender.sendMessage(Messages.color("&a\u0421\u0445\u0435\u043c\u0430 \u0432\u0441\u0442\u0430\u0432\u043b\u0435\u043d\u0430 \u0447\u0435\u0440\u0435\u0437 WorldEdit/FAWE. \u0413\u0440\u0430\u043d\u0438\u0446\u044b \u0438 \u043f\u0440\u0438\u0432\u0430\u0442 \u043e\u0431\u043d\u043e\u0432\u043b\u0435\u043d\u044b. \u041c\u043e\u0431\u044b \u0438 PvP \u043d\u0435 \u0437\u0430\u043f\u0443\u0449\u0435\u043d\u044b."));
            } else {
                sender.sendMessage(Messages.color("&a\u0413\u0440\u0430\u043d\u0438\u0446\u044b \u0438 \u043f\u0440\u0438\u0432\u0430\u0442 \u043f\u0435\u0440\u0435\u0441\u0447\u0438\u0442\u0430\u043d\u044b \u043f\u043e \u0444\u0430\u0439\u043b\u0443 \u0441\u0445\u0435\u043c\u0430\u0442\u0438\u043a\u0438."));
            }
            this.schematicBounds(sender, arena);
        } else {
            throw new IllegalArgumentException("/jd schematic list|use <arena> <file>|paste|fit <arena>");
        }
    }

    private void saveSchematic(Model.Arena previous, Model.Arena arena) throws Exception {
        this.e.regions.sync(arena);
        try {
            this.e.c.saveArena(arena);
        }
        catch (Exception error) {
            try {
                this.e.regions.deleteDefinition(arena);
                if (previous.pos1 != null && previous.pos2 != null) {
                    this.e.regions.repair(previous);
                }
            }
            catch (Exception rollback) {
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
        sender.sendMessage(Messages.color("&a\u0421\u0445\u0435\u043c\u0430\u0442\u0438\u043a\u0430: " + (b.x2() - b.x1() + 1) + "\u00d7" + (b.y2() - b.y1() + 1) + "\u00d7" + (b.z2() - b.z1() + 1) + "; \u043f\u0440\u0438\u0432\u0430\u0442 +" + this.e.c.main().getInt("schematics.protection-padding", 4) + " \u0441\u043e \u0432\u0441\u0435\u0445 \u0441\u0442\u043e\u0440\u043e\u043d: [" + region.x1() + ", " + region.y1() + ", " + region.z1() + "] \u2014 [" + region.x2() + ", " + region.y2() + ", " + region.z2() + "]"));
    }

    private void point(CommandSender sender, String[] a) {
        String op = this.arg(a, 1);
        String id = this.arg(a, 2);
        Model.Arena ar = this.arena(id);
        if (op.equals("list")) {
            for (Model.Spawn point2 : ar.points) {
                sender.sendMessage(point2.id + " wave=" + point2.wave + " mob=" + point2.mob + " " + String.valueOf(point2.point));
            }
            return;
        }
        if (op.equals("show")) {
            Player pl = this.player(sender);
            for (Model.Spawn point3 : ar.points) {
                Location loc = point3.point.location();
                if (loc == null || !loc.getWorld().equals((Object)pl.getWorld())) continue;
                pl.spawnParticle(Particle.HAPPY_VILLAGER, loc, 12, 0.3, 0.5, 0.3);
            }
            return;
        }
        if (op.equals("clear")) {
            if (!this.arg(a, 3).equals("confirm")) {
                throw new IllegalArgumentException("\u0414\u043b\u044f \u043e\u0447\u0438\u0441\u0442\u043a\u0438 \u0434\u043e\u0431\u0430\u0432\u044c\u0442\u0435 confirm");
            }
            ar.points.clear();
        } else if (op.equals("add")) {
            String pointId;
            int wave = a.length > 3 ? Integer.parseInt(a[3]) : 0;
            String mob = a.length > 4 ? a[4] : "any";
            String string = pointId = a.length > 5 ? a[5] : "p" + (ar.points.stream().map(x -> x.id).toList().hashCode() & Integer.MAX_VALUE) + "_" + ar.points.size();
            if (wave < 0 || wave > 5 || !mob.equals("any") && !this.e.c.themes.get((Object)ar.theme).mobs.containsKey(mob) || !Rules.validId(pointId)) {
                throw new IllegalArgumentException("\u0412\u043e\u043b\u043d\u0430 0..5, \u043c\u043e\u0431 any \u0438\u043b\u0438 \u0441\u0443\u0449\u0435\u0441\u0442\u0432\u0443\u044e\u0449\u0438\u0439 preset, \u043a\u043e\u0440\u0440\u0435\u043a\u0442\u043d\u044b\u0439 ID \u0442\u043e\u0447\u043a\u0438");
            }
            if (ar.points.stream().anyMatch(pt -> pt.id.equals(pointId))) {
                throw new IllegalArgumentException("ID \u0442\u043e\u0447\u043a\u0438 \u0437\u0430\u043d\u044f\u0442");
            }
            ar.points.add(new Model.Spawn(pointId, Model.Point.of(this.player(sender).getLocation()), wave, mob));
        } else {
            Model.Spawn pt2 = ar.points.stream().filter(point -> point.id.equals(this.arg(a, 3))).findFirst().orElseThrow(() -> new IllegalArgumentException("\u041d\u0435\u0442 \u0442\u043e\u0447\u043a\u0438"));
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
            ar.sites.forEach((name, point) -> sender.sendMessage(name + " " + String.valueOf(point)));
            return;
        }
        String name2 = this.arg(a, 3);
        if (!Rules.validId(name2)) {
            throw new IllegalArgumentException("\u041d\u0435\u043a\u043e\u0440\u0440\u0435\u043a\u0442\u043d\u043e\u0435 \u0438\u043c\u044f \u043f\u043b\u043e\u0449\u0430\u0434\u043a\u0438");
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
            throw new IllegalArgumentException("\u0412\u043e\u043b\u043d\u0430 1..5, \u043a\u043e\u043b\u0438\u0447\u0435\u0441\u0442\u0432\u043e 0..256");
        }
        Model.Theme t = this.e.c.themes.get(theme);
        if (t == null || !t.mobs.containsKey(mob)) {
            throw new IllegalArgumentException("\u041d\u0435\u0442 \u0442\u0435\u043c\u044b/\u043c\u043e\u0431\u0430");
        }
        this.e.c.edit("themes", y -> {
            ArrayList rows = new ArrayList();
            for (Map row : y.getMapList("themes." + theme + ".waves")) {
                LinkedHashMap copy = new LinkedHashMap();
                row.forEach((k, v) -> copy.put(k.toString(), v));
                rows.add(copy);
            }
            if (count == 0) {
                ((Map)rows.get(wave - 1)).remove(mob);
            } else {
                ((Map)rows.get(wave - 1)).put(mob, count);
            }
            y.set("themes." + theme + ".waves", rows);
        });
    }

    private void wave(String[] a) {
        if (this.arg(a, 1).equals("reset")) {
            if (a.length < 4 || !a[3].equals("confirm")) {
                throw new IllegalArgumentException("\u0417\u0430\u043c\u0435\u043d\u0438\u0442\u044c \u0432\u0441\u0435 5 \u0432\u043e\u043b\u043d \u0441\u0442\u0430\u043d\u0434\u0430\u0440\u0442\u043d\u044b\u043c\u0438: /jd wave reset " + this.arg(a, 2) + " confirm");
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
            throw new IllegalArgumentException("\u041d\u0435\u0442 \u0442\u0435\u043c\u044b " + theme);
        }
        List<Map<?, ?>> rows = this.e.c.standardWaves(theme);
        this.e.c.edit("themes", yaml -> yaml.set("themes." + theme + ".waves", (Object)rows));
    }

    public static boolean parseBool(String value) {
        if (List.of("on", "true", "1").contains(value.toLowerCase(Locale.ROOT))) {
            return true;
        }
        if (List.of("off", "false", "0").contains(value.toLowerCase(Locale.ROOT))) {
            return false;
        }
        throw new IllegalArgumentException("\u0423\u043a\u0430\u0436\u0438\u0442\u0435 on \u0438\u043b\u0438 off");
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
        sender.sendMessage("JamDungeons " + this.e.version() + ": \u0432\u0441\u0442\u0440\u043e\u0435\u043d\u043d\u0430\u044f \u0437\u0430\u0449\u0438\u0442\u0430 \u0432\u043a\u043b\u044e\u0447\u0435\u043d\u0430; OP/Creative \u0441\u0430\u043c\u0438 \u0435\u0451 \u043d\u0435 \u043e\u0431\u0445\u043e\u0434\u044f\u0442");
        this.describeProtection(sender, "\u0418\u0441\u0445\u043e\u0434\u043d\u0430\u044f \u043f\u043e\u0441\u0442\u0440\u043e\u0439\u043a\u0430", WorldGuardRegions.definitionId(source.id), source);
        if (session != null) {
            this.describeProtection(sender, "\u0421\u043e\u0431\u044b\u0442\u0438\u0435 " + String.valueOf((Object)session.phase), WorldGuardRegions.eventId(session), session.arena);
        }
        if (sender instanceof Player) {
            Player player = (Player)sender;
            sender.sendMessage("\u0412\u0430\u0448 \u0431\u043b\u043e\u043a: " + player.getWorld().getName() + " " + player.getLocation().getBlockX() + " " + player.getLocation().getBlockY() + " " + player.getLocation().getBlockZ() + "; \u0437\u0430\u0449\u0438\u0449\u0451\u043d=" + this.e.buildProtected(player.getLocation()) + "; \u044f\u0432\u043d\u043e\u0435 \u0440\u0435\u0434\u0430\u043a\u0442\u0438\u0440\u043e\u0432\u0430\u043d\u0438\u0435=" + this.e.editingBuilding(player, source.id));
            Block target = player.getTargetBlockExact(8);
            if (target != null) {
                sender.sendMessage("\u0411\u043b\u043e\u043a \u043f\u043e\u0434 \u043f\u0440\u0438\u0446\u0435\u043b\u043e\u043c: " + target.getX() + " " + target.getY() + " " + target.getZ() + "; \u0437\u0430\u0449\u0438\u0449\u0451\u043d=" + this.e.buildProtected(target.getLocation()));
            }
        }
    }

    private void describeProtection(CommandSender sender, String title, String regionId, Model.Arena arena) {
        Model.Box box = this.e.c.protectionBox(arena);
        sender.sendMessage(title + ": " + box.world() + " [" + box.x1() + ", " + box.y1() + ", " + box.z1() + "] \u2014 [" + box.x2() + ", " + box.y2() + ", " + box.z2() + "]");
        sender.sendMessage(this.e.regions.status(regionId, arena));
    }

    private void help(CommandSender s) {
        s.sendMessage(Messages.color("&aJamDungeons &f/jd list &7| &f/jd rewards"));
        if (!s.hasPermission("jamdungeons.admin")) {
            return;
        }
        for (String line : List.of("/jd \u2014 \u043c\u0435\u043d\u044e; /jd edit <id>; /jd create <id> pve|pvp [theme]", "/jd protection <id> [repair] \u2014 \u0433\u0440\u0430\u043d\u0438\u0446\u044b \u0438 \u0440\u0435\u0430\u043b\u044c\u043d\u044b\u0439 \u043f\u0440\u0438\u0432\u0430\u0442 WorldGuard", "/jd build <id> on|off \u2014 \u044f\u0432\u043d\u043e\u0435 \u0440\u0435\u0434\u0430\u043a\u0442\u0438\u0440\u043e\u0432\u0430\u043d\u0438\u0435 \u0438\u0441\u0445\u043e\u0434\u043d\u043e\u0439 \u043f\u043e\u0441\u0442\u0440\u043e\u0439\u043a\u0438 \u0432 Creative", "/jd pos1|pos2|chest|exit <id>; /jd enable <id> on|off", "/jd point add <id> [wave:0..5] [mob:any] [point-id]", "/jd point list|show <id>; /jd point move|remove|tp <id> <point-id>", "/jd spawn|test <id> [random|static|site]; /jd stop <id>", "/jd template save <id>; /jd site add|remove <id> <name>", "/jd schematic list; /jd schematic use <id> <file>; /jd schematic paste|fit <id>", "/jd type <id> pve|pvp; /jd placement <id> static|ground|air|water|underwater", "/jd worlds <id> all|world,world2; /jd launch all|pve|pvp", "/jd voucher review <id> delivered|retry", "/jd wave set <theme> <wave> <mob> <count>; /jd timer pve|pvp <seconds>", "/jd wave reset <theme> confirm \u2014 \u0441\u0442\u0430\u043d\u0434\u0430\u0440\u0442\u043d\u044b\u0439 \u0440\u043e\u0441\u0442 \u0432\u043e\u043b\u043d; /jd combat on|off \u2014 \u043f\u0440\u043e\u0432\u0435\u0440\u043a\u0430 \u0443\u0440\u043e\u043d\u0430", "/jd case list; /jd case map donation|epic|resource <PhoenixCrates-ID>", "/jd config set <file> <path> <YAML-value>; /jd reload; /jd validate [id]", "/jd status; /jd audit [count]; /jd review [reward-id coins|case|item-number delivered|retry]", "/jd delete <id> confirm")) {
            s.sendMessage(line);
        }
    }

    public List<String> onTabComplete(CommandSender s, Command command, String alias, String[] a) {
        ArrayList<String> options = new ArrayList<String>();
        if (a.length == 1) {
            options.addAll(List.of("help", "list", "rewards"));
            if (s.hasPermission("jamdungeons.admin")) {
                options.addAll(List.of("menu", "protection", "build", "schematic", "type", "placement", "worlds", "launch", "voucher", "create", "edit", "delete", "rename", "theme", "enable", "pos1", "pos2", "chest", "exit", "point", "site", "template", "spawn", "test", "stop", "wave", "combat", "timer", "case", "config", "reload", "validate", "status", "audit", "review"));
            }
        } else if (s.hasPermission("jamdungeons.admin")) {
            String sub = a[0];
            if (List.of("edit", "protection", "build", "delete", "rename", "theme", "enable", "pos1", "pos2", "chest", "exit", "spawn", "test", "stop", "validate").contains(sub) && a.length == 2 || List.of("point", "site", "template").contains(sub) && a.length == 3) {
                options.addAll(this.e.c.arenas.keySet());
            }
            if (sub.equals("point") && a.length == 2) {
                options.addAll(List.of("add", "list", "show", "move", "remove", "tp", "clear"));
            }
            if (sub.equals("combat") && a.length == 2) {
                options.addAll(List.of("on", "off"));
            }
            if (sub.equals("build") && a.length == 3) {
                options.addAll(List.of("on", "off"));
            }
            if (sub.equals("protection") && a.length == 3) {
                options.add("repair");
            }
            if (sub.equals("wave") && a.length == 2) {
                options.addAll(List.of("set", "reset"));
            }
            if (sub.equals("wave") && a.length == 3) {
                options.addAll(this.e.c.themes.keySet());
            }
            if (sub.equals("wave") && a.length == 4 && a[1].equals("reset")) {
                options.add("confirm");
            }
            if (sub.equals("site") && a.length == 2) {
                options.addAll(List.of("add", "remove", "list"));
            }
            if (sub.equals("template") && a.length == 2) {
                options.add("save");
            }
            if (sub.equals("timer") && a.length == 2) {
                options.addAll(List.of("pve", "pvp"));
            }
            if (sub.equals("case")) {
                if (a.length == 2) {
                    options.addAll(List.of("list", "map"));
                }
                if (a.length == 3) {
                    options.addAll(List.of("donation", "epic", "resource"));
                }
                if (a.length == 4) {
                    try {
                        options.addAll(this.e.hooks.caseIds());
                    }
                    catch (Exception exception) {
                        // empty catch block
                    }
                }
            }
            if (sub.equals("theme") && a.length == 3) {
                options.addAll(this.e.c.themes.keySet());
            }
            if (sub.equals("point") && a.length == 5) {
                options.add("any");
                Model.Arena selected = this.e.c.arenas.get(a[2]);
                if (selected != null && this.e.c.themes.containsKey(selected.theme)) {
                    options.addAll(this.e.c.themes.get((Object)selected.theme).mobs.keySet());
                }
            }
        }
        String prefix = a[a.length - 1].toLowerCase(Locale.ROOT);
        if (s.hasPermission("jamdungeons.admin")) {
            if (a[0].equals("schematic")) {
                if (a.length == 2) {
                    options.addAll(List.of("list", "use", "paste", "fit"));
                }
                if (a.length == 3) {
                    options.addAll(this.e.c.arenas.keySet());
                }
                if (a.length == 4 && a[1].equals("use")) {
                    try {
                        options.addAll(this.e.structures.schematics());
                    }
                    catch (Exception exception) {
                        // empty catch block
                    }
                }
            }
            if (List.of("type", "placement", "worlds").contains(a[0]) && a.length == 2) {
                options.addAll(this.e.c.arenas.keySet());
            }
            if (a[0].equals("placement") && a.length == 3) {
                options.addAll(List.of("static", "ground", "air", "water", "underwater"));
            }
            if (a[0].equals("type") && a.length == 3) {
                options.addAll(List.of("pve", "pvp"));
            }
            if (a[0].equals("launch") && a.length == 2) {
                options.addAll(List.of("all", "pve", "pvp"));
            }
        }
        return options.stream().filter(x -> x.toLowerCase(Locale.ROOT).startsWith(prefix)).sorted().toList();
    }
}

