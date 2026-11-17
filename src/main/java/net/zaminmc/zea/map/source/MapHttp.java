package net.zaminmc.zea.map.source;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

/**
 * Shared conditional-GET plumbing for every map data source.
 *
 * <p>Discipline encoded here, each earned the hard way upstream:
 *
 * <ul>
 * <li><b>Always accept gzip.</b> markers.json is ~10.7 MB raw vs ~1.4 MB
 *     gzipped; polling it uncompressed multiplied bandwidth ~7.5× and the
 *     slow transfers surfaced as random connection resets.
 * <li><b>Validators are per encoding.</b> The server issues a different
 *     ETag per content encoding ({@code W/"…-gzip"}). Sending
 *     {@code Accept-Encoding: gzip} on every request to a given URL —
 *     including conditional ones — keeps the validator matching; mixing
 *     encodings makes every conditional request miss silently.
 * <li><b>Cache-Control is a floor.</b> The server publishes how long a
 *     document stays fresh (markers: {@code max-age=120}); polling faster
 *     than that is waste, so {@link #freshForMs} exposes it as a floor
 *     for refresh schedules.
 * <li><b>304 is success.</b> The caller's on-screen data is current;
 *     there was just nothing to send.
 * </ul>
 */
public final class MapHttp {

	/** A completed conditional GET: body or not-modified, plus validators. */
	public record Fetched(byte[] body, String etag, String lastModified, long maxAgeMs) {
		public boolean notModified() {
			return body == null;
		}
	}

	private static final Pattern MAX_AGE = Pattern.compile("max-age\\s*=\\s*(\\d+)", Pattern.CASE_INSENSITIVE);

	private final HttpClient http;
	private final Duration timeout;

	public MapHttp(Executor executor) {
		this(executor, Duration.ofSeconds(15));
	}

	public MapHttp(Executor executor, Duration timeout) {
		this.http = HttpClient.newBuilder()
				.connectTimeout(Duration.ofSeconds(10))
				.executor(executor)
				.build();
		this.timeout = timeout;
	}

	/**
	 * A conditional GET. Sends {@code Accept-Encoding: gzip} and, when
	 * known, {@code If-None-Match} / {@code If-Modified-Since} — the same
	 * headers on every request for a URL, so validators stay comparable.
	 *
	 * @param bearerKey optional {@code Authorization: Bearer} value; sent
	 *                  only to the URL the caller chose, which must be the
	 *                  fixed API-host feed and nothing else
	 * @return the body (gunzipped when needed), or a body-less
	 *         {@link Fetched} on 304
	 * @throws IOException          on transport failure or non-2xx status
	 *                              other than 304 (message carries the code)
	 * @throws InterruptedException if the caller's executor is shut down
	 */
	public Fetched get(String url, String priorEtag, String priorLastModified, String bearerKey)
			throws IOException, InterruptedException {
		HttpRequest.Builder builder = HttpRequest.newBuilder()
				.uri(URI.create(url))
				.timeout(timeout)
				.header("Accept-Encoding", "gzip")
				.header("User-Agent", "ZEA (github.com/ZaminEarthMCAddon/ZEA)");
		if (bearerKey != null && !bearerKey.isBlank()) {
			builder.header("Authorization", "Bearer " + bearerKey);
		}
		if (priorEtag != null && !priorEtag.isBlank()) {
			builder.header("If-None-Match", priorEtag);
		}
		if (priorLastModified != null && !priorLastModified.isBlank()) {
			builder.header("If-Modified-Since", priorLastModified);
		}
		HttpResponse<byte[]> response = http.send(builder.GET().build(), HttpResponse.BodyHandlers.ofByteArray());
		int status = response.statusCode();
		if (status == 304) {
			return new Fetched(null, priorEtag, priorLastModified, maxAgeMs(response));
		}
		if (status != 200) {
			throw new HttpRefusal(status, url);
		}
		byte[] body = response.body();
		if (isGzipped(response)) {
			body = gunzip(body);
		}
		return new Fetched(body,
				header(response, "ETag"),
				header(response, "Last-Modified"),
				maxAgeMs(response));
	}

