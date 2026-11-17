package net.zaminmc.zea.browser;

import java.util.List;

/**
 * The pure layout math of the inventory item browser — the one place
 * that answers where the pane sits and where its cells land, so
 * rendering and hit-testing can never drift apart.
 *
 * <p>The layout is NEU's item-pane math, reproduced from
 * {@code NEUOverlay.iterateItemSlots} / {@code getSlotsXSize} /
 * {@code getSlotsYSize} / {@code renderNavElement} and adapted to the
 * modern API's scaled coordinate space: the pane anchors to the screen's
 * right edge and spans the screen's full height, the grid centers inside
 * the pane by splitting the leftover width in half, and a sort bar
 * occupies the pane's bottom strip. At a scale-3 1080p screen this is
 * NEU's nine-column, full-height grid; at other scales and resolutions
 * the same formulas keep the same density.
 *
 * <p>The pane's width is a third of the screen — NEU's default
 * {@code width / 3 * paneWidthMult(1.0)}; the grid's stride is 20 px
 * (a 16 px item plus 4 px padding, NEU's {@code ITEM_SIZE} +
 * {@code ITEM_PADDING}); the tile hit box is the item plus a one-pixel
 * gutter, NEU's {@code mouseX >= x - 1 && mouseX <= x + ITEM_SIZE + 1}.
 */
public final class BrowserGeometry {
        /** A tile's 16 px item icon — NEU's {@code ITEM_SIZE}. */
        public static final int ITEM_SIZE = 16;

        /** The gap between tiles — NEU's {@code ITEM_PADDING}. */
        public static final int ITEM_PADDING = 4;

        /** The stride of one grid column or row: item plus padding. */
        public static final int STRIDE = ITEM_SIZE + ITEM_PADDING;

        /** The pane's inset from the screen's edges — NEU's box padding. */
        public static final int BOX_PADDING = 5;

        /**
         * The pane's background bleeds this far past the content box, NEU's
         * {@code drawRect(left + box - 5, box - 5, ...)}.
         */
        public static final int BACKGROUND_BLEED = 5;

        /** The top strip the nav element occupies (NEU reserves the search bar's height). */
        public static final int NAV_HEIGHT = 16;

        /** The bottom strip the sort bar occupies: the icon plus its two-pixel frame. */
        public static final int SORT_BAR_HEIGHT = ITEM_SIZE + 2;

        /** The fewest columns a pane keeps; a screen this narrow still gets a usable grid. */
        public static final int MIN_COLUMNS = 3;

        /** The most columns a wide screen may show — a sanity bound NEU never reaches. */
        public static final int MAX_COLUMNS = 30;

        /** The nav element's button width: thrice its height, like NEU's 480×160 arrows. */
        public static final int NAV_BUTTON_WIDTH = NAV_HEIGHT * 3;

        /** The reopen tab's size, NEU's edge tab shrunken to a discreet strip. */
        public static final int TAB_WIDTH = 6;
        public static final int TAB_HEIGHT = 32;

        private BrowserGeometry() {
        }

        // ------------------------------------------------------------------
        // Pane
        // ------------------------------------------------------------------

        /** The pane's width: a third of the screen, never beyond it. */
        public static int paneWidth(int screenWidth) {
                int third = screenWidth / 3;
                int minPane = minPaneWidth();
                return Math.min(Math.max(third, minPane), Math.max(minPane, screenWidth));
        }

        /** The narrowest pane that still fits the minimum grid. */
        public static int minPaneWidth() {
                int width = minimumGridWidth();
                return 2 * BOX_PADDING + 2 * itemBoxXPadding(width) + width;
        }

        /** The minimum grid's width for the minimum column count. */
        private static int minimumGridWidth() {
                return (MIN_COLUMNS - 1) * STRIDE + ITEM_SIZE;
        }

        /** The pane's left edge: anchored to the screen's right — NEU's open offset 2/3. */
        public static int paneLeft(int screenWidth) {
                return Math.max(0, screenWidth - paneWidth(screenWidth));
        }

        /** The pane's content top — the box padding. */
        public static int paneTop() {
                return BOX_PADDING;
        }

        /** The pane's content bottom — the screen's height minus the box padding. */
        public static int paneBottom(int screenHeight) {
                return Math.max(paneTop() + NAV_HEIGHT + SORT_BAR_HEIGHT, screenHeight - BOX_PADDING);
        }

        /** The pane's content height. */
        public static int paneHeight(int screenHeight) {
                return paneBottom(screenHeight) - paneTop();
        }

        // ------------------------------------------------------------------
        // Grid
        // ------------------------------------------------------------------

