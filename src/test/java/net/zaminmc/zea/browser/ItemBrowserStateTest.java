package net.zaminmc.zea.browser;

import net.minecraft.core.BlockPos;
import net.zaminmc.zea.VanillaBootstrap;
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
 * variants the way their labels promise, the direction filter keeps
 * only the variants carrying that side's offers, and the detail state
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

	private static List<String> displays(List<ItemVariantIndex.Variant> variants) {
		return variants.stream().map(ItemVariantIndex.Variant::display).collect(Collectors.toList());
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
	void newestOrderRanksByTheLatestObservationAndKeepsOldestLast() {
		var comparator = ItemBrowserState.Order.NEWEST.comparator();
		var fresh = variant("Fresh", offer("s1", "A", ShopDirection.SELLING, T2, new BigDecimal("1")));
		var stale = variant("Stale", offer("s2", "B", ShopDirection.SELLING, T0, new BigDecimal("1")));
		var oldest = variant("Oldest", offer("s3", "C", ShopDirection.BUYING, Instant.EPOCH, new BigDecimal("1")));
		assertTrue(comparator.compare(fresh, stale) < 0, "the fresher observation sorts first");
		assertTrue(comparator.compare(stale, oldest) < 0, "the older observation sorts last");
	}

	@Test
	void directionFilterKeepsOnlyTheVariantsCarryingThatSide() {
		var selling = variant("Selling", offer("s1", "A", ShopDirection.SELLING, T1, new BigDecimal("1")));
		var buying = variant("Buying", offer("s2", "B", ShopDirection.BUYING, T1, new BigDecimal("1")));
		var both = variant("Both", offer("s3", "C", ShopDirection.SELLING, T1, new BigDecimal("1")),
				offer("s4", "D", ShopDirection.BUYING, T1, new BigDecimal("1")));
		assertTrue(ItemBrowserState.Filter.ALL.accepts(selling));
		assertTrue(ItemBrowserState.Filter.ALL.accepts(buying));
		assertTrue(ItemBrowserState.Filter.BUY_FROM.accepts(selling));
		assertFalse(ItemBrowserState.Filter.BUY_FROM.accepts(buying));
		assertFalse(ItemBrowserState.Filter.SELL_TO.accepts(selling));
		assertTrue(ItemBrowserState.Filter.SELL_TO.accepts(buying));
		assertTrue(ItemBrowserState.Filter.BUY_FROM.accepts(both));
		assertTrue(ItemBrowserState.Filter.SELL_TO.accepts(both));
	}

	@Test
	void resultsCacheInvalidatesOnQueryOrderAndFilterChanges() {
		ItemBrowserState state = new ItemBrowserState();
		var results = List.of(variant("A"));
		state.replaceResults(results, 7L, "dia", ItemBrowserState.Order.PRICE,
				true, ItemBrowserState.Filter.BUY_FROM);
		assertTrue(state.isCurrent(7L, "dia", ItemBrowserState.Order.PRICE,
				true, ItemBrowserState.Filter.BUY_FROM));
		assertFalse(state.isCurrent(8L, "dia", ItemBrowserState.Order.PRICE,
				true, ItemBrowserState.Filter.BUY_FROM), "a market revision invalidates the cache");
		assertFalse(state.isCurrent(7L, "dia ", ItemBrowserState.Order.PRICE,
				true, ItemBrowserState.Filter.BUY_FROM), "a changed query invalidates the cache");
		assertFalse(state.isCurrent(7L, "dia", ItemBrowserState.Order.NEWEST,
				true, ItemBrowserState.Filter.BUY_FROM), "a changed order invalidates the cache");
		assertFalse(state.isCurrent(7L, "dia", ItemBrowserState.Order.PRICE,
				false, ItemBrowserState.Filter.BUY_FROM), "a flipped direction invalidates the cache");
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
