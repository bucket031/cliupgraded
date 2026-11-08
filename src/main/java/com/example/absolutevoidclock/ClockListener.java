package com.example.absolutevoidclock;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Allay;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.Iterator;
import java.util.UUID;

/** Ability activation, soulbound rules and the Freeze Strike hold. */
public final class ClockListener implements Listener {
    private final AbsoluteVoidClock plugin;

    public ClockListener(AbsoluteVoidClock plugin) {
        this.plugin = plugin;
    }

    private ClockItem items() {
        return plugin.items();
    }

    private void openSelectLater(Player player) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                plugin.selectMenu().open(player);
            }
        });
    }

    // ------------------------------------------------------------------ abilities

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        ItemStack item = event.getItem();
        if (!items().isClock(item)) {
            return;
        }
        Player player = event.getPlayer();
        if (event.getHand() == EquipmentSlot.OFF_HAND && items().isClock(player.getInventory().getItemInMainHand())) {
            return;
        }
        event.setCancelled(true);
        if (action == Action.RIGHT_CLICK_BLOCK && event.getClickedBlock() != null
                && event.getClickedBlock().getType() == Material.DECORATED_POT) {
            return;
        }
        if (!player.hasPermission("absolutevoidclock.use")) {
            return;
        }
        plugin.abilities().use(player, items().getAbility(item));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK) {
            return;
        }
        if (!(event.getDamager() instanceof Player attacker)) {
            return;
        }
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        ItemStack held = attacker.getInventory().getItemInMainHand();
        if (!items().isClock(held) || items().getAbility(held) != Ability.FREEZE_STRIKE) {
            return;
        }
        if (!attacker.hasPermission("absolutevoidclock.use")) {
            return;
        }
        plugin.abilities().onFreezeHit(attacker, victim);
    }

    // ------------------------------------------------------------------ frozen players

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location lock = plugin.abilities().frozenLock(event.getPlayer().getUniqueId());
        if (lock == null) {
            return;
        }
        Location to = event.getTo();
        if (to == null || to.getWorld() == null || !to.getWorld().equals(lock.getWorld())) {
            return;
        }
        if (to.getX() == lock.getX() && to.getY() == lock.getY() && to.getZ() == lock.getZ()) {
            return;
        }
        Location fixed = to.clone();
        fixed.setX(lock.getX());
        fixed.setY(lock.getY());
        fixed.setZ(lock.getZ());
        event.setTo(fixed);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (!plugin.abilities().isFrozen(event.getPlayer().getUniqueId())) {
            return;
        }
        PlayerTeleportEvent.TeleportCause cause = event.getCause();
        if (cause == PlayerTeleportEvent.TeleportCause.ENDER_PEARL
                || cause == PlayerTeleportEvent.TeleportCause.CHORUS_FRUIT) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.abilities().clearPlayer(event.getPlayer());
    }

    // ------------------------------------------------------------------ soulbound: death

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent event) {
        plugin.abilities().clearPlayer(event.getEntity());
        if (event.getKeepInventory()) {
            return;
        }
        Iterator<ItemStack> iterator = event.getDrops().iterator();
        while (iterator.hasNext()) {
            ItemStack stack = iterator.next();
            if (items().isClock(stack)) {
                iterator.remove();
                event.getItemsToKeep().add(stack);
            }
        }
    }

    // ------------------------------------------------------------------ soulbound: dropping

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrop(PlayerDropItemEvent event) {
        if (!items().isClock(event.getItemDrop().getItemStack())) {
            return;
        }
        event.setCancelled(true);
        openSelectLater(event.getPlayer());
    }

    /** Safety net: if something else spawns the clock as an item, hand it back to whoever threw it. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onItemSpawn(ItemSpawnEvent event) {
        Item entity = event.getEntity();
        if (!items().isClock(entity.getItemStack())) {
            return;
        }
        UUID thrower = entity.getThrower();
        if (thrower == null) {
            return;
        }
        Player player = Bukkit.getPlayer(thrower);
        if (player == null || !player.isOnline() || player.getInventory().firstEmpty() == -1) {
            return;
        }
        event.setCancelled(true);
        player.getInventory().addItem(entity.getItemStack());
    }

    // ------------------------------------------------------------------ soulbound: inventories

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        Inventory top = event.getView().getTopInventory();
        if (top.getHolder() instanceof MenuHolder) {
            return;
        }
        ClockItem clock = items();
        Inventory clicked = event.getClickedInventory();
        boolean clickedTop = clicked != null && clicked == top;
        boolean cursorIsClock = clock.isClock(event.getCursor());
        boolean currentIsClock = clock.isClock(event.getCurrentItem());
        ClickType click = event.getClick();

        // Throwing it out of the window with the cursor.
        if (event.getSlotType() == InventoryType.SlotType.OUTSIDE && cursorIsClock) {
            event.setCancelled(true);
            openSelectLater(player);
            return;
        }
        // Q / Ctrl+Q while hovering the clock.
        if ((click == ClickType.DROP || click == ClickType.CONTROL_DROP) && currentIsClock) {
            event.setCancelled(true);
            openSelectLater(player);
            return;
        }
        // Placing it into anything that is not the player's own inventory.
        if (clickedTop) {
            if (cursorIsClock) {
                event.setCancelled(true);
                return;
            }
            if (click == ClickType.NUMBER_KEY) {
                int button = event.getHotbarButton();
                if (button >= 0 && clock.isClock(player.getInventory().getItem(button))) {
                    event.setCancelled(true);
                    return;
                }
            }
            if (click == ClickType.SWAP_OFFHAND && clock.isClock(player.getInventory().getItemInOffHand())) {
                event.setCancelled(true);
                return;
            }
        }
        // Shift-clicking it into a chest, ender chest, anvil, etc.
        if (event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY && currentIsClock
                && !clickedTop && top.getType() != InventoryType.CRAFTING) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrag(InventoryDragEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (top.getHolder() instanceof MenuHolder) {
            return;
        }
        if (!items().isClock(event.getOldCursor())) {
            return;
        }
        int topSize = top.getSize();
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot < topSize) {
                event.setCancelled(true);
                return;
            }
        }
    }

    // ------------------------------------------------------------------ soulbound: giving it to entities

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityInteract(PlayerInteractEntityEvent event) {
        guardEntity(event.getPlayer(), event.getHand(), event.getRightClicked(), event);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityInteractAt(PlayerInteractAtEntityEvent event) {
        guardEntity(event.getPlayer(), event.getHand(), event.getRightClicked(), event);
    }

    private void guardEntity(Player player, EquipmentSlot hand, Entity target, PlayerInteractEntityEvent event) {
        ItemStack held = hand == EquipmentSlot.HAND
                ? player.getInventory().getItemInMainHand()
                : player.getInventory().getItemInOffHand();
        if (!items().isClock(held)) {
            return;
        }
        if (target instanceof ItemFrame || target instanceof ArmorStand || target instanceof Allay) {
            event.setCancelled(true);
        }
    }
}
