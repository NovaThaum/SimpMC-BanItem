package cn.simpmc.banitem.service;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import cn.simpmc.banitem.config.ConfigSnapshot;
import cn.simpmc.banitem.config.ItemDescriptor;
import cn.simpmc.banitem.config.PlayerIdentity;
import cn.simpmc.banitem.config.PluginSettings;
import cn.simpmc.banitem.log.ViolationLogger;
import cn.simpmc.banitem.log.ViolationRecord;

/** Performs all Bukkit mutations on the caller's current Folia region thread. */
public final class EnforcementService {
    private final AtomicReference<PluginSettings> settings;
    private final ViolationLogger violations;
    private final MessageService messages;
    private final Logger logger;
    private final AtomicLong nextRejectedLogWarning = new AtomicLong();

    public EnforcementService(AtomicReference<PluginSettings> settings, ViolationLogger violations,
                              MessageService messages, Logger logger) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.violations = Objects.requireNonNull(violations, "violations");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    public PluginSettings settings() {
        return settings.get();
    }

    public boolean isBanned(Player player, ItemStack item, PluginSettings current) {
        return usable(item) && current.policy().shouldConfiscate(identity(player), descriptor(item));
    }

    public boolean confiscate(Player player, ItemStack item, String action, Runnable removal, PluginSettings current) {
        if (!isBanned(player, item, current)) {
            return false;
        }
        String material = material(item);
        int amount = item.getAmount();
        removal.run();
        record(player, material, amount, action);
        messages.sendNow(player, current.messages().confiscated(),
                Map.of("item", material, "amount", Integer.toString(amount)));
        return true;
    }

    public int scanPlayerInventory(Player player, String action, PluginSettings current) {
        Inventory inventory = player.getInventory();
        int removed = 0;
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            int capturedSlot = slot;
            if (confiscate(player, item, action + "_SLOT_" + slot, () -> inventory.clear(capturedSlot), current)) {
                removed++;
            }
        }
        return removed;
    }

    public boolean confiscateDroppedEntity(Player player, Item dropped, String action, PluginSettings current) {
        ItemStack stack = dropped.getItemStack();
        return confiscate(player, stack, action, dropped::remove, current);
    }

    public boolean shouldBlockCreativeDrop(Player player, ItemStack item, PluginSettings current) {
        return player.getGameMode() == GameMode.CREATIVE
                && current.creativeDropEnabled()
                && usable(item)
                && current.policy().shouldBlockCreativeDrop(descriptor(item));
    }

    public void reportCreativeDropBlocked(Player player, ItemStack item, String action, PluginSettings current) {
        String material = material(item);
        record(player, material, item.getAmount(), action);
        messages.sendNow(player, current.messages().creativeDropBlocked(), Map.of("item", material));
    }

    private void record(Player player, String material, int amount, String action) {
        Location location = player.getLocation();
        boolean accepted = violations.submit(new ViolationRecord(
                player.getName(),
                player.getUniqueId(),
                Instant.now(),
                material,
                amount,
                action,
                location.getWorld().getName(),
                location.getX(),
                location.getY(),
                location.getZ()));
        if (!accepted) {
            warnRejectedViolation();
        }
    }

    private void warnRejectedViolation() {
        long now = System.nanoTime();
        long next = nextRejectedLogWarning.get();
        if (now < next || !nextRejectedLogWarning.compareAndSet(next, now + TimeUnit.MINUTES.toNanos(1))) {
            return;
        }
        logger.warning("A violation audit entry was rejected because the log writer is shutting down.");
    }

    private static PlayerIdentity identity(Player player) {
        return new PlayerIdentity(player.getUniqueId(), player.getName());
    }

    private static ItemDescriptor descriptor(ItemStack item) {
        return ItemDescriptor.of(material(item));
    }

    @SuppressWarnings("deprecation")
    private static String material(ItemStack item) {
        return item.getType().getKey().toString();
    }

    private static boolean usable(ItemStack item) {
        return item != null && !item.isEmpty();
    }
}
