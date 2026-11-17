package net.zaminmc.zea.browser;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The item browser's layout math, pinned to the values NEU's own
 * formulas produce — computed by hand from {@code NEUOverlay}: the box
 * padding ({@code panePadding * 2 / scaleFactor + 5}), the nav strip's
 * height ({@code max(searchBarHeight / scaleFactor, 16)}), the
 * centering paddings splitting the leftovers, the ceiling-based column
 * and row counts, the sort bar's narrowing scale, the nav buttons'
 * search-bar-derived size and the edge tab's slide. Every expectation
 * below was traced through NEU's code paths at this source tree, so a
 * drift in ZEA's math against NEU's model fails here first.
 */
class BrowserGeometryTest {
	/** A small window at scale 1 — NEU's low-end case. */
	private static final BrowserGeometry.Metrics SMALL =
			BrowserGeometry.metrics(480, 320, 1, 3, 3);
	/** The typical desktop window at scale 2. */
	private static final BrowserGeometry.Metrics TYPICAL =
			BrowserGeometry.metrics(1280, 800, 2, 3, 3);
	/** A large window at scale 3. */
	private static final BrowserGeometry.Metrics LARGE =
			BrowserGeometry.metrics(1920, 1080, 3, 3, 3);

	@Test
	void paneTakesAThirdOfTheScreen() {
		assertEquals(160, BrowserGeometry.paneWidth(480));
		assertEquals(426, BrowserGeometry.paneWidth(1280));
		assertEquals(640, BrowserGeometry.paneWidth(1920));
	}

	@Test
	void paneLeftFollowsTheAnimatedOffset() {
		// Closed (factor 1) the pane sits off the screen's right edge.
		assertEquals(1280, BrowserGeometry.paneLeft(1280, 1f));
		// Open (NEU's 2/3) the pane's left edge rests at two-thirds.
		assertEquals((int) (1280 * 2f / 3f), BrowserGeometry.paneLeft(1280, BrowserGeometry.OPEN_OFFSET));
	}

	@Test
	void boxPaddingFollowsNeusScaleFormula() {
		// NEU: (int) (panePadding * 2 / scaleFactor + 5), panePadding 10.
		assertEquals(25, SMALL.boxPadding);
		assertEquals(15, TYPICAL.boxPadding);
		assertEquals(11, LARGE.boxPadding);
	}

	@Test
	void navStripHeightFollowsNeusSearchBarSize() {
		// NEU: max(searchBarHeight / scaleFactor, ITEM_SIZE), default 40.
		assertEquals(40, SMALL.searchBarYSize);
		assertEquals(20, TYPICAL.searchBarYSize);
		assertEquals(16, LARGE.searchBarYSize);
	}

	@Test
	void gridCenteringMatchesNeusItemBoxPadding() {
		// NEU: ((paneWidth - 2*box) % stride + padding) / 2.
		assertEquals(7, SMALL.itemBoxXPadding);
		assertEquals(10, TYPICAL.itemBoxXPadding);
		assertEquals(11, LARGE.itemBoxXPadding);
		// NEU: ((height - searchBar - 2*box - item - 2) % stride + padding) / 2.
		assertEquals(8, SMALL.itemBoxYPadding);
		assertEquals(8, TYPICAL.itemBoxYPadding);
		assertEquals(4, LARGE.itemBoxYPadding);
	}

	@Test
	void columnsAndRowsAreNeusCeilingCounts() {
		// xEnd - xStart over the stride, ceiling.
		assertEquals(5, SMALL.columns);
		assertEquals(19, TYPICAL.columns);
		assertEquals(30, LARGE.columns);
		assertEquals(10, SMALL.rows);
		assertEquals(36, TYPICAL.rows);
		assertEquals(51, LARGE.rows);
		assertEquals(50, SMALL.slotsPerPage());
		assertEquals(684, TYPICAL.slotsPerPage());
	}

