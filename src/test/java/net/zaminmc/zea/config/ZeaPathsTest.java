package net.zaminmc.zea.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The config directory layout and its one-time migration: the historical
 * flat {@code config/zea.json} moves into {@code config/zea/config.json}
 * byte-for-byte, a backup is kept, an existing migrated layout is never
 * touched, and the organized tree exposes every store's path.
 */
class ZeaPathsTest {
        @TempDir
        Path configDir;

        @Test
        void theLayoutResolvesUnderConfigZea() {
                ZeaPaths paths = new ZeaPaths(configDir);
                assertEquals(configDir.resolve("zea"), paths.root());
                assertEquals(configDir.resolve("zea/config.json"), paths.mainConfig());
                assertEquals(configDir.resolve("zea/hud.json"), paths.hudSettings());
        }

        @Test
        void theFlatLegacyConfigMigratesIntoTheDirectory() throws Exception {
                Path legacy = configDir.resolve("zea.json");
                Files.writeString(legacy, "{\"configVersion\": 2, \"chat\": {\"channelTint\": true}}");
                ZeaPaths paths = new ZeaPaths(configDir);
                Path migrated = paths.migrateMainConfig();
                assertEquals(paths.mainConfig(), migrated);
                assertTrue(Files.exists(migrated), "the config moved into config/zea/config.json");
                assertFalse(Files.exists(legacy), "the legacy file moved away");
                assertEquals("{\"configVersion\": 2, \"chat\": {\"channelTint\": true}}",
                                ZeaPaths.readText(migrated),
                                "the migration is byte-for-byte — no value is lost");
                try (var backups = Files.list(paths.backups())) {
                        assertTrue(backups.count() >= 1, "a pre-migration backup is kept");
                }
        }

        @Test
        void anAlreadyMigratedLayoutIsLeftAlone() throws Exception {
                ZeaPaths paths = new ZeaPaths(configDir);
                Files.createDirectories(paths.root());
                Files.writeString(paths.mainConfig(), "{\"migrated\": true}");
                Path legacy = configDir.resolve("zea.json");
                Files.writeString(legacy, "{\"legacy\": true}");
                assertEquals(paths.mainConfig(), paths.migrateMainConfig());
                assertTrue(Files.exists(legacy),
                                "the migration never overwrites a live layout with a stray legacy file");
                assertEquals("{\"migrated\": true}", ZeaPaths.readText(paths.mainConfig()));
        }

        @Test
        void noLegacyFileMeansNothingToDo() throws Exception {
                ZeaPaths paths = new ZeaPaths(configDir);
                assertEquals(paths.mainConfig(), paths.migrateMainConfig());
                assertFalse(Files.exists(paths.backups()));
        }
}
