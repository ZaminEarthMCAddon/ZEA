package net.zaminmc.zea.map.cache;

import net.zaminmc.zea.map.model.MapWorld;
import net.zaminmc.zea.map.model.TileId;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.LongSupplier;

/**
 * The bounded store of map tile imagery: lifecycle, LRU eviction,
 * world pinning and validator bookkeeping for every {@link TileId}.
 *
 * <p>Generic over the decoded image type so the whole machinery is
 * unit-testable without a client: production instantiates it with
 * {@code NativeImage}, tests with {@code byte[]}. GPU texture handles
 * stay render-side, keyed by tile id; this cache decides <i>when</i>
 * images live and die, and hands every retired image to the render
 * thread through {@link #drainDisposals()} — image memory is never
 * freed from a fetch thread, because a texture scheduled on the GPU
 * queue may only be released where it was uploaded
 * ({@code aa92179f}: the freed-mid-upload race rendered permanent
 * black tiles).
 *
 * <p>Discipline encoded:
 *
 * <ul>
 * <li><b>Access-order LRU, capacity 1024.</b> Only entries holding a
 *     decoded image count; {@code FAILED} and {@code LOADING} slots
 *     are bookkeeping, not capacity.
 * <li><b>Pinned overview.</b> Tiles at zoom ≤ {@link #PINNED_MAX_ZOOM}
 *     are never evicted — the coarse under-layer must not re-fetch
 *     just because the world map scrolled around.
 * <li><b>World reserve.</b> The newest {@link #WORLD_RESERVE} images
 *     of the reserved world (the minimap's world) survive eviction
 *     pressure from the other world flooding the cache while its map
 *     is open.
 * <li><b>Eviction evicts the validator.</b> An evicted tile re-fetches
 *     unconditionally; keeping its ETag would make the next request a
 *     guaranteed miss against a body we no longer hold
 *     ({@code d2b6a024}).
 * <li><b>Generation stamps.</b> {@link #bumpGeneration()} on world
 *     switches and cache clears; completions stamped with a stale
 *     generation are rejected and their images disposed by the caller.
 * <li><b>Claimed entries are immune.</b> While the render thread owns
 *     an {@link Upload}, eviction and world clears skip the entry; the
 *     uploader must always finish with {@link #markUploaded} or
 *     {@link #markUploadFailed}.
 * </ul>
 *
 * <p>All methods synchronize on the cache itself: mutation comes from
 * the render thread (loads, claims, touches) and fetch threads
 * (completions, failures) alike, and the critical sections are a few
 * pointer moves.
 */
public final class TileCache<T> {

	/** Lifecycle of one tile slot. */
	public enum State {
		/** No entry at all. */
		ABSENT,
		/** A fetch is in flight; nothing to draw. */
		LOADING,
		/** Decoded image waiting for the render thread to upload. */
		QUEUED,
		/** Uploaded and drawable. */
		READY,
		/** Last attempt failed; retry not before {@code retryAtMs}. */
		FAILED
	}

	/** What {@link #beginLoad} hands the fetcher: validators plus the generation to answer with. */
	public record LoadTicket(String etag, String lastModified, long generation) {
	}

	/** One image the render thread owns for upload. */
	public record Upload<T>(TileId id, T image, boolean replacement) {
	}

	/** A retired image, to be freed on the render thread. */
	public record Disposal<T>(TileId id, T image) {
	}

	/** Receives retired images on the render thread. */
	public interface Disposer<T> {
		void dispose(TileId id, T image);
	}

	/** LRU capacity of drawable tiles. */
	public static final int DEFAULT_CAPACITY = 1024;
	/** Overview zoom levels that are never evicted. */
	public static final int PINNED_MAX_ZOOM = 2;
	/** How many of the reserved world's newest images survive eviction pressure. */
	public static final int WORLD_RESERVE = 128;
	/** A confirmed-absent tile (404) is not worth asking about again for a minute. */
	public static final long RETRY_ABSENT_MS = 60_000L;
	/** A drawable tile is revalidated with its ETag after this age ({@code d2b6a024}). */
	public static final long STALE_AFTER_MS = 20 * 60_000L;
	/** First retry delay of a retryable refusal (429/5xx/transport). */
	static final long RETRYABLE_BASE_MS = 400L;
	/** Retry delay ceiling for retryable refusals. */
	static final long RETRYABLE_MAX_MS = 3_200L;
	/** After this many retryable failures the tile falls back to the absent cadence. */
	static final int RETRYABLE_STEPS = 5;

	private static final class Entry<T> {
		final TileId id;
		State state = State.LOADING;
		T image;
		T pending;
		boolean claimed;
		boolean revalidating;
		String etag;
		String lastModified;
		long readyAtMs;
		long validatedAtMs;
		long retryAtMs;
		int retryableFailures;
		int refusalStatus;
		Entry<T> newer;
		Entry<T> older;

