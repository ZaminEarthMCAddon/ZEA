package net.zaminmc.zea.browser;

import java.util.List;

/**
 * The pure layout math of the inventory item browser — the one place
 * that answers where the pane sits and where its cells land, so
 * rendering and hit-testing can never drift apart.
 *
 * <p>Every formula here is NEU's own, taken from
 * {@code NEUOverlay.java} — not approximated from screenshots: the box
 * padding ({@code getBoxPadding}: {@code panePadding * 2 / scaleFactor + 5}
 * with the default {@code panePadding} of 10), the search-bar band the
 * nav strip occupies ({@code getSearchBarYSize}: {@code max(40 / scaleFactor, 16)}),
 * the grid's centering paddings ({@code getItemBoxXPadding} /
 * {@code getItemBoxYPadding}, the leftover split in half), the ceiling
 * column and row counts ({@code getSlotsXSize} / {@code getSlotsYSize}),
 * the nav buttons' size from the search bar's height
 * ({@code renderNavElement}: {@code min(maxButtonXSize, searchBarYSize * 3)}),
 * the sort bar's scaling ({@code sortOrderScaleFactor}) and the edge
 * tab's 64 px texture. What NEU computes against its 1.8.9
 * {@code ScaledResolution} is computed here against 26.3's scaled
 * coordinate space; the numbers are the numbers NEU's formulas produce.
 *
 * <p>The pane's left edge is the caller's input — it animates (the slide
 * between the closed and the open position), so every pane-anchored box
 * is a function of that edge, exactly as NEU's
 * {@code getItemPaneOffsetFactor()} feeds every draw and every hit test.
 */
public final class BrowserGeometry {
	/** A tile's 16 px item icon — NEU's {@code ITEM_SIZE}. */
	public static final int ITEM_SIZE = 16;

	/** The gap between tiles — NEU's {@code ITEM_PADDING}. */
	public static final int ITEM_PADDING = 4;

	/** The stride of one grid column or row: item plus padding. */
	public static final int STRIDE = ITEM_SIZE + ITEM_PADDING;

	/** NEU's open offset: the pane's left edge rests at two-thirds of the screen. */
	public static final float OPEN_OFFSET = 2f / 3f;

	/** NEU's info-pane offset: the left pane's right edge rests at one-third. */
	public static final float INFO_OPEN_OFFSET = 1f / 3f;

	/** The edge tab's texture size — NEU's 64 px {@code item_pane_tab_arrow}. */
	public static final int TAB_TEXTURE_SIZE = 64;

	/** The closed tab's rest offset — NEU's {@code LerpingInteger(20, 50)} start. */
	public static final int TAB_CLOSED_OFFSET = 20;

	/** NEU's default {@code itemlist.panePadding} (the config default). */
	public static final int PANE_PADDING = 10;

	/** NEU's default {@code toolbar.searchBarHeight}, raw pixels. */
	public static final int SEARCH_BAR_RAW_HEIGHT = 40;

	/** The fewest columns a pane keeps; a screen this narrow still gets a usable grid. */
	public static final int MIN_COLUMNS = 3;

	/** The most columns a wide screen may show — a sanity bound NEU never reaches. */
	public static final int MAX_COLUMNS = 30;

	private BrowserGeometry() {
	}

	/**
	 * The scale-dependent constants, resolved once per frame from the
	 * screen rectangle and the GUI scale factor — NEU reads the same
	 * values off its {@code ScaledResolution} and the config defaults.
	 */
	public static final class Metrics {
		public final int screenWidth;
		public final int screenHeight;
		/** NEU's {@code getBoxPadding()}. */
		public final int boxPadding;
		/** NEU's {@code getSearchBarYSize()}: the nav strip's height. */
		public final int searchBarYSize;
		/** The pane's width: a third of the screen ({@code paneWidthMult} 1.0). */
		public final int paneWidth;
		/** NEU's {@code getItemBoxXPadding()}. */
		public final int itemBoxXPadding;
		/** NEU's {@code getItemBoxYPadding()}. */
		public final int itemBoxYPadding;
		/** NEU's {@code getSlotsXSize()}. */
		public final int columns;
		/** NEU's {@code getSlotsYSize()}. */
		public final int rows;
		/** The sort bar's scaled icon size — NEU's {@code sortOrderScaleFactor}. */
		public final int sortIconSize;
		public final int sortStride;
		public final int sortIconTop;

