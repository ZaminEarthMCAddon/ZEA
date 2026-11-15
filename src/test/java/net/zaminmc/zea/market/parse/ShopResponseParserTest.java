package net.zaminmc.zea.market.parse;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.zaminmc.zea.VanillaBootstrap;
import net.zaminmc.zea.chat.model.CapturedMessage;
import net.zaminmc.zea.market.ItemFingerprints;
import net.zaminmc.zea.market.model.ItemFingerprint;
import net.zaminmc.zea.market.model.PriceScope;
import net.zaminmc.zea.market.model.ShopDirection;
import net.zaminmc.zea.market.model.ShopResponse;
import net.zaminmc.zea.market.model.StockUnit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

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
}
