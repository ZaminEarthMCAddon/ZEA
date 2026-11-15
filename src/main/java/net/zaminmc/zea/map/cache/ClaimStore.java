package net.zaminmc.zea.map.cache;

import net.zaminmc.zea.map.geometry.ChunkRings;
import net.zaminmc.zea.map.model.MapWorld;
import net.zaminmc.zea.map.model.TownClaim;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.LongSupplier;

/**
 * The authoritative in-memory state of town claims on both worlds:
 * the published documents, the API-sourced geometry overrides and the
 * optimistic layer that bridges a player's own claim commands until
 * the map catches up.
 *
 * <p>Every stage of that bridge has a purpose and a clock:
 *
 * <ul>
 * <li><b>Pending</b> ({@link #PENDING_TTL_MS}, 12 s) — a claim or
 *     unclaim command was sent and is waiting for the server's answer.
 *     Recorded at send time, while the affected chunk still answers to
 *     its current owner.
 * <li><b>Optimistic chunk</b> ({@link #OPTIMISTIC_TTL_MS}, 20 s) — a
 *     confirmed claim, drawn as one chunk in neutral colors until the
 *     authoritative API pull reshapes the whole town. Deliberately only
 *     the delta the map has not published, never a whole-town overlay
 *     ({@code c41b0185}).
 * <li><b>Geometry override</b> ({@link #OVERRIDE_TTL_MS}, 180 s) — a
 *     town's published outline replaced by rings traced from the v4
 *     API's {@code coordinates.townBlocks}, which updates faster than
 *     markers.json. Dropped the moment the published town covers the
 *     override with equal chunk count, or when it outlives any
 *     plausible publish lag — by then a stale API read is more likely
 *     than a slow map ({@code 62c1f9a7}).
 * </ul>
 *
 * <p>Identity discipline: a town is {@code (world, key)}; the API's
 * chunk list is the town's <b>Earth</b> claim and nothing else, so
 * {@link #applyApiChunks} refuses the Moon outright ({@code eb0c3ba}).
 * Unchanged towns keep their instances across publishes — the
 * identity-equality fast path render caches rely on
 * ({@code 7ea2bb8d}).
 */
public final class ClaimStore {

	/** One sent command awaiting the server's answer. */
	public record PendingClaim(MapWorld world, int chunkX, int chunkZ, boolean unclaim, long expiresAtMs) {
	}

	/** A confirmed claim drawn ahead of the published map. */
	public record OptimisticClaim(MapWorld world, int chunkX, int chunkZ, long expiresAtMs) {
	}

	/** How long a sent command stays believable without an answer. */
	public static final long PENDING_TTL_MS = 12_000L;
	/** How long an optimistic chunk draws before the API must have spoken. */
	public static final long OPTIMISTIC_TTL_MS = 20_000L;
	/** How long a geometry override survives a map that has not caught up. */
	public static final long OVERRIDE_TTL_MS = 180_000L;

	/** Neutral colors for claims whose town is not yet known. */
	static final int PENDING_OUTLINE_RGB = 0xD8D8D8;
	static final int PENDING_FILL_RGB = 0x8A8A8A;

	private static final class Override {
		final TownClaim claim;
		long expiresAtMs;

		Override(TownClaim claim, long expiresAtMs) {
			this.claim = claim;
			this.expiresAtMs = expiresAtMs;
		}
	}

	private final LongSupplier clock;

	private final Map<MapWorld, List<TownClaim>> published = new HashMap<>();
	private final Map<MapWorld, Map<String, TownClaim>> publishedByKey = new HashMap<>();
	private final Map<MapWorld, Map<String, Override>> overrides = new HashMap<>();
	private final Map<MapWorld, Map<Long, OptimisticClaim>> optimistic = new HashMap<>();
	private final List<PendingClaim> pendings = new ArrayList<>();

	private final Map<MapWorld, List<TownClaim>> effectiveCache = new HashMap<>();

	public ClaimStore(LongSupplier clock) {
		this.clock = Objects.requireNonNull(clock, "clock");
	}

