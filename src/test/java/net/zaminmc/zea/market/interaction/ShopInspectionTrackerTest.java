package net.zaminmc.zea.market.interaction;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.zaminmc.zea.VanillaBootstrap;
import net.zaminmc.zea.chat.model.CapturedMessage;
import net.zaminmc.zea.market.model.ShopDirection;
import net.zaminmc.zea.market.model.ShopResponse;
import net.zaminmc.zea.market.parse.ShopSignParser;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShopInspectionTrackerTest {
	private static final Instant T = Instant.ofEpochMilli(1_000_000L);
	private static final List<String> SHOP_SIGN_TEXT = List.of(
			"&aAbasing", "Selling 36", "Diamond Pickaxe", "2G each");
	private static final String SELLING_RESPONSE = """
			Shop Information:
			Owner: Pelle_Nomme
			Item: Block of Diamond [Item Preview]
			Stock: 78
			Price per 7x Block of Diamond - 1G
			This shop is SELLING items.
			There are 7 items in each bulk and you can buy 64 bulks.""";

	@BeforeAll
	static void boot() {
		VanillaBootstrap.ensure();
	}

	private static CapturedMessage message(String text, Instant at) {
		return CapturedMessage.of(Component.literal(text), at);
	}

	@Test
	void correlatesAResponseWithTheSignThatCausedIt() {
		List<ShopInspectionTracker.Inspection> out = new ArrayList<>();
		ShopInspectionTracker tracker = new ShopInspectionTracker(out::add);

		tracker.noteSignUse("minecraft:overworld", "earthmc.net",
				new BlockPos(10, 64, -20), new BlockPos(10, 64, -19), SHOP_SIGN_TEXT, T);
		tracker.onMessage(message(SELLING_RESPONSE, T.plusMillis(50)), T.plusMillis(50));
		tracker.tick(T.plusMillis(2_000));

		assertEquals(1, out.size());
		ShopInspectionTracker.Inspection inspection = out.get(0);
		assertTrue(inspection.isServerVerified());
		ShopResponse response = inspection.response();
		assertEquals(ShopDirection.SELLING, response.direction());
		assertEquals("Pelle_Nomme", response.owner());
		assertEquals(new BlockPos(10, 64, -20), inspection.signPos());
		assertEquals(new BlockPos(10, 64, -19), inspection.chestPos());
		assertEquals("minecraft:overworld", inspection.world());
		assertEquals("earthmc.net", inspection.serverId());
	}

	@Test
	void ignoresShopShapedLinesWithoutASignUse() {
		List<ShopInspectionTracker.Inspection> out = new ArrayList<>();
		ShopInspectionTracker tracker = new ShopInspectionTracker(out::add);

		tracker.onMessage(message(SELLING_RESPONSE, T), T);
		tracker.tick(T.plusSeconds(30));

		assertEquals(0, out.size());
	}

	@Test
	void closesTheRunOnTheFirstUnrelatedMessage() {
		List<ShopInspectionTracker.Inspection> out = new ArrayList<>();
		ShopInspectionTracker tracker = new ShopInspectionTracker(out::add);

		tracker.noteSignUse("minecraft:overworld", "earthmc.net",
				new BlockPos(1, 64, 2), null, SHOP_SIGN_TEXT, T);
		tracker.onMessage(message("Owner: Pelle_Nomme", T.plusMillis(10)), T.plusMillis(10));
		tracker.onMessage(message("[Local] Someone: hi", T.plusMillis(20)), T.plusMillis(20));
		// The partial run (no direction anchor yet) is discarded, not
		// emitted; the use stays pending.
		assertEquals(0, out.size());
		tracker.onMessage(message(SELLING_RESPONSE, T.plusMillis(30)), T.plusMillis(30));
		tracker.tick(T.plusSeconds(3));

		assertEquals(1, out.size());
		assertTrue(out.get(0).isServerVerified());
	}

	@Test
	void suppressesAnExactDuplicateResponse() {
		List<ShopInspectionTracker.Inspection> out = new ArrayList<>();
		ShopInspectionTracker tracker = new ShopInspectionTracker(out::add);

		tracker.noteSignUse("minecraft:overworld", "earthmc.net",
				new BlockPos(1, 64, 2), null, SHOP_SIGN_TEXT, T);
		tracker.onMessage(message(SELLING_RESPONSE, T.plusMillis(50)), T.plusMillis(50));
		tracker.tick(T.plusMillis(2_000));
		tracker.onMessage(message(SELLING_RESPONSE, T.plusMillis(2_100)), T.plusMillis(2_100));
		tracker.tick(T.plusMillis(4_000));

		assertEquals(1, out.size());
	}

	@Test
	void aSecondClickWinsTheCorrelationInsideTheWindow() {
		List<ShopInspectionTracker.Inspection> out = new ArrayList<>();
		ShopInspectionTracker tracker = new ShopInspectionTracker(out::add);

		tracker.noteSignUse("minecraft:overworld", "earthmc.net",
				new BlockPos(1, 64, 2), null, SHOP_SIGN_TEXT, T);
		tracker.noteSignUse("minecraft:overworld", "earthmc.net",
				new BlockPos(90, 64, 2), null, SHOP_SIGN_TEXT, T.plusMillis(100));
		tracker.onMessage(message(SELLING_RESPONSE, T.plusMillis(200)), T.plusMillis(200));
		tracker.tick(T.plusSeconds(15));

		// The response belongs to the newest click; the older click can
		// only ever fall back to its own sign text — never to this
		// response.
		assertEquals(2, out.size());
		assertEquals(new BlockPos(90, 64, 2), out.get(0).signPos());
		assertTrue(out.get(0).isServerVerified());
		assertEquals(new BlockPos(1, 64, 2), out.get(1).signPos());
		assertFalse(out.get(1).isServerVerified());
	}

	@Test
	void anExpiredShopSignFallsBackToItsSignText() {
		List<ShopInspectionTracker.Inspection> out = new ArrayList<>();
		ShopInspectionTracker tracker = new ShopInspectionTracker(out::add);

		tracker.noteSignUse("minecraft:overworld", "earthmc.net",
				new BlockPos(1, 64, 2), null, SHOP_SIGN_TEXT, T);
		tracker.tick(T.plusSeconds(11));

		assertEquals(1, out.size());
		ShopInspectionTracker.Inspection inspection = out.get(0);
		assertFalse(inspection.isServerVerified());
		assertNull(inspection.response());
		assertEquals(SHOP_SIGN_TEXT, inspection.signLines());
	}

	@Test
	void anExpiredNonShopSignIsIgnored() {
		List<ShopInspectionTracker.Inspection> out = new ArrayList<>();
		ShopInspectionTracker tracker = new ShopInspectionTracker(out::add);

		tracker.noteSignUse("minecraft:overworld", "earthmc.net",
				new BlockPos(1, 64, 2), null, List.of("[Free] Take something"), T);
		tracker.tick(T.plusSeconds(11));

		assertEquals(0, out.size());
	}

	@Test
	void anUnparseableRunKeepsTheUsePendingForALaterResponse() {
		List<ShopInspectionTracker.Inspection> out = new ArrayList<>();
		ShopInspectionTracker tracker = new ShopInspectionTracker(out::add);

		tracker.noteSignUse("minecraft:overworld", "earthmc.net",
				new BlockPos(1, 64, 2), null, SHOP_SIGN_TEXT, T);
		tracker.onMessage(message("Owner: Pelle_Nomme", T.plusMillis(10)), T.plusMillis(10));
		tracker.tick(T.plusSeconds(2));
		// The server never finished talking; the use is still pending, so
		// a complete later response is still correlated.
		tracker.onMessage(message(SELLING_RESPONSE, T.plusSeconds(3)), T.plusSeconds(3));
		tracker.tick(T.plusSeconds(6));

		assertEquals(1, out.size());
		assertTrue(out.get(0).isServerVerified());
	}

	@Test
	void resetForgetsEverything() {
		List<ShopInspectionTracker.Inspection> out = new ArrayList<>();
		ShopInspectionTracker tracker = new ShopInspectionTracker(out::add);

		tracker.noteSignUse("minecraft:overworld", "earthmc.net",
				new BlockPos(1, 64, 2), null, SHOP_SIGN_TEXT, T);
		tracker.reset();
		tracker.onMessage(message(SELLING_RESPONSE, T.plusMillis(50)), T.plusMillis(50));
		tracker.tick(T.plusSeconds(15));

		assertEquals(0, out.size());
	}

	@Test
	void parsesSignAdverts() {
		var advert = ShopSignParser.parse(SHOP_SIGN_TEXT).orElseThrow();
		assertEquals(ShopDirection.SELLING, advert.direction());
		assertEquals("36", advert.quantity());
		assertEquals(36, advert.quantityOrZero());
		assertEquals("Diamond Pickaxe", advert.itemDisplay());
		assertEquals("2G each", advert.priceRaw());
		assertEquals("Abasing", advert.owner());
	}

	@Test
	void parsesABuyingSignAdvert() {
		var advert = ShopSignParser.parse(List.of("&aPelle", "Buying 13", "Netherite ingot", "17G each"))
				.orElseThrow();
		assertEquals(ShopDirection.BUYING, advert.direction());
		assertEquals("13", advert.quantity());
		assertEquals("Netherite ingot", advert.itemDisplay());
	}

	@Test
	void nonShopSignsAreNotAdverts() {
		assertFalse(ShopSignParser.isShopSign(List.of("[Free] Take something")));
		assertFalse(ShopSignParser.isShopSign(List.of("&aSomeone", "Welcome!")));
		assertTrue(ShopSignParser.parse(List.of("[Free] Take something")).isEmpty());
	}
}
