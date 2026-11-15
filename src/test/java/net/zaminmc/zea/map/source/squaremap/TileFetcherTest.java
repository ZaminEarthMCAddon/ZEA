package net.zaminmc.zea.map.source.squaremap;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import net.zaminmc.zea.map.cache.TileCache;
import net.zaminmc.zea.map.model.MapWorld;
import net.zaminmc.zea.map.model.TileId;
import net.zaminmc.zea.map.source.MapHttp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the tile fetch pipeline against a real local HTTP server:
 * happy path into the cache, 304 revalidation keeping the drawable
 * image, the 404 and 429 cadences, decoder failure, and the per-world
 * gate that keeps one world's burst from stacking.
 */
class TileFetcherTest {

	private final AtomicLong clock = new AtomicLong(5_000_000);
	private final TileCache<String> cache = new TileCache<>(16, clock::get);

	private HttpServer server;
	private ExecutorService executor;
	private TileFetcher<String> fetcher;

	private final AtomicInteger tileHits = new AtomicInteger();
	private final AtomicInteger absentHits = new AtomicInteger();
	private volatile String lastIfNoneMatch;
	private volatile long stallMillis;

	@BeforeEach
	void start() throws IOException {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.setExecutor(Executors.newCachedThreadPool());
		String base = "http://127.0.0.1:" + server.getAddress().getPort();

		server.createContext("/tiles/", exchange -> {
			lastIfNoneMatch = exchange.getRequestHeaders().getFirst("If-None-Match");
			tileHits.incrementAndGet();
			if (stallMillis > 0) {
				try {
					Thread.sleep(stallMillis);
				} catch (InterruptedException interrupted) {
					Thread.currentThread().interrupt();
					return;
				}
			}
			if ("\"t1\"".equals(lastIfNoneMatch)) {
				respond(exchange, 304, null, "image/png", "\"t1\"", "public, max-age=7200");
				return;
			}
			respond(exchange, 200, "png-bytes".getBytes(StandardCharsets.UTF_8),
					"image/png", "\"t1\"", "public, max-age=7200, stale-while-revalidate=600");
		});
		server.createContext("/absent", exchange -> {
			absentHits.incrementAndGet();
				respond(exchange, 404, "no tile".getBytes(StandardCharsets.UTF_8),
						"text/plain", null, null);
		});
		server.createContext("/busy", exchange ->
				respond(exchange, 429, "slow down".getBytes(StandardCharsets.UTF_8),
						"text/plain", null, null));
		server.start();

		executor = Executors.newVirtualThreadPerTaskExecutor();
		SquaremapUrls local = new SquaremapUrls() {
			@Override
			public String markers(MapWorld world) {
				return base + "/markers";
			}

			@Override
			public String worlds() {
				return base + "/settings.json";
			}

			@Override
			public String players() {
				return base + "/players";
			}

			@Override
			public String tile(MapWorld world, int zoom, int x, int z) {
				String leaf = "/" + zoom + "/" + x + "_" + z + ".png";
				if (world == MapWorld.MOON) {
					return base + "/absent" + leaf;
				}
				return base + "/tiles" + leaf;
			}
		};
		fetcher = new TileFetcher<>(new MapHttp(executor), executor, cache,
				bytes -> "decoded-" + bytes.length + "b", local);
	}

	@AfterEach
	void stop() throws InterruptedException {
		server.stop(0);
		executor.shutdownNow();
		assertTrue(executor.awaitTermination(2, TimeUnit.SECONDS));
	}

	private static TileId earth(int x, int z) {
		return new TileId(MapWorld.EARTH, TileId.TileSource.SQUAREMAP, 5, x, z);
	}

	private static TileId moon(int x, int z) {
		return new TileId(MapWorld.MOON, TileId.TileSource.SQUAREMAP, 5, x, z);
	}

	private void drainUploads() {
		TileCache.Upload<String> upload;
		while ((upload = cache.claimForUpload()) != null) {
			cache.markUploaded(upload);
		}
	}

	@Test
	void servesATileIntoTheCacheAndUploadsIt() throws Exception {
		TileId id = earth(1, 2);
		fetcher.request(id).join();
		assertEquals(TileCache.State.QUEUED, cache.stateOf(id));
		TileCache.Upload<String> upload = cache.claimForUpload();
		assertNotNull(upload);
		assertEquals("decoded-9b", upload.image());
		assertEquals(id, upload.id());
		cache.markUploaded(upload);
		assertEquals(TileCache.State.READY, cache.stateOf(id));
		assertEquals("decoded-9b", cache.ready(id));
	}

