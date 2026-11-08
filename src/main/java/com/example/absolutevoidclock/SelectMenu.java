package com.example.absolutevoidclock;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** The 3-option menu that opens when you try to drop the clock. */
public final class SelectMenu {
    private static final int[] SLOTS = {11, 13, 15};
    private static final int CONFIG_SLOT = 22;

    private final AbsoluteVoidClock plugin;

    public SelectMenu(AbsoluteVoidClock plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        MenuHolder holder = new MenuHolder(MenuHolder.Type.SELECT, null);
        Inventory inventory = Bukkit.createInventory(holder, 27,
                Component.text("Absolute Void Clock", NamedTextColor.DARK_PURPLE));
        holder.setInventory(inventory);

        ItemStack pane = Text.filler();
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, pane);
        }

        Ability current = plugin.items().findAbility(player);
        Ability[] abilities = Ability.values();
        for (int i = 0; i < abilities.length; i++) {
            inventory.setItem(SLOTS[i], build(abilities[i], abilities[i] == current));
        }

        if (player.hasPermission("absolutevoidclock.admin")) {
            ItemStack button = new ItemStack(Material.NETHER_STAR);
            button.editMeta(meta -> {
                meta.displayName(Text.bold("Configure abilities", NamedTextColor.GOLD));
                meta.lore(List.of(Text.plain("Open the config menus", NamedTextColor.GRAY)));
            });
            inventory.setItem(CONFIG_SLOT, button);
        }

        player.openInventory(inventory);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
    }

    public void handleClick(Player player, int slot) {
        Ability[] abilities = Ability.values();
        for (int i = 0; i < abilities.length; i++) {
            if (slot == SLOTS[i]) {
                select(player, abilities[i]);
                return;
            }
        }
        if (slot == CONFIG_SLOT && player.hasPermission("absolutevoidclock.admin")) {
            Bukkit.getScheduler().runTask(plugin, () -> plugin.configMenu().openMain(player));
        }
    }

    private void select(Player player, Ability ability) {
        plugin.items().setAbilityOnAll(player, ability);
        Bukkit.getScheduler().runTask(plugin, () -> player.closeInventory());
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1.2f);
        player.sendActionBar(Component.text("Ability set to " + ability.displayName(), ability.color()));
    }

    private ItemStack build(Ability ability, boolean selected) {
        ItemStack item = new ItemStack(ability.icon());
        item.editMeta(meta -> {
            meta.displayName(Text.bold(ability.displayName(), ability.color()));
            List<Component> lore = new ArrayList<>();
            for (String line : ability.description()) {
                lore.add(Text.plain(line, NamedTextColor.GRAY));
            }
            lore.add(Component.empty());
            for (ConfigMenu.Setting setting : ConfigMenu.settings(ability)) {
                lore.add(Text.plain("- " + setting.name() + ": "
                        + plugin.configMenu().display(ability, setting), NamedTextColor.WHITE));
            }
            lore.add(Component.empty());
            if (selected) {
                lore.add(Text.bold("SELECTED", NamedTextColor.GREEN));
            } else {
                lore.add(Text.plain("Click to select", NamedTextColor.YELLOW));
            }
            meta.lore(lore);
            if (selected) {
                meta.setEnchantmentGlintOverride(true);
            }
        });
        return item;
    }
}
