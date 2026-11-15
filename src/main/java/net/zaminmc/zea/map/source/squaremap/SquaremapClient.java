package net.zaminmc.zea.map.source.squaremap;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.zaminmc.zea.map.model.Freshness;
import net.zaminmc.zea.map.model.MapWorld;
import net.zaminmc.zea.map.model.PlayerMarker;
import net.zaminmc.zea.map.source.MapHttp;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Data client for EarthMC's squaremap instance: per-world claim
 * documents, the live player feed and the published world list.
 *
 * <p>This class is pure data plumbing — no Minecraft classes, no
 * scheduling. The subsystem drives it on its own timers and reads the
 * resulting futures; freshness bookkeeping updates here, at completion,
 * so every consumer sees one coherent outcome.
 *
 * <p>Rules with a history:
 *
 * <ul>
 * <li>Validators (ETag / If-Modified-Since) are kept <b>per world</b> for
 *     markers — they identify a document, and the two worlds have
 *     different documents at the same URL shape. A world switch starts
 *     from that world's own validators; nothing is reused across worlds.
 * <li>A 304 answer is a <b>success</b>: the data on screen is current,
 *     the server just had nothing to send. It refreshes the
 *     last-success clock.
 * <li>The player feed URL is fixed and the bearer key only ever travels
 *     to it — never to the squaremap host.
 * </ul>
 */
public final class SquaremapClient {

	private final MapHttp http;
	private final Executor executor;
	private final SquaremapUrls urls;

	private final Map<MapWorld, String> markerEtags = new ConcurrentHashMap<>();
	private final Map<MapWorld, String> markerLastModified = new ConcurrentHashMap<>();
	private final AtomicReference<String> playersEtag = new AtomicReference<>();
	private final AtomicReference<String> playersLastModified = new AtomicReference<>();

	private final Map<MapWorld, AtomicReference<Freshness>> claimsFreshness = new ConcurrentHashMap<>();
	private final AtomicReference<Freshness> playersFreshness = new AtomicReference<>(Freshness.NEVER);

	public SquaremapClient(MapHttp http, Executor executor) {
		this(http, executor, SquaremapUrls.live());
	}

	public SquaremapClient(MapHttp http, Executor executor, SquaremapUrls urls) {
		this.http = http;
		this.executor = executor;
		this.urls = urls;
	}

	/**
	 * Fetches one world's claims. Updates validators and freshness on
	 * completion. On a 304 the caller's current document is returned
	 * unchanged — the server just confirmed it is still current. The
	 * future fails (IOException cause) on refusal or transport error,
	 * leaving the caller's previous data in place.
	 */
	public CompletableFuture<MarkersDocument> fetchMarkers(MapWorld world, MarkersDocument previous) {
		claimsFreshnessHolder(world).updateAndGet(f -> f.attempting(now()));
		String url = urls.markers(world);
		String etag = markerEtags.get(world);
		String lastModified = markerLastModified.get(world);
		return http.getAsync(url, etag, lastModified, executor)
				.thenApply(fetched -> {
					if (fetched.notModified()) {
						claimsFreshnessHolder(world).updateAndGet(f -> f.succeeded(now()));
						return previous == null ? MarkersDocument.EMPTY : previous;
					}
					if (fetched.etag() != null) {
						markerEtags.put(world, fetched.etag());
					}
					if (fetched.lastModified() != null) {
						markerLastModified.put(world, fetched.lastModified());
					}
					MarkersDocument document = MarkersParser.parse(world, MapHttp.utf8(fetched.body()));
					if (document == MarkersDocument.EMPTY) {
						// Parsed to nothing: treat as a failure so a blank
						// document never reads as a successful refresh.
						claimsFreshnessHolder(world).updateAndGet(f -> f.failed(now()));
						throw new RuntimeException("markers.json parsed empty for " + world);
					}
					claimsFreshnessHolder(world).updateAndGet(f -> f.succeeded(now()));
					return document;
				})
				.exceptionallyCompose(failure -> {
					recordFailure(claimsFreshnessHolder(world), failure);
					return CompletableFuture.failedFuture(failure);
				});
	}

