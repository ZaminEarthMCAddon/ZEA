package net.zaminmc.zea.map.source.earthmcapi;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import net.zaminmc.zea.map.geometry.ChunkRings;
import net.zaminmc.zea.map.source.MapHttp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the v4 client against a real local HTTP server: request
 * shape, townBlocks parsing, unknown towns, the retry ladder (both
 * recovery and exhaustion) and the concurrency gate.
 */
class EarthMcApiClientTest {

	private HttpServer server;
	private ExecutorService executor;
	private String base;

	private final AtomicInteger posts = new AtomicInteger();
	private final AtomicInteger inFlight = new AtomicInteger();
	private final AtomicInteger maxInFlight = new AtomicInteger();
	private final ConcurrentHashMap<String, String> lastBodies = new ConcurrentHashMap<>();
	private volatile int refuseFirstPosts;
	private volatile CountDownLatch holdBarrier;
	private volatile CountDownLatch holdRelease;

	@BeforeEach
	void start() throws IOException {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.setExecutor(Executors.newCachedThreadPool());
		base = "http://127.0.0.1:" + server.getAddress().getPort();

		server.createContext("/towns", exchange -> {
			int now = inFlight.incrementAndGet();
			maxInFlight.accumulateAndGet(now, Math::max);
			inFlight.decrementAndGet();
			String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
			lastBodies.put("last", body);
			int seen = posts.incrementAndGet();
			CountDownLatch barrier = holdBarrier;
			CountDownLatch release = holdRelease;
			if (barrier != null && release != null) {
				barrier.countDown();
				try {
					release.await(3, TimeUnit.SECONDS);
				} catch (InterruptedException interrupted) {
					Thread.currentThread().interrupt();
				}
			}
			if (seen <= refuseFirstPosts) {
				respond(exchange, 429, "slow down".getBytes(StandardCharsets.UTF_8));
				return;
			}
			if (body.contains("Nowhere")) {
				respond(exchange, 200, "[]".getBytes(StandardCharsets.UTF_8));
				return;
			}
			if (body.contains("Gone")) {
				respond(exchange, 404, "no such town".getBytes(StandardCharsets.UTF_8));
				return;
			}
			if (body.contains("NoCoordinates")) {
				respond(exchange, 200, "[{\"name\":\"NoCoordinates\"}]".getBytes(StandardCharsets.UTF_8));
				return;
			}
			respond(exchange, 200, ("[{\"name\":\"Amsterdam\",\"coordinates\":{\"townBlocks\":"
							+ "[[1,2],[3,4],[1,2],[\"x\",\"y\"]]}}]").getBytes(StandardCharsets.UTF_8));
		});
		server.start();
		executor = Executors.newVirtualThreadPerTaskExecutor();
	}

	@AfterEach
	void stop() throws InterruptedException {
		server.stop(0);
		executor.shutdownNow();
		assertTrue(executor.awaitTermination(2, TimeUnit.SECONDS));
	}

	private EarthMcApiClient client(long retryBaseMillis) {
		return new EarthMcApiClient(new MapHttp(executor), executor, base + "/towns", retryBaseMillis);
	}

	@Test
	void fetchesTownBlocks() throws Exception {
		Optional<EarthMcApiClient.TownChunks> result = client(1).fetchTownClaims("Amsterdam").join();
		assertTrue(result.isPresent());
		assertEquals("Amsterdam", result.get().name());
		assertEquals(java.util.Set.of(ChunkRings.key(1, 2), ChunkRings.key(3, 4)),
						result.get().chunks(), "malformed pairs are skipped, dupes collapse");
		String body = lastBodies.get("last");
		assertTrue(body.contains("\"query\":[\"Amsterdam\"]"), body);
		assertTrue(body.contains("\"template\":{\"name\":true,\"coordinates\":true}"), body);
	}

	@Test
	void unknownTownsAreEmptyNotFailures() throws Exception {
		Optional<EarthMcApiClient.TownChunks> result = client(1).fetchTownClaims("Nowhere").join();
		assertFalse(result.isPresent());
	}

	@Test
	void http404IsAlsoAnUnknownTown() throws Exception {
		assertFalse(client(1).fetchTownClaims("Gone").join().isPresent());
	}

	@Test
	void townsWithoutCoordinatesHaveNoChunks() throws Exception {
		Optional<EarthMcApiClient.TownChunks> result = client(1).fetchTownClaims("NoCoordinates").join();
		assertTrue(result.isPresent());
		assertTrue(result.get().chunks().isEmpty());
	}

	@Test
	void theLadderRecoversFromRefusals() throws Exception {
		refuseFirstPosts = 2;
		Optional<EarthMcApiClient.TownChunks> result = client(1).fetchTownClaims("Amsterdam").join();
		assertTrue(result.isPresent());
		assertEquals(3, posts.get(), "two refusals then success");
	}

	@Test
	void exhaustedRetriesFailTheFuture() throws Exception {
		refuseFirstPosts = 100;
		CompletableFuture<Optional<EarthMcApiClient.TownChunks>> future =
						client(1).fetchTownClaims("Amsterdam");
		assertThrows(java.util.concurrent.CompletionException.class, future::join);
		assertEquals(EarthMcApiClient.ATTEMPTS, posts.get(), "five attempts, all refused");
	}

	@Test
	void theGateHoldsTheLineAtFour() throws Exception {
		holdBarrier = new CountDownLatch(EarthMcApiClient.CONCURRENCY);
		holdRelease = new CountDownLatch(1);
		try {
			EarthMcApiClient gated = client(1);
			java.util.List<CompletableFuture<Optional<EarthMcApiClient.TownChunks>>> futures =
							new java.util.ArrayList<>();
			for (int i = 0; i < 12; i++) {
				futures.add(gated.fetchTownClaims("Amsterdam"));
			}
			assertTrue(holdBarrier.await(3, TimeUnit.SECONDS),
							"four requests must reach the server together");
			// The gate is fully occupied: the other eight are parked
			// client-side and have not reached the server at all.
			Thread.sleep(150);
			assertEquals(EarthMcApiClient.CONCURRENCY, posts.get(),
							"no more than the gate's worth of requests is in flight");
			holdRelease.countDown();
			CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
			assertEquals(12, posts.get(), "everything drains after the release");
		} finally {
			holdBarrier = null;
			holdRelease = null;
		}
	}

	private static void respond(HttpExchange exchange, int status, byte[] body) throws IOException {
		exchange.getResponseHeaders().set("Content-Type", "application/json");
		exchange.sendResponseHeaders(status, body.length);
		try (OutputStream out = exchange.getResponseBody()) {
			out.write(body);
		}
		exchange.close();
	}
}
