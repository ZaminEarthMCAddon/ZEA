package net.zaminmc.zea.map.geometry;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChunkRingsTest {

	private static Set<Long> chunks(int... xzPairs) {
		Set<Long> set = new LinkedHashSet<>();
		for (int i = 0; i + 1 < xzPairs.length; i += 2) {
			set.add(ChunkRings.key(xzPairs[i], xzPairs[i + 1]));
		}
		return set;
	}

	private static long area(int[][] ring) {
		long sum = 0;
		for (int i = 0, j = ring.length - 1; i < ring.length; j = i++) {
			sum += (long) ring[j][0] * ring[i][1] - (long) ring[i][0] * ring[j][1];
		}
		return Math.abs(sum) / 2;
	}

	private static int[] bbox(List<int[][]> rings) {
		int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
		for (int[][] ring : rings) {
			for (int[] p : ring) {
				minX = Math.min(minX, p[0]);
				maxX = Math.max(maxX, p[0]);
				minZ = Math.min(minZ, p[1]);
				maxZ = Math.max(maxZ, p[1]);
			}
		}
		return new int[] {minX, maxX, minZ, maxZ};
	}

	/** Even-odd point-in-polygon on block corners. */
	private static boolean inside(int[][] ring, double x, double z) {
		boolean inside = false;
		for (int i = 0, j = ring.length - 1; i < ring.length; j = i++) {
			if ((ring[i][1] > z) != (ring[j][1] > z)
					&& x < (double) (ring[j][0] - ring[i][0]) * (z - ring[i][1]) / (ring[j][1] - ring[i][1]) + ring[i][0]) {
				inside = !inside;
			}
		}
		return inside;
	}

	@Test
	void emptyInputYieldsNoRings() {
		assertEquals(List.of(), ChunkRings.trace(null));
		assertEquals(List.of(), ChunkRings.trace(Set.of()));
	}

	@Test
	void singleChunkIsOneClosedSquare() {
		List<int[][]> rings = ChunkRings.trace(chunks(1, 1));
		assertEquals(1, rings.size());
		int[][] ring = rings.get(0);
		assertEquals(4, ring.length, "a square must simplify to 4 corners");
		assertCyclicUnitSteps(ring);
		assertEquals(256, area(ring));
		int[] box = bbox(rings);
		assertEquals(16, box[0]);
		assertEquals(32, box[1]);
		assertEquals(16, box[2]);
		assertEquals(32, box[3]);
	}

	@Test
	void adjacentChunksMergeIntoOneOutline() {
		List<int[][]> rings = ChunkRings.trace(chunks(0, 0, 1, 0));
		assertEquals(1, rings.size());
		assertEquals(512, area(rings.get(0)));
		assertEquals(4, rings.get(0).length, "the shared edge must vanish: 4 corners, not 6");
	}

	@Test
	void straightRunCollinearPointsAreSimplified() {
		List<int[][]> rings = ChunkRings.trace(chunks(0, 0, 1, 0, 2, 0, 3, 0, 4, 0));
		assertEquals(1, rings.size());
		assertEquals(4, rings.get(0).length, "a 5-chunk strip is 4 corners");
		assertEquals(5 * 256, area(rings.get(0)));
	}

	@Test
	void diagonalChunksStaySeparateRings() {
		// NE (1,0) and SW (0,1) touch only at the point (16,16).
		List<int[][]> rings = ChunkRings.trace(chunks(1, 0, 0, 1));
		assertEquals(2, rings.size(), "a diagonal pinch must not stitch a figure-eight");
		assertEquals(256, area(rings.get(0)));
		assertEquals(256, area(rings.get(1)));
	}

	@Test
	void enclosedWildernessBecomesAHole() {
		// 3x3 minus the center: the center is unclaimed land fully enclosed.
		List<int[][]> rings = ChunkRings.trace(chunks(
				0, 0, 1, 0, 2, 0,
				0, 1, 2, 1,
				0, 2, 1, 2, 2, 2));
		assertEquals(2, rings.size());
		int[][] outer = rings.get(0);
		int[][] hole = rings.get(1);
		assertEquals(9 * 256, area(outer));
		assertEquals(256, area(hole));
		assertTrue(inside(outer, 24, 24), "the outer ring must cover the whole claim");
		assertTrue(inside(hole, 24, 24), "the hole ring covers the enclosed pocket; even-odd punches it out");
		long outerSigned = signedArea(outer);
		long holeSigned = signedArea(hole);
		assertTrue(outerSigned * holeSigned < 0, "hole must wind opposite the outer boundary");
		// Even-odd union: exactly the 8 claimed chunks are inside an odd number of rings.
		for (int cx = 0; cx <= 2; cx++) {
			for (int cz = 0; cz <= 2; cz++) {
				boolean claimed = !(cx == 1 && cz == 1);
				double px = cx * 16 + 8, pz = cz * 16 + 8;
				int crossings = (inside(outer, px, pz) ? 1 : 0) + (inside(hole, px, pz) ? 1 : 0);
				assertEquals(claimed, crossings % 2 == 1,
						"chunk (" + cx + "," + cz + ") even-odd fill mismatch");
			}
		}
	}

	@Test
	void disconnectedClaimsProduceOneRingEach() {
		List<int[][]> rings = ChunkRings.trace(chunks(0, 0, 10, 10));
		assertEquals(2, rings.size());
		assertEquals(256, area(rings.get(0)));
		assertEquals(256, area(rings.get(1)));
	}

	@Test
	void outpostsAreTheirOwnRegion() {
		// A 2x2 home near the origin plus a single-chunk outpost far away.
		List<int[][]> rings = ChunkRings.trace(chunks(
				0, 0, 1, 0,
				0, 1, 1, 1,
				30, 30));
		assertEquals(2, rings.size());
		assertEquals(4 * 256, area(rings.get(0)), "the larger region is sorted first");
		assertEquals(256, area(rings.get(1)));
	}

	@Test
	void negativeChunkCoordinatesWork() {
		List<int[][]> rings = ChunkRings.trace(chunks(-1, -1, -2, -1));
		assertEquals(1, rings.size());
		assertEquals(512, area(rings.get(0)));
		int[] box = bbox(rings);
		assertEquals(-32, box[0]);
		assertEquals(0, box[1], "chunk -1 spans to x=0");
	}

	@Test
	void identicalInputAlwaysYieldsIdenticalRings() {
		Set<Long> a = new HashSet<>(chunks(1, 0, 0, 1, 3, 3, 3, 4, 4, 3));
		Set<Long> b = new LinkedHashSet<>();
		// Same contents, deliberately different insertion order.
		b.add(ChunkRings.key(4, 3));
		b.add(ChunkRings.key(0, 1));
		b.add(ChunkRings.key(3, 3));
		b.add(ChunkRings.key(1, 0));
		b.add(ChunkRings.key(3, 4));
		List<int[][]> ra = ChunkRings.trace(a);
		List<int[][]> rb = ChunkRings.trace(b);
		assertEquals(ra.size(), rb.size());
		for (int i = 0; i < ra.size(); i++) {
			assertEquals(ra.get(i).length, rb.get(i).length);
			for (int j = 0; j < ra.get(i).length; j++) {
				assertEquals(ra.get(i)[j][0], rb.get(i)[j][0], "ring " + i + " point " + j);
				assertEquals(ra.get(i)[j][1], rb.get(i)[j][1], "ring " + i + " point " + j);
			}
		}
	}

	@Test
	void everyRingIsAClosedUnitStepCycle() {
		List<int[][]> rings = ChunkRings.trace(chunks(
				0, 0, 1, 0, 2, 0,
				0, 1, 2, 1,
				0, 2, 1, 2, 2, 2,
				7, 7));
		assertTrue(rings.size() >= 2);
		for (int[][] ring : rings) {
			assertCyclicUnitSteps(ring);
		}
	}

	/**
	 * Rings are closed cycles of axis-aligned 16-block steps. The walk
	 * records edge endpoints, so the cycle is rotated — the first element
	 * is the successor of the last, not its equal.
	 */
	private static void assertCyclicUnitSteps(int[][] ring) {
		for (int i = 0; i < ring.length; i++) {
			int[] current = ring[i];
			int[] next = ring[(i + 1) % ring.length];
			int dx = Math.abs(next[0] - current[0]);
			int dz = Math.abs(next[1] - current[1]);
			// Simplified rings merge straight runs: a step is any multiple
			// of one chunk side along exactly one axis.
			boolean unitStep = (dx > 0 && dx % 16 == 0 && dz == 0) || (dz > 0 && dz % 16 == 0 && dx == 0);
			assertTrue(unitStep, "vertices " + i + "->" + ((i + 1) % ring.length)
					+ " must be one chunk side apart: [" + current[0] + "," + current[1]
					+ "] to [" + next[0] + "," + next[1] + "]");
		}
	}

	private static long signedArea(int[][] ring) {
		long sum = 0;
		for (int i = 0, j = ring.length - 1; i < ring.length; j = i++) {
			sum += (long) ring[j][0] * ring[i][1] - (long) ring[i][0] * ring[j][1];
		}
		return sum;
	}
}
