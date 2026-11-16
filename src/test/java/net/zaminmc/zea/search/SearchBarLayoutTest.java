package net.zaminmc.zea.search;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The shared search-bar layout: horizontally centered on the container
 * GUI's bounds, vertically near the screen's bottom edge — the same
 * numbers the runtime mixin and any preview both use, so the two can
 * never drift apart.
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
        void theBarSitsAboveTheScreenBottomEdge() {
                assertEquals(300 - SearchBarWidget.HEIGHT - SearchBarWidget.BOTTOM_MARGIN,
                                SearchBarLayout.top(300));
        }

        @Test
        void shortScreensNeverPushTheBarOffTheTop() {
                // A screen exactly as tall as the bar pins the bar at the top.
                assertEquals(0, SearchBarLayout.top(SearchBarWidget.HEIGHT));
                // Anything shorter still clamps to a visible rectangle.
                assertTrue(SearchBarLayout.top(SearchBarWidget.HEIGHT / 2) >= 0);
                assertTrue(SearchBarLayout.top(SearchBarWidget.HEIGHT / 2)
                                <= Math.max(0, SearchBarWidget.HEIGHT / 2 - SearchBarWidget.HEIGHT));
        }

        @Test
        void degenerateWidthsStayPositive() {
                assertTrue(SearchBarLayout.width(4) >= 8);
        }
}