	/**
	 * Replaces one world's published claims. Unchanged towns keep
	 * their previous instances (identity fast path); overrides whose
	 * towns have caught up are retired; optimistic chunks the map now
	 * shows are dropped.
	 */
	public synchronized void publish(MapWorld world, List<TownClaim> towns) {
		Map<String, TownClaim> previous = publishedByKey.getOrDefault(world, Map.of());
		Map<String, TownClaim> byKey = new HashMap<>();
		List<TownClaim> reused = new ArrayList<>(towns.size());
		for (TownClaim town : towns) {
			TownClaim old = previous.get(town.key());
			if (isUnchanged(old, town)) {
				// Same metadata and geometry signature: keep the
				// instance render caches already hold.
				reused.add(old);
				byKey.put(old.key(), old);
			} else {
				reused.add(town);
				byKey.put(town.key(), town);
			}
		}
		published.put(world, List.copyOf(reused));
		publishedByKey.put(world, byKey);
		dropSatisfiedOverrides(world);
		retireCaughtUpOptimistic(world);
		rebuildEffective(world);
	}

	/** The claims a renderer should draw for one world, overrides applied. */
	public synchronized List<TownClaim> claims(MapWorld world) {
		return effectiveCache.getOrDefault(world, List.of());
	}

	/** The optimistic delta chunks for one world, as drawable claims. */
	public synchronized List<TownClaim> optimisticClaims(MapWorld world) {
		Map<Long, OptimisticClaim> chunks = optimistic.get(world);
		if (chunks == null || chunks.isEmpty()) {
			return List.of();
		}
		List<TownClaim> out = new ArrayList<>(chunks.size());
		for (OptimisticClaim chunk : chunks.values()) {
			out.add(optimisticToClaim(world, chunk));
		}
		out.sort((a, b) -> Long.compare(a.minX(), b.minX()));
		return out;
	}

	/** Records a sent {@code /t claim}. The answer must arrive within {@link #PENDING_TTL_MS}. */
	public synchronized void claimSent(MapWorld world, int chunkX, int chunkZ) {
		pendings.add(new PendingClaim(world, chunkX, chunkZ, false, now() + PENDING_TTL_MS));
	}

	/** Records a sent {@code /t unclaim}, caught at send — before the server acts. */
	public synchronized void unclaimSent(MapWorld world, int chunkX, int chunkZ) {
		pendings.add(new PendingClaim(world, chunkX, chunkZ, true, now() + PENDING_TTL_MS));
	}

	/**
	 * The server confirmed a command. A confirmed claim becomes an
	 * optimistic chunk; a confirmed unclaim drops any optimistic
	 * claim on that chunk (the API pull that follows can remove it
	 * from the published outline). Returns the confirmed pending, or
	 * null when nothing matched.
	 */
	public synchronized PendingClaim confirmPending(MapWorld world, int chunkX, int chunkZ, boolean unclaim) {
		PendingClaim match = takePending(world, chunkX, chunkZ, unclaim);
		if (match == null) {
			return null;
		}
		if (unclaim) {
			Map<Long, OptimisticClaim> chunks = optimistic.get(world);
			if (chunks != null) {
				chunks.remove(chunkKey(chunkX, chunkZ));
			}
		} else {
			optimistic.computeIfAbsent(world, w -> new HashMap<>())
					.put(chunkKey(chunkX, chunkZ),
							new OptimisticClaim(world, chunkX, chunkZ, now() + OPTIMISTIC_TTL_MS));
		}
		return match;
	}

	/** The server rejected a command (already claimed, too close, funds…): forget it. */
	public synchronized void rejectPending(MapWorld world, int chunkX, int chunkZ) {
		takePending(world, chunkX, chunkZ, false);
		takePending(world, chunkX, chunkZ, true);
	}

	/**
	 * Replaces one town's geometry with rings traced from the v4
	 * API's chunk list. Earth-only by construction —
	 * {@code coordinates.townBlocks} is the town's Earth claim; a
	 * Moon claim must never be written from it. The town must exist
	 * in the published map: its colors come from there, and a town
	 * the map has never shown has nothing to re-shape. An empty
	 * chunk set is a town with no blocks left and yields an
	 * empty-geometry override that draws nothing.
	 *
	 * @return the override applied, or null when refused
	 */
	public synchronized TownClaim applyApiChunks(MapWorld world, String townName, Set<Long> chunks) {
		if (world != MapWorld.EARTH) {
			return null;
		}
		Map<String, TownClaim> byKey = publishedByKey.getOrDefault(world, Map.of());
		String key = TownClaim.keyOf(townName);
		TownClaim publishedTown = byKey.get(key);
		if (publishedTown == null) {
			return null;
		}
		List<int[][]> rings = ChunkRings.trace(chunks);
		TownClaim replacement = publishedTown.withRings(rings);
		overrides.computeIfAbsent(world, w -> new HashMap<>())
				.put(key, new Override(replacement, now() + OVERRIDE_TTL_MS));
		rebuildEffective(world);
		return replacement;
	}

