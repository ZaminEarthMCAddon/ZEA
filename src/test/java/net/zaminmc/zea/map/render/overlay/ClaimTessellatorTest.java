package net.zaminmc.zea.map.render.overlay;

import net.zaminmc.zea.map.model.MapWorld;
import net.zaminmc.zea.map.model.TownClaim;
import net.zaminmc.zea.map.render.overlay.ClaimTessellator.EdgeRun;
import net.zaminmc.zea.map.render.overlay.ClaimTessellator.FillRun;
import net.zaminmc.zea.map.render.overlay.ClaimTessellator.Tessellation;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The claim geometry compiler: band scanning, run merging, the even-odd
 * hole rule, the instance-and-signature render cache, and the whole-town
 * run ceiling — the arithmetic that decides what a town "looks like"
 * before any colour or projection is applied.
 */
class ClaimTessellatorTest {

        private final ClaimTessellator tessellator = new ClaimTessellator();

        private static int[][] ring(int... xz) {
                int[][] points = new int[xz.length / 2][];
                for (int i = 0; i < points.length; i++) {
                        points[i] = new int[] { xz[i * 2], xz[i * 2 + 1] };
                }
                return points;
        }

        private static TownClaim town(int[][]... rings) {
                return TownClaim.of(MapWorld.EARTH, "Testville", null, 0xFF0000, 0x0000FF, List.of(rings));
        }

        @Test
        void rectangleTessellatesToOneFillAndFourEdges() {
                Tessellation tessellation = tessellator.tessellate(town(ring(0, 0, 256, 0, 256, 256, 0, 256)));
                assertEquals(List.of(new FillRun(0, 0, 256, 256)), tessellation.fills());
                assertEquals(List.of(
                                                new EdgeRun(0, 0, 256, true),
                                                new EdgeRun(256, 0, 256, false),
                                                new EdgeRun(0, 256, 256, true),
                                                new EdgeRun(0, 0, 256, false)), tessellation.edges());
        }

        @Test
        void lShapeTessellatesToTwoUnmergeableRuns() {
                Tessellation tessellation = tessellator.tessellate(
                                                town(ring(0, 0, 512, 0, 512, 256, 256, 256, 256, 512, 0, 512)));
                assertEquals(List.of(
                                                new FillRun(0, 0, 512, 256),
                                                new FillRun(0, 256, 256, 512)), tessellation.fills());
                assertEquals(6, tessellation.edges().size());
        }

        @Test
        void identicalAdjacentBandsMergeIntoOneRun() {
                // Collinear side vertices split the rectangle into two bands with
                // identical spans — the scanline must merge them back into one run.
                Tessellation tessellation = tessellator.tessellate(
                                                town(ring(0, 0, 256, 0, 256, 256, 256, 512, 0, 512, 0, 256)));
                assertEquals(List.of(new FillRun(0, 0, 256, 512)), tessellation.fills());
                // Edges stay per ring segment: collinear simplification is
                // ChunkRings' job upstream, not the tessellator's.
                assertEquals(6, tessellation.edges().size());
        }

        @Test
        void enclosedPocketIsAHole() {
                TownClaim claim = town(
                                                ring(0, 0, 512, 0, 512, 512, 0, 512),
                                                ring(128, 128, 384, 128, 384, 384, 128, 384));
                Tessellation tessellation = tessellator.tessellate(claim);
                assertEquals(List.of(
                                                new FillRun(0, 0, 512, 128),
                                                new FillRun(0, 128, 128, 384),
                                                new FillRun(384, 128, 512, 384),
                                                new FillRun(0, 384, 512, 512)), tessellation.fills());
                // The same even-odd rule the model's point test uses.
                assertTrue(claim.coversBlock(64, 64), "claimed ground is covered");
                assertFalse(claim.coversBlock(256, 256), "the enclosed pocket stays unclaimed");
                assertTrue(claim.coversBlock(448, 448), "claimed ground is covered");
        }

        @Test
        void bandsSeparatedByAGapNeverMerge() {
                // Two disjoint bars: the (0, 512) span recurs after an empty band.
                // A stale open-run entry must not bridge the gap and paint the
                // wilderness between the bars.
                Tessellation tessellation = tessellator.tessellate(town(
                                                ring(0, 0, 512, 0, 512, 128, 0, 128),
                                                ring(0, 384, 512, 384, 512, 512, 0, 512)));
                assertEquals(List.of(
                                                new FillRun(0, 0, 512, 128),
                                                new FillRun(0, 384, 512, 512)), tessellation.fills());
        }

