package cn.simpmc.banitem.listener;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import cn.simpmc.banitem.config.PluginSettings;
import cn.simpmc.banitem.service.EnforcementService;

/**
 * Interactive checks stay synchronous: Folia already invokes these callbacks on
 * the owning region, and immediate inventory/entity mutation is not async-safe.
 */
public final class ItemEnforcementListener implements Listener {
    private final Plugin plugin;
    private final EnforcementService enforcement;

    public ItemEnforcementListener(Plugin plugin, EnforcementService enforcement) {
        this.plugin = plugin;
        this.enforcement = enforcement;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent event) {
        PluginSettings current = enforcement.settings();
        if (enforcement.scanPlayerInventory(event.getPlayer(), "INTERACT", current) > 0) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        PluginSettings current = enforcement.settings();
        if (enforcement.scanPlayerInventory(event.getPlayer(), "INTERACT_ENTITY", current) > 0) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteractAtEntity(PlayerInteractAtEntityEvent event) {
        PluginSettings current = enforcement.settings();
        if (enforcement.scanPlayerInventory(event.getPlayer(), "INTERACT_AT_ENTITY", current) > 0) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) {
            return;
        }
        if (!Bukkit.isOwnedByCurrentRegion(player)) {
            // Folia may deliver a cross-region damage event on the victim's
            // region. Do not touch the attacker's inventory from there.
            event.setCancelled(true);
            player.getScheduler().execute(plugin,
                    () -> enforcement.scanPlayerInventory(player, "CROSS_REGION_ENTITY_ATTACK", enforcement.settings()),
                    null, 1L);
            return;
        }
        PluginSettings current = enforcement.settings();
        if (enforcement.scanPlayerInventory(player, "ENTITY_ATTACK", current) > 0) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onArmorStandManipulate(PlayerArmorStandManipulateEvent event) {
        PluginSettings current = enforcement.settings();
        if (enforcement.scanPlayerInventory(event.getPlayer(), "ARMOR_STAND", current) > 0) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onConsume(PlayerItemConsumeEvent event) {
        PluginSettings current = enforcement.settings();
        if (enforcement.scanPlayerInventory(event.getPlayer(), "CONSUME", current) > 0) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onHeldSlot(PlayerItemHeldEvent event) {
        PluginSettings current = enforcement.settings();
        if (enforcement.scanPlayerInventory(event.getPlayer(), "HELD_SLOT_CHANGE", current) > 0) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        PluginSettings current = enforcement.settings();
        if (enforcement.scanPlayerInventory(event.getPlayer(), "SWAP_HANDS", current) > 0) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        PluginSettings current = enforcement.settings();
        if (event.isCancelled()) {
            // Respect protection plugins: do not remove their ground entity.
            enforcement.scanPlayerInventory(player, "CANCELLED_PICKUP_SCAN", current);
            return;
        }
        boolean confiscated = enforcement.confiscateDroppedEntity(player, event.getItem(), "PICKUP", current);
        if (confiscated) {
            event.setCancelled(true);
        }
        enforcement.scanPlayerInventory(player, "PICKUP_SCAN", current);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrop(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        PluginSettings current = enforcement.settings();
        if (event.isCancelled()) {
            // The server restores a cancelled drop after the event transaction.
            // Leave the protected entity alone and confiscate the restored player
            // copy on its owning region thread.
            enforcement.scanPlayerInventory(player, "CANCELLED_DROP_SCAN", current);
            scheduleInventoryRecheck(player, "CANCELLED_DROP_RESTORE_CHECK");
            return;
        }
        ItemStack dropped = event.getItemDrop().getItemStack();

        if (enforcement.isBanned(player, dropped, current)) {
            enforcement.confiscateDroppedEntity(player, event.getItemDrop(), "DROP_CONFISCATED", current);
            enforcement.scanPlayerInventory(player, "DROP_SCAN", current);
            // Another plugin may cancel later and restore the stack. Recheck the player
            // one tick later on that player's entity scheduler as a defensive fallback.
            player.getScheduler().execute(plugin,
                    () -> enforcement.scanPlayerInventory(player, "DROP_RESTORE_CHECK", enforcement.settings()),
                    null, 1L);
            return;
        }

        if (enforcement.shouldBlockCreativeDrop(player, dropped, current)) {
            event.setCancelled(true);
            enforcement.reportCreativeDropBlocked(player, dropped, "CREATIVE_WORLD_DROP_BLOCKED", current);
        }
        enforcement.scanPlayerInventory(player, "DROP_SCAN", current);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        PluginSettings current = enforcement.settings();
        if (event.isCancelled()) {
            // Never delete a slot from a container protected by another plugin.
            // The player's inventory and cursor are player-owned and remain in scope.
            enforcement.confiscate(player, player.getItemOnCursor(), "CANCELLED_INVENTORY_CURSOR",
                    () -> player.setItemOnCursor(ItemStack.empty()), current);
            enforcement.scanPlayerInventory(player, "CANCELLED_INVENTORY_SCAN", current);
            scheduleInventoryRecheck(player, "CANCELLED_INVENTORY_RESTORE_CHECK");
            return;
        }
        boolean changed = false;

        ItemStack currentItem = event.getCurrentItem();
        if (enforcement.confiscate(player, currentItem, "INVENTORY_CURRENT",
                () -> event.setCurrentItem(ItemStack.empty()), current)) {
            changed = true;
        }
        ItemStack cursor = event.getCursor();
        if (enforcement.confiscate(player, cursor, "INVENTORY_CURSOR",
                () -> event.setCursor(ItemStack.empty()), current)) {
            changed = true;
        }
        if (enforcement.scanPlayerInventory(player, "INVENTORY_SCAN", current) > 0) {
            changed = true;
        }

        if (changed) {
            event.setCancelled(true);
            scheduleInventoryRecheck(player, "INVENTORY_RESTORE_CHECK");
            return;
        }

        ItemStack dropCandidate = dropCandidate(event);
        if (dropCandidate != null && enforcement.shouldBlockCreativeDrop(player, dropCandidate, current)) {
            event.setCancelled(true);
            enforcement.reportCreativeDropBlocked(player, dropCandidate, "CREATIVE_GUI_DROP_BLOCKED", current);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        PluginSettings current = enforcement.settings();
        if (event.isCancelled()) {
            enforcement.confiscate(player, player.getItemOnCursor(), "CANCELLED_INVENTORY_DRAG_CURSOR",
                    () -> player.setItemOnCursor(ItemStack.empty()), current);
            enforcement.scanPlayerInventory(player, "CANCELLED_INVENTORY_DRAG_SCAN", current);
            scheduleInventoryRecheck(player, "CANCELLED_INVENTORY_DRAG_RESTORE_CHECK");
            return;
        }
        boolean changed = enforcement.confiscate(player, event.getOldCursor(), "INVENTORY_DRAG",
                () -> event.setCursor(ItemStack.empty()), current);
        if (enforcement.scanPlayerInventory(player, "INVENTORY_DRAG_SCAN", current) > 0) {
            changed = true;
        }
        if (changed) {
            event.setCancelled(true);
            scheduleInventoryRecheck(player, "INVENTORY_DRAG_RESTORE_CHECK");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (event.getPlayer() instanceof Player player) {
            enforcement.scanPlayerInventory(player, "INVENTORY_OPEN", enforcement.settings());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) {
            enforcement.scanPlayerInventory(player, "INVENTORY_CLOSE", enforcement.settings());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onJoin(PlayerJoinEvent event) {
        enforcement.scanPlayerInventory(event.getPlayer(), "JOIN", enforcement.settings());
    }

    /**
     * A cancelled inventory transaction can restore an item after this listener
     * has changed the event view. Recheck on the player's entity scheduler so
     * the follow-up remains on the owning Folia region thread.
     */
    private void scheduleInventoryRecheck(Player player, String action) {
        player.getScheduler().execute(plugin,
                () -> {
                    if (player.isOnline()) {
                        enforcement.scanPlayerInventory(player, action, enforcement.settings());
                    }
                }, null, 1L);
    }

    private static ItemStack dropCandidate(InventoryClickEvent event) {
        InventoryAction action = event.getAction();
        return switch (action) {
            case DROP_ONE_SLOT, DROP_ALL_SLOT -> event.getCurrentItem();
            case DROP_ONE_CURSOR, DROP_ALL_CURSOR -> event.getCursor();
            default -> null;
        };
    }
}
