package net.zaminmc.zea.map.projection;

import net.zaminmc.zea.map.model.MapCamera;

/**
 * Interprets raw surface input against a camera: the drag that keeps
 * the world under the cursor put, and the zoom that pins it there.
 * The pure counterpart of {@link MapProjection}'s transforms — input
 * arrives in screen pixels, and the rotation's inverse carries it back
 * into world space exactly once, here, instead of being re-derived
 * (differently, then diverging) by every surface that receives input.
 *
 * <p><b>Drag.</b> Dragging the surface moves the content with the
 * cursor: the world point under the cursor stays under the cursor, so
 * the center moves by the inverse-rotated drag, negated. At rotation
 * zero this is {@link MapCamera#pannedBy} to the pixel; the formula is
 * the same one, generalized.
 *
 * <p><b>Zoom.</b> One wheel notch is one factor of
 * {@link #ZOOM_PER_NOTCH} — deliberately one tile-pyramid level, so a
 * zoom step lands the request pattern exactly on the next zoom's tile
 * set instead of between two. The zoom anchors on the cursor's world
 * point (the inverse-rotated offset from the view centre), and the
 * result is clamped back through {@link MapCamera#centeredOn}:
 * {@code zoomedAround} itself does not clamp, and a cursor near a
 * world edge would otherwise pull the centre past the extent.
 *
 * <p><b>Motion.</b> Frame-to-frame camera motion — what the tile
 * budgets key on — is simply the centre delta, whatever caused it: a
 * pan, or the anchored drift of a zoom. No special zoom policy exists
 * to drift out of sync with the budget's assumptions; the caller
 * subtracts the previous camera from the current one.
 */
public final class MapCameraInput {

	/** Zoom per wheel notch: one tile-pyramid level. */
	public static final double ZOOM_PER_NOTCH = 2.0;

	private MapCameraInput() {
	}

	/**
	 * Pans the camera by a screen-space drag of
	 * ({@code screenDeltaX}, {@code screenDeltaY}) pixels, under a view
	 * rotated by {@code rotationRadians} (the projection's angle of map
	 * north away from screen up).
	 */
	public static MapCamera pan(MapCamera camera, double rotationRadians, double screenDeltaX,
			double screenDeltaY) {
		double cos = Math.cos(rotationRadians);
		double sin = Math.sin(rotationRadians);
		double ppb = camera.pixelsPerBlock();
		double worldX = (screenDeltaX * cos - screenDeltaY * sin) / ppb;
		double worldZ = (screenDeltaX * sin + screenDeltaY * cos) / ppb;
		return camera.centeredOn(camera.centerX() - worldX, camera.centerZ() - worldZ);
	}

	/**
	 * Zooms by {@code factor} with the world point under
	 * ({@code screenX}, {@code screenY}) pinned to its screen position,
	 * under a view rotated by {@code rotationRadians}.
	 */
	public static MapCamera zoomAtCursor(MapCamera camera, int surfaceWidth, int surfaceHeight,
			double rotationRadians, double screenX, double screenY, double factor) {
		double cos = Math.cos(rotationRadians);
		double sin = Math.sin(rotationRadians);
		double ppb = camera.pixelsPerBlock();
		double offsetX = (screenX - surfaceWidth / 2.0) / ppb;
		double offsetZ = (screenY - surfaceHeight / 2.0) / ppb;
		double anchorX = camera.centerX() + offsetX * cos - offsetZ * sin;
		double anchorZ = camera.centerZ() + offsetX * sin + offsetZ * cos;
		MapCamera zoomed = camera.zoomedAround(anchorX, anchorZ, factor);
		// zoomedAround does not clamp; a cursor near a world edge must
		// not pull the centre past the extent.
		return zoomed.centeredOn(zoomed.centerX(), zoomed.centerZ());
	}
}
