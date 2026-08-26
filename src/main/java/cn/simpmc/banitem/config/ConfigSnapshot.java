package cn.simpmc.banitem.config;

import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * An immutable, fully parsed configuration generation.  Construct a new
 * snapshot during /banitem reload and replace the old reference atomically.
 */
public final class ConfigSnapshot {
    private final Set<String> whitelistNames;
    private final Set<UUID> whitelistIds;
    private final MaterialRuleSet creativeDropBlacklist;
    private final BannedItemRules bannedItemRules;

    private ConfigSnapshot(Set<String> whitelistNames, Set<UUID> whitelistIds,
                           MaterialRuleSet creativeDropBlacklist, BannedItemRules bannedItemRules) {
        this.whitelistNames = Set.copyOf(whitelistNames);
        this.whitelistIds = Set.copyOf(whitelistIds);
        this.creativeDropBlacklist = creativeDropBlacklist;
        this.bannedItemRules = bannedItemRules;
    }

    public static ConfigSnapshot parse(Collection<String> whitelist,
                                       Collection<String> creativeDropBlacklist,
                                       Collection<String> bannedItemEntries) {
        Objects.requireNonNull(whitelist, "whitelist");
        Set<String> names = new HashSet<>();
        Set<UUID> ids = new HashSet<>();
        for (String entry : whitelist) {
            if (entry == null || entry.isBlank()) {
                continue;
            }
            String trimmed = entry.trim();
            try {
                ids.add(UUID.fromString(trimmed));
            } catch (IllegalArgumentException ignored) {
                names.add(normalizeName(trimmed));
            }
        }
        return new ConfigSnapshot(names, ids, MaterialRuleSet.parse(creativeDropBlacklist),
                BannedItemRules.parse(bannedItemEntries));
    }

    public boolean isWhitelisted(PlayerIdentity player) {
        Objects.requireNonNull(player, "player");
        return whitelistIds.contains(player.uniqueId()) || whitelistNames.contains(normalizeName(player.name()));
    }

    public boolean shouldConfiscate(PlayerIdentity player, ItemDescriptor item) {
        return !isWhitelisted(player) && bannedItemRules.isBanned(item);
    }

    public boolean shouldBlockCreativeDrop(ItemDescriptor item) {
        return creativeDropBlacklist.matches(item);
    }

    public MaterialRuleSet creativeDropBlacklist() {
        return creativeDropBlacklist;
    }

    public BannedItemRules bannedItemRules() {
        return bannedItemRules;
    }

    private static String normalizeName(String name) {
        String result = name.trim().toLowerCase(Locale.ROOT);
        if (result.isEmpty()) {
            throw new IllegalArgumentException("whitelist player name must not be blank");
        }
        return result;
    }
}