	/**
	 * A JSON POST. Never conditional (POSTs are not revalidated), but
	 * gzip acceptance and refusal reporting follow the same rules as
	 * {@link #get}. Used by the v4 API client.
	 */
	public Fetched post(String url, String jsonBody) throws IOException, InterruptedException {
		HttpRequest.Builder builder = HttpRequest.newBuilder()
						.uri(URI.create(url))
						.timeout(timeout)
						.header("Content-Type", "application/json")
						.header("Accept-Encoding", "gzip")
						.header("User-Agent", "ZEA (github.com/ZaminEarthMCAddon/ZEA)");
		HttpResponse<byte[]> response = http.send(
						builder.POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8)).build(),
						HttpResponse.BodyHandlers.ofByteArray());
		int status = response.statusCode();
		if (status != 200) {
			throw new HttpRefusal(status, url);
		}
		byte[] body = response.body();
		if (isGzipped(response)) {
			body = gunzip(body);
		}
		return new Fetched(body, header(response, "ETag"), header(response, "Last-Modified"),
						maxAgeMs(response));
	}

	/** Async form of {@link #post}. */
	public CompletableFuture<Fetched> postAsync(String url, String jsonBody, Executor executor) {
		return CompletableFuture.supplyAsync(() -> {
			try {
				return post(url, jsonBody);
			} catch (IOException failure) {
				throw new RuntimeException(failure);
			} catch (InterruptedException interrupted) {
				Thread.currentThread().interrupt();
				throw new RuntimeException(interrupted);
			}
		}, executor);
	}

	/** Async form of {@link #get} without authentication. */
	public CompletableFuture<Fetched> getAsync(String url, String priorEtag, String priorLastModified,
			Executor executor) {
		return getAsync(url, priorEtag, priorLastModified, null, executor);
	}

	/** Async form of {@link #get}, including the optional bearer key. */
	public CompletableFuture<Fetched> getAsync(String url, String priorEtag, String priorLastModified,
			String bearerKey, Executor executor) {
		return CompletableFuture.supplyAsync(() -> {
			try {
				return get(url, priorEtag, priorLastModified, bearerKey);
			} catch (IOException failure) {
				throw new RuntimeException(failure);
			} catch (InterruptedException interrupted) {
				Thread.currentThread().interrupt();
				throw new RuntimeException(interrupted);
			}
		}, executor);
	}

	/** A non-2xx answer (403/429/5xx…), carrying the status for freshness reporting. */
	public static final class HttpRefusal extends IOException {
		public final int status;

		HttpRefusal(int status, String url) {
			super("HTTP " + status + " from " + url);
			this.status = status;
		}
	}

	private static boolean isGzipped(HttpResponse<byte[]> response) {
		Optional<String> encoding = response.headers().firstValue("Content-Encoding");
		return encoding.isPresent() && encoding.get().toLowerCase(Locale.ROOT).contains("gzip");
	}

	private static String header(HttpResponse<byte[]> response, String name) {
		return response.headers().firstValue(name).orElse(null);
	}

	/** The server's own freshness claim in ms, or 0 when none. A floor, never a target. */
	static long maxAgeMs(HttpResponse<byte[]> response) {
		Optional<String> cacheControl = response.headers().firstValue("Cache-Control");
		if (cacheControl.isEmpty()) {
			return 0;
		}
		String value = cacheControl.get().toLowerCase(Locale.ROOT);
		if (value.contains("no-store") || value.contains("no-cache")) {
			return 0;
		}
		Matcher matcher = MAX_AGE.matcher(value);
		if (!matcher.find()) {
			return 0;
		}
		try {
			return Long.parseLong(matcher.group(1)) * 1000L;
		} catch (NumberFormatException ignored) {
			return 0;
		}
	}

	static byte[] gunzip(byte[] gzipped) throws IOException {
		try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(gzipped));
				ByteArrayOutputStream out = new ByteArrayOutputStream(gzipped.length)) {
			in.transferTo(out);
			return out.toByteArray();
		}
	}

	public static String utf8(byte[] body) {
		return body == null ? null : new String(body, StandardCharsets.UTF_8);
	}
}
