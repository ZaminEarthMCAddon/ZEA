package net.zaminmc.zea.map.projection;

import net.zaminmc.zea.map.model.MapWorld;

/**
 * The flat-to-globe projection family: one continuous surface whose
 * curvature grows from the flat block grid (curvature 0) to the closed
 * sphere (curvature 1), driven only by the camera's zoom — never by a
 * mode switch.
 *
 * <h2>The surface family</h2>
 *
 * <p>A world point {@code (x, z)} has fixed geographic coordinates,
 * defined once per world from its verified extent (Minecraft north is
 * −z, so latitude is positive northward):
 *
 * <pre>
 *   lon = (x / halfWidth)  · π      — ±π at the west/east border
 *   lat = (−z / halfHeight) · π/2   — +π/2 at the north border
 * </pre>
 *
 * <p>The surface wraps that geographic rectangle onto a sphere whose
 * angular span grows with the curvature while its radius shrinks. At
 * curvature {@code t} the longitude half-span is {@code Ω(t) = π·t}, the
 * latitude half-span is {@code Φ(t) = π/2·t}, and the surface radius is
 * {@code R(t) = R_globe / t} in GUI pixels. A point's screen offset from
 * the globe's center is the orthographic projection of its sphere point
 * (north pole up, longitude 0 toward the viewer):
 *
 * <pre>
 *   X = R·cos(lat)·sin(lon)      sx = X
 *   Y = R·sin(lat)               sy = −Y
 *   Z = R·cos(lat)·cos(lon)      (depth, for limb culling)
 * </pre>
 *
 * <p>Continuity is exact, not fitted. As {@code t → 0}, {@code R → ∞}
 * while the angular span vanishes at the same rate, and the offset tends
 * to the linear flat map
 * {@code sx ≈ x · π·R_globe / halfWidth} — a plane. At {@code t = 1} the
 * spans close the full sphere: the map's west/east borders meet at the
 * antimeridian and the borders lie on the far side, culled by depth.
 * Between the two the surface curves like a page curling into a ball —
 * EarthMC's own zoom-out behaves the same way.
 *
 * <h2>Scale phases</h2>
 *
 * <p>The camera stays the single zoom scalar ({@code pixelsPerBlock});
 * the curvature is derived from it, so the transition rides the existing
 * wheel zoom:
 *
 * <ol>
 * <li><b>Flat</b> — {@code ppb ≥ fit}: the ordinary flat map, exact and
 *     unchanged; the whole world fits at {@code ppb = fit}.</li>
 * <li><b>Transition</b> — zooming out below the fit point bends the
 *     surface: {@code t} climbs from 0 to 1 over {@link #PHASE_SPAN}
 *     e-foldings of zoom-out. The apparent size stays near-constant while
 *     the map curls.</li>
 * <li><b>Globe</b> — {@code t = 1}: further zoom-out shrinks the globe's
 *     apparent radius by the same e-fold factor. The globe is a real
 *     sphere: its far side culls, its poles close, its antimeridian
 *     seals.</li>
 * </ol>
 *
 * <p>Reversing the zoom retraces the same phases with no hysteresis
 * needed: every value here is a pure function of {@code ppb}, so the
 * surface cannot oscillate at the threshold.
 *
 * <p>Pure type: no Minecraft classes, everything pinned by tests. The
 * clamped curvature used by the math is deliberately distinct from the
 * raw phase value callers use for decisions (which renderer path, limb
 * culling) — a raw phase of 0 means "definitely flat" even though the
 * math path clamps its own copy to a floor to keep the radius finite.
 */
public final class GlobeTransition {

        /** The math floor for the curvature: below this the surface is flat for any purpose. */
        public static final double CURVATURE_EPSILON = 1.0 / 4096.0;

        /**
         * E-foldings of zoom-out between the fit point and full curvature.
         * Three keeps the curl readable: each e-fold roughly halves the
         * map, so the sphere becomes recognizable while the flat map is
         * still too large to read as a map.
         */
        public static final double PHASE_SPAN = 3.0;

        /**
         * The flat map's width at the fit point, as a fraction of the
         * viewport: a hair of margin so the border plate stays visible.
         * Everything else derives from it — see {@link #globeRadius}.
         */
        public static final double FIT_WIDTH_FRACTION = 0.94;

        private GlobeTransition() {
        }

        /**
         * The whole-map fit scale: the ppb at which the world's width
         * fills {@link #FIT_WIDTH_FRACTION} of the viewport. This is also
         * the scale the curved family implies as curvature → 0 — the two
         * paths hand off with no scale jump, exactly.
         */
        public static double fitPixelsPerBlock(MapWorld world, int viewportWidth) {
                return Math.max(1e-9,
                                                FIT_WIDTH_FRACTION * viewportWidth / (double) world.worldWidth);
        }

        /**
         * The curvature for a camera scale: 0 flat, 1 full globe, the
         * climb in between logarithmic in the zoom (equal wheel effort per
         * phase, no jump at either end).
         */
        public static double curvatureOf(double pixelsPerBlock, double fitPixelsPerBlock) {
                if (!(pixelsPerBlock > 0) || !(fitPixelsPerBlock > 0)
                                                || Double.isNaN(pixelsPerBlock) || Double.isNaN(fitPixelsPerBlock)
                                                || pixelsPerBlock >= fitPixelsPerBlock) {
                        return 0.0;
                }
                double eFolds = Math.log(fitPixelsPerBlock / pixelsPerBlock);
                return Math.clamp(eFolds / PHASE_SPAN, 0.0, 1.0);
        }

