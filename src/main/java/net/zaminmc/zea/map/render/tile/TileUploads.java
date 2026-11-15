package net.zaminmc.zea.map.render.tile;

import net.zaminmc.zea.map.cache.TileCache;
import net.zaminmc.zea.map.model.TileId;

import java.util.List;
import java.util.Objects;
import java.util.function.LongSupplier;

/**
 * Drains the cache's upload queue onto the GPU, within the frame's
 * budgets — the only code that ever turns a claimed
 * {@link TileCache.Upload} into a drawable texture.
 *
 * <p>Budgets exist because texture uploads are the one tile-pipeline
 * step that can spike: a 512×512 RGBA image is a full megabyte of
 * pixel push, and a burst of them after a fast pan is exactly the
 * stutter the reference implementation spent its upload throttling on.
 * The contract is "at least one, then time-boxed":
 *
 * <ul>
 * <li><b>Always one.</b> The first queued upload proceeds even at a
 *     zero time budget, so a trickle of arrivals still makes progress
 *     on the cheapest frames.
 * <li><b>Count- and time-boxed after that.</b> {@link #UPLOADS_IDLE}
 *     uploads within {@link #BUDGET_IDLE_NANOS} on an idle view;
 *     {@link #UPLOADS_MOVING} within {@link #BUDGET_MOVING_NANOS}
 *     while panning or zooming.
 * <li><b>Failure is not death.</b> A sink that rejects an image (GL
 *     error, rejected allocation) sends the slot to the failed ladder
 *     through {@link TileCache#markUploadFailed} — the tile retries on
 *     the absent cadence rather than wedging the queue.
 * </ul>
 *
 * <p>Runs on the render thread only; the cache's claim/mark contract
 * makes the claimed entry immune to eviction and world clears until
 * the loop finishes with it.
 */
public final class TileUploads<T> {

	/** Performs the GPU upload for one claimed image; false = rejected. */
	@FunctionalInterface
	public interface Sink<T> {
		boolean upload(TileId id, T image);
	}

	/** Uploads per frame on an idle view. */
	public static final int UPLOADS_IDLE = 5;
	/** Uploads per frame while the view moves. */
	public static final int UPLOADS_MOVING = 1;
	/** Time budget per idle frame: 1.5 ms. */
	public static final long BUDGET_IDLE_NANOS = 1_500_000L;
	/** Time budget while moving: 750 µs. */
	public static final long BUDGET_MOVING_NANOS = 750_000L;

	/**
	 * Claims and uploads queued images until both budgets say stop.
	 * Returns how many uploads landed.
	 *
	 * @param moving     panning/zooming this frame selects the tighter
	 *                   budgets
	 * @param nanosClock the render thread's monotonic clock; injected
	 *                   so tests run the loop without real time
	 */
	public int run(TileCache<T> cache, Sink<T> sink, boolean moving, LongSupplier nanosClock) {
		Objects.requireNonNull(cache, "cache");
		Objects.requireNonNull(sink, "sink");
		Objects.requireNonNull(nanosClock, "nanosClock");
		int maxUploads = moving ? UPLOADS_MOVING : UPLOADS_IDLE;
		long budget = moving ? BUDGET_MOVING_NANOS : BUDGET_IDLE_NANOS;
		long start = nanosClock.getAsLong();
		int uploaded = 0;
		while (uploaded < maxUploads) {
			if (uploaded > 0 && nanosClock.getAsLong() - start >= budget) {
				break;
			}
			TileCache.Upload<T> upload = cache.claimForUpload();
			if (upload == null) {
				break;
			}
			if (sink.upload(upload.id(), upload.image())) {
				cache.markUploaded(upload);
				uploaded++;
			} else {
				cache.markUploadFailed(upload);
			}
		}
		return uploaded;
	}

	/**
	 * Frees everything the cache retired since last frame — the other
	 * half of the render thread's ownership duty. Retired images are
	 * only ever freed here, never on the fetch thread that produced
	 * them ({@code aa92179f}).
	 */
	public static <T> void disposeRetired(TileCache<T> cache, TileCache.Disposer<T> disposer) {
		List<TileCache.Disposal<T>> retired = cache.drainDisposals();
		for (TileCache.Disposal<T> disposal : retired) {
			disposer.dispose(disposal.id(), disposal.image());
		}
	}
}
