package net.zaminmc.zea.map;

/**
 * The minimap's on-screen geometry, as pure math over the config's
 * semantic choices (edge, size) — never hand-placed pixels.
 *
 * <p>The default berth is the screen's top-right corner: NEU's classic
 * spot, clear of the hotbar, the chat and the effects column. The
 * coordinates line sits just under the panel, the scale bar under
 * that, each on its own text row so they never collide at any size.
 */
public final class MinimapLayout {

	/** Distance between the panel and the screen edges. */
	public static final int MARGIN = 4;

	/** Height of one text row under the panel (font line height plus one). */
	public static final int TEXT_ROW = 10;

	/** The panel rectangle. */
	public record Rect(int x, int y, int width, int height) {
	}

	/** One resolved scale bar: its pixel length and the blocks it represents. */
	public record ScaleBar(int pixels, long blocks) {
	}

	private MinimapLayout() {
	}

	/** The panel rectangle for the given panel size and screen. */
	public static Rect panel(int size, int screenWidth, int screenHeight) {
		int edge = Math.max(1, MARGIN);
		int side = Math.clamp(size, 16, Math.max(16, Math.min(screenWidth, screenHeight) - 2 * edge));
		return new Rect(screenWidth - side - edge, edge, side, side);
	}

	/** The y of the coordinates line under the panel; -1 when it does not fit. */
	public static int coordinatesY(Rect panel, int screenHeight) {
		int y = panel.y() + panel.height() + 1;
		return y + TEXT_ROW > screenHeight ? -1 : y;
	}

	/** The y of the scale bar line under the coordinates; -1 when it does not fit. */
	public static int scaleBarY(Rect panel, int screenHeight) {
		int y = panel.y() + panel.height() + 1 + TEXT_ROW;
		return y + TEXT_ROW > screenHeight ? -1 : y;
	}

	/**
	 * The scale bar for the view's zoom: the longest rung of the ladder of
	 * round block counts (1, 2, 5, 10, …) whose pixel length still fits
	 * inside the panel with a small inset.
	 */
	public static ScaleBar scaleBar(int panelSize, int blocksAcross) {
		int usable = Math.max(16, panelSize - 8);
		double pixelsPerBlock = panelSize / (double) Math.max(1, blocksAcross);
		ScaleBar best = new ScaleBar(Math.max(1, (int) Math.round(pixelsPerBlock)), 1);
		for (long blocks = 1; blocks <= blocksAcross; blocks = nextNice(blocks)) {
			int pixels = (int) Math.round(blocks * pixelsPerBlock);
			if (pixels <= usable) {
				best = new ScaleBar(pixels, blocks);
			}
		}
		return best;
	}

	/** 1 → 2 → 5 → 10 → 20 → 50 → 100 …: the ladder of round numbers. */
	private static long nextNice(long blocks) {
		long scale = 1;
		while (blocks >= 10 * scale) {
			scale *= 10;
		}
		long mantissa = blocks / scale;
		if (mantissa < 2) {
			return 2 * scale;
		}
		if (mantissa < 5) {
			return 5 * scale;
		}
		return 10 * scale;
	}
}
