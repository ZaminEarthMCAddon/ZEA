package net.zaminmc.zea.map.render.tile;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.zaminmc.zea.map.render.overlay.ClaimOverlay;
import net.zaminmc.zea.map.render.overlay.PlayerOverlay;
import org.joml.Matrix3x2fStack;

/**
 * Draws the overlay layers that live in the same map frame as the
 * tiles: claim fills and outlines, and the player markers. The frame
 * naming is the planner's \— map-frame pixels, view-centre
 * relative, north up \— so this class composes exactly the pose
 * {@link TileFrameDrawer} does and draws every rectangle axis-aligned
 * inside it, snapped the same way, so claim borders and tile seams
 * agree to the pixel.
 *
 * <p>Labels are not drawn here: they stay upright in screen space, and
 * the surface composes them after this pass through the projection's
 * own screen transforms.
 */
public final class OverlayDrawer {

	private OverlayDrawer() {
	}

	/**
	 * Draws one frame's claim rectangles under the rotated pose.
	 *
	 * @param centerX        the surface's center x in screen pixels
	 * @param centerY        the surface's center y in screen pixels
	 * @param rotationRadians the projection's rotation
	 */
	public static void drawClaims(GuiGraphicsExtractor graphics, Iterable<ClaimOverlay.Rect> rects,
					float centerX, float centerY, double rotationRadians) {
		Matrix3x2fStack pose = graphics.pose();
		pose.pushMatrix();
		TileFrameDrawer.composePose(pose, centerX, centerY, rotationRadians);
		for (ClaimOverlay.Rect rect : rects) {
			TileFrameDrawer.Rect snapped = TileFrameDrawer.snap(rect.x(), rect.y(), rect.width(), rect.height());
			if (snapped.width() > 0 && snapped.height() > 0) {
				graphics.fill(snapped.x(), snapped.y(),
								snapped.x() + snapped.width(), snapped.y() + snapped.height(), rect.argb());
			}
		}
		pose.popMatrix();
	}

	/**
	 * Draws one frame's player markers under the rotated pose: a dot
	 * with a heading nub, gold for the local player. Marker positions
	 * are map-frame pixels; the nub is offset along the yaw in world
	 * space, which the same rotation carries onto the screen.
	 */
	public static void drawMarkers(GuiGraphicsExtractor graphics, Iterable<PlayerOverlay.Marker> markers,
					float centerX, float centerY, double rotationRadians) {
		Matrix3x2fStack pose = graphics.pose();
		pose.pushMatrix();
		TileFrameDrawer.composePose(pose, centerX, centerY, rotationRadians);
		for (PlayerOverlay.Marker marker : markers) {
			int x = (int) Math.round(marker.x());
			int y = (int) Math.round(marker.y());
			int body = marker.self() ? 0xFFFFD75A : 0xFFE0E0E8;
			int edge = 0xFF10101A;
			graphics.fill(x - 3, y - 3, x + 3, y + 3, edge);
			graphics.fill(x - 2, y - 2, x + 2, y + 2, body);
			double yawRadians = Math.toRadians(marker.yawDegrees());
			double dirX = -Math.sin(yawRadians);
			double dirZ = Math.cos(yawRadians);
			int nubX = x + (int) Math.round(dirX * 3.0);
			int nubY = y + (int) Math.round(dirZ * 3.0);
			graphics.fill(nubX - 1, nubY - 1, nubX + 1, nubY + 1, marker.self() ? 0xFFFFF0B0 : 0xFF101018);
		}
		pose.popMatrix();
	}
}
