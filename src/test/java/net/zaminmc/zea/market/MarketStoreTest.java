package net.zaminmc.zea.market;

import net.minecraft.core.BlockPos;
import net.zaminmc.zea.VanillaBootstrap;
import net.zaminmc.zea.market.model.ItemFingerprint;
import net.zaminmc.zea.market.model.PhysicalShop;
import net.zaminmc.zea.market.model.PriceScope;
import net.zaminmc.zea.market.model.ShopDirection;
import net.zaminmc.zea.market.model.ShopObservation;
import net.zaminmc.zea.market.model.SourceType;
import net.zaminmc.zea.market.model.StockUnit;
import net.zaminmc.zea.market.store.MarketStore;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarketStoreTest {
        private static final Instant T = Instant.ofEpochMilli(1_000_000L);

        @BeforeAll
        static void boot() {
                VanillaBootstrap.ensure();
        }

        private static PhysicalShop shop() {
                return new PhysicalShop("earthmc.net|minecraft_overworld|10,64,-20", "earthmc.net",
                                "minecraft_overworld", new BlockPos(10, 64, -20), new BlockPos(10, 64, -19),
                                "Abasing", null, null, T, T);
        }

        private static ShopObservation observation(BigDecimal price, Instant at) {
                return new ShopObservation("earthmc.net|minecraft_overworld|10,64,-20",
                                ShopDirection.SELLING,
                                new ItemFingerprint("minecraft:diamond_pickaxe|{}", ItemFingerprint.Fidelity.EXACT),
                                "Diamond Pickaxe", price, PriceScope.PER_BULK, 7, "78", StockUnit.UNKNOWN,
                                null, 64, null, null, null, true, List.of(), SourceType.SERVER_RESPONSE, at);
        }

        private static ShopObservation manual(BigDecimal price, Instant at) {
                return new ShopObservation("earthmc.net|minecraft_overworld|10,64,-20",
                                ShopDirection.SELLING,
                                new ItemFingerprint("minecraft:diamond_pickaxe|{}", ItemFingerprint.Fidelity.EXACT),
                                "Diamond Pickaxe", price, PriceScope.PER_ITEM, null, "78", StockUnit.UNKNOWN,
                                null, null, null, null, null, false, List.of(), SourceType.MANUAL, at);
        }

        @Test
        void roundTripsShopsAndObservationsThroughTheFile(@TempDir Path dir) throws IOException {
                Path file = dir.resolve("market.json");
                MarketStore store = new MarketStore(file);
                MarketData data = new MarketData();
                data.record(shop(), observation(new BigDecimal("1"), T));
                store.save(MarketPersistence.snapshot(data));

                MarketData restored = new MarketData();
                MarketStore.MarketFile stored = store.load();
                MarketStore.toShop(stored.shops().get(0)).ifPresent(restored::putShop);
                MarketStore.toObservation(stored.observations().get(0)).ifPresent(restored::putObservation);

                assertEquals(1, restored.shopCount());
                PhysicalShop restoredShop = restored.shops().get(0);
                assertEquals("Abasing", restoredShop.owner());
                assertEquals(new BlockPos(10, 64, -19), restoredShop.chestPos());
                assertEquals(1, restored.observations(restoredShop.shopId()).size());
                ShopObservation restoredObservation = restored.observations(restoredShop.shopId()).get(0);
                assertEquals(new BigDecimal("1"), restoredObservation.price());
                assertEquals(PriceScope.PER_BULK, restoredObservation.priceScope());
                assertEquals(StockUnit.UNKNOWN, restoredObservation.stockUnit());
                assertEquals("78", restoredObservation.stockRaw());
                assertTrue(restoredObservation.fingerprint().isExact());
        }

        @Test
        void manualListingsRoundTripThroughTheFile(@TempDir Path dir) throws IOException {
                Path file = dir.resolve("market.json");
                MarketStore store = new MarketStore(file);
                MarketData data = new MarketData();
                data.record(shop(), manual(new BigDecimal("2"), T));
                store.save(MarketPersistence.snapshot(data));

                MarketData restored = new MarketData();
                MarketStore.MarketFile stored = store.load();
                MarketStore.toShop(stored.shops().get(0)).ifPresent(restored::putShop);
                MarketStore.toObservation(stored.observations().get(0)).ifPresent(restored::putObservation);

                assertEquals(SourceType.MANUAL,
                                restored.observations(restored.shops().get(0).shopId()).get(0).sourceType());
        }

        @Test
        void anObservationSurvivesAFileVersionBumpFromAnOlderBuild(@TempDir Path dir) throws IOException {
                Path file = dir.resolve("market.json");
                Files.writeString(file, "{\"version\":1,\"shops\":[],\"observations\":[]}");
                MarketStore store = new MarketStore(file);
                store.save(new MarketStore.MarketFile(MarketStore.CURRENT_VERSION, List.of(), List.of()));

                assertEquals(MarketStore.CURRENT_VERSION, store.load().version());
        }

        @Test
        void aFileFromANewerBuildIsNotApplied(@TempDir Path dir) throws IOException {
                Path file = dir.resolve("market.json");
                Files.writeString(file, "{\"version\":99,\"shops\":[{\"id\":\"x\"}]}");

                MarketStore.MarketFile loaded = new MarketStore(file).load();

                assertEquals(MarketStore.CURRENT_VERSION, loaded.version());
                assertTrue(loaded.shops().isEmpty());
        }

        @Test
        void aCorruptFileIsReplacedByAnEmptyMarketAndPreservedAside(@TempDir Path dir) throws IOException {
                Path file = dir.resolve("market.json");
                Files.writeString(file, "{not json at all");

                MarketStore.MarketFile loaded = new MarketStore(file).load();

                assertEquals(0, loaded.shops().size());
                assertTrue(Files.exists(dir.resolve("market.json.corrupted")));
        }

        @Test
        void damagedEntriesAreDroppedAndHealthyOnesSurvive(@TempDir Path dir) throws IOException {
                Path file = dir.resolve("market.json");
                Files.writeString(file, """
                                {"version":1,
                                 "shops":[
                                   {"id":"good|world|1,2,3","sx":1,"sy":2,"sz":3,"createdAt":1000,"lastObservedAt":2000},
                                   {"id":"nocoords"},
                                   {"createdAt":1000}
                                 ],
                                 "observations":[
                                   {"shopId":"good|world|1,2,3","direction":"SELLING","observedAt":1000},
                                   {"shopId":"good|world|1,2,3","observedAt":1000},
                                   {"direction":"BUYING","observedAt":1000}
                                 ]}""");

                MarketStore.MarketFile loaded = new MarketStore(file).load();
                assertEquals(1, MarketStore.usableShops(loaded).size());
                assertEquals(1, MarketStore.usableObservations(loaded).size());
                assertNotNull(MarketStore.toShop(loaded.shops().get(0)).orElseThrow());
                assertEquals(ShopDirection.SELLING, MarketStore.toObservation(loaded.observations().get(0)).orElseThrow().direction());
        }

        @Test
        void unknownEnumNamesDegradeToTheSafeEnd(@TempDir Path dir) throws IOException {
                Path file = dir.resolve("market.json");
                Files.writeString(file, """
                                {"version":1,"shops":[],
                                 "observations":[
                                   {"shopId":"s","direction":"SELLING","token":"minecraft:diamond|{}",
                                    "fidelity":"EXACT","stockUnit":"WIBBLY","sourceType":"MAGIC","observedAt":1000}
                                 ]}""");

                ShopObservation restored = MarketStore.toObservation(new MarketStore(file).load().observations().get(0))
                                .orElseThrow();
                assertEquals(StockUnit.UNKNOWN, restored.stockUnit());
                assertEquals(SourceType.SIGN_ONLY, restored.sourceType());
        }

        @Test
        void aDamagedPriceDegradesToNoneNeverToZero(@TempDir Path dir) throws IOException {
                Path file = dir.resolve("market.json");
                Files.writeString(file, """
                                {"version":1,"shops":[],
                                 "observations":[
                                   {"shopId":"s","direction":"SELLING","price":"garbage","observedAt":1000}
                                 ]}""");

                ShopObservation restored = MarketStore.toObservation(new MarketStore(file).load().observations().get(0))
                                .orElseThrow();
                assertNull(restored.price());
        }

        @Test
        void aDirectionThatIsNotKnowableDropsTheObservation(@TempDir Path dir) throws IOException {
                Path file = dir.resolve("market.json");
                Files.writeString(file, """
                                {"version":1,"shops":[],
                                 "observations":[
                                   {"shopId":"s","direction":"SIDEWAYS","observedAt":1000}
                                 ]}""");

                assertTrue(MarketStore.toObservation(new MarketStore(file).load().observations().get(0)).isEmpty());
        }

        @Test
        void recordingARevisitUpdatesTheShopAndAppendsTheObservation() {
                MarketData data = new MarketData();
                PhysicalShop first = shop();
                data.record(first, observation(new BigDecimal("1"), T));

                PhysicalShop revisitedShop = new PhysicalShop(first.shopId(), first.serverId(), first.world(),
                                first.signPos(), first.chestPos(), "Pelle_Nomme", null, null, first.createdAt(),
                                T.plusSeconds(60));
                data.record(revisitedShop, observation(new BigDecimal("2"), T.plusSeconds(60)));

                assertEquals(1, data.shopCount());
                assertEquals("Pelle_Nomme", data.shop(first.shopId()).owner());
                assertEquals(2, data.observations(first.shopId()).size());
        }

        @Test
        void identicalMeasurementsInsideTheWindowAreOneObservation() {
                MarketData data = new MarketData();
                data.record(shop(), observation(new BigDecimal("1"), T));
                data.record(shop(), observation(new BigDecimal("1"), T.plusSeconds(30)));

                assertEquals(1, data.observations(shop().shopId()).size());
        }

        @Test
        void identicalMeasurementsOutsideTheWindowAreTwoObservations() {
                MarketData data = new MarketData();
                data.record(shop(), observation(new BigDecimal("1"), T));
                data.record(shop(), observation(new BigDecimal("1"), T.plusSeconds(300)));

                assertEquals(2, data.observations(shop().shopId()).size());
        }

        @Test
        void aChangedPriceIsNeverADuplicate() {
                MarketData data = new MarketData();
                data.record(shop(), observation(new BigDecimal("1"), T));
                data.record(shop(), observation(new BigDecimal("2"), T.plusSeconds(10)));

                assertEquals(2, data.observations(shop().shopId()).size());
        }

        @Test
        void historyIsBoundedPerShop() {
                MarketData data = new MarketData();
                for (int i = 0; i < MarketData.HISTORY_PER_SHOP + 10; i++) {
                        data.record(shop(), observation(new BigDecimal(i), T.plusSeconds(i)));
                }

                assertEquals(MarketData.HISTORY_PER_SHOP, data.observations(shop().shopId()).size());
                List<ShopObservation> kept = data.observations(shop().shopId());
                assertEquals(new BigDecimal(10 + MarketData.HISTORY_PER_SHOP - 1),
                                kept.get(kept.size() - 1).price());
        }

        @Test
        void aChestResolvedLaterFillsInWithoutErasingOwner() {
                MarketData data = new MarketData();
                PhysicalShop bare = new PhysicalShop("id", "earthmc.net", "minecraft_overworld",
                                new BlockPos(1, 64, 2), null, "Abasing", null, null, T, T);
                data.record(bare, observation(new BigDecimal("1"), T));

                PhysicalShop withChest = new PhysicalShop("id", "earthmc.net", "minecraft_overworld",
                                new BlockPos(1, 64, 2), new BlockPos(1, 64, 1), null, null, null, T, T.plusSeconds(30));
                data.record(withChest, observation(new BigDecimal("1"), T.plusSeconds(30)));

                assertEquals(new BlockPos(1, 64, 1), data.shop("id").chestPos());
                assertEquals("Abasing", data.shop("id").owner());
        }
}
