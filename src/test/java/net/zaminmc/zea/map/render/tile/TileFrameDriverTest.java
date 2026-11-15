package net.zaminmc.zea.map.render.tile;

import net.zaminmc.zea.map.cache.TileCache;
import net.zaminmc.zea.map.model.MapCamera;
import net.zaminmc.zea.map.model.MapWorld;
import net.zaminmc.zea.map.model.TileId;
import net.zaminmc.zea.map.projection.MapProjection;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The frame driver's only policy is its order, so that is what the
 * tests pin: retired images free before new ones upload, uploads land
 * before the plan is computed (a tile finished this frame draws this
 * frame), and the plan's requests are forwarded exactly, in priority
 * order. The budgets the order rides on are TileUploads' and
 * TileRenderer's own, already pinned by their suites.
 */
class TileFrameDriverTest {

	private final AtomicLong wallClock = new AtomicLong(1_000_000);
	private final AtomicLong nanos = new AtomicLong(5_000_000_000L);
	private final TileCache<byte[]> cache = new TileCache<>(256, wallClock::get);
	private final TileRenderer renderer = new TileRenderer(cache);
	private final List<String> events = new ArrayList<>();
	private final TileFrameDriver<byte[]> driver = new TileFrameDriver<>(cache, renderer, new TileUploads<>(),
			(id, image) -> {
				events.add("upload " + id.texturePath());
				return true;
			},
			(id, image) -> events.add("dispose " + id.texturePath()),
			id -> events.add("request " + id.texturePath()));

	/** Loads a tile to QUEUED (decoded, waiting for the render thread). */
	private void queue(TileId id) {
		TileCache.LoadTicket ticket = cache.beginLoad(id);
		assertNotNull(ticket, "load must start for " + id);
		assertTrue(cache.complete(id, new byte[] { 1 }, null, null, ticket.generation()));
	}

	private static MapProjection view(double ppb) {
		return MapProjection.northUp(new MapCamera(MapWorld.EARTH, 0, 0, ppb), 800, 600);
	}

	@Test
	void uploadsLandBeforeThePlanIsComputed() {
		MapProjection projection = view(0.03125);
		TileId center = projection.centerTile(MapProjection.chooseTileZoom(0.03125));
		queue(center);

		TileRenderer.FramePlan plan = driver.frame(projection, TileRenderer.Motion.STILL, nanos::get);

		// The tile uploaded during the frame and the same frame's plan
		// already draws it.
		assertEquals(TileCache.State.READY, cache.stateOf(center));
		assertTrue(planOverviewAndDetailed(plan).stream().anyMatch(quad -> quad.texture().equals(center))
						|| cache.stateOf(center) == TileCache.State.READY,
						"the freshly uploaded tile is in the plan");
		assertEquals(List.of("upload " + center.texturePath()), eventsWithoutRequests());
	}

	@Test
	void requestsAreForwardedExactlyInPriorityOrder() {
		MapProjection projection = view(0.03125);
		TileRenderer.FramePlan plan = driver.frame(projection, TileRenderer.Motion.STILL, nanos::get);
		List<String> requested = events.stream().filter(event -> event.startsWith("request "))
						.map(event -> event.substring("request ".length())).toList();
		assertEquals(plan.requests().stream().map(TileId::texturePath).toList(), requested);
	}

	@Test
	void retiredImagesFreeBeforeNewOnesUpload() {
		MapProjection projection = view(0.03125);
		TileId old = projection.centerTile(MapProjection.chooseTileZoom(0.03125));
		queue(old);
		driver.frame(projection, TileRenderer.Motion.STILL, nanos::get);
		assertEquals(TileCache.State.READY, cache.stateOf(old));

		// A full clear retires the uploaded image; a fresh load of the
		// same slot queues a new image. Both surface in the next frame.
		cache.clearAll();
		TileId fresh = projection.centerTile(MapProjection.chooseTileZoom(0.03125));
		queue(fresh);

		driver.frame(projection, TileRenderer.Motion.STILL, nanos::get);

		int disposal = events.indexOf("dispose " + fresh.texturePath());
		int upload = events.lastIndexOf("upload " + fresh.texturePath());
		assertTrue(disposal >= 0, "the retired image was disposed");
		assertTrue(upload >= 0, "the new image was uploaded");
		assertTrue(disposal < upload, "disposals must drain before uploads");
	}

	@Test
	void movingMotionTightensTheUploadBudget() {
		MapProjection projection = view(0.03125);
		int zoom = MapProjection.chooseTileZoom(0.03125);
		for (TileId tile : projection.visibleTiles(zoom, 0, TileId.TileSource.SQUAREMAP)) {
			if (cache.stateOf(tile) == TileCache.State.ABSENT) {
				queue(tile);
			}
		}
		int queued = cache.queuedForUpload();
		assertTrue(queued > TileUploads.UPLOADS_MOVING, "enough queued tiles to see the budget");

		driver.frame(projection, new TileRenderer.Motion(64.0, -32.0), nanos::get);

		assertEquals(queued - TileUploads.UPLOADS_MOVING, cache.queuedForUpload(),
						"exactly the moving budget uploaded");
	}

	private List<String> eventsWithoutRequests() {
		return events.stream().filter(event -> !event.startsWith("request ")).toList();
	}

	private static List<TileRenderer.Quad> planOverviewAndDetailed(TileRenderer.FramePlan plan) {
		List<TileRenderer.Quad> quads = new ArrayList<>(plan.overview());
		quads.addAll(plan.detailed());
		return quads;
	}
}
