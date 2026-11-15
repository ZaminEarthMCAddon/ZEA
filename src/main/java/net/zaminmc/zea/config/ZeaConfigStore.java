package net.zaminmc.zea.config;

import com.google.gson.JsonObject;
import net.zaminmc.zea.persistence.JsonStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Loads and saves {@link ZeaConfig} through the safe-write protocol: the
 * file is serialized and verified in memory, then atomically replaced. A
 * corrupt file is preserved as {@code *.corrupted} and defaults are used
 * instead — the editor can never crash on a damaged config, and a failed
 * save never partially overwrites it.
 */
public final class ZeaConfigStore {
        private static final Logger LOGGER = LoggerFactory.getLogger(ZeaConfigStore.class);

        /**
         * The category objects version 1 kept at the top level that version
         * 2 nests under {@code chat} (dev.26: chat-related settings grouped
         * under one Chat category). Migration moves the whole objects —
         * every value inside carries over unchanged.
         */
        private static final List<String> V1_TOP_LEVEL_CHAT_CATEGORIES =
                        List.of("tabs", "chatInterface", "notifications", "mentions", "history");

        private final Path file;
        private final JsonStore json = new JsonStore();

        public ZeaConfigStore(Path file) {
                this.file = file;
        }

        /** Loads the configuration, applying migrations for older schema versions. */
        public ZeaConfig load() {
                boolean[] treeMigrated = {false};
                ZeaConfig config = json.load(file, ZeaConfig.class, ZeaConfig::new,
                                tree -> migrateTree(tree, treeMigrated));
                if (config.configVersion() < ZeaConfig.CURRENT_VERSION) {
                        migrate(config);
                        config.markMigrated();
                        treeMigrated[0] = true;
                }
                if (treeMigrated[0]) {
                        save(config);
                }
                config.setSaveAction(() -> save(config));
                return config;
        }

        /**
         * Saves the configuration. Failures are logged, not thrown — the MoulConfig
         * editor calls {@link ZeaConfig#saveNow()} from its own UI callbacks.
         */
        public void save(ZeaConfig config) {
                try {
                        json.save(file, config);
                } catch (IOException e) {
                        LOGGER.warn("Could not save configuration {}", file, e);
                }
        }

        /**
         * Tree-level migrations, one step per schema change. Runs on the
         * raw JSON before deserialization, so relocations of whole objects
         * survive (an object-level migration could not see them — Gson
         * drops unknown fields). A file already at the current version is
         * returned untouched.
         */
        private JsonObject migrateTree(JsonObject tree, boolean[] migrated) {
                int version = versionOf(tree);
                if (version >= ZeaConfig.CURRENT_VERSION) {
                        return tree;
                }
                LOGGER.info("Migrating configuration from version {}", version);
                if (version < 2) {
                        migrateV1ToV2(tree);
                }
                if (version < 3) {
                        migrateV2ToV3(tree);
                }
                tree.addProperty("configVersion", ZeaConfig.CURRENT_VERSION);
                migrated[0] = true;
                return tree;
        }

        /** Version 1 → 2: hoist the chat-related categories under {@code chat}. */
        private static void migrateV1ToV2(JsonObject tree) {
                JsonObject chat = tree.has("chat") && tree.get("chat").isJsonObject()
                                ? tree.getAsJsonObject("chat")
                                : new JsonObject();
                for (String name : V1_TOP_LEVEL_CHAT_CATEGORIES) {
                        if (tree.has(name)) {
                                chat.add(name, tree.remove(name));
                        }
                }
                tree.add("chat", chat);
        }

        /**
         * Version 2 → 3: the General tab is gone and Global is the
         * aggregate, so its single "hide server broadcasts in General"
         * switch becomes the Global tab's per-type toggles. A player who
         * had the switch on (hide votes, crate wins, deaths and system
         * lines in the aggregate) keeps exactly that: those four kinds
         * turn off on Global and everything else stays on. A player who
         * had it off keeps the every-message default — nothing to write.
         */
        private static void migrateV2ToV3(JsonObject tree) {
                JsonObject chat = tree.has("chat") && tree.get("chat").isJsonObject()
                                ? tree.getAsJsonObject("chat")
                                : null;
                if (chat == null || !chat.has("hideServerBroadcastsInGeneral")) {
                        return;
                }
                if (chat.remove("hideServerBroadcastsInGeneral").getAsBoolean()) {
                        JsonObject globalTab = new JsonObject();
                        globalTab.addProperty("votes", false);
                        globalTab.addProperty("crateWins", false);
                        globalTab.addProperty("deaths", false);
                        globalTab.addProperty("system", false);
                        chat.add("globalTab", globalTab);
                }
        }

        /** The schema version a tree declares; files before versioning read as 0. */
        private static int versionOf(JsonObject tree) {
                return tree.has("configVersion") && tree.get("configVersion").isJsonPrimitive()
                                ? tree.get("configVersion").getAsInt()
                                : 0;
        }

        /**
         * Object-level migrations, one case per source version. Version 1
         * needed no value transforms — its schema change is a relocation,
         * handled in the tree above — so there is nothing to migrate yet.
         */
        private static void migrate(ZeaConfig config) {
                LOGGER.info("Migrating configuration values from version {}", config.configVersion());
                // switch (config.configVersion()) { case 1: ... } — added when needed.
        }
}
