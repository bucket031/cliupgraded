package com.example.absolutevoidclock;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** /absolutevoidclock (aliases /voidclock, /avc). */
public final class ClockCommand implements TabExecutor {
    private static final List<String> SUBCOMMANDS = List.of("give", "config", "reload", "resetcooldown", "help");
    private static final List<String> ABILITY_NAMES = List.of("freeze", "slow", "beam");

    private final AbsoluteVoidClock plugin;

    public ClockCommand(AbsoluteVoidClock plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            help(sender, label);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("give")) {
            give(sender, args);
        } else if (sub.equals("config")) {
            config(sender, args);
        } else if (sub.equals("reload")) {
            plugin.reloadConfig();
            send(sender, "Absolute Void Clock config reloaded.", NamedTextColor.GREEN);
        } else if (sub.equals("resetcooldown")) {
            resetCooldown(sender, args);
        } else {
            help(sender, label);
        }
        return true;
    }

    private void give(CommandSender sender, String[] args) {
        Player target = resolveTarget(sender, args, "Usage: /avc give <player>");
        if (target == null) {
            return;
        }
        if (target.getInventory().firstEmpty() == -1) {
            send(sender, target.getName() + " has no free inventory slot.", NamedTextColor.RED);
            return;
        }
        target.getInventory().addItem(plugin.items().create(ClockItem.DEFAULT_ABILITY));
        send(sender, "Gave the Absolute Void Clock to " + target.getName() + ".", NamedTextColor.GREEN);
        if (!target.equals(sender)) {
            send(target, "You received the Absolute Void Clock. Drop it (Q) to choose an ability.",
                    NamedTextColor.LIGHT_PURPLE);
        }
    }

    private void config(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            send(sender, "The config menu can only be opened in-game.", NamedTextColor.RED);
            return;
        }
        if (args.length >= 2) {
            Ability ability = Ability.fromInput(args[1]);
            if (ability == null) {
                send(sender, "Unknown ability. Use: freeze, slow or beam.", NamedTextColor.RED);
                return;
            }
            plugin.configMenu().openPage(player, ability);
        } else {
            plugin.configMenu().openMain(player);
        }
    }

    private void resetCooldown(CommandSender sender, String[] args) {
        Player target = resolveTarget(sender, args, "Usage: /avc resetcooldown <player>");
        if (target == null) {
            return;
        }
        plugin.abilities().resetCooldowns(target.getUniqueId());
        send(sender, "Reset all Void Clock cooldowns for " + target.getName() + ".", NamedTextColor.GREEN);
    }

    private Player resolveTarget(CommandSender sender, String[] args, String usage) {
        if (args.length >= 2) {
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                send(sender, "Player not found: " + args[1], NamedTextColor.RED);
            }
            return target;
        }
        if (sender instanceof Player player) {
            return player;
        }
        send(sender, usage, NamedTextColor.RED);
        return null;
    }

    private void help(CommandSender sender, String label) {
        send(sender, "Absolute Void Clock", NamedTextColor.DARK_PURPLE);
        send(sender, "/" + label + " give [player] - give the clock", NamedTextColor.GRAY);
        send(sender, "/" + label + " config [freeze|slow|beam] - open the config menus", NamedTextColor.GRAY);
        send(sender, "/" + label + " reload - reload config.yml", NamedTextColor.GRAY);
        send(sender, "/" + label + " resetcooldown [player] - reset ability cooldowns", NamedTextColor.GRAY);
    }

    private static void send(CommandSender to, String text, NamedTextColor color) {
        to.sendMessage(Component.text(text, color));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (String sub : SUBCOMMANDS) {
                if (sub.startsWith(args[0].toLowerCase(Locale.ROOT))) {
                    out.add(sub);
                }
            }
        } else if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            String typed = args[1].toLowerCase(Locale.ROOT);
            if (sub.equals("give") || sub.equals("resetcooldown")) {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getName().toLowerCase(Locale.ROOT).startsWith(typed)) {
                        out.add(p.getName());
                    }
                }
            } else if (sub.equals("config")) {
                for (String name : ABILITY_NAMES) {
                    if (name.startsWith(typed)) {
                        out.add(name);
                    }
                }
            }
        }
        return out;
    }
}
