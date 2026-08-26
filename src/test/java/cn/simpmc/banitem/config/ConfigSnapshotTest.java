package cn.simpmc.banitem.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ConfigSnapshotTest {
    @Test
    void confiscationHonoursCaseInsensitiveNameAndUuidWhitelist() {
        UUID id = UUID.randomUUID();
        ConfigSnapshot config = ConfigSnapshot.parse(
                List.of("TrustedPlayer", id.toString()), List.of(), List.of("DIAMOND_SWORD"));

        assertFalse(config.shouldConfiscate(new PlayerIdentity(UUID.randomUUID(), "trustedplayer"),
                ItemDescriptor.of("minecraft:diamond_sword")));
        assertFalse(config.shouldConfiscate(new PlayerIdentity(id, "other"), ItemDescriptor.of("DIAMOND_SWORD")));
        assertTrue(config.shouldConfiscate(new PlayerIdentity(UUID.randomUUID(), "other"),
                ItemDescriptor.of("diamond_sword")));
    }

    @Test
    void wildcardRulesMatchOnlyTheirScope() {
        ConfigSnapshot config = ConfigSnapshot.parse(List.of(), List.of("minecraft:*"),
                List.of("custom:forbidden", "minecraft:command_block"));

        assertTrue(config.shouldBlockCreativeDrop(ItemDescriptor.of("STONE")));
        assertFalse(config.shouldBlockCreativeDrop(ItemDescriptor.of("custom:stone")));
        assertTrue(config.shouldConfiscate(new PlayerIdentity(UUID.randomUUID(), "player"),
                ItemDescriptor.of("custom:forbidden")));
        assertFalse(config.shouldConfiscate(new PlayerIdentity(UUID.randomUUID(), "player"),
                ItemDescriptor.of("custom:allowed")));
    }

    @Test
    void parserAcceptsYamlListLinesAndRejectsMalformedPatterns() {
        BannedItemRules rules = BannedItemRules.parse(List.of("# a comment", "- BEDROCK", "  "));
        assertTrue(rules.isBanned(ItemDescriptor.of("minecraft:bedrock")));
        assertThrows(IllegalArgumentException.class, () -> MaterialPattern.parse("minecraft:bad value"));
    }
}
