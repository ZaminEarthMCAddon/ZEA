package net.zaminmc.zea.buttons;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InventoryButtonsEditingTest {
        @TempDir
        Path tempDir;

        private final List<String> sent = new ArrayList<>();

        private InventoryButtons withButtons(String json) throws IOException {
                Path file = tempDir.resolve("buttons.json");
                Files.writeString(file, json, UTF_8);
                return new InventoryButtons(file, new ButtonActivator(sent::add, 0));
        }

        private static String oneButton(String name) {
                return """
                                {
                                  "version": 2,
                                  "buttons": [
                                    { "name": "%s", "command": "town spawn", "icon": "minecraft:compass" }
                                  ]
                                }
                                """.formatted(name);
        }

        @Test
        void upsertReplacesByIdAndPersists() throws IOException {
                InventoryButtons buttons = withButtons(oneButton("Home"));
                InventoryButton original = buttons.buttons().get(0);

                buttons.upsert(original.withName("Renamed").withCommand("home"));

                assertEquals(1, buttons.buttons().size());
                assertEquals("Renamed", buttons.buttons().get(0).name());
                assertEquals("home", buttons.buttons().get(0).command());
                assertEquals(List.of(buttons.buttons().get(0)), new InventoryButtons(
                                tempDir.resolve("buttons.json"), new ButtonActivator(sent::add, 0)).buttons(),
                                "the change must survive a reload");
        }

        @Test
        void upsertAppendsUnknownIds() throws IOException {
                InventoryButtons buttons = withButtons(oneButton("Home"));

                buttons.upsert(InventoryButton.newButton(2).withCommand("vote"));

                assertEquals(2, buttons.buttons().size());
                assertTrue(buttons.buttons().stream().anyMatch(b -> b.name().equals("Button 2")),
                                "the new button joins the set (position follows the region sort)");
        }

        @Test
        void deleteRemovesByIdAndPersists() throws IOException {
                InventoryButtons buttons = withButtons(oneButton("Home"));
                InventoryButton home = buttons.buttons().get(0);

                buttons.delete(home.id());

                assertEquals(List.of(), buttons.buttons());
                assertEquals(List.of(), new InventoryButtons(
                                tempDir.resolve("buttons.json"), new ButtonActivator(sent::add, 0)).buttons());
        }

        @Test
        void editModeTogglesAndDropsTheDrag() throws IOException {
                InventoryButtons buttons = withButtons(oneButton("Home"));
                assertFalse(buttons.editMode());

                buttons.toggleEditMode();
                assertTrue(buttons.editMode());

                InventoryButton home = buttons.buttons().get(0);
                buttons.drag().begin(home, 0, 0, 5, 5);
                buttons.toggleEditMode();

                assertFalse(buttons.editMode());
                assertFalse(buttons.drag().isActive(), "leaving edit mode aborts the drag");
        }

        @Test
        void moveInsertsAtTheRequestedIndexAndRepacksOrders() throws IOException {
                InventoryButtons buttons = withButtons("""
                                {
                                  "version": 2,
                                  "buttons": [
                                    { "name": "A", "command": "spawn", "icon": "minecraft:compass",
                                      "region": "RIGHT", "order": 0 },
                                    { "name": "B", "command": "spawn", "icon": "minecraft:paper",
                                      "region": "RIGHT", "order": 1 },
                                    { "name": "C", "command": "spawn", "icon": "minecraft:book",
                                      "region": "RIGHT", "order": 2 }
                                  ]
                                }
                                """);
                InventoryButton c = buttons.regionButtons(ButtonRegion.RIGHT).get(2);

                buttons.move(c.id(), ButtonRegion.RIGHT, 0);

                List<InventoryButton> right = buttons.regionButtons(ButtonRegion.RIGHT);
                assertEquals("C", right.get(0).name());
                assertEquals("A", right.get(1).name());
                assertEquals("B", right.get(2).name());
                assertEquals(0, right.get(0).order(), "orders are dense after every move");
                assertEquals(1, right.get(1).order());
                assertEquals(2, right.get(2).order());
        }

        @Test
        void moveBetweenRegionsKeepsTheOldRegionDense() throws IOException {
                InventoryButtons buttons = withButtons("""
                                {
                                  "version": 2,
                                  "buttons": [
                                    { "name": "A", "command": "spawn", "icon": "minecraft:compass",
                                      "region": "TOP", "order": 0 },
                                    { "name": "B", "command": "spawn", "icon": "minecraft:paper",
                                      "region": "TOP", "order": 1 }
                                  ]
                                }
                                """);
                InventoryButton a = buttons.regionButtons(ButtonRegion.TOP).get(0);

                buttons.move(a.id(), ButtonRegion.BOTTOM, 0);

                assertEquals(1, buttons.regionButtons(ButtonRegion.TOP).size());
                assertEquals(0, buttons.regionButtons(ButtonRegion.TOP).get(0).order(),
                                "the gap closes behind the moved button");
                assertEquals(1, buttons.regionButtons(ButtonRegion.BOTTOM).size());
        }

        @Test
        void moveLaterAndEarlierShiftWithinTheRegion() throws IOException {
                InventoryButtons buttons = withButtons("""
                                {
                                  "version": 2,
                                  "buttons": [
                                    { "name": "A", "command": "spawn", "icon": "minecraft:compass",
                                      "region": "LEFT", "order": 0 },
                                    { "name": "B", "command": "spawn", "icon": "minecraft:paper",
                                      "region": "LEFT", "order": 1 }
                                  ]
                                }
                                """);
                InventoryButton a = buttons.regionButtons(ButtonRegion.LEFT).get(0);

                buttons.moveLater(a.id());

                assertEquals("B", buttons.regionButtons(ButtonRegion.LEFT).get(0).name());
                buttons.moveEarlier(a.id());
                assertEquals("A", buttons.regionButtons(ButtonRegion.LEFT).get(0).name());
        }

        @Test
        void applyingAPresetKeepsTheReplacedSetRecoverable() throws IOException {
                InventoryButtons buttons = withButtons(oneButton("Home"));
                ButtonPresets.Preset empty = ButtonPresets.byId(ButtonPresets.EMPTY);

                buttons.applyPreset(empty);

                assertTrue(buttons.buttons().isEmpty());
                assertTrue(buttons.hasRestorePoint());
                assertEquals(ButtonPresets.EMPTY, buttons.appliedPresetId());

                assertTrue(buttons.restorePrevious());
                assertEquals(1, buttons.buttons().size());
                assertEquals("Home", buttons.buttons().get(0).name());
                assertFalse(buttons.hasRestorePoint());
                assertFalse(buttons.restorePrevious(), "only one step back");
        }

        @Test
        void aRegularEditDropsTheRestorePoint() throws IOException {
                InventoryButtons buttons = withButtons(oneButton("Home"));
                buttons.applyPreset(ButtonPresets.byId(ButtonPresets.EMPTY));
                assertTrue(buttons.hasRestorePoint());

                buttons.upsert(InventoryButton.newButton(9).withCommand("vote"));

                assertFalse(buttons.hasRestorePoint(), "edits after a preset are deliberate");
        }

        @Test
        void keepingAPresetDiscardsTheRestorePoint() throws IOException {
                InventoryButtons buttons = withButtons(oneButton("Home"));
                buttons.applyPreset(ButtonPresets.byId(ButtonPresets.EMPTY));

                buttons.keepPreset();

                assertFalse(buttons.hasRestorePoint());
                assertTrue(buttons.buttons().isEmpty());
        }

        @Test
        void presetApplicationsPersist() throws IOException {
                InventoryButtons buttons = withButtons(oneButton("Home"));

                buttons.applyPreset(ButtonPresets.byId(ButtonPresets.EVERYWHERE));

                assertEquals(new InventoryButtons(tempDir.resolve("buttons.json"),
                                new ButtonActivator(sent::add, 0)).buttons().size(),
                                ButtonPresets.byId(ButtonPresets.EVERYWHERE).buttons().size());
        }
}
