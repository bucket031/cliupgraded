package com.example.absolutevoidclock;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class AbsoluteVoidClock extends JavaPlugin {
    private ClockItem clockItem;
    private AbilityManager abilities;
    private SelectMenu selectMenu;
    private ConfigMenu configMenu;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        clockItem = new ClockItem(this);
        abilities = new AbilityManager(this);
        selectMenu = new SelectMenu(this);
        configMenu = new ConfigMenu(this);

        getServer().getPluginManager().registerEvents(new ClockListener(this), this);
        getServer().getPluginManager().registerEvents(new MenuListener(this), this);

        PluginCommand command = getCommand("absolutevoidclock");
        if (command != null) {
            ClockCommand handler = new ClockCommand(this);
            command.setExecutor(handler);
            command.setTabCompleter(handler);
        }

        abilities.start();
    }

    @Override
    public void onDisable() {
        if (abilities != null) {
            abilities.shutdown();
        }
    }

    public ClockItem items() {
        return clockItem;
    }

    public AbilityManager abilities() {
        return abilities;
    }

    public SelectMenu selectMenu() {
        return selectMenu;
    }

    public ConfigMenu configMenu() {
        return configMenu;
    }
}
