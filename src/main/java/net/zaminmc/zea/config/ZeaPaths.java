package net.zaminmc.zea.config;

import net.zaminmc.zea.persistence.SafeFileWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;

/**
 * ZEA's configuration directory layout — the authoritative local
 * persistence location, {@code config/zea/}, with user preferences,
 * HUD settings, discovery state and market data as separate files with
 * separate audiences:
 *
 * <pre>
 * config/
 * └── zea/
 *     ├── config.json              (user preferences — the MoulConfig tree)
 *     ├── hud.json                 (HUD element visibility, anchors, offsets)
 *     ├── buttons.json             (inventory buttons, existing format kept)
 *     ├── slotlocks.json           (slot locks, existing format kept)
 *     ├── history/                 (per-server chat history, existing)
 *     ├── capture/                 (message capture output, existing)
 *     ├── market/
 *     │   ├── market.json          (the market index: shops and observations)
 *     │   ├── market-submissions.json (the ZEA-REPO contribution queue)
 *     │   └── shared-shops.json    (the last accepted ZEA-REPO dataset)
 *     └── backups/                 (pre-migration copies, kept forever)
 * </pre>
 *
 * <p>The historical layout stored the main config flat at
 * {@code config/zea.json}; {@link #migrateMainConfig} moves it into the
 * directory exactly once, keeping a copy under {@code backups/} before
 * the move, so an interrupted migration or a bad guess is always
 * recoverable. Side stores that already lived in {@code config/zea/}
 * keep their paths and formats — nothing else migrates, and preferences
 * survive every upgrade untouched by default (writes are atomic, corrupt
 * files are preserved, and saves only happen when something changed).
 */
public final class ZeaPaths {
        public static final Logger LOGGER = LoggerFactory.getLogger("ZEA/Config");

        private final Path configDir;
        private final Path root;

        public ZeaPaths(Path configDir) {
                this.configDir = configDir;
                this.root = configDir.resolve("zea");
        }

        /** The {@code config/zea/} root. */
        public Path root() {
                return root;
        }

        /** The main user-preferences file. */
        public Path mainConfig() {
                return root.resolve("config.json");
        }

        /** The HUD manager's settings. */
        public Path hudSettings() {
                return root.resolve("hud.json");
        }

        /** The inventory-button store (existing format and path). */
        public Path buttons() {
                return root.resolve("buttons.json");
        }

        /** The slot-lock store (existing format and path). */
        public Path slotLocks() {
                return root.resolve("slotlocks.json");
        }

        /** The per-server chat history directory (existing). */
        public Path historyDir() {
                return root.resolve("history");
        }

        /** The message-capture output directory (existing). */
        public Path captureDir() {
                return root.resolve("capture");
        }

        /** The shared shop dataset cache: the last accepted ZEA-REPO snapshot. */
        public Path sharedDataset() {
                return root.resolve("market/shared-shops.json");
        }

        /** Where pre-migration copies land. */
        public Path backups() {
                return root.resolve("backups");
        }

        /**
         * The main config file to load, migrating the historical flat
         * {@code config/zea.json} into {@code config/zea/config.json} when
         * that move has not happened yet. Migration keeps the old file's
         * bytes: a timestamped copy lands in {@code backups/} first, then
         * the file moves (atomic), so values carry over byte-for-byte.
         */
        public Path migrateMainConfig() {
                Path legacy = configDir.resolve("zea.json");
                Path target = mainConfig();
                if (!Files.exists(legacy) || Files.exists(target)) {
                        return target;
                }
                try {
                        Files.createDirectories(backups());
                        String stamp = Instant.now().toString().replace(":", "-");
                        Path backup = backups().resolve("zea.json.pre-layout-migration." + stamp);
                        byte[] bytes = Files.readAllBytes(legacy);
                        SafeFileWriter.writeAtomically(backup, bytes);
                        Files.createDirectories(root);
                        // The move itself is the migration: same bytes, new home.
                        try {
                                Files.move(legacy, target, StandardCopyOption.ATOMIC_MOVE);
                        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                                Files.move(legacy, target, StandardCopyOption.REPLACE_EXISTING);
                        }
                        LOGGER.info("Migrated main config {} → {} (backup at {})",
                                        legacy.getFileName(), target, backup);
                } catch (IOException e) {
                        LOGGER.warn("Config migration failed ({}); loading from the legacy path instead",
                                        e.toString());
                        return legacy;
                }
                return target;
        }

        /** The legacy file's bytes, for tests that stage a pre-migration state. */
        public static byte[] readAll(Path file) throws IOException {
                return Files.readAllBytes(file);
        }

        /** Writes a file atomically — the test helper for staging layouts. */
        public static void writeAll(Path file, byte[] bytes) throws IOException {
                SafeFileWriter.writeAtomically(file, bytes);
        }

        /** Reads a file as UTF-8 text — the test helper for asserting migrations. */
        public static String readText(Path file) throws IOException {
                return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        }
}
