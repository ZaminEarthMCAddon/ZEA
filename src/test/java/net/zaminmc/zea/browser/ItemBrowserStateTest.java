package net.zaminmc.zea.browser;

import net.minecraft.core.BlockPos;
import net.zaminmc.zea.VanillaBootstrap;
import net.zaminmc.zea.itempresentation.recognition.ItemCategory;
import net.zaminmc.zea.market.index.ItemVariantIndex;
import net.zaminmc.zea.market.model.ItemFingerprint;
import net.zaminmc.zea.market.model.PhysicalShop;
import net.zaminmc.zea.market.model.PriceScope;
import net.zaminmc.zea.market.model.ShopDirection;
import net.zaminmc.zea.market.model.ShopObservation;
import net.zaminmc.zea.market.model.SourceType;
import net.zaminmc.zea.market.model.StockUnit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The item browser's session semantics: the order comparators rank
 * variants the way their labels promise, the three-state sort cycle
 * (relevance → primary → opposite → relevance) and the category
 * filters behave exactly as the buttons promise, and the detail state
 * keys everything by token and shop id so a refresh or re-sort cannot
 * move a selection.
 */
class ItemBrowserStateTest {
        private static final Instant T0 = Instant.ofEpochMilli(1_000_000L);
        private static final Instant T1 = Instant.ofEpochMilli(2_000_000L);
        private static final Instant T2 = Instant.ofEpochMilli(3_000_000L);

        @BeforeAll
        static void boot() {
                VanillaBootstrap.ensure();
        }

        private static PhysicalShop shop(String shopId, String owner) {
                return new PhysicalShop(shopId, "earthmc.net", "minecraft_overworld",
                                new BlockPos(10, 64, -20), null, owner, null, T0, T1);
        }

        private static ShopObservation observation(String shopId, ShopDirection direction, Instant at) {
                return new ShopObservation(shopId, direction,
                                new ItemFingerprint("minecraft:diamond_pickaxe|{}", ItemFingerprint.Fidelity.EXACT),
                                "Diamond Pickaxe", new BigDecimal("2"), PriceScope.PER_ITEM, null,
                                "47", StockUnit.ITEMS, null, null, null, null, null, true,
                                List.of(), SourceType.SERVER_RESPONSE, at);
        }

        private static ItemVariantIndex.Variant variant(String display, ItemVariantIndex.Offer... offers) {
                return new ItemVariantIndex.Variant("minecraft:diamond_pickaxe|" + display.toLowerCase(),
                                display, List.of(offers));
        }

        private static ItemVariantIndex.Offer offer(String shopId, String owner, ShopDirection direction,
                        Instant at, @org.jspecify.annotations.Nullable BigDecimal unitPrice) {
                return new ItemVariantIndex.Offer(shop(shopId, owner), observation(shopId, direction, at), unitPrice);
        }

        @Test
        void alphabeticalOrderSortsByNameCaseInsensitively() {
                var comparator = ItemBrowserState.Order.ALPHABETICAL.comparator();
                var apple = variant("apple");
                var Banana = variant("Banana");
                var cherry = variant("cherry");
                assertTrue(comparator.compare(apple, Banana) < 0, "apple sorts before Banana");
                assertTrue(comparator.compare(Banana, cherry) < 0, "Banana sorts before cherry");
                assertEquals(0, comparator.compare(apple, variant("Apple")), "the order is case-insensitive");
        }

        @Test
        void priceOrderRanksByTheBestBuyPriceAndKeepsNonBuyableLast() {
                var comparator = ItemBrowserState.Order.PRICE.comparator();
                var cheap = variant("Cheap", offer("s1", "A", ShopDirection.SELLING, T1, new BigDecimal("1")));
                var dear = variant("Dear", offer("s2", "B", ShopDirection.SELLING, T1, new BigDecimal("5")));
                var none = variant("None", offer("s3", "C", ShopDirection.BUYING, T1, new BigDecimal("2")));
                assertTrue(comparator.compare(cheap, dear) < 0, "the cheaper buy price sorts first");
                assertTrue(comparator.compare(dear, none) < 0, "a variant without a buyable offer sorts last");
                assertEquals(0, comparator.compare(none, none));
        }

        @Test
        void rarityOrderSinksUnratedVariantsAndTiesBreakByName() {
                // A bare-JVM test has no config and no level, so the rarity
                // system is off: every variant is unrated, the comparator must
                // stay total and tie-break by display name, and the sort must
                // not resolve a single stack for the no-op. The rated path is
                // the rarity engine's own tests; the browser only reads the
                // ordinal, live-verified in the client.
                var comparator = ItemBrowserState.Order.RARITY.comparator();
                var alpha = variant("Alpha Pickaxe");
                var beta = variant("Beta Sword");
                assertEquals(0, comparator.compare(alpha, alpha));
                assertTrue(comparator.compare(alpha, beta) < 0, "unrated variants tie-break by display name");
                assertTrue(comparator.compare(beta, alpha) > 0, "the comparator is antisymmetric");
                assertEquals(0, comparator.compare(alpha, variant("alpha pickaxe")),
                                "the same display ties whatever the token is");
        }

