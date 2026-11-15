package net.zaminmc.zea.map.cache;

import net.zaminmc.zea.map.model.MapWorld;
import net.zaminmc.zea.map.model.TileId;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
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
 * Exercises the tile cache machinery exactly as the render and fetch
 * threads will drive it: lifecycle, dedup, the failure ladders, LRU
 * eviction with pinning and the world reserve, claim immunity, the
 * generation guard, and the render-thread disposal channel.
 */
class TileCacheTest {

	private final AtomicLong clock = new AtomicLong(1_000_000);
	private final TileCache<byte[]> cache = new TileCache<>(64, clock::get);

	private static TileId tile(int x, int z) {
	return new TileId(MapWorld.EARTH, TileId.TileSource.SQUAREMAP, 5, x, z);
	}

	private static TileId moonTile(int x, int z) {
	return new TileId(MapWorld.MOON, TileId.TileSource.SQUAREMAP, 5, x, z);
	}

	/** Loads a tile all the way to READY, as the two threads would together. */
	private void loadReady(TileId id, byte[] image) {
	TileCache.LoadTicket ticket = cache.beginLoad(id);
	assertNotNull(ticket, "load must start");
	assertTrue(cache.complete(id, image, "\"e-" + x(id) + "\"", null, ticket.generation()));
	uploadQueue();
	assertSame(image, cache.ready(id));
	}

	private void uploadQueue() {
	TileCache.Upload<byte[]> upload;
	while ((upload = cache.claimForUpload()) != null) {
		cache.markUploaded(upload);
	}
	}

	private static int x(TileId id) {
	return id.x() * 1000 + id.z();
	}

	@Test
	void loadLifecycleFromAbsentToReady() {
	TileId id = tile(1, 1);
	assertEquals(TileCache.State.ABSENT, cache.stateOf(id));
	TileCache.LoadTicket ticket = cache.beginLoad(id);
	assertNotNull(ticket);
	assertNull(ticket.etag());
	assertEquals(TileCache.State.LOADING, cache.stateOf(id));
	assertTrue(cache.complete(id, new byte[]{1}, "\"a\"", null, ticket.generation()));
	assertEquals(TileCache.State.QUEUED, cache.stateOf(id));
	assertNull(cache.ready(id));
	TileCache.Upload<byte[]> upload = cache.claimForUpload();
	assertNotNull(upload);
	assertEquals(id, upload.id());
	assertFalse(upload.replacement());
	assertEquals(1, upload.image()[0]);
	cache.markUploaded(upload);
	assertEquals(TileCache.State.READY, cache.stateOf(id));
	assertEquals(1, cache.ready(id)[0]);
	// A second load may not start while the tile is drawable.
	assertNull(cache.beginLoad(id));
	assertNull(cache.beginRevalidate(id));
	}

	@Test
	void beginLoadDeduplicatesConcurrentStarts() {
	TileId id = tile(2, 2);
	assertNotNull(cache.beginLoad(id));
	assertNull(cache.beginLoad(id), "LOADING dedups");
	cache.fail(id, 500);
	assertEquals(TileCache.State.FAILED, cache.stateOf(id));
	assertNull(cache.beginLoad(id), "cooldown has not elapsed");
	}

	@Test
	void absentRegionsRetryAfterAMinute() {
	TileId id = tile(3, 3);
	assertNotNull(cache.beginLoad(id));
	cache.fail(id, 404);
	assertEquals(404, cache.refusalStatusOf(id));
	clock.addAndGet(59_999);
	assertNull(cache.beginLoad(id));
	clock.addAndGet(1);
	assertNotNull(cache.beginLoad(id), "60s later the 404 is worth asking about again");
	}

	@Test
	void retryableRefusalsClimbTheLadder() {
	TileId id = tile(4, 4);
	long[] expectedDelays = {400, 800, 1600, 3_200, 60_000, 60_000};
	for (long delay : expectedDelays) {
		assertNotNull(cache.beginLoad(id), "attempt should be allowed");
		cache.fail(id, 429);
		clock.addAndGet(delay - 1);
		assertNull(cache.beginLoad(id), "cooldown of " + delay + "ms still running");
		clock.addAndGet(1);
	}
	}

