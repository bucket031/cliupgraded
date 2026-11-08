package com.example.absolutevoidclock;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/** Marks one of our menus so the listeners can recognise it. */
public final class MenuHolder implements InventoryHolder {
    public enum Type { SELECT, CONFIG_MAIN, CONFIG_PAGE }

    private final Type type;
    private final Ability ability;
    private Inventory inventory;

    public MenuHolder(Type type, Ability ability) {
        this.type = type;
        this.ability = ability;
    }

    public Type type() {
        return type;
    }

    /** Only set for CONFIG_PAGE menus. */
    public Ability ability() {
        return ability;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