	/**
	 * Fetches the live player feed. The optional bearer key is sent only
	 * to the fixed feed URL. 304 keeps the previous markers — the caller's
	 * copy is still current — and reports success.
	 *
	 * @param previous the markers the caller currently holds, returned
	 *                 unchanged on a 304
	 */
	public CompletableFuture<List<PlayerMarker>> fetchPlayers(List<PlayerMarker> previous, String bearerKey) {
		playersFreshness.updateAndGet(f -> f.attempting(now()));
		String etag = playersEtag.get();
		String lastModified = playersLastModified.get();
		CompletableFuture<List<PlayerMarker>> result = http
				.getAsync(urls.players(), etag, lastModified, bearerKey, executor)
				.thenApply(fetched -> {
					if (fetched.notModified()) {
						playersFreshness.updateAndGet(f -> f.succeeded(now()));
						return previous == null ? List.<PlayerMarker>of() : previous;
					}
					if (fetched.etag() != null) {
						playersEtag.set(fetched.etag());
					}
					if (fetched.lastModified() != null) {
						playersLastModified.set(fetched.lastModified());
					}
					List<PlayerMarker> markers = PlayerMarker.parseAll(
							JsonParser.parseString(MapHttp.utf8(fetched.body())));
					playersFreshness.updateAndGet(f -> f.succeeded(now()));
					return markers;
				});
		return result.exceptionallyCompose(failure -> {
			recordFailure(playersFreshness, failure);
			CompletableFuture<List<PlayerMarker>> failed = new CompletableFuture<>();
			failed.completeExceptionally(failure);
			return failed;
		});
	}

	/** The squaremap world list from settings.json; empty map on failure. */
	public CompletableFuture<Map<String, String>> fetchWorlds() {
		return http.getAsync(urls.worlds(), null, null, executor)
				.thenApply(fetched -> {
					Map<String, String> out = new java.util.LinkedHashMap<>();
					if (fetched.notModified() || fetched.body() == null) {
						return out;
					}
					JsonElement root = JsonParser.parseString(MapHttp.utf8(fetched.body()));
					if (!root.isJsonObject()) {
						return out;
					}
					JsonElement worlds = root.getAsJsonObject().get("worlds");
					if (worlds == null || !worlds.isJsonArray()) {
						return out;
					}
					for (JsonElement element : worlds.getAsJsonArray()) {
						if (!element.isJsonObject()) {
							continue;
						}
						JsonObject world = element.getAsJsonObject();
						String name = world.has("name") && world.get("name").isJsonPrimitive()
								? world.get("name").getAsString() : null;
						if (name == null || name.isBlank()) {
							continue;
						}
						String display = world.has("display_name") && world.get("display_name").isJsonPrimitive()
								? world.get("display_name").getAsString() : name;
						out.put(name, display == null || display.isBlank() ? name : display);
					}
					return out;
				})
				.exceptionally(failure -> Map.of());
	}

	public Freshness claimsFreshness(MapWorld world) {
		return claimsFreshnessHolder(world).get();
	}

	private AtomicReference<Freshness> claimsFreshnessHolder(MapWorld world) {
		return claimsFreshness.computeIfAbsent(world, w -> new AtomicReference<>(Freshness.NEVER));
	}

	public Freshness playersFreshness() {
		return playersFreshness.get();
	}

	private static void recordFailure(AtomicReference<Freshness> freshness, Throwable failure) {
		MapHttp.HttpRefusal refusal = findRefusal(failure);
		if (refusal != null) {
			freshness.updateAndGet(f -> f.refused(now(), refusal.status));
		} else {
			freshness.updateAndGet(f -> f.failed(now()));
		}
	}

	/**
	 * Walks the cause chain for the refusal: dependent stages wrap source
	 * failures in CompletionException and the async layer wraps checked
	 * exceptions in RuntimeException, so the refusal may sit two or three
	 * links deep.
	 */
	private static MapHttp.HttpRefusal findRefusal(Throwable failure) {
		Throwable current = failure;
		for (int depth = 0; current != null && depth < 5; depth++) {
			if (current instanceof MapHttp.HttpRefusal refusal) {
				return refusal;
			}
			current = current.getCause();
		}
		return null;
	}

	private static long now() {
		return System.currentTimeMillis();
	}
}
