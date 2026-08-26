package cn.simpmc.banitem.config;

import java.util.Objects;

/** Complete immutable runtime configuration swapped atomically on reload. */
public record PluginSettings(
        ConfigSnapshot policy,
        boolean creativeDropEnabled,
        Messages messages) {

    public PluginSettings {
        Objects.requireNonNull(policy, "policy");
        Objects.requireNonNull(messages, "messages");
    }

    public record Messages(
            String confiscated,
            String creativeDropBlocked,
            String reloaded,
            String reloadFailed,
            String noPermission,
            String usage) {
        public Messages {
            Objects.requireNonNull(confiscated, "confiscated");
            Objects.requireNonNull(creativeDropBlocked, "creativeDropBlocked");
            Objects.requireNonNull(reloaded, "reloaded");
            Objects.requireNonNull(reloadFailed, "reloadFailed");
            Objects.requireNonNull(noPermission, "noPermission");
            Objects.requireNonNull(usage, "usage");
        }
    }
}