	@Test
	void revalidationKeepsTheOldImageOnNotModified() throws Exception {
		TileId id = earth(3, 3);
		fetcher.request(id).join();
		drainUploads();
		assertEquals(TileCache.State.READY, cache.stateOf(id));
		int hitsBefore = tileHits.get();
		clock.addAndGet(TileCache.STALE_AFTER_MS);
		fetcher.revalidate(id).join();
		assertEquals(hitsBefore + 1, tileHits.get());
		assertEquals("\"t1\"", lastIfNoneMatch);
		assertEquals("decoded-9b", cache.ready(id), "the drawable image is untouched");
		assertNull(cache.claimForUpload(), "a 304 queues no replacement upload");
		clock.addAndGet(TileCache.STALE_AFTER_MS - 1);
		assertNull(fetcherBeginRevalidateForTest(id), "age clock was reset");
	}

	private Object fetcherBeginRevalidateForTest(TileId id) {
		// Mirrors the gate-then-ticket order of revalidate(); used
		// here only to observe the cache's answer.
		TileCache.LoadTicket ticket = cache.beginRevalidate(id);
		if (ticket != null) {
			// Put it back: complete with the same generation would be
			// a second fetch; instead just mark it not-revalidating.
			cache.revalidated(id);
		}
		return ticket;
	}

	@Test
	void absentRegionsAreQuietFailures() throws Exception {
		TileId id = moon(0, 0);
		fetcher.request(id).join();
		assertEquals(TileCache.State.FAILED, cache.stateOf(id));
		assertEquals(404, cache.refusalStatusOf(id));
		clock.addAndGet(TileCache.RETRY_ABSENT_MS - 1);
		fetcher.request(id).join();
		assertEquals(1, absentHits.get(), "no second fetch inside the cooldown");
		clock.addAndGet(1);
		fetcher.request(id).join();
		assertEquals(2, absentHits.get(), "the retry really asked the server");
		assertEquals(TileCache.State.FAILED, cache.stateOf(id),
					"absent again, with a fresh cooldown");
	}

	@Test
	void retryableRefusalsBackOff() throws Exception {
		TileId id = new TileId(MapWorld.MOON, TileId.TileSource.SQUAREMAP, 5, 9, 9);
		// The moon URL is /absent/... in this fixture; point a
		// second fetcher at /busy instead.
		TileFetcher<String> busy = busyFetcher();
		busy.request(id).join();
		assertEquals(TileCache.State.FAILED, cache.stateOf(id));
		assertEquals(429, cache.refusalStatusOf(id));
		clock.addAndGet(399);
		assertNull(cache.beginLoad(id));
		clock.addAndGet(1);
		assertNotNull(cache.beginLoad(id));
	}

	private TileFetcher<String> busyFetcher() {
		String base = "http://127.0.0.1:" + server.getAddress().getPort();
		SquaremapUrls busyUrls = new SquaremapUrls() {
			@Override
			public String markers(MapWorld world) {
				return base + "/markers";
			}

			@Override
			public String worlds() {
				return base + "/settings.json";
			}

			@Override
			public String players() {
				return base + "/players";
			}

			@Override
			public String tile(MapWorld world, int zoom, int x, int z) {
				return base + "/busy";
			}
		};
		return new TileFetcher<>(new MapHttp(executor), executor, cache,
				bytes -> "decoded", busyUrls);
	}

	@Test
	void decoderFailuresLandOnTheRetryableLadder() throws Exception {
		String base = "http://127.0.0.1:" + server.getAddress().getPort();
		TileFetcher<String> broken = new TileFetcher<>(new MapHttp(executor), executor, cache,
				bytes -> {
					throw new IOException("not a png");
				}, new SquaremapUrls() {
					@Override
					public String markers(MapWorld world) {
					return base + "/markers";
					}

					@Override
					public String worlds() {
					return base + "/settings.json";
					}

					@Override
					public String players() {
					return base + "/players";
					}
				});
		TileId id = earth(7, 7);
		broken.request(id).join();
		assertEquals(TileCache.State.FAILED, cache.stateOf(id));
		assertEquals(0, cache.refusalStatusOf(id), "no HTTP status involved");
		clock.addAndGet(399);
		assertNull(cache.beginLoad(id));
		clock.addAndGet(1);
		assertNotNull(cache.beginLoad(id));
	}

