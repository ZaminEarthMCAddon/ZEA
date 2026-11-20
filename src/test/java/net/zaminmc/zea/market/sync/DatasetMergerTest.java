package net.zaminmc.zea.market.sync;

import net.minecraft.core.BlockPos;
import net.zaminmc.zea.market.MarketData;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatasetMergerTest {
	private static final Instant LOCAL_T = Instant.ofEpochMilli(1_700_000_000_000L);
	private static final String TOKEN = "minecraft:diamond_pickaxe|{}";

	private final MarketData data = new MarketData();

	private static SharedShops.SharedRecord record(String shopId, long observedAt) {
		return new SharedShops.SharedRecord(shopId, "earthmc.net", "minecraft:overworld",
				10, 64, -20, 10, 64, -19, "RemoteOwner", null, null,
				"SELLING", TOKEN, "Diamond Pickaxe", "2", "PER_ITEM",
				null, "47", "UNKNOWN", null, null, 4, null, null,
				List.of("Fortune III"), true, observedAt);
	}

	private void recordLocal(String shopId, Instant at) {
		data.record(shop(shopId), observation(shopId, at));
	}

	private static PhysicalShop shop(String id) {
		return new PhysicalShop(id, "earthmc.net", "minecraft:overworld",
				new BlockPos(10, 64, -20), null, "LocalOwner", null, null, LOCAL_T, LOCAL_T);
	}

	private static ShopObservation observation(String shopId, Instant at) {
		return new ShopObservation(shopId, ShopDirection.SELLING,
				new ItemFingerprint(TOKEN, ItemFingerprint.Fidelity.EXACT),
				"Diamond Pickaxe", new BigDecimal("2"), PriceScope.PER_ITEM, null,
				"50", StockUnit.UNKNOWN, null, null, null, null, null,
				false, List.of(), SourceType.SERVER_RESPONSE, at);
	}

	@Test
	void anUnknownShopJoinsAsShared() {
		DatasetMerger.Result result = DatasetMerger.merge(data, List.of(record("remote|1", LOCAL_T.toEpochMilli())));

		assertEquals(1, result.shopsAdded());
		assertEquals(1, result.observationsAppended());
		PhysicalShop shop = data.shop("remote|1");
		assertNotNull(shop);
		assertEquals("RemoteOwner", shop.owner());
		ShopObservation observation = data.observations("remote|1").get(0);
		assertEquals(SourceType.REMOTE_SHARED, observation.sourceType());
		assertEquals("Fortune III", observation.enchantments().get(0));
	}

	@Test
	void aRemoteNewerObservationAppendsAndKeepsTheLocalHistory() {
		recordLocal("s|1", LOCAL_T);
		long newer = LOCAL_T.plusSeconds(3600).toEpochMilli();

		DatasetMerger.Result result = DatasetMerger.merge(data, List.of(record("s|1", newer)));

		assertEquals(1, result.observationsAppended());
		List<ShopObservation> history = data.observations("s|1");
		assertEquals(2, history.size());
		// The local record stays in the history; the shared one becomes
		// the active advert because it is genuinely the newest.
		assertEquals(SourceType.SERVER_RESPONSE, history.get(0).sourceType());
		assertEquals(SourceType.REMOTE_SHARED, history.get(1).sourceType());
	}

	@Test
	void theSameMeasurementAtTheSameTimeIsADuplicateNotATie() {
		// Same shop, same fingerprint, same timestamp is literally the
		// same measurement — the local one stands, nothing is appended.
		recordLocal("s|1", LOCAL_T);

		DatasetMerger.Result result = DatasetMerger.merge(data, List.of(record("s|1", LOCAL_T.toEpochMilli())));

		assertEquals(0, result.observationsAppended());
		assertEquals(1, result.duplicatesSkipped());
		assertEquals(1, data.observations("s|1").size());
		assertEquals(SourceType.SERVER_RESPONSE, data.observations("s|1").get(0).sourceType());
	}

	@Test
	void aTimestampTieBetweenDifferentMeasurementsKeepsTheLocalOne() {
		recordLocal("s|1", LOCAL_T);
		// A different variant measured at the same millisecond: the
		// provenance rule decides — local wins the tie.
		SharedShops.SharedRecord other = new SharedShops.SharedRecord("s|1", "earthmc.net",
				"minecraft:overworld", 10, 64, -20, null, null, null, "RemoteOwner", null, null,
				"SELLING", "minecraft:diamond_pickaxe|{\"minecraft:enchantments\":1}",
				"Enchanted Pickaxe", "2", "PER_ITEM", null, null, "UNKNOWN",
				null, null, null, null, null, List.of(), false, LOCAL_T.toEpochMilli());

		DatasetMerger.Result result = DatasetMerger.merge(data, List.of(other));

		assertEquals(0, result.observationsAppended());
		assertEquals(1, result.tiesSkipped());
		assertEquals(1, data.observations("s|1").size());
	}

	@Test
	void anOlderRemoteRecordIsSkippedAsStale() {
		recordLocal("s|1", LOCAL_T);
		long older = LOCAL_T.minusSeconds(3600).toEpochMilli();

		DatasetMerger.Result result = DatasetMerger.merge(data, List.of(record("s|1", older)));

		assertEquals(1, result.staleSkipped());
		assertEquals(1, data.observations("s|1").size());
	}

	@Test
	void reMergingTheSameSnapshotAppendsNothingTwice() {
		List<SharedShops.SharedRecord> records = List.of(record("s|1", LOCAL_T.plusSeconds(60).toEpochMilli()));

		DatasetMerger.Result first = DatasetMerger.merge(data, records);
		assertEquals(1, first.observationsAppended());

		DatasetMerger.Result second = DatasetMerger.merge(data, records);
		assertEquals(1, second.duplicatesSkipped());
		assertEquals(0, second.observationsAppended());
		assertEquals(1, data.observations("s|1").size());
	}

	@Test
	void anOlderRemoteRecordCannotResurrectOverANewerLocalSignAdvert() {
		// The newest local observation of any source decides: a sign
		// advert newer than the shared record keeps the shop's active
		// state local.
		recordLocal("s|1", LOCAL_T);
		data.record(shop("s|1"), new ShopObservation("s|1", ShopDirection.SELLING,
				new ItemFingerprint("diamond_pickaxe", ItemFingerprint.Fidelity.DISPLAY_ONLY),
				"Diamond Pickaxe", null, null, null, "36", StockUnit.UNKNOWN,
				null, null, null, null, null, false, List.of(),
				SourceType.SIGN_ONLY, LOCAL_T.plusSeconds(7200)));

		DatasetMerger.Result result = DatasetMerger.merge(data,
				List.of(record("s|1", LOCAL_T.plusSeconds(3600).toEpochMilli())));

		assertEquals(1, result.staleSkipped());
		assertEquals(2, data.observations("s|1").size());
		assertTrue(data.observations("s|1").stream()
				.allMatch(observation -> observation.sourceType() != SourceType.REMOTE_SHARED));
	}
}
