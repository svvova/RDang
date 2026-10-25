/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.Bukkit
 *  org.bukkit.command.CommandExecutor
 *  org.bukkit.command.PluginCommand
 *  org.bukkit.command.TabCompleter
 *  org.bukkit.event.Listener
 *  org.bukkit.plugin.Plugin
 *  org.bukkit.plugin.java.JavaPlugin
 */
package org.jamhardcore.dungeons;

import java.util.Objects;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.jamhardcore.dungeons.AdminUI;
import org.jamhardcore.dungeons.Commands;
import org.jamhardcore.dungeons.Configs;
import org.jamhardcore.dungeons.Engine;
import org.jamhardcore.dungeons.EventSchedule;
import org.jamhardcore.dungeons.Guards;
import org.jamhardcore.dungeons.Integrations;
import org.jamhardcore.dungeons.Messages;
import org.jamhardcore.dungeons.Rewards;
import org.jamhardcore.dungeons.Spawner;
import org.jamhardcore.dungeons.Store;
import org.jamhardcore.dungeons.Templates;

public class JamDungeons
extends JavaPlugin {
    private Store db;
    private Engine engine;
    private AdminUI ui;
    private Guards guards;

    Engine engine() {
        return this.engine;
    }

    protected Store createStore(Configs c) throws Exception {
        return new Store(c.main());
    }

    public void onEnable() {
        try {
            Configs c = new Configs(this);
            this.db = this.createStore(c);
            Messages msg = new Messages(c);
            Integrations hooks = new Integrations(c);
            CoreProtectHook coreProtect = new CoreProtectHook(this, c);
            coreProtect.init();
            Rewards rewards = new Rewards(this, c, this.db, hooks, msg);
            Spawner mobs = new Spawner(this, c);
            Templates templates = new Templates(this, c);
            this.engine = new Engine(this, c, this.db, msg, hooks, rewards, mobs, templates, coreProtect);
            this.ui = new AdminUI(this, this.engine);
            Commands commands = new Commands(this.engine, this.ui);
            this.ui.commands(commands);
            PluginCommand command = Objects.requireNonNull(this.getCommand("jamdungeons"));
            command.setExecutor((CommandExecutor)commands);
            command.setTabCompleter((TabCompleter)commands);
            this.guards = new Guards(this, this.engine);
            Bukkit.getPluginManager().registerEvents((Listener)this.guards, (Plugin)this);
            Bukkit.getPluginManager().registerEvents((Listener)this.ui, (Plugin)this);
            try {
                this.engine.regions.refreshDefinitions();
            }
            catch (Exception error) {
                this.getLogger().log(Level.WARNING, "\u041d\u0435 \u0443\u0434\u0430\u043b\u043e\u0441\u044c \u043e\u0431\u043d\u043e\u0432\u0438\u0442\u044c \u043f\u0440\u0438\u0432\u0430\u0442\u044b WorldGuard. \u0412\u0441\u0442\u0440\u043e\u0435\u043d\u043d\u0430\u044f \u0437\u0430\u0449\u0438\u0442\u0430 \u043f\u043e\u0441\u0442\u0440\u043e\u0435\u043a \u0430\u043a\u0442\u0438\u0432\u043d\u0430.", error);
            }
            this.db.recoverVouchers();
            this.engine.recover();
            EventSchedule schedule = new EventSchedule(this, this.engine);
            this.engine.schedule = schedule;
            Bukkit.getScheduler().runTaskTimer((Plugin)this, () -> {
                this.engine.tick();
                this.guards.pulse();
                try {
                    schedule.tick();
                }
                catch (Exception ex) {
                    this.getLogger().log(Level.SEVERE, "\u041e\u0448\u0438\u0431\u043a\u0430 \u0440\u0430\u0441\u043f\u0438\u0441\u0430\u043d\u0438\u044f", ex);
                }
            }, 20L, 20L);
            this.getLogger().info("JamDungeons enabled: " + c.themes.size() + " themes, " + c.arenas.size() + " arenas. Admin menu: /jd");
            for (String s : hooks.status()) {
                this.getLogger().info(s);
            }
        }
        catch (Exception ex) {
            this.getLogger().log(Level.SEVERE, "\u0417\u0430\u043f\u0443\u0441\u043a JamDungeons \u043e\u0441\u0442\u0430\u043d\u043e\u0432\u043b\u0435\u043d", ex);
            Bukkit.getPluginManager().disablePlugin((Plugin)this);
        }
    }

    public void onDisable() {
        if (this.engine != null) {
            this.engine.placement.close();
        }
        if (this.ui != null) {
            this.ui.close();
        }
        if (this.guards != null) {
            this.guards.close();
        }
        try {
            if (this.engine != null) {
                this.engine.close();
            }
        }
        catch (Exception ex) {
            this.getLogger().log(Level.SEVERE, "\u041d\u0435 \u0443\u0434\u0430\u043b\u043e\u0441\u044c \u0441\u043e\u0445\u0440\u0430\u043d\u0438\u0442\u044c \u0441\u043e\u0431\u044b\u0442\u0438\u044f", ex);
        }
        try {
            if (this.db != null) {
                this.db.close();
            }
        }
        catch (Exception ex) {
            this.getLogger().log(Level.SEVERE, "\u041d\u0435 \u0443\u0434\u0430\u043b\u043e\u0441\u044c \u0437\u0430\u043a\u0440\u044b\u0442\u044c \u0431\u0430\u0437\u0443", ex);
        }
    }
}

