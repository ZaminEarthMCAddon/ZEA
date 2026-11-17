package net.zaminmc.zea.market.pricing;

import net.minecraft.core.BlockPos;
import net.zaminmc.zea.VanillaBootstrap;
import net.zaminmc.zea.market.MarketData;
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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OfferResolverTest {
	private static final Instant T = Instant.ofEpochMilli(1_000_000L);
	private static final String PICKAXE = "minecraft:diamond_pickaxe|{\"minecraft:enchantments\":\"eff5/fort3\"}";
	private static final String PICKAXE_OTHER = "minecraft:diamond_pickaxe|{\"minecraft:enchantments\":\"eff5/silk1\"}";

	@BeforeAll
	static void boot() {
		VanillaBootstrap.ensure();
	}

	private static PhysicalShop shop(String id, String owner) {
		return new PhysicalShop(id, "earthmc.net", "minecraft_overworld",
				new BlockPos(10, 64, 20 + id.hashCode() % 50), null, owner, null, T, T);
	}

	private static ShopObservation selling(String shopId, String token, String price, int bulk, Instant at) {
		return new ShopObservation(shopId, ShopDirection.SELLING,
				new ItemFingerprint(token, ItemFingerprint.Fidelity.EXACT),
				"Diamond Pickaxe", new BigDecimal(price), PriceScope.PER_BULK, bulk,
				"78", StockUnit.UNKNOWN, null, 64, null, null, null, true, List.of(), SourceType.SERVER_RESPONSE, at);
	}

	private static ShopObservation buying(String shopId, String token, String price, Instant at) {
		return new ShopObservation(shopId, ShopDirection.BUYING,
				new ItemFingerprint(token, ItemFingerprint.Fidelity.EXACT),
				"Diamond Pickaxe", new BigDecimal(price), PriceScope.PER_ITEM, null,
				null, StockUnit.SPACE, null, null, null, null, 10, true, List.of(), SourceType.SERVER_RESPONSE, at);
	}

	@Test
	void differentBulkSizesNormalizeBeforeRanking() {
		// 1G per 7 items = 0.142857/item, undercutting 1G per bulk of 1
		// (= 1G/item) even though the raw price is the same.
		MarketData data = new MarketData();
		data.record(shop("cheap|sign", "A"), selling("cheap|sign", PICKAXE, "1", 7, T));
		data.record(shop("expensive|sign", "B"), selling("expensive|sign", PICKAXE, "1", 1, T));

		Optional<MarketOffer> best = new OfferResolver().bestBuyOffer(data, PICKAXE);
		assertEquals("cheap|sign", best.orElseThrow().shop().shopId());
		assertEquals(0, best.orElseThrow().unitPrice().compareTo(new BigDecimal("0.142857")));
	}

	@Test
	void theLowestSellingPriceWinsTheBuyFromRanking() {
		MarketData data = new MarketData();
		data.record(shop("france|s1", "A"), selling("france|s1", PICKAXE, "2", 1, T));
		data.record(shop("germany|s1", "B"), selling("germany|s1", PICKAXE, "1.5", 1, T));

		assertEquals("germany|s1", new OfferResolver().bestBuyOffer(data, PICKAXE).orElseThrow().shop().shopId());
	}

	@Test
	void theHighestBuyingPriceWinsTheSellToRanking() {
		MarketData data = new MarketData();
		data.record(shop("a|sign", "A"), buying("a|sign", PICKAXE, "17", T));
		data.record(shop("b|sign", "B"), buying("b|sign", PICKAXE, "19", T));

		assertEquals("b|sign", new OfferResolver().bestSellOffer(data, PICKAXE).orElseThrow().shop().shopId());
	}

	@Test
	void theDirectionsNeverMix() {
		MarketData data = new MarketData();
		// A buying shop paying 17G is irrelevant to buying from.
		data.record(shop("buyer|sign", "A"), buying("buyer|sign", PICKAXE, "17", T));

		assertTrue(new OfferResolver().bestBuyOffer(data, PICKAXE).isEmpty());
		assertTrue(new OfferResolver().bestSellOffer(data, PICKAXE).isPresent());
	}

	@Test
	void incompatibleIdentitiesNeverCompare() {
		MarketData data = new MarketData();
		data.record(shop("silk|sign", "A"), selling("silk|sign", PICKAXE_OTHER, "0.1", 1, T));

		assertTrue(new OfferResolver().bestBuyOffer(data, PICKAXE).isEmpty());
		assertEquals(1, new OfferResolver().offers(data, PICKAXE_OTHER).size());
	}

	@Test
	void aDisplayOnlyObservationNeverMatchesAnExactToken() {
		MarketData data = new MarketData();
		data.record(shop("sign|sign", "A"),
				new ShopObservation("sign|sign", ShopDirection.SELLING,
						new ItemFingerprint("diamond_pickaxe", ItemFingerprint.Fidelity.DISPLAY_ONLY),
						"Diamond Pickaxe", new BigDecimal("1"), PriceScope.PER_ITEM, null,
						null, StockUnit.UNKNOWN, null, null, null, null, null, false, List.of(), SourceType.SIGN_ONLY, T));

		assertTrue(new OfferResolver().offers(data, PICKAXE).isEmpty());
	}

	@Test
	void theLatestObservationPerShopDecidesItsOffer() {
		MarketData data = new MarketData();
		// An older cheaper measurement must not outrank the shop's newer
		// price: the winner is the shop's current advert, not its history.
		data.record(shop("shop|sign", "A"), selling("shop|sign", PICKAXE, "5", 1, T));
		data.record(shop("shop|sign", "A"), selling("shop|sign", PICKAXE, "2", 1, T.plusSeconds(60)));

		List<MarketOffer> offers = new OfferResolver().offers(data, PICKAXE);
		assertEquals(1, offers.size());
		assertEquals(0, offers.get(0).unitPrice().compareTo(new BigDecimal("2")));
	}

	@Test
	void anObservationWithoutAComparablePriceIsListedButNeverWins() {
		MarketData data = new MarketData();
		// Per-bulk without a bulk size is incomparable.
		data.record(shop("opaque|sign", "A"), new ShopObservation("opaque|sign",
				ShopDirection.SELLING,
				new ItemFingerprint(PICKAXE, ItemFingerprint.Fidelity.EXACT),
				"Diamond Pickaxe", new BigDecimal("1"), PriceScope.PER_BULK, null,
				null, StockUnit.UNKNOWN, null, null, null, null, null, false, List.of(), SourceType.SERVER_RESPONSE, T));
		data.record(shop("clear|sign", "B"), selling("clear|sign", PICKAXE, "9", 1, T));

		assertEquals("clear|sign", new OfferResolver().bestBuyOffer(data, PICKAXE).orElseThrow().shop().shopId());
		assertEquals(2, new OfferResolver().offers(data, PICKAXE).size());
	}

	@Test
	void equalPricesResolveDeterministically() {
		MarketData data = new MarketData();
		data.record(shop("zzz|sign", "A"), selling("zzz|sign", PICKAXE, "2", 1, T));
		data.record(shop("aaa|sign", "B"), selling("aaa|sign", PICKAXE, "2", 1, T.plusSeconds(5)));

		List<MarketOffer> offers = new OfferResolver().offers(data, PICKAXE);
		// Same price: the more recent observation wins the tie.
		assertEquals("aaa|sign", offers.get(0).shop().shopId());
		assertEquals("zzz|sign", offers.get(1).shop().shopId());
	}

	@Test
	void totalsAreExact() {
		assertEquals(0, PriceMath.total(new BigDecimal("1"), 7).compareTo(new BigDecimal("7")));
		assertEquals(0, PriceMath.total(new BigDecimal("17"), 2).compareTo(new BigDecimal("34")));
		assertEquals(0, PriceMath.total(new BigDecimal("0.25"), 2).compareTo(new BigDecimal("0.5")));
	}

	@Test
	void itemsForUsesTheReportedBulkSizeOnly() {
		assertEquals(14, PriceMath.itemsFor(2, 7).orElseThrow());
		assertEquals(64, PriceMath.itemsFor(1, 64).orElseThrow());
		assertTrue(PriceMath.itemsFor(2, null).isEmpty());
		assertTrue(PriceMath.itemsFor(2, 0).isEmpty());
	}

	@Test
	void anEmptyMarketResolvesNoOffers() {
		assertTrue(new OfferResolver().bestBuyOffer(new MarketData(), PICKAXE).isEmpty());
		assertTrue(new OfferResolver().bestSellOffer(new MarketData(), PICKAXE).isEmpty());
	}
}
