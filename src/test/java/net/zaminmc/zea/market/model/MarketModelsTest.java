package net.zaminmc.zea.market.model;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarketModelsTest {
	private static final Instant T = Instant.ofEpochMilli(1_000_000L);

	@Test
	void shopIdentityIsStableAcrossObservations() {
		String first = PhysicalShop.idOf("earthmc.net", "minecraft_overworld",
				new BlockPos(100, 64, -200), new BlockPos(101, 64, -200));
		String second = PhysicalShop.idOf("earthmc.net", "minecraft_overworld",
				new BlockPos(100, 64, -200), new BlockPos(101, 64, -200));

		assertEquals(first, second);
	}

	@Test
	void changingOwnerKeepsTheShopIdentity() {
		String before = PhysicalShop.idOf("earthmc.net", "minecraft_overworld",
				new BlockPos(100, 64, -200), null);
		String after = PhysicalShop.idOf("earthmc.net", "minecraft_overworld",
				new BlockPos(100, 64, -200), null);

		assertEquals(before, after);
	}

	@Test
	void differentPlacesAreDifferentShops() {
		String a = PhysicalShop.idOf("earthmc.net", "minecraft_overworld",
				new BlockPos(100, 64, -200), null);
		String b = PhysicalShop.idOf("earthmc.net", "minecraft_overworld",
				new BlockPos(100, 64, -201), null);
		String c = PhysicalShop.idOf("earthmc.net", "earthmc_moon",
				new BlockPos(100, 64, -200), null);
		String d = PhysicalShop.idOf("other.host", "minecraft_overworld",
				new BlockPos(100, 64, -200), null);

		assertNotEquals(a, b);
		assertNotEquals(a, c);
		assertNotEquals(a, d);
	}

	@Test
	void aKnownChestDisambiguatesTheIdentity() {
		String bare = PhysicalShop.idOf("earthmc.net", "minecraft_overworld",
				new BlockPos(100, 64, -200), null);
		String withChest = PhysicalShop.idOf("earthmc.net", "minecraft_overworld",
				new BlockPos(100, 64, -200), new BlockPos(100, 64, -201));

		assertNotEquals(bare, withChest);
	}

	@Test
	void revisitingUpdatesOwnerAndStampButKeepsIdentity() {
		PhysicalShop shop = new PhysicalShop("id", "earthmc.net", "minecraft_overworld",
				new BlockPos(0, 64, 0), null, "Abasing", null, T, T);

		PhysicalShop revisited = shop.revisited("Pelle_Nomme", "France", Instant.ofEpochMilli(2_000_000L));

		assertEquals(shop.shopId(), revisited.shopId());
		assertEquals("Pelle_Nomme", revisited.owner());
		assertEquals("France", revisited.nation());
		assertEquals(Instant.ofEpochMilli(2_000_000L), revisited.lastObservedAt());
	}

	@Test
	void revisitingKeepsThePreviousOwnerWhenUnreported() {
		PhysicalShop shop = new PhysicalShop("id", "earthmc.net", "minecraft_overworld",
				new BlockPos(0, 64, 0), null, "Abasing", null, T, T);

		assertEquals("Abasing", shop.revisited(null, null, T).owner());
	}

	@Test
	void sellingShopsMakeThePlayerBuy() {
		assertEquals("buy", ShopDirection.SELLING.playerVerb());
	}

	@Test
	void buyingShopsMakeThePlayerSell() {
		assertEquals("sell", ShopDirection.BUYING.playerVerb());
	}

	@Test
	void exactFingerprintsRequireAToken() {
		assertThrows(IllegalArgumentException.class, () -> new ItemFingerprint("", ItemFingerprint.Fidelity.EXACT));
		assertTrue(new ItemFingerprint("minecraft:diamond_pickaxe|enchantments=efficiency5", ItemFingerprint.Fidelity.EXACT).isExact());
	}

	@Test
	void displayOnlyFingerprintsNeverExactMatch() {
		assertFalse(new ItemFingerprint("diamond_pickaxe", ItemFingerprint.Fidelity.DISPLAY_ONLY).isExact());
		assertFalse(ItemFingerprint.unknown().isExact());
	}

	@Test
	void observationsRejectNegativePrices() {
		assertThrows(IllegalArgumentException.class,
				() -> observation(new BigDecimal("-1"), 7, null, null));
	}

	@Test
	void observationsRejectImpossibleQuantities() {
		assertThrows(IllegalArgumentException.class,
				() -> observation(new BigDecimal("1"), -1, null, null));
		assertThrows(IllegalArgumentException.class,
				() -> observation(new BigDecimal("1"), 7, -5, null));
		assertThrows(IllegalArgumentException.class,
				() -> observation(new BigDecimal("1"), 7, null, -2));
	}

	private static ShopObservation observation(BigDecimal price, Integer bulkSize,
											Integer maxBuyBulks, Integer maxSellItems) {
		return new ShopObservation("id", ShopDirection.SELLING, ItemFingerprint.unknown(),
				"Diamond Pickaxe", price, PriceScope.PER_BULK, bulkSize, "78", StockUnit.UNKNOWN,
				maxBuyBulks, maxSellItems, false, SourceType.SERVER_RESPONSE, T);
	}
}
