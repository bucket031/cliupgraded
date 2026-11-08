package com.example.absolutevoidclock;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;

/** Creates and identifies the Absolute Void Clock item. */
public final class ClockItem {
    public static final Ability DEFAULT_ABILITY = Ability.TIME_SLOW;

    private final NamespacedKey itemKey;
    private final NamespacedKey abilityKey;

    public ClockItem(Plugin plugin) {
        this.itemKey = new NamespacedKey(plugin, "absolute_void_clock");
        this.abilityKey = new NamespacedKey(plugin, "ability");
    }

    public ItemStack create(Ability ability) {
        ItemStack item = new ItemStack(Material.CLOCK);
        item.editMeta(meta -> {
            meta.getPersistentDataContainer().set(itemKey, PersistentDataType.BYTE, (byte) 1);
            decorate(meta, ability);
        });
        return item;
    }

    public boolean isClock(ItemStack item) {
        if (item == null || item.getType() != Material.CLOCK || !item.hasItemMeta()) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer().has(itemKey, PersistentDataType.BYTE);
    }

    public Ability getAbility(ItemStack item) {
        if (!isClock(item)) {
            return DEFAULT_ABILITY;
        }
        String id = item.getItemMeta().getPersistentDataContainer().get(abilityKey, PersistentDataType.STRING);
        Ability ability = Ability.fromId(id);
        return ability == null ? DEFAULT_ABILITY : ability;
    }

    public void setAbility(ItemStack item, Ability ability) {
        item.editMeta(meta -> decorate(meta, ability));
    }

    /** The ability on the first clock found in the player's inventory. */
    public Ability findAbility(Player player) {
        PlayerInventory inv = player.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (isClock(stack)) {
                return getAbility(stack);
            }
        }
        return DEFAULT_ABILITY;
    }

    /** Switches every clock the player carries to the given ability. */
    public void setAbilityOnAll(Player player, Ability ability) {
        PlayerInventory inv = player.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (isClock(stack)) {
                setAbility(stack, ability);
                inv.setItem(i, stack);
            }
        }
    }

    public boolean hasClock(Player player) {
        PlayerInventory inv = player.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            if (isClock(inv.getItem(i))) {
                return true;
            }
        }
        return false;
    }

    private void decorate(ItemMeta meta, Ability ability) {
        meta.getPersistentDataContainer().set(abilityKey, PersistentDataType.STRING, ability.id());
        meta.displayName(Text.bold("Absolute Void Clock", NamedTextColor.DARK_PURPLE));

        List<Component> lore = new ArrayList<>();
        lore.add(Text.plain("Time itself kneels to the void.", NamedTextColor.GRAY));
        lore.add(Component.empty());
        lore.add(Text.plain("Ability: ", NamedTextColor.GRAY)
                .append(Text.bold(ability.displayName(), ability.color())));
        for (String line : ability.description()) {
            lore.add(Text.plain(line, NamedTextColor.GRAY));
        }
        lore.add(Component.empty());
        lore.add(Text.plain("Drop (Q) to choose an ability", NamedTextColor.YELLOW));
        lore.add(Text.plain("Soulbound - it can never leave you", NamedTextColor.DARK_PURPLE));
        meta.lore(lore);
        meta.setEnchantmentGlintOverride(true);
    }
}
