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
 * <p>At curvature {@code t} the camera-centered world offset
 * {@code (x − centerX, z − centerZ)} maps to geographic angles:
 *
 * <pre>
 *   lon = ((x − centerX) / halfWidth)  · π      — ±π across the world
 *   lat = (−(z − centerZ) / halfHeight) · π/2   — +π/2 north of center
 * </pre>
 *
 * <p>(Minecraft north is −z, so latitude is positive northward.) Those
 * angles wrap onto a sphere of radius {@code R(t) = R_globe / t} in GUI
 * pixels — the angular span grows with {@code t} while the radius
 * shrinks, and the orthographic projection (north pole up, the camera's
 * own position at the sphere's front) gives the screen offset:
 *
 * <pre>
 *   X = R·cos(lat)·sin(lon)      sx = X
 *   Y = R·sin(lat)               sy = −Y
 *   Z = R·cos(lat)·cos(lon)      (depth: positive = front hemisphere)
 * </pre>
 *
 * <p>Continuity is exact, not fitted. As {@code t → 0}, {@code R → ∞}
 * while the angular span vanishes at the same rate, and the offset tends
 * to the linear flat map {@code sx ≈ (x − centerX) · π·R_globe/halfWidth}
 * — the plane the flat path already renders, to the last bit ({@link
 * #flatScale}). At {@code t = 1} the spans close the full sphere: the
 * world's west/east borders meet at the antimeridian (behind the
 * viewer, culled by depth) and the poles seal. Between the two the
 * surface curves like a page curling into a ball.
 *
 * <h2>The latitude glide</h2>
 *
 * <p>The family wraps {@code lat ∈ [−π/2, +π/2]} at full curvature only
 * when the camera sits on the equator; a camera parked near a world edge
 * would push the plate past the pole and fold the sphere over itself.
 * The effective camera center therefore glides toward the equator as
 * {@code t → 1}, inside the band {@code |centerZ| ≤ halfHeight·(1−t)/t}
 * — continuous, and the stored camera never moves, so zooming back in
 * restores the exact flat position. Longitude needs no glide: the plate
 * spans exactly {@code 2π} at any {@code centerX}, so panning keeps
 * spinning the finished globe.
 *
 * <h2>Scale phases</h2>
 *
 * <p>The camera stays the single zoom scalar ({@code pixelsPerBlock});
 * the curvature is derived from it, so the transition rides the existing
 * wheel zoom: flat while {@code ppb ≥ fit}; the curl across {@link
 * #PHASE_SPAN} e-foldings below the fit point; then the finished globe
 * recedes by the remaining zoom-out. Every value is a pure function of
 * {@code ppb}, so reversing the zoom retraces the same path with no
 * hysteresis.
 *
 * <p>Pure type: no Minecraft classes, everything pinned by tests. The
 * clamped curvature the math uses is deliberately distinct from the raw
 * phase callers branch on — a raw phase of 0 means "definitely flat"
 * even though the math clamps its own copy to a floor to keep the
 * radius finite.
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
         * One point of the family, relative to the camera's center. The
         * curvature is clamped to the math floor inside; callers that
         * branch on flatness use their raw phase, never this method's
         * internals.
         *
         * @param worldX    world X in blocks
         * @param worldZ    world Z in blocks
         * @param centerX   the camera's center X (faces the viewer)
         * @param centerZ   the camera's center Z, glided toward the equator
         * @param curvature raw transition phase in [0, 1]
         * @param radius    the globe radius in GUI pixels (at full curvature)
         * @param halfWidthBlocks  the world's half width in blocks
         * @param halfHeightBlocks the world's half height in blocks
         * @return screen offset from the globe's center: {@code x()} right,
         *         {@code y()} down, {@code z()} the orthographic depth
         *         (positive = front hemisphere)
         */
        public static SpherePoint project(double worldX, double worldZ,
                        double centerX, double centerZ,
                        double curvature, double radius,
                        double halfWidthBlocks, double halfHeightBlocks) {
                double t = Math.clamp(curvature, CURVATURE_EPSILON, 1.0);
                double lon = ((worldX - centerX) / halfWidthBlocks) * Math.PI * t;
                double lat = (-(worldZ - glidedCenterZ(centerZ, t, halfHeightBlocks))
                                                / halfHeightBlocks) * Math.PI / 2.0 * t;
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
         * The latitude glide: the camera's effective center Z, clamped so
         * the plate's latitude span stays inside the sphere at curvature
         * {@code t}. At {@code t = 1} the band closes on the equator; at
         * small {@code t} it admits any stored position.
         */
        public static double glidedCenterZ(double centerZ, double t, double halfHeightBlocks) {
                double band = halfHeightBlocks * (1.0 - t) / t;
                return Math.clamp(centerZ, -band, band);
        }

        /**
         * Inverse for the front hemisphere: a screen offset from the
         * globe's center back to world blocks. Returns {@code null} when
         * the offset lies off the sphere — nothing to pick there.
         */
        public static SphereAngle unproject(double screenX, double screenY,
                        double centerX, double centerZ,
                        double curvature, double radius,
                        double halfWidthBlocks, double halfHeightBlocks) {
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
                double glided = glidedCenterZ(centerZ, t, halfHeightBlocks);
                return new SphereAngle(
                                                centerX + lon / (Math.PI * t) * halfWidthBlocks,
                                                glided - lat / (Math.PI / 2.0 * t) * halfHeightBlocks);
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
