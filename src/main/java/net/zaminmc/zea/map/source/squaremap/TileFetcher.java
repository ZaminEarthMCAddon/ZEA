package net.zaminmc.zea.map.source.squaremap;

import net.zaminmc.zea.map.cache.TileCache;
import net.zaminmc.zea.map.model.TileId;
import net.zaminmc.zea.map.source.MapHttp;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Semaphore;

/**
 * Drives tile imagery from the squaremap host into the
 * {@link TileCache}. Pure plumbing with one seam: the PNG decoder is
 * injected, so production hands it {@code NativeImage::read} while
 * tests decode nothing at all.
 *
 * <p>Responsibilities, and nothing more:
 *
 * <ul>
 * <li><b>Per-world concurrency caps.</b> Each world gets its own
 *     semaphore (default {@link #DEFAULT_PER_WORLD_LOADS}); one
 *     world's burst cannot starve the other's. The caller (render
 *     thread) already bounds how many tiles per frame it asks for.
 * <li><b>Validators travel with the ticket.</b> The cache hands back
 *     the tile's own ETag from its last success; a 304 is a success
 *     that keeps the drawable image and resets its age.
 * <li><b>404 is normal.</b> An ungenerated region answers 404; the
 *     cache records it as absent with its calm retry cadence. Real
 *     refusals (403/429/5xx) and transport failures land on the
 *     retryable ladder.
 * <li><b>Decoding happens here, on the fetch thread.</b> The decoded
 *     image is completed into the cache, and the cache decides its
 *     fate — including retiring it through the render-thread disposal
 *     channel when the answer arrives stale.
 * <li><b>No key, ever.</b> The squaremap host is public; the optional
 *     API key only ever travels to the fixed feed URL and must not be
 *     reachable from here.
 * </ul>
 */
public final class TileFetcher<T> {

	/** PNG bytes to a decoded image; may throw, and must not leak on failure. */
	@FunctionalInterface
	public interface Decoder<T> {
		T decode(byte[] pngBytes) throws IOException;
	}

	/** Concurrent tile loads allowed per world. */
	public static final int DEFAULT_PER_WORLD_LOADS = 8;

	private final MapHttp http;
	private final Executor executor;
	private final TileCache<T> cache;
	private final Decoder<T> decoder;
	private final SquaremapUrls urls;
	private final Map<String, Semaphore> worldGates = new ConcurrentHashMap<>();
	private final int perWorldLoads;

	public TileFetcher(MapHttp http, Executor executor, TileCache<T> cache, Decoder<T> decoder) {
		this(http, executor, cache, decoder, SquaremapUrls.live());
	}

	public TileFetcher(MapHttp http, Executor executor, TileCache<T> cache, Decoder<T> decoder,
			SquaremapUrls urls) {
		this(http, executor, cache, decoder, urls, DEFAULT_PER_WORLD_LOADS);
	}

	public TileFetcher(MapHttp http, Executor executor, TileCache<T> cache, Decoder<T> decoder,
			SquaremapUrls urls, int perWorldLoads) {
		this.http = Objects.requireNonNull(http, "http");
		this.executor = Objects.requireNonNull(executor, "executor");
		this.cache = Objects.requireNonNull(cache, "cache");
		this.decoder = Objects.requireNonNull(decoder, "decoder");
		this.urls = Objects.requireNonNull(urls, "urls");
		this.perWorldLoads = perWorldLoads;
	}

	/**
	 * Asks for one tile to become drawable. Returns a future that
	 * completes normally, whatever happened; the cache state is the
	 * observable outcome. Called from the render thread; the gate is
	 * taken first so a busy world costs nothing.
	 */
	public CompletableFuture<Void> request(TileId id) {
		Semaphore gate = gateFor(id);
		if (!gate.tryAcquire()) {
			return CompletableFuture.completedFuture(null);
		}
		TileCache.LoadTicket ticket = cache.beginLoad(id);
		if (ticket == null) {
			gate.release();
			return CompletableFuture.completedFuture(null);
		}
		return fetch(id, ticket, gate);
	}

	/**
	 * Asks for a stale drawable tile to be revalidated with its
	 * ETag. The old image keeps drawing throughout; a 200 answer
	 * queues a replacement upload in the cache.
	 */
	public CompletableFuture<Void> revalidate(TileId id) {
		Semaphore gate = gateFor(id);
		if (!gate.tryAcquire()) {
			return CompletableFuture.completedFuture(null);
		}
		TileCache.LoadTicket ticket = cache.beginRevalidate(id);
		if (ticket == null) {
			gate.release();
			return CompletableFuture.completedFuture(null);
		}
		return fetch(id, ticket, gate);
	}

	private CompletableFuture<Void> fetch(TileId id, TileCache.LoadTicket ticket, Semaphore gate) {
		String url = urls.tile(id.world(), id.zoom(), id.x(), id.z());
		return http.getAsync(url, ticket.etag(), ticket.lastModified(), executor)
				.thenAccept(fetched -> {
					try {
						if (fetched.notModified()) {
							cache.revalidated(id);
							return;
						}
						T image = decoder.decode(fetched.body());
						cache.complete(id, image, fetched.etag(), fetched.lastModified(),
								ticket.generation());
					} catch (IOException decodeFailure) {
						// A body that will not decode is a retryable
						// problem without an HTTP status.
						cache.fail(id, 0);
					}
				})
				.exceptionally(failure -> {
					cache.fail(id, refusalStatus(failure));
					return null;
				})
				// One release per attempt, whatever happened above.
				.whenComplete((result, failure) -> gate.release());
	}

	private Semaphore gateFor(TileId id) {
		return worldGates.computeIfAbsent(id.world().squaremapKey,
				key -> new Semaphore(perWorldLoads));
	}

	/** The refusal status inside whatever the future chain wrapped it in, or 0. */
	private static int refusalStatus(Throwable failure) {
		Throwable current = failure;
		for (int depth = 0; current != null && depth < 6; depth++) {
			if (current instanceof MapHttp.HttpRefusal refusal) {
				return refusal.status;
			}
			current = current.getCause();
		}
		return 0;
	}
}
