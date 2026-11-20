package net.zaminmc.zea.market.claims;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Turns a set of claimed chunks into polygon rings — the same shape
 * squaremap publishes for them, derived independently from the flat chunk
 * list the EarthMC API returns in {@code coordinates.townBlocks}.
 *
 * <p>Algorithm: every chunk side with no claimed chunk beyond it is a
 * boundary edge; edges are wound so they chain head-to-tail into closed
 * loops; walking the chains yields rings with the outer boundary and any
 * enclosed gap (hole) in opposite orientations, which is exactly the
 * GeoJSON outer-first, holes-after convention {@code TownClaim} expects.
 * Even-odd fill over all rings of a town in one pass renders holes as
 * unclaimed pockets.
 *
 * <p>Two deliberate deviations from the reference implementation, both
 * behavior-preserving where it matters:
 *
 * <ul>
 * <li><b>Deterministic iteration.</b> Chunks are visited in sorted key
 *     order, so the same input always yields the same rings — the
 *     reference iterated a plain {@code HashSet} and could produce either
 *     two separate rings or a joined figure-eight for a diagonally
 *     pinched pair. Determinism matters here because geometry signatures
 *     drive render-cache invalidation.
 * <li><b>Right-turn preference at pinch points.</b> Where four chunks
 *     meet and the two diagonal ones are claimed, two outgoing edges
 *     leave the pinch. Turning right (staying on the same chunk's
 *     clockwise perimeter) always separates the loops, producing clean
 *     non-self-touching rings; the reference took the first candidate,
 *     which could stitch the pair into a figure-eight. Both shapes fill
 *     identically under even-odd, but separate rings are valid GeoJSON.
 * </ul>
 */
public final class ChunkRings {

	private static final int CHUNK = 16;

	private ChunkRings() {
	}

	/** Packs a chunk coordinate into one long. */
	public static long key(int chunkX, int chunkZ) {
		return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
	}

	/**
	 * Rings in block coordinates, largest first. Empty if the set is null
	 * or empty. Ring vertices are {@code int[]{worldX, worldZ}}.
	 */
	public static List<int[][]> trace(Set<Long> chunks) {
		if (chunks == null || chunks.isEmpty()) {
			return List.of();
		}

		// TreeMap: sorted chunk visit order, and sorted edge start points,
		// so identical inputs produce identical rings.
		TreeMap<Long, List<long[]>> outgoing = new TreeMap<>();
		for (long chunk : chunks) {
			int cx = (int) (chunk >> 32);
			int cz = (int) chunk;
			int x0 = cx * CHUNK, z0 = cz * CHUNK;
			int x1 = x0 + CHUNK, z1 = z0 + CHUNK;
			if (!chunks.contains(key(cx, cz - 1))) {
				edge(outgoing, x0, z0, x1, z0);
			}
			if (!chunks.contains(key(cx + 1, cz))) {
				edge(outgoing, x1, z0, x1, z1);
			}
			if (!chunks.contains(key(cx, cz + 1))) {
				edge(outgoing, x1, z1, x0, z1);
			}
			if (!chunks.contains(key(cx - 1, cz))) {
				edge(outgoing, x0, z1, x0, z0);
			}
		}

		List<int[][]> rings = new ArrayList<>();
		while (!outgoing.isEmpty()) {
			Map.Entry<Long, List<long[]>> firstEntry = outgoing.firstEntry();
			long[] first = firstEntry.getValue().get(0);
			int[][] ring = walk(outgoing, first);
			if (ring.length >= 3) {
				rings.add(ring);
			}
		}
		if (rings.size() > 1) {
			int widest = 0;
			long best = -1;
			for (int i = 0; i < rings.size(); i++) {
				long area = Math.abs(area2(rings.get(i)));
				if (area > best) {
					best = area;
					widest = i;
				}
			}
			rings.add(0, rings.remove(widest));
		}
		return rings;
	}

	private static void edge(Map<Long, List<long[]>> outgoing, int x0, int z0, int x1, int z1) {
		outgoing.computeIfAbsent(point(x0, z0), k -> new ArrayList<>())
				.add(new long[] {point(x0, z0), point(x1, z1)});
	}

	private static long point(int x, int z) {
		return ((long) x << 32) | (z & 0xFFFFFFFFL);
	}

	/** Follows edges from the given one until the loop closes, consuming each as it goes. */
	private static int[][] walk(Map<Long, List<long[]>> outgoing, long[] first) {
		List<int[]> points = new ArrayList<>();
		long[] current = first;
		long start = current[0];
		while (true) {
			consume(outgoing, current);
			points.add(new int[] {(int) (current[1] >> 32), (int) current[1]});
			if (current[1] == start) {
				break;
			}
			List<long[]> next = outgoing.get(current[1]);
			if (next == null || next.isEmpty()) {
				break; // open chain: stop rather than spin
			}
			current = pick(next, current);
		}
		return simplify(points);
	}

	private static void consume(Map<Long, List<long[]>> outgoing, long[] edge) {
		List<long[]> at = outgoing.get(edge[0]);
		if (at != null) {
			at.remove(edge);
			if (at.isEmpty()) {
				outgoing.remove(edge[0]);
			}
		}
	}

	/**
	 * Chooses the outgoing edge at a junction. At most two candidates ever
	 * share a start point (a diagonal pinch); turning right keeps the walk
	 * on the same chunk's perimeter, so pinched diagonals come out as
	 * separate rings instead of a self-touching figure-eight.
	 */
	private static long[] pick(List<long[]> candidates, long[] incoming) {
		if (candidates.size() == 1) {
			return candidates.get(0);
		}
		int dx = (int) (incoming[1] >> 32) - (int) (incoming[0] >> 32);
		int dz = (int) incoming[1] - (int) incoming[0];
		int rightX = -dz;
		int rightZ = dx;
		for (long[] candidate : candidates) {
			int cdx = (int) (candidate[1] >> 32) - (int) (candidate[0] >> 32);
			int cdz = (int) candidate[1] - (int) candidate[0];
			if (cdx == rightX && cdz == rightZ) {
				return candidate;
			}
		}
		return candidates.get(0);
	}

	/** Drops the points in the middle of a straight run — a 181-chunk town is a handful of corners. */
	private static int[][] simplify(List<int[]> points) {
		int n = points.size();
		if (n < 3) {
			return new int[0][];
		}
		List<int[]> out = new ArrayList<>(n);
		for (int i = 0; i < n; i++) {
			int[] prev = points.get((i - 1 + n) % n);
			int[] current = points.get(i);
			int[] next = points.get((i + 1) % n);
			boolean collinear = (current[0] - prev[0]) * (next[1] - current[1])
					== (current[1] - prev[1]) * (next[0] - current[0]);
			if (!collinear) {
				out.add(current);
			}
		}
		return out.size() >= 3 ? out.toArray(new int[0][]) : points.toArray(new int[0][]);
	}

	/** Twice the signed area, for ordering rings outer-first. */
	private static long area2(int[][] ring) {
		long sum = 0;
		for (int i = 0, j = ring.length - 1; i < ring.length; j = i++) {
			sum += (long) ring[j][0] * ring[i][1] - (long) ring[i][0] * ring[j][1];
		}
		return sum;
	}
}
