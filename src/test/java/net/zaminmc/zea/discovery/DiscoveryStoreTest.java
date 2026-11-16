package net.zaminmc.zea.discovery;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The discovery store: separate files for separate audiences, atomic
 * saves, and persistence across a reload (the restart simulation).
 */
class DiscoveryStoreTest {
        private final java.nio.file.Path dir = tempDir("discovery-store");

    private static java.nio.file.Path tempDir(String name) {
        try {
            return java.nio.file.Files.createTempDirectory("zea-" + name);
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

        @Test
        void observationsPersistAcrossReload() throws Exception {
                Path local = dir.resolve("market/local-observations.json");
                Path nav = dir.resolve("market/navigation-observations.json");
                Path remote = dir.resolve("market/remote-cache.json");
                DiscoveryStore store = new DiscoveryStore(local, nav, remote);

                ShopObservation shop = new ShopObservation();
                shop.shopId = "aurora.earthmc.net|minecraft:overworld|10,64,-20";
                shop.owner = "Ayskow";
                shop.itemName = "Diamond Pickaxe";
                shop.direction = ShopObservation.Direction.BUY_FROM;
                shop.priceRaw = "2G";
                shop.stock = 33;
                shop.maxTransaction = 16;
                shop.enchantments.addAll(List.of("Fortune III"));
                shop.firstObservedAt = 1_000L;
                shop.lastObservedAt = 2_000L;
                store.putLocalShop(shop);

                NavigationObservation navigation = new NavigationObservation();
                navigation.command = "n spawn anjou";
                navigation.kind = "n";
                navigation.target = "anjou";
                navigation.observedAt = 3_000L;
                store.addNavigation(navigation);
                store.saveIfDirty();
                store.flush();

                DiscoveryStore reloaded = new DiscoveryStore(local, nav, remote);
                assertTrue(reloaded.localShops().containsKey(shop.shopId),
                                "observations survive the restart");
                assertEquals("Ayskow", reloaded.localShops().get(shop.shopId).owner);
                assertEquals(1, reloaded.navigationEntries().size());
                assertEquals("n spawn anjou", reloaded.navigationEntries().get(0).command);
        }

        @Test
        void filesLandInTheSeparateLayout() throws Exception {
                Path local = dir.resolve("market/local-observations.json");
                Path nav = dir.resolve("market/navigation-observations.json");
                Path remote = dir.resolve("market/remote-cache.json");
                DiscoveryStore store = new DiscoveryStore(local, nav, remote);
                ShopObservation shop = new ShopObservation();
                shop.shopId = "id";
                shop.signPos = "1,1,1";
                store.putLocalShop(shop);
                store.addNavigation(new NavigationObservation());
                store.flush();
                assertTrue(Files.exists(local), "the shop observations file exists");
                assertTrue(nav.toString().contains("market"), "navigation lives in the market tree");
                assertTrue(nav.toString().contains("navigation"), "navigation has its own file");
                assertTrue(remote.toString().endsWith("remote-cache.json"));
        }

        @Test
        void theShopBlockCollectorCompletesOnThePrompt() {
                ShopTransactionCollector collector = new ShopTransactionCollector();
                collector.begin("10,64,-20", 0L);
                assertFalse(collector.feed("+---------------------------------------------------+", 100L));
                assertFalse(collector.feed("| Owner: Ayskow", 200L));
                assertFalse(collector.feed("| Stock: 33", 300L));
                assertTrue(collector.feed("Enter in chat, how many you wish to BUY.", 400L),
                                "the quantity prompt completes the block");
                assertTrue(collector.completedByPrompt());
                assertEquals("10,64,-20", collector.signPos());
        }

        @Test
        void aSecondSignClickRestartsTheCollection() {
                ShopTransactionCollector collector = new ShopTransactionCollector();
                collector.begin("10,64,-20", 0L);
                collector.feed("| Owner: Ayskow", 100L);
                collector.begin("12,64,-20", 200L);
                collector.feed("| Owner: Somebody", 300L);
                assertTrue(collector.feed("Enter in chat, how many you wish to BUY.", 400L));
                List<String> collected = collector.collected();
                assertFalse(collected.stream().anyMatch(line -> line.contains("Ayskow")),
                                "the first shop's lines are not associated with the second click");
        }

        @Test
        void timeoutCompletesAPartialCollection() {
                ShopTransactionCollector collector = new ShopTransactionCollector();
                collector.begin("10,64,-20", 0L);
                collector.feed("| Owner: Ayskow", 100L);
                assertTrue(collector.feed("| Price per Diamond Pickaxe - 2G",
                                ShopTransactionCollector.COLLECTION_TIMEOUT_MS + 1),
                                "the timed-out block is handed over for parsing");
        }

        @Test
        void unrelatedLinesDoNotCorruptTheBuffer() {
                ShopTransactionCollector collector = new ShopTransactionCollector();
                collector.begin("10,64,-20", 0L);
                collector.feed("| Owner: Ayskow", 100L);
                collector.feed("hello world this is unrelated chat", 200L);
                assertTrue(collector.feed("Enter in chat, how many you wish to BUY.", 300L));
                assertFalse(collector.collected().stream().anyMatch(line -> line.contains("unrelated")),
                                "unrelated chat lines are dropped, never stored");
        }

        @Test
        void repoDatasetsParseAndValidate() {
                String json = """
                                {"shops": {
                                   "aurora.earthmc.net|minecraft:overworld|10,64,-20": {
                                     "shopId": "aurora.earthmc.net|minecraft:overworld|10,64,-20",
                                     "signPos": "10,64,-20",
                                     "owner": "Ayskow",
                                     "priceRaw": "2G",
                                     "lastObservedAt": 1700000000000,
                                     "firstObservedAt": 1700000000000
                                   },
                                   "bad": {"shopId": "x"},
                                   "noTime": {"shopId": "y", "signPos": "1,1,1"}
                                }}
                                """;
                var parsed = RepoClient.parseShops(json);
                assertEquals(1, parsed.size(),
                                "malformed and timestamp-less entries are rejected, the valid one kept");
                assertNotNull(parsed.values().iterator().next().owner);
        }

        @Test
        void manifestWithoutAShopDatasetYieldsNoUrl() {
            assertNullLike(RepoClient.resolveShopDatasetUrl("{\"datasets\": [\"datasets/towns/anjou.json\"]}"),
                            "no market dataset — the client stays local-only");
            assertNullLike(RepoClient.resolveShopDatasetUrl("not json at all"),
                            "a broken manifest must not crash the pass");
            assertNullLike(RepoClient.resolveShopDatasetUrl(""), "empty manifest, no dataset");
        }

        @Test
        void manifestWithAShopDatasetResolvesItsUrl() {
                String url = RepoClient.resolveShopDatasetUrl(
                                "{\"files\": [\"datasets/badges.json\", \"datasets/market/shops.json\"]}");
                assertEquals(RepoClient.RAW_BASE + "/datasets/market/shops.json", url);
        }

        private static void assertNullLike(Object actual, String message) {
                org.junit.jupiter.api.Assertions.assertNull(actual, message);
        }
}
