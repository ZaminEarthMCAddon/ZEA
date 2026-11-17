package net.zaminmc.zea.browser;

import java.util.List;

/**
 * The pure layout math of the inventory shop-index browser — the one
 * place that answers where the panel sits and where its cells land, so
 * rendering and hit-testing can never drift apart. The panel anchors to
 * the container GUI's right edge like NEU's item list: it never covers
 * the inventory itself while there is room for it, and it slides over
 * the GUI's edge only when a narrow screen leaves none.
 */
public final class BrowserGeometry {
	/** A tile cell's edge — the 16px item plus its one-pixel gutter. */
	public static final int CELL = 18;

	/** The panel's inner padding. */
	public static final int PADDING = 3;

	/** The header strip that carries the title, the page buttons and the close. */
	public static final int HEADER = 16;

	/** The minimum columns a panel keeps; narrower screens get the overlap path. */
	public static final int MIN_COLUMNS = 4;

	/** The maximum columns a wide screen may show. */
	public static final int MAX_COLUMNS = 9;

	private BrowserGeometry() {
	}

	/** The panel's columns for the room right of the GUI. */
	public static int columns(int guiRight, int screenWidth) {
		int room = screenWidth - guiRight - PADDING * 2;
		int byRoom = room / CELL;
		int byMax = MAX_COLUMNS;
		if (byRoom >= MIN_COLUMNS) {
			return Math.min(byRoom, byMax);
		}
		// No room: the panel overlaps the GUI's right edge, keeping at
		// least the minimum columns.
		return MIN_COLUMNS;
	}

	/** The panel's left edge, clamped so it always stays on screen. */
	public static int left(int guiRight, int screenWidth, int columns) {
		int preferred = guiRight + 4;
		int width = width(columns);
		if (preferred + width > screenWidth) {
			return Math.max(0, screenWidth - width);
		}
		return preferred;
	}

	/** The panel's width: the columns plus padding, capped by the screen. */
	public static int width(int columns) {
		return columns * CELL + PADDING * 2;
	}

	/** The panel's height: the GUI's height, clamped onto the screen. */
	public static int height(int guiTop, int guiHeight, int screenHeight) {
		int top = Math.max(0, Math.min(guiTop, screenHeight - HEADER));
		return Math.min(guiHeight, screenHeight - top);
	}

	/** The panel's top edge, clamped onto the screen. */
	public static int top(int guiTop, int screenHeight) {
		return Math.max(0, Math.min(guiTop, Math.max(0, screenHeight - HEADER)));
	}

	/** The rows of tiles below the header. */
	public static int rows(int panelHeight) {
		return Math.max(0, (panelHeight - HEADER - PADDING * 2) / CELL);
	}

	/** The grid's top edge, below the header. */
	public static int gridTop(int panelTop) {
		return panelTop + HEADER + PADDING;
	}

	/** The grid's left edge, inside the padding. */
	public static int gridLeft(int panelLeft) {
		return panelLeft + PADDING;
	}

	/** The top of a cell in the grid, given the scroll offset in rows. */
	public static int cellTop(int gridTop, int row, int scrollRows) {
		return gridTop + (row - scrollRows) * CELL;
	}

	/** The left of a cell in the grid, column-major index. */
	public static int cellLeft(int gridLeft, int column) {
		return gridLeft + column * CELL;
	}

	/** The visible row range for the panel's height and the scroll. */
	public static int visibleRows(int panelHeight, int scrollRows, int totalRows) {
		return Math.min(rows(panelHeight), Math.max(0, totalRows - scrollRows));
	}

	/** The scroll clamped so the last page of rows is reachable and no further. */
	public static int clampScroll(int scrollRows, int totalRows, int panelHeight) {
		int pageRows = rows(panelHeight);
		int max = Math.max(0, totalRows - pageRows);
		return Math.max(0, Math.min(scrollRows, max));
	}

	/** The variant index a point in the panel sits on, or -1. */
	public static int indexAt(int px, int py, int panelLeft, int panelTop, int columns,
			int scrollRows, int visibleRowCount) {
		int gridLeft = gridLeft(panelLeft);
		int gridTop = gridTop(panelTop);
		if (px < gridLeft || py < gridTop) {
			return -1;
		}
		int column = (px - gridLeft) / CELL;
		if (column >= columns || px - gridLeft >= columns * CELL) {
			return -1;
		}
		int row = scrollRows + (py - gridTop) / CELL;
		int index = row * columns + column;
		return index < visibleRowCount ? index : -1;
	}

	/** Whether a point sits inside a rectangle. */
	public static boolean contains(int x, int y, int w, int h, int px, int py) {
		return px >= x && px < x + w && py >= y && py < y + h;
	}

	/** The header buttons' geometry: page arrows and the close, right-aligned. */
	public static List<int[]> headerButtons(int panelLeft, int panelTop, int panelWidth) {
		int right = panelLeft + panelWidth - PADDING;
		int y = panelTop + (HEADER - 10) / 2;
		// {x, y, w, h, kind} — kind 0 close, 1 page up, 2 page down.
		return List.of(
				new int[] {right - 10, y, 10, 10, 0},
				new int[] {right - 22, y, 10, 10, 1},
				new int[] {right - 34, y, 10, 10, 2});
	}
}