        /**
         * The grid's horizontal centering — NEU's item-box X padding, which
         * splits the pane's leftover width in half so the grid centers in
         * the pane instead of hugging its left edge.
         */
        public static int itemBoxXPadding(int paneWidth) {
                return ((paneWidth - 2 * BOX_PADDING) % STRIDE + ITEM_PADDING) / 2;
        }

        /**
         * The grid's vertical centering — NEU's item-box Y padding, the same
         * split for the height left under the nav strip and above the sort bar.
         */
        public static int itemBoxYPadding(int screenHeight) {
                return ((screenHeight - NAV_HEIGHT - 2 * BOX_PADDING - ITEM_SIZE - 2) % STRIDE + ITEM_PADDING) / 2;
        }

        /** The grid's left edge: the pane's left, its box padding and its centering split. */
        public static int gridLeft(int paneLeft, int paneWidth) {
                return paneLeft + BOX_PADDING + itemBoxXPadding(paneWidth);
        }

        /** The grid's top edge: under the nav strip, past the vertical centering split. */
        public static int gridTop(int screenHeight) {
                return BOX_PADDING + NAV_HEIGHT + itemBoxYPadding(screenHeight);
        }

        /** The grid's bottom edge — NEU's {@code yEnd}: above the sort bar, past the split. */
        public static int gridBottom(int screenHeight) {
                return screenHeight - BOX_PADDING - ITEM_SIZE - 2 - itemBoxYPadding(screenHeight);
        }

        /**
         * The grid's columns — NEU's {@code getSlotsXSize}, the ceiling of
         * the span between the first and the last tile.
         */
        public static int columns(int paneWidth) {
                int span = paneWidth - 2 * BOX_PADDING - ITEM_SIZE - 2 * itemBoxXPadding(paneWidth);
                int columns = (int) Math.ceil(span / (float) STRIDE);
                return Math.max(MIN_COLUMNS, Math.min(MAX_COLUMNS, columns));
        }

        /** The grid's rows — NEU's {@code getSlotsYSize}. */
        public static int rows(int screenHeight) {
                int span = gridBottom(screenHeight) - gridTop(screenHeight);
                int rows = (int) Math.ceil(span / (float) STRIDE);
                return Math.max(1, rows);
        }

        /** The grid's tiles per page: the columns times the rows. */
        public static int slotsPerPage(int paneWidth, int screenHeight) {
                return columns(paneWidth) * rows(screenHeight);
        }

        /** The left of a grid tile in the given column. */
        public static int cellLeft(int gridLeft, int column) {
                return gridLeft + column * STRIDE;
        }

        /** The top of a grid tile in the given row. */
        public static int cellTop(int gridTop, int row) {
                return gridTop + row * STRIDE;
        }

        // ------------------------------------------------------------------
        // Pages
        // ------------------------------------------------------------------

        /** The page count for a result count — NEU's {@code getMaxPages}. */
        public static int maxPages(int resultCount, int paneWidth, int screenHeight) {
                int slots = slotsPerPage(paneWidth, screenHeight);
                if (resultCount <= 0 || slots <= 0) {
                        return 1;
                }
                return (resultCount + slots - 1) / slots;
        }

        /** The page clamped onto the reachable range, NEU's render-time clamp. */
        public static int clampPage(int page, int resultCount, int paneWidth, int screenHeight) {
                return Math.max(0, Math.min(page, maxPages(resultCount, paneWidth, screenHeight) - 1));
        }

        /**
         * The grid index a point sits on, or -1. The hit box is NEU's: the
         * item's 16 px plus a one-pixel gutter on every side, so a click in
         * the stride's middle gap hits nothing.
         */
        public static int indexAt(int px, int py, int paneLeft, int paneWidth, int screenHeight,
                        int page, int visibleCount) {
                int gridLeft = gridLeft(paneLeft, paneWidth);
                int gridTop = gridTop(screenHeight);
                if (px < gridLeft - 1 || py < gridTop - 1) {
                        return -1;
                }
                int columns = columns(paneWidth);
                int column = (px - gridLeft + 1) / STRIDE;
                int row = (py - gridTop + 1) / STRIDE;
                if (column >= columns || row >= rows(screenHeight)) {
                        return -1;
                }
                int x = cellLeft(gridLeft, column);
                int y = cellTop(gridTop, row);
                if (px > x + ITEM_SIZE + 1 || py > y + ITEM_SIZE + 1) {
                        return -1;
                }
                int index = page * slotsPerPage(paneWidth, screenHeight) + row * columns + column;
                return index >= 0 && index < visibleCount ? index : -1;
        }

        // ------------------------------------------------------------------
        // Nav element
        // ------------------------------------------------------------------

