package net.zaminmc.zea.map.render.overlay;

import net.zaminmc.zea.map.model.MapCamera;
import net.zaminmc.zea.map.model.MapWorld;
import net.zaminmc.zea.map.model.TownClaim;
import net.zaminmc.zea.map.projection.MapProjection;
import net.zaminmc.zea.map.render.overlay.ClaimOverlay.Rect;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The claim draw-list planner: map-frame projection of fill runs and
 * edges, bounding-box culling, view clipping, the constant-pixel outline
 * policy and its far-zoom decimation floor, and the layer switches.
 */
class ClaimOverlayTest {

        private final ClaimOverlay overlay = new ClaimOverlay();

        private static TownClaim town(int minX, int minZ, int maxX, int maxZ) {
                return TownClaim.of(MapWorld.EARTH, "Testville", null, 0xFF0000, 0x0000FF,
                                                List.<int[][]>of(ring(minX, minZ, maxX, minZ, maxX, maxZ, minX, maxZ)));
        }

        private static int[][] ring(int... xz) {
                int[][] points = new int[xz.length / 2][];
                for (int i = 0; i < points.length; i++) {
                        points[i] = new int[] { xz[i * 2], xz[i * 2 + 1] };
                }
                return points;
        }

        private static MapProjection view(double camX, double camZ, double ppb, int w, int h) {
                return MapProjection.northUp(new MapCamera(MapWorld.EARTH, camX, camZ, ppb), w, h);
        }

        @Test
        void fillRunsProjectToCentreRelativeMapFrame() {
                MapProjection projection = view(100, 100, 0.5, 400, 300);
                List<Rect> rects = overlay.plan(List.of(town(0, 0, 256, 256)), projection,
                                                true, false, 128, 0, 2.0);
                assertEquals(1, rects.size());
                Rect rect = rects.get(0);
                assertEquals(-50.0, rect.x(), 1e-9, "(0 - 100) blocks at ppb 0.5");
                assertEquals(-50.0, rect.y(), 1e-9);
                assertEquals(128.0, rect.width(), 1e-9, "256 blocks at ppb 0.5");
                assertEquals(128.0, rect.height(), 1e-9);
                assertEquals(0x800000FF, rect.argb(), "fill colour carries the requested alpha");
        }

        @Test
        void fillRunsClipToThePaddedView() {
                MapProjection projection = view(0, 0, 1.0, 400, 300);
                List<Rect> rects = overlay.plan(List.of(town(-10_000, -10_000, 10_000, 10_000)), projection,
                                                true, false, 128, 0, 2.0);
                assertEquals(1, rects.size());
                Rect rect = rects.get(0);
                // The view is [-200,200] x [-150,150]; the 16-block pad widens
                // every edge, and the town's run clips to exactly that.
                assertEquals(-216.0, rect.x(), 1e-9);
                assertEquals(-166.0, rect.y(), 1e-9);
                assertEquals(432.0, rect.width(), 1e-9);
                assertEquals(332.0, rect.height(), 1e-9);
        }

        @Test
        void townsBeyondThePaddedViewAreCulled() {
                MapProjection projection = view(0, 0, 1.0, 400, 300);
                List<TownClaim> claims = List.of(
                                                town(1000, 0, 1100, 100),
                                                town(-5000, -5000, -4900, -4900),
                                                town(0, 1000, 100, 1100));
                assertTrue(overlay.plan(claims, projection, true, true, 128, 200, 2.0).isEmpty(),
                                                "no bbox reaches the padded view");
        }

        @Test
        void mixedListsDrawOnlyWhatIntersects() {
                MapProjection projection = view(0, 0, 1.0, 400, 300);
                List<TownClaim> claims = List.of(
                                                town(-5000, -5000, -4900, -4900),
                                                town(0, 0, 100, 100));
                List<Rect> rects = overlay.plan(claims, projection, true, false, 128, 0, 2.0);
                assertEquals(1, rects.size());
                assertEquals(0.0, rects.get(0).x(), 1e-9);
                assertEquals(0.0, rects.get(0).y(), 1e-9);
        }

        @Test
        void outlinesAreConstantPixelsWideAndCentredOnEdges() {
                MapProjection projection = view(100, 100, 1.0, 400, 300);
                List<Rect> rects = overlay.plan(List.of(town(0, 0, 256, 256)), projection,
                                                false, true, 0, 200, 2.0);
                assertEquals(4, rects.size());
                // North and south edges: full width, thickness centred on the line.
                assertTrue(rects.contains(new Rect(-100.0, -101.0, 256.0, 2.0, 0xC8FF0000)), "north edge: " + rects);
                assertTrue(rects.contains(new Rect(-100.0, 155.0, 256.0, 2.0, 0xC8FF0000)), "south edge: " + rects);
                // West and east edges: thickness wide, full height.
                assertTrue(rects.contains(new Rect(-101.0, -100.0, 2.0, 256.0, 0xC8FF0000)), "west edge: " + rects);
                assertTrue(rects.contains(new Rect(155.0, -100.0, 2.0, 256.0, 0xC8FF0000)), "east edge: " + rects);
        }

        @Test
        void outlinesDropBelowTheBlockPixelFloor() {
                // ppb 0.1 < 2/16: a border under a fifth of a screen pixel per
                // block is noise — fills stay, outlines go.
                MapProjection coarse = view(0, 0, 0.1, 400, 300);
                List<Rect> rects = overlay.plan(List.of(town(0, 0, 256, 256)), coarse,
                                                true, true, 128, 200, 2.0);
                assertEquals(1, rects.size(), "only the fill survives: " + rects);

                MapProjection atFloor = view(0, 0, ClaimOverlay.OUTLINE_MIN_BLOCK_PIXELS, 400, 300);
                rects = overlay.plan(List.of(town(0, 0, 256, 256)), atFloor,
                                                true, true, 128, 200, 2.0);
                assertEquals(5, rects.size(), "at the floor exactly, outlines stay");
        }

        @Test
        void layerSwitchesSuppressEachLayer() {
                MapProjection projection = view(128, 128, 1.0, 400, 300);
                List<TownClaim> claims = List.of(town(0, 0, 256, 256));
                assertEquals(5, overlay.plan(claims, projection, true, true, 128, 200, 2.0).size());
                assertEquals(4, overlay.plan(claims, projection, false, true, 0, 200, 2.0).size(),
                                                "fill alpha 0 suppresses fills");
                assertEquals(1, overlay.plan(claims, projection, true, false, 128, 0, 2.0).size(),
                                                "outline alpha 0 suppresses outlines");
                assertTrue(overlay.plan(claims, projection, true, true, 0, 0, 2.0).isEmpty(),
                                                "nothing visible means nothing planned");
        }
}
