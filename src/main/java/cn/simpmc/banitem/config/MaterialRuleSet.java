package cn.simpmc.banitem.config;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

/** Immutable OR-set of material patterns. */
public final class MaterialRuleSet {
    private final List<MaterialPattern> patterns;

    private MaterialRuleSet(List<MaterialPattern> patterns) {
        this.patterns = List.copyOf(patterns);
    }

    public static MaterialRuleSet parse(Collection<String> lines) {
        Objects.requireNonNull(lines, "lines");
        return new MaterialRuleSet(lines.stream()
                .filter(Objects::nonNull)
                .map(MaterialRuleSet::stripYamlListPrefix)
                .filter(line -> !line.isBlank() && !line.stripLeading().startsWith("#"))
                .map(MaterialPattern::parse)
                .toList());
    }

    public boolean matches(ItemDescriptor item) {
        Objects.requireNonNull(item, "item");
        return patterns.stream().anyMatch(pattern -> pattern.matches(item));
    }

    public List<String> values() {
        return patterns.stream().map(MaterialPattern::value).toList();
    }

    public boolean isEmpty() {
        return patterns.isEmpty();
    }

    private static String stripYamlListPrefix(String line) {
        String stripped = line.trim();
        return stripped.startsWith("- ") ? stripped.substring(2).trim() : stripped;
    }
}
