package net.zaminmc.zea.map.render.overlay;

import net.zaminmc.zea.map.model.MapCamera;
import net.zaminmc.zea.map.model.TownClaim;
import net.zaminmc.zea.map.projection.MapProjection;
import net.zaminmc.zea.map.render.overlay.ClaimTessellator.FillRun;

import java.util.ArrayList;
import java.util.List;

/**
 * Projects one world's claims into the frame's draw list: fill runs
 * and outline rectangles, culled by bounding box, clipped to the view,
 * coloured per town, positioned in map-frame pixels — ready for the
 * adapter's flat {@code fill} calls.
 *
 * <p>Pure planning keeps the visual policies testable: the even-odd
 * holes, the run merging, the screen-space outline thickness (outlines
 * are constant-<i>pixel</i> wide no matter the zoom — they are map
 * furniture, not geography), the far-zoom outline decimation (a border
 * whose chunk is under {@link #OUTLINE_MIN_BLOCK_PIXELS} on screen is
 * noise, not information — {@code 10118a2}'s hybrid-vector lesson),
 * and the double-blend seam compromise (fill edges round outward, so
 * abutting runs can overlap by one pixel at low alpha).
 */
public final class ClaimOverlay {

	/** One rectangle to fill: map-frame pixels plus the ARGB colour. */
	public record Rect(double x, double y, double width, double height, int argb) {
	}

	/** Below this many screen pixels per chunk, outlines are dropped. */
	public static final double OUTLINE_MIN_BLOCK_PIXELS = 2.0 / 16.0;

	private final ClaimTessellator tessellator = new ClaimTessellator();

	/**
	 * Plans the claim rectangles for one frame.
	 *
	 * @param claims         the world's effective claims (overrides applied)
	 * @param showFills      whether fills are enabled at all
	 * @param showOutlines   whether outlines are enabled at all
	 * @param fillAlpha      0-255
	 * @param outlineAlpha   0-255
	 * @param outlineThicknessPx screen-space thickness of a claim border
	 */
	public List<Rect> plan(List<TownClaim> claims, MapProjection projection, boolean showFills,
					boolean showOutlines, int fillAlpha, int outlineAlpha, double outlineThicknessPx) {
		if (claims.isEmpty() || !(fillAlpha > 0 || outlineAlpha > 0)) {
			return List.of();
		}
		MapCamera camera = projection.camera();
		double ppb = camera.pixelsPerBlock();
		double left = camera.viewLeft(projection.surfaceWidth()) - 16.0;
		double right = camera.viewRight(projection.surfaceWidth()) + 16.0;
		double top = camera.viewTop(projection.surfaceHeight()) - 16.0;
		double bottom = camera.viewBottom(projection.surfaceHeight()) + 16.0;
		boolean outlines = showOutlines && outlineAlpha > 0 && ppb >= OUTLINE_MIN_BLOCK_PIXELS;
		boolean fills = showFills && fillAlpha > 0;
		if (!fills && !outlines) {
			return List.of();
		}
		List<Rect> out = new ArrayList<>();
		for (TownClaim town : claims) {
			if (!town.intersectsWorld(left, right, top, bottom)) {
				continue;
			}
			ClaimTessellator.Tessellation tessellation = tessellator.tessellate(town);
			if (fills) {
				int argb = town.argbFill(fillAlpha);
				for (FillRun run : tessellation.fills()) {
					if (run.x1() <= left || run.x0() >= right || run.z1() <= top || run.z0() >= bottom) {
						continue;
					}
					double x = Math.max(run.x0(), left);
					double z = Math.max(run.z0(), top);
					double x1 = Math.min(run.x1(), right);
					double z1 = Math.min(run.z1(), bottom);
					out.add(new Rect((x - camera.centerX()) * ppb, (z - camera.centerZ()) * ppb,
									(x1 - x) * ppb, (z1 - z) * ppb, argb));
				}
			}
			if (outlines) {
				int argb = town.argbOutline(outlineAlpha);
				for (ClaimTessellator.EdgeRun edge : tessellation.edges()) {
					if (edge.horizontal() ? (edge.x() >= right || edge.x() + edge.length() <= left
									|| edge.z() <= top || edge.z() >= bottom)
									: (edge.x() <= left || edge.x() >= right
													|| edge.z() >= bottom || edge.z() + edge.length() <= top)) {
						continue;
					}
					if (edge.horizontal()) {
						out.add(new Rect((edge.x() - camera.centerX()) * ppb,
										(edge.z() - camera.centerZ()) * ppb - outlineThicknessPx / 2.0,
										edge.length() * ppb, outlineThicknessPx, argb));
					} else {
						out.add(new Rect((edge.x() - camera.centerX()) * ppb - outlineThicknessPx / 2.0,
										(edge.z() - camera.centerZ()) * ppb,
										outlineThicknessPx, edge.length() * ppb, argb));
					}
				}
			}
		}
		return out;
	}
}
