package net.zaminmc.zea.map.render.tile;

import net.zaminmc.zea.map.cache.TileCache;
import net.zaminmc.zea.map.model.TileId;
import net.zaminmc.zea.map.projection.MapProjection;

import java.util.Objects;
import java.util.function.LongSupplier;

/**
 * Runs one frame of the tile pipeline in its one correct order:
 * retired images are freed, queued images are uploaded within the
 * budgets, the frame is planned against the post-upload cache state,
 * and the plan's requests go out last — so a tile that finished
 * uploading this frame is drawn this frame, and the requests reflect
 * what the cache still lacks rather than what it lacked a frame ago.
 *
 * <p>The order is the whole class: every policy lives in the pieces it
 * composes ({@link TileUploads} the budgets, {@link TileRenderer} the
 * plan, the cache the lifecycle), and the screen that calls this gets
 * back the plan to hand to {@link TileFrameDrawer}. Disposals run
 * first because freeing before allocating reads in the frame's memory
 * ledger the way it executes; with handles keyed by image identity the
 * order is not a correctness load either way.
 *
 * <p>Stale-tile ETag revalidation is deliberately absent: network is
 * scheduled, never frame-driven — the refresh scheduler owns it.
 *
 * <p>Render thread only.
 */
public final class TileFrameDriver<T> {

	/** Receives the frame's tile requests; the fetcher's {@code request} in production. */
	@FunctionalInterface
	public interface Requester {
		void request(TileId id);
	}

	private final TileCache<T> cache;
	private final TileRenderer renderer;
	private final TileUploads uploads;
	private final TileUploads.Sink<T> sink;
	private final TileCache.Disposer<T> disposer;
	private final Requester requester;

	public TileFrameDriver(TileCache<T> cache, TileRenderer renderer, TileUploads uploads,
			TileUploads.Sink<T> sink, TileCache.Disposer<T> disposer, Requester requester) {
		this.cache = Objects.requireNonNull(cache, "cache");
		this.renderer = Objects.requireNonNull(renderer, "renderer");
		this.uploads = Objects.requireNonNull(uploads, "uploads");
		this.sink = Objects.requireNonNull(sink, "sink");
		this.disposer = Objects.requireNonNull(disposer, "disposer");
		this.requester = Objects.requireNonNull(requester, "requester");
	}

	/**
	 * Runs one frame and returns the plan to draw. Call once per frame
	 * on the render thread.
	 *
	 * @param motion    the camera's motion since the previous frame —
	 *                  selects the tighter upload and request budgets
	 * @param nanosClock the render thread's monotonic clock
	 */
	public TileRenderer.FramePlan frame(MapProjection projection, TileRenderer.Motion motion,
			LongSupplier nanosClock) {
		Objects.requireNonNull(projection, "projection");
		Objects.requireNonNull(motion, "motion");
		Objects.requireNonNull(nanosClock, "nanosClock");
		TileUploads.disposeRetired(cache, disposer);
		uploads.run(cache, sink, motion.moving(), nanosClock);
		TileRenderer.FramePlan plan = renderer.planFrame(projection, motion);
		for (TileId request : plan.requests()) {
			requester.request(request);
		}
		return plan;
	}
}