        /**
         * The bucket mapping, the pure seam of the category filters. The
         * stack-level acceptance ({@code Filter.accepts} against real
         * tokens) needs bound components — a bare JVM never binds them —
         * so that half is verified live in the client instead.
         */
        @Test
        void categoryBucketsMapTheEquipmentFamilies() {
                for (var family : List.of(ItemCategory.SWORD, ItemCategory.AXE, ItemCategory.PICKAXE,
                                ItemCategory.SHOVEL, ItemCategory.HOE, ItemCategory.SPEAR, ItemCategory.MACE,
                                ItemCategory.BOW, ItemCategory.CROSSBOW, ItemCategory.TRIDENT)) {
                        assertEquals(BrowserCategory.WEAPONS, BrowserCategory.fromCategory(family),
                                        family + " is a weapon or tool");
                }
                for (var family : List.of(ItemCategory.HELMET, ItemCategory.CHESTPLATE,
                                ItemCategory.LEGGINGS, ItemCategory.BOOTS)) {
                        assertEquals(BrowserCategory.ARMOR, BrowserCategory.fromCategory(family),
                                        family + " is armor");
                }
                assertEquals(BrowserCategory.GENERAL, BrowserCategory.fromCategory(ItemCategory.OTHER),
                                "everything unrecognized is general goods");
        }

        @Test
        void theServerMarkerRidesTheRawTokenNeverAStackResolution() {
                assertTrue(BrowserCategory.isServerItem("earthmc:oxygen_tank|{}"),
                                "an earthmc item id marks the item");
                assertTrue(BrowserCategory.isServerItem(
                                "minecraft:stone|{\"earthmc:custom_data\":{\"item\":\"moon_rock\"}}"),
                                "an earthmc namespace inside the patch marks the item");
                assertTrue(BrowserCategory.isServerItem("MINECRAFT:SWORD|{EARTHMC:X:1}"),
                                "the marker check is case-insensitive");
                assertFalse(BrowserCategory.isServerItem("minecraft:diamond_sword|{}"),
                                "vanilla items are not server items");
                assertFalse(BrowserCategory.isServerItem(null));
        }

        @Test
        void allAcceptsEverythingWithoutTouchingTheStack() {
                var any = variant("Anything");
                assertTrue(ItemBrowserState.Filter.ALL.accepts(any),
                                "ALL is the no-filter state and never resolves");
        }

        @Test
        void alphabeticalSortCyclesRelevanceThenAzThenZaThenRelevance() {
                ItemBrowserState state = new ItemBrowserState();
                assertEquals(ItemBrowserState.Order.NONE, state.order(), "relevance until a button is clicked");
                state.cycleOrder(ItemBrowserState.Order.ALPHABETICAL, true);
                assertEquals(ItemBrowserState.Order.ALPHABETICAL, state.order());
                assertTrue(state.ascending(), "the first click sorts A→Z, NEU's green arrow");
                state.cycleOrder(ItemBrowserState.Order.ALPHABETICAL, true);
                assertTrue(state.ascending() == false, "the second click sorts Z→A, NEU's red arrow");
                state.cycleOrder(ItemBrowserState.Order.ALPHABETICAL, true);
                assertEquals(ItemBrowserState.Order.NONE, state.order(),
                                "the third click returns to relevance, arrow gone");
        }

        @Test
        void priceSortOpensHighestFirstThenFlipsThenReturnsToRelevance() {
                ItemBrowserState state = new ItemBrowserState();
                state.cycleOrder(ItemBrowserState.Order.PRICE, false);
                assertEquals(ItemBrowserState.Order.PRICE, state.order());
                assertTrue(state.ascending() == false, "price opens highest price first");
                state.cycleOrder(ItemBrowserState.Order.PRICE, false);
                assertTrue(state.ascending(), "the second click sorts cheapest first");
                state.cycleOrder(ItemBrowserState.Order.PRICE, false);
                assertEquals(ItemBrowserState.Order.NONE, state.order());
        }

        @Test
        void clickingAnotherSortButtonRestartsAtItsPrimaryDirection() {
                ItemBrowserState state = new ItemBrowserState();
                state.cycleOrder(ItemBrowserState.Order.ALPHABETICAL, true);
                state.cycleOrder(ItemBrowserState.Order.ALPHABETICAL, true); // now Z→A
                state.cycleOrder(ItemBrowserState.Order.RARITY, true);
                assertEquals(ItemBrowserState.Order.RARITY, state.order());
                assertTrue(state.ascending(), "rarity opens Common→Legendary");
                state.cycleOrder(ItemBrowserState.Order.RARITY, true);
                assertTrue(state.ascending() == false, "Legendary→Common");
                state.cycleOrder(ItemBrowserState.Order.RARITY, true);
                assertEquals(ItemBrowserState.Order.NONE, state.order());

        }

