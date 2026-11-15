package net.zaminmc.zea.map.source.earthmcapi;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.zaminmc.zea.map.geometry.ChunkRings;
import net.zaminmc.zea.map.source.MapHttp;

import java.io.IOException;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * The EarthMC v4 API client, scoped to what the map subsystem needs:
 * one town's authoritative claim set.
 *
 * <p>{@code POST /v4/towns} with a {@code template} of
 * {@code name + coordinates} answers an array of town objects whose
 * {@code coordinates.townBlocks} is {@code [[chunkX, chunkZ], …]} — the
 * town's complete <b>Earth</b> claim, which updates faster than
 * markers.json and is the correction source for the claim pipeline.
 *
 * <p>Discipline:
 *
 * <ul>
 * <li><b>Gated to {@link #CONCURRENCY}.</b> The documented budget is
 *     180 req/min, but live testing showed 429s at roughly four
 *     concurrent requests — the gate matches the observed behavior, not
 *     the documentation.
 * <li><b>Retried on the ladder.</b> 429/5xx and transport failures
 *     retry up to {@link #ATTEMPTS} times with 0.4 s doubling backoff
 *     (0.4/0.8/1.6/3.2 s) — cheap when the API is healthy, and the
 *     difference between a blip and silently missing data otherwise.
 * <li><b>Public data only.</b> Town claims need no key; nothing here
 *     ever sends one. The key-bearing endpoints are other features'
 *     business.
 * <li><b>Unknown towns are empty, not failures.</b> A name absent from
 *     the answer (renamed or deleted) completes with
 *     {@link Optional#empty()}; a town present without
 *     {@code coordinates} has no claim blocks and completes with an
 *     empty set.
 * </ul>
 */
public final class EarthMcApiClient {

	/** One town's claim, as the API sees it. */
	public record TownChunks(String name, Set<Long> chunks) {
	}

	/** The live v4 base. */
	public static final String BASE = "https://api.earthmc.net/v4";

	/** Observed safe concurrency (429s begin just above this). */
	public static final int CONCURRENCY = 4;

	/** Total attempts per call: the initial one plus retries. */
	public static final int ATTEMPTS = 5;

	private final MapHttp http;
	private final Executor executor;
	private final String townsUrl;
	private final long retryBaseMillis;
	private final Semaphore gate = new Semaphore(CONCURRENCY);

	public EarthMcApiClient(MapHttp http, Executor executor) {
		this(http, executor, BASE + "/towns");
	}

	public EarthMcApiClient(MapHttp http, Executor executor, String townsUrl) {
		this(http, executor, townsUrl, 400L);
	}

	/** The full shape; the retry base is injectable so tests run the ladder instantly. */
	public EarthMcApiClient(MapHttp http, Executor executor, String townsUrl, long retryBaseMillis) {
		this.http = Objects.requireNonNull(http, "http");
		this.executor = Objects.requireNonNull(executor, "executor");
		this.townsUrl = Objects.requireNonNull(townsUrl, "townsUrl");
		this.retryBaseMillis = retryBaseMillis;
	}

	/**
	 * Fetches one town's claim chunks. Completes empty when the town is
	 * unknown; fails when the API could not be reached within the retry
	 * ladder.
	 */
	public CompletableFuture<Optional<TownChunks>> fetchTownClaims(String townName) {
		Objects.requireNonNull(townName, "townName");
		return CompletableFuture.supplyAsync(() -> {
			boolean acquired = false;
			try {
				gate.acquire();
				acquired = true;
				return fetchWithRetries(townName);
			} catch (InterruptedException interrupted) {
				Thread.currentThread().interrupt();
				throw new RuntimeException(interrupted);
			} finally {
				if (acquired) {
					gate.release();
				}
			}
		}, executor);
	}

	private Optional<TownChunks> fetchWithRetries(String townName) {
		String body = queryBody(townName);
		IOException last = null;
		for (int attempt = 1; attempt <= ATTEMPTS; attempt++) {
			if (attempt > 1) {
				sleep(retryBaseMillis << (attempt - 2));
			}
			try {
				MapHttp.Fetched fetched = http.post(townsUrl, body);
				return parse(fetched, townName);
			} catch (MapHttp.HttpRefusal refusal) {
				if (refusal.status == 404 || refusal.status == 400) {
					return Optional.empty();
				}
				last = refusal;
			} catch (IOException transport) {
				last = transport;
			} catch (InterruptedException interrupted) {
				Thread.currentThread().interrupt();
				throw new RuntimeException("interrupted while fetching " + townName, interrupted);
			}
		}
		throw new RuntimeException("EarthMC API unreachable for " + townName, last);
	}

	private static String queryBody(String townName) {
		JsonObject body = new JsonObject();
		JsonArray query = new JsonArray();
		query.add(townName);
		body.add("query", query);
		JsonObject template = new JsonObject();
		template.addProperty("name", true);
		template.addProperty("coordinates", true);
		body.add("template", template);
		return body.toString();
	}

	private static Optional<TownChunks> parse(MapHttp.Fetched fetched, String townName) {
		String text = MapHttp.utf8(fetched.body());
		JsonElement root;
		try {
			root = JsonParser.parseString(text);
		} catch (RuntimeException malformed) {
			return Optional.empty();
		}
		if (!root.isJsonArray()) {
			return Optional.empty();
		}
		for (JsonElement element : root.getAsJsonArray()) {
			if (!element.isJsonObject()) {
				continue;
			}
			JsonObject town = element.getAsJsonObject();
			String name = string(town, "name");
			if (name == null || !name.equalsIgnoreCase(townName)) {
				continue;
			}
			return Optional.of(new TownChunks(name, parseChunks(town)));
		}
		return Optional.empty();
	}

	private static Set<Long> parseChunks(JsonObject town) {
		JsonElement coordinates = town.get("coordinates");
		if (coordinates == null || !coordinates.isJsonObject()) {
			return Set.of();
		}
		JsonElement blocks = coordinates.getAsJsonObject().get("townBlocks");
		if (blocks == null || !blocks.isJsonArray()) {
			return Set.of();
		}
		JsonArray array = blocks.getAsJsonArray();
		java.util.Set<Long> out = new java.util.HashSet<>(array.size());
		for (JsonElement block : array) {
			if (!block.isJsonArray() || block.getAsJsonArray().size() < 2) {
				continue;
			}
			JsonArray pair = block.getAsJsonArray();
			if (!pair.get(0).isJsonPrimitive() || !pair.get(1).isJsonPrimitive()) {
				continue;
			}
			try {
				out.add(ChunkRings.key(pair.get(0).getAsInt(), pair.get(1).getAsInt()));
			} catch (RuntimeException ignored) {
				// One malformed pair must not discard the town's set.
			}
		}
		return out;
	}

	private static String string(JsonObject object, String field) {
		JsonElement value = object.get(field);
		return value != null && value.isJsonPrimitive() ? value.getAsString() : null;
	}

	private static void sleep(long millis) {
		try {
			TimeUnit.MILLISECONDS.sleep(millis);
		} catch (InterruptedException interrupted) {
			Thread.currentThread().interrupt();
			throw new RuntimeException(interrupted);
		}
	}
}
