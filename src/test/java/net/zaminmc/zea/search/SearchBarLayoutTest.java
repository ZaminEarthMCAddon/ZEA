package net.zaminmc.zea.search;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The shared search-bar layout: centered on the container GUI's bounds,
 * one gap below it — the same numbers the runtime mixin and the editor
 * preview both use, so the two can never drift apart.
 */
class SearchBarLayoutTest {
        @Test
        void theBarIsCenteredOnTheGuiNotTheFramebuffer() {
                // A GUI at left=10, width=176 sits at 10..186; the bar is
                // inset by the margin on both sides — centered on the GUI.
                assertEquals(10 + SearchBarWidget.MARGIN, SearchBarLayout.left(10));
                assertEquals(176 - 2 * SearchBarWidget.MARGIN, SearchBarLayout.width(176));
                // Centering holds: bar center == GUI center.
                int guiLeft = 500;
                int guiWidth = 176;
                int barLeft = SearchBarLayout.left(guiLeft);
                int barWidth = SearchBarLayout.width(guiWidth);
                assertEquals(guiLeft + guiWidth / 2, barLeft + barWidth / 2);
        }

        @Test
        void theBarSitsOneGapBelowTheGui() {
                assertEquals(78 + 166 + 2, SearchBarLayout.top(78, 166));
        }

        @Test
        void theBottomBandCoversTheBarAndItsGap() {
                assertEquals(SearchBarWidget.HEIGHT + 2, SearchBarWidget.BOTTOM_BAND);
        }

        @Test
        void degenerateWidthsStayPositive() {
                assertTrue_like(SearchBarLayout.width(4) >= 8);
        }

        private static void assertTrue_like(boolean condition) {
                org.junit.jupiter.api.Assertions.assertTrue(condition);
        }
}
