package com.example.absolutevoidclock;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/** Small helpers for building item text without the default italics. */
final class Text {
    private Text() {
    }

    static Component plain(String text, TextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }

    static Component bold(String text, TextColor color) {
        return Component.text(text, color)
                .decoration(TextDecoration.ITALIC, false)
                .decoration(TextDecoration.BOLD, true);
    }

    static ItemStack filler() {
        ItemStack pane = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        pane.editMeta(meta -> meta.displayName(Component.text(" ")));
        return pane;
    }
}
