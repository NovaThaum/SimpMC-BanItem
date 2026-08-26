package cn.simpmc.banitem.config;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Parsed content of the separately stored prohibited-items file.
 * Lines may be plain values or YAML list entries; blank lines and # comments
 * are ignored.  A matching item is to be confiscated by the caller.
 */
public final class BannedItemRules {
    private final MaterialRuleSet materialRules;

    private BannedItemRules(MaterialRuleSet materialRules) {
        this.materialRules = materialRules;
    }

    public static BannedItemRules parse(Collection<String> lines) {
        Objects.requireNonNull(lines, "lines");
        return new BannedItemRules(MaterialRuleSet.parse(lines));
    }

    public boolean isBanned(ItemDescriptor item) {
        return materialRules.matches(item);
    }

    public List<String> entries() {
        return materialRules.values();
    }
}