		Metrics(int screenWidth, int screenHeight, int boxPadding, int searchBarYSize,
				int paneWidth, int itemBoxXPadding, int itemBoxYPadding,
				int columns, int rows, int sortIconSize, int sortStride, int sortIconTop) {
			this.screenWidth = screenWidth;
			this.screenHeight = screenHeight;
			this.boxPadding = boxPadding;
			this.searchBarYSize = searchBarYSize;
			this.paneWidth = paneWidth;
			this.itemBoxXPadding = itemBoxXPadding;
			this.itemBoxYPadding = itemBoxYPadding;
			this.columns = columns;
			this.rows = rows;
			this.sortIconSize = sortIconSize;
			this.sortStride = sortStride;
			this.sortIconTop = sortIconTop;
		}

		public int slotsPerPage() {
			return columns * rows;
		}
	}

	/** The metrics for a screen at a GUI scale factor. */
	public static Metrics metrics(int screenWidth, int screenHeight, int scaleFactor,
			int orderIconCount, int filterIconCount) {
		int boxPadding = (int) (PANE_PADDING * 2f / Math.max(1, scaleFactor) + 5);
		int searchBarYSize = Math.max(SEARCH_BAR_RAW_HEIGHT / Math.max(1, scaleFactor), ITEM_SIZE);
		int paneWidth = paneWidth(screenWidth);
		int itemBoxXPadding = ((paneWidth - 2 * boxPadding) % STRIDE + ITEM_PADDING) / 2;
		int itemBoxYPadding = ((screenHeight - searchBarYSize - 2 * boxPadding - ITEM_SIZE - 2)
				% STRIDE + ITEM_PADDING) / 2;
		int gridLeft = boxPadding + itemBoxXPadding;
		int xEnd = gridLeft + paneWidth - 2 * boxPadding - ITEM_SIZE - itemBoxXPadding;
		int columns = Math.max(MIN_COLUMNS,
				Math.min(MAX_COLUMNS, (int) Math.ceil((xEnd - gridLeft) / (float) STRIDE)));
		int yStart = boxPadding + searchBarYSize + itemBoxYPadding;
		int yEnd = screenHeight - boxPadding - ITEM_SIZE - 2 - itemBoxYPadding;
		int rows = Math.max(1, (int) Math.ceil((yEnd - yStart) / (float) STRIDE));

		// NEU's sort bar: scale the icons down when the pane is too narrow
		// for all of them at full stride — sortIconsMinX =
		// (orders + filters) * (ITEM_SIZE + ITEM_PADDING) + ITEM_SIZE, the
		// available span the pane's grid actually has.
		int availableX = paneWidth - 2 * boxPadding - 2 * itemBoxXPadding;
		int minSpan = (orderIconCount + filterIconCount) * STRIDE + ITEM_SIZE;
		int sortIconSize = Math.max(4,
				(int) (ITEM_SIZE * Math.min(1, availableX / (float) minSpan)));
		int sortStride = (int) (STRIDE * sortIconSize / (float) ITEM_SIZE);
		int sortIconTop = screenHeight - boxPadding - (ITEM_SIZE + sortIconSize) / 2 - 1;

		return new Metrics(screenWidth, screenHeight, boxPadding, searchBarYSize, paneWidth,
				itemBoxXPadding, itemBoxYPadding, columns, rows, sortIconSize, sortStride,
				sortIconTop);
	}

	/** The pane's width: a third of the screen, never degenerate. */
	public static int paneWidth(int screenWidth) {
		int third = screenWidth / 3;
		int minPane = 2 * minimumPadding() + 2 * MIN_ITEM_BOX_PADDING + (MIN_COLUMNS - 1) * STRIDE
				+ ITEM_SIZE;
		return Math.min(Math.max(third, minPane), Math.max(minPane, screenWidth));
	}

	/** The padding floor for the minimum-pane computation. */
	private static int minimumPadding() {
		return 5;
	}

	/** The item-box padding floor for the minimum-pane computation. */
	private static final int MIN_ITEM_BOX_PADDING = 0;

	/**
	 * The pane's left edge for an open-offset factor — NEU's
	 * {@code (int) (width * getItemPaneOffsetFactor())}. The animation
	 * supplies the factor; the fully-open pane rests at {@link #OPEN_OFFSET}.
	 */
	public static int paneLeft(int screenWidth, float offsetFactor) {
		return (int) (screenWidth * offsetFactor);
	}

	// ------------------------------------------------------------------
	// Pane-anchored boxes (functions of the animated pane left edge)
	// ------------------------------------------------------------------

