package net.zaminmc.zea.browser;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The item browser's layout math, pinned to the values NEU's own
 * formulas produce. The expectations were computed by hand from
 * {@code NEUOverlay} — a third of the screen for the pane's width, the
 * centering paddings splitting the leftovers, and the ceiling-based
 * column and row counts — so a drift in ZEA's math against NEU's model
 * fails here first.
 */
class BrowserGeometryTest {
        /** A scale-3 1080p screen: NEU's own example dimensions. */
        private static final int SCALE3_WIDTH = 640;
        private static final int SCALE3_HEIGHT = 360;

        @Test
        void paneTakesAThirdOfTheScreenAnchoredRight() {
                assertEquals(213, BrowserGeometry.paneWidth(SCALE3_WIDTH));
                assertEquals(427, BrowserGeometry.paneLeft(SCALE3_WIDTH));
                assertEquals(320, BrowserGeometry.paneWidth(960));
                assertEquals(640, BrowserGeometry.paneLeft(960));
        }

        @Test
        void paneSpansTheScreenFullHeight() {
                assertEquals(BrowserGeometry.BOX_PADDING, BrowserGeometry.paneTop());
                assertEquals(SCALE3_HEIGHT - BrowserGeometry.BOX_PADDING,
                                BrowserGeometry.paneBottom(SCALE3_HEIGHT));
                assertEquals(SCALE3_HEIGHT - 2 * BrowserGeometry.BOX_PADDING,
                                BrowserGeometry.paneHeight(SCALE3_HEIGHT));
        }

        @Test
        void narrowScreensKeepAUsableMinimumPane() {
                int narrow = 90;
                int paneWidth = BrowserGeometry.paneWidth(narrow);
                int columns = BrowserGeometry.columns(paneWidth);
                assertTrue(columns >= BrowserGeometry.MIN_COLUMNS,
                                "a 90px screen still carries the minimum grid: " + columns);
                assertEquals(narrow, BrowserGeometry.paneLeft(narrow) + paneWidth);
        }

        @Test
        void gridCenteringMatchesNeusItemBoxPadding() {
                // NEU: ((paneWidth - 2*box) % stride + padding) / 2 — 213 gives 3.
                assertEquals(3, BrowserGeometry.itemBoxXPadding(213));
                assertEquals(7, BrowserGeometry.itemBoxXPadding(320));
                // NEU: ((height - nav - 2*box - item - 2) % stride + padding) / 2 — 360 gives 10.
                assertEquals(10, BrowserGeometry.itemBoxYPadding(SCALE3_HEIGHT));
        }

        @Test
        void gridEdgesLandInsideThePane() {
                int paneWidth = BrowserGeometry.paneWidth(SCALE3_WIDTH);
                int paneLeft = BrowserGeometry.paneLeft(SCALE3_WIDTH);
                int gridLeft = BrowserGeometry.gridLeft(paneLeft, paneWidth);
                int gridTop = BrowserGeometry.gridTop(SCALE3_HEIGHT);
                int gridBottom = BrowserGeometry.gridBottom(SCALE3_HEIGHT);
                assertEquals(paneLeft + BrowserGeometry.BOX_PADDING + 3, gridLeft);
                assertEquals(BrowserGeometry.BOX_PADDING + BrowserGeometry.NAV_HEIGHT + 10, gridTop);
                assertTrue(gridTop < gridBottom, "the grid's top edge sits above its bottom edge");
                // NEU: yEnd = height - box - item - 2 - itemBoxYPadding.
                assertEquals(SCALE3_HEIGHT - BrowserGeometry.BOX_PADDING - BrowserGeometry.ITEM_SIZE - 2 - 10,
                                gridBottom);
        }

        @Test
        void gridDensityMatchesNeusCounts() {
                // Scale-3 1080p: span 181 → 10 columns; rows span 296 → 15 rows.
                assertEquals(10, BrowserGeometry.columns(BrowserGeometry.paneWidth(SCALE3_WIDTH)));
                assertEquals(15, BrowserGeometry.rows(SCALE3_HEIGHT));
                assertEquals(150, BrowserGeometry.slotsPerPage(
                                BrowserGeometry.paneWidth(SCALE3_WIDTH), SCALE3_HEIGHT));
                // Scale-2 1080p: 14 columns, 24 rows.
                assertEquals(14, BrowserGeometry.columns(BrowserGeometry.paneWidth(960)));
                assertEquals(24, BrowserGeometry.rows(540));
        }