        @Test
        void cacheReusesUnchangedTownInstances() {
                TownClaim claim = town(ring(0, 0, 256, 0, 256, 256, 0, 256));
                Tessellation first = tessellator.tessellate(claim);
                assertSame(first, tessellator.tessellate(claim), "same instance, same geometry: cached");

                // A different instance with identical geometry re-tessellates —
                // the cache is keyed by signature *and* instance identity.
                TownClaim twin = town(ring(0, 0, 256, 0, 256, 256, 0, 256));
                assertEquals(first.fills(), tessellator.tessellate(twin).fills());
                assertNotSame(first, tessellator.tessellate(twin));

                // New geometry on the same identity re-tessellates too.
                TownClaim grown = claim.withRings(List.<int[][]>of(ring(0, 0, 512, 0, 512, 512, 0, 512)));
                assertEquals(List.of(new FillRun(0, 0, 512, 512)), tessellator.tessellate(grown).fills());
        }

        @Test
        void cacheFlushBeyondCapacityStartsOver() {
                TownClaim first = town(ring(0, 0, 256, 0, 256, 256, 0, 256));
                Tessellation firstResult = tessellator.tessellate(first);
                // Fill the cache to capacity with distinct towns, then push one
                // past it: the whole cache clears, and the first town recomputes.
                // The offsets start at 1024: a loop town at the origin would be
                // geometry-identical to the first town and share its signature.
                for (int i = 0; i < 255; i++) {
                                int x = (i + 1) * 1024;
                                tessellator.tessellate(town(ring(x, 0, x + 256, 0, x + 256, 256, x, 256)));
                }
                assertSame(firstResult, tessellator.tessellate(first), "still cached at capacity");
                tessellator.tessellate(town(ring(-1024, 0, -768, 0, -768, 256, -1024, 256)));
                assertNotSame(firstResult, tessellator.tessellate(first), "the flush dropped the early towns");
        }

        @Test
        void justUnderTheRunCeilingDrawsEverything() {
                // A checkerboard of 1638 single-chunk squares in two rows: no two
                // adjacent bands share a span, so nothing merges — 1638 fill runs
                // plus 6552 edges stays under the ceiling.
                List<int[][]> squares = new ArrayList<>();
                int columns = 1638;
                for (int row = 0; row < 2; row++) {
                                for (int column = 0; column < columns; column++) {
                                        if ((column + row) % 2 == 0) {
                                                int x = column * 256;
                                                int z = row * 128;
                                                squares.add(ring(x, z, x + 128, z, x + 128, z + 128, x, z + 128));
                                        }
                                }
                }
                assertEquals(columns, squares.size());
                Tessellation tessellation = tessellator.tessellate(
                                                TownClaim.of(MapWorld.EARTH, "Checkerland", null, 0xFF0000, 0x0000FF, squares));
                assertEquals(columns, tessellation.fills().size());
                assertEquals(4 * columns, tessellation.edges().size());
        }

        @Test
        void townsBeyondTheRunCeilingDrawNothingRatherThanHalf() {
                // One more checkerboard square than the ceiling allows: the whole
                // town is dropped, never a partial band.
                List<int[][]> squares = new ArrayList<>();
                int columns = 1639;
                for (int row = 0; row < 2; row++) {
                                for (int column = 0; column < columns; column++) {
                                        if ((column + row) % 2 == 0) {
                                                int x = column * 256;
                                                int z = row * 128;
                                                squares.add(ring(x, z, x + 128, z, x + 128, z + 128, x, z + 128));
                                        }
                                }
                }
                assertEquals(columns, squares.size());
                Tessellation tessellation = tessellator.tessellate(
                                                TownClaim.of(MapWorld.EARTH, "Checkerland", null, 0xFF0000, 0x0000FF, squares));
                assertEquals(Tessellation.EMPTY, tessellation);
        }

        @Test
        void ringlessTownsTessellateToNothing() {
                assertEquals(Tessellation.EMPTY, tessellator.tessellate(
                                                TownClaim.of(MapWorld.EARTH, "Nowhere", null, 0xFF0000, 0x0000FF, List.of())));
        }
}
