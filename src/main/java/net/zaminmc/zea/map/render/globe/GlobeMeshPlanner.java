package net.zaminmc.zea.map.render.globe;

import net.zaminmc.zea.map.model.MapWorld;
import net.zaminmc.zea.map.model.TileId;
import net.zaminmc.zea.map.projection.GlobeTransition;

import java.util.ArrayList;
import java.util.List;

/**
 * Plans the curved surface's quads: the water disc, the world plate and
 * the imagery cells, all in screen offsets from the globe's center.
 *
 * <p>The mesh reuses the flat pipeline's data unchanged. The transition
 * holds the map's apparent scale at the fit point, so the zoom-0 pyramid
 * layer (the whole world in a handful of 16,384-block tiles) supplies
 * every curved phase — no second imagery source, no second cache. Each
 * tile's rectangle subdivides in geographic space until its straight
 * screen edges track the curved surface within a pixel budget; cells
 * whose corners all fall past the limb cull, and at full curvature the
 * world's borders wrap to the far side and disappear with it.
 *
 * <p>Layer order (the renderer draws them in this order; same-pipeline
 * elements keep insertion order, and the imagery's textured pipeline
 * sorts after the colored one): water disc, world plate, imagery cells.
 * The plate is the undiscovered treatment — inside the world's bounds
 * the surface never reads as water just because a tile has not arrived.
 *
 * <p>Pure type: no Minecraft classes, pinned by tests. The screen
 * offsets are relative to the globe's center; the renderer adds the
 * plate's center.
 */
public final class GlobeMeshPlanner {

        /** Straight-edge deviation budget, in GUI pixels, per mesh cell. */
        public static final double SUBDIVISION_PIXEL_BUDGET = 1.0;

        /** The finest subdivision: 32 × 32 cells per tile keeps the limb round. */
        public static final int MAX_SUBDIVISIONS = 32;

        /** Quads in the water disc's fan; 96 keeps the rim smooth past the plate. */
        public static final int DISC_SEGMENTS = 96;

        /** Slack beyond the plate's half diagonal before a cell is culled off-screen. */
        public static final double CULL_SLACK_PX = 48.0;

        private GlobeMeshPlanner() {
        }

        /** One imagery cell: a tile's texture region drawn across four screen corners. */
        public record Cell(TileId tile, double u0, double v0, double u1, double v1,
                        double[] xs, double[] ys) {
                /** Corner order matches the blit contract: TL, BL, BR, TR. */
        }

        /** One colored quad: the disc's water and the plate's undiscovered treatment. */
        public record ColoredCell(double[] xs, double[] ys, int argb) {
        }

        /** The planned frame: colored layers, imagery cells, disc geometry. */
        public record Plan(List<ColoredCell> colored, List<Cell> cells,
                        double discRadius, double curvature, double sphereRadius) {
        }

        /**
         * The curved frame's geometry. {@code radius} is the globe's
         * apparent radius at full curvature (the viewport-derived radius
         * times the recede scale); {@code curvature} the raw phase;
         * {@code maxDiscRadius} caps the water disc's screen radius so the
         * near-flat phases do not emit geometry thousands of pixels wide.
         */
        public static Plan plan(MapWorld world, double centerX, double centerZ,
                        double radius, double curvature, double maxDiscRadius,
                        int plateHalfWidthPx, int plateHalfHeightPx) {
                double t = Math.clamp(curvature, GlobeTransition.CURVATURE_EPSILON, 1.0);
                double sphereRadius = radius / t;
                double discRadius = Math.min(sphereRadius, maxDiscRadius);
                double halfW = world.worldWidth / 2.0;
                double halfH = world.worldHeight / 2.0;
                double cullRadius = Math.hypot(plateHalfWidthPx, plateHalfHeightPx) + CULL_SLACK_PX;

                List<ColoredCell> colored = new ArrayList<>();
                List<Cell> cells = new ArrayList<>();

                // The water disc: degenerate fan quads from the center —
                // quads only, because the GUI pipelines' topology is quads.
                for (int i = 0; i < DISC_SEGMENTS; i++) {
                        double a0 = 2.0 * Math.PI * i / DISC_SEGMENTS;
                        double a1 = 2.0 * Math.PI * (i + 1) / DISC_SEGMENTS;
                        double[] xs = {0.0,
                                        discRadius * Math.sin(a0),
                                        discRadius * Math.sin(a1),
                                        0.0};
                        double[] ys = {0.0,
                                        -discRadius * Math.cos(a0),
                                        -discRadius * Math.cos(a1),
                                        0.0};
                        colored.add(new ColoredCell(xs, ys, 0));
                }

                // The world plate: the whole map extent subdivided on the
                // curved surface — the undiscovered treatment under the
                // imagery.
                subdivideRect(-halfW, -halfH, halfW, halfH, world, centerX, centerZ,
                                                radius, t, sphereRadius, cullRadius,
                                                (xs, ys) -> colored.add(new ColoredCell(xs, ys, 0)));

                // The imagery: zoom-0 tiles intersecting the world's extent,
                // clipped to it (the pyramid's margin tiles hang past the
                // border and the sphere has no longitude beyond it).
                int span = net.zaminmc.zea.map.projection.MapProjection.tileWorldSpan(0);
                int tileMinX = floorDiv(-halfW, span);
                int tileMaxX = floorDiv(halfW - 1e-9, span);
                int tileMinZ = floorDiv(-halfH, span);
                int tileMaxZ = floorDiv(halfH - 1e-9, span);
                for (int tz = tileMinZ; tz <= tileMaxZ; tz++) {
                        for (int tx = tileMinX; tx <= tileMaxX; tx++) {
                                double x0 = Math.max(-halfW, (double) tx * span);
                                double x1 = Math.min(halfW, (double) (tx + 1) * span);
                                double z0 = Math.max(-halfH, (double) tz * span);
                                double z1 = Math.min(halfH, (double) (tz + 1) * span);
                                TileId tile = new TileId(world, TileId.TileSource.SQUAREMAP, 0, tx, tz);
                                double u0 = (x0 - (double) tx * span) / span;
                                double v0 = (z0 - (double) tz * span) / span;
                                double u1 = (x1 - (double) tx * span) / span;
                                double v1 = (z1 - (double) tz * span) / span;
                                subdivideRect(x0, z0, x1, z1, world, centerX, centerZ,
                                                                radius, t, sphereRadius, cullRadius,
                                                                (xs, ys) -> cells.add(new Cell(tile,
                                                                                u0, v0, u1, v1, xs, ys)));
                        }
                }
                return new Plan(colored, cells, discRadius, t, sphereRadius);
        }

