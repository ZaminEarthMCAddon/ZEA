package net.zaminmc.zea.map.model;

import java.util.Objects;

/**
 * The view state of one map surface: where it is looking and how zoomed
 * it is. Cameras are per-world and per-surface — the world map and the
 * minimap each own one per world and never overwrite each other. A camera
 * is initialized from its saved value or the player's position, then only
 * changes through user input; nothing resets it per frame.
 *
 * @param world         the world this camera views
 * @param centerX       world X at the view center
 * @param centerZ       world Z at the view center
 * @param pixelsPerBlock the single zoom scalar: GUI pixels per world block
 */
public record MapCamera(MapWorld world, double centerX, double centerZ, double pixelsPerBlock) {

        /**
         * Default zoom limits, in GUI pixels per block. The floor is far
         * below the whole-map fit scale (~1280/129,024 ≈ 1/101 at a
         * 1280-wide viewport): the globe phases need headroom below the
         * fit point — the transition curls the map at fit, and continued
         * zoom-out recedes the finished globe.
         */
        public static final double MIN_PIXELS_PER_BLOCK = 1.0 / 1024.0;
        public static final double MAX_PIXELS_PER_BLOCK = 8.0;

        public MapCamera {
                Objects.requireNonNull(world, "world");
                if (!(pixelsPerBlock > 0) || Double.isNaN(pixelsPerBlock)) {
                        pixelsPerBlock = 1.0;
                }
                pixelsPerBlock = Math.clamp(pixelsPerBlock, MIN_PIXELS_PER_BLOCK, MAX_PIXELS_PER_BLOCK);
        }

        /** A camera centered on a position at the default zoom. */
        public static MapWorldHolder at(MapWorld world, double x, double z) {
                return new MapWorldHolder(world, x, z);
        }

        /** Moves the center, clamped to the world's known extent. */
        public MapCamera centeredOn(double x, double z) {
                double halfW = world.worldWidth / 2.0;
                double halfH = world.worldHeight / 2.0;
                double cx = Math.clamp(x, -halfW, halfW);
                double cz = Math.clamp(z, -halfH, halfH);
                return new MapCamera(world, cx, cz, pixelsPerBlock);
        }

        /**
         * Zooms by a multiplier, keeping the given world point pinned to the
         * same screen position (the cursor). The anchor is the point that must
         * stay fixed, in world coordinates.
         */
        public MapCamera zoomedAround(double anchorX, double anchorZ, double factor) {
                double next = Math.clamp(pixelsPerBlock * factor, MIN_PIXELS_PER_BLOCK, MAX_PIXELS_PER_BLOCK);
                if (next == pixelsPerBlock) {
                        return this;
                }
                // Keep (anchor − center) * ppb constant on screen.
                double scale = pixelsPerBlock / next;
                double cx = anchorX - (anchorX - centerX) * scale;
                double cz = anchorZ - (anchorZ - centerZ) * scale;
                return new MapCamera(world, cx, cz, next);
        }

        /** Panned by a screen-space delta, converted back to world units. */
        public MapCamera pannedBy(double screenDeltaX, double screenDeltaY) {
                return centeredOn(centerX - screenDeltaX / pixelsPerBlock, centerZ - screenDeltaY / pixelsPerBlock);
        }

        /** The world X at a screen coordinate, given the surface size. */
        public double worldXAt(double screenX, int surfaceWidth) {
                return centerX + (screenX - surfaceWidth / 2.0) / pixelsPerBlock;
        }

        /** The world Z at a screen coordinate, given the surface height. */
        public double worldZAt(double screenY, int surfaceHeight) {
                return centerZ + (screenY - surfaceHeight / 2.0) / pixelsPerBlock;
        }

        /** Left edge of the visible world rectangle. */
        public double viewLeft(int surfaceWidth) {
                return centerX - surfaceWidth / 2.0 / pixelsPerBlock;
        }

        /** Right edge of the visible world rectangle. */
        public double viewRight(int surfaceWidth) {
                return centerX + surfaceWidth / 2.0 / pixelsPerBlock;
        }

        /** Top (north) edge of the visible world rectangle. */
        public double viewTop(int surfaceHeight) {
                return centerZ - surfaceHeight / 2.0 / pixelsPerBlock;
        }

        /** Bottom (south) edge of the visible world rectangle. */
        public double viewBottom(int surfaceHeight) {
                return centerZ + surfaceHeight / 2.0 / pixelsPerBlock;
        }

        /** Builder-style anchor holder for {@link #at(MapWorld, double, double)}. */
        public record MapWorldHolder(MapWorld world, double x, double z) {
                public MapCamera zoom(double pixelsPerBlock) {
                        return new MapCamera(world, x, z, pixelsPerBlock);
                }
        }
}
