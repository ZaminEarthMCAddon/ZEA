package net.zaminmc.zea.browser;

import net.zaminmc.zea.VanillaBootstrap;
import net.zaminmc.zea.market.MarketData;
import net.zaminmc.zea.market.store.MarketStore;
import net.zaminmc.zea.market.index.ItemVariantIndex;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reads the dev client's seeded market file and prints what the index
 * builds from it — a live check that the in-game pane's data pipeline
 * sees the seed exactly as written.
 */
class SeedMarketCheckTest {
	@BeforeAll
	static void boot() {
		VanillaBootstrap.ensure();
	}

	@Test
	void readsTheDevSeed() throws Exception {
		Path seed = Path.of("/home/z/my-project/zea/run/config/zea/market.json");
		if (!java.nio.file.Files.exists(seed)) {
			System.out.println("SEED missing");
			return;
		}
		MarketStore store = new MarketStore(seed);
		MarketData data = new MarketData();
		var file = store.load();
		System.out.println("SEED version=" + file.version()
				+ " shops=" + file.shops().size()
				+ " observations=" + file.observations().size());
		for (var wire : file.observations().subList(0, 3)) {
			System.out.println("SEED obs: shop=" + wire.shopId() + " token=" + wire.token()
					+ " dir=" + wire.direction() + " price=" + wire.price());
		}
		for (var wire : file.shops()) {
			MarketStore.toShop(wire).ifPresent(data::putShop);
		}
		int rejected = 0;
		for (var wire : file.observations()) {
			if (MarketStore.toObservation(wire).isEmpty()) {
				rejected++;
				if (rejected <= 5) {
					System.out.println("SEED rejected: shop=" + wire.shopId() + " token=" + wire.token()
							+ " dir=" + wire.direction() + " fidelity=" + wire.fidelity()
							+ " stockUnit=" + wire.stockUnit() + " source=" + wire.sourceType());
				}
			} else {
				MarketStore.toObservation(wire).ifPresent(data::putObservation);
			}
		}
		System.out.println("SEED shops-in=" + data.shopCount() + " rejected=" + rejected);
		var variants = ItemVariantIndex.variants(data);
		System.out.println("SEED variants=" + variants.size());
		assertTrue(variants.size() > 0, "the seed builds a non-empty index");
	}
}