	@Test
	void staleGenerationCompletionsAreRejected() {
	TileId id = tile(5, 5);
	TileCache.LoadTicket ticket = cache.beginLoad(id);
	assertNotNull(ticket);
	cache.bumpGeneration();
	byte[] image = new byte[]{9};
	assertFalse(cache.complete(id, image, "\"e\"", null, ticket.generation()),
		"stale generation must be rejected");
	assertEquals(TileCache.State.ABSENT, cache.stateOf(id));
	// The fetcher cannot know which thread is safe to free the
	// image on, so the cache takes it: retired, not leaked.
	assertEquals(1, cache.drainDisposals().size());
	}

	@Test
	void clearedEntriesRejectCompletionsWithoutLeaking() {
	TileId id = tile(6, 6);
	TileCache.LoadTicket ticket = cache.beginLoad(id);
	assertNotNull(ticket);
	cache.clearWorld(MapWorld.EARTH);
	assertFalse(cache.complete(id, new byte[]{1}, "\"e\"", null, ticket.generation()));
	assertEquals(TileCache.State.ABSENT, cache.stateOf(id));
	// The rejected image is retired through the render-thread channel.
	assertEquals(1, cache.drainDisposals().size());
	}

	@Test
	void evictionRemovesOldestAndItsValidator() {
	TileCache<byte[]> small = new TileCache<>(3, clock::get);
	TileId a = tile(10, 0);
	TileId b = tile(11, 0);
	TileId c = tile(12, 0);
	TileId d = tile(13, 0);
	loadInto(small, a, new byte[]{1});
	loadInto(small, b, new byte[]{2});
	loadInto(small, c, new byte[]{3});
	assertEquals(3, small.imageCount());
	// Touch a: it must no longer be the eviction candidate.
	assertNotNull(small.ready(a));
	loadInto(small, d, new byte[]{4});
	assertEquals(3, small.imageCount());
	assertEquals(TileCache.State.ABSENT, small.stateOf(b), "least recently used goes");
	assertEquals(TileCache.State.READY, small.stateOf(a));
	assertEquals(TileCache.State.READY, small.stateOf(c));
	assertEquals(TileCache.State.READY, small.stateOf(d));
	List<TileCache.Disposal<byte[]>> disposals = small.drainDisposals();
	assertEquals(1, disposals.size());
	assertEquals(b, disposals.get(0).id());
	assertEquals(2, disposals.get(0).image()[0]);
	}

	@Test
	void overviewZoomsArePinned() {
	TileCache<byte[]> small = new TileCache<>(2, clock::get);
	TileId overview = new TileId(MapWorld.EARTH, TileId.TileSource.SQUAREMAP, 1, 0, 0);
	loadInto(small, overview, new byte[]{1});
	loadInto(small, tile(20, 0), new byte[]{2});
	loadInto(small, tile(21, 0), new byte[]{3});
	// The overview is pinned, so the pressure falls on the unpinned
	// tiles: capacity holds and the overview survives.
	assertEquals(2, small.imageCount());
	assertEquals(TileCache.State.READY, small.stateOf(overview));
	assertEquals(TileCache.State.ABSENT, small.stateOf(tile(20, 0)), "unpinned oldest evicted");
	assertEquals(TileCache.State.READY, small.stateOf(tile(21, 0)));
	}

