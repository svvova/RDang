/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.Bukkit
 *  org.bukkit.GameMode
 *  org.bukkit.OfflinePlayer
 *  org.bukkit.command.CommandSender
 *  org.bukkit.configuration.ConfigurationSection
 *  org.bukkit.configuration.file.YamlConfiguration
 *  org.bukkit.entity.Player
 *  org.bukkit.plugin.Plugin
 *  org.bukkit.plugin.RegisteredServiceProvider
 */
package org.jamhardcore.dungeons;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.jamhardcore.dungeons.Configs;
import org.jamhardcore.dungeons.api.CoinEconomyProvider;

public final class Integrations {
    private final Configs c;

    public Integrations(Configs c) {
        this.c = c;
    }

    private Plugin plugin(String name) {
        Plugin p = Bukkit.getPluginManager().getPlugin(name);
        return p != null && p.isEnabled() ? p : null;
    }

    public boolean canPlay(Player p) {
        Plugin papi;
        if (p.isDead() || p.getGameMode() != GameMode.SURVIVAL && p.getGameMode() != GameMode.ADVENTURE) {
            return false;
        }
        if (!this.c.main().getBoolean("integrations.lives.enabled")) {
            return true;
        }
        Plugin core = this.plugin("JamCore");
        if (core != null) {
            try {
                Object api = core.getClass().getMethod("getHardcoreAccessApi", new Class[0]).invoke((Object)core, new Object[0]);
                Class<?> contract = Class.forName("org.blood.jamCore.api.hardcore.HardcoreAccessApi", true, core.getClass().getClassLoader());
                Object provider = contract.getMethod("getCurrentProvider", new Class[0]).invoke(api, new Object[0]);
                if (!provider.getClass().getSimpleName().equals("DefaultHardcoreAccessProvider")) {
                    return (Boolean)contract.getMethod("hasAccess", Player.class).invoke(api, p);
                }
            }
            catch (ReflectiveOperationException api) {
                // empty catch block
            }
        }
        if ((papi = this.plugin("PlaceholderAPI")) != null) {
            try {
                Class<?> api = Class.forName("me.clip.placeholderapi.PlaceholderAPI", true, papi.getClass().getClassLoader());
                String value = (String)api.getMethod("setPlaceholders", OfflinePlayer.class, String.class).invoke(null, p, this.c.main().getString("integrations.lives.placeholder"));
                return Integer.parseInt(value.strip()) > 0;
            }
            catch (NumberFormatException | ReflectiveOperationException exception) {
                // empty catch block
            }
        }
        return !this.c.main().getBoolean("integrations.lives.deny-unresolved", true);
    }

    private Object[] economy() throws Exception {
        Plugin vault = this.plugin("Vault");
        if (vault == null) {
            throw new IllegalStateException("\u0422\u0440\u0435\u0431\u0443\u0435\u0442\u0441\u044f Vault \u0438 \u043f\u0440\u043e\u0432\u0430\u0439\u0434\u0435\u0440 \u044d\u043a\u043e\u043d\u043e\u043c\u0438\u043a\u0438");
        }
        Class<?> type = Class.forName("net.milkbowl.vault.economy.Economy", true, vault.getClass().getClassLoader());
        RegisteredServiceProvider r = Bukkit.getServicesManager().getRegistration(type);
        if (r == null) {
            throw new IllegalStateException("\u0412 Vault \u043d\u0435 \u0437\u0430\u0440\u0435\u0433\u0438\u0441\u0442\u0440\u0438\u0440\u043e\u0432\u0430\u043d\u0430 \u044d\u043a\u043e\u043d\u043e\u043c\u0438\u043a\u0430");
        }
        return new Object[]{type, r.getProvider()};
    }

