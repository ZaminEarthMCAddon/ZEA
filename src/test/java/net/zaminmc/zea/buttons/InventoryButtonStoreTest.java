package net.zaminmc.zea.buttons;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InventoryButtonStoreTest {
        @TempDir
        Path tempDir;

        private InventoryButtonStore newStore() {
                return new InventoryButtonStore(tempDir.resolve("buttons.json"));
        }

        private static InventoryButton usable(String name) {
                return InventoryButton.newButton(1).withName(name).withCommand("town spawn");
        }

        @Test
        void missingFileYieldsNoButtons() {
                assertTrue(newStore().load().isEmpty());
        }

        @Test
        void roundTripsButtons() {
                InventoryButtonStore store = newStore();
                List<InventoryButton> buttons = List.of(
                                usable("Home").withIcon("minecraft:compass"),
                                usable("Trade").withRegion(ButtonRegion.BOTTOM).withOrder(2).withEnabled(false),
                                usable("Hub").withText("Hub").withCloseOnExecute(true));

                store.save(buttons);
                List<InventoryButton> loaded = store.load();

                // The store normalizes on the way through (regions sorted,
                // orders dense) but every field survives, keyed by identity.
                assertEquals(3, loaded.size());
                InventoryButton home = loaded.stream().filter(b -> b.name().equals("Home")).findFirst().orElseThrow();
                InventoryButton trade = loaded.stream().filter(b -> b.name().equals("Trade")).findFirst().orElseThrow();
                InventoryButton hub = loaded.stream().filter(b -> b.name().equals("Hub")).findFirst().orElseThrow();
                assertEquals("minecraft:compass", home.icon());
                assertEquals(ButtonRegion.BOTTOM, trade.region());
                assertEquals(0, trade.order(), "sparse orders are re-packed densely");
                assertFalse(trade.enabled());
                assertEquals("Hub", hub.text());
                assertTrue(hub.closeOnExecute());
                // And the normalization is stable: a second round trip is the identity.
                store.save(loaded);
                assertEquals(loaded, store.load());
        }

        @Test
        void corruptFileYieldsNoButtonsAndIsPreserved() throws IOException {
                Path file = tempDir.resolve("buttons.json");
                Files.writeString(file, "{ not json", UTF_8);

                assertTrue(newStore().load().isEmpty());
                assertEquals("{ not json", Files.readString(tempDir.resolve("buttons.json.corrupted"), UTF_8));
        }

        @Test
        void nullEntriesAreDroppedAndBlankCommandsKept() throws IOException {
                // A v2 file: one good, one template with a blank command, one damaged.
                Path file = tempDir.resolve("buttons.json");
                Files.writeString(file, """
                                {
                                  "version": 2,
                                  "buttons": [
                                    { "name": "Good", "command": "town spawn", "icon": "minecraft:compass" },
                                    { "name": "Template", "icon": "minecraft:chest", "order": 1 },
                                    null
                                  ]
                                }
                                """, UTF_8);

                List<InventoryButton> loaded = newStore().load();

                assertEquals(2, loaded.size());
                InventoryButton good = loaded.stream().filter(b -> b.name().equals("Good")).findFirst().orElseThrow();
                InventoryButton template = loaded.stream().filter(b -> b.name().equals("Template")).findFirst().orElseThrow();
                assertEquals("town spawn", good.command());
                assertEquals("", template.command(), "preset templates survive persistence");
                assertEquals(0, good.order());
                assertEquals(1, template.order());
        }

        @Test
        void nullFieldsAreTolerated() throws IOException {
                Path file = tempDir.resolve("buttons.json");
                Files.writeString(file, """
                                {
                                  "version": 2,
                                  "buttons": [
                                    { "name": null, "command": "/g hi", "icon": "minecraft:paper", "text": null,
                                      "region": null, "order": null, "closeOnExecute": null, "enabled": null }
                                  ]
                                }
                                """, UTF_8);

                List<InventoryButton> loaded = newStore().load();

                assertEquals(1, loaded.size());
                assertEquals(InventoryButton.DEFAULT_NAME, loaded.get(0).name());
                assertEquals("g hi", loaded.get(0).command());
                assertEquals(ButtonRegion.RIGHT, loaded.get(0).region());
                assertEquals(0, loaded.get(0).order());
                assertFalse(loaded.get(0).closeOnExecute());
                assertTrue(loaded.get(0).enabled());
        }

        @Test
        void duplicatedIdsAreReissuedOnLoad() throws IOException {
                UUID shared = UUID.randomUUID();
                Path file = tempDir.resolve("buttons.json");
                Files.writeString(file, """
                                {
                                  "version": 2,
                                  "buttons": [
                                    { "id": "%s", "name": "First", "command": "spawn", "icon": "minecraft:compass" },
                                    { "id": "%s", "name": "Second", "command": "spawn", "icon": "minecraft:paper" }
                                  ]
                                }
                                """.formatted(shared, shared), UTF_8);

                List<InventoryButton> loaded = newStore().load();

                assertEquals(2, loaded.size());
                assertEquals(2, loaded.stream().map(InventoryButton::id).distinct().count(),
                                "each button must keep a unique identity");
        }

        @Test
        void saveWritesTheVersionedShape() throws IOException {
                newStore().save(List.of(usable("Home")));

                String json = Files.readString(tempDir.resolve("buttons.json"), UTF_8);
                assertTrue(json.contains("\"version\": 2"), "file should carry its schema version");
                assertTrue(json.contains("\"buttons\": ["), "file should carry the button list");
                assertTrue(json.contains("\"region\""), "v2 entries carry the placement region");
        }

        @Test
        void overwritingKeepsTheLastState() {
                InventoryButtonStore store = newStore();
                store.save(List.of(usable("First")));
                store.save(List.of(usable("Second"), usable("Third")));

                List<InventoryButton> loaded = store.load();

                assertEquals(2, loaded.size());
                List<String> names = loaded.stream().map(InventoryButton::name).toList();
                assertTrue(names.containsAll(List.of("Second", "Third")));
                org.junit.jupiter.api.Assertions.assertFalse(names.contains("First"));
        }

        // ---- The v1 → v2 migration ----

        @Test
        void v1TopCornersMigrateToTheTopRowInHorizontalOrder() throws IOException {
                Path file = tempDir.resolve("buttons.json");
                Files.writeString(file, """
                                {
                                  "version": 1,
                                  "buttons": [
                                    { "name": "A", "command": "spawn", "icon": "minecraft:compass",
                                      "anchor": "TOP_LEFT", "offsetX": 20, "offsetY": 5 },
                                    { "name": "B", "command": "spawn", "icon": "minecraft:paper",
                                      "anchor": "TOP_LEFT", "offsetX": 60, "offsetY": 5 },
                                    { "name": "C", "command": "spawn", "icon": "minecraft:book",
                                      "anchor": "TOP_RIGHT", "offsetX": -40, "offsetY": 5 }
                                  ]
                                }
                                """, UTF_8);

                List<InventoryButton> loaded = newStore().load();

                assertEquals(3, loaded.size());
                assertTrue(loaded.stream().allMatch(button -> button.region() == ButtonRegion.TOP));
                // Old horizontal offsets become orders: A at x=20 sorts before B at
                // x=60, and the right-anchored C (measured from the right edge)
                // sorts last; the repack then renumbers densely.
                assertEquals("A", loaded.get(0).name());
                assertEquals("B", loaded.get(1).name());
                assertEquals("C", loaded.get(2).name());
                assertEquals(0, loaded.get(0).order(), "orders are re-packed densely");
                assertEquals(1, loaded.get(1).order());
                assertEquals(2, loaded.get(2).order());
        }

        @Test
        void v1BottomCornersMigrateToTheBottomRow() throws IOException {
                Path file = tempDir.resolve("buttons.json");
                Files.writeString(file, """
                                {
                                  "version": 1,
                                  "buttons": [
                                    { "name": "A", "command": "spawn", "icon": "minecraft:compass",
                                      "anchor": "BOTTOM_LEFT", "offsetX": 4, "offsetY": 2 }
                                  ]
                                }
                                """, UTF_8);

                List<InventoryButton> loaded = newStore().load();

                assertEquals(1, loaded.size());
                assertEquals(ButtonRegion.BOTTOM, loaded.get(0).region());
        }

        @Test
        void v1ButtonsPushedPastTheRightEdgeMigrateToTheRightColumn() throws IOException {
                Path file = tempDir.resolve("buttons.json");
                Files.writeString(file, """
                                {
                                  "version": 1,
                                  "buttons": [
                                    { "name": "Side", "command": "spawn", "icon": "minecraft:compass",
                                      "anchor": "TOP_RIGHT", "offsetX": 22, "offsetY": 2 }
                                  ]
                                }
                                """, UTF_8);

                List<InventoryButton> loaded = newStore().load();

                assertEquals(1, loaded.size());
                assertEquals(ButtonRegion.RIGHT, loaded.get(0).region());
                assertEquals(0, loaded.get(0).order());
        }

        @Test
        void v1MigrationSurvivesTheRoundTripAsV2() throws IOException {
                Path file = tempDir.resolve("buttons.json");
                Files.writeString(file, """
                                {
                                  "version": 1,
                                  "buttons": [
                                    { "name": "A", "command": "spawn", "icon": "minecraft:compass",
                                      "anchor": "TOP_LEFT", "offsetX": 0, "offsetY": 0 }
                                  ]
                                }
                                """, UTF_8);
                InventoryButtonStore store = newStore();
                List<InventoryButton> loaded = store.load();

                store.save(loaded);

                assertTrue(Files.readString(file, UTF_8).contains("\"version\": 2"),
                                "the first save rewrites the file in v2 shape");
                assertEquals(loaded, store.load(), "the migration's output is already normalized");
        }
}
