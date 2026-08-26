package cn.simpmc.banitem.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PluginConfigLoaderTest {
    private static final String VALID_CREATIVE = "creative-drop:\n  enabled: true\n  blacklist: []\n";

    @TempDir
    Path dataFolder;

    @Test
    void rejectsMissingBannedItemsPath() throws IOException {
        writeConfig("whitelist: {}\n" + VALID_CREATIVE);
        writeBannedItems("wrong-key: []\n");

        assertThrows(IllegalArgumentException.class, this::load);
    }

    @Test
    void rejectsNonListConfigurationValues() throws IOException {
        writeConfig("creative-drop:\n  enabled: true\n  blacklist: BEDROCK\n");
        writeBannedItems("banned-items: []\n");

        assertThrows(IllegalArgumentException.class, this::load);
    }

    @Test
    void rejectsScalarWhitelistSection() throws IOException {
        writeConfig("whitelist: invalid\n" + VALID_CREATIVE);
        writeBannedItems("banned-items: []\n");

        assertThrows(IllegalArgumentException.class, this::load);
    }

    @Test
    void rejectsUnknownMinecraftMaterial() throws IOException {
        writeConfig("whitelist: {}\n" + VALID_CREATIVE);
        writeBannedItems("banned-items:\n  - STOEN\n");

        assertThrows(IllegalArgumentException.class, this::load);
    }

    @Test
    void rejectsInvalidUuidWithoutTreatingItAsAName() throws IOException {
        writeConfig("whitelist:\n  uuids:\n    - not-a-uuid\n" + VALID_CREATIVE);
        writeBannedItems("banned-items: []\n");

        assertThrows(IllegalArgumentException.class, this::load);
    }

    @Test
    void rejectsInvalidUsername() throws IOException {
        writeConfig("whitelist:\n  users:\n    - invalid-name-with-dash\n" + VALID_CREATIVE);
        writeBannedItems("banned-items: []\n");

        assertThrows(IllegalArgumentException.class, this::load);
    }

    @Test
    void acceptsExplicitEmptyBannedListAndValidRules() throws IOException {
        UUID trusted = UUID.randomUUID();
        writeConfig("whitelist:\n  users: [Trusted_User]\n  uuids: [" + trusted + "]\n"
                + "creative-drop:\n  enabled: true\n"
                + "  blacklist: [minecraft:bedrock, custom:wand, minecraft:*]\n");
        writeBannedItems("banned-items: []\n");

        PluginSettings settings = load();

        assertFalse(settings.policy().bannedItemRules().isBanned(ItemDescriptor.of("BEDROCK")));
        assertTrue(settings.policy().shouldBlockCreativeDrop(ItemDescriptor.of("STONE")));
        assertTrue(settings.policy().isWhitelisted(new PlayerIdentity(trusted, "different_name")));
    }

    @Test
    void rejectsMissingOrInvalidCreativeDropConfiguration() throws IOException {
        writeBannedItems("banned-items: []\n");

        writeConfig("whitelist: {}\n");
        assertThrows(IllegalArgumentException.class, this::load);

        writeConfig("creative-drop:\n  enabled: not-a-boolean\n  blacklist: []\n");
        assertThrows(IllegalArgumentException.class, this::load);

        writeConfig("creative-drop:\n  enabled: true\n");
        assertThrows(IllegalArgumentException.class, this::load);
    }

    @Test
    void rejectsNonCanonicalUuid() throws IOException {
        writeConfig("whitelist:\n  uuids: [0-0-0-0-0]\n" + VALID_CREATIVE);
        writeBannedItems("banned-items: []\n");

        assertThrows(IllegalArgumentException.class, this::load);
    }

    private PluginSettings load() throws IOException {
        try {
            return PluginConfigLoader.load(dataFolder.toFile());
        } catch (IOException exception) {
            throw exception;
        } catch (Exception exception) {
            if (exception instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new AssertionError(exception);
        }
    }

    private void writeConfig(String content) throws IOException {
        Files.writeString(dataFolder.resolve("config.yml"), content);
    }

    private void writeBannedItems(String content) throws IOException {
        Files.writeString(dataFolder.resolve("banned-items.yml"), content);
    }
}
