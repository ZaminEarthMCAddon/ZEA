package net.zaminmc.zea.config;

import net.zaminmc.zea.chat.feeds.Feeds;
import net.zaminmc.zea.chat.feeds.Feed;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZeaConfigTest {
        @Test
        void defaultsFollowTheSpec() {
                ZeaConfig config = new ZeaConfig();
                assertFalse(config.multiSendEnabled(), "multi-channel sending must default to disabled");
                assertEquals(1000, config.dispatchIntervalMillis());
                assertEquals(ZeaConfig.CURRENT_VERSION, config.configVersion());
                assertTrue(config.enabledExtraFeedIds().isEmpty(), "no extra tabs by default");
                assertTrue(config.smoothChatEnabled(), "smooth chat defaults to on");
                assertEquals(500, config.smoothChatDurationMillis(),
                        "Chatting's arrival duration is the default");
                assertTrue(config.smoothScrollingEnabled(), "smooth scrolling defaults to on");
                assertEquals(150, config.smoothScrollingDurationMillis(),
                        "Chatting's scroll glide duration is the default");
                assertTrue(config.historyEnabled(), "persistent history defaults to on");
                assertEquals(7, config.historyRetentionDays());
                assertEquals(7L * 86_400_000L, config.historyRetentionMillis());
                assertEquals(ZeaConfig.DEFAULT_HISTORY_ENTRIES, config.historyMaxEntries());
        }

        @Test
        void smoothChatDurationIsClampedToTheSliderRange() {
                ZeaConfig config = new ZeaConfig();
                config.chat.chatInterface.smoothChatDurationMillis = 0;
                assertEquals(ZeaConfig.MIN_SMOOTH_CHAT_MILLIS, config.smoothChatDurationMillis());
                config.chat.chatInterface.smoothChatDurationMillis = 999_999;
                assertEquals(ZeaConfig.MAX_SMOOTH_CHAT_MILLIS, config.smoothChatDurationMillis());
        }

        @Test
        void dispatchIntervalIsClampedToTheSliderRange() {
                ZeaConfig config = new ZeaConfig();
                config.chat.dispatchIntervalMillis = 0;
                assertEquals(ZeaConfig.MIN_DISPATCH_INTERVAL_MILLIS, config.dispatchIntervalMillis());
                config.chat.dispatchIntervalMillis = 999_999;
                assertEquals(ZeaConfig.MAX_DISPATCH_INTERVAL_MILLIS, config.dispatchIntervalMillis());
        }

        @Test
        void historySizeIsClampedToTheSliderRange() {
                ZeaConfig config = new ZeaConfig();
                config.chat.history.maxEntries = 0;
                assertEquals(ZeaConfig.MIN_HISTORY_ENTRIES, config.historyMaxEntries());
                config.chat.history.maxEntries = 999_999;
                assertEquals(ZeaConfig.MAX_HISTORY_ENTRIES, config.historyMaxEntries());
        }

        @Test
        void clearedRetentionFallsBackToOneWeek() {
                ZeaConfig config = new ZeaConfig();
                config.chat.history.retention = null;
                assertEquals(7, config.historyRetentionDays(), "a hand-cleared field must not crash");
                config.chat.history.retention = ZeaConfig.History.Retention.NINETY_DAYS;
                assertEquals(90, config.historyRetentionDays());
        }

        @Test
        void everyExtraFeedIsEnableable() {
                // A feed added to Feeds without a matching option would become
                // invisible to configuration — pin the mapping to the registry.
                ZeaConfig allOn = new ZeaConfig();
                allOn.chat.tabs.discord = true;
                allOn.chat.tabs.votes = true;
                allOn.chat.tabs.crates = true;
                allOn.chat.tabs.deaths = true;
                allOn.chat.tabs.system = true;
                allOn.chat.tabs.unclassified = true;
                Set<String> enabled = allOn.enabledExtraFeedIds();
                for (Feed extra : Feeds.extraFeeds()) {
                        assertTrue(enabled.contains(extra.id()), "extra feed " + extra.id() + " has no config option");
                }
                assertEquals(Feeds.extraFeeds().size(), enabled.size());
        }

        @Test
        void saveLoadRoundTripPreservesValues(@TempDir Path dir) throws IOException {
                Path file = dir.resolve("zea.json");
                ZeaConfigStore store = new ZeaConfigStore(file);
                ZeaConfig original = store.load();
                original.chat.multiSendEnabled = true;
                original.chat.dispatchIntervalMillis = 2500;
                original.chat.tabs.deaths = true;
                original.chat.history.enabled = false;
                original.chat.history.retention = ZeaConfig.History.Retention.THIRTY_DAYS;
                original.chat.history.maxEntries = 5_000;
                store.save(original);

                ZeaConfig loaded = new ZeaConfigStore(file).load();
                assertTrue(loaded.multiSendEnabled());
                assertEquals(2500, loaded.dispatchIntervalMillis());
                assertTrue(loaded.enabledExtraFeedIds().contains(Feeds.DEATHS.id()));
                assertFalse(loaded.historyEnabled());
                assertEquals(30, loaded.historyRetentionDays());
                assertEquals(5_000, loaded.historyMaxEntries());
        }

        @Test
        void saveNowWithoutSaveActionIsASilentNoOp() {
                ZeaConfig config = new ZeaConfig();
                config.saveNow(); // must not throw
        }

        @Test
        void corruptedFileFallsBackToDefaultsAndIsPreserved(@TempDir Path dir) throws IOException {
                Path file = dir.resolve("zea.json");
                Files.writeString(file, "{ this is not json");
                ZeaConfig config = new ZeaConfigStore(file).load();
                assertFalse(config.multiSendEnabled());
                assertEquals(1000, config.dispatchIntervalMillis());
                assertTrue(Files.exists(dir.resolve("zea.json.corrupted")), "corrupt file must be preserved");
        }

        @Test
        void serializedFormCarriesSchemaButNotRuntimeState(@TempDir Path dir) throws IOException {
                Path file = dir.resolve("zea.json");
                ZeaConfig config = new ZeaConfigStore(file).load();
                config.saveNow(); // goes through the store's save action
                String json = Files.readString(file);
                assertTrue(json.contains("\"configVersion\""));
                assertTrue(json.contains("\"chat\""));
                assertTrue(json.contains("\"tabs\""));
                assertFalse(json.contains("saveRunnables"), "MoulConfig runtime state must not persist");
                assertFalse(json.contains("saveAction"), "runtime callback must not persist");
        }

        @Test
        void versionZeroMigratesAndRewrites(@TempDir Path dir) throws IOException {
                Path file = dir.resolve("zea.json");
                Files.writeString(file, """
                                {"configVersion": 0, "chat": {"multiSendEnabled": true, "dispatchIntervalMillis": 2000}}
                                """);
                ZeaConfig config = new ZeaConfigStore(file).load();
                assertEquals(ZeaConfig.CURRENT_VERSION, config.configVersion());
                assertTrue(config.multiSendEnabled(), "known fields survive a version migration");
                assertEquals(2000, config.dispatchIntervalMillis());
                String rewritten = Files.readString(file);
                assertTrue(rewritten.contains("\"configVersion\": " + ZeaConfig.CURRENT_VERSION),
                                "migrated file is rewritten immediately");
        }

        @Test
        void versionOneChatCategoriesHoistUnderChatAndRewrite(@TempDir Path dir) throws IOException {
                // A dev.25-era file: the chat-related categories sat at the top
                // level. Version 2 nests them under chat — the values themselves
                // must carry over untouched.
                Path file = dir.resolve("zea.json");
                Files.writeString(file, """
                                {
                                  "configVersion": 1,
                                  "chat": {"multiSendEnabled": true, "dispatchIntervalMillis": 2000},
                                  "chatInterface": {"smoothChat": false, "smoothChatDurationMillis": 120},
                                  "tabs": {"discord": true, "deaths": true},
                                  "notifications": {"pmNotifications": false},
                                  "mentions": {"autoRespond": true, "autoResponseText": "hi"},
                                  "history": {"enabled": false, "maxEntries": 4000}
                                }
                                """);
                ZeaConfig config = new ZeaConfigStore(file).load();
                assertEquals(ZeaConfig.CURRENT_VERSION, config.configVersion());
                assertTrue(config.multiSendEnabled());
                assertEquals(2000, config.dispatchIntervalMillis());
                assertFalse(config.smoothChatEnabled(), "relocated category values survive");
                assertEquals(120, config.smoothChatDurationMillis());
                assertTrue(config.enabledExtraFeedIds().contains(Feeds.DISCORD.id()));
                assertTrue(config.enabledExtraFeedIds().contains(Feeds.DEATHS.id()));
                assertFalse(config.pmNotificationsEnabled());
                assertTrue(config.autoRespondEnabled());
                assertEquals("hi", config.autoResponseText());
                assertFalse(config.historyEnabled());
                assertEquals(4000, config.historyMaxEntries());
                String rewritten = Files.readString(file);
                assertTrue(rewritten.contains("\"configVersion\": " + ZeaConfig.CURRENT_VERSION),
                                "migrated file is rewritten immediately");
                // The old top-level members are gone: everything lives under chat.
                var tree = com.google.gson.JsonParser.parseString(rewritten).getAsJsonObject();
                assertFalse(tree.has("tabs"), "the flat layout must not survive the rewrite");
                assertTrue(tree.getAsJsonObject("chat").has("tabs"));
        }

        @Test
        void currentVersionFilesAreNotRewritten(@TempDir Path dir) throws IOException {
                Path file = dir.resolve("zea.json");
                Files.writeString(file, """
                                {"configVersion": %d, "chat": {"multiSendEnabled": true}}
                                """.formatted(ZeaConfig.CURRENT_VERSION));
                ZeaConfig config = new ZeaConfigStore(file).load();
                assertTrue(config.multiSendEnabled());
                assertEquals(ZeaConfig.CURRENT_VERSION, config.configVersion());
                String onDisk = Files.readString(file);
                var tree = com.google.gson.JsonParser.parseString(onDisk).getAsJsonObject();
                assertEquals(ZeaConfig.CURRENT_VERSION,
                                tree.get("configVersion").getAsInt(),
                                "a current-version file must pass through untouched");
        }

        @Test
        void missingFieldsKeepTheirDefaults(@TempDir Path dir) throws IOException {
                Path file = dir.resolve("zea.json");
                Files.writeString(file, "{\"chat\": {\"multiSendEnabled\": true}}");
                ZeaConfig config = new ZeaConfigStore(file).load();
                assertTrue(config.multiSendEnabled());
                assertEquals(1000, config.dispatchIntervalMillis(), "omitted fields fall back to defaults");
                assertTrue(config.enabledExtraFeedIds().isEmpty());
                assertTrue(config.historyEnabled(), "a pre-history config file gets history defaults");
                assertEquals(ZeaConfig.DEFAULT_HISTORY_ENTRIES, config.historyMaxEntries());
        }
}
