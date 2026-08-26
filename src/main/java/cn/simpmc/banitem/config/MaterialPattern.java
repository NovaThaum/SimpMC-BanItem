package cn.simpmc.banitem.config;

import java.util.Locale;
import java.util.Objects;

/**
 * Matches a material id without loading Bukkit classes.  Supported patterns
 * are an exact material ({@code diamond_sword} or {@code minecraft:diamond_sword}),
 * one namespace ({@code minecraft:*}), and {@code *}.
 */
public final class MaterialPattern {
    private static final String DEFAULT_NAMESPACE = "minecraft";

    private final String source;
    private final String namespace;
    private final String key;

    private MaterialPattern(String source, String namespace, String key) {
        this.source = source;
        this.namespace = namespace;
        this.key = key;
    }

    public static MaterialPattern parse(String raw) {
        Objects.requireNonNull(raw, "raw");
        String value = raw.trim().toLowerCase(Locale.ROOT);
        if (value.equals("*")) {
            return new MaterialPattern("*", "*", "*");
        }
        int separator = value.indexOf(':');
        if (separator != value.lastIndexOf(':')) {
            throw new IllegalArgumentException("material pattern may contain at most one ':'");
        }
        String namespace = separator < 0 ? DEFAULT_NAMESPACE : value.substring(0, separator);
        String key = separator < 0 ? value : value.substring(separator + 1);
        if (!isPart(namespace, false) || !isPart(key, true)) {
            throw new IllegalArgumentException("invalid material pattern '" + raw + "'");
        }
        return new MaterialPattern(namespace + ':' + key, namespace, key);
    }

    public boolean matches(ItemDescriptor item) {
        return matches(item.material());
    }

    public boolean matches(String material) {
        String normalized = normalizeMaterial(material);
        int separator = normalized.indexOf(':');
        String candidateNamespace = normalized.substring(0, separator);
        String candidateKey = normalized.substring(separator + 1);
        return (namespace.equals("*") || namespace.equals(candidateNamespace))
                && (key.equals("*") || key.equals(candidateKey));
    }

    public String value() {
        return source;
    }

    /** Normalizes Bukkit-style names such as DIAMOND_SWORD to minecraft:diamond_sword. */
    public static String normalizeMaterial(String raw) {
        Objects.requireNonNull(raw, "material");
        String value = raw.trim().toLowerCase(Locale.ROOT);
        if (value.isEmpty() || value.equals("*")) {
            throw new IllegalArgumentException("invalid material '" + raw + "'");
        }
        int separator = value.indexOf(':');
        if (separator != value.lastIndexOf(':')) {
            throw new IllegalArgumentException("material may contain at most one ':'");
        }
        String namespace = separator < 0 ? DEFAULT_NAMESPACE : value.substring(0, separator);
        String key = separator < 0 ? value : value.substring(separator + 1);
        if (!isPart(namespace, false) || !isPart(key, false)) {
            throw new IllegalArgumentException("invalid material '" + raw + "'");
        }
        return namespace + ':' + key;
    }

    private static boolean isPart(String value, boolean permitWildcard) {
        if (value.isEmpty()) {
            return false;
        }
        if (permitWildcard && value.equals("*")) {
            return true;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (!(c >= 'a' && c <= 'z') && !(c >= '0' && c <= '9') && c != '_' && c != '-' && c != '.') {
                return false;
            }
        }
        return true;
    }
}
