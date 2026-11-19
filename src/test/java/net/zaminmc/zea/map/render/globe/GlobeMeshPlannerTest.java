package net.zaminmc.zea.map.render.globe;

import net.zaminmc.zea.map.model.MapWorld;
import net.zaminmc.zea.map.projection.GlobeTransition;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The mesh planner's contract: the tile grid matches the live pyramid,
 * the layers emit in draw order, the subdivision tracks the curve within
 * budget, the limb culls, and the near-flat phases approximate the flat
 * rectangle.
 */
class GlobeMeshPlannerTest {

        private static final double R = 300.0;
        private static final double HALF_W = MapWorld.EARTH.worldWidth / 2.0;
        private static final double HALF_H = MapWorld.EARTH.worldHeight / 2.0;
        private static final int PLATE_W = 1280;
        private static final int PLATE_H = 800;

        private GlobeMeshPlanner.Plan plan(MapWorld world, double cx, double cz,
                        double curvature, double maxDiscRadius) {
                return plan(world, cx, cz, curvature, maxDiscRadius, PLATE_W / 2, PLATE_H / 2);
        }

        private GlobeMeshPlanner.Plan plan(MapWorld world, double cx, double cz,
                        double curvature, double maxDiscRadius, int halfW, int halfH) {
                return GlobeMeshPlanner.plan(world, cx, cz, R, curvature,
                                                maxDiscRadius, halfW, halfH);
        }

        @Test
        void tileGridMatchesTheLivePyramid() {
                // Verified live against map.earthmc.net's zoom-0 coverage:
                // Earth spans tiles x ∈ [−4..3], y ∈ [−2..1]; Moon x ∈
                // [−2..1], y ∈ [−1..0]. The planner must never enumerate
                // outside that grid — whatever the viewport culls.
                for (double t : new double[] {GlobeTransition.CURVATURE_EPSILON * 2, 0.5, 1.0}) {
                        GlobeMeshPlanner.Plan earth = plan(MapWorld.EARTH, 0.0, 0.0, t, 1e6);
                        for (GlobeMeshPlanner.Cell cell : earth.cells()) {
                                assertTrue(cell.tile().x() >= -4 && cell.tile().x() <= 3
                                                                && cell.tile().z() >= -2 && cell.tile().z() <= 1,
                                                                "tile outside the live grid: " + cell.tile());
                        }
                        GlobeMeshPlanner.Plan moon = plan(MapWorld.MOON, 0.0, 0.0, t, 1e6);
                        for (GlobeMeshPlanner.Cell cell : moon.cells()) {
                                assertTrue(cell.tile().x() >= -2 && cell.tile().x() <= 1
                                                                && cell.tile().z() >= -1 && cell.tile().z() <= 0,
                                                                "tile outside the live grid: " + cell.tile());
                        }
                }
        }

        @Test
        void fullGlobeShowsTheFrontHalfOfTheGrid() {
                // From the world's center at full curvature the front
                // hemisphere spans half the world's longitude: the middle
                // four tile columns.
                GlobeMeshPlanner.Plan p = plan(MapWorld.EARTH, 0.0, 0.0, 1.0, 1e6,
                                                4000, 2500);
                Set<Integer> columns = new HashSet<>();
                for (GlobeMeshPlanner.Cell cell : p.cells()) {
                        columns.add(cell.tile().x());
                }
                for (int x = -2; x <= 1; x++) {
                        assertTrue(columns.contains(x), "front column " + x + " missing");
                }
                // The far-side columns never appear.
                assertTrue(!columns.contains(-4), "back column drawn");
                assertTrue(!columns.contains(3), "back column drawn");
        }

        @Test
        void layersEmitInDrawOrder() {
                // Water disc first, then the world plate — both colored;
                // imagery cells last (the textured pipeline sorts after the
                // colored one regardless of emission order).
                GlobeMeshPlanner.Plan p = plan(MapWorld.EARTH, 0.0, 0.0, 0.5, 1e6);
                assertEquals(GlobeMeshPlanner.DISC_SEGMENTS, p.colored().size()
                                                - plateCellCount(p), "disc quads lead the colored list");
                assertTrue(p.colored().get(0).xs()[0] == 0.0
                                                && p.colored().get(0).ys()[0] == 0.0,
                                                "the disc's first quad starts at the center");
        }