        /**
         * The pane starts closed — NEU's {@code itemPaneOpen = false}:
         * the sidebar never greets a screen wide open. Opening, closing
         * and reopening round-trip, and closing takes the detail view
         * with it.
         */
        @Test
        void thePaneStartsClosedAndRoundTrips() {
                ItemBrowserState state = new ItemBrowserState();
                assertTrue(state.isOpen() == false, "NEU starts with the pane closed");
                state.open();
                assertTrue(state.isOpen());
                state.openDetail("some-token");
                assertEquals(ItemBrowserState.View.DETAIL, state.view());
                state.close();
                assertTrue(state.isOpen() == false, "the close hides the pane");
                assertEquals(ItemBrowserState.View.GRID, state.view(),
                                "closing takes the detail view with it");
                state.open();
                assertTrue(state.isOpen(), "the edge tab reopens it");
        }

        @Test
        void categoryFilterTogglesBackToAll() {
                ItemBrowserState state = new ItemBrowserState();
                assertEquals(ItemBrowserState.Filter.ALL, state.filter());
                state.cycleFilter(ItemBrowserState.Filter.ARMOR);
                assertEquals(ItemBrowserState.Filter.ARMOR, state.filter());
                state.cycleFilter(ItemBrowserState.Filter.ARMOR);
                assertEquals(ItemBrowserState.Filter.ALL, state.filter(),
                                "clicking the active filter returns to everything");
                state.cycleFilter(ItemBrowserState.Filter.EARTH);
                assertEquals(ItemBrowserState.Filter.EARTH, state.filter());
                state.cycleFilter(ItemBrowserState.Filter.ALL);
                assertEquals(ItemBrowserState.Filter.ALL, state.filter());
        }

        @Test
        void resultsCacheInvalidatesOnQueryOrderAndFilterChanges() {
                ItemBrowserState state = new ItemBrowserState();
                var results = List.of(variant("A"));
                state.replaceResults(results, 7L, "dia", ItemBrowserState.Order.PRICE,
                                true, ItemBrowserState.Filter.WEAPONS);
                assertTrue(state.isCurrent(7L, "dia", ItemBrowserState.Order.PRICE,
                                true, ItemBrowserState.Filter.WEAPONS));
                assertFalse(state.isCurrent(8L, "dia", ItemBrowserState.Order.PRICE,
                                true, ItemBrowserState.Filter.WEAPONS), "a market revision invalidates the cache");
                assertFalse(state.isCurrent(7L, "dia ", ItemBrowserState.Order.PRICE,
                                true, ItemBrowserState.Filter.WEAPONS), "a changed query invalidates the cache");
                assertFalse(state.isCurrent(7L, "dia", ItemBrowserState.Order.RARITY,
                                true, ItemBrowserState.Filter.WEAPONS), "a changed order invalidates the cache");
                assertFalse(state.isCurrent(7L, "dia", ItemBrowserState.Order.PRICE,
                                false, ItemBrowserState.Filter.WEAPONS), "a flipped direction invalidates the cache");
                assertFalse(state.isCurrent(7L, "dia", ItemBrowserState.Order.PRICE,
                                true, ItemBrowserState.Filter.ALL), "a changed filter invalidates the cache");
                assertEquals(results, state.results());
        }

        @Test
        void detailStateKeysSelectionByTokenAndShopId() {
                ItemBrowserState state = new ItemBrowserState();
                state.openDetail("minecraft:diamond_pickaxe|enchanted");
                assertEquals(ItemBrowserState.View.DETAIL, state.view());
                assertEquals("minecraft:diamond_pickaxe|enchanted", state.selectedToken());
                assertNull(state.selectedShopId());
                state.selectOffer("shop-a");
                assertEquals("shop-a", state.selectedShopId());
                state.selectOffer("shop-a");
                assertNull(state.selectedShopId(), "re-clicking the row deselects it");
                state.selectOffer("shop-b");
                assertEquals("shop-b", state.selectedShopId());
                state.closeDetail();
                assertEquals(ItemBrowserState.View.GRID, state.view());
                assertNull(state.selectedToken());
                assertNull(state.selectedShopId());
                assertEquals(0, state.detailScroll());
        }

        @Test
        void detailScrollNeverGoesNegative() {
                ItemBrowserState state = new ItemBrowserState();
                state.setDetailScroll(30);
                assertEquals(30, state.detailScroll());
                state.setDetailScroll(-5);
                assertEquals(0, state.detailScroll());
        }

        @Test
        void closeHidesThePaneAndOpenBringsItBack() {
                ItemBrowserState state = new ItemBrowserState();
                state.open();
                assertTrue(state.isOpen());
                state.openDetail("token");
                state.close();
                assertFalse(state.isOpen());
                assertEquals(ItemBrowserState.View.GRID, state.view(), "closing the pane leaves the detail too");
                state.open();
                assertTrue(state.isOpen());
        }

        @Test
        void pagesNeverGoNegative() {
                ItemBrowserState state = new ItemBrowserState();
                state.setPage(3);
                assertEquals(3, state.page());
                state.setPage(-1);
                assertEquals(0, state.page());
        }
}
