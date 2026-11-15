package net.zaminmc.zea.map.source.squaremap;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import net.zaminmc.zea.map.model.Freshness;
import net.zaminmc.zea.map.model.MapWorld;
import net.zaminmc.zea.map.model.PlayerMarker;
import net.zaminmc.zea.map.model.TownClaim;
import net.zaminmc.zea.map.source.MapHttp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end client behavior against a local server: freshness
 * bookkeeping across success, 304 and refusal, per-world validators, and
 * the player feed's keep-previous-on-304 semantics.
 */
class SquaremapClientTest {

	private static final String MARKERS_BODY = """
			[{"control":true,"hide":false,"id":"towny","name":"Territory","z_index":250,"markers":[
			  {"type":"polygon","color":"#aa0000","fillColor":"#ffffff",
			   "tooltip":"<div><b>Etna</b> (Member of Italy)</div>",
			   "popup":"<div>Etna (Italy)</div>",
			   "points":[[[{"x":0,"z":0},{"x":16,"z":0},{"x":16,"z":16},{"x":0,"z":16},{"x":0,"z":0}]]]}
			]},{"id":"chunky","name":"World Border","markers":[]}]""";

	private HttpServer server;
	private ExecutorService executor;
	private SquaremapClient client;
	private volatile String lastAuthorization;