        @Test
        void pageCountFollowsNeusMaxPages() {
                int paneWidth = BrowserGeometry.paneWidth(SCALE3_WIDTH);
                assertEquals(1, BrowserGeometry.maxPages(0, paneWidth, SCALE3_HEIGHT));
                assertEquals(1, BrowserGeometry.maxPages(150, paneWidth, SCALE3_HEIGHT));
                assertEquals(2, BrowserGeometry.maxPages(151, paneWidth, SCALE3_HEIGHT));
                assertEquals(2, BrowserGeometry.maxPages(300, paneWidth, SCALE3_HEIGHT));
                assertEquals(3, BrowserGeometry.maxPages(301, paneWidth, SCALE3_HEIGHT));
        }

        @Test
        void pageClampsOntoTheReachableRange() {
                int paneWidth = BrowserGeometry.paneWidth(SCALE3_WIDTH);
                assertEquals(0, BrowserGeometry.clampPage(0, 10, paneWidth, SCALE3_HEIGHT));
                assertEquals(0, BrowserGeometry.clampPage(5, 10, paneWidth, SCALE3_HEIGHT));
                assertEquals(1, BrowserGeometry.clampPage(4, 201, paneWidth, SCALE3_HEIGHT));
                assertEquals(2, BrowserGeometry.clampPage(4, 301, paneWidth, SCALE3_HEIGHT));
                assertEquals(0, BrowserGeometry.clampPage(-1, 301, paneWidth, SCALE3_HEIGHT));
        }

        @Test
        void tileHitBoxIsTheItemPlusItsGutter() {
                int paneWidth = BrowserGeometry.paneWidth(SCALE3_WIDTH);
                int paneLeft = BrowserGeometry.paneLeft(SCALE3_WIDTH);
                int gridLeft = BrowserGeometry.gridLeft(paneLeft, paneWidth);
                int gridTop = BrowserGeometry.gridTop(SCALE3_HEIGHT);
                int columns = BrowserGeometry.columns(paneWidth);
                // The first tile's center hits index 0 on page zero.
                int centerX = gridLeft + BrowserGeometry.ITEM_SIZE / 2;
                int centerY = gridTop + BrowserGeometry.ITEM_SIZE / 2;
                assertEquals(0, BrowserGeometry.indexAt(centerX, centerY, paneLeft, paneWidth,
                                SCALE3_HEIGHT, 0, 150));
                // The one-pixel gutter around the tile still hits it — NEU's
                // `mouseX >= x - 1 && mouseX <= x + ITEM_SIZE + 1`.
                assertEquals(0, BrowserGeometry.indexAt(gridLeft - 1, gridTop - 1, paneLeft, paneWidth,
                                SCALE3_HEIGHT, 0, 150));
                assertEquals(0, BrowserGeometry.indexAt(gridLeft + BrowserGeometry.ITEM_SIZE + 1,
                                gridTop + BrowserGeometry.ITEM_SIZE + 1, paneLeft, paneWidth, SCALE3_HEIGHT, 0, 150));
                // The stride's middle gap hits nothing.
                int gapX = gridLeft + BrowserGeometry.ITEM_SIZE + 2;
                assertEquals(-1, BrowserGeometry.indexAt(gapX, centerY, paneLeft, paneWidth,
                                SCALE3_HEIGHT, 0, 150));
                // The second column's center hits index 1.
                int secondX = gridLeft + BrowserGeometry.STRIDE + BrowserGeometry.ITEM_SIZE / 2;
                assertEquals(1, BrowserGeometry.indexAt(secondX, centerY, paneLeft, paneWidth,
                                SCALE3_HEIGHT, 0, 150));
                // Past the visible result count hits nothing.
                assertEquals(-1, BrowserGeometry.indexAt(centerX, centerY, paneLeft, paneWidth,
                                SCALE3_HEIGHT, 0, 0));
                // A tile on page one carries its page offset — one full page of slots.
                int pageOne = BrowserGeometry.indexAt(centerX, centerY, paneLeft, paneWidth,
                                SCALE3_HEIGHT, 1, 150 + columns);
                assertEquals(BrowserGeometry.slotsPerPage(paneWidth, SCALE3_HEIGHT), pageOne);
        }

        @Test
        void navButtonsSpanThePaneTopWithoutOverlapping() {
                int paneWidth = BrowserGeometry.paneWidth(SCALE3_WIDTH);
                int paneLeft = BrowserGeometry.paneLeft(SCALE3_WIDTH);
                List<int[]> buttons = BrowserGeometry.navButtons(paneLeft, paneWidth, SCALE3_HEIGHT);
                assertEquals(3, buttons.size());
                int[] prev = buttons.get(0);
                int[] pill = buttons.get(1);
                int[] next = buttons.get(2);
                assertEquals(0, prev[4]);
                assertEquals(1, pill[4]);
                assertEquals(2, next[4]);
                assertTrue(prev[0] + prev[2] <= pill[0], "Prev ends before the pill starts");
                assertTrue(pill[0] + pill[2] <= next[0], "the pill ends before Next starts");
                assertEquals(BrowserGeometry.BOX_PADDING, prev[1]);
                assertEquals(BrowserGeometry.NAV_HEIGHT, prev[3]);
                assertEquals(BrowserGeometry.NAV_HEIGHT, pill[3]);
        }

