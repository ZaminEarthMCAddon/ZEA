package net.zaminmc.zea.market.claims;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * One town's claim on one world, as drawn by both the world map and the
 * minimap.
 *
 * <p>Identity is {@code (world, key)}: every Moon town shares its name
 * with the Earth town it is an outpost of, so a name alone is ambiguous
 * and must never key a cache.
 *
 * <p>Geometry follows the GeoJSON ring convention:
 * {@code rings[0]} is the outer boundary, any further ring is a hole (an
 * unclaimed pocket fully enclosed by the town). Vertices are
 * {@code int[]{worldX, worldZ}} in block coordinates. Filling must be
 * even-odd over <b>all</b> rings in one pass — filling rings one at a
 * time paints enclosed wilderness solid.
 */
public record TownClaim(
		MapWorld world,
		String key,
		String name,
		String nation,
		int outlineRgb,
		int fillRgb,
		List<int[][]> rings,
		int minX,
		int maxX,
		int minZ,
		int maxZ) {

	public TownClaim {
		Objects.requireNonNull(world, "world");
		Objects.requireNonNull(key, "key");
		Objects.requireNonNull(rings, "rings");
		rings = List.copyOf(rings);
	}

	/** Canonical key for a town name. */
	public static String keyOf(String townName) {
		return townName == null ? "" : townName.toLowerCase(Locale.ROOT);
	}

	public static TownClaim of(MapWorld world, String name, String nation, int outlineRgb, int fillRgb, List<int[][]> rings) {
		int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
		int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
		for (int[][] ring : rings) {
			for (int[] point : ring) {
				minX = Math.min(minX, point[0]);
				maxX = Math.max(maxX, point[0]);
				minZ = Math.min(minZ, point[1]);
				maxZ = Math.max(maxZ, point[1]);
			}
		}
		if (minX > maxX || minZ > maxZ) {
			minX = maxX = minZ = maxZ = 0;
		}
		return new TownClaim(world, keyOf(name), name, nation, outlineRgb, fillRgb, rings, minX, maxX, minZ, maxZ);
	}

	public int centerX() {
		return minX + (maxX - minX) / 2;
	}

	public int centerZ() {
		return minZ + (maxZ - minZ) / 2;
	}

	/** Whether the claim's bounding box can intersect the world rectangle. */
	public boolean intersectsWorld(double left, double right, double top, double bottom) {
		return maxX >= left && minX <= right && maxZ >= top && minZ <= bottom;
	}

	/**
	 * Whether a block position is inside this claim, even-odd over all
	 * rings — a block in an enclosed unclaimed pocket is outside. Used
	 * to retire optimistic chunks once the published map catches up,
	 * and for hover lookups.
	 */
	public boolean coversBlock(int x, int z) {
		if (x < minX || x > maxX || z < minZ || z > maxZ || rings.isEmpty()) {
			return false;
		}
		boolean inside = false;
		for (int[][] ring : rings) {
			for (int i = 0, j = ring.length - 1; i < ring.length; j = i++) {
				int xi = ring[i][0], zi = ring[i][1];
				int xj = ring[j][0], zj = ring[j][1];
				if ((zi > z) != (zj > z)
								&& x < (xj - xi) * (z - zi) / (double) (zj - zi) + xi) {
					inside = !inside;
				}
			}
		}
		return inside;
	}

	/**
	 * Approximate chunk count, derived from ring areas — used to tell when
	 * a published outline has caught up with an API override (same bbox
	 * coverage and same approximate size).
	 */
	public int approximateChunkCount() {
		long blocks = 0;
		for (int[][] ring : rings) {
			blocks += Math.abs(signedArea2(ring)) / 2;
		}
		return (int) Math.max(1, Math.round(blocks / 256.0));
	}

	/** ARGB fill color at the given alpha. */
	public int argbFill(int alpha) {
		return (clampAlpha(alpha) << 24) | (fillRgb & 0x00FFFFFF);
	}

	/** ARGB outline color at the given alpha. */
	public int argbOutline(int alpha) {
		return (clampAlpha(alpha) << 24) | (outlineRgb & 0x00FFFFFF);
	}

	/**
	 * A copy with replacement geometry, keeping identity and colors. This
	 * is how an API-sourced correction takes the town's place in the
	 * published list — same visual language, no overlay boxes.
	 */
	public TownClaim withRings(List<int[][]> newRings) {
		return of(world, name, nation, outlineRgb, fillRgb, newRings);
	}

	/**
	 * A geometry signature: cheap to compare, changes only when the
	 * outline changes. Render caches key on this so a data refresh that
	 * leaves a town unchanged never re-tessellates it.
	 */
	public int geometrySignature() {
		int hash = 31 * world.hashCode() + key.hashCode();
		for (int[][] ring : rings) {
			hash = 31 * hash + ring.length;
			for (int[] point : ring) {
				hash = 31 * hash + point[0];
				hash = 31 * hash + point[1];
			}
		}
		return hash;
	}

	/**
	 * Whether this claim's outline covers another's bounding box with the
	 * same approximate size — the satisfaction test that retires a
	 * geometry override once the published map has caught up.
	 */
	public boolean covers(TownClaim other) {
		return minX <= other.minX && maxX >= other.maxX
						&& minZ <= other.minZ && maxZ >= other.maxZ
						&& approximateChunkCount() == other.approximateChunkCount();
	}

	private static long signedArea2(int[][] ring) {
		long sum = 0;
		for (int i = 0, j = ring.length - 1; i < ring.length; j = i++) {
			sum += (long) ring[j][0] * ring[i][1] - (long) ring[i][0] * ring[j][1];
		}
		return sum;
	}

	private static int clampAlpha(int alpha) {
		return Math.max(0, Math.min(255, alpha));
	}
}
