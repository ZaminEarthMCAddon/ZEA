package net.zaminmc.zea.map.projection;

import net.zaminmc.zea.map.model.MapWorld;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The projection family's contract: flat at curvature 0, a closed sphere
 * at curvature 1, continuous between, invertible on the front hemisphere,
 * and coupled to the camera by pure functions.
 */
class GlobeTransitionTest {

        private static final double R = 300.0;
        private static final double HALF_W = MapWorld.EARTH.worldWidth / 2.0;
        private static final double HALF_H = MapWorld.EARTH.worldHeight / 2.0;
        private static final double FIT = GlobeTransition.fitPixelsPerBlock(MapWorld.EARTH, 1280);

        // ------------------------------------------------------------------
        // Flat end
        // ------------------------------------------------------------------

        @Test
        void flatCurvatureIsTheLinearMap() {
                // As curvature → 0 the family must tend to the exact flat
                // offset: x · π·R / halfWidth — the plane the flat path
                // already renders.
                double scale = GlobeTransition.flatScale(R, HALF_W);
                double tiny = GlobeTransition.CURVATURE_EPSILON * 2;
                GlobeTransition.SpherePoint p = GlobeTransition.project(
                                                1000.0, -2000.0, tiny, R, HALF_W, HALF_H);
                assertEquals(1000.0 * scale, p.x(), 1e-6);
                assertEquals(-2000.0 * scale, p.y(), 1e-6);
        }

        @Test
        void northIsScreenUpAtTheGlobe() {
                // Minecraft north is −z; the north pole must land at the
                // top of the screen (negative y).
                GlobeTransition.SpherePoint north = GlobeTransition.project(
                                                0.0, -HALF_H, 1.0, R, HALF_W, HALF_H);
                assertEquals(-R, north.y(), 1e-6);
                assertEquals(0.0, north.x(), 1e-6);
        }

        // ------------------------------------------------------------------
        // Globe end: closure and culling
        // ------------------------------------------------------------------

        @Test
        void antimeridianSealsAtFullGlobe() {
                // Just inside the west and east borders at the equator: at
                // full curvature both hug the antimeridian — their x
                // offsets converge on the same meridian line and both sit
                // on the far side.
                double inset = HALF_W * 1e-6;
                GlobeTransition.SpherePoint east = GlobeTransition.project(
                                                HALF_W - inset, 0.0, 1.0, R, HALF_W, HALF_H);
                GlobeTransition.SpherePoint west = GlobeTransition.project(
                                                -HALF_W + inset, 0.0, 1.0, R, HALF_W, HALF_H);
                assertEquals(0.0, east.x(), R * 1e-4);
                assertEquals(0.0, west.x(), R * 1e-4);
                // Both borders are on the far hemisphere: culled.
                assertTrue(GlobeTransition.frontFacing(east.z()) == false);
                assertTrue(GlobeTransition.frontFacing(west.z()) == false);
        }

        @Test
        void centerFacesTheViewerAtFullGlobe() {
                GlobeTransition.SpherePoint center = GlobeTransition.project(
                                                0.0, 0.0, 1.0, R, HALF_W, HALF_H);
                assertEquals(R, center.z(), 1e-9);
                assertTrue(GlobeTransition.frontFacing(center.z()));
        }

        @Test
        void polesCloseAtFullGlobe() {
                GlobeTransition.SpherePoint south = GlobeTransition.project(
                                                0.0, HALF_H, 1.0, R, HALF_W, HALF_H);
                assertEquals(R, south.y(), 1e-6);
        }

        // ------------------------------------------------------------------
        // Continuity
        // ------------------------------------------------------------------

        @Test
        void surfaceCurvesContinuouslyBetweenTheEnds() {
                // March the curvature across the whole family and require
                // each step's screen motion to be small relative to the
                // radius — no jumps anywhere on the path.
                double x = HALF_W * 0.37;
                double z = HALF_H * -0.21;
                double previousX = GlobeTransition.project(x, z, GlobeTransition.CURVATURE_EPSILON,
                                                R, HALF_W, HALF_H).x();
                double previousY = GlobeTransition.project(x, z, GlobeTransition.CURVATURE_EPSILON,
                                                R, HALF_W, HALF_H).y();
                int steps = 512;
                for (int i = 1; i <= steps; i++) {
                        double t = (double) i / steps;
                        GlobeTransition.SpherePoint p = GlobeTransition.project(x, z, t, R, HALF_W, HALF_H);
                        double dx = Math.abs(p.x() - previousX);
                        double dy = Math.abs(p.y() - previousY);
                        assertTrue(dx < R / 200.0 && dy < R / 200.0,
                                                        "jump at t=" + t + ": dx=" + dx + " dy=" + dy);
                        previousX = p.x();
                        previousY = p.y();
                }
        }

