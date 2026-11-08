package com.example.absolutevoidclock;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** In-game config menus: one main page and one page per ability. */
public final class ConfigMenu {
    public enum Kind { INT, DOUBLE, BOOL }

    public record Setting(String key, String name, Material icon, Kind kind,
                          double min, double max, double step, String unit, String help) {
    }

    private static final int[] MAIN_SLOTS = {11, 13, 15};
    private static final int[] SETTING_SLOTS = {10, 11, 12, 13, 14, 15, 16};
    private static final int BACK_SLOT = 22;

    private static final Map<Ability, List<Setting>> SETTINGS = new EnumMap<>(Ability.class);

    static {
        SETTINGS.put(Ability.FREEZE_STRIKE, List.of(
                new Setting("freeze_seconds", "Freeze Duration", Material.BLUE_ICE, Kind.DOUBLE,
                        0.5, 10, 0.5, "s", "How long the target stays frozen."),
                new Setting("cooldown_seconds", "Cooldown", Material.REPEATER, Kind.INT,
                        0, 300, 1, "s", "Time before you can freeze again.")));

        SETTINGS.put(Ability.TIME_SLOW, List.of(
                new Setting("duration_seconds", "Duration", Material.CLOCK, Kind.INT,
                        1, 120, 1, "s", "How long the field lasts."),
                new Setting("cooldown_seconds", "Cooldown", Material.REPEATER, Kind.INT,
                        0, 3600, 5, "s", "Time before you can use it again."),
                new Setting("tick_rate", "Tick Rate", Material.SOUL_SAND, Kind.INT,
                        1, 20, 1, "", "20 = normal speed, 5 = quarter speed."),
                new Setting("attack_speed_percent", "Attack Speed Boost", Material.IRON_SWORD, Kind.INT,
                        0, 300, 25, "%", "100 = double your attack speed."),
                new Setting("radius", "Radius", Material.COMPASS, Kind.INT,
                        1, 32, 1, "blocks", "Size of the slow field."),
                new Setting("resistance_level", "Resistance Level", Material.SHIELD, Kind.INT,
                        0, 5, 1, "", "Resistance for you while active. 0 = none.")));

        SETTINGS.put(Ability.VOID_BEAM, List.of(
                new Setting("damage_hearts", "Damage", Material.REDSTONE, Kind.DOUBLE,
                        0.5, 20, 0.5, "hearts", "Damage dealt to the target."),
                new Setting("range", "Range", Material.SPYGLASS, Kind.INT,
                        5, 100, 5, "blocks", "How far the beam reaches."),
                new Setting("cooldown_seconds", "Cooldown", Material.REPEATER, Kind.INT,
                        0, 300, 1, "s", "Time before you can fire again."),
                new Setting("ignore_armor", "Ignore Armor", Material.IRON_CHESTPLATE, Kind.BOOL,
                        0, 1, 1, "", "On: armor does not reduce the damage.")));
    }

    private final AbsoluteVoidClock plugin;

    public ConfigMenu(AbsoluteVoidClock plugin) {
        this.plugin = plugin;
    }

    public static List<Setting> settings(Ability ability) {
        return SETTINGS.get(ability);
    }

    // ------------------------------------------------------------------ values

    public String display(Ability ability, Setting setting) {
        String path = ability.id() + "." + setting.key();
        if (setting.kind() == Kind.BOOL) {
            return plugin.getConfig().getBoolean(path) ? "Enabled" : "Disabled";
        }
        String value = fmt(plugin.getConfig().getDouble(path));
        return setting.unit().isEmpty() ? value : value + " " + setting.unit();
    }