    public void checkEconomy() {
        try {
            switch (this.c.main().getString("integrations.economy.mode", "VAULT")) {
                case "SERVICE": {
                    if (Bukkit.getServicesManager().load(CoinEconomyProvider.class) == null) {
                        throw new IllegalArgumentException("\u041d\u0435 \u0437\u0430\u0440\u0435\u0433\u0438\u0441\u0442\u0440\u0438\u0440\u043e\u0432\u0430\u043d CoinEconomyProvider");
                    }
                    break;
                }
                case "COMMAND": {
                    String provider = this.c.main().getString("integrations.economy.provider-plugin", "");
                    if (provider.isBlank() || this.plugin(provider) == null) {
                        throw new IllegalArgumentException("\u0423\u043a\u0430\u0436\u0438\u0442\u0435 \u0443\u0441\u0442\u0430\u043d\u043e\u0432\u043b\u0435\u043d\u043d\u044b\u0439 \u043f\u043b\u0430\u0433\u0438\u043d \u043c\u043e\u043d\u0435\u0442 \u0432 integrations.economy.provider-plugin");
                    }
                    Integrations.commandTemplate(this.c.main().getString("integrations.economy.command", ""), List.of("{amount}"));
                    break;
                }
                default: {
                    this.economy();
                    break;
                }
            }
        }
        catch (Exception e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
    }

    public void deposit(UUID who, int amount) throws Exception {
        this.deposit(who, amount, UUID.randomUUID().toString());
    }

    public void deposit(UUID who, int amount, String operation) throws Exception {
        String mode = this.c.main().getString("integrations.economy.mode", "VAULT");
        if (mode.equals("SERVICE")) {
            CoinEconomyProvider provider = (CoinEconomyProvider)Bukkit.getServicesManager().load(CoinEconomyProvider.class);
            if (provider == null || !provider.credit(who, amount, operation)) {
                throw new IllegalStateException("\u041f\u0440\u043e\u0432\u0430\u0439\u0434\u0435\u0440 \u043c\u043e\u043d\u0435\u0442 \u043d\u0435 \u043f\u043e\u0434\u0442\u0432\u0435\u0440\u0434\u0438\u043b \u043d\u0430\u0447\u0438\u0441\u043b\u0435\u043d\u0438\u0435");
            }
            return;
        }
        if (mode.equals("COMMAND")) {
            this.checkEconomy();
            String command = this.c.main().getString("integrations.economy.command").replace("{player}", Integrations.online(who).getName()).replace("{uuid}", who.toString()).replace("{amount}", Integer.toString(amount)).replace("{operation}", operation);
            if (!Bukkit.dispatchCommand((CommandSender)Bukkit.getConsoleSender(), (String)command)) {
                throw new IllegalStateException("\u041a\u043e\u043c\u0430\u043d\u0434\u0430 \u043d\u0430\u0447\u0438\u0441\u043b\u0435\u043d\u0438\u044f \u043c\u043e\u043d\u0435\u0442 \u043d\u0435 \u043f\u0440\u0438\u043d\u044f\u0442\u0430; \u0442\u0440\u0435\u0431\u0443\u0435\u0442\u0441\u044f \u0441\u0432\u0435\u0440\u043a\u0430");
            }
            return;
        }
        Object[] e = this.economy();
        Class api = (Class)e[0];
        Object result = api.getMethod("depositPlayer", OfflinePlayer.class, Double.TYPE).invoke(e[1], Bukkit.getOfflinePlayer((UUID)who), amount);
        if (!Boolean.TRUE.equals(result.getClass().getMethod("transactionSuccess", new Class[0]).invoke(result, new Object[0]))) {
            throw new IllegalStateException("\u042d\u043a\u043e\u043d\u043e\u043c\u0438\u043a\u0430 \u043e\u0442\u043a\u043b\u043e\u043d\u0438\u043b\u0430 \u043d\u0430\u0447\u0438\u0441\u043b\u0435\u043d\u0438\u0435; \u043f\u0440\u043e\u0432\u0435\u0440\u044c\u0442\u0435 \u0431\u0430\u043b\u0430\u043d\u0441 \u043f\u0435\u0440\u0435\u0434 \u043f\u043e\u0432\u0442\u043e\u0440\u043e\u043c");
        }
    }

    private Plugin cases() {
        Plugin p = this.plugin(this.c.main().getString("integrations.cases.plugin", "PhoenixCrates"));
        if (p == null) {
            throw new IllegalArgumentException("PhoenixCrates \u043d\u0435 \u0432\u043a\u043b\u044e\u0447\u0451\u043d");
        }
        return p;
    }

    public Set<String> caseIds() {
        YamlConfiguration y;
        ConfigurationSection section;
        File combined;
        Plugin p = this.cases();
        TreeSet<String> ids = new TreeSet<String>(this.c.main().getStringList("integrations.cases.known-keys"));
        File dir = new File(p.getDataFolder(), "keys");
        File[] files = dir.listFiles((d, n) -> n.endsWith(".yml"));
        if (files != null) {
            for (File file : files) {
                YamlConfiguration y2 = YamlConfiguration.loadConfiguration((File)file);
                ids.add(y2.getString("identifier", file.getName().substring(0, file.getName().length() - 4)));
            }
        }
        if ((combined = new File(p.getDataFolder(), "keys.yml")).isFile() && (section = (y = YamlConfiguration.loadConfiguration((File)combined)).getConfigurationSection("keys")) != null) {
            ids.addAll(section.getKeys(false));
        }
        ids.removeIf(id -> !id.matches("[A-Za-z0-9_-]+"));
        return ids;
    }

    public String mappedCase(String role) {
        String id = this.c.main().getString("integrations.cases." + role, "");
        if (!this.caseIds().contains(id)) {
            throw new IllegalArgumentException("\u0412\u044b\u0431\u0435\u0440\u0438\u0442\u0435 ID \u043a\u043b\u044e\u0447\u0430 PhoenixCrates: /jd case map " + role + " <key-id>. \u0414\u043b\u044f \u0438\u043d\u043e\u0439 \u0441\u0442\u0440\u0443\u043a\u0442\u0443\u0440\u044b \u0444\u0430\u0439\u043b\u043e\u0432 \u0438\u0441\u043f\u043e\u043b\u044c\u0437\u0443\u0439\u0442\u0435 integrations.cases.known-keys");
        }
        return id;
    }

    public void checkCases() {
        for (String role : List.of("donation", "epic", "resource")) {
            this.mappedCase(role);
        }
        Integrations.commandTemplate(this.c.main().getString("integrations.cases.command", ""), List.of("{key}"));
    }

    public void giveCase(UUID who, String id) throws Exception {
        this.cases();
        if (!this.caseIds().contains(id) || !id.matches("[A-Za-z0-9_-]+")) {
            throw new IllegalArgumentException("\u041d\u0435\u0438\u0437\u0432\u0435\u0441\u0442\u043d\u044b\u0439 ID \u043a\u043b\u044e\u0447\u0430 PhoenixCrates " + id);
        }
        String command = this.c.main().getString("integrations.cases.command").replace("{key}", id).replace("{player}", Integrations.online(who).getName()).replace("{uuid}", who.toString());
        boolean ok = Bukkit.dispatchCommand((CommandSender)Bukkit.getConsoleSender(), (String)command);
        if (!ok) {
            throw new IllegalStateException("PhoenixCrates \u043d\u0435 \u043f\u0440\u0438\u043d\u044f\u043b \u043a\u043e\u043c\u0430\u043d\u0434\u0443; \u0442\u0440\u0435\u0431\u0443\u0435\u0442\u0441\u044f \u0441\u0432\u0435\u0440\u043a\u0430");
        }
    }

    private static Player online(UUID who) {
        Player p = Bukkit.getPlayer((UUID)who);
        if (p == null) {
            throw new IllegalStateException("\u0418\u0433\u0440\u043e\u043a \u0434\u043e\u043b\u0436\u0435\u043d \u0431\u044b\u0442\u044c \u0432 \u0441\u0435\u0442\u0438");
        }
        return p;
    }

    private static void commandTemplate(String command, List<String> fields) {
        if (command.isBlank() || command.contains("\n") || command.contains("\r") || command.startsWith("/")) {
            throw new IllegalArgumentException("\u041a\u043e\u043c\u0430\u043d\u0434\u0430 \u0438\u043d\u0442\u0435\u0433\u0440\u0430\u0446\u0438\u0438 \u0434\u043e\u043b\u0436\u043d\u0430 \u0431\u044b\u0442\u044c \u043e\u0434\u043d\u043e\u0439 \u0441\u0442\u0440\u043e\u043a\u043e\u0439 \u0431\u0435\u0437 \u043d\u0430\u0447\u0430\u043b\u044c\u043d\u043e\u0433\u043e /");
        }
        if (!command.contains("{player}") && !command.contains("{uuid}")) {
            throw new IllegalArgumentException("\u0412 \u043a\u043e\u043c\u0430\u043d\u0434\u0435 \u0442\u0440\u0435\u0431\u0443\u0435\u0442\u0441\u044f {player} \u0438\u043b\u0438 {uuid}");
        }
        for (String field : fields) {
            if (command.contains(field)) continue;
            throw new IllegalArgumentException("\u0412 \u043a\u043e\u043c\u0430\u043d\u0434\u0435 \u0442\u0440\u0435\u0431\u0443\u0435\u0442\u0441\u044f " + field);
        }
    }

    public List<String> status() {
        ArrayList<String> out = new ArrayList<String>();
        out.add("\u0416\u0438\u0437\u043d\u0438: " + (this.plugin("JamCore") != null ? "JamCore HardcoreAccess API" : (this.plugin("PlaceholderAPI") != null ? "PlaceholderAPI" : "\u043d\u0435\u0442 \u043f\u0440\u043e\u0432\u0430\u0439\u0434\u0435\u0440\u0430")));
        try {
            this.checkEconomy();
            out.add("\u041c\u043e\u043d\u0435\u0442\u044b: " + this.c.main().getString("integrations.economy.mode"));
        }
        catch (Exception e) {
            out.add(e.getMessage());
        }
        try {
            this.checkCases();
            out.add("PhoenixCrates: \u0442\u0440\u0438 \u0440\u043e\u043b\u0438 \u043a\u043b\u044e\u0447\u0435\u0439 \u0441\u043e\u043f\u043e\u0441\u0442\u0430\u0432\u043b\u0435\u043d\u044b");
        }
        catch (Exception e) {
            out.add(e.getMessage());
        }
        return out;
    }
}