	/** The grid's left edge — NEU's {@code xStart}. */
	public static int gridLeft(Metrics m, int paneLeft) {
		return paneLeft + m.boxPadding + m.itemBoxXPadding;
	}

	/** The grid's right bound — NEU's {@code xEnd}. */
	public static int gridRight(Metrics m, int paneLeft) {
		return gridLeft(m, paneLeft) + m.paneWidth - 2 * m.boxPadding - ITEM_SIZE
				- m.itemBoxXPadding;
	}

	/** The grid's top edge — NEU's {@code yStart}, under the nav strip. */
	public static int gridTop(Metrics m) {
		return m.boxPadding + m.searchBarYSize + m.itemBoxYPadding;
	}

	/** The grid's bottom bound — NEU's {@code yEnd}, above the sort bar. */
	public static int gridBottom(Metrics m) {
		return m.screenHeight - m.boxPadding - ITEM_SIZE - 2 - m.itemBoxYPadding;
	}

	/** The left of a grid tile in the given column. */
	public static int cellLeft(int gridLeft, int column) {
		return gridLeft + column * STRIDE;
	}

	/** The top of a grid tile in the given row. */
	public static int cellTop(int gridTop, int row) {
		return gridTop + row * STRIDE;
	}

	/** The page count for a result count — NEU's {@code getMaxPages}. */
	public static int maxPages(int resultCount, Metrics m) {
		int slots = m.slotsPerPage();
		if (resultCount <= 0 || slots <= 0) {
			return 1;
		}
		return (resultCount + slots - 1) / slots;
	}

	/** The page clamped onto the reachable range, NEU's render-time clamp. */
	public static int clampPage(int page, int resultCount, Metrics m) {
		return Math.max(0, Math.min(page, maxPages(resultCount, m) - 1));
	}

	/**
	 * The grid index a point sits on, or -1. The hit box is NEU's: the
	 * item's 16 px plus a one-pixel gutter on every side, so a click in
	 * the stride's middle gap hits nothing.
	 */
	public static int indexAt(int px, int py, Metrics m, int paneLeft, int page, int visibleCount) {
		int gridLeft = gridLeft(m, paneLeft);
		int gridTop = gridTop(m);
		if (px < gridLeft - 1 || py < gridTop - 1) {
			return -1;
		}
		int column = (px - gridLeft + 1) / STRIDE;
		int row = (py - gridTop + 1) / STRIDE;
		if (column >= m.columns || row >= m.rows) {
			return -1;
		}
		int x = cellLeft(gridLeft, column);
		int y = cellTop(gridTop, row);
		if (px > x + ITEM_SIZE + 1 || py > y + ITEM_SIZE + 1) {
			return -1;
		}
		int index = page * m.slotsPerPage() + row * m.columns + column;
		return index >= 0 && index < visibleCount ? index : -1;
	}

	// ------------------------------------------------------------------
	// Nav element — NEU's renderNavElement math
	// ------------------------------------------------------------------

	/**
	 * The nav element's boxes — NEU's {@code renderNavElement}:
	 * {@code {x, y, w, h, kind}} with kind 0 Prev, 1 the page pill,
	 * 2 Next. The button size derives from the search bar's height,
	 * capped so both buttons and the pill fit between the grid edges.
	 */
	public static List<int[]> navButtons(Metrics m, int paneLeft, int maxPages, FontMetrics fontMetrics) {
		int left = gridLeft(m, paneLeft);
		int right = gridRight(m, paneLeft);
		String reserve = "Page: " + maxPages + "/" + maxPages;
		float maxStrLen = fontMetrics.boldWidth(reserve);
		float maxButtonXSize = (right - left + 2 - maxStrLen * 0.5f - 10) / 2f;
		int buttonXSize = (int) Math.min(maxButtonXSize, m.searchBarYSize * 480 / 160f);
		int ySize = (int) (buttonXSize / 480f * 160);
		int yOffset = (int) ((m.searchBarYSize - ySize) / 2f);
		int top = m.boxPadding + yOffset;
		int navLeft = left - 1;
		int navRight = right + 1;
		return List.of(
				new int[] {navLeft, top, buttonXSize, ySize, 0},
				new int[] {navLeft + buttonXSize + 3, top,
						Math.max(8, navRight - buttonXSize - 3 - (navLeft + buttonXSize + 3)),
						ySize, 1},
				new int[] {navRight - buttonXSize, top, buttonXSize, ySize, 2});
	}

