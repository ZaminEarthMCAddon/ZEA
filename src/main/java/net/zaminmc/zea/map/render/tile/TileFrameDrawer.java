package net.zaminmc.zea.map.render.tile;

import com.mojang.renderpearl.api.textures.GpuTextureView;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.zaminmc.zea.map.projection.MapProjection;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fStack;

/**
 * The quad-drawing half of the tile render layer: takes a planned
 * {@link TileRenderer.FramePlan} and blits its quads through exactly
 * one rotated pose per frame, the way the planner's frame naming
 * demands — the projection's rotation is set up once, and every quad
 * draws axis-aligned inside it instead of carrying its own transform.
 *
 * <p><b>Pixel snapping.</b> Vanilla's blit takes whole-pixel
 * rectangles, but camera positions are fractional, and rounding each
 * quad independently is the classic seam bug: two tiles that share an
 * edge in double space can round to a one-pixel gap or overlap.
 * {@link #snap} rounds each <i>edge</i> — never a width — so adjacent
 * quads, which share their edge coordinate exactly by construction,
 * snap to the same integer edge and stay adjacent. The pose then maps
 * those integers through one affine transform, and a shared edge is
 * still one shared coordinate on the other side.
 *
 * <p>Scissoring is deliberately not this class's business: the screen
 * owns where the surface sits and what it may paint over; the drawer
 * only knows the surface's center and rotation.
 */
public final class TileFrameDrawer {

	private TileFrameDrawer() {
	}

	/**
	 * Draws a planned frame. The overview layer goes under, the
	 * detailed layer over it, in the planner's emission order.
	 *
	 * @param centerX        the surface's center x in screen pixels —
	 *                       where the camera looks
	 * @param centerY        the surface's center y in screen pixels
	 * @param rotationRadians the projection's rotation
	 *                       ({@link MapProjection#rotationRadians()})
	 */
	public static void draw(GuiGraphicsExtractor graphics, TileTextures textures, TileRenderer.FramePlan plan,
			float centerX, float centerY, double rotationRadians) {
		Matrix3x2fStack pose = graphics.pose();
		pose.pushMatrix();
		composePose(pose, centerX, centerY, rotationRadians);
		for (TileRenderer.Quad quad : plan.overview()) {
			drawQuad(graphics, textures, quad);
		}
		for (TileRenderer.Quad quad : plan.detailed()) {
			drawQuad(graphics, textures, quad);
		}
		pose.popMatrix();
	}

	/**
	 * The one pose a frame draws through: map-frame pixels (view-centre
	 * relative, north up) become surface pixels. {@code A} is the angle
	 * by which map north is rotated away from screen up, so the pose
	 * rotates by {@code −A}: at {@code A = π/2} north points screen-left,
	 * exactly as {@code MapProjection.screenX/screenY} define it. The
	 * equivalence is pinned by tests against those very methods, so the
	 * adapter cannot drift from the projection's own pinned math.
	 */
	static void composePose(Matrix3x2f pose, float centerX, float centerY, double rotationRadians) {
		pose.translate(centerX, centerY);
		pose.rotate((float) -rotationRadians);
	}

	/** A pixel-aligned rectangle on the drawing surface. */
	record Rect(int x, int y, int width, int height) {
	}

	/**
	 * Snaps a map-frame rectangle to whole pixels. Each edge rounds
	 * independently ({@link Math#round}, halves toward positive
	 * infinity); width and height follow from the snapped edges and
	 * never round alone, which is what keeps a snapped seam from
	 * opening a gap or doubling a pixel.
	 */
	static Rect snap(double x, double y, double width, double height) {
		int left = (int) Math.round(x);
		int top = (int) Math.round(y);
		int right = (int) Math.round(x + width);
		int bottom = (int) Math.round(y + height);
		return new Rect(left, top, right - left, bottom - top);
	}

	private static void drawQuad(GuiGraphicsExtractor graphics, TileTextures textures, TileRenderer.Quad quad) {
		GpuTextureView view = textures.viewOf(quad.texture());
		if (view == null) {
			// The plan only emits quads for drawable tiles, and nothing
			// frees a handle between plan and draw on this thread; the
			// guard exists so a late free can never crash a frame if
			// that ordering ever changes.
			return;
		}
		Rect rect = snap(quad.x(), quad.y(), quad.width(), quad.height());
		MapProjection.TileRegion region = quad.region();
		graphics.blit(view, TileTextures.tileSampler(), rect.x(), rect.y(), rect.width(), rect.height(),
				(float) region.u0(), (float) region.v0(), (float) region.u1(), (float) region.v1());
	}
}