        // ------------------------------------------------------------------
        // Inverse
        // ------------------------------------------------------------------

        @Test
        void unprojectRoundTripsTheFrontHemisphere() {
                for (double t : new double[] {0.1, 0.5, 1.0}) {
                        double wx = HALF_W * 0.4;
                        double wz = HALF_H * -0.6;
                        GlobeTransition.SpherePoint p = GlobeTransition.project(
                                                        wx, wz, t, R, HALF_W, HALF_H);
                        GlobeTransition.SphereAngle back = GlobeTransition.unproject(
                                                        p.x(), p.y(), t, R, HALF_W, HALF_H);
                        assertEquals(wx, back.worldX(), HALF_W * 1e-9);
                        assertEquals(wz, back.worldZ(), HALF_H * 1e-9);
                }
        }

        @Test
        void unprojectRefusesBeyondTheLimb() {
                assertNull(GlobeTransition.unproject(R * 2.0, 0.0, 1.0, R, HALF_W, HALF_H));
        }

        // ------------------------------------------------------------------
        // Camera coupling
        // ------------------------------------------------------------------

        @Test
        void curvatureIsZeroAtAndAboveTheFitScale() {
                assertEquals(0.0, GlobeTransition.curvatureOf(FIT, FIT), 0.0);
                assertEquals(0.0, GlobeTransition.curvatureOf(FIT * 8.0, FIT), 0.0);
        }

        @Test
        void curvatureClimbsMonotonicallyToFull() {
                double previous = 0.0;
                for (int i = 1; i <= 60; i++) {
                        double ppb = FIT / Math.pow(2.0, i / 20.0);
                        double t = GlobeTransition.curvatureOf(ppb, FIT);
                        assertTrue(t >= previous, "curvature fell at ppb=" + ppb);
                        previous = t;
                }
                // PHASE_SPAN = 3 e-folds ≈ 4.33 doublings.
                assertEquals(1.0, GlobeTransition.curvatureOf(FIT / Math.pow(2.0, 4.33), FIT), 1e-9);
        }

        @Test
        void globeRecedesPastFullCurvature() {
                double atFull = GlobeTransition.curvatureOf(FIT / Math.pow(2.0, 4.33), FIT);
                assertEquals(1.0, atFull, 1e-9);
                // During the transition the apparent scale stays 1.
                assertEquals(1.0, GlobeTransition.globeScaleOf(FIT / 2.0, FIT), 0.0);
                // Four e-folds past the fit point: three complete the
                // transition, the fourth recedes the globe by e.
                double shrunk = GlobeTransition.globeScaleOf(FIT / Math.exp(4.0), FIT);
                assertEquals(Math.exp(-1.0), shrunk, 1e-12);
        }

        @Test
        void fitScaleIsTheFramedWidthOverViewport() {
                assertEquals(GlobeTransition.FIT_WIDTH_FRACTION * 1280.0 / MapWorld.EARTH.worldWidth,
                                                FIT, 1e-12);
                double moonFit = GlobeTransition.fitPixelsPerBlock(MapWorld.MOON, 1280);
                assertEquals(GlobeTransition.FIT_WIDTH_FRACTION * 1280.0 / MapWorld.MOON.worldWidth,
                                                moonFit, 1e-12);
        }

        @Test
        void flatHandoffMatchesTheFitScaleExactly() {
                // The curved family at curvature → 0 implies the flat scale
                // π·R_globe/halfWidth — it must equal the fit ppb, or the
                // flat-to-curved handoff jumps.
                double radius = GlobeTransition.globeRadius(1280, 800);
                double implied = GlobeTransition.flatScale(radius, HALF_W);
                assertEquals(FIT, implied, 1e-9);
        }

        // ------------------------------------------------------------------
        // The Moon closes on its own extent
        // ------------------------------------------------------------------

        @Test
        void moonGlobeClosesOnItsOwnBounds() {
                double halfW = MapWorld.MOON.worldWidth / 2.0;
                double halfH = MapWorld.MOON.worldHeight / 2.0;
                GlobeTransition.SpherePoint north = GlobeTransition.project(
                                                0.0, -halfH, 1.0, R, halfW, halfH);
                assertEquals(-R, north.y(), 1e-6);
                GlobeTransition.SpherePoint east = GlobeTransition.project(
                                                halfW, 0.0, 1.0, R, halfW, halfH);
                assertTrue(!GlobeTransition.frontFacing(east.z()));
        }
}