	@Test
	void theReservedWorldsNewestImagesSurvive() {
	TileCache<byte[]> big = new TileCache<>(256, clock::get);
	big.setReservedWorld(MapWorld.EARTH);
	for (int i = 0; i < 130; i++) {
		loadInto(big, tile(i, 7), new byte[]{(byte) i});
	}
	for (int i = 0; i < 130; i++) {
		loadInto(big, moonTile(i, 7), new byte[]{(byte) (i + 100)});
	}
	assertEquals(256, big.imageCount());
	// Earth keeps its newest 128 (plus the two oldest were
	// sacrificed before Moon imagery went).
	assertEquals(TileCache.State.ABSENT, big.stateOf(tile(0, 7)));
	assertEquals(TileCache.State.ABSENT, big.stateOf(tile(1, 7)));
	assertEquals(TileCache.State.READY, big.stateOf(tile(2, 7)));
	assertEquals(TileCache.State.READY, big.stateOf(tile(129, 7)));
	// Moon kept its newest 128 as well.
	assertEquals(TileCache.State.ABSENT, big.stateOf(moonTile(0, 7)));
	assertEquals(TileCache.State.ABSENT, big.stateOf(moonTile(1, 7)));
	assertEquals(TileCache.State.READY, big.stateOf(moonTile(2, 7)));
	assertEquals(TileCache.State.READY, big.stateOf(moonTile(129, 7)));
	}

	@Test
	void claimedUploadsAreImmuneUntilFinished() {
	TileCache<byte[]> small = new TileCache<>(2, clock::get);
	TileId a = tile(30, 0);
	TileId b = tile(31, 0);
	TileId c = tile(32, 0);
	TileCache.LoadTicket ticket = small.beginLoad(a);
	assertNotNull(ticket);
	assertTrue(small.complete(a, new byte[]{1}, "\"a\"", null, ticket.generation()));
	TileCache.Upload<byte[]> claimed = small.claimForUpload();
	assertNotNull(claimed);
	// While claimed, neither eviction nor a world clear frees it.
	loadInto(small, b, new byte[]{2});
	loadInto(small, c, new byte[]{3});
	small.clearWorld(MapWorld.EARTH);
	small.markUploaded(claimed);
	assertEquals(TileCache.State.ABSENT, small.stateOf(a), "generation rejects the late upload");
	// Three retirements: the evicted b, the cleared c, and the late
	// upload of a itself — nothing leaked.
	assertEquals(3, small.drainDisposals().size());
	assertEquals(0, small.imageCount(), "nothing survives the clear");
	}

	@Test
	void revalidationReplacesTheImageAndRetiresTheOldOne() {
	TileId id = tile(40, 0);
	byte[] first = new byte[]{1};
	loadReady(id, first);
	clock.addAndGet(TileCache.STALE_AFTER_MS - 1);
	assertNull(cache.beginRevalidate(id), "not stale yet");
	clock.addAndGet(1);
	TileCache.LoadTicket ticket = cache.beginRevalidate(id);
	assertNotNull(ticket);
	assertEquals("\"e-" + x(id) + "\"", ticket.etag());
	assertNull(cache.beginRevalidate(id), "revalidation dedups");
	// The old image stays drawable while the new one travels.
	assertSame(first, cache.ready(id));
	byte[] second = new byte[]{2};
	assertTrue(cache.complete(id, second, "\"e2\"", null, ticket.generation()));
	assertSame(first, cache.ready(id), "replacement not drawable before upload");
	TileCache.Upload<byte[]> upload = cache.claimForUpload();
	assertNotNull(upload);
	assertTrue(upload.replacement());
	assertSame(second, upload.image());
	cache.markUploaded(upload);
	assertSame(second, cache.ready(id));
	List<TileCache.Disposal<byte[]>> disposals = cache.drainDisposals();
	assertEquals(1, disposals.size());
	assertSame(first, disposals.get(0).image());
	}

	@Test
	void aNotModifiedAnswerResetsTheAgeClock() {
	TileId id = tile(41, 0);
	loadReady(id, new byte[]{1});
	clock.addAndGet(TileCache.STALE_AFTER_MS);
	assertNotNull(cache.beginRevalidate(id));
	cache.revalidated(id);
	assertNull(cache.beginRevalidate(id), "fresh again");
	clock.addAndGet(TileCache.STALE_AFTER_MS - 1);
	assertNull(cache.beginRevalidate(id));
	clock.addAndGet(1);
	assertNotNull(cache.beginRevalidate(id));
	}