	/** The bold-text widths the nav math needs — implemented by the caller. */
	public interface FontMetrics {
		float boldWidth(String text);
	}

	// ------------------------------------------------------------------
	// Sort bar
	// ------------------------------------------------------------------

	/** The sort bar's strip top — NEU's {@code height - boxPadding - ITEM_SIZE - 2}. */
	public static int sortBarTop(Metrics m) {
		return m.screenHeight - m.boxPadding - ITEM_SIZE - 2;
	}

	/**
	 * The sort bar's order icons, anchored from the bar's left —
	 * {@code {x, y, size, kind}} per icon.
	 */
	public static List<int[]> orderIcons(Metrics m, int paneLeft, int iconCount) {
		int left = gridLeft(m, paneLeft);
		int top = m.sortIconTop;
		var icons = new int[iconCount][];
		for (int i = 0; i < iconCount; i++) {
			icons[i] = new int[] {left + i * m.sortStride, top, m.sortIconSize, i};
		}
		return List.of(icons);
	}

	/**
	 * The sort bar's filter icons, anchored from the bar's right —
	 * NEU's {@code sortIconX = rightSide - scaledITEM_SIZE - i * scaledItemPaddedSize}.
	 */
	public static List<int[]> filterIcons(Metrics m, int paneLeft, int iconCount) {
		int right = gridRight(m, paneLeft);
		int top = m.sortIconTop;
		var icons = new int[iconCount][];
		for (int i = 0; i < iconCount; i++) {
			icons[i] = new int[] {right - m.sortIconSize - i * m.sortStride, top, m.sortIconSize, i};
		}
		return List.of(icons);
	}

	// ------------------------------------------------------------------
	// Edge tab
	// ------------------------------------------------------------------

	/**
	 * The edge tab's texture box — NEU's 64 px arrow at
	 * {@code width - tabOffset * 64 / 20}, vertically centered. The
	 * offset animates 20 (closed, the tab visible at the edge) to 0
	 * (open, the tab slid off the screen).
	 */
	public static int[] tabBox(int screenWidth, int screenHeight, int tabOffset) {
		return new int[] {(int) (screenWidth - tabOffset * TAB_TEXTURE_SIZE / 20f),
				screenHeight / 2 - TAB_TEXTURE_SIZE / 2,
				TAB_TEXTURE_SIZE, TAB_TEXTURE_SIZE};
	}

	/** The tab's hover region — NEU's {@code mouseX > width - tabOffset}. */
	public static boolean tabHover(int screenWidth, int tabOffset, int px, int py, int screenHeight) {
		return px > screenWidth - tabOffset && py > screenHeight / 2 - TAB_TEXTURE_SIZE / 2
				&& py < screenHeight / 2 + TAB_TEXTURE_SIZE / 2;
	}

	// ------------------------------------------------------------------
	// Info pane (the detail view, NEU's left pane)
	// ------------------------------------------------------------------

	/**
	 * The info pane's left edge — NEU's
	 * {@code rightSide = width * getInfoPaneOffsetFactor(); leftSide = rightSide - paneWidth}.
	 */
	public static int infoPaneLeft(Metrics m, float infoOffsetFactor) {
		return (int) (m.screenWidth * infoOffsetFactor) - m.paneWidth;
	}

	/** The info pane's right edge. */
	public static int infoPaneRight(Metrics m, float infoOffsetFactor) {
		return (int) (m.screenWidth * infoOffsetFactor);
	}

	/**
	 * The info pane's close button — NEU's 16 px {@code close} texture
	 * at the pane's top-right corner.
	 */
	public static int[] infoCloseButton(Metrics m, float infoOffsetFactor) {
		return new int[] {infoPaneRight(m, infoOffsetFactor) - m.boxPadding - 8,
				m.boxPadding - 8, 16, 16};
	}

	// ------------------------------------------------------------------
	// Points and rectangles
	// ------------------------------------------------------------------

	/** Whether a point sits inside a rectangle. */
	public static boolean contains(int x, int y, int w, int h, int px, int py) {
		return px >= x && px < x + w && py >= y && py < y + h;
	}

	/**
	 * Whether a point sits inside the pane's full rectangle — NEU's
	 * {@code mouseX > width * getItemPaneOffsetFactor()} gate that
	 * consumes every click over the pane.
	 */
	public static boolean inPane(float offsetFactor, Metrics m, int px, int py) {
		return px > (int) (m.screenWidth * offsetFactor);
	}
}
