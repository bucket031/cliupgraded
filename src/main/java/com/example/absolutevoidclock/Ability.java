package com.example.absolutevoidclock;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;

public enum Ability {
    FREEZE_STRIKE("freeze_strike", "freeze", "Freeze Strike", Material.PACKED_ICE, NamedTextColor.AQUA,
            "Hit a player with the clock to freeze them",
            "in place, even mid-air. Free hits while frozen."),
    TIME_SLOW("time_slow", "slow", "Time Slow", Material.CLOCK, NamedTextColor.LIGHT_PURPLE,
            "Right-click to slow everything around you",
            "and boost your own attack speed."),
    VOID_BEAM("void_beam", "beam", "Void Beam", Material.END_ROD, NamedTextColor.DARK_PURPLE,
            "Right-click to fire a beam where you look",
            "that damages the first target it hits.");

    private final String id;
    private final String shortName;
    private final String displayName;
    private final Material icon;
    private final NamedTextColor color;
    private final String[] description;

    Ability(String id, String shortName, String displayName, Material icon, NamedTextColor color, String... description) {
        this.id = id;
        this.shortName = shortName;
        this.displayName = displayName;
        this.icon = icon;
        this.color = color;
        this.description = description;
    }

    /** Config section name and item tag value. */
    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public Material icon() {
        return icon;
    }

    public NamedTextColor color() {
        return color;
    }

    public String[] description() {
        return description;
    }

    public static Ability fromId(String id) {
        if (id == null) {
            return null;
        }
        for (Ability a : values()) {
            if (a.id.equalsIgnoreCase(id)) {
                return a;
            }
        }
        return null;
    }

    /** Accepts the id, the short name (freeze/slow/beam) or the enum name. */
    public static Ability fromInput(String input) {
        if (input == null) {
            return null;
        }
        for (Ability a : values()) {
            if (a.id.equalsIgnoreCase(input) || a.shortName.equalsIgnoreCase(input) || a.name().equalsIgnoreCase(input)) {
                return a;
            }
        }
        return null;
    }

    public String shortName() {
        return shortName;
    }
}
