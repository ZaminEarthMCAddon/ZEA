package net.zaminmc.zea.buttons;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ButtonGeometryTest {
        private InventoryButton at(String zone, int order) {
                return new InventoryButton(UUID.randomUUID(), "b", "cmd", "minecraft:stone", "", "",
                                zone, order, 0, false, true);
        }

        /** The representative geometry the zone math is pinned against. */
        private ButtonGeometry.Inputs screen(boolean playerInventory) {
                return new ButtonGeometry.Inputs(0, 0, 176, 166, 320, 240, playerInventory, false);
        }

        @Test
        void topRowHangsAboveTheGui() {
                assertEquals(8, ButtonGeometry.slotRect(ButtonGeometry.Zone.TOP, 0, screen(true)).x());
                assertEquals(-20, ButtonGeometry.slotRect(ButtonGeometry.Zone.TOP, 0, screen(true)).y());
                assertEquals(148, ButtonGeometry.slotRect(ButtonGeometry.Zone.TOP, 7, screen(true)).x(),
                                "the row starts at ROW_START_X and steps by one button plus the gap");
                assertNull(ButtonGeometry.slotRect(ButtonGeometry.Zone.TOP, 8, screen(true)),
                                "order past the capacity is no slot at all");
        }

        @Test
        void leftColumnBottomAlignsToTheHotbarLevel() {
                ButtonGeometry.SlotRect slot = ButtonGeometry.slotRect(ButtonGeometry.Zone.LEFT, 0, screen(true));

                assertEquals(-20, slot.x());
                assertEquals(166 - 18, slot.y(), "order 0's bottom edge sits at the GUI's bottom edge");
                assertEquals(166 - 2 * 18 - 2,
                                ButtonGeometry.slotRect(ButtonGeometry.Zone.LEFT, 1, screen(true)).y(),
                                "later orders climb up by one button plus the gap");
        }

        @Test
        void rightColumnHugsTheGuiEdge() {
                ButtonGeometry.SlotRect slot = ButtonGeometry.slotRect(ButtonGeometry.Zone.RIGHT, 0, screen(true));

                assertEquals(178, slot.x());
                assertEquals(148, slot.y());
        }

        @Test
        void theUnderHotbarRowSitsUnderTheGui() {
                ButtonGeometry.SlotRect slot = ButtonGeometry.slotRect(ButtonGeometry.Zone.UNDER_HOTBAR, 0, screen(true));

                assertEquals(8, slot.x());
                assertEquals(168, slot.y());
        }

        @Test
        void theAboveSearchRowHangsOverTheScreenBottomBar() {
                ButtonGeometry.SlotRect slot = ButtonGeometry.slotRect(ButtonGeometry.Zone.ABOVE_SEARCH, 0, screen(true));

                assertEquals(8, slot.x());
                // The row hangs above the bottom bar: screen minus the bar's
                // bottom margin (5), its height (22) and the gap.
                assertEquals(240 - 5 - 22 - 2 - 18, slot.y());
                assertEquals(9, ButtonGeometry.Zone.ABOVE_SEARCH.capacity);
                assertNull(ButtonGeometry.slotRect(ButtonGeometry.Zone.ABOVE_SEARCH, 9, screen(true)));
        }

        @Test
        void theAboveSearchRowHidesOnScreensTooShortForBothBottomRows() {
                ButtonGeometry.Inputs shortScreen = new ButtonGeometry.Inputs(
                                0, 0, 176, 166, 320, 216, true, false);

                // 216 - 5 - 22 - 2 - 18 = 169 < 166 + 2 + 18: the rows would collide.
                assertNull(ButtonGeometry.slotRect(ButtonGeometry.Zone.ABOVE_SEARCH, 0, shortScreen));
                assertNotNull(ButtonGeometry.slotRect(ButtonGeometry.Zone.UNDER_HOTBAR, 0, shortScreen),
                                "the under-hotbar row still renders");
        }

        @Test
        void underCraftingBelongsToThePlayerInventoryOnly() {
                ButtonGeometry.SlotRect slot = ButtonGeometry.slotRect(ButtonGeometry.Zone.UNDER_CRAFTING, 0, screen(true));

                assertEquals(97, slot.x());
                assertEquals(55, slot.y());
                assertNull(ButtonGeometry.slotRect(ButtonGeometry.Zone.UNDER_CRAFTING, 0, screen(false)),
                                "on a chest the row would cover live slots");
                assertNull(ButtonGeometry.slotRect(ButtonGeometry.Zone.UNDER_CRAFTING, 4, screen(true)),
                                "only four buttons fit under the crafting grid");
        }

        @Test
        void topRightIsTheSingleCornerSlotOfThePlayerInventory() {
                ButtonGeometry.SlotRect slot = ButtonGeometry.slotRect(ButtonGeometry.Zone.TOP_RIGHT, 0, screen(true));

                assertEquals(156, slot.x());
                assertEquals(2, slot.y());
                assertNull(ButtonGeometry.slotRect(ButtonGeometry.Zone.TOP_RIGHT, 1, screen(true)),
                                "the corner holds exactly one button");
                assertNull(ButtonGeometry.slotRect(ButtonGeometry.Zone.TOP_RIGHT, 0, screen(false)));
        }

        @Test
        void zoneNamesResolveCaseInsensitivelyAndUnknownNamesAreNull() {
                assertEquals(ButtonGeometry.Zone.TOP, ButtonGeometry.Zone.byName("top"));
                assertEquals(ButtonGeometry.Zone.TOP, ButtonGeometry.Zone.byName("TOP"));
                assertNull(ButtonGeometry.Zone.byName("NOWHERE"));
                assertNull(ButtonGeometry.Zone.byName(null));
        }

        @Test
        void buttonsInUnknownZonesRenderNowhere() {
                ButtonGeometry.Placement placement = ButtonGeometry.placements(
                                List.of(new InventoryButton(null, "b", "cmd", "minecraft:stone", "", "",
                                                "NOWHERE", 0, 0, false, true)), screen(true)).get(0);

                assertFalse(placement.rendered());
        }

        @Test
        void renderedPlacementsRespectTheScreenEdges() {
                List<ButtonGeometry.Placement> placements = ButtonGeometry.placements(
                                List.of(at("RIGHT", 0), at("TOP", 0), at("UNDER_HOTBAR", 0)), screen(false));

                assertTrue(placements.get(0).rendered(), "the right column renders beside the GUI");
                assertEquals(178, placements.get(0).x());
                assertFalse(placements.get(1).rendered(), "a top button at y = -20 is off-screen and hidden");
                assertTrue(placements.get(2).rendered());
        }

        @Test
        void theRecipeBookClaimsTheLeftStripWhileOpen() {
                ButtonGeometry.Inputs withBook = new ButtonGeometry.Inputs(100, 50, 176, 166, 400, 300, false, true);

                ButtonGeometry.Placement left = ButtonGeometry.placements(
                                List.of(at("LEFT", 0)), withBook).get(0);
                assertFalse(left.rendered(), "the recipe book panel owns the strip left of the GUI");

                ButtonGeometry.Placement right = ButtonGeometry.placements(
                                List.of(at("RIGHT", 0)), withBook).get(0);
                assertTrue(right.rendered());
        }

        @Test
        void hitTestsFindTheTopmostRenderedButton() {
                List<ButtonGeometry.Placement> placements = ButtonGeometry.placements(
                                List.of(at("RIGHT", 0), at("RIGHT", 0)), screen(false));

                assertNotNull(ButtonGeometry.at(placements, 187, 157));
                assertEquals(placements.get(1).button().id(),
                                ButtonGeometry.at(placements, 187, 157).button().id());
                assertNull(ButtonGeometry.at(placements, 5, 5));
        }

        @Test
        void editableAtFindsHiddenButtonsToo() {
                // A TOP button on this screen resolves to y = -20: nothing renders
                // there, but edit mode still shows it so crowding stays visible.
                List<ButtonGeometry.Placement> placements = ButtonGeometry.placements(
                                List.of(at("TOP", 0)), screen(false));

                assertFalse(placements.get(0).rendered());
                assertNotNull(ButtonGeometry.editableAt(placements, 9, -10));
        }

        @Test
        void occupiedMatchesTheZoneSlotAndSparesTheExceptButton() {
                InventoryButton first = at("RIGHT", 0);
                List<InventoryButton> buttons = List.of(first, at("RIGHT", 3));

                assertTrue(ButtonGeometry.occupied(buttons, null, ButtonGeometry.Zone.RIGHT, 0));
                assertTrue(ButtonGeometry.occupied(buttons, null, ButtonGeometry.Zone.RIGHT, 3));
                assertFalse(ButtonGeometry.occupied(buttons, null, ButtonGeometry.Zone.RIGHT, 1));
                assertFalse(ButtonGeometry.occupied(buttons, null, ButtonGeometry.Zone.LEFT, 0));
                assertFalse(ButtonGeometry.occupied(buttons, first, ButtonGeometry.Zone.RIGHT, 0),
                                "the dragged button never blocks its own slot");
        }

        @Test
        void firstFreeOrderFindsTheGapAndReportsFullZones() {
                List<InventoryButton> buttons = List.of(at("RIGHT", 0), at("RIGHT", 2));

                assertEquals(1, ButtonGeometry.firstFreeOrder(buttons, ButtonGeometry.Zone.RIGHT));

                List<InventoryButton> full = new ArrayList<>();
                for (int i = 0; i < ButtonGeometry.Zone.RIGHT.capacity; i++) {
                        full.add(at("RIGHT", i));
                }
                assertEquals(-1, ButtonGeometry.firstFreeOrder(full, ButtonGeometry.Zone.RIGHT),
                                "a full zone has no free order");
        }

        @Test
        void dropTargetReturnsTheNearestFreeSlot() {
                // Dead center of the under-crafting row's first slot (97 + 9, 55 + 9).
                ButtonGeometry.SlotRect target = ButtonGeometry.dropTarget(
                                List.of(), null, 106, 64, screen(true));

                assertEquals(ButtonGeometry.Zone.UNDER_CRAFTING, target.zone());
                assertEquals(0, target.order());
        }

        @Test
        void dropTargetSkipsOccupiedSlots() {
                List<InventoryButton> buttons = List.of(at("UNDER_CRAFTING", 0));

                ButtonGeometry.SlotRect target = ButtonGeometry.dropTarget(
                                buttons, null, 106, 64, screen(true));

                assertEquals(ButtonGeometry.Zone.UNDER_CRAFTING, target.zone());
                assertEquals(1, target.order(), "the nearest occupied slot is skipped for its free neighbor");

                // The dragged button itself does not block its old slot.
                ButtonGeometry.SlotRect self = ButtonGeometry.dropTarget(
                                buttons, buttons.get(0), 106, 64, screen(true));
                assertEquals(ButtonGeometry.Zone.UNDER_CRAFTING, self.zone());
                assertEquals(0, self.order());
        }

        @Test
        void dropTargetFallsThroughToTheNextNearestZoneWhenFull() {
                List<InventoryButton> underCrafting = new ArrayList<>();
                for (int i = 0; i < ButtonGeometry.Zone.UNDER_CRAFTING.capacity; i++) {
                        underCrafting.add(at("UNDER_CRAFTING", i));
                }

                // The whole row is taken, so the nearest free slot wins — the top
                // row's slot directly above the crafting row (center 97, -11: dead
                // horizontal of the drop point) beats the corner slot.
                ButtonGeometry.SlotRect target = ButtonGeometry.dropTarget(
                                underCrafting, null, 106, 64, screen(true));

                assertEquals(ButtonGeometry.Zone.TOP, target.zone());
                assertEquals(4, target.order());

                // A full top row falls to the next-nearest zone with a free slot.
                List<InventoryButton> top = new ArrayList<>();
                for (int i = 0; i < ButtonGeometry.Zone.TOP.capacity; i++) {
                        top.add(at("TOP", i));
                }
                // A full top row falls to the next-nearest zone with a free
                // slot: dropped at the row's left end, the left column's top
                // slot (center -11, 17) is the nearest free rectangle.
                ButtonGeometry.SlotRect fromTop = ButtonGeometry.dropTarget(
                                top, null, 17, -11, screen(true));

                assertEquals(ButtonGeometry.Zone.LEFT, fromTop.zone(),
                                "a full top row falls through to the nearest free zone");
                assertEquals(7, fromTop.order());
        }

        @Test
        void dropTargetNeverTargetsAnInventoryOnlyZoneOnForeignContainers() {
                ButtonGeometry.SlotRect target = ButtonGeometry.dropTarget(
                                List.of(), null, 106, 64, screen(false));

                assertFalse(target.zone().inventoryOnly,
                                "the under-crafting row cannot take a drop on a chest");
        }

        @Test
        void zoneConstantsMatchTheDev36Spec() {
                assertEquals(18, ButtonGeometry.SIZE);
                assertEquals(2, ButtonGeometry.GAP);
                assertEquals(20, ButtonGeometry.STEP);
                assertEquals(8, ButtonGeometry.ROW_START_X);
                assertEquals(97, ButtonGeometry.CRAFTING_X);
                assertEquals(17, ButtonGeometry.CRAFTING_Y);
                assertEquals(55, ButtonGeometry.UNDER_CRAFTING_Y);
                assertEquals(176, ButtonGeometry.CANVAS_WIDTH);
                assertEquals(166, ButtonGeometry.CANVAS_HEIGHT);
        }
}
