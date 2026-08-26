package cn.simpmc.banitem.config;

import java.util.Objects;
import java.util.UUID;

/**
 * The minimum player information needed by the policy model.  Keeping this
 * separate from Bukkit's Player type makes configuration checks safe to use
 * outside an entity scheduler.
 */
public record PlayerIdentity(UUID uniqueId, String name) {
    public PlayerIdentity {
        Objects.requireNonNull(uniqueId, "uniqueId");
        Objects.requireNonNull(name, "name");
        if (name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
    }
}
