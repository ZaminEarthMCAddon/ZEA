package net.zaminmc.zea.market.claims;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the claim state machine end to end: publish and the
 * instance fast path, the pending → optimistic → API override bridge
 * with all its TTLs, override satisfaction, the Earth-only guard, and
 * the delta filter that retires optimistic chunks the map has adopted.
 */
class ClaimStoreTest {

	private final AtomicLong clock = new AtomicLong(100_000);
	private final ClaimStore store = new ClaimStore(clock::get);

	private static TownClaim town(String name, int x, int z, int w, int h) {
		return TownClaim.of(MapWorld.EARTH, name, null, 0xFF0000, 0x00FF00,
						List.<int[][]>of(rect(x, z, w, h)));
	}

	private static int[][] rect(int x, int z, int w, int h) {
		return new int[][]{{x, z}, {x + w, z}, {x + w, z + h}, {x, z + h}};
	}

	@Test
	void publishReplacesTheWorldsClaims() {
		store.publish(MapWorld.EARTH, List.of(town("Amsterdam", 0, 0, 256, 256)));
		assertEquals(1, store.claims(MapWorld.EARTH).size());
		store.publish(MapWorld.EARTH, List.of(
						town("Amsterdam", 0, 0, 256, 256),
						town("Berlin", 1024, 0, 128, 128)));
		assertEquals(2, store.claims(MapWorld.EARTH).size());
		// The other world is untouched by Earth publishes.
		assertTrue(store.claims(MapWorld.MOON).isEmpty());
	}

	@Test
	void unchangedTownsKeepTheirInstances() {
		store.publish(MapWorld.EARTH, List.of(town("Amsterdam", 0, 0, 256, 256)));
		TownClaim before = store.claims(MapWorld.EARTH).get(0);
		store.publish(MapWorld.EARTH, List.of(
						town("Rotterdam", 4096, 0, 64, 64),
						town("Amsterdam", 0, 0, 256, 256)));
		List<TownClaim> after = store.claims(MapWorld.EARTH);
		assertSame(before, after.get(1), "the instance render caches hold survives");
		// A changed outline must NOT reuse the instance.
		store.publish(MapWorld.EARTH, List.of(town("Amsterdam", 0, 0, 512, 256)));
		TownClaim changed = store.claims(MapWorld.EARTH).get(0);
		assertFalse(changed == before);
		assertEquals(512 * 256 / 256, changed.approximateChunkCount());
	}

	@Test
	void confirmedClaimsDrawOptimisticallyThenExpire() {
		store.publish(MapWorld.EARTH, List.of(town("Amsterdam", 0, 0, 256, 256)));
		store.claimSent(MapWorld.EARTH, 20, 20);
		assertTrue(store.optimisticClaims(MapWorld.EARTH).isEmpty(), "nothing draws before the answer");
		assertNotNull(store.confirmPending(MapWorld.EARTH, 20, 20, false));
		List<TownClaim> optimistic = store.optimisticClaims(MapWorld.EARTH);
		assertEquals(1, optimistic.size());
		TownClaim chunk = optimistic.get(0);
		assertEquals(20 * 16, chunk.minX());
		assertEquals(20 * 16 + 16, chunk.maxX());
		assertTrue(chunk.coversBlock(20 * 16 + 8, 20 * 16 + 8));
		assertFalse(chunk.coversBlock(19 * 16, 19 * 16));
		clock.addAndGet(ClaimStore.OPTIMISTIC_TTL_MS - 1);
		assertEquals(1, store.optimisticClaims(MapWorld.EARTH).size());
		store.tick();
		assertEquals(1, store.optimisticClaims(MapWorld.EARTH).size(), "one ms of patience left");
		clock.addAndGet(1);
		store.tick();
		assertTrue(store.optimisticClaims(MapWorld.EARTH).isEmpty(), "the bridge expires");
	}

	@Test
	void pendingsThatNeverAnswerExpire() {
		store.claimSent(MapWorld.EARTH, 1, 2);
		clock.addAndGet(ClaimStore.PENDING_TTL_MS - 1);
		store.tick();
		assertNotNull(store.confirmPending(MapWorld.EARTH, 1, 2, false),
						"still believable inside the TTL");
		assertNull(store.confirmPending(MapWorld.EARTH, 1, 2, false), "consumed by the first confirm");
	}

	@Test
	void rejectedPendingsNeverDraw() {
		store.claimSent(MapWorld.EARTH, 5, 5);
		store.rejectPending(MapWorld.EARTH, 5, 5);
		assertNull(store.confirmPending(MapWorld.EARTH, 5, 5, false));
		assertTrue(store.optimisticClaims(MapWorld.EARTH).isEmpty());
	}

	@Test
	void unclaimsAreCaughtAtSendAndDropOptimisticClaims() {
		store.claimSent(MapWorld.EARTH, 7, 7);
		store.confirmPending(MapWorld.EARTH, 7, 7, false);
		assertEquals(1, store.optimisticClaims(MapWorld.EARTH).size());
		store.unclaimSent(MapWorld.EARTH, 7, 7);
		assertNotNull(store.confirmPending(MapWorld.EARTH, 7, 7, true));
		assertTrue(store.optimisticClaims(MapWorld.EARTH).isEmpty(),
						"the optimistic claim disappears at once");
	}

