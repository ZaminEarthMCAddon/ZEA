package net.zaminmc.zea.misc;

import org.joml.Vector2ic;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The tooltip scroller's clamped bounds math — the part extracted from
 * the rendering path so it can be pinned without a screen.
 */
class TooltipScrollerTest {
        private static final int MARGIN = TooltipScroller.SCREEN_MARGIN;

        @Test
        void overflowingTooltipClampsInsideMargins() {
                // A tooltip at y=10, 900 tall, screen 600: the scroll range
                // spans pushing the top down to the margin and pulling the
                // bottom up to the other margin.
                TooltipScroller.Adjustment adjustment = TooltipScroller.computeAdjustment(
                                                100, 10, 200, 900, 400, 600, -10_000, 10_000);
                // Top fully down: y + scrollY = MARGIN → scrollY = 6 - 10.
                assertEquals(MARGIN - 10, adjustment.scrollY(), 0.0001);
        }

        @Test
        void scrollToBottomRespectsBottomMargin() {
                TooltipScroller.Adjustment adjustment = TooltipScroller.computeAdjustment(
                                                100, 10, 200, 900, 400, 600, -10_000, -10_000);
                // Bottom pulled to the margin: scrollY = screenH - margin
                // - y - height.
                assertEquals(600 - MARGIN - 10 - 900, adjustment.scrollY(), 0.0001);
        }

        @Test
        void fitsWithoutScrollingCollapsesOffset() {
                // The tooltip fits the screen: the offset is clamped into
                // the valid range and pins to the nearest boundary, so the
                // content never floats free of the margins.
                TooltipScroller.Adjustment adjustment = TooltipScroller.computeAdjustment(
                                                100, 100, 200, 200, 400, 600, 500, -500);
                // desiredY = -500 clamps to the top boundary (margin - y).
                assertEquals(MARGIN - 100, adjustment.scrollY(), 0.0001);
                // desiredX = 500 clamps to the right boundary.
                assertEquals(400 - MARGIN - 100 - 200, adjustment.scrollX(), 0.0001);
        }

        @Test
        void horizontalBoundsMatchVerticalSemantics() {
                TooltipScroller.Adjustment adjustment = TooltipScroller.computeAdjustment(
                                                0, 0, 800, 100, 400, 600, 5, 0);
                // Wide tooltip: max = margin - x = 6, min = 400 - 6 - 0 -
                // 800 = -406; desired 5 within range stays.
                assertEquals(5, adjustment.scrollX(), 0.0001);
        }

        @Test
        void adjustPositionPassesThroughVanillaWhenReferenceShapeUnusable() {
                // adjustPosition is only reachable when the mixin passes a
                // real resolved position; with a fitting tooltip the
                // vanilla vector is returned unchanged (guarded by
                // engaged(), which is false in tests without a screen).
                Vector2ic vanilla = new org.joml.Vector2i(12, 34);
                Vector2ic result = TooltipScroller.adjustPosition(vanilla, 400, 600, 100, 100);
                assertEquals(vanilla.x(), result.x());
                assertEquals(vanilla.y(), result.y());
        }
}
