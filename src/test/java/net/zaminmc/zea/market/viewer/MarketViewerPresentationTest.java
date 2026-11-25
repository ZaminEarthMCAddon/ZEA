package net.zaminmc.zea.market.viewer;

import net.minecraft.core.BlockPos;
import net.zaminmc.zea.market.index.ItemVariantIndex.Offer;
import net.zaminmc.zea.market.model.ItemFingerprint;
import net.zaminmc.zea.market.model.PhysicalShop;
import net.zaminmc.zea.market.model.PriceScope;
import net.zaminmc.zea.market.model.ShopDirection;
import net.zaminmc.zea.market.model.ShopObservation;
import net.zaminmc.zea.market.model.SourceType;
import net.zaminmc.zea.market.model.StockUnit;
import net.zaminmc.zea.market.viewer.MarketViewerScreen.DetailLine;
import net.zaminmc.zea.market.viewer.MarketViewerScreen.Side;
import net.zaminmc.zea.nav.ShopDestination;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The viewer's presentation facts: the count line's nouns, the detail
 * lines' order and wording, and the teleport gate's two block reasons.
 * Pure strings and records — no client, no screen, no index.
 */
class MarketViewerPresentationTest {

	private static final Instant T = Instant.ofEpochMilli(1_000_000L);

	private static PhysicalShop shop(String owner) {
		return new PhysicalShop("shop-1", "earthmc.net", "minecraft:overworld",
				new BlockPos(100, 64, 200), null, owner, null, T, T);
	}

	private static ShopObservation observed(String price, PriceScope scope, Integer bulk,
			String stockRaw, StockUnit unit, Integer maxBuyItems, SourceType source) {
		return new ShopObservation("shop-1", ShopDirection.SELLING,
				new ItemFingerprint("minecraft:diamond|{}", ItemFingerprint.Fidelity.EXACT),
				"Diamond", price == null ? null : new BigDecimal(price), scope, bulk,
				stockRaw, unit, null, null, maxBuyItems, null, null,
				false, List.of(), source, T);
	}

	private static Offer offer(ShopObservation observation, String unitPrice) {
		return new Offer(shop("KoenigLuis"), observation,
				unitPrice == null ? null : new BigDecimal(unitPrice));
	}

	@Test
	void sidesBindTheirVocabularyToTheRightDirections() {
		// The whole redesign's contract: "Selling Shops" lists the shops
		// that sell the item (the player buys from them), "Buying Shops"
		// the shops that buy it (the player sells to them).
		assertEquals(ShopDirection.SELLING, Side.SELLING_SHOPS.direction());
		assertEquals(ShopDirection.BUYING, Side.BUYING_SHOPS.direction());
	}

	@Test
	void countLineNamesTheSideNounAndTheItem() {
		assertEquals("8 sellers · Diamond",
				MarketViewerScreen.countLine(Side.SELLING_SHOPS, 8, "Diamond"));
		assertEquals("1 seller · Diamond",
				MarketViewerScreen.countLine(Side.SELLING_SHOPS, 1, "Diamond"));
		assertEquals("3 buyers · Diamond",
				MarketViewerScreen.countLine(Side.BUYING_SHOPS, 3, "Diamond"));
		assertEquals("1 buyer · Diamond",
				MarketViewerScreen.countLine(Side.BUYING_SHOPS, 1, "Diamond"));
	}

	@Test
	void detailLinesCarrySellerPriceStockWhereAndObserved() {
		List<DetailLine> lines = MarketViewerScreen.detailLines(
				offer(observed("400", PriceScope.PER_ITEM, null,
						"8 stacks", StockUnit.STACKS, 32, SourceType.SERVER_RESPONSE), "400"),
				"Sunrise", "Empire", T);
		assertEquals(List.of(
				"Seller: KoenigLuis",
				"Price per item: 400G",
				"Stock: 8 stacks · can buy 32",
				"Where: Sunrise (Empire) · 100, 64, 200",
				"Observed just now"), lines.stream().map(DetailLine::text).toList());
		assertEquals(0xFF000000, lines.get(0).color());
	}

	@Test
	void unknownSellerAndPriceReadAsPlaceholdersNotBlanks() {
		Offer offer = new Offer(shop(null), observed(null, PriceScope.PER_ITEM, null,
				null, StockUnit.UNKNOWN, null, SourceType.MANUAL), null);
		List<String> texts = MarketViewerScreen.detailLines(offer, null, null, T)
				.stream().map(DetailLine::text).toList();
		assertEquals(List.of(
				"Seller: ?",
				"Price per item: —",
				"Where: 100, 64, 200",
				"Observed just now"), texts);
	}

	@Test
	void rawStockNeverRepeatsItsUnitAndBulkKeepsItsTerms() {
		// The raw already carries the unit word: no parsed suffix after it.
		List<String> carried = MarketViewerScreen.detailLines(
				offer(observed(null, PriceScope.PER_ITEM, null,
						"8 stacks", StockUnit.STACKS, null, SourceType.SERVER_RESPONSE), "400"),
				null, null, T).stream().map(DetailLine::text).toList();
		assertEquals("Stock: 8 stacks", carried.get(2));

		// A bare number with a known unit gets the suffix; a bulk price
		// keeps its own terms on the bulk line.
		List<String> bulks = MarketViewerScreen.detailLines(
				offer(observed("25", PriceScope.PER_BULK, 64,
						"64", StockUnit.ITEMS, null, SourceType.SERVER_RESPONSE), "0.39"),
				null, null, T).stream().map(DetailLine::text).toList();
		assertEquals("Stock: 64 items", bulks.get(2));
		assertEquals("Bulk: 64x 25G", bulks.get(3));
	}

	@Test
	void sharedObservationsSaySo() {
		List<String> texts = MarketViewerScreen.detailLines(
				offer(observed("400", PriceScope.PER_ITEM, null,
						null, StockUnit.UNKNOWN, null, SourceType.REMOTE_SHARED), "400"),
				null, null, T).stream().map(DetailLine::text).toList();
		assertEquals("Observed just now · shared", texts.get(texts.size() - 1));
	}

	@Test
	void theGateBlocksOnlyWhenTheNavigatorPlansNoCommand() {
		// A town-resolved shop plans "t spawn" — the gate stays open, so
		// only the two no-command branches have viewer words.
		ShopDestination inNation = new ShopDestination("shop-1", "earthmc.net", "minecraft:overworld",
				new BlockPos(100, 64, 200), null, "KoenigLuis", null, "Empire", T);
		assertEquals("No spawn needed — you are already in Empire",
				MarketViewerScreen.teleportBlockReason(inNation, "empire"));
		ShopDestination unknown = new ShopDestination("shop-1", "earthmc.net", "minecraft:overworld",
				new BlockPos(100, 64, 200), null, "KoenigLuis", null, null, T);
		assertEquals("No spawn command — this shop's town and nation are unknown",
				MarketViewerScreen.teleportBlockReason(unknown, null));
	}
}
