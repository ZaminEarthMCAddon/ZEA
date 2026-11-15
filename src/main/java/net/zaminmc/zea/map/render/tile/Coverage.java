package net.zaminmc.zea.map.render.tile;

import net.zaminmc.zea.map.model.MapWorld;

import java.util.Objects;

/**
 * When the renderer may swap from the coarse Quick Map layer (zoom 0
 * under everything) to the Detailed layer at the chosen zoom — and when
 * it must not.
 *
 * <p>The rule comes from the reference implementation's hardest visual
 * lesson ({@code 8ce45767}): swapping to fine tiles the moment
 * <i>some</i> of them arrive produces a sharp-blurry mosaic that
 * flickers while panning. So the Detailed layer activates only at
 * <b>full visible coverage</b>, and once active it is <b>sticky</b> —
 * coverage may dip (a pan into fresh territory, an eviction) without
 * falling back to the blurry layer wholesale; individual gaps are
 * filled by parent-fallback quads instead.
 *
 * <p>Stickiness is scoped to one {@code (world, zoom)} view. A zoom
 * change or world switch re-arms the gate, because the new zoom's tiles
 * have not been shown yet and the mosaic argument applies again. A
 * cache clear for the world re-arms it too — there is nothing left to
 * draw a clean Detailed layer from, and the Quick Map's re-fetch is the
 * honest state to show.
 *
 * <p>An empty view (no visible slots at all) never activates: a fully
 * zoomed-out camera over an unloaded cache must not latch the Detailed
 * layer on a vacuous truth.
 */
public final class Coverage {

	private MapWorld world;
	private int zoom = -1;
	private boolean active;

	/**
	 * Feeds one frame's view identity and slot accounting; returns
	 * whether the Detailed layer should draw this frame.
	 *
	 * @param coveredSlots visible slots that are drawable or confirmed
	 *                     absent (a 404 slot is covered: no finer
	 *                     imagery exists for it, at any level)
	 * @param totalSlots   visible slots at the chosen zoom; zero when
	 *                     the view shows no tiles at all
	 */
	public boolean update(MapWorld world, int zoom, int coveredSlots, int totalSlots) {
		if (!Objects.equals(world, this.world) || zoom != this.zoom) {
			this.world = world;
			this.zoom = zoom;
			active = false;
		}
		if (!active && totalSlots > 0 && coveredSlots >= totalSlots) {
			active = true;
		}
		return active;
	}

	/** Whether the Detailed layer is currently active for the view. */
	public boolean active() {
		return active;
	}

	/** The view the stickiness currently belongs to. */
	public MapWorld world() {
		return world;
	}

	/** The zoom the stickiness currently belongs to. */
	public int zoom() {
		return zoom;
	}

	/**
	 * Re-arms the gate, keeping the view identity — for cache clears
	 * and explicit rebuilds, where the imagery behind an active
	 * Detailed layer has just been dropped.
	 */
	public void rearm() {
		active = false;
	}

	/** Forget everything, as if no frame had ever been planned. */
	public void reset() {
		world = null;
		zoom = -1;
		active = false;
	}
}
