package net.zaminmc.zea.map.projection;

import net.zaminmc.zea.map.model.MapCamera;
import net.zaminmc.zea.map.model.MapWorld;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The input interpretation, pinned the only way input math can be
 * pinned: by its user-visible invariant. A drag moves the whole view's
 * content by exactly the drag; a cursor-anchored zoom keeps the world
 * point under the cursor on that pixel — both under rotation, both
 * measured through {@link MapProjection}'s own transforms, which its
 * tests pinned first.
 */
class MapCameraInputTest {

        private static final double[] ROTATIONS = { 0.0, Math.PI / 6.0, Math.PI / 2.0, Math.PI, -0.7, 2.9 };

        @Test
        void dragMatchesPanByWhenNorthUp() {
                MapCamera camera = MapCamera.at(MapWorld.EARTH, 1000.0, -2000.0).zoom(0.5);
                assertEquals(camera.pannedBy(37.0, -11.0), MapCameraInput.pan(camera, 0.0, 37.0, -11.0));
                assertEquals(camera.pannedBy(-4.5, 260.25), MapCameraInput.pan(camera, 0.0, -4.5, 260.25));
        }

        @Test
        void dragMovesTheContentByExactlyTheDrag() {
                for (double rotation : ROTATIONS) {
                        MapCamera camera = MapCamera.at(MapWorld.EARTH, 4096.0, -8192.0).zoom(1.75);
                        MapProjection before = new MapProjection(camera, 854, 480, rotation);
                        // A fixed world point and the drag applied to the view.
                        double worldX = camera.centerX() + 300.0;
                        double worldZ = camera.centerZ() - 150.0;
                        double dragX = 42.0;
                        double dragY = -17.5;
                        MapCamera after = MapCameraInput.pan(camera, rotation, dragX, dragY);
                        MapProjection afterProjection = new MapProjection(after, 854, 480, rotation);
                        assertEquals(before.screenX(worldX, worldZ) + dragX, afterProjection.screenX(worldX, worldZ), 1e-6,
                                                        "content x did not follow the drag at rotation " + rotation);
                        assertEquals(before.screenY(worldX, worldZ) + dragY, afterProjection.screenY(worldX, worldZ), 1e-6,
                                                        "content y did not follow the drag at rotation " + rotation);
                }
        }

        @Test
        void zoomAtCursorPinsTheWorldUnderTheCursor() {
                for (double rotation : ROTATIONS) {
                        MapCamera camera = MapCamera.at(MapWorld.EARTH, -512.5, 1024.25).zoom(1.0);
                        int width = 800;
                        int height = 600;
                        MapProjection projection = new MapProjection(camera, width, height, rotation);
                        double cursorX = 590.0;
                        double cursorY = 410.0;
                        double worldX = projection.worldX(cursorX, cursorY);
                        double worldZ = projection.worldZ(cursorX, cursorY);
                        for (double factor : new double[] { 2.0, 0.5, 2.0 }) {
                                MapCamera zoomed = MapCameraInput.zoomAtCursor(camera, width, height, rotation,
                                                                cursorX, cursorY, factor);
                                MapProjection zoomedProjection = new MapProjection(zoomed, width, height, rotation);
                                assertEquals(cursorX, zoomedProjection.screenX(worldX, worldZ), 1e-6,
                                                                "world point left the cursor at rotation " + rotation);
                                assertEquals(cursorY, zoomedProjection.screenY(worldX, worldZ), 1e-6,
                                                                "world point left the cursor at rotation " + rotation);
                                camera = zoomed;
                        }
                }
        }

        @Test
        void zoomAtCursorClampsToTheWorldExtent() {
                MapWorld world = MapWorld.MOON;
                // Parked at the north-west corner of the world, zooming in with
                // the cursor beyond the corner would pull the centre outside.
                MapCamera camera = new MapCamera(world, -world.worldWidth / 2.0, -world.worldHeight / 2.0, 0.125);
                MapCamera zoomed = MapCameraInput.zoomAtCursor(camera, 800, 600, 0.0, -400.0, -300.0, 4.0);
                assertTrue(zoomed.centerX() >= -world.worldWidth / 2.0 && zoomed.centerX() <= world.worldWidth / 2.0,
                                                "center x escaped the world");
                assertTrue(zoomed.centerZ() >= -world.worldHeight / 2.0 && zoomed.centerZ() <= world.worldHeight / 2.0,
                                                "center z escaped the world");
        }

        @Test
        void zoomNotchIsOnePyramidLevel() {
                assertEquals(2.0, MapCameraInput.ZOOM_PER_NOTCH, 0.0);
                // One notch from a power-of-two zoom lands exactly one tile
                // pyramid level away, until the chooser's own cap says
                // otherwise. The ppb must sit below the bias-pinned band:
                // QUALITY_ZOOM_BIAS keeps demanding cap-level tiles down to
                // 2^-3 pixels per block, so a notch from 0.25 is already
                // pinned at MAX_ZOOM and cannot step a level.
                double ppb = 1.0 / 32.0;
                int zoomBefore = MapProjection.chooseTileZoom(ppb);
                double next = Math.clamp(ppb * MapCameraInput.ZOOM_PER_NOTCH,
                                                MapCamera.MIN_PIXELS_PER_BLOCK, MapCamera.MAX_PIXELS_PER_BLOCK);
                assertEquals(zoomBefore + 1, MapProjection.chooseTileZoom(next));
                // One notch back out, the same step holds in reverse.
                double back = Math.clamp(ppb / MapCameraInput.ZOOM_PER_NOTCH,
                                                MapCamera.MIN_PIXELS_PER_BLOCK, MapCamera.MAX_PIXELS_PER_BLOCK);
                assertEquals(zoomBefore - 1, MapProjection.chooseTileZoom(back));
        }

        @Test
        void zoomClampsAtTheCameraBounds() {
                MapCamera maxed = MapCamera.at(MapWorld.EARTH, 0.0, 0.0).zoom(MapCamera.MAX_PIXELS_PER_BLOCK);
                assertEquals(maxed, MapCameraInput.zoomAtCursor(maxed, 800, 600, 0.0, 400.0, 300.0,
                                                MapCameraInput.ZOOM_PER_NOTCH));
                MapCamera minned = MapCamera.at(MapWorld.EARTH, 0.0, 0.0).zoom(MapCamera.MIN_PIXELS_PER_BLOCK);
                assertEquals(minned, MapCameraInput.zoomAtCursor(minned, 800, 600, 0.0, 400.0, 300.0, 0.5));
        }
}