        /**
         * The apparent-scale factor past full curvature: 1 while the
         * transition runs, shrinking smoothly as the camera zooms out
         * beyond it — the globe recedes instead of freezing.
         */
        public static double globeScaleOf(double pixelsPerBlock, double fitPixelsPerBlock) {
                if (!(pixelsPerBlock > 0) || !(fitPixelsPerBlock > 0)
                                                || Double.isNaN(pixelsPerBlock) || Double.isNaN(fitPixelsPerBlock)
                                                || pixelsPerBlock >= fitPixelsPerBlock) {
                        return 1.0;
                }
                double eFolds = Math.log(fitPixelsPerBlock / pixelsPerBlock);
                double over = eFolds - PHASE_SPAN;
                return over <= 0 ? 1.0 : Math.exp(-over);
        }

        /**
         * The globe's radius in GUI pixels, derived — not chosen. The
         * curved family holds the map's screen width at {@code 2Ω·R =
         * 2π·R_globe} from the flat end until the wrap closes it, so the
         * radius is fixed by where the flat map starts: {@code 2π·R_globe
         * = FIT_WIDTH_FRACTION · viewportWidth}. The finished globe's
         * diameter is therefore {@code FIT_WIDTH_FRACTION/π ≈ 0.3} of the
         * viewport width — a comfortable quarter-screen sphere that needs
         * no viewport-height negotiation (the map is 2:1, so its flat
         * height is always the smaller constraint).
         */
        public static double globeRadius(int viewportWidth, int viewportHeight) {
                return FIT_WIDTH_FRACTION * viewportWidth / (2.0 * Math.PI);
        }

        /**
         * One point of the family. The curvature is clamped to the math
         * floor inside; callers that branch on flatness use their raw
         * phase, never this method's internals.
         *
         * @param worldX    world X in blocks
         * @param worldZ    world Z in blocks
         * @param curvature raw transition phase in [0, 1]
         * @param radius    the globe radius in GUI pixels (at full curvature)
         * @param halfWidthBlocks  the world's half width in blocks
         * @param halfHeightBlocks the world's half height in blocks
         * @return screen offset from the globe's center: {@code x()} right,
         *         {@code y()} down, {@code z()} the orthographic depth
         *         (positive = front hemisphere)
         */
        public static SpherePoint project(double worldX, double worldZ, double curvature,
                        double radius, double halfWidthBlocks, double halfHeightBlocks) {
                double t = Math.clamp(curvature, CURVATURE_EPSILON, 1.0);
                double lon = (worldX / halfWidthBlocks) * Math.PI * t;
                double lat = (-worldZ / halfHeightBlocks) * Math.PI / 2.0 * t;
                double r = radius / t;
                double cosLat = Math.cos(lat);
                return new SpherePoint(
                                                r * cosLat * Math.sin(lon),
                                                -r * Math.sin(lat),
                                                r * cosLat * Math.cos(lon));
        }

        /** The exact flat offset the family tends to as curvature → 0. */
        public static double flatScale(double radius, double halfWidthBlocks) {
                return Math.PI * radius / halfWidthBlocks;
        }

        /**
         * Inverse for the front hemisphere: a screen offset from the
         * globe's center back to geographic angles. Returns {@code null}
         * when the offset lies off the sphere — nothing to pick there.
         */
        public static SphereAngle unproject(double screenX, double screenY, double curvature,
                        double radius, double halfWidthBlocks, double halfHeightBlocks) {
                double t = Math.clamp(curvature, CURVATURE_EPSILON, 1.0);
                double r = radius / t;
                double rho2 = screenX * screenX + screenY * screenY;
                if (rho2 > r * r) {
                        return null;
                }
                double cosLat = Math.sqrt(Math.max(0.0, 1.0 - rho2 / (r * r)));
                // atan2(x, z) with z = cosLat·r: the orthographic inverse of
                // X = r·cosLat·sin(lon), Z = r·cosLat·cos(lon).
                double lon = Math.atan2(screenX, cosLat * r);
                double lat = Math.asin(Math.clamp(-screenY / r, -1.0, 1.0));
                return new SphereAngle(lon / (Math.PI * t) * halfWidthBlocks,
                                                -lat / (Math.PI / 2.0 * t) * halfHeightBlocks);
        }

        /**
         * Whether the sphere point at the given depth faces the viewer:
         * the limb-culling predicate. At full curvature the map's borders
         * wrap to the far side and cull; during the transition everything
         * is front-facing and nothing culls.
         */
        public static boolean frontFacing(double depth) {
                return depth >= 0.0;
        }

        /** A screen offset plus its orthographic depth, the family's output. */
        public record SpherePoint(double x, double y, double z) {
        }

        /** World blocks recovered by {@link #unproject}. */
        public record SphereAngle(double worldX, double worldZ) {
        }
}
