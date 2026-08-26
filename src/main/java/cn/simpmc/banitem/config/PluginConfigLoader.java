package cn.simpmc.banitem.config;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

/** Reads fresh YAML objects so reload never mutates the live configuration. */
public final class PluginConfigLoader {
    private static final String MINECRAFT_NAMESPACE = "minecraft:";
    private static final Pattern PLAYER_NAME_PATTERN = Pattern.compile("[A-Za-z0-9_]{3,16}");

    private PluginConfigLoader() {
    }

    public static PluginSettings load(File dataFolder) throws Exception {
        Objects.requireNonNull(dataFolder, "dataFolder");
        YamlConfiguration main = loadYaml(new File(dataFolder, "config.yml"));
        YamlConfiguration banned = loadYaml(new File(dataFolder, "banned-items.yml"));

        requireSection(main, "whitelist", false);
        requireSection(main, "creative-drop", true);
        requireSection(main, "messages", false);
        List<String> whitelist = readWhitelist(main);
        boolean creativeDropEnabled = readRequiredBoolean(main, "creative-drop.enabled");
        List<String> creativeDropBlacklist = readStringList(main, "creative-drop.blacklist", true);
        List<String> bannedItems = readStringList(banned, "banned-items", true);

        ConfigSnapshot policy = ConfigSnapshot.parse(
                whitelist,
                creativeDropBlacklist,
                bannedItems);
        validateMinecraftMaterials(policy.creativeDropBlacklist(), "creative-drop.blacklist");
        validateMinecraftMaterials(policy.bannedItemRules().entries(), "banned-items");
        PluginSettings.Messages messages = new PluginSettings.Messages(
                message(main, "messages.confiscated", "&c违规物品已没收: &f{item} &7x{amount}"),
                message(main, "messages.creative-drop-blocked", "&c创造模式不能丢弃该物品: &f{item}"),
                message(main, "messages.reloaded", "&aSimpMC-BanItem 配置已重载。"),
                message(main, "messages.reload-failed", "&c配置重载失败，请查看控制台日志。"),
                message(main, "messages.no-permission", "&c你没有执行此命令的权限。"),
                message(main, "messages.usage", "&e用法: /banitem reload"));
        return new PluginSettings(policy, creativeDropEnabled, messages);
    }

    private static YamlConfiguration loadYaml(File file) throws Exception {
        if (!file.isFile()) {
            throw new IllegalStateException("Missing configuration file: " + file.getName());
        }
        YamlConfiguration configuration = new YamlConfiguration();
        try {
            configuration.load(file);
        } catch (InvalidConfigurationException exception) {
            throw new IllegalArgumentException("Invalid YAML in " + file.getName() + ": " + exception.getMessage(), exception);
        }
        return configuration;
    }

    private static List<String> readWhitelist(YamlConfiguration config) {
        List<String> users = readStringList(config, "whitelist.users", false);
        List<String> uuids = readStringList(config, "whitelist.uuids", false);
        List<String> result = new ArrayList<>(users.size() + uuids.size());
        for (String user : users) {
            if (!PLAYER_NAME_PATTERN.matcher(user).matches()) {
                throw new IllegalArgumentException("Invalid Minecraft player name in whitelist.users: " + user);
            }
            result.add(user);
        }
        for (String uuid : uuids) {
            try {
                UUID parsed = UUID.fromString(uuid);
                if (!parsed.toString().equalsIgnoreCase(uuid)) {
                    throw new IllegalArgumentException("UUID is not in canonical form");
                }
                result.add(parsed.toString());
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("Invalid UUID in whitelist.uuids: " + uuid, exception);
            }
        }
        return result;
    }

    private static void requireSection(YamlConfiguration config, String path, boolean required) {
        if (!config.contains(path)) {
            if (required) {
                throw new IllegalArgumentException("Missing required YAML section: " + path);
            }
            return;
        }
        if (!config.isConfigurationSection(path)) {
            throw new IllegalArgumentException("Expected a YAML section at " + path);
        }
    }

    private static boolean readRequiredBoolean(YamlConfiguration config, String path) {
        if (!config.contains(path)) {
            throw new IllegalArgumentException("Missing required boolean: " + path);
        }
        if (!config.isBoolean(path)) {
            throw new IllegalArgumentException("Expected a boolean at " + path);
        }
        return config.getBoolean(path);
    }

    /**
     * Accepts a missing optional path as an empty list, but never silently
     * coerces a scalar/map into a list.  Required paths must be explicitly
     * present; an explicit empty YAML list remains a valid configuration.
     */
    private static List<String> readStringList(YamlConfiguration config, String path, boolean required) {
        if (!config.contains(path)) {
            if (required) {
                throw new IllegalArgumentException("Missing required list: " + path);
            }
            return List.of();
        }
        if (!config.isList(path)) {
            throw new IllegalArgumentException("Expected a YAML list at " + path);
        }
        List<?> values = config.getList(path);
        List<String> result = new ArrayList<>(values.size());
        for (Object value : values) {
            if (!(value instanceof String string)) {
                throw new IllegalArgumentException("Expected only strings in " + path);
            }
            if (string.isBlank()) {
                throw new IllegalArgumentException("Blank entry in " + path);
            }
            result.add(string.trim());
        }
        return List.copyOf(result);
    }

    private static void validateMinecraftMaterials(MaterialRuleSet rules, String path) {
        validateMinecraftMaterials(rules.values(), path);
    }

    private static void validateMinecraftMaterials(List<String> patterns, String path) {
        for (String pattern : patterns) {
            if (!pattern.startsWith(MINECRAFT_NAMESPACE) || pattern.contains("*")) {
                continue;
            }
            String enumName = pattern.substring(MINECRAFT_NAMESPACE.length()).toUpperCase(Locale.ROOT);
            if (Material.getMaterial(enumName) == null) {
                throw new IllegalArgumentException("Unknown Minecraft material in " + path + ": " + pattern);
            }
        }
    }

    private static String message(YamlConfiguration config, String path, String fallback) {
        String value = config.getString(path, fallback);
        return value == null ? fallback : value;
    }
}