		Entry(TileId id) {
			this.id = id;
		}
	}

	private final int capacity;
	private final LongSupplier clock;

	private final Map<TileId, Entry<T>> entries = new HashMap<>();
	/** Newest last; {@code null} anchors. */
	private Entry<T> newest;
	private Entry<T> oldest;
	private int imageCount;
	private final Deque<Entry<T>> uploadQueue = new ArrayDeque<>();
	private final List<Disposal<T>> disposals = new ArrayList<>();
	private MapWorld reservedWorld;
	private long generation;

	public TileCache() {
		this(DEFAULT_CAPACITY, System::currentTimeMillis);
	}

	public TileCache(int capacity, LongSupplier clock) {
		if (capacity <= 0) {
			throw new IllegalArgumentException("capacity must be positive: " + capacity);
		}
		this.capacity = capacity;
		this.clock = Objects.requireNonNull(clock, "clock");
	}

	public int capacity() {
		return capacity;
	}

	/** The current generation; completions must answer with the one they were given. */
	public synchronized long generation() {
		return generation;
	}

	/** Invalidates everything in flight; callers dispose what they hold. */
	public synchronized long bumpGeneration() {
		return ++generation;
	}

	/**
	 * Marks the world whose imagery must survive the other world's
	 * cache pressure: the minimap's world. May be null for none.
	 */
	public synchronized void setReservedWorld(MapWorld world) {
		this.reservedWorld = world;
	}

	/**
	 * Starts a load of an absent, failed-and-elapsed or cleared
	 * tile. Returns the validators to send (an earlier successful
	 * response's, if any) together with the generation the
	 * completion must carry, or null when a load may not start —
	 * the slot is already loading, queued, ready, or cooling down
	 * after a failure. Callers run on the render thread.
	 */
	public synchronized LoadTicket beginLoad(TileId id) {
		Entry<T> entry = entries.get(id);
		if (entry == null) {
			entry = new Entry<>(id);
			entries.put(id, entry);
			return new LoadTicket(null, null, generation);
		}
		if (entry.state == State.FAILED && clock.getAsLong() >= entry.retryAtMs) {
			entry.state = State.LOADING;
			return new LoadTicket(entry.etag, entry.lastModified, generation);
		}
		return null;
	}

	/**
	 * Starts an ETag revalidation of a stale, drawable tile. The
	 * entry stays READY — the old image keeps drawing — and only a
	 * 200 answer replaces it. Returns null when the tile is not
	 * stale, already revalidating, or gone.
	 */
	public synchronized LoadTicket beginRevalidate(TileId id) {
		Entry<T> entry = entries.get(id);
		if (entry == null || entry.state != State.READY || entry.revalidating
				|| clock.getAsLong() - entry.validatedAtMs < STALE_AFTER_MS) {
			return null;
		}
		entry.revalidating = true;
		return new LoadTicket(entry.etag, entry.lastModified, generation);
	}

	/**
	 * Lands a decoded image. For an entry without an image this
	 * queues the initial upload; for a drawable entry (revalidation
	 * hit) it queues a replacement. Returns false — with the image
	 * left entirely to the caller — when the generation is stale or
	 * the entry is gone, so a late answer can never poison the
	 * cache for a world that moved on.
	 */
	public synchronized boolean complete(TileId id, T image, String etag, String lastModified,
			long requestGeneration) {
		Objects.requireNonNull(image, "image");
		if (requestGeneration != generation) {
			// A stale answer can never be satisfied by this cache any
			// more: its LOADING slot would block every future load, and
			// a stale revalidation flag would never clear. Retire both,
			// and take the image with us — the fetcher cannot know which
			// thread is safe to free it on.
			Entry<T> stale = entries.get(id);
			if (stale != null) {
				if (stale.state == State.LOADING) {
					entries.remove(id);
				} else {
					stale.revalidating = false;
				}
			}
			disposals.add(new Disposal<>(id, image));
			return false;
		}
		Entry<T> entry = entries.get(id);
		if (entry == null) {
			disposals.add(new Disposal<>(id, image));
			return false;
		}
		if (entry.state == State.LOADING) {
			entry.state = State.QUEUED;
			entry.image = image;
			entry.etag = etag == null ? entry.etag : etag;
			entry.lastModified = lastModified == null ? entry.lastModified : lastModified;
			entry.refusalStatus = 0;
			entry.retryableFailures = 0;
			lruInsert(entry);
			uploadQueue.addLast(entry);
			evictIfNeeded();
			return true;
		}
		if (entry.state == State.READY && entry.revalidating) {
			entry.revalidating = false;
			entry.pending = image;
			entry.etag = etag == null ? entry.etag : etag;
			entry.lastModified = lastModified == null ? entry.lastModified : lastModified;
			uploadQueue.addLast(entry);
			return true;
		}
		return false;
	}

