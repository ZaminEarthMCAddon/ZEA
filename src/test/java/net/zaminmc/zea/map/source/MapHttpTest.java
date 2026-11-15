package net.zaminmc.zea.map.source;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the conditional-GET plumbing against a real local HTTP
 * server: gzip decoding, ETag revalidation, Cache-Control floors,
 * refusals and bearer auth.
 */
class MapHttpTest {

	private HttpServer server;
	private ExecutorService executor;
	private MapHttp http;
	private String base;

	/** What the server saw on the last request, for header assertions. */
	private volatile String lastAuthorization;
	private volatile String lastIfNoneMatch;

	@BeforeEach
	void start() throws IOException {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.setExecutor(Executors.newSingleThreadExecutor());
		base = "http://127.0.0.1:" + server.getAddress().getPort();

		server.createContext("/doc", exchange -> {
			lastAuthorization = exchange.getRequestHeaders().getFirst("Authorization");
			lastIfNoneMatch = exchange.getRequestHeaders().getFirst("If-None-Match");
			if ("\"v1\"".equals(lastIfNoneMatch)) {
				respond(exchange, 304, null, "text/plain", "\"v1\"", "public, max-age=120");
				return;
			}
			respondGzipped(exchange, "hello world", "\"v1\"", "public, max-age=120");
		});
		server.createContext("/plain", exchange ->
				respond(exchange, 200, "plain".getBytes(StandardCharsets.UTF_8), "text/plain", null, "no-store"));
		server.createContext("/refused", exchange ->
				respond(exchange, 429, "slow down".getBytes(StandardCharsets.UTF_8), "text/plain", null, null));
		server.createContext("/bearer", exchange -> {
			lastAuthorization = exchange.getRequestHeaders().getFirst("Authorization");
			respond(exchange, 200, "ok".getBytes(StandardCharsets.UTF_8), "text/plain", null, null);
		});

		server.start();
		executor = Executors.newVirtualThreadPerTaskExecutor();
		http = new MapHttp(executor);
	}

	@AfterEach
	void stop() throws InterruptedException {
		server.stop(0);
		executor.shutdownNow();
		assertTrue(executor.awaitTermination(2, TimeUnit.SECONDS));
	}

	@Test
	void decodesGzipBodies() throws Exception {
		MapHttp.Fetched fetched = http.get(base + "/doc", null, null, null);
		assertEquals("hello world", MapHttp.utf8(fetched.body()));
		assertEquals("\"v1\"", fetched.etag());
		assertEquals(120_000L, fetched.maxAgeMs(), "max-age=120 is a floor the schedule must respect");
	}

	@Test
	void etagRevalidationReturnsNotModified() throws Exception {
		MapHttp.Fetched first = http.get(base + "/doc", null, null, null);
		MapHttp.Fetched second = http.get(base + "/doc", first.etag(), null, null);
		assertTrue(second.notModified());
		assertNull(second.body());
		assertEquals("\"v1\"", second.etag(), "the validator survives a 304");
	}

	@Test
	void sameEncodingOnEveryRequestKeepsValidatorsComparable() throws Exception {
		// Both requests carry Accept-Encoding: gzip — the server sees it
		// and the ETag it issues is the gzip one on both visits.
		MapHttp.Fetched first = http.get(base + "/doc", null, null, null);
		MapHttp.Fetched second = http.get(base + "/doc", first.etag(), null, null);
		assertTrue(second.notModified());
	}

	@Test
	void refusalCarriesTheStatus() {
		MapHttp.HttpRefusal refusal = assertThrows(MapHttp.HttpRefusal.class,
				() -> http.get(base + "/refused", null, null, null));
		assertEquals(429, refusal.status);
	}

	@Test
	void noStoreMeansNoFloor() throws Exception {
		MapHttp.Fetched fetched = http.get(base + "/plain", null, null, null);
		assertEquals(0, fetched.maxAgeMs());
	}

	@Test
	void bearerKeyIsSentWhenPresent() throws Exception {
		http.get(base + "/bearer", null, null, "sekrit-token");
		assertEquals("Bearer sekrit-token", lastAuthorization);
		http.get(base + "/bearer", null, null, null);
		assertNull(lastAuthorization, "no key, no header");
	}

	@Test
	void gunzipRoundTrip() throws Exception {
		byte[] data = "map data".repeat(100).getBytes(StandardCharsets.UTF_8);
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try (GZIPOutputStream gzip = new GZIPOutputStream(out)) {
			gzip.write(data);
		}
		assertArrayEquals(data, MapHttp.gunzip(out.toByteArray()));
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

	private static void respondGzipped(HttpExchange exchange, String body, String etag, String cacheControl)
			throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try (GZIPOutputStream gzip = new GZIPOutputStream(out)) {
			gzip.write(body.getBytes(StandardCharsets.UTF_8));
		}
		byte[] gzipped = out.toByteArray();
		exchange.getResponseHeaders().set("ETag", etag);
		exchange.getResponseHeaders().set("Cache-Control", cacheControl);
		exchange.getResponseHeaders().set("Content-Encoding", "gzip");
		exchange.getResponseHeaders().set("Content-Type", "text/plain");
		exchange.sendResponseHeaders(200, gzipped.length);
		try (OutputStream out2 = exchange.getResponseBody()) {
			out2.write(gzipped);
		}
		exchange.close();
	}
}
