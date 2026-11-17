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
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarketListingsTest {
	private static final Instant T = Instant.ofEpochMilli(1_000_000L);
	private static final String PICKAXE = "minecraft:diamond_pickaxe|{}";
	private static final String LOG = "minecraft:oak_log|{}";

	@BeforeAll
	static void boot() {
		VanillaBootstrap.ensure();
	}

	private static PhysicalShop shop(String id, String owner, BlockPos signPos) {
		return new PhysicalShop(id, "earthmc.net", "minecraft_overworld",
				signPos, null, owner, null, T, T);
	}

	private static ShopObservation selling(String shopId, String token, String display,
			String price, int bulk, Instant at) {
		return new ShopObservation(shopId, ShopDirection.SELLING,
				new ItemFingerprint(token, ItemFingerprint.Fidelity.EXACT),
				display, new BigDecimal(price), PriceScope.PER_BULK, bulk,
				"78", StockUnit.UNKNOWN, null, 64, null, null, null, true, List.of(), SourceType.SERVER_RESPONSE, at);
	}

	private static ShopObservation buying(String shopId, String token, String display,
			String price, Instant at) {
		return new ShopObservation(shopId, ShopDirection.BUYING,
				new ItemFingerprint(token, ItemFingerprint.Fidelity.EXACT),
				display, new BigDecimal(price), PriceScope.PER_ITEM, null,
				"38", StockUnit.SPACE, null, null, null, null, 10, true, List.of(), SourceType.SERVER_RESPONSE, at);
	}

	private static void record(MarketData data, PhysicalShop shop, ShopObservation observation) {
		data.record(shop, observation);
	}

	@Test
	void oneRowPerShopFromItsLatestVerifiedAdvert() {
		MarketData data = new MarketData();
		record(data, shop("a|sign", "Alice", new BlockPos(1, 64, 1)),
				selling("a|sign", PICKAXE, "Diamond Pickaxe", "2", 1, T));
		// A newer price replaces the row's advert; a second shop stays separate.
		record(data, shop("a|sign", "Alice", new BlockPos(1, 64, 1)),
				selling("a|sign", PICKAXE, "Diamond Pickaxe", "3", 1, T.plusSeconds(60)));
		record(data, shop("b|sign", "Bob", new BlockPos(2, 64, 2)),
				selling("b|sign", PICKAXE, "Diamond Pickaxe", "5", 1, T));

		List<MarketListings.Listing> rows = MarketListings.query(data, "",
				MarketListings.Filter.ALL, MarketSort.RECENTLY_OBSERVED, null);

		assertEquals(2, rows.size());
		assertEquals(new BigDecimal("3"), rows.stream()
				.filter(row -> row.shop().shopId().equals("a|sign"))
				.findFirst().orElseThrow().observation().price());
	}

	@Test
	void aChangedDirectionUpdatesTheRowInsteadOfSplittingIt() {
		MarketData data = new MarketData();
		record(data, shop("flip|sign", "Alice", new BlockPos(1, 64, 1)),
				selling("flip|sign", PICKAXE, "Diamond Pickaxe", "2", 1, T));
		record(data, shop("flip|sign", "Alice", new BlockPos(1, 64, 1)),
				buying("flip|sign", PICKAXE, "Diamond Pickaxe", "2", T.plusSeconds(60)));

		// The shop moved to the sell-to side: same identity, one row, new
		// direction — never a duplicate listing.
		assertEquals(0, MarketListings.query(data, "", MarketListings.Filter.BUY_FROM,
				MarketSort.ITEM_NAME, null).size());
		List<MarketListings.Listing> sellTo = MarketListings.query(data, "",
				MarketListings.Filter.SELL_TO, MarketSort.ITEM_NAME, null);
		assertEquals(1, sellTo.size());
		assertEquals(ShopDirection.BUYING, sellTo.get(0).direction());
		assertTrue(sellTo.get(0).isServerVerified());
	}

	@Test
	void filtersSelectTheRightSideOfTheCounter() {
		MarketData data = new MarketData();
		record(data, shop("seller|sign", "Alice", new BlockPos(1, 64, 1)),
				selling("seller|sign", PICKAXE, "Diamond Pickaxe", "2", 1, T));
		record(data, shop("buyer|sign", "Bob", new BlockPos(2, 64, 2)),
				buying("buyer|sign", PICKAXE, "Diamond Pickaxe", "17", T));

		assertEquals(2, MarketListings.query(data, "", MarketListings.Filter.ALL,
				MarketSort.ITEM_NAME, null).size());
		List<MarketListings.Listing> buyFrom = MarketListings.query(data, "",
				MarketListings.Filter.BUY_FROM, MarketSort.ITEM_NAME, null);
		assertEquals(1, buyFrom.size());
		assertEquals(ShopDirection.SELLING, buyFrom.get(0).direction());
		List<MarketListings.Listing> sellTo = MarketListings.query(data, "",
				MarketListings.Filter.SELL_TO, MarketSort.ITEM_NAME, null);
		assertEquals(1, sellTo.size());
		assertEquals(ShopDirection.BUYING, sellTo.get(0).direction());
	}

	@Test
	void searchMatchesItemOwnerAndTown() {
		MarketData data = new MarketData();
		MarketListings.TownLookup towns = (world, x, z) ->
				x == 1 ? new MarketListings.ResolvedTown("Pest", "Yugoslavia") : null;
		record(data, shop("a|sign", "Alice", new BlockPos(1, 64, 1)),
				selling("a|sign", PICKAXE, "Diamond Pickaxe", "2", 1, T));
		record(data, shop("b|sign", "Bob", new BlockPos(2, 64, 2)),
				selling("b|sign", LOG, "Oak Log", "1", 64, T));

		assertEquals(1, MarketListings.query(data, "pickaxe", MarketListings.Filter.ALL,
				MarketSort.ITEM_NAME, null).size());
		assertEquals(1, MarketListings.query(data, "alice", MarketListings.Filter.ALL,
				MarketSort.ITEM_NAME, null).size());
		// The shop in Pest matches by its resolved town.
		assertEquals(1, MarketListings.query(data, "pest", MarketListings.Filter.ALL,
				MarketSort.ITEM_NAME, towns).size());
		assertEquals(0, MarketListings.query(data, "netherite", MarketListings.Filter.ALL,
				MarketSort.ITEM_NAME, null).size());
	}

	@Test
	void signOnlyAdvertsAreListedAndLabelled() {
		MarketData data = new MarketData();
		record(data, shop("sign|sign", "Alice", new BlockPos(1, 64, 1)),
				new ShopObservation("sign|sign", ShopDirection.SELLING,
						new ItemFingerprint("diamond_pickaxe", ItemFingerprint.Fidelity.DISPLAY_ONLY),
						"Diamond Pickaxe", null, null, null, "36", StockUnit.UNKNOWN,
						null, null, null, null, null, false, List.of(), SourceType.SIGN_ONLY, T));

		List<MarketListings.Listing> rows = MarketListings.query(data, "",
				MarketListings.Filter.ALL, MarketSort.ITEM_NAME, null);
		assertEquals(1, rows.size());
		assertEquals("Diamond Pickaxe", rows.get(0).observation().itemDisplay());
		assertTrue(rows.get(0).observation().price() == null);
	}

	@Test
	void townResolutionReachesTheLookupWithTheSignPosition() {
		MarketData data = new MarketData();
		record(data, shop("a|sign", "Alice", new BlockPos(10, 64, -20)),
				selling("a|sign", PICKAXE, "Diamond Pickaxe", "2", 1, T));

		java.util.concurrent.atomic.AtomicReference<String> askedWorld = new java.util.concurrent.atomic.AtomicReference<>();
		java.util.concurrent.atomic.AtomicLong askedX = new java.util.concurrent.atomic.AtomicLong(-1);
		java.util.concurrent.atomic.AtomicLong askedZ = new java.util.concurrent.atomic.AtomicLong(-1);
		MarketListings.TownLookup towns = (world, x, z) -> {
			askedWorld.set(world);
			askedX.set(x);
			askedZ.set(z);
			return null;
		};

		MarketListings.query(data, "", MarketListings.Filter.ALL, MarketSort.ITEM_NAME, towns);

		assertEquals("minecraft_overworld", askedWorld.get());
		assertEquals(10, askedX.get());
		assertEquals(-20, askedZ.get());
	}

	@Test
	void aNullTownLookupLeavesTownAndNationUnavailable() {
		MarketData data = new MarketData();
		record(data, shop("a|sign", "Alice", new BlockPos(1, 64, 1)),
				selling("a|sign", PICKAXE, "Diamond Pickaxe", "2", 1, T));

		MarketListings.Listing row = MarketListings.query(data, "", MarketListings.Filter.ALL,
				MarketSort.ITEM_NAME, null).get(0);
		assertNull(row.town());
		assertNull(row.nation());
	}
}