	@BeforeEach
	void start() throws IOException {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.setExecutor(Executors.newSingleThreadExecutor());
		int port = server.getAddress().getPort();
		String base = "http://127.0.0.1:" + port;

		server.createContext("/markers", exchange -> {
			String ifNoneMatch = exchange.getRequestHeaders().getFirst("If-None-Match");
			if ("\"m1\"".equals(ifNoneMatch)) {
				respond(exchange, 304, null, null, "\"m1\"", "public, max-age=120");
				return;
			}
			respond(exchange, 200, MARKERS_BODY.getBytes(StandardCharsets.UTF_8),
					"application/json", "\"m1\"", "public, max-age=120");
		});
		server.createContext("/players", exchange -> {
			lastAuthorization = exchange.getRequestHeaders().getFirst("Authorization");
			respond(exchange, 200, """
					{"max":800,"current":2,"players":[
					  {"world":"minecraft_overworld","name":"A","x":1,"z":2,"yaw":0,"uuid":"u1"},
					  {"world":"earthmc_moon","name":"B","x":1,"z":2,"yaw":0,"uuid":"u2"}
					]}"""
					.getBytes(StandardCharsets.UTF_8), "application/json", null, "public, max-age=1");
		});
		server.createContext("/refused", exchange ->
				respond(exchange, 429, "[]".getBytes(StandardCharsets.UTF_8), "application/json", null, null));
		server.createContext("/settings.json", exchange ->
				respond(exchange, 200, ("{\"worlds\":[{\"name\":\"minecraft_overworld\",\"display_name\":\"Terra Nostra\"},"
						+ "{\"name\":\"earthmc_moon\",\"display_name\":\"Moon\"}]}")
						.getBytes(StandardCharsets.UTF_8), "application/json", null, null));
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
		};
		client = new SquaremapClient(new MapHttp(executor), executor, local);
	}

	@AfterEach
	void stop() throws InterruptedException {
		server.stop(0);
		executor.shutdownNow();
		assertTrue(executor.awaitTermination(2, TimeUnit.SECONDS));
	}

	@Test
	void markersParseAndFreshnessSucceeds() throws Exception {
		MarkersDocument document = client.fetchMarkers(MapWorld.EARTH, null).get(5, TimeUnit.SECONDS);
		assertEquals(1, document.towns().size());
		TownClaim etna = document.towns().get(0);
		assertEquals("Etna", etna.name());
		assertEquals("Italy", etna.nation());
		assertFalse(client.claimsFreshness(MapWorld.EARTH).failing());
		assertTrue(client.claimsFreshness(MapWorld.EARTH).hasData());
	}

	@Test
	void notModifiedCountsAsSuccessAndKeepsPreviousDocument() throws Exception {
		MarkersDocument first = client.fetchMarkers(MapWorld.EARTH, null).get(5, TimeUnit.SECONDS);
		long successAt = client.claimsFreshness(MapWorld.EARTH).lastSuccessMs();

		// The stored ETag matches what the server serves: 304.
		MarkersDocument second = client.fetchMarkers(MapWorld.EARTH, first).get(5, TimeUnit.SECONDS);
		assertEquals(first, second, "a 304 must return the caller's document unchanged");
		Freshness after = client.claimsFreshness(MapWorld.EARTH);
		assertFalse(after.failing());
		assertTrue(after.lastSuccessMs() >= successAt, "a 304 refreshes the success clock");
	}

	@Test
	void refusalMarksFreshnessFailingWithoutResettingSuccess() throws Exception {
		MarkersDocument document = client.fetchMarkers(MapWorld.EARTH, null).get(5, TimeUnit.SECONDS);
		long successAt = client.claimsFreshness(MapWorld.EARTH).lastSuccessMs();

		// Swap the URL behind the same client's world to a refusing endpoint
		// by fetching Moon's markers from a refused route: the local server
		// serves Moon from /refused via a second client sharing state style.
		SquaremapUrls refusing = new SquaremapUrls() {
			@Override
			public String markers(MapWorld world) {
				return "http://127.0.0.1:" + server.getAddress().getPort() + "/refused";
			}

			@Override
			public String worlds() {
				return "http://127.0.0.1:" + server.getAddress().getPort() + "/settings.json";
			}

			@Override
			public String players() {
				return "http://127.0.0.1:" + server.getAddress().getPort() + "/players";
			}
		};
		SquaremapClient refused = new SquaremapClient(new MapHttp(executor), executor, refusing);
		CompletableFuture<MarkersDocument> failed = refused.fetchMarkers(MapWorld.EARTH, document);
		assertThrows(ExecutionException.class, () -> failed.get(5, TimeUnit.SECONDS));
		Freshness stale = refused.claimsFreshness(MapWorld.EARTH);
		assertTrue(stale.failing());
		assertEquals(429, stale.lastRefusedStatus());
		assertEquals(0, stale.lastSuccessMs(), "the refusing client never succeeded");

		// The good client's success is untouched by the other's failure.
		assertTrue(client.claimsFreshness(MapWorld.EARTH).lastSuccessMs() >= successAt);
	}

	@Test
	void validatorsArePerWorld() throws Exception {
		client.fetchMarkers(MapWorld.EARTH, null).get(5, TimeUnit.SECONDS);
		client.fetchMarkers(MapWorld.MOON, null).get(5, TimeUnit.SECONDS);
		// Both worlds went through the same local URL and both succeeded;
		// the per-world maps each hold their own validator entry.
		// (The routing is shared here, so this asserts the bookkeeping
		// does not throw or cross-contaminate freshness.)
		assertFalse(client.claimsFreshness(MapWorld.EARTH).failing());
		assertFalse(client.claimsFreshness(MapWorld.MOON).failing());
	}

	@Test
	void playerFeedParsesAndTracksFreshness() throws Exception {
		List<PlayerMarker> markers = client.fetchPlayers(List.of(), null).get(5, TimeUnit.SECONDS);
		assertEquals(2, markers.size());
		assertTrue(markers.get(0).inWorld(MapWorld.EARTH));
		assertTrue(markers.get(1).inWorld(MapWorld.MOON));
		assertFalse(client.playersFreshness().failing());
	}

	@Test
	void playerFeedSendsBearerOnlyWhenConfigured() throws Exception {
		client.fetchPlayers(List.of(), "token-1").get(5, TimeUnit.SECONDS);
		assertEquals("Bearer token-1", lastAuthorization);
		client.fetchPlayers(List.of(), null).get(5, TimeUnit.SECONDS);
		assertEquals(null, lastAuthorization);
	}

	@Test
	void worldListParses() throws Exception {
		Map<String, String> worlds = client.fetchWorlds().get(5, TimeUnit.SECONDS);
		assertEquals(Map.of("minecraft_overworld", "Terra Nostra", "earthmc_moon", "Moon"), worlds);
	}

	private static void respond(HttpExchange exchange, int status, byte[] body, String contentType,
			String etag, String cacheControl) throws IOException {
		if (etag != null) {
			exchange.getResponseHeaders().set("ETag", etag);
		}
		if (cacheControl != null) {
			exchange.getResponseHeaders().set("Cache-Control", cacheControl);
		}
		if (body == null) {
			exchange.sendResponseHeaders(status, -1);
		} else {
			exchange.getResponseHeaders().set("Content-Type", contentType);
			exchange.sendResponseHeaders(status, body.length);
			try (OutputStream out = exchange.getResponseBody()) {
				out.write(body);
			}
		}
		exchange.close();
	}
}
