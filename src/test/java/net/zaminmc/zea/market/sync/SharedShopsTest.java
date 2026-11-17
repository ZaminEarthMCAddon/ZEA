package net.zaminmc.zea.market.sync;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SharedShopsTest {
	private static final String VALID_RECORD = """
			{
			  "shopId": "earthmc.net|minecraft:overworld|10,64,-20",
			  "serverId": "earthmc.net", "world": "minecraft:overworld",
			  "sx": 10, "sy": 64, "sz": -20, "cx": 10, "cy": 64, "cz": -19,
			  "owner": "Ayskow", "nation": "France",
			  "direction": "SELLING", "token": "minecraft:diamond_pickaxe|{}",
			  "itemDisplay": "Diamond Pickaxe", "price": "2", "priceScope": "PER_ITEM",
			  "stockRaw": "47", "stockUnit": "UNKNOWN",
			  "enchantments": ["Fortune III", "Efficiency V"], "supportsAll": false,
			  "observedAt": 1700000000000
			}""";

	@Test
	void parsesValidRecordsAndCarriesEveryField() {
		String dataset = "{\"version\": 1, \"records\": [" + VALID_RECORD + "]}";

		List<SharedShops.SharedRecord> records = SharedShops.parse(dataset, "test");

		assertEquals(1, records.size());
		SharedShops.SharedRecord record = records.get(0);
		assertEquals("Ayskow", record.owner());
		var observation = SharedShops.toObservation(record);
		assertEquals(new BigDecimal("2"), observation.price());
		assertEquals(2, observation.enchantments().size());
		assertEquals("minecraft:diamond_pickaxe|{}", observation.fingerprint().token());
		assertTrue(observation.fingerprint().isExact());
		var shop = SharedShops.toShop(record);
		assertEquals("France", shop.nation());
		assertEquals(10, shop.signPos().getX());
		assertEquals(-19, shop.chestPos().getZ());
	}

	@Test
	void malformedRecordsAreRejectedAlone() {
		String dataset = "{\"version\": 1, \"records\": [" + VALID_RECORD + ","
				+ "{\"shopId\": \"x\", \"direction\": \"SELLING\", \"observedAt\": 1700000000000},"
				+ "{\"shopId\": \"y\", \"direction\": \"NOT_A_DIRECTION\", \"token\": \"minecraft:t|{}\","
				+ "\"price\": \"1\", \"priceScope\": \"PER_ITEM\", \"sx\": 1, \"sy\": 2, \"sz\": 3,"
				+ "\"serverId\": \"s\", \"world\": \"w\", \"observedAt\": 1700000000000},"
				+ "{\"shopId\": \"z\", \"direction\": \"SELLING\", \"token\": \"minecraft:t|{}\","
				+ "\"price\": \"1\", \"priceScope\": \"PER_ITEM\", \"sx\": 1, \"sy\": 2, \"sz\": 3,"
				+ "\"serverId\": \"s\", \"world\": \"w\", \"observedAt\": 0}"
				+ "]}";

		List<SharedShops.SharedRecord> records = SharedShops.parse(dataset, "test");

		// The valid record survives; the shapeless, unnameable and
		// timestamp-less ones do not.
		assertEquals(1, records.size());
		assertEquals("earthmc.net|minecraft:overworld|10,64,-20", records.get(0).shopId());
	}

	@Test
	void aNewerDatasetVersionIsNeverGuessedAt() {
		String dataset = "{\"version\": 99, \"records\": [" + VALID_RECORD + "]}";
		assertTrue(SharedShops.parse(dataset, "test").isEmpty());
	}

	@Test
	void garbageDoesNotBreakTheParser() {
		assertTrue(SharedShops.parse("not json at all", "test").isEmpty());
		assertTrue(SharedShops.parse("", "test").isEmpty());
		assertTrue(SharedShops.parse(null, "test").isEmpty());
	}

	@Test
	void theCacheRoundTripsAndDegradesSafely(@TempDir Path dir) throws Exception {
		Path cache = dir.resolve("shared-shops.json");
		List<SharedShops.SharedRecord> records = SharedShops.parse(
				"{\"version\": 1, \"records\": [" + VALID_RECORD + "]}", "test");

		SharedShops.saveCache(cache, records);
		List<SharedShops.SharedRecord> loaded = SharedShops.loadCache(cache);
		assertEquals(1, loaded.size());
		assertEquals(records.get(0).shopId(), loaded.get(0).shopId());

		// A corrupt cache is preserved as unusable, never crashes.
		Files.writeString(cache, "{ broken");
		assertEquals(List.of(), SharedShops.loadCache(cache));
	}

	@Test
	void resolvesTheShopDatasetFromTheManifest() {
		String manifest = """
				{
				  "format": "zea-repo/manifest", "version": 1,
				  "datasets": {
				    "badges": {"files": {"datasets/badges.json": "aaa"}},
				    "shops": {"files": {"datasets/shops.json": "bbb"}}
				  }
				}""";
		RepoSync.Entry entry = RepoSync.resolveShopDataset(manifest);
		assertEquals("datasets/shops.json", entry.path());
		assertEquals("bbb", entry.sha256());
	}

	@Test
	void aManifestWithoutShopDatasetsYieldsNothing() {
		String manifest = """
				{"datasets": {"badges": {"files": {"datasets/badges.json": "aaa"}}}}""";
		assertNull(RepoSync.resolveShopDataset(manifest));
		assertNull(RepoSync.resolveShopDataset("garbage"));
		assertNull(RepoSync.resolveShopDataset(null));
	}
}