	/** Expires pendings, optimistic chunks and overrides. Called once a tick. */
	public synchronized void tick() {
		long now = now();
		pendings.removeIf(pending -> pending.expiresAtMs() <= now);
		for (Map<Long, OptimisticClaim> chunks : optimistic.values()) {
			chunks.values().removeIf(chunk -> chunk.expiresAtMs() <= now);
		}
		boolean changed = false;
		for (Map<String, Override> byTown : overrides.values()) {
			changed |= byTown.values().removeIf(override -> override.expiresAtMs <= now);
		}
		if (changed) {
			for (MapWorld world : List.copyOf(effectiveCache.keySet())) {
				rebuildEffective(world);
			}
		}
	}

	/** The published town for a world and key, overrides applied — hover lookups. */
	public synchronized TownClaim town(MapWorld world, String key) {
		if (key == null) {
			return null;
		}
		Override override = overrideOf(world, key);
		if (override != null) {
			return override.claim;
		}
		return publishedByKey.getOrDefault(world, Map.of()).get(key);
	}

	/** Same identity, colors, nation and geometry: an unchanged town. */
	private static boolean isUnchanged(TownClaim old, TownClaim town) {
		if (old == null || !old.name().equals(town.name())) {
			return false;
		}
		if (old.nation() == null ? town.nation() != null : !old.nation().equals(town.nation())) {
			return false;
		}
		return old.outlineRgb() == town.outlineRgb() && old.fillRgb() == town.fillRgb()
					&& old.geometrySignature() == town.geometrySignature();
	}

	private PendingClaim takePending(MapWorld world, int chunkX, int chunkZ, boolean unclaim) {
		for (int i = pendings.size() - 1; i >= 0; i--) {
			PendingClaim pending = pendings.get(i);
			if (pending.world() == world && pending.chunkX() == chunkX && pending.chunkZ() == chunkZ
					&& pending.unclaim() == unclaim && pending.expiresAtMs() > now()) {
				pendings.remove(i);
				return pending;
			}
		}
		return null;
	}

	private Override overrideOf(MapWorld world, String key) {
		Map<String, Override> byTown = overrides.get(world);
		return byTown == null ? null : byTown.get(key);
	}

	private void dropSatisfiedOverrides(MapWorld world) {
		Map<String, Override> byTown = overrides.get(world);
		if (byTown == null) {
			return;
		}
		Map<String, TownClaim> byKey = publishedByKey.getOrDefault(world, Map.of());
		byTown.values().removeIf(override -> {
			TownClaim caughtUp = byKey.get(override.claim.key());
			return caughtUp != null && caughtUp.covers(override.claim);
		});
	}

	private void retireCaughtUpOptimistic(MapWorld world) {
		Map<Long, OptimisticClaim> chunks = optimistic.get(world);
		if (chunks == null || chunks.isEmpty()) {
			return;
		}
		List<TownClaim> towns = published.getOrDefault(world, List.of());
		chunks.values().removeIf(chunk -> {
			int x = chunk.chunkX() * 16 + 8;
			int z = chunk.chunkZ() * 16 + 8;
			for (TownClaim town : towns) {
				if (town.coversBlock(x, z)) {
					return true;
				}
			}
			return false;
		});
	}

	private void rebuildEffective(MapWorld world) {
		List<TownClaim> base = published.getOrDefault(world, List.of());
		Map<String, Override> byTown = overrides.get(world);
		if (byTown == null || byTown.isEmpty()) {
			effectiveCache.put(world, base);
			return;
		}
		List<TownClaim> out = new ArrayList<>(base.size());
		for (TownClaim town : base) {
			Override override = byTown.get(town.key());
			out.add(override == null ? town : override.claim);
		}
		effectiveCache.put(world, List.copyOf(out));
	}

	private static TownClaim optimisticToClaim(MapWorld world, OptimisticClaim chunk) {
		List<int[][]> rings = ChunkRings.trace(Set.of(chunkKey(chunk.chunkX(), chunk.chunkZ())));
		TownClaim claim = TownClaim.of(world, "", null, PENDING_OUTLINE_RGB, PENDING_FILL_RGB, rings);
		return claim;
	}

	private static long chunkKey(int chunkX, int chunkZ) {
		return ChunkRings.key(chunkX, chunkZ);
	}

	private long now() {
		return clock.getAsLong();
	}
}