        @Test
        void closeButtonSitsRightOfTheNav() {
                int paneWidth = BrowserGeometry.paneWidth(SCALE3_WIDTH);
                int paneLeft = BrowserGeometry.paneLeft(SCALE3_WIDTH);
                List<int[]> buttons = BrowserGeometry.navButtons(paneLeft, paneWidth, SCALE3_HEIGHT);
                int[] next = buttons.get(2);
                int[] close = BrowserGeometry.closeButton(paneLeft, paneWidth, SCALE3_HEIGHT);
                assertTrue(close[0] >= next[0] + next[2], "the close button sits right of Next");
        }

        @Test
        void filterIconsAnchorToTheBarsRightEdge() {
                int paneWidth = BrowserGeometry.paneWidth(SCALE3_WIDTH);
                int paneLeft = BrowserGeometry.paneLeft(SCALE3_WIDTH);
                List<int[]> filters = BrowserGeometry.filterIcons(paneLeft, paneWidth, SCALE3_HEIGHT, 3);
                assertEquals(3, filters.size());
                int gridLeft = BrowserGeometry.gridLeft(paneLeft, paneWidth);
                int barRight = gridLeft + BrowserGeometry.columns(paneWidth) * BrowserGeometry.STRIDE
                                - BrowserGeometry.ITEM_PADDING;
                int[] rightmost = filters.get(0);
                assertEquals(barRight, rightmost[0] + rightmost[2],
                                "the first filter icon's right edge is the bar's right edge");
                // NEU's category icons flow right to left: each next icon sits one
                // stride to the left of the one before it.
                int[] middle = filters.get(1);
                int[] leftmost = filters.get(2);
                assertEquals(rightmost[0] - BrowserGeometry.STRIDE, middle[0]);
                assertEquals(middle[0] - BrowserGeometry.STRIDE, leftmost[0]);
                // The order icons anchor to the bar's left edge instead.
                List<int[]> orders = BrowserGeometry.orderIcons(paneLeft, paneWidth, SCALE3_HEIGHT, 3);
                assertEquals(gridLeft, orders.get(0)[0], "the first order icon's left edge is the bar's left edge");
        }

        @Test
        void sortIconsShrinkWhenThePaneIsNarrow() {
                int paneWidth = BrowserGeometry.paneWidth(SCALE3_WIDTH);
                assertEquals(BrowserGeometry.ITEM_SIZE,
                                BrowserGeometry.sortIconSize(paneWidth, ItemBrowser.SORT_ICON_COUNT),
                                "a full-size pane keeps the icons at full size");
                int tinyPane = 40;
                assertTrue(BrowserGeometry.sortIconSize(tinyPane, ItemBrowser.SORT_ICON_COUNT)
                                < BrowserGeometry.ITEM_SIZE, "a tiny pane shrinks the icons");
        }

        @Test
        void reopenTabSitsAtTheScreensRightEdgeVerticallyCentered() {
                int[] tab = BrowserGeometry.tabBox(SCALE3_WIDTH, SCALE3_HEIGHT);
                assertEquals(SCALE3_WIDTH - BrowserGeometry.TAB_WIDTH, tab[0]);
                assertEquals(SCALE3_HEIGHT / 2 - BrowserGeometry.TAB_HEIGHT / 2, tab[1]);
                assertEquals(BrowserGeometry.TAB_WIDTH, tab[2]);
                assertEquals(BrowserGeometry.TAB_HEIGHT, tab[3]);
        }

        @Test
        void pointContainmentCoversTheRectangles() {
                assertTrue(BrowserGeometry.contains(10, 10, 5, 5, 10, 10));
                assertTrue(BrowserGeometry.contains(10, 10, 5, 5, 14, 14));
                assertFalse(BrowserGeometry.contains(10, 10, 5, 5, 15, 10));
                assertFalse(BrowserGeometry.contains(10, 10, 5, 5, 10, 15));
                int paneWidth = BrowserGeometry.paneWidth(SCALE3_WIDTH);
                int paneLeft = BrowserGeometry.paneLeft(SCALE3_WIDTH);
                assertTrue(BrowserGeometry.inPane(paneLeft, paneWidth, SCALE3_HEIGHT,
                                paneLeft + paneWidth / 2, BrowserGeometry.paneTop() + 20));
                assertFalse(BrowserGeometry.inPane(paneLeft, paneWidth, SCALE3_HEIGHT, paneLeft - 1, 50));
                assertFalse(BrowserGeometry.inPane(paneLeft, paneWidth, SCALE3_HEIGHT, 10, 50));
        }
}
