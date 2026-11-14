package net.zaminmc.zea.buttons;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ButtonGeometryTest {
        private static final int LEFT = 40;
        private static final int TOP = 20;
        private static final int WIDTH = 176;
        private static final int HEIGHT = 166;
        private static final int SCREEN_W = 320;
        private static final int SCREEN_H = 240;

        private static ButtonGeometry.Inputs inputs(boolean searchBar, boolean recipeBook) {
                return new ButtonGeometry.Inputs(LEFT, TOP, WIDTH, HEIGHT, SCREEN_W, SCREEN_H, searchBar, recipeBook);
        }

        private static InventoryButton button(ButtonRegion region, int order) {
                return InventoryButton.newButton(order + 1)
                                .withCommand("g hi")
                                .withRegion(region)
                                .withOrder(order);
        }

        private static List<ButtonGeometry.Placement> placements(ButtonGeometry.Inputs inputs,
                        ButtonRegion... regions) {
                List<InventoryButton> buttons = new java.util.ArrayList<>();
                int i = 0;
                for (ButtonRegion region : regions) {
                        buttons.add(button(region, i++));
                }
                return ButtonGeometry.placements(buttons, inputs, region -> true);
        }

        /** The placement of the (single) button of one region in a placements list. */
        private static ButtonGeometry.Placement of(List<ButtonGeometry.Placement> placements, ButtonRegion region) {
                for (ButtonGeometry.Placement placement : placements) {
                        if (placement.button().region() == region) {
                                return placement;
                        }
                }
                throw new AssertionError("no placement for " + region);
        }

        @Test
        void topRowSitsAboveTheGuiAndSpreadsLeftToRight() {
                List<ButtonGeometry.Placement> placed = placements(inputs(true, false),
                                ButtonRegion.TOP, ButtonRegion.TOP, ButtonRegion.TOP);
                assertEquals(LEFT, placed.get(0).x());
                assertEquals(TOP - ButtonGeometry.SIZE - ButtonGeometry.GAP, placed.get(0).y());
                assertEquals(LEFT + ButtonGeometry.SIZE + ButtonGeometry.GAP, placed.get(1).x());
                assertEquals(LEFT + 2 * (ButtonGeometry.SIZE + ButtonGeometry.GAP), placed.get(2).x());
                assertTrue(placed.stream().allMatch(ButtonGeometry.Placement::rendered));
        }

        @Test
        void columnsSitBesideTheGuiAndGrowDownward() {
                List<ButtonGeometry.Placement> placed = placements(inputs(true, false),
                                ButtonRegion.LEFT, ButtonRegion.RIGHT, ButtonRegion.RIGHT);
                ButtonGeometry.Placement left = of(placed, ButtonRegion.LEFT);
                List<ButtonGeometry.Placement> rights = placed.stream()
                                .filter(p -> p.button().region() == ButtonRegion.RIGHT).toList();
                assertEquals(LEFT - ButtonGeometry.SIZE - ButtonGeometry.GAP, left.x());
                assertEquals(TOP, left.y());
                assertEquals(LEFT + WIDTH + ButtonGeometry.GAP, rights.get(0).x());
                assertEquals(TOP + ButtonGeometry.SIZE + ButtonGeometry.GAP, rights.get(1).y());
        }

        @Test
        void searchBarStacksUnderSearchThenBottomBelowIt() {
                ButtonGeometry.Inputs withBar = inputs(true, false);
                ButtonGeometry.Inputs withoutBar = inputs(false, false);
                int barBottom = TOP + HEIGHT + ButtonGeometry.GAP + ButtonGeometry.SEARCH_BAR_HEIGHT + ButtonGeometry.GAP;

                // Under-search buttons sit directly below the bar.
                List<ButtonGeometry.Placement> combined = placements(withBar,
                                ButtonRegion.UNDER_SEARCH, ButtonRegion.UNDER_SEARCH, ButtonRegion.BOTTOM);
                ButtonGeometry.Placement under = of(combined, ButtonRegion.UNDER_SEARCH);
                ButtonGeometry.Placement bottom = of(combined, ButtonRegion.BOTTOM);
                assertEquals(barBottom, under.y());
                assertEquals(LEFT, under.x());
                // The bottom row stacks below the under-search row when it is occupied…
                assertEquals(barBottom + ButtonGeometry.SIZE + ButtonGeometry.GAP, bottom.y());

                // …and moves up right under the bar when it is not.
                List<ButtonGeometry.Placement> bottomAlone = placements(withBar, ButtonRegion.BOTTOM);
                assertEquals(barBottom, of(bottomAlone, ButtonRegion.BOTTOM).y());

                // Without the bar, the bottom row sits flush under the GUI
                // and the under-search region has no home at all.
                List<ButtonGeometry.Placement> bottomNoBar = placements(withoutBar, ButtonRegion.BOTTOM);
                assertEquals(TOP + HEIGHT + ButtonGeometry.GAP, of(bottomNoBar, ButtonRegion.BOTTOM).y());
                List<ButtonGeometry.Placement> underNoBar = placements(withoutBar, ButtonRegion.UNDER_SEARCH);
                assertFalse(of(underNoBar, ButtonRegion.UNDER_SEARCH).rendered());
        }

        @Test
        void recipeBookHidesTheLeftColumnOnly() {
                List<ButtonGeometry.Placement> open = placements(inputs(true, true),
                                ButtonRegion.LEFT, ButtonRegion.RIGHT, ButtonRegion.TOP);
                assertFalse(of(open, ButtonRegion.LEFT).rendered());
                assertTrue(of(open, ButtonRegion.RIGHT).rendered());
                assertTrue(of(open, ButtonRegion.TOP).rendered());

                List<ButtonGeometry.Placement> closed = placements(inputs(true, false), ButtonRegion.LEFT);
                assertTrue(of(closed, ButtonRegion.LEFT).rendered());
        }

        @Test
        void beyondCapacityButtonsAreHiddenNotOverlapping() {
                List<InventoryButton> crowded = new java.util.ArrayList<>();
                for (int i = 0; i < ButtonRegion.TOP.capacity() + 3; i++) {
                        crowded.add(button(ButtonRegion.TOP, i));
                }
                List<ButtonGeometry.Placement> placed = ButtonGeometry.placements(crowded, inputs(true, false),
                                region -> true);
                long rendered = placed.stream().filter(ButtonGeometry.Placement::rendered).count();
                assertEquals(ButtonRegion.TOP.capacity(), rendered);
                // Hidden ones still carry a position (edit mode shows them) but
                // never collide with the rendered row: they are past its end.
                for (int i = 0; i < placed.size(); i++) {
                        for (int j = i + 1; j < placed.size(); j++) {
                                ButtonGeometry.Placement a = placed.get(i);
                                ButtonGeometry.Placement b = placed.get(j);
                                boolean overlap = a.x() < b.x() + ButtonGeometry.SIZE
                                                && b.x() < a.x() + ButtonGeometry.SIZE
                                                && a.y() < b.y() + ButtonGeometry.SIZE
                                                && b.y() < a.y() + ButtonGeometry.SIZE;
                                assertFalse(overlap, "buttons " + i + " and " + j + " overlap");
                        }
                }
        }

        @Test
        void buttonsOutsideTheScreenAreHidden() {
                // A GUI at the screen's right edge leaves no room for the right column.
                ButtonGeometry.Inputs tight = new ButtonGeometry.Inputs(
                                SCREEN_W - WIDTH, TOP, WIDTH, HEIGHT, SCREEN_W, SCREEN_H, false, false);
                List<ButtonGeometry.Placement> placed = placements(tight, ButtonRegion.RIGHT);
                assertFalse(placed.get(0).rendered());
                // The left column still fits on the other side.
                List<ButtonGeometry.Placement> left = placements(tight, ButtonRegion.LEFT);
                assertTrue(left.get(0).rendered());
        }

        @Test
        void hitTestFindsTheTopmostRenderedPlacement() {
                List<ButtonGeometry.Placement> placed = placements(inputs(true, false),
                                ButtonRegion.TOP, ButtonRegion.BOTTOM);
                ButtonGeometry.Placement hit = ButtonGeometry.at(placed,
                                placed.get(0).x() + 5, placed.get(0).y() + 5);
                assertNotNull(hit);
                assertEquals(placed.get(0).button().id(), hit.button().id());
                assertNull(ButtonGeometry.at(placed, 0, 0));
        }

        @Test
        void disabledRegionsPlaceNothing() {
                List<InventoryButton> buttons = List.of(button(ButtonRegion.LEFT, 0), button(ButtonRegion.RIGHT, 0));
                List<ButtonGeometry.Placement> placed = ButtonGeometry.placements(buttons, inputs(true, false),
                                region -> region != ButtonRegion.LEFT);
                assertFalse(placed.get(0).rendered());
                assertTrue(placed.get(1).rendered());
        }

        @Test
        void dropTargetsResolveRegionAndIndex() {
                ButtonGeometry.Inputs inputs = inputs(true, false);
                ButtonGeometry.DropTarget top = ButtonGeometry.dropTarget(inputs,
                                LEFT + 45, TOP - ButtonGeometry.SIZE, true);
                assertEquals(ButtonRegion.TOP, top.region());
                assertEquals(2, top.index());

                ButtonGeometry.DropTarget under = ButtonGeometry.dropTarget(inputs,
                                LEFT + 3, TOP + HEIGHT + ButtonGeometry.GAP + ButtonGeometry.SEARCH_BAR_HEIGHT
                                                + ButtonGeometry.GAP + 4,
                                true);
                assertEquals(ButtonRegion.UNDER_SEARCH, under.region());

                ButtonGeometry.DropTarget right = ButtonGeometry.dropTarget(inputs,
                                LEFT + WIDTH + ButtonGeometry.GAP + 5, TOP + 45, true);
                assertEquals(ButtonRegion.RIGHT, right.region());
                assertEquals(2, right.index());

                assertNull(ButtonGeometry.dropTarget(inputs, LEFT + 50, TOP + 50, true));
        }

        @Test
        void sameButtonKeepsStableIdentityAcrossLayouts() {
                InventoryButton button = InventoryButton.newButton(1)
                                .withCommand("g hi")
                                .withRegion(ButtonRegion.RIGHT)
                                .withOrder(0);
                List<ButtonGeometry.Placement> a = ButtonGeometry.placements(List.of(button), inputs(true, false),
                                region -> true);
                List<ButtonGeometry.Placement> b = ButtonGeometry.placements(List.of(button), inputs(false, false),
                                region -> true);
                assertEquals(a.get(0).button().id(), b.get(0).button().id());
                assertEquals(UUID.class, a.get(0).button().id().getClass());
        }
}
