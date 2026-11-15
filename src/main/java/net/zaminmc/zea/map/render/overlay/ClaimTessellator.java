package net.zaminmc.zea.map.render.overlay;

import net.zaminmc.zea.map.model.TownClaim;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns one town's claim rings into the rectangles the renderer draws:
 * horizontal <b>fill runs</b> (bands of identical crossing structure,
 * merged as far as the geometry allows) and <b>edge runs</b> (one thin
 * rectangle per ring edge — {@code ChunkRings} already simplified
 * collinear vertices, so every edge is a maximal straight segment).
 *
 * <p>Rectilinear-scanline discipline ({@code c0347f6}: merged runs drew
 * 3-5× fewer quads; {@code b92d190}: a cap applied mid-scan rendered
 * half-drawn towns — so the cap here counts <i>emitted</i> runs and
 * simply stops the whole town beyond it, never a partial band).
 * EarthMC claim geometry is chunk-rectilinear: horizontal edges sit at
 * chunk-corner Z values, so scanning between consecutive distinct
 * vertex rows yields whole-chunk bands in one step, not sixteen.
 *
 * <p>Even-odd over all rings in one pass (the {@code 19bd14a4} lesson):
 * an unclaimed pocket enclosed by the town reads as a hole because its
 * span is crossed an even number of times — the fill never paints it.
 * Non-axis-aligned edges (which EarthMC data does not produce) still
 * fill correctly through the crossing test; only their outlines are
 * skipped.
 *
 * <p>Results are cached by the claim's geometry signature plus
 * instance identity — {@code ClaimStore} reuses unchanged town
 * instances precisely so this cache ({@code a5570c6f}'s render cache)
 * stays valid across refreshes. The cache is small and dumb: beyond
 * {@link #MAX_CACHED} towns it is cleared whole; signatures are hashes
 * and an instance mismatch always re-tessellates.
 */
public final class ClaimTessellator {

	/** A merged horizontal fill rectangle, in block coordinates. */
	public record FillRun(int x0, int z0, int x1, int z1) {
	}

	/** One ring edge, in block coordinates. */
	public record EdgeRun(int x, int z, int length, boolean horizontal) {
	}

	/** A town's tessellated geometry. */
	public record Tessellation(List<FillRun> fills, List<EdgeRun> edges) {

		public static final Tessellation EMPTY = new Tessellation(List.of(), List.of());
	}

	/** Emitted-run ceiling per town; beyond it the town draws nothing rather than half. */
	public static final int MAX_RUNS = 8_192;
	/** Cached towns; beyond it the cache starts over. */
	private static final int MAX_CACHED = 256;

	private final Map<Integer, Cached> cache = new HashMap<>();

	/** The town's tessellation, from cache when its geometry has not changed. */
	public Tessellation tessellate(TownClaim town) {
		if (town.rings().isEmpty()) {
			return Tessellation.EMPTY;
		}
		int signature = town.geometrySignature();
		Cached cached = cache.get(signature);
		if (cached != null && cached.claim == town) {
			return cached.tessellation;
		}
		Tessellation tessellation = compute(town);
		if (cache.size() >= MAX_CACHED) {
			cache.clear();
		}
		cache.put(signature, new Cached(town, tessellation));
		return tessellation;
	}

	private record Cached(TownClaim claim, Tessellation tessellation) {
	}

	private static Tessellation compute(TownClaim town) {
		// Distinct vertex rows: fill structure is uniform between them.
		List<Integer> rows = new ArrayList<>();
		for (int[][] ring : town.rings()) {
			for (int[] point : ring) {
				rows.add(point[1]);
			}
		}
		rows.sort(Integer::compare);
		List<int[]> uniqueRows = new ArrayList<>(rows.size());
		for (int z : rows) {
			if (uniqueRows.isEmpty() || uniqueRows.get(uniqueRows.size() - 1)[0] != z) {
				uniqueRows.add(new int[] { z });
			}
		}

		List<FillRun> fills = new ArrayList<>();
		List<EdgeRun> edges = new ArrayList<>();
		for (int[][] ring : town.rings()) {
			for (int i = 0; i < ring.length; i++) {
				int[] from = ring[i];
				int[] to = ring[(i + 1) % ring.length];
				if (from[1] == to[1] && from[0] != to[0]) {
					int x = Math.min(from[0], to[0]);
					edges.add(new EdgeRun(x, from[1], Math.abs(to[0] - from[0]), true));
				} else if (from[0] == to[0] && from[1] != to[1]) {
					int z = Math.min(from[1], to[1]);
					edges.add(new EdgeRun(from[0], z, Math.abs(to[1] - from[1]), false));
				}
				// Diagonals (not produced by EarthMC data) are skipped as
				// outlines; the scanline below still fills them correctly.
			}
		}
		if (fills.size() + edges.size() > MAX_RUNS) {
			return Tessellation.EMPTY;
		}

		// Scan one representative row per band; merge runs whose band
		// above produced the identical span into vertical extensions.
		int total = edges.size();
		Map<Long, Integer> openRuns = new HashMap<>();
		for (int band = 0; band + 1 < uniqueRows.size(); band++) {
			int z0 = uniqueRows.get(band)[0];
			int z1 = uniqueRows.get(band + 1)[0];
			// Only runs that ended exactly at this band's top can extend.
			openRuns.values().removeIf(index -> fills.get(index).z1() != z0);
			double probe = z0 + 0.5;
			List<Integer> crossings = new ArrayList<>();
			for (int[][] ring : town.rings()) {
				for (int i = 0, j = ring.length - 1; i < ring.length; j = i++) {
					int zi = ring[i][1];
					int zj = ring[j][1];
					if ((zi > probe) != (zj > probe)) {
						int xi = ring[i][0];
						int xj = ring[j][0];
						crossings.add(xi + (int) ((probe - zi) * (xj - xi) / (double) (zj - zi)));
					}
				}
			}
			crossings.sort(Integer::compare);
			for (int i = 0; i + 1 < crossings.size(); i += 2) {
				int x0 = crossings.get(i);
				int x1 = crossings.get(i + 1);
				if (x0 == x1) {
					continue;
				}
				long span = (long) x0 << 32 | (x1 & 0xFFFFFFFFL);
				Integer extendable = openRuns.get(span);
				if (extendable != null) {
					FillRun run = fills.get(extendable);
					fills.set(extendable, new FillRun(run.x0(), run.z0(), run.x1(), z1));
				} else {
					fills.add(new FillRun(x0, z0, x1, z1));
					total++;
				}
				openRuns.put(span, fills.size() - 1);
				if (total > MAX_RUNS) {
					return Tessellation.EMPTY;
				}
			}
		}
		return new Tessellation(List.copyOf(fills), List.copyOf(edges));
	}
}
