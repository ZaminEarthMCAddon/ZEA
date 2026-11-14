package net.zaminmc.zea.buttons;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ButtonEditFormTest {

        private ButtonEditForm form(InventoryButton button) {
                return new ButtonEditForm(button);
        }

        @Test
        void savesValidValues() {
                ButtonEditForm form = form(InventoryButton.newButton(1));

                var result = form.save("Home", "town spawn", "minecraft:compass", "", "");

                var saved = assertInstanceOf(ButtonEditForm.Result.Saved.class, result);
                assertEquals("Home", saved.button().name());
                assertEquals("town spawn", saved.button().command());
                assertEquals("minecraft:compass", saved.button().icon());
                assertEquals(saved.button(), form.button(), "a successful save updates the working copy");
        }

        @Test
        void textButtonsPassWithoutAnIcon() {
                ButtonEditForm form = form(InventoryButton.newButton(1));

                var result = form.save("Hub", "hub", "", "Hub", "Teleports home");

                var saved = assertInstanceOf(ButtonEditForm.Result.Saved.class, result);
                assertTrue(saved.button().isUsable());
                assertEquals("Teleports home", saved.button().tooltip());
        }

        @Test
        void refusesMissingCommand() {
                ButtonEditForm form = form(InventoryButton.newButton(1));

                var result = form.save("Home", " ", "minecraft:compass", "", "");

                var refused = assertInstanceOf(ButtonEditForm.Result.Refused.class, result);
                assertEquals(ButtonEditForm.Rejection.BAD_COMMAND, refused.reason());
        }

        @Test
        void refusesMalformedIconWithoutText() {
                ButtonEditForm form = form(InventoryButton.newButton(1));

                var result = form.save("Home", "town spawn", "not an item id", "", "");

                var refused = assertInstanceOf(ButtonEditForm.Result.Refused.class, result);
                assertEquals(ButtonEditForm.Rejection.BAD_ICON, refused.reason());
        }

        @Test
        void commandErrorWinsWhenBothAreInvalid() {
                ButtonEditForm form = form(InventoryButton.newButton(1));

                var result = form.save("Home", "", "not an item id", "", "");

                var refused = assertInstanceOf(ButtonEditForm.Result.Refused.class, result);
                assertEquals(ButtonEditForm.Rejection.BAD_COMMAND, refused.reason());
        }

        @Test
        void refusedSaveLeavesWorkingCopyUntouched() {
                InventoryButton button = InventoryButton.newButton(1).withCommand("original");
                ButtonEditForm form = form(button);

                form.save("Home", " ", "minecraft:compass", "", "");

                assertEquals("original", form.button().command(),
                                "the text fields stay the source of truth until a save succeeds");
        }

        @Test
        void leadingSlashIsAcceptedAndStripped() {
                ButtonEditForm form = form(InventoryButton.newButton(1));

                var result = form.save("Home", "/town spawn", "minecraft:compass", "", "");

                var saved = assertInstanceOf(ButtonEditForm.Result.Saved.class, result);
                assertEquals("town spawn", saved.button().command());
        }

        @Test
        void regionOrderAndFlagsMutateTheWorkingCopy() {
                ButtonEditForm form = form(InventoryButton.newButton(1));

                form.region(ButtonRegion.BOTTOM);
                boolean close = form.toggleCloseOnExecute();
                boolean enabled = form.toggleEnabled();
                boolean later = form.orderLater();
                boolean earlier = form.orderEarlier();

                assertTrue(close);
                assertFalse(enabled);
                assertTrue(later);
                assertTrue(earlier);
                assertEquals(ButtonRegion.BOTTOM, form.button().region());
                assertFalse(form.button().enabled());
                assertTrue(form.button().closeOnExecute());
                assertEquals(0, form.button().order());
        }

        @Test
        void orderMovesStopAtTheRegionBounds() {
                ButtonEditForm form = form(InventoryButton.newButton(1).withOrder(0));

                assertFalse(form.orderEarlier());
                form.orderFirst();
                assertEquals(0, form.button().order());
                while (form.orderLater()) {
                        // climb to the top of the region
                }
                assertEquals(ButtonRegion.RIGHT.capacity() - 1, form.button().order());
                assertFalse(form.orderLater());
        }

        @Test
        void cycleRegionWrapsAround() {
                ButtonEditForm form = form(InventoryButton.newButton(1).withRegion(ButtonRegion.RIGHT));

                form.cycleRegion();
                assertEquals(ButtonRegion.TOP, form.button().region());
                form.cycleRegion();
                form.cycleRegion();
                form.cycleRegion();
                form.cycleRegion();
                assertEquals(ButtonRegion.RIGHT, form.button().region());
        }

        @Test
        void deleteRequiresTwoClicks() {
                ButtonEditForm form = form(InventoryButton.newButton(1));

                assertFalse(form.deleteArmed());
                assertFalse(form.requestDelete(), "the first click only arms");
                assertTrue(form.deleteArmed());
                assertTrue(form.requestDelete(), "the second click deletes");
        }

        @Test
        void rejectionKeysMatchTheLangFileConvention() {
                assertEquals("command", ButtonEditForm.Rejection.BAD_COMMAND.key());
                assertEquals("icon", ButtonEditForm.Rejection.BAD_ICON.key());
        }

        @Test
        void formKeepsButtonIdentityAcrossEdits() {
                InventoryButton button = InventoryButton.newButton(1);
                ButtonEditForm form = form(button);

                form.region(ButtonRegion.BOTTOM);
                form.toggleEnabled();
                var result = form.save("Renamed", "home", "minecraft:compass", "", "");

                var saved = assertInstanceOf(ButtonEditForm.Result.Saved.class, result);
                assertEquals(button.id(), saved.button().id());
                assertNotEquals(button, saved.button());
        }

        @Test
        void nullRegionIsIgnored() {
                ButtonEditForm form = form(InventoryButton.newButton(1).withRegion(ButtonRegion.LEFT));

                form.region(null);

                assertEquals(ButtonRegion.LEFT, form.button().region());
                assertNull(null);
        }
}
