package net.zaminmc.zea.buttons;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InventoryButtonTest {
        @Test
        void newButtonIsIncompleteUntilGivenACommand() {
                InventoryButton button = InventoryButton.newButton(1);

                assertEquals("Button 1", button.name());
                assertEquals("", button.command());
                assertEquals("minecraft:compass", button.icon());
                assertEquals(ButtonRegion.RIGHT, button.region());
                assertEquals(0, button.order());
                assertFalse(button.closeOnExecute());
                assertTrue(button.enabled());
                assertFalse(button.isUsable());

                assertTrue(button.withCommand("g hello").isUsable());
        }

        @Test
        void missingFieldsGetSafeDefaults() {
                InventoryButton button = new InventoryButton(null, null, null, null, null, null, null, 5, false, true);

                assertNotNull(button.id());
                assertEquals(InventoryButton.DEFAULT_NAME, button.name());
                assertEquals("", button.command());
                assertEquals("", button.icon());
                assertEquals("", button.text());
                assertEquals("", button.tooltip());
                assertEquals(ButtonRegion.RIGHT, button.region());
                assertEquals(5, button.order());
                assertFalse(button.isUsable());
        }

        @Test
        void orderIsClampedToTheRegionCapacity() {
                InventoryButton button = InventoryButton.newButton(1)
                                .withRegion(ButtonRegion.UNDER_SEARCH)
                                .withOrder(99);

                assertEquals(ButtonRegion.UNDER_SEARCH.capacity() - 1, button.order());
        }

        @Test
        void textButtonsAreUsableWithoutAnIcon() {
                InventoryButton button = InventoryButton.newButton(1)
                                .withCommand("hub")
                                .withIcon("")
                                .withText("Hub");

                assertTrue(button.isUsable());
                // Without text, an icon is required.
                assertFalse(button.withText("").isUsable());
        }

        @Test
        void leadingSlashIsStrippedFromCommands() {
                InventoryButton button = InventoryButton.newButton(1).withCommand("/town spawn");

                assertEquals("town spawn", button.command());
        }

        @Test
        void longNamesAndCommandsAreTruncated() {
                InventoryButton button = InventoryButton.newButton(1)
                                .withCommand("a".repeat(InventoryButton.MAX_COMMAND_LENGTH + 10))
                                .withName("b".repeat(InventoryButton.MAX_NAME_LENGTH + 10))
                                .withText("c".repeat(InventoryButton.MAX_TEXT_LENGTH + 10));

                assertEquals(InventoryButton.MAX_COMMAND_LENGTH, button.command().length());
                assertEquals(InventoryButton.MAX_NAME_LENGTH, button.name().length());
                assertEquals(InventoryButton.MAX_TEXT_LENGTH, button.text().length());
        }

        @Test
        void controlCharactersAreRemoved() {
                InventoryButton button = InventoryButton.newButton(1).withCommand("town\r\nspawn");

                assertEquals("townspawn", button.command());
                assertTrue(button.isUsable());
        }

        @Test
        void commandValidityRules() {
                assertFalse(InventoryButton.validCommand(null));
                assertFalse(InventoryButton.validCommand(""));
                assertFalse(InventoryButton.validCommand("   "));
                assertFalse(InventoryButton.validCommand("a".repeat(InventoryButton.MAX_COMMAND_LENGTH + 1)));
                assertFalse(InventoryButton.validCommand("town\nspawn"));
                assertTrue(InventoryButton.validCommand("g hello world"));
                assertTrue(InventoryButton.validCommand("a".repeat(InventoryButton.MAX_COMMAND_LENGTH)));
        }

        @Test
        void iconValidityRules() {
                assertFalse(InventoryButton.validIcon(null));
                assertFalse(InventoryButton.validIcon(""));
                assertFalse(InventoryButton.validIcon("a".repeat(InventoryButton.MAX_ICON_LENGTH + 1)));
                assertFalse(InventoryButton.validIcon("compass\n"));
                assertTrue(InventoryButton.validIcon("minecraft:compass"));
                assertTrue(InventoryButton.validIcon("zea:anything"));
        }

        @Test
        void withNewIdChangesIdentityButKeepsData() {
                InventoryButton button = InventoryButton.newButton(1).withCommand("g hi");
                InventoryButton copy = button.withNewId();

                assertNotEquals(button.id(), copy.id());
                assertEquals(button.command(), copy.command());
                assertEquals(button.icon(), copy.icon());
                assertEquals(button.region(), copy.region());
                assertEquals(button.order(), copy.order());
                assertEquals(button.closeOnExecute(), copy.closeOnExecute());
        }

        @Test
        void legacyAnchorsMapToRegions() {
                assertEquals(ButtonRegion.TOP, ButtonRegion.fromLegacyAnchor(ButtonAnchor.TOP_LEFT));
                assertEquals(ButtonRegion.TOP, ButtonRegion.fromLegacyAnchor(ButtonAnchor.TOP_RIGHT));
                assertEquals(ButtonRegion.BOTTOM, ButtonRegion.fromLegacyAnchor(ButtonAnchor.BOTTOM_LEFT));
                assertEquals(ButtonRegion.BOTTOM, ButtonRegion.fromLegacyAnchor(ButtonAnchor.BOTTOM_RIGHT));
        }
}