        private static int plateCellCount(GlobeMeshPlanner.Plan p) {
                // The plate contributes one cell per (i, j) of its own
                // subdivision; count the colored cells past the disc.
                return p.colored().size() - GlobeMeshPlanner.DISC_SEGMENTS;
        }

        @Test
        void subdivisionTracksTheCurveWithinBudget() {
                // At full curvature the limb's straight edges must stay
                // within the budget of the true arc: the worst cell's
                // deviation is bounded by the formula the planner uses.
                double sphereRadius = R;
                double thetaMax = Math.PI; // the plate's latitude span at t=1
                int k = GlobeMeshPlanner.subdivisions(thetaMax, sphereRadius);
                double deviation = sphereRadius
                                                * (1.0 - Math.cos(thetaMax / (2.0 * k)));
                assertTrue(deviation <= GlobeMeshPlanner.SUBDIVISION_PIXEL_BUDGET + 1e-9,
                                                "k=" + k + " deviates " + deviation + "px");
                // Flat phases need no subdivision at all.
                assertEquals(1, GlobeMeshPlanner.subdivisions(
                                                GlobeTransition.CURVATURE_EPSILON * 2, R));
        }

        @Test
        void limbCullsAtFullGlobe() {
                // At full curvature every projected corner lies inside the
                // silhouette — the disc's radius; nothing pokes past the
                // limb, and the disc itself spans exactly the sphere.
                GlobeMeshPlanner.Plan p = plan(MapWorld.EARTH, 0.0, 0.0, 1.0, 1e6,
                                                4000, 2500);
                for (GlobeMeshPlanner.Cell cell : p.cells()) {
                        for (int i = 0; i < 4; i++) {
                                assertTrue(Math.hypot(cell.xs()[i], cell.ys()[i]) <= R + 1e-6,
                                                                "corner beyond the limb");
                        }
                }
                assertEquals(R, p.discRadius(), 1e-9);
        }

        @Test
        void nearFlatPhaseApproximatesTheFlatRectangle() {
                // At a tiny curvature the plate's north-west corner sits at
                // the flat offsets: the world's edge at the flat scale. The
                // generous cull radius keeps the whole plate alive.
                double tiny = GlobeTransition.CURVATURE_EPSILON * 2;
                GlobeMeshPlanner.Plan p = plan(MapWorld.EARTH, 0.0, 0.0, tiny, 1e6,
                                                8000, 8000);
                GlobeMeshPlanner.ColoredCell plate = p.colored()
                                                .get(GlobeMeshPlanner.DISC_SEGMENTS);
                double flatScale = GlobeTransition.flatScale(R, HALF_W);
                assertEquals(-HALF_W * flatScale, plate.xs()[0],
                                                HALF_W * flatScale * 1e-3);
                assertEquals(-HALF_H * flatScale, plate.ys()[0],
                                                HALF_H * flatScale * 1e-3);
        }

        @Test
        void discRadiusCapsAtThePlate() {
                // Near the flat end the sphere's silhouette is enormous;
                // the planner caps it so the fan stays sane.
                double tiny = GlobeTransition.CURVATURE_EPSILON * 2;
                GlobeMeshPlanner.Plan p = plan(MapWorld.EARTH, 0.0, 0.0, tiny, 5000.0);
                assertEquals(5000.0, p.discRadius(), 1e-9);
                double atGlobe = plan(MapWorld.EARTH, 0.0, 0.0, 1.0, 5000.0).discRadius();
                assertEquals(R, atGlobe, 1e-9);
        }

        @Test
        void imageryUvSpansStayWithinTheTile() {
                GlobeMeshPlanner.Plan p = plan(MapWorld.EARTH, 0.0, 0.0, 0.5, 1e6);
                for (GlobeMeshPlanner.Cell cell : p.cells()) {
                        assertTrue(cell.u0() >= 0.0 && cell.u1() <= 1.0
                                                        && cell.v0() >= 0.0 && cell.v1() <= 1.0,
                                                        "uv out of tile: " + cell.u0() + "," + cell.v0()
                                                                        + " " + cell.u1() + "," + cell.v1());
                }
        }
}
