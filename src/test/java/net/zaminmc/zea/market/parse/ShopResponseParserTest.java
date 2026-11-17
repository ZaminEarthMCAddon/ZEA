package net.zaminmc.zea.market.parse;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.zaminmc.zea.VanillaBootstrap;
import net.zaminmc.zea.chat.model.CapturedMessage;
import net.zaminmc.zea.market.ItemFingerprints;
import net.zaminmc.zea.market.model.ItemFingerprint;
import net.zaminmc.zea.market.model.PriceScope;
import net.zaminmc.zea.market.model.ShopDirection;
import net.zaminmc.zea.market.model.ShopResponse;
import net.zaminmc.zea.market.model.StockUnit;
import net.zaminmc.zea.market.pricing.PriceMath;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShopResponseParserTest {
	private static final Instant T = Instant.ofEpochMilli(1_000_000L);
	private final ShopResponseParser parser = new ShopResponseParser();

	@BeforeAll
	static void boot() {
		VanillaBootstrap.ensure();
	}

	private static CapturedMessage message(String text) {
		return CapturedMessage.of(Component.literal(text), T);
	}

	@Test
	void parsesTheSellingShopResponse() {
		var response = parser.parse(List.of(message("""
				Shop Information:
				Owner: Pelle_Nomme
				Item: Block of Diamond [Item Preview]
				Stock: 78
				Price per 7x Block of Diamond - 1G
				This shop is SELLING items.

				Enter in chat, how many bulks you wish to BUY.
				There are 7 items in each bulk and you can buy 64 bulks.
				Enter all in chat to buy all.""")), T);

		ShopResponse shop = response.orElseThrow();
		assertEquals("Pelle_Nomme", shop.owner());
		assertSame(ShopDirection.SELLING, shop.direction());
		assertEquals("Block of Diamond", shop.itemDisplay());
		assertNull(shop.preview());
		assertEquals(new BigDecimal("1"), shop.price());
		assertSame(PriceScope.PER_BULK, shop.priceScope());
		assertEquals(7, shop.bulkSize());
		assertEquals("78", shop.stockRaw());
		assertSame(StockUnit.UNKNOWN, shop.stockUnit());
		assertEquals(64, shop.maxBuyBulks());
		assertNull(shop.maxSellItems());
		assertTrue(shop.supportsAll());
		assertEquals(ShopResponse.Completeness.COMPLETE, shop.completeness());
	}

	@Test
	void parsesTheBuyingShopResponse() {
		var response = parser.parse(List.of(message("""
				Owner: Pelle_Nomme
				Item: Netherite Ingot [Item Preview]
				Space: 38
				Price per Netherite Ingot - 17G
				This shop is BUYING items.

				Enter in chat, how much you wish to SELL.
				You can sell 0.
				Enter all in chat, to sell all.""")), T);

		ShopResponse shop = response.orElseThrow();
		assertSame(ShopDirection.BUYING, shop.direction());
		assertEquals(new BigDecimal("17"), shop.price());
		assertSame(PriceScope.PER_ITEM, shop.priceScope());
		assertNull(shop.bulkSize());
		assertEquals("38", shop.stockRaw());
		assertSame(StockUnit.SPACE, shop.stockUnit());
		assertEquals(0, shop.maxSellItems());
		assertTrue(shop.supportsAll());
		assertEquals(ShopResponse.Completeness.COMPLETE, shop.completeness());
	}

	@Test
	void parsesLinesThatArriveAsSeparateMessages() {
		var response = parser.parse(List.of(
				message("Shop Information:"),
				message("Owner: Pelle_Nomme"),
				message("Item: Block of Diamond [Item Preview]"),
				message("Stock: 78"),
				message("Price per 7x Block of Diamond - 1G"),
				message("This shop is SELLING items."),
				message("There are 7 items in each bulk and you can buy 64 bulks."),
				message("Enter all in chat to buy all.")), T);

		ShopResponse shop = response.orElseThrow();
		assertEquals(ShopResponse.Completeness.COMPLETE, shop.completeness());
		assertEquals(7, shop.bulkSize());
	}

	@Test
	void extractsTheItemPreviewFromTheHover() {
		ItemStackTemplate template = new ItemStackTemplate(Items.DIAMOND_PICKAXE, 1);
		Component itemLine = Component.literal("Item: Enchanted Pickaxe ")
				.append(Component.literal("[Item Preview]")
						.withStyle(style -> style.withHoverEvent(new HoverEvent.ShowItem(template))));
		CapturedMessage withHover = CapturedMessage.of(itemLine, T);

		var response = parser.parse(List.of(
				message("Owner: Pelle_Nomme"),
				withHover,
				message("Price per 7x Enchanted Pickaxe - 2G"),
				message("This shop is SELLING items.")), T);

		ShopResponse shop = response.orElseThrow();
		assertEquals("Enchanted Pickaxe", shop.itemDisplay());
		assertTrue(shop.preview() != null && shop.preview().item().value() == Items.DIAMOND_PICKAXE);
		assertTrue(ItemFingerprints.ofTemplate(shop.preview()).isExact());
	}

	@Test
	void unparseablePriceDegradesTheResponseToPartial() {
		var response = parser.parse(List.of(message("""
				Owner: Pelle_Nomme
				Item: Block of Diamond [Item Preview]
				Price per 7x Block of Diamond - GG
				This shop is SELLING items.""")), T);

		ShopResponse shop = response.orElseThrow();
		assertEquals(ShopResponse.Completeness.PARTIAL, shop.completeness());
		assertNull(shop.price());
	}

	@Test
	void declinesWithoutTheDirectionAnchor() {
		assertTrue(parser.parse(List.of(message("""
				Owner: Pelle_Nomme
				Item: Block of Diamond [Item Preview]
				Price per 7x Block of Diamond - 1G""")), T).isEmpty());
	}

	@Test
	void declinesWithoutAnOwnerOrItem() {
		assertTrue(parser.parse(List.of(message("This shop is SELLING items.")), T).isEmpty());
	}

	@Test
	void selectsOnlyShopShapedLinesFromAChatRun() {
		assertNull(ShopResponseParser.shopLines(message("[Local] Abasing: hi")));
		var lines = ShopResponseParser.shopLines(message("""
				Owner: Pelle_Nomme
				hello world
				Stock: 78"""));
		assertEquals(List.of("Owner: Pelle_Nomme", "Stock: 78"), lines);
	}

	@Test
	void parsesDecimalPrices() {
		var response = parser.parse(List.of(message("""
				Owner: A
				Item: Oak Log [Item Preview]
				Price per Oak Log - 1.5G
				This shop is SELLING items.""")), T);

		assertEquals(new BigDecimal("1.5"), response.orElseThrow().price());
	}

	@Test
	void parsesPerBulkPriceLinesOfDifferentBulkSizes() {
		var response = parser.parse(List.of(message("""
				Owner: A
				Item: Oak Log [Item Preview]
				Price per 64x Oak Log - 1G
				This shop is SELLING items.
				There are 64 items in each bulk and you can buy 64 bulks.""")), T);

		ShopResponse shop = response.orElseThrow();
		assertEquals(64, shop.bulkSize());
		assertEquals(new BigDecimal("1"), shop.price());
	}

	@Test
	void supportsAllIsOnlyClaimedWhenTheServerSaysSo() {
		var withoutAll = parser.parse(List.of(message("""
				Owner: A
				Item: Oak Log [Item Preview]
				Price per 64x Oak Log - 1G
				This shop is SELLING items.""")), T);

		assertFalse(withoutAll.orElseThrow().supportsAll());
	}

	// The EarthMC shop-response fixtures below are mandatory parser
	// regression tests: they pin the two observed styles (boxed and plain)
	// and every field the protocol carries, exactly as the server words
	// them.

	/** Fixture A — a bulk SELLING shop, boxed style. */
	private static final String FIXTURE_A = """
			+---------------------------------------------------+
			| Shop Information:
			| Owner: Pelle_Nomme
			| Item: Observer   [Item Preview]
			| Stock: 28
			| Price per 8x Observer - 1G
			| This shop is SELLING items.
			+---------------------------------------------------+
			Enter in chat, how many bulks you wish to BUY.
			There are 8 items in each bulk and you can buy 28 bulks.
			Enter all in chat to buy all.""";

	/** Fixture B — a bulk BUYING shop, boxed style. */
	private static final String FIXTURE_B = """
			+---------------------------------------------------+
			| Shop Information:
			| Owner: Pelle_Nomme
			| Item: Observer   [Item Preview]
			| Space: 2
			| Price per 12x Observer - 1G
			| This shop is BUYING items.
			+---------------------------------------------------+
			Enter in chat, how many bulk you wish to SELL.
			There are 12 items per bulk and you can sell 0 bulks.
			Enter all in chat to sell all.""";

	/** Fixture C's block without the item line, which the hover test carries. */
	private static final String FIXTURE_C = """
			+---------------------------------------------------+
			| Shop Information:
			| Owner: Ayskow
			| 100% remaining.
			| Stock: 47
			| Price per Diamond Pickaxe - 2G
			| This shop is SELLING items.
			+--------------------Enchantments--------------------+
			| Fortune III
			| Unbreaking III
			| Efficiency V
			+---------------------------------------------------+
			Enter in chat, how many you wish to BUY.
			You can buy 4.
			Enter all to buy them all.""";

	/** Fixture D — a per-item SELLING shop with an enchanted item, plain style. */
	private static final String FIXTURE_D = """
			Owner: Ayskow
			Item: Diamond Chestplate   [Item Preview]
			100% remaining.
			Stock: 51
			Price per Diamond Chestplate - 2G
			This shop is SELLING items.

			Enchantments:
			Thorns II
			Protection IV
			Unbreaking III""";

	/** Fixture E — a per-item SELLING shop whose item carries five enchantments. */
	private static final String FIXTURE_E = """
			Owner: karakan31
			Item: Bow   [Item Preview]
			100% remaining.
			Stock: 30
			Price per Bow - 6G
			This shop is SELLING items.

			Enchantments:
			Flame I
			Punch II
			Unbreaking III
			Power V
			Infinity I""";

	@Test
	void parsesFixtureATheBoxedBulkSellingShop() {
		ShopResponse shop = parser.parse(List.of(message(FIXTURE_A)), T).orElseThrow();

		assertEquals("Pelle_Nomme", shop.owner());
		assertSame(ShopDirection.SELLING, shop.direction());
		assertEquals("Observer", shop.itemDisplay());
		assertEquals("28", shop.stockRaw());
		assertSame(StockUnit.UNKNOWN, shop.stockUnit());
		assertEquals(8, shop.bulkSize());
		assertEquals(new BigDecimal("1"), shop.price());
		assertSame(PriceScope.PER_BULK, shop.priceScope());
		assertEquals(28, shop.maxBuyBulks());
		assertNull(shop.maxBuyItems());
		assertNull(shop.maxSellBulks());
		assertNull(shop.remainingPercent());
		assertTrue(shop.supportsAll());
		assertTrue(shop.enchantments().isEmpty());
		assertEquals(ShopResponse.Completeness.COMPLETE, shop.completeness());
		// 8 Observers for 1G — the documented example unit price.
		assertEquals(new BigDecimal("0.125000"),
				PriceMath.normalize(shop.price(), shop.priceScope(), shop.bulkSize()).orElseThrow());
	}

	@Test
	void parsesFixtureBTheBoxedBulkBuyingShop() {
		ShopResponse shop = parser.parse(List.of(message(FIXTURE_B)), T).orElseThrow();

		assertEquals("Pelle_Nomme", shop.owner());
		assertSame(ShopDirection.BUYING, shop.direction());
		assertEquals("Observer", shop.itemDisplay());
		// Space, not Stock: the shop's capacity, kept verbatim and distinct.
		assertEquals("2", shop.stockRaw());
		assertSame(StockUnit.SPACE, shop.stockUnit());
		assertEquals(12, shop.bulkSize());
		assertEquals(new BigDecimal("1"), shop.price());
		assertSame(PriceScope.PER_BULK, shop.priceScope());
		assertEquals(0, shop.maxSellBulks());
		assertNull(shop.maxBuyBulks());
		assertNull(shop.maxSellItems());
		assertTrue(shop.supportsAll());
		assertEquals(ShopResponse.Completeness.COMPLETE, shop.completeness());
		// 12 Observers for 1G — the documented example unit price.
		assertEquals(new BigDecimal("0.083333"),
				PriceMath.normalize(shop.price(), shop.priceScope(), shop.bulkSize()).orElseThrow());
	}

	@Test
	void parsesFixtureCTheBoxedEnchantedSellingShop() {
		// The boxed block with the item line rebuilt as a component: the
		// item preview travels in the hover event, exactly as the server
		// sends it.
		ItemStackTemplate template = enchanted(Items.DIAMOND_PICKAXE, Map.of(
				enchant("fortune"), 3, enchant("unbreaking"), 3, enchant("efficiency"), 5));
		Component itemLine = Component.literal("Item: Diamond Pickaxe   ")
				.append(Component.literal("[Item Preview]")
						.withStyle(style -> style.withHoverEvent(new HoverEvent.ShowItem(template))));
		ShopResponse shop = parser.parse(
				List.of(CapturedMessage.of(itemLine, T), message(FIXTURE_C)), T).orElseThrow();

		assertEquals("Ayskow", shop.owner());
		assertSame(ShopDirection.SELLING, shop.direction());
		assertEquals("Diamond Pickaxe", shop.itemDisplay());
		assertEquals("47", shop.stockRaw());
		assertSame(StockUnit.UNKNOWN, shop.stockUnit());
		// A per-item price needs no bulk size, and the response is still
		// complete.
		assertNull(shop.bulkSize());
		assertEquals(new BigDecimal("2"), shop.price());
		assertSame(PriceScope.PER_ITEM, shop.priceScope());
		// The server's maximum purchase is 4 items — never confused with
		// the reported stock of 47.
		assertEquals(4, shop.maxBuyItems());
		assertNull(shop.maxBuyBulks());
		assertEquals(100, shop.remainingPercent());
		assertEquals(List.of("Fortune III", "Unbreaking III", "Efficiency V"), shop.enchantments());
		assertEquals(ShopResponse.Completeness.COMPLETE, shop.completeness());
		// The preview is the actual item, carried through verbatim: the
		// parser hands back the hover's own template, whose component
		// patch carries the three enchantments. Reading the component
		// back out goes through the item's default components, which this
		// bare test JVM never binds — the exact-fingerprint behavior of
		// the same template is covered below.
		assertTrue(shop.preview() != null && shop.preview().item().value() == Items.DIAMOND_PICKAXE);
		assertSame(template, shop.preview());
	}

	@Test
	void parsesFixtureDThePlainEnchantedSellingShop() {
		ShopResponse shop = parser.parse(List.of(message(FIXTURE_D)), T).orElseThrow();

		assertEquals("Ayskow", shop.owner());
		assertSame(ShopDirection.SELLING, shop.direction());
		assertEquals("Diamond Chestplate", shop.itemDisplay());
		assertEquals("51", shop.stockRaw());
		assertSame(StockUnit.UNKNOWN, shop.stockUnit());
		assertEquals(new BigDecimal("2"), shop.price());
		assertSame(PriceScope.PER_ITEM, shop.priceScope());
		assertEquals(100, shop.remainingPercent());
		assertEquals(List.of("Thorns II", "Protection IV", "Unbreaking III"), shop.enchantments());
		assertEquals(ShopResponse.Completeness.COMPLETE, shop.completeness());
	}

	@Test
	void parsesFixtureEThePlainBowWithFiveEnchantments() {
		ShopResponse shop = parser.parse(List.of(message(FIXTURE_E)), T).orElseThrow();

		assertEquals("karakan31", shop.owner());
		assertSame(ShopDirection.SELLING, shop.direction());
		assertEquals("Bow", shop.itemDisplay());
		assertEquals("30", shop.stockRaw());
		assertEquals(new BigDecimal("6"), shop.price());
		assertSame(PriceScope.PER_ITEM, shop.priceScope());
		assertEquals(100, shop.remainingPercent());
		// All five enchantments, in the server's own order.
		assertEquals(List.of("Flame I", "Punch II", "Unbreaking III", "Power V", "Infinity I"),
				shop.enchantments());
		assertEquals(ShopResponse.Completeness.COMPLETE, shop.completeness());
	}

	@Test
	void parsesBoxedFixturesDeliveredLineByLine() {
		List<CapturedMessage> lines = FIXTURE_A.lines()
				.map(line -> message(line))
				.toList();
		ShopResponse shop = parser.parse(lines, T).orElseThrow();

		assertEquals("Pelle_Nomme", shop.owner());
		assertEquals("Observer", shop.itemDisplay());
		assertEquals(8, shop.bulkSize());
		assertEquals(28, shop.maxBuyBulks());
		assertEquals(ShopResponse.Completeness.COMPLETE, shop.completeness());
	}

	@Test
	void anUnencodablePreviewDegradesInsteadOfGuessing() {
		// The enchanted template's components reference the data-driven
		// enchantment registry, which this bare test JVM does not load:
		// the patch cannot be canonically serialized. The fingerprint must
		// then declare no exact identity — never an identity that silently
		// dropped the enchantments and would match a different product.
		// Production clients hold the server's registry view and encode
		// the same payload exactly.
		ItemStackTemplate template = enchanted(Items.DIAMOND_PICKAXE, Map.of(
				enchant("fortune"), 3, enchant("unbreaking"), 3, enchant("efficiency"), 5));
		ItemFingerprint fingerprint = ItemFingerprints.ofTemplate(template);

		assertFalse(fingerprint.isExact());
		assertTrue(fingerprint.token().isEmpty());
	}

	@Test
	void meaningfulComponentDifferencesAreDistinctVariants() {
		// A damaged pickaxe is a different product from an undamaged one,
		// and both are exact identities: damage encodes against the
		// built-in component registry alone.
		ItemFingerprint plain = ItemFingerprints.ofTemplate(new ItemStackTemplate(Items.DIAMOND_PICKAXE, 1));
		ItemFingerprint damaged = ItemFingerprints.ofTemplate(new ItemStackTemplate(Items.DIAMOND_PICKAXE,
				DataComponentPatch.builder().set(DataComponents.DAMAGE, 543).build()));

		assertTrue(plain.isExact());
		assertTrue(damaged.isExact());
		assertTrue(plain.token().startsWith("minecraft:diamond_pickaxe|"));
		assertFalse(plain.token().equals(damaged.token()));
	}

	/** A direct-holder enchantment fixture; no registry participation. */
	private static Holder<Enchantment> enchant(String name) {
		String display = Character.toUpperCase(name.charAt(0)) + name.substring(1);
		Enchantment enchantment = new Enchantment(Component.literal(display),
				Enchantment.definition(HolderSet.empty(), 10, 10,
						Enchantment.constantCost(1), Enchantment.constantCost(1), 0, EquipmentSlotGroup.ANY),
				HolderSet.empty(), DataComponentMap.EMPTY);
		return Holder.direct(enchantment);
	}

	/** A template carrying the given enchantments as real component data. */
	private static ItemStackTemplate enchanted(Item item, Map<Holder<Enchantment>, Integer> enchantments) {
		ItemEnchantments.Mutable builder = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
		enchantments.forEach(builder::set);
		return new ItemStackTemplate(item, DataComponentPatch.builder()
				.set(DataComponents.ENCHANTMENTS, builder.toImmutable()).build());
	}
}
