package cn.simpmc.banitem.log;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** A complete, immutable audit entry for one confiscated or blocked item. */
public record ViolationRecord(
        String playerName,
        UUID playerUuid,
        Instant timestamp,
        String material,
        int amount,
        String action,
        String world,
        double x,
        double y,
        double z) {

    public ViolationRecord {
        playerName = requireText(playerName, "playerName");
        playerUuid = Objects.requireNonNull(playerUuid, "playerUuid");
        timestamp = Objects.requireNonNull(timestamp, "timestamp");
        material = requireText(material, "material");
        if (amount < 1) {
            throw new IllegalArgumentException("amount must be at least 1");
        }
        action = requireText(action, "action");
        world = requireText(world, "world");
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