	@Test
	void gridEdgesLandWhereNeusIterationStarts() {
		// xStart = paneLeft + box + ixp; the typical pane open at 2/3.
		int paneLeft = BrowserGeometry.paneLeft(1280, BrowserGeometry.OPEN_OFFSET);
		int gridLeft = BrowserGeometry.gridLeft(TYPICAL, paneLeft);
		assertEquals(paneLeft + 15 + 10, gridLeft);
		int gridRight = BrowserGeometry.gridRight(TYPICAL, paneLeft);
		assertEquals(gridLeft + TYPICAL.paneWidth - 2 * 15 - 16 - 10, gridRight);
		// yStart = box + searchBar + iyp; yEnd = height - box - item - 2 - iyp.
		assertEquals(15 + 20 + 8, BrowserGeometry.gridTop(TYPICAL));
		assertEquals(800 - 15 - 16 - 2 - 8, BrowserGeometry.gridBottom(TYPICAL));
	}

	@Test
	void sortBarScalesDownOnNarrowPanes() {
		// NEU: min(1, availableX / ((orders + filters) * 20 + 16)) * ITEM_SIZE.
		assertEquals(11, SMALL.sortIconSize);
		assertEquals(13, SMALL.sortStride);
		assertEquals(320 - 25 - (16 + 11) / 2 - 1, SMALL.sortIconTop);
		// The typical pane fits all six icons at full size.
		assertEquals(16, TYPICAL.sortIconSize);
		assertEquals(20, TYPICAL.sortStride);
		assertEquals(800 - 15 - 16 - 1, TYPICAL.sortIconTop);
	}

	@Test
	void maxPagesIsNeusCeilingOverThePageCapacity() {
		assertEquals(1, BrowserGeometry.maxPages(0, TYPICAL));
		assertEquals(1, BrowserGeometry.maxPages(684, TYPICAL));
		assertEquals(2, BrowserGeometry.maxPages(685, TYPICAL));
		assertEquals(0, BrowserGeometry.clampPage(5, 10, TYPICAL));
		assertEquals(1, BrowserGeometry.clampPage(1, 685, TYPICAL));
	}

	@Test
	void tileHitBoxIsTheItemPlusItsGutter() {
		int paneLeft = BrowserGeometry.paneLeft(1280, BrowserGeometry.OPEN_OFFSET);
		int gridLeft = BrowserGeometry.gridLeft(TYPICAL, paneLeft);
		int gridTop = BrowserGeometry.gridTop(TYPICAL);
		// The first tile's own box, one pixel inside its gutter.
		assertEquals(0, BrowserGeometry.indexAt(gridLeft + 1, gridTop + 1, TYPICAL, paneLeft, 0, 100));
		// The second column's tile.
		assertEquals(1, BrowserGeometry.indexAt(gridLeft + 21, gridTop + 1, TYPICAL, paneLeft, 0, 100));
		// The single-pixel gutter between the hit boxes lands
		// nowhere — NEU's rule: cell 0 spans [left-1, left+17], cell
		// 1 starts at left+19, so only left+18 falls between.
		assertEquals(-1, BrowserGeometry.indexAt(gridLeft + 18, gridTop + 1, TYPICAL, paneLeft, 0, 100));
		assertEquals(1, BrowserGeometry.indexAt(gridLeft + 19, gridTop + 1, TYPICAL, paneLeft, 0, 100));
		// Past the last column, nothing.
		assertEquals(-1, BrowserGeometry.indexAt(
				gridLeft + TYPICAL.columns * 20 + 4, gridTop + 1, TYPICAL, paneLeft, 0, 100));
	}

	@Test
	void navButtonsDeriveFromTheSearchBarHeight() {
		// NEU: buttonXSize = min(maxButtonXSize, searchBarYSize * 3),
		// ySize = buttonXSize / 3; the font widths come from a fake
		// so the numbers stay deterministic.
		int paneLeft = BrowserGeometry.paneLeft(1280, BrowserGeometry.OPEN_OFFSET);
		List<int[]> buttons = BrowserGeometry.navButtons(TYPICAL, paneLeft, 12,
				text -> text.length() * 6f);
		int[] prev = buttons.get(0);
		int[] pill = buttons.get(1);
		int[] next = buttons.get(2);
		assertEquals(60, prev[2]);
		assertEquals(20, prev[3]);
		int gridLeft = BrowserGeometry.gridLeft(TYPICAL, paneLeft);
		int gridRight = BrowserGeometry.gridRight(TYPICAL, paneLeft);
		assertEquals(gridLeft - 1, prev[0]);
		assertEquals(15, prev[1]);
		assertEquals(gridRight + 1 - 60, next[0]);
		assertEquals(gridLeft - 1 + 60 + 3, pill[0]);
		assertEquals(gridRight + 1 - 60 - 3, pill[0] + pill[2]);
	}

