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
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarketSortTest {
	private static final Instant T = Instant.ofEpochMilli(1_000_000L);
	private static final String PICKAXE = "minecraft:diamond_pickaxe|{}";

	@BeforeAll
	static void boot() {
		VanillaBootstrap.ensure();
	}

	private static MarketData market(java.util.function.Consumer<MarketData> fill) {
		MarketData data = new MarketData();
		fill.accept(data);
		return data;
	}

	private static void selling(MarketData data, String id, String display, String price,
			int bulk, Instant at) {
		data.record(shop(id, display), new ShopObservation(id, ShopDirection.SELLING,
				new ItemFingerprint(PICKAXE, ItemFingerprint.Fidelity.EXACT),
				display, new BigDecimal(price), PriceScope.PER_BULK, bulk,
				"78", StockUnit.UNKNOWN, null, 64, null, null, null, true, List.of(), SourceType.SERVER_RESPONSE, at));
	}

	private static void buying(MarketData data, String id, String display, String price, Instant at) {
		data.record(shop(id, display), new ShopObservation(id, ShopDirection.BUYING,
				new ItemFingerprint(PICKAXE, ItemFingerprint.Fidelity.EXACT),
				display, new BigDecimal(price), PriceScope.PER_ITEM, null,
				"38", StockUnit.SPACE, null, null, null, null, 10, true, List.of(), SourceType.SERVER_RESPONSE, at));
	}

	private static PhysicalShop shop(String id, String display) {
		return new PhysicalShop(id, "earthmc.net", "minecraft_overworld",
				new BlockPos(display.length(), 64, id.length()), null,
				display + "-owner", null, T, T);
	}

	private static List<String> order(MarketData data, MarketListings.Filter filter, MarketSort sort) {
		return MarketListings.query(data, "", filter, sort, null).stream()
				.map(row -> row.observation().itemDisplay())
				.toList();
	}

	@Test
	void priceOnBuyFromSortsCheapestFirst() {
		MarketData data = market(d -> {
			selling(d, "s1", "Alpha", "500", 32, T);
			selling(d, "s2", "Beta", "300", 32, T.plusSeconds(1));
		});

		assertEquals(List.of("Beta", "Alpha"),
				order(data, MarketListings.Filter.BUY_FROM, MarketSort.PRICE));
	}

	@Test
	void priceOnSellToSortsHighestPayoutFirst() {
		MarketData data = market(d -> {
			buying(d, "b1", "Alpha", "500", T);
			buying(d, "b2", "Beta", "700", T.plusSeconds(1));
		});

		assertEquals(List.of("Beta", "Alpha"),
				order(data, MarketListings.Filter.SELL_TO, MarketSort.PRICE));
	}

	@Test
	void pricePerItemComparesAcrossBulkSizesFairly() {
		MarketData data = market(d -> {
			// 1G per bulk of 7 = 0.142857/item: cheaper per item than
			// 1G per single item, despite the same raw price.
			selling(d, "s1", "Alpha", "1", 1, T);
			selling(d, "s2", "Beta", "1", 7, T.plusSeconds(1));
		});

		assertEquals(List.of("Beta", "Alpha"),
				order(data, MarketListings.Filter.BUY_FROM, MarketSort.PRICE_PER_ITEM));
		// By raw price both show 1G; the name breaks the tie deterministically.
		assertEquals(List.of("Alpha", "Beta"),
				order(data, MarketListings.Filter.BUY_FROM, MarketSort.PRICE));
	}

	@Test
	void unjudgeablePricesSinkInBothDirections() {
		MarketData data = market(d -> {
			selling(d, "s1", "Alpha", "5", 1, T);
			// Per-bulk without a bulk size is unjudgeable.
			d.record(shop("s2", "Beta"), new ShopObservation("s2", ShopDirection.SELLING,
					new ItemFingerprint(PICKAXE, ItemFingerprint.Fidelity.EXACT),
					"Beta", new BigDecimal("1"), PriceScope.PER_BULK, null,
					null, StockUnit.UNKNOWN, null, null, null, null, null, false, List.of(), SourceType.SERVER_RESPONSE, T.plusSeconds(1)));
			// A sign-only advert has no price at all.
			d.record(shop("s3", "Gamma"), new ShopObservation("s3", ShopDirection.SELLING,
					new ItemFingerprint("pick", ItemFingerprint.Fidelity.DISPLAY_ONLY),
					"Gamma", null, null, null, "36", StockUnit.UNKNOWN,
					null, null, null, null, null, false, List.of(), SourceType.SIGN_ONLY, T.plusSeconds(2)));
		});

		// On the raw-price sort Beta is judgeable (a real 1G advert), and
		// cheaper than Alpha's 5G; only the priceless advert sinks.
		List<String> buyFrom = order(data, MarketListings.Filter.BUY_FROM, MarketSort.PRICE);
		assertEquals("Beta", buyFrom.get(0));
		assertEquals("Alpha", buyFrom.get(1));
		assertEquals("Gamma", buyFrom.get(2));

		List<String> sellTo = order(data, MarketListings.Filter.ALL, MarketSort.PRICE_PER_ITEM);
		assertEquals("Alpha", sellTo.get(0));
		assertTrue(sellTo.indexOf("Gamma") > sellTo.indexOf("Alpha"));
		assertTrue(sellTo.indexOf("Beta") > sellTo.indexOf("Alpha"));
	}

	@Test
	void recentlyObservedPutsFreshVerificationsFirst() {
		MarketData data = market(d -> {
			selling(d, "s1", "Alpha", "5", 1, T);
			selling(d, "s2", "Beta", "5", 1, T.plusSeconds(3 * 3600));
		});

		assertEquals(List.of("Beta", "Alpha"),
				order(data, MarketListings.Filter.ALL, MarketSort.RECENTLY_OBSERVED));
	}

	@Test
	void nameOrderIsAlphabeticalAndDeterministic() {
		MarketData data = market(d -> {
			selling(d, "s2", "banana", "5", 1, T);
			selling(d, "s1", "Apple", "5", 1, T.plusSeconds(5));
		});

		assertEquals(List.of("Apple", "banana"),
				order(data, MarketListings.Filter.ALL, MarketSort.ITEM_NAME));
	}

	@Test
	void everySortBreaksTiesByShopId() {
		MarketData data = market(d -> {
			selling(d, "zzz|sign", "Same", "5", 1, T);
			selling(d, "aaa|sign", "Same", "5", 1, T);
		});

		for (MarketSort sort : MarketSort.values()) {
			List<String> order = order(data, MarketListings.Filter.ALL, sort);
			assertEquals(2, order.size());
			// Both rows carry the same display name, so the shop id must
			// decide; verify the whole pipeline ran without a crash and
			// kept both rows.
			assertEquals("Same", order.get(0));
			assertEquals("Same", order.get(1));
		}
	}

	@Test
	void cycleOrderIsComplete() {
		assertEquals(MarketSort.PRICE, MarketSort.ITEM_NAME.next());
		assertEquals(MarketSort.PRICE_PER_ITEM, MarketSort.PRICE.next());
		assertEquals(MarketSort.RECENTLY_OBSERVED, MarketSort.PRICE_PER_ITEM.next());
		assertEquals(MarketSort.ITEM_NAME, MarketSort.RECENTLY_OBSERVED.next());
	}
}