	/**
	 * Records a failed attempt: 404 (absent region) and everything
	 * else follow different retry cadences. Also retires an
	 * in-flight revalidation, leaving the old image drawable but
	 * delaying the next revalidation attempt on the same ladder,
	 * so a persistently failing server cannot be polled hot.
	 */
	public synchronized void fail(TileId id, int status) {
		Entry<T> entry = entries.get(id);
		if (entry == null) {
			return;
		}
		if (entry.revalidating) {
			entry.revalidating = false;
			if (entry.state == State.READY) {
				entry.retryableFailures++;
				long delay = nextRetryDelay(entry);
				entry.validatedAtMs = clock.getAsLong() - STALE_AFTER_MS + delay;
				return;
			}
		}
		if (entry.state == State.LOADING) {
			entry.state = State.FAILED;
			entry.refusalStatus = status;
			if (status == 404) {
				entry.retryableFailures = 0;
				entry.retryAtMs = clock.getAsLong() + RETRY_ABSENT_MS;
			} else {
				entry.retryableFailures++;
				entry.retryAtMs = clock.getAsLong() + nextRetryDelay(entry);
			}
		}
	}

	/**
	 * The next image waiting for upload, claimed for the render
	 * thread. A claimed entry is immune to eviction and world
	 * clears until the uploader finishes with
	 * {@link #markUploaded} or {@link #markUploadFailed}.
	 */
	public synchronized Upload<T> claimForUpload() {
		Entry<T> entry = uploadQueue.pollFirst();
		if (entry == null) {
			return null;
		}
		entry.claimed = true;
		if (entry.pending != null) {
			return new Upload<>(entry.id, entry.pending, true);
		}
		return new Upload<>(entry.id, entry.image, false);
	}

	/** Finishes a successful upload: the image becomes drawable. */
	public synchronized void markUploaded(Upload<T> upload) {
		Objects.requireNonNull(upload, "upload");
		Entry<T> entry = entries.get(upload.id());
		if (entry == null || !entry.claimed) {
			disposals.add(new Disposal<>(upload.id(), upload.image()));
			return;
		}
		entry.claimed = false;
		long now = clock.getAsLong();
		if (upload.replacement()) {
			disposals.add(new Disposal<>(entry.id, entry.image));
			entry.image = upload.image();
			entry.pending = null;
			entry.validatedAtMs = now;
			lruTouch(entry);
			return;
		}
		entry.state = State.READY;
		entry.readyAtMs = now;
		entry.validatedAtMs = now;
		lruTouch(entry);
	}

	/** Finishes a failed upload: the image is dead and the slot cools down. */
	public synchronized void markUploadFailed(Upload<T> upload) {
		Objects.requireNonNull(upload, "upload");
		disposals.add(new Disposal<>(upload.id(), upload.image()));
		Entry<T> entry = entries.get(upload.id());
		if (entry == null || !entry.claimed) {
			return;
		}
		entry.claimed = false;
		entry.pending = null;
		if (entry.state == State.QUEUED) {
			entry.state = State.FAILED;
			entry.retryAtMs = clock.getAsLong() + RETRY_ABSENT_MS;
			lruRemove(entry);
		}
	}

	/**
	 * The drawable entry for a tile, refreshing its LRU position,
	 * or null when there is nothing to draw yet.
	 */
	public synchronized T ready(TileId id) {
		Entry<T> entry = entries.get(id);
		if (entry == null || entry.state != State.READY) {
			return null;
		}
		lruTouch(entry);
		return entry.image;
	}

	/** A 304 answer: the drawable image is still the current one; its age clock resets. */
	public synchronized void revalidated(TileId id) {
		Entry<T> entry = entries.get(id);
		if (entry != null) {
			entry.revalidating = false;
			entry.validatedAtMs = clock.getAsLong();
		}
	}

	/**
	 * Drops every entry of one world and invalidates in-flight
	 * answers for it. Retired images go to
	 * {@link #drainDisposals()}; claimed entries finish their
	 * upload first and are then rejected by the generation.
	 */
	public synchronized void clearWorld(MapWorld world) {
		Objects.requireNonNull(world, "world");
		generation++;
		entries.keySet().removeIf(id -> id.world() == world);
		// The key sweep above trimmed the map only; the upload
		// queue and the LRU keep their nodes and need the same cut.
		uploadQueue.removeIf(entry -> entry.id.world() == world);
		for (Entry<T> entry = oldest; entry != null; ) {
			Entry<T> next = entry.newer;
			if (entry.id.world() == world) {
				if (entry.claimed) {
					// The uploader still owns this image and
					// disposes it through its own finish path;
					// here it only leaves the LRU.
					lruUnlink(entry);
					entry.newer = entry.older = null;
					if (entry.image != null) {
						imageCount--;
					}
				} else {
					if (entry.image != null) {
						disposals.add(new Disposal<>(entry.id, entry.image));
					}
					lruRemove(entry);
				}
			}
			entry = next;
		}
	}