	@Test
	void theGateCapsOneWorldAtATime() throws Exception {
		String base = "http://127.0.0.1:" + server.getAddress().getPort();
		TileFetcher<String> single = new TileFetcher<>(new MapHttp(executor), executor, cache,
				bytes -> "decoded-" + bytes.length + "b",
				localUrlsAt(base), 1);
		stallMillis = 250;
		try {
			TileId first = earth(10, 10);
			TileId second = earth(11, 11);
			single.request(first);
			single.request(second);
			assertEquals(TileCache.State.LOADING, cache.stateOf(first));
			assertEquals(TileCache.State.ABSENT, cache.stateOf(second),
					"a busy gate costs nothing: no slot was taken");
			// After the first lands, the second may start.
			waitFor(() -> cache.stateOf(first) == TileCache.State.QUEUED);
			drainUploads();
			single.request(second).join();
			assertEquals(TileCache.State.QUEUED, cache.stateOf(second));
		} finally {
			stallMillis = 0;
		}
	}

	@Test
	void lateAnswersAfterAGenerationBumpAreRetired() throws Exception {
		CountDownLatch served = new CountDownLatch(1);
		AtomicReference<CountDownLatch> hold = new AtomicReference<>(new CountDownLatch(1));
		String base = "http://127.0.0.1:" + server.getAddress().getPort();
		HttpServer slow = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		slow.createContext("/tiles/", exchange -> {
			try {
				hold.get().await();
			} catch (InterruptedException interrupted) {
				Thread.currentThread().interrupt();
				return;
			}
			respond(exchange, 200, "late".getBytes(StandardCharsets.UTF_8),
					"image/png", null, null);
			served.countDown();
		});
		slow.start();
		try {
			TileFetcher<String> late = new TileFetcher<>(new MapHttp(executor), executor, cache,
					bytes -> "late-decoded",
					localUrlsAt("http://127.0.0.1:" + slow.getAddress().getPort()));
			TileId id = earth(20, 20);
			late.request(id);
			// The world moves on while the answer is in flight.
			cache.clearWorld(MapWorld.EARTH);
			hold.get().countDown();
			assertTrue(served.await(2, TimeUnit.SECONDS));
			waitFor(() -> !cache.drainDisposals().isEmpty());
			assertEquals(TileCache.State.ABSENT, cache.stateOf(id));
		} finally {
			slow.stop(0);
		}
	}

	private SquaremapUrls localUrlsAt(String base) {
		return new SquaremapUrls() {
			@Override
			public String markers(MapWorld world) {
				return base + "/markers";
			}

			@Override
			public String worlds() {
				return base + "/settings.json";
			}

			@Override
			public String players() {
				return base + "/players";
			}

			@Override
			public String tile(MapWorld world, int zoom, int x, int z) {
				return base + "/tiles/" + zoom + "/" + x + "_" + z + ".png";
			}
		};
	}

	private static void waitFor(BooleanSupplier condition) throws InterruptedException {
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
		while (!condition.getAsBoolean()) {
			if (System.nanoTime() > deadline) {
				throw new AssertionError("condition not reached in time");
			}
			Thread.sleep(10);
		}
	}

	@FunctionalInterface
	private interface BooleanSupplier {
		boolean getAsBoolean();
	}

	private static void respond(HttpExchange exchange, int status, byte[] body, String contentType,
			String etag, String cacheControl) throws IOException {
		if (contentType != null) {
			exchange.getResponseHeaders().set("Content-Type", contentType);
		}
		if (etag != null) {
			exchange.getResponseHeaders().set("ETag", etag);
		}
		if (cacheControl != null) {
			exchange.getResponseHeaders().set("Cache-Control", cacheControl);
		}
		if (body == null) {
			exchange.sendResponseHeaders(status, -1);
		} else {
			exchange.sendResponseHeaders(status, body.length);
			try (OutputStream out = exchange.getResponseBody()) {
				out.write(body);
			}
		}
		exchange.close();
	}
}