        /**
         * Emits {@code k × k} cells for one world rectangle, {@code k}
         * chosen so a cell's straight screen edges stay within the
         * subdivision budget of the true curved surface.
         */
        private static void subdivideRect(double x0, double z0, double x1, double z1,
                        MapWorld world, double centerX, double centerZ,
                        double radius, double t, double sphereRadius, double cullRadius,
                        CellSink sink) {
                double halfW = world.worldWidth / 2.0;
                double halfH = world.worldHeight / 2.0;
                double thetaX = Math.PI * t * (x1 - x0) / halfW;
                double thetaZ = Math.PI / 2.0 * t * (z1 - z0) / halfH;
                double thetaMax = Math.max(thetaX, thetaZ);
                int k = subdivisions(thetaMax, sphereRadius);
                double[] xs = new double[4];
                double[] ys = new double[4];
                for (int j = 0; j < k; j++) {
                        double cellZ0 = z0 + (z1 - z0) * j / k;
                        double cellZ1 = z0 + (z1 - z0) * (j + 1) / k;
                        for (int i = 0; i < k; i++) {
                                double cellX0 = x0 + (x1 - x0) * i / k;
                                double cellX1 = x0 + (x1 - x0) * (i + 1) / k;
                                GlobeTransition.SpherePoint tl = GlobeTransition.project(
                                                                cellX0, cellZ0, centerX, centerZ,
                                                                t, radius, halfW, halfH);
                                GlobeTransition.SpherePoint bl = GlobeTransition.project(
                                                                cellX0, cellZ1, centerX, centerZ,
                                                                t, radius, halfW, halfH);
                                GlobeTransition.SpherePoint br = GlobeTransition.project(
                                                                cellX1, cellZ1, centerX, centerZ,
                                                                t, radius, halfW, halfH);
                                GlobeTransition.SpherePoint tr = GlobeTransition.project(
                                                                cellX1, cellZ0, centerX, centerZ,
                                                                t, radius, halfW, halfH);
                                // A cell straddling the limb keeps its front
                                // corners; a fully back-side cell culls.
                                if (tl.z() < 0.0 && bl.z() < 0.0 && br.z() < 0.0 && tr.z() < 0.0) {
                                        continue;
                                }
                                xs[0] = tl.x();
                                ys[0] = tl.y();
                                xs[1] = bl.x();
                                ys[1] = bl.y();
                                xs[2] = br.x();
                                ys[2] = br.y();
                                xs[3] = tr.x();
                                ys[3] = tr.y();
                                if (offScreen(xs, ys, cullRadius)) {
                                        continue;
                                }
                                sink.cell(xs.clone(), ys.clone());
                        }
                }
        }

        private static boolean offScreen(double[] xs, double[] ys, double cullRadius) {
                for (int i = 0; i < 4; i++) {
                        if (Math.hypot(xs[i], ys[i]) < cullRadius) {
                                return false;
                        }
                }
                return true;
        }

        /**
         * Cells per edge: the smallest {@code k} whose cells' straight
         * edges deviate from the arc by at most one pixel — the exact
         * sagitta {@code r·(1−cos(θ/(2k)))}, solved for {@code k}.
         */
        static int subdivisions(double thetaMax, double sphereRadius) {
                if (!(thetaMax > 0) || !(sphereRadius > SUBDIVISION_PIXEL_BUDGET)) {
                        return 1;
                }
                double ratio = 1.0 - SUBDIVISION_PIXEL_BUDGET / sphereRadius;
                double halfThetaCell = Math.acos(Math.clamp(ratio, -1.0, 1.0));
                if (halfThetaCell <= 0) {
                        return MAX_SUBDIVISIONS;
                }
                return (int) Math.min(MAX_SUBDIVISIONS,
                                        Math.max(1, Math.ceil(thetaMax / (2.0 * halfThetaCell))));
        }

        private static int floorDiv(double value, int span) {
                return (int) Math.floorDiv((long) Math.floor(value), span);
        }

        /** Receives one cell's four screen corners, TL, BL, BR, TR. */
        private interface CellSink {
                void cell(double[] xs, double[] ys);
        }
}