	/** Drops everything; retired images go to {@link #drainDisposals()}. */
	public synchronized void clearAll() {
		generation++;
		for (Entry<T> entry = oldest; entry != null; entry = entry.newer) {
			if (entry.image != null && !entry.claimed) {
				disposals.add(new Disposal<>(entry.id, entry.image));
			}
		}
		entries.clear();
		uploadQueue.clear();
		newest = oldest = null;
		imageCount = 0;
	}

	/** Takes the retired images for freeing on the render thread. */
	public synchronized List<Disposal<T>> drainDisposals() {
		if (disposals.isEmpty()) {
			return List.of();
		}
		List<Disposal<T>> out = new ArrayList<>(disposals);
		disposals.clear();
		return out;
	}

	/** The slot's state; {@link State#ABSENT} when unknown. */
	public synchronized State stateOf(TileId id) {
		Entry<T> entry = entries.get(id);
		return entry == null ? State.ABSENT : entry.state;
	}

	/** The last refusal status recorded for the slot; 0 when none. */
	public synchronized int refusalStatusOf(TileId id) {
		Entry<T> entry = entries.get(id);
		return entry == null ? 0 : entry.refusalStatus;
	}

	/** Number of drawable or queued images — the population the capacity bounds. */
	public synchronized int imageCount() {
		return imageCount;
	}

	/** Number of images waiting for upload. */
	public synchronized int queuedForUpload() {
		return uploadQueue.size();
	}

	private void lruInsert(Entry<T> entry) {
		if (newest == null) {
			newest = oldest = entry;
		} else {
			entry.older = newest;
			newest.newer = entry;
			newest = entry;
		}
		imageCount++;
	}

	private void lruTouch(Entry<T> entry) {
		if (entry.image == null || entry == newest) {
			return;
		}
		lruUnlink(entry);
		entry.older = newest;
		entry.newer = null;
		if (newest != null) {
			newest.newer = entry;
		}
		newest = entry;
		if (oldest == null) {
			oldest = entry;
		}
	}

	private void lruRemove(Entry<T> entry) {
		lruUnlink(entry);
		entry.newer = entry.older = null;
		if (entry.image != null) {
			entry.image = null;
			imageCount--;
		}
	}

	private void lruUnlink(Entry<T> entry) {
		if (entry.older != null) {
			entry.older.newer = entry.newer;
		} else if (oldest == entry) {
			oldest = entry.newer;
		}
		if (entry.newer != null) {
			entry.newer.older = entry.older;
		} else if (newest == entry) {
			newest = entry.older;
		}
	}

	private void evictIfNeeded() {
		if (imageCount <= capacity) {
			return;
		}
		// The reserved world's newest images are protected.
		List<Entry<T>> reserved = new ArrayList<>();
		if (reservedWorld != null) {
			int left = WORLD_RESERVE;
			for (Entry<T> entry = newest; entry != null && left > 0; entry = entry.older) {
				if (entry.id.world() == reservedWorld) {
					reserved.add(entry);
					left--;
				}
			}
		}
		for (Entry<T> entry = oldest; entry != null && imageCount > capacity; ) {
			Entry<T> next = entry.newer;
			if (isEvictable(entry, reserved)) {
				disposals.add(new Disposal<>(entry.id, entry.image));
				entries.remove(entry.id);
				// A queued victim leaves the upload queue too,
				// or the render thread would claim an image
				// that is already retired — a double free.
				uploadQueue.remove(entry);
				lruRemove(entry);
			}
			entry = next;
		}
	}

	private boolean isEvictable(Entry<T> entry, List<Entry<T>> reserved) {
		if (entry.claimed || entry.pending != null) {
			return false;
		}
		if (entry.id.zoom() <= PINNED_MAX_ZOOM) {
			return false;
		}
		return !reserved.contains(entry);
	}

	/** The retry ladder: 0.4 s doubling to 3.2 s, then the absent cadence. */
	private long nextRetryDelay(Entry<T> entry) {
		return entry.retryableFailures >= RETRYABLE_STEPS
				? RETRY_ABSENT_MS
				: Math.min(RETRYABLE_BASE_MS << (entry.retryableFailures - 1), RETRYABLE_MAX_MS);
	}
}