    private static String fmt(double value) {
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    private void adjust(Ability ability, Setting setting, ClickType click) {
        String path = ability.id() + "." + setting.key();
        if (setting.kind() == Kind.BOOL) {
            plugin.getConfig().set(path, !plugin.getConfig().getBoolean(path));
            plugin.saveConfig();
            return;
        }
        boolean up = click == ClickType.LEFT || click == ClickType.SHIFT_LEFT;
        boolean down = click == ClickType.RIGHT || click == ClickType.SHIFT_RIGHT;
        if (!up && !down) {
            return;
        }
        double multiplier = click.isShiftClick() ? 5 : 1;
        double current = plugin.getConfig().getDouble(path);
        double next = current + (up ? 1 : -1) * setting.step() * multiplier;
        next = Math.max(setting.min(), Math.min(setting.max(), next));
        next = Math.round(next * 100.0) / 100.0;
        if (setting.kind() == Kind.INT) {
            plugin.getConfig().set(path, (int) Math.round(next));
        } else {
            plugin.getConfig().set(path, next);
        }
        plugin.saveConfig();
    }

    // ------------------------------------------------------------------ opening

    public void openMain(Player player) {
        MenuHolder holder = new MenuHolder(MenuHolder.Type.CONFIG_MAIN, null);
        Inventory inventory = Bukkit.createInventory(holder, 27,
                Component.text("Void Clock Config", NamedTextColor.DARK_PURPLE));
        holder.setInventory(inventory);

        ItemStack pane = Text.filler();
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, pane);
        }
        Ability[] abilities = Ability.values();
        for (int i = 0; i < abilities.length; i++) {
            Ability ability = abilities[i];
            ItemStack item = new ItemStack(ability.icon());
            item.editMeta(meta -> {
                meta.displayName(Text.bold(ability.displayName(), ability.color()));
                List<Component> lore = new ArrayList<>();
                for (String line : ability.description()) {
                    lore.add(Text.plain(line, NamedTextColor.GRAY));
                }
                lore.add(Component.empty());
                lore.add(Text.plain("Click to configure", NamedTextColor.YELLOW));
                meta.lore(lore);
            });
            inventory.setItem(MAIN_SLOTS[i], item);
        }
        player.openInventory(inventory);
    }

    public void openPage(Player player, Ability ability) {
        MenuHolder holder = new MenuHolder(MenuHolder.Type.CONFIG_PAGE, ability);
        Inventory inventory = Bukkit.createInventory(holder, 27,
                Component.text("Config: " + ability.displayName(), ability.color()));
        holder.setInventory(inventory);
        render(inventory, ability);
        player.openInventory(inventory);
    }

    private void render(Inventory inventory, Ability ability) {
        ItemStack pane = Text.filler();
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, pane);
        }
        List<Setting> list = settings(ability);
        for (int i = 0; i < list.size(); i++) {
            inventory.setItem(SETTING_SLOTS[i], settingItem(ability, list.get(i)));
        }
        ItemStack back = new ItemStack(Material.ARROW);
        back.editMeta(meta -> meta.displayName(Text.bold("Back", NamedTextColor.YELLOW)));
        inventory.setItem(BACK_SLOT, back);
    }

    private ItemStack settingItem(Ability ability, Setting setting) {
        ItemStack item = new ItemStack(setting.icon());
        item.editMeta(meta -> {
            meta.displayName(Text.bold(setting.name(), NamedTextColor.GOLD));
            List<Component> lore = new ArrayList<>();
            lore.add(Text.plain("Current: " + display(ability, setting), NamedTextColor.GREEN));
            if (setting.kind() != Kind.BOOL) {
                lore.add(Text.plain("Range: " + fmt(setting.min()) + " - " + fmt(setting.max()),
                        NamedTextColor.GRAY));
            }
            lore.add(Text.plain(setting.help(), NamedTextColor.GRAY));
            lore.add(Component.empty());
            if (setting.kind() == Kind.BOOL) {
                lore.add(Text.plain("Click to toggle", NamedTextColor.YELLOW));
            } else {
                lore.add(Text.plain("Left-click: +" + fmt(setting.step()), NamedTextColor.YELLOW));
                lore.add(Text.plain("Right-click: -" + fmt(setting.step()), NamedTextColor.YELLOW));
                lore.add(Text.plain("Shift-click: x5", NamedTextColor.YELLOW));
            }
            meta.lore(lore);
        });
        return item;
    }

    // ------------------------------------------------------------------ clicks

    public void handleClick(Player player, MenuHolder holder, int slot, ClickType click) {
        if (!player.hasPermission("absolutevoidclock.admin")) {
            Bukkit.getScheduler().runTask(plugin, () -> player.closeInventory());
            return;
        }
        if (holder.type() == MenuHolder.Type.CONFIG_MAIN) {
            Ability[] abilities = Ability.values();
            for (int i = 0; i < abilities.length; i++) {
                if (slot == MAIN_SLOTS[i]) {
                    Ability picked = abilities[i];
                    Bukkit.getScheduler().runTask(plugin, () -> openPage(player, picked));
                    return;
                }
            }
            return;
        }

        Ability ability = holder.ability();
        if (ability == null) {
            return;
        }
        if (slot == BACK_SLOT) {
            Bukkit.getScheduler().runTask(plugin, () -> openMain(player));
            return;
        }
        List<Setting> list = settings(ability);
        for (int i = 0; i < list.size(); i++) {
            if (slot == SETTING_SLOTS[i]) {
                adjust(ability, list.get(i), click);
                render(holder.getInventory(), ability);
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.3f);
                return;
            }
        }
    }
}