        /**
         * The nav element's three boxes — NEU's {@code renderNavElement}:
         * {@code {x, y, w, h, kind}} with kind 0 Prev, 1 the page pill,
         * 2 Next, laid across the pane's top strip.
         */
        public static List<int[]> navButtons(int paneLeft, int paneWidth, int screenHeight) {
                int left = gridLeft(paneLeft, paneWidth);
                int top = BOX_PADDING;
                int width = columns(paneWidth) * STRIDE - ITEM_PADDING;
                int pillWidth = width - 2 * NAV_BUTTON_WIDTH - 6;
                return List.of(
                                new int[] {left, top, NAV_BUTTON_WIDTH, NAV_HEIGHT, 0},
                                new int[] {left + NAV_BUTTON_WIDTH + 3, top, Math.max(20, pillWidth), NAV_HEIGHT, 1},
                                new int[] {left + width - NAV_BUTTON_WIDTH, top, NAV_BUTTON_WIDTH, NAV_HEIGHT, 2});
        }

        /** The close button's box, right of the nav's Next button. */
        public static int[] closeButton(int paneLeft, int paneWidth, int screenHeight) {
                int left = gridLeft(paneLeft, paneWidth) + columns(paneWidth) * STRIDE - ITEM_PADDING + 1;
                return new int[] {left, BOX_PADDING + 2, ITEM_SIZE, ITEM_SIZE - 4};
        }

        // ------------------------------------------------------------------
        // Sort bar
        // ------------------------------------------------------------------

        /**
         * NEU scales the bar's icons down when the pane is too narrow for
         * all of them at full size; this is that scale in pixels.
         */
        public static int sortIconSize(int paneWidth, int iconCount) {
                int left = BOX_PADDING;
                int width = columns(paneWidth) * STRIDE - ITEM_PADDING;
                int minSpan = iconCount * STRIDE + ITEM_SIZE;
                return Math.max(4, (int) (ITEM_SIZE * Math.min(1, width / (float) minSpan)));
        }

        /** The sort bar's top edge — the strip the icons sit in. */
        public static int sortBarTop(int screenHeight, int iconSize) {
                return screenHeight - BOX_PADDING - (ITEM_SIZE + iconSize) / 2 - 1;
        }

        /**
         * The sort bar's order icons, anchored from the bar's left —
         * {@code {x, y, size, kind}} per icon.
         */
        public static List<int[]> orderIcons(int paneLeft, int paneWidth, int screenHeight, int iconCount) {
                int left = gridLeft(paneLeft, paneWidth);
                int size = sortIconSize(paneWidth, iconCount);
                int stride = (int) (STRIDE * size / (float) ITEM_SIZE);
                int top = sortBarTop(screenHeight, size);
                var icons = new int[iconCount][];
                for (int i = 0; i < iconCount; i++) {
                        icons[i] = new int[] {left + i * stride, top, size, i};
                }
                return List.of(icons);
        }

        /**
         * The sort bar's filter icons, anchored from the bar's right —
         * NEU's {@code sortIconX = rightSide - size - i * stride}.
         */
        public static List<int[]> filterIcons(int paneLeft, int paneWidth, int screenHeight, int iconCount) {
                int size = sortIconSize(paneWidth, iconCount);
                int stride = (int) (STRIDE * size / (float) ITEM_SIZE);
                int top = sortBarTop(screenHeight, size);
                int right = gridLeft(paneLeft, paneWidth) + columns(paneWidth) * STRIDE - ITEM_PADDING;
                var icons = new int[iconCount][];
                for (int i = 0; i < iconCount; i++) {
                        icons[i] = new int[] {right - size - i * stride, top, size, i};
                }
                return List.of(icons);
        }

        // ------------------------------------------------------------------
        // Reopen tab
        // ------------------------------------------------------------------

        /** The reopen tab's box at the screen's right edge, vertically centered. */
        public static int[] tabBox(int screenWidth, int screenHeight) {
                return new int[] {Math.max(0, screenWidth - TAB_WIDTH),
                                Math.max(0, screenHeight / 2 - TAB_HEIGHT / 2), TAB_WIDTH, TAB_HEIGHT};
        }

        // ------------------------------------------------------------------
        // Points and rectangles
        // ------------------------------------------------------------------

        /** Whether a point sits inside a rectangle. */
        public static boolean contains(int x, int y, int w, int h, int px, int py) {
                return px >= x && px < x + w && py >= y && py < y + h;
        }

        /** Whether a point sits inside the pane's content rectangle. */
        public static boolean inPane(int paneLeft, int paneWidth, int screenHeight, int px, int py) {
                return contains(paneLeft, paneTop(), paneWidth, paneHeight(screenHeight), px, py);
        }
}