	@Test
	void apiChunksReshapeThePublishedTown() {
		TownClaim published = town("Amsterdam", 0, 0, 256, 256);
		store.publish(MapWorld.EARTH, List.of(published));
		// Claim chunks (0,0)..(2,2) — 3x3 chunks, offset from the published outline.
		Set<Long> chunks = Set.of(
						ChunkRings.key(0, 0), ChunkRings.key(1, 0), ChunkRings.key(2, 0),
						ChunkRings.key(0, 1), ChunkRings.key(1, 1), ChunkRings.key(2, 1),
						ChunkRings.key(0, 2), ChunkRings.key(1, 2), ChunkRings.key(2, 2));
		TownClaim override = store.applyApiChunks(MapWorld.EARTH, "amsterdam", chunks);
		assertNotNull(override);
		List<TownClaim> effective = store.claims(MapWorld.EARTH);
		assertEquals(1, effective.size());
		assertTrue(effective.get(0).coversBlock(2 * 16 + 8, 2 * 16 + 8), "the new geometry is live");
		// Colors and identity come from the published town.
		assertEquals(published.key(), effective.get(0).key());
		assertEquals(published.fillRgb(), effective.get(0).fillRgb());
		assertEquals(9, effective.get(0).approximateChunkCount());
		// The world-keyed lookup answers with the override in force.
		assertSame(effective.get(0), store.town(MapWorld.EARTH, "amsterdam"));
	}

	@Test
	void apiChunksRefuseTheMoonAndUnknownTowns() {
		store.publish(MapWorld.MOON, List.of(town("Amsterdam", 0, 0, 16, 16)));
		assertNull(store.applyApiChunks(MapWorld.MOON, "Amsterdam",
						Set.of(ChunkRings.key(0, 0))),
						"townBlocks is the Earth claim only");
		store.publish(MapWorld.EARTH, List.of(town("Amsterdam", 0, 0, 256, 256)));
		assertNull(store.applyApiChunks(MapWorld.EARTH, "Nowhere",
						Set.of(ChunkRings.key(0, 0))),
						"a town the map has never shown has nothing to reshape");
	}

	@Test
	void overridesAreDroppedWhenTheMapCatchesUp() {
		TownClaim published = town("Amsterdam", 0, 0, 256, 256);
		store.publish(MapWorld.EARTH, List.of(published));
		Set<Long> chunks = Set.of(
						ChunkRings.key(0, 0), ChunkRings.key(1, 0),
						ChunkRings.key(0, 1), ChunkRings.key(1, 1));
		assertNotNull(store.applyApiChunks(MapWorld.EARTH, "Amsterdam", chunks));
		assertEquals(4, store.claims(MapWorld.EARTH).get(0).approximateChunkCount());
		// The map republishes the same 2x2 chunk square: same bbox,
		// same approximate size — the satisfaction test.
		store.publish(MapWorld.EARTH, List.of(town("Amsterdam", 0, 0, 32, 32)));
		assertEquals(4, store.claims(MapWorld.EARTH).get(0).approximateChunkCount());
		// Satisfied and dropped: publishing the stale old shape now
		// shows through — the effective view follows published data.
		store.publish(MapWorld.EARTH, List.of(town("Amsterdam", 0, 0, 256, 256)));
		assertEquals(256 * 256 / 256, store.claims(MapWorld.EARTH).get(0).approximateChunkCount());
	}

	@Test
	void overridesExpireAfterThreeMinutes() {
		store.publish(MapWorld.EARTH, List.of(town("Amsterdam", 0, 0, 256, 256)));
		assertNotNull(store.applyApiChunks(MapWorld.EARTH, "Amsterdam",
						Set.of(ChunkRings.key(0, 0))));
		clock.addAndGet(ClaimStore.OVERRIDE_TTL_MS - 1);
		store.tick();
		assertEquals(1, store.claims(MapWorld.EARTH).get(0).approximateChunkCount(),
						"override still in force");
		clock.addAndGet(1);
		store.tick();
		assertEquals(256 * 256 / 256, store.claims(MapWorld.EARTH).get(0).approximateChunkCount(),
						"expired: the published outline returns");
	}

	@Test
	void optimisticChunksRetireWhenTheMapAdoptsThem() {
		store.publish(MapWorld.EARTH, List.of(town("Amsterdam", 0, 0, 256, 256)));
		store.claimSent(MapWorld.EARTH, 40, 40);
		store.confirmPending(MapWorld.EARTH, 40, 40, false);
		assertEquals(1, store.optimisticClaims(MapWorld.EARTH).size());
		// The published map grows to cover the chunk center (40*16+8).
		store.publish(MapWorld.EARTH, List.of(town("Amsterdam", 0, 0, 704, 704)));
		assertTrue(store.optimisticClaims(MapWorld.EARTH).isEmpty(),
						"the delta the map published is no longer ours to draw");
	}

	@Test
	void holesInClaimsExcludeTheirBlocks() {
		// A 64x64 outer ring with a 16x16 hole in the middle.
		int[][] outer = rect(0, 0, 64, 64);
		int[][] hole = rect(32, 32, 16, 16);
		TownClaim holed = TownClaim.of(MapWorld.EARTH, "Middelburg", null, 0, 0,
						List.<int[][]>of(outer, hole));
		assertTrue(holed.coversBlock(4, 4));
		assertTrue(holed.coversBlock(60, 60));
		assertFalse(holed.coversBlock(40, 40), "an enclosed pocket is wilderness");
		assertFalse(holed.coversBlock(100, 100));
	}
}