	@Test
	void sortIconsAnchorLeftAndRight() {
		List<int[]> orders = BrowserGeometry.orderIcons(TYPICAL, BrowserGeometry.paneLeft(1280,
				BrowserGeometry.OPEN_OFFSET), 3);
		List<int[]> filters = BrowserGeometry.filterIcons(TYPICAL, BrowserGeometry.paneLeft(1280,
				BrowserGeometry.OPEN_OFFSET), 3);
		int gridLeft = BrowserGeometry.gridLeft(TYPICAL, BrowserGeometry.paneLeft(1280,
				BrowserGeometry.OPEN_OFFSET));
		int gridRight = BrowserGeometry.gridRight(TYPICAL, BrowserGeometry.paneLeft(1280,
				BrowserGeometry.OPEN_OFFSET));
		assertEquals(gridLeft, orders.get(0)[0]);
		assertEquals(gridLeft + 20, orders.get(1)[0]);
		assertEquals(gridLeft + 40, orders.get(2)[0]);
		assertEquals(gridRight - 16, filters.get(0)[0]);
		assertEquals(gridRight - 36, filters.get(1)[0]);
		assertEquals(gridRight - 56, filters.get(2)[0]);
		assertEquals(TYPICAL.sortIconTop, orders.get(0)[1]);
	}

	@Test
	void edgeTabSlidesWithNeusOffset() {
		// Closed: the 64 px arrow fully visible at the screen's edge.
		assertEquals(1216, BrowserGeometry.tabBox(1280, 800, 20)[0]);
		assertEquals(368, BrowserGeometry.tabBox(1280, 800, 20)[1]);
		// Open: slid off the screen.
		assertEquals(1280, BrowserGeometry.tabBox(1280, 800, 0)[0]);
		// The hover region is only the protruding strip — NEU's rule.
		assertTrue(BrowserGeometry.tabHover(1280, 20, 1270, 400, 800));
		assertFalse(BrowserGeometry.tabHover(1280, 20, 1250, 400, 800));
		assertFalse(BrowserGeometry.tabHover(1280, 20, 1270, 300, 800));
	}

	@Test
	void infoPaneAnchorsAtOneThirdWithNeusCloseButton() {
		float open = BrowserGeometry.INFO_OPEN_OFFSET;
		assertEquals((int) (1280 * open), BrowserGeometry.infoPaneRight(TYPICAL, open));
		assertEquals(0, BrowserGeometry.infoPaneLeft(TYPICAL, open));
		int[] close = BrowserGeometry.infoCloseButton(TYPICAL, open);
		assertEquals(426 - 15 - 8, close[0]);
		assertEquals(15 - 8, close[1]);
		assertEquals(16, close[2]);
	}

	@Test
	void paneRegionGateFollowsTheAnimatedEdge() {
		// NEU: clicks right of the moving edge are the pane's.
		assertTrue(BrowserGeometry.inPane(BrowserGeometry.OPEN_OFFSET, TYPICAL, 900, 400));
		assertFalse(BrowserGeometry.inPane(BrowserGeometry.OPEN_OFFSET, TYPICAL, 850, 400));
	}

	@Test
	void narrowScreensKeepAUsableMinimumPane() {
		int narrow = BrowserGeometry.paneWidth(90);
		BrowserGeometry.Metrics m = BrowserGeometry.metrics(90, 240, 2, 3, 3);
		assertTrue(m.columns >= BrowserGeometry.MIN_COLUMNS,
				"a 90px screen still carries the minimum grid: " + m.columns);
		assertEquals(90, BrowserGeometry.paneLeft(90, 1f) - narrow + narrow);
	}
}
