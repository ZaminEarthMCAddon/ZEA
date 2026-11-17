package net.zaminmc.zea.market.index;

import net.minecraft.core.BlockPos;
import net.zaminmc.zea.market.MarketData;
import net.zaminmc.zea.market.index.ItemVariantIndex.Offer;
import net.zaminmc.zea.market.index.ItemVariantIndex.Variant;
import net.zaminmc.zea.market.model.ItemFingerprint;
import net.zaminmc.zea.market.model.PhysicalShop;
import net.zaminmc.zea.market.model.PriceScope;
import net.zaminmc.zea.market.model.ShopDirection;
import net.zaminmc.zea.market.model.ShopObservation;
import net.zaminmc.zea.market.model.SourceType;
import net.zaminmc.zea.market.model.StockUnit;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemVariantIndexTest {
	private static final Instant T = Instant.ofEpochMilli(1_000_000L);
	private static final String ENCHANTED = "minecraft:diamond_pickaxe|{\"minecraft:enchantments\":1}";
	private static final String PLAIN = "minecraft:diamond_pickaxe|{}";

	private final MarketData data = new MarketData();

	private static PhysicalShop shop(String id, String owner) {
		int hash = Math.abs(id.hashCode());
		return new PhysicalShop(id, "earthmc.net", "minecraft:overworld",
				new BlockPos(hash % 1000, 64, hash % 997), null, owner, null, T, T);
	}

	private static ShopObservation verified(String shopId, String token, String display,
			ShopDirection direction, String price, PriceScope scope, Integer bulk, Instant at) {
		return new ShopObservation(shopId, direction,
				new ItemFingerprint(token, ItemFingerprint.Fidelity.EXACT),
				display, price == null ? null : new BigDecimal(price), scope, bulk,
				"78", StockUnit.UNKNOWN, null, null, null, null, null,
				false, List.of(), SourceType.SERVER_RESPONSE, at);
	}

	@Test
	void identicalVariantsAcrossOwnersFormOneTile() {
		// The spec's duplicate-shop aggregation example: three owners sell
		// the same enchanted pickaxe at 2G, 2G and 2.5G; a fourth sells a
		// plain one. Two tiles, not four.
		data.record(shop("s-a", "Player A"), verified("s-a", ENCHANTED,
				"Diamond Pickaxe", ShopDirection.SELLING, "2", PriceScope.PER_ITEM, null, T));
		data.record(shop("s-b", "Player B"), verified("s-b", ENCHANTED,
				"Diamond Pickaxe", ShopDirection.SELLING, "2", PriceScope.PER_ITEM, null, T));
		data.record(shop("s-c", "Player C"), verified("s-c", ENCHANTED,
				"Diamond Pickaxe", ShopDirection.SELLING, "2.5", PriceScope.PER_ITEM, null, T));
		data.record(shop("s-d", "Player D"), verified("s-d", PLAIN,
				"Diamond Pickaxe", ShopDirection.SELLING, "1", PriceScope.PER_ITEM, null, T));

		List<Variant> variants = ItemVariantIndex.variants(data);
		assertEquals(2, variants.size());

		Variant enchanted = variants.stream()
				.filter(variant -> variant.token().equals(ENCHANTED)).findFirst().orElseThrow();
		assertEquals(3, enchanted.offers().size());
		assertEquals(3, enchanted.owners().size());
		// Every physical shop stays independently present.
		assertEquals(List.of("Player A", "Player B", "Player C"), enchanted.owners());
		// The plain pickaxe is a different variant of the same item.
		Variant plain = variants.stream()
				.filter(variant -> variant.token().equals(PLAIN)).findFirst().orElseThrow();
		assertEquals(1, plain.offers().size());
		assertEquals("Player D", plain.owners().get(0));
	}

	@Test
	void theBuyFromIndexIsOwnerBalanced() {
		// Player C runs three shops; the owner-balanced index gives them
		// one vote — their cheapest — not three.
		data.record(shop("c1", "Player C"), verified("c1", ENCHANTED,
				"Diamond Pickaxe", ShopDirection.SELLING, "3", PriceScope.PER_ITEM, null, T));
		data.record(shop("c2", "Player C"), verified("c2", ENCHANTED,
				"Diamond Pickaxe", ShopDirection.SELLING, "2", PriceScope.PER_ITEM, null, T));
		data.record(shop("c3", "Player C"), verified("c3", ENCHANTED,
				"Diamond Pickaxe", ShopDirection.SELLING, "4", PriceScope.PER_ITEM, null, T));
		data.record(shop("s-a", "Player A"), verified("s-a", ENCHANTED,
				"Diamond Pickaxe", ShopDirection.SELLING, "2.5", PriceScope.PER_ITEM, null, T));

		Variant variant = ItemVariantIndex.variants(data).get(0);
		assertEquals(4, variant.offers().size());
		VariantPricing.SideStats buyFrom = VariantPricing.stats(variant.offers(), ShopDirection.SELLING);

		// Cheapest purchase wins the vote of each owner: C 2, A 2.5.
		// Worst is an aggregate statistic too, so it is the worst owner
		// vote (2.5) — not one of C's individual shops (4).
		assertEquals(new BigDecimal("2"), buyFrom.best());
		assertEquals(new BigDecimal("2.5"), buyFrom.worst());
		// Two owners is below the median threshold: no middle is claimed.
		assertNull(buyFrom.median());
		assertEquals(2, buyFrom.owners());
		assertEquals(4, buyFrom.pricedShops());
		assertEquals(4, buyFrom.shops());
	}

	@Test
	void theMedianRunsOverOwnerVotesNotShops() {
		// Five owners, five prices; one of them operates two shops, whose
		// worse price must not drag the median.
		data.record(shop("a", "A"), verified("a", ENCHANTED, "Diamond Pickaxe",
				ShopDirection.SELLING, "1", PriceScope.PER_ITEM, null, T));
		data.record(shop("b", "B"), verified("b", ENCHANTED, "Diamond Pickaxe",
				ShopDirection.SELLING, "2", PriceScope.PER_ITEM, null, T));
		data.record(shop("c1", "C"), verified("c1", ENCHANTED, "Diamond Pickaxe",
				ShopDirection.SELLING, "3", PriceScope.PER_ITEM, null, T));
		data.record(shop("c2", "C"), verified("c2", ENCHANTED, "Diamond Pickaxe",
				ShopDirection.SELLING, "9", PriceScope.PER_ITEM, null, T));
		data.record(shop("d", "D"), verified("d", ENCHANTED, "Diamond Pickaxe",
				ShopDirection.SELLING, "4", PriceScope.PER_ITEM, null, T));
		data.record(shop("e", "E"), verified("e", ENCHANTED, "Diamond Pickaxe",
				ShopDirection.SELLING, "5", PriceScope.PER_ITEM, null, T));

		Variant variant = ItemVariantIndex.variants(data).get(0);
		VariantPricing.SideStats stats = VariantPricing.stats(variant.offers(), ShopDirection.SELLING);

		// Votes: A 1, B 2, C 3 (best of C's shops), D 4, E 5 → median 3.
		assertEquals(new BigDecimal("3"), stats.median());
		assertEquals(new BigDecimal("1"), stats.best());
		assertEquals(new BigDecimal("5"), stats.worst());
		assertEquals(5, stats.owners());
		assertEquals(6, stats.pricedShops());
	}

	@Test
	void sellToAggregatesTheHighestPayoutAsBest() {
		data.record(shop("s1", "A"), verified("s1", ENCHANTED, "Diamond Pickaxe",
				ShopDirection.BUYING, "2", PriceScope.PER_ITEM, null, T));
		data.record(shop("s2", "B"), verified("s2", ENCHANTED, "Diamond Pickaxe",
				ShopDirection.BUYING, "3", PriceScope.PER_ITEM, null, T));

		Variant variant = ItemVariantIndex.variants(data).get(0);
		VariantPricing.SideStats sellTo = VariantPricing.stats(variant.offers(), ShopDirection.BUYING);

		// Best payout is the highest price the shops pay.
		assertEquals(new BigDecimal("3"), sellTo.best());
		assertEquals(new BigDecimal("2"), sellTo.worst());
		// The two sides never mix: no selling shop exists here.
		VariantPricing.SideStats buyFrom = VariantPricing.stats(variant.offers(), ShopDirection.SELLING);
		assertNull(buyFrom.best());
		assertEquals(0, buyFrom.pricedShops());
		assertEquals(2, buyFrom.shops());
	}

	@Test
	void unpricedOffersAreListedButNeverCounted() {
		data.record(shop("s1", "A"), verified("s1", ENCHANTED, "Diamond Pickaxe",
				ShopDirection.SELLING, "2", PriceScope.PER_ITEM, null, T));
		// Per-bulk without a bulk size cannot normalize.
		data.record(shop("s2", "B"), verified("s2", ENCHANTED, "Diamond Pickaxe",
				ShopDirection.SELLING, "1", PriceScope.PER_BULK, null, T));
		// No price at all.
		data.record(shop("s3", "C"), verified("s3", ENCHANTED, "Diamond Pickaxe",
				ShopDirection.SELLING, null, null, null, T));

		Variant variant = ItemVariantIndex.variants(data).get(0);
		assertEquals(3, variant.offers().size());
		VariantPricing.SideStats stats = VariantPricing.stats(variant.offers(), ShopDirection.SELLING);
		assertEquals(new BigDecimal("2"), stats.best());
		assertEquals(1, stats.pricedShops());
		assertEquals(1, stats.owners());
	}

	@Test
	void signOnlyObservationsNeverJoinTheIndex() {
		data.record(shop("s1", "A"), new ShopObservation("s1", ShopDirection.SELLING,
				new ItemFingerprint("diamond_pickaxe", ItemFingerprint.Fidelity.DISPLAY_ONLY),
				"Diamond Pickaxe", null, null, null, "36", StockUnit.UNKNOWN,
				null, null, null, null, null, false, List.of(),
				SourceType.SIGN_ONLY, T));
		data.record(shop("s2", "B"), new ShopObservation("s2", ShopDirection.SELLING,
				ItemFingerprint.unknown(),
				"Diamond Pickaxe", null, null, null, null, StockUnit.UNKNOWN,
				null, null, null, null, null, false, List.of(),
				SourceType.SIGN_ONLY, T));

		assertEquals(List.of(), ItemVariantIndex.variants(data));
	}

	@Test
	void anOfferCarriesTheShopsLatestObservationOfTheVariant() {
		data.record(shop("s1", "A"), verified("s1", ENCHANTED, "Diamond Pickaxe",
				ShopDirection.SELLING, "3", PriceScope.PER_ITEM, null, T));
		data.record(shop("s1", "A"), verified("s1", ENCHANTED, "Diamond Pickaxe",
				ShopDirection.SELLING, "2", PriceScope.PER_ITEM, null, T.plusSeconds(60)));
		// An older price arriving late must not become the offer.
		data.record(shop("s1", "A"), verified("s1", ENCHANTED, "Diamond Pickaxe",
				ShopDirection.SELLING, "9", PriceScope.PER_ITEM, null, T.plusSeconds(30)));

		Variant variant = ItemVariantIndex.variants(data).get(0);
		assertEquals(1, variant.offers().size());
		Offer offer = variant.offers().get(0);
		assertEquals(new BigDecimal("2"), offer.unitPrice());
	}

	@Test
	void searchCoversNameRegistryEnchantmentsAndOwners() {
		data.record(shop("s1", "Abasing"), new ShopObservation("s1", ShopDirection.SELLING,
				new ItemFingerprint(ENCHANTED, ItemFingerprint.Fidelity.EXACT),
				"Diamond Pickaxe", new BigDecimal("2"), PriceScope.PER_ITEM, null,
				"47", StockUnit.UNKNOWN, null, null, null, null, null,
				false, List.of("Fortune III", "Efficiency V"), SourceType.SERVER_RESPONSE, T));

		String search = ItemVariantIndex.variants(data).get(0).searchText();
		assertTrue(search.contains("diamond pickaxe"));
		assertTrue(search.contains("minecraft:diamond_pickaxe"));
		assertTrue(search.contains("fortune iii"));
		assertTrue(search.contains("abasing"));
	}
}