	@Test
	void aFailedRevalidationKeepsDrawingAndCoolsDown() {
	TileId id = tile(42, 0);
	byte[] image = new byte[]{1};
	loadReady(id, image);
	clock.addAndGet(TileCache.STALE_AFTER_MS);
	assertNotNull(cache.beginRevalidate(id));
	cache.fail(id, 503);
	assertSame(image, cache.ready(id), "the old image keeps drawing");
	assertNull(cache.beginRevalidate(id), "cooldown after the failure");
	clock.addAndGet(399);
	assertNull(cache.beginRevalidate(id));
	clock.addAndGet(1);
	assertNotNull(cache.beginRevalidate(id), "0.4s ladder applies to revalidation too");
	}

	@Test
	void clearWorldDisposesOnlyThatWorld() {
	TileId earth = tile(50, 0);
	TileId moon = moonTile(50, 0);
	loadReady(earth, new byte[]{1});
	loadReady(moon, new byte[]{2});
	long before = cache.generation();
	cache.clearWorld(MapWorld.EARTH);
	assertEquals(before + 1, cache.generation());
	assertEquals(TileCache.State.ABSENT, cache.stateOf(earth));
	assertEquals(TileCache.State.READY, cache.stateOf(moon));
	assertEquals(1, cache.imageCount());
	List<TileCache.Disposal<byte[]>> disposals = cache.drainDisposals();
	assertEquals(1, disposals.size());
	assertEquals(earth, disposals.get(0).id());
	assertTrue(cache.drainDisposals().isEmpty(), "drained once");
	}

	@Test
	void failedUploadsCoolDownAndFreeTheImage() {
	TileId id = tile(60, 0);
	TileCache.LoadTicket ticket = cache.beginLoad(id);
	assertNotNull(ticket);
	assertTrue(cache.complete(id, new byte[]{1}, "\"a\"", null, ticket.generation()));
	TileCache.Upload<byte[]> upload = cache.claimForUpload();
	assertNotNull(upload);
	cache.markUploadFailed(upload);
	assertEquals(TileCache.State.FAILED, cache.stateOf(id));
	assertEquals(0, cache.imageCount());
	List<TileCache.Disposal<byte[]>> disposals = cache.drainDisposals();
	assertEquals(1, disposals.size());
	assertSame(upload.image(), disposals.get(0).image());
	}

	@Test
	void massEvictionDisposesEveryVictimExactlyOnce() {
	Set<TileId> evicted = new HashSet<>();
	for (int i = 0; i < 100; i++) {
		TileId id = tile(i, 9);
		TileCache.LoadTicket ticket = cache.beginLoad(id);
		assertNotNull(ticket);
		assertTrue(cache.complete(id, new byte[]{(byte) i}, "\"e\"", null, ticket.generation()));
	}
	// capacity 64: 36 of the 100 queued images were evicted
	// while the rest wait for upload; none leaked.
	List<TileCache.Disposal<byte[]>> disposals = cache.drainDisposals();
	assertEquals(36, disposals.size());
	for (TileCache.Disposal<byte[]> disposal : disposals) {
		assertTrue(evicted.add(disposal.id()), "evicted once: " + disposal.id());
	}
	assertEquals(64, cache.imageCount());
	uploadQueue();
	assertEquals(64, cache.imageCount());
	assertTrue(cache.drainDisposals().isEmpty());
	}

	private void loadInto(TileCache<byte[]> target, TileId id, byte[] image) {
	TileCache.LoadTicket ticket = target.beginLoad(id);
	assertNotNull(ticket);
	assertTrue(target.complete(id, image, "\"e-" + x(id) + "\"", null, ticket.generation()));
	TileCache.Upload<byte[]> upload;
	while ((upload = target.claimForUpload()) != null) {
		target.markUploaded(upload);
	}
	}
}
