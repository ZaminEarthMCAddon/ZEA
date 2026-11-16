package net.zaminmc.zea.hud;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The HUD layout math: anchored rects, clamping, and the drag
 * round-trip — a dropped rectangle must resolve back to the identical
 * rectangle from its stored placement, at any window size.
 */
class HudLayoutMathTest {
        @Test
        void topLeftMeasuresFromTheEdges() {
                var rect = HudLayoutMath.rectFor(HudAnchor.TOP_LEFT, 8, 10, 100, 80, 800, 600);
                assertEquals(8, rect.x());
                assertEquals(10, rect.y());
        }

        @Test
        void topRightMeasuresFromTheRightEdge() {
                var rect = HudLayoutMath.rectFor(HudAnchor.TOP_RIGHT, 8, 6, 100, 80, 800, 600);
                assertEquals(800 - 100 - 8, rect.x());
                assertEquals(6, rect.y());
        }

        @Test
        void bottomRightMeasuresFromBothEdges() {
                var rect = HudLayoutMath.rectFor(HudAnchor.BOTTOM_RIGHT, 8, 8, 100, 80, 800, 600);
                assertEquals(800 - 100 - 8, rect.x());
                assertEquals(600 - 80 - 8, rect.y());
        }

        @Test
        void anchoredPositionsSurviveResolutionChanges() {
                // A minimap docked 8 px from the top-right corner stays docked
                // 8 px from the top-right corner on any window.
                var small = HudLayoutMath.rectFor(HudAnchor.TOP_RIGHT, 8, 8, 160, 160, 854, 480);
                var large = HudLayoutMath.rectFor(HudAnchor.TOP_RIGHT, 8, 8, 160, 160, 2560, 1440);
                assertEquals(854 - 168, small.x());
                assertEquals(2560 - 168, large.x());
        }

        @Test
        void clampingKeepsTheElementOnScreen() {
                var clamped = HudLayoutMath.clamped(new HudLayoutMath.Rect(-30, -30, 100, 80), 800, 600);
                assertEquals(0, clamped.x());
                assertEquals(0, clamped.y());
                var offBottom = HudLayoutMath.clamped(new HudLayoutMath.Rect(790, 560, 100, 80), 800, 600);
                assertEquals(800 - 100, offBottom.x());
                assertEquals(600 - 80, offBottom.y());
        }

        @Test
        void dragRoundTripsExactly() {
                // A drag to some point: the stored placement must resolve back
                // to the dropped rectangle.
                var dropped = new HudLayoutMath.Rect(521, 342, 160, 160);
                var placement = HudLayoutMath.placementFor(dropped, 800, 600);
                var resolved = HudLayoutMath.rectFor(placement.anchor(), placement.offsetX(),
                                placement.offsetY(), dropped.width(), dropped.height(), 800, 600);
                assertEquals(dropped.x(), resolved.x());
                assertEquals(dropped.y(), resolved.y());
        }

        @Test
        void dragRoundTripsOnEveryAnchorQuadrant() {
                for (int x : new int[] {10, 300, 640}) {
                        for (int y : new int[] {12, 220, 430}) {
                                var dropped = new HudLayoutMath.Rect(x, y, 160, 160);
                                var placement = HudLayoutMath.placementFor(dropped, 800, 600);
                                var resolved = HudLayoutMath.rectFor(placement.anchor(), placement.offsetX(),
                                                placement.offsetY(), dropped.width(), dropped.height(), 800, 600);
                                assertEquals(dropped.x(), resolved.x(), "x for drop at " + x + "," + y);
                                assertEquals(dropped.y(), resolved.y(), "y for drop at " + x + "," + y);
                        }
                }
        }

        @Test
        void placementPicksTheNearestQuadrant() {
                var topLeft = HudLayoutMath.placementFor(new HudLayoutMath.Rect(10, 10, 50, 50), 800, 600);
                assertEquals(HudAnchor.TOP_LEFT, topLeft.anchor());
                var bottomRight = HudLayoutMath.placementFor(new HudLayoutMath.Rect(700, 520, 50, 50), 800, 600);
                assertEquals(HudAnchor.BOTTOM_RIGHT, bottomRight.anchor());
        }

        @Test
        void scaleChangesSizeWithinBounds() {
                HudElementState state = new HudElementState();
                HudElementSpec spec = new HudElementSpec(
                                net.minecraft.resources.Identifier.fromNamespaceAndPath("zea", "test"),
                                "Test", 100, 50, HudAnchor.TOP_LEFT, 0, 0, true);
                state.scalePercent(200);
                assertEquals(200, state.scaledWidth(spec));
                assertEquals(100, state.scaledHeight(spec));
                state.scalePercent(10);
                assertEquals(25, state.scalePercent(), "the floor clamps");
                assertTrue(state.scaledWidth(spec) >= 8);
        }
}
