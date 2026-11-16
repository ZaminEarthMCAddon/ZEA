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

        private Path file() {
                return tempDir.resolve("buttons.json");
        }

        private InventoryButtonStore newStore() {
                return new InventoryButtonStore(file());
        }

        private static InventoryButton usable(String name) {
                return InventoryButton.newButton(1, "RIGHT", 0).withName(name).withCommand("town spawn");
        }

        private void write(String json) throws IOException {
                Files.write(file(), json.getBytes(UTF_8));
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
                                usable("Trade").withZone("UNDER_HOTBAR", 1).withEnabled(false),
                                usable("Hub").withText("Hub").withCloseOnExecute(true).withStyle(3));

                store.save(buttons);
                List<InventoryButton> loaded = store.load();

                assertEquals(3, loaded.size());
                InventoryButton home = loaded.stream().filter(b -> b.name().equals("Home")).findFirst().orElseThrow();
                InventoryButton trade = loaded.stream().filter(b -> b.name().equals("Trade")).findFirst().orElseThrow();
                InventoryButton hub = loaded.stream().filter(b -> b.name().equals("Hub")).findFirst().orElseThrow();
                assertEquals("minecraft:compass", home.icon());
                assertEquals("RIGHT", home.zone(), "the new-button default zone survives");
                assertEquals("UNDER_HOTBAR", trade.zone());
                assertEquals(1, trade.order());
                assertFalse(trade.enabled());
                assertEquals("Hub", hub.text());
                assertTrue(hub.closeOnExecute());
                assertEquals(3, hub.style());
                // And a second round trip is the identity.
                store.save(loaded);
                assertEquals(loaded, store.load());
        }

        @Test
        void versionThreeFilesLoadDirectly() throws IOException {
                write("""
                                {"version":3,"buttons":[
                                  {"id":"11111111-1111-1111-1111-111111111111","name":"Home","command":"home",
                                   "icon":"minecraft:compass","x":2,"y":4,"anchorRight":true,"anchorBottom":false,
                                   "style":2,"closeOnExecute":false,"enabled":true}
                                ]}
                                """);

                List<InventoryButton> loaded = newStore().load();

                assertEquals(1, loaded.size());
                assertEquals("Home", loaded.get(0).name());
                // An anchored-right free position classifies into the right zone.
                assertEquals("RIGHT", loaded.get(0).zone());
                assertEquals(0, loaded.get(0).order());
        }

        @Test
        void partialEntriesDegradeInsteadOfFailing() throws IOException {
                write("""
                                {"version":3,"buttons":[
                                  {"name":"Home","command":"home"},
                                  null,
                                  {"name":"Broken","command":"","icon":""}
                                ]}
                                """);

                List<InventoryButton> loaded = newStore().load();

                // A null entry is dropped (it carries nothing to keep); a damaged
                // but present entry degrades into inert data instead of failing
                // the whole file. Entries without any position classify into the
                // under-hotbar row (the unanchored default).
                assertEquals(2, loaded.size());
                assertEquals("Home", loaded.get(0).name());
                assertEquals("UNDER_HOTBAR", loaded.get(0).zone());
                assertFalse(loaded.get(1).isUsable());
        }

        @Test
        void duplicateIdentitiesAreReissued() throws IOException {
                write("""
                                {"version":3,"buttons":[
                                  {"id":"11111111-1111-1111-1111-111111111111","name":"A","command":"a","icon":"minecraft:stone"},
                                  {"id":"11111111-1111-1111-1111-111111111111","name":"B","command":"b","icon":"minecraft:stone"}
                                ]}
                                """);

                List<InventoryButton> loaded = newStore().load();

                assertEquals(2, loaded.size());
                long distinct = loaded.stream().map(InventoryButton::id).distinct().count();
                assertEquals(2, distinct);
                // Both entries classify into the same zone; orders stay distinct.
                assertEquals("UNDER_HOTBAR", loaded.get(0).zone());
                assertEquals(0, loaded.get(0).order());
                assertEquals("UNDER_HOTBAR", loaded.get(1).zone());
                assertEquals(1, loaded.get(1).order());
        }

        @Test
        void versionOneCornerAnchorsClassifyIntoTheMatchingZones() throws IOException {
                write("""
                                {"version":1,"buttons":[
                                  {"id":"11111111-1111-1111-1111-111111111111","name":"TL","command":"a","icon":"minecraft:stone",
                                   "anchor":"TOP_LEFT","offsetX":10,"offsetY":-20},
                                  {"name":"BR","command":"b","icon":"minecraft:stone",
                                   "anchor":"BOTTOM_RIGHT","offsetX":30,"offsetY":40}
                                ]}
                                """);

                List<InventoryButton> loaded = newStore().load();

                assertEquals(2, loaded.size());
                // The top-left corner button rides above the GUI (y < 0 unanchored).
                assertEquals("TOP", loaded.get(0).zone());
                // The bottom-right corner button was anchored on both edges — the
                // above-search row is where it lived.
                assertEquals("ABOVE_SEARCH", loaded.get(1).zone());
        }

        @Test
        void versionOneDefaultsFillMissingOffsets() throws IOException {
                write("""
                                {"version":0,"buttons":[
                                  {"name":"Old","command":"old","icon":"minecraft:stone"}
                                ]}
                                """);

                List<InventoryButton> loaded = newStore().load();

                // The v1 defaults (22, 2) are unanchored canvas pixels — they
                // classify into the under-hotbar row.
                assertEquals(1, loaded.size());
                assertEquals("UNDER_HOTBAR", loaded.get(0).zone());
        }

        @Test
        void versionTwoRegionsResolveToTheZonesTheyLivedIn() throws IOException {
                write("""
                                {"version":2,"buttons":[
                                  {"id":"11111111-1111-1111-1111-111111111111","name":"Top0","command":"a","icon":"minecraft:stone",
                                   "region":"TOP","order":0},
                                  {"name":"Right3","command":"b","icon":"minecraft:stone",
                                   "region":"RIGHT","order":3},
                                  {"name":"Left1","command":"c","icon":"minecraft:stone",
                                   "region":"LEFT","order":1}
                                ]}
                                """);

                List<InventoryButton> loaded = newStore().load();

                assertEquals(3, loaded.size());
                // The migrated list groups per zone in enum order (TOP, LEFT, RIGHT).
                assertEquals("TOP", loaded.get(0).zone());
                assertEquals("LEFT", loaded.get(1).zone());
                assertEquals("RIGHT", loaded.get(2).zone());
        }

        @Test
        void versionTwoBottomRowsClassifyAboveTheSearchBar() throws IOException {
                write("""
                                {"version":2,"buttons":[
                                  {"name":"Under","command":"a","icon":"minecraft:stone","region":"UNDER_SEARCH","order":0},
                                  {"name":"Bottom","command":"b","icon":"minecraft:stone","region":"BOTTOM","order":2}
                                ]}
                                """);

                List<InventoryButton> loaded = newStore().load();

                // Both bottom regions sat under the GUI, anchored to its bottom
                // edge — the above-search row is their zone, orders re-assigned
                // sequentially in stored order.
                assertEquals("ABOVE_SEARCH", loaded.get(0).zone());
                assertEquals(0, loaded.get(0).order());
                assertEquals("ABOVE_SEARCH", loaded.get(1).zone());
                assertEquals(1, loaded.get(1).order());
        }

        @Test
        void legacyFreePositionsClassifyIntoTheirZones() throws IOException {
                write("""
                                {"version":3,"buttons":[
                                  {"name":"BothAnchors","command":"a","icon":"minecraft:stone","x":0,"y":0,"anchorRight":true,"anchorBottom":true},
                                  {"name":"RightOnly","command":"b","icon":"minecraft:stone","x":2,"y":10,"anchorRight":true,"anchorBottom":false},
                                  {"name":"BottomOnly","command":"c","icon":"minecraft:stone","x":10,"y":2,"anchorRight":false,"anchorBottom":true},
                                  {"name":"West","command":"d","icon":"minecraft:stone","x":-20,"y":60,"anchorRight":false,"anchorBottom":false},
                                  {"name":"North","command":"e","icon":"minecraft:stone","x":40,"y":-20,"anchorRight":false,"anchorBottom":false},
                                  {"name":"Crafting","command":"f","icon":"minecraft:stone","x":100,"y":60,"anchorRight":false,"anchorBottom":false},
                                  {"name":"Corner","command":"g","icon":"minecraft:stone","x":158,"y":36,"anchorRight":false,"anchorBottom":false},
                                  {"name":"Floor","command":"h","icon":"minecraft:stone","x":40,"y":100,"anchorRight":false,"anchorBottom":false}
                                ]}
                                """);

                List<InventoryButton> loaded = newStore().load();

                assertEquals(8, loaded.size());
                assertEquals("ABOVE_SEARCH", zoneOf(loaded, "BothAnchors"), "right + bottom anchors = the search-bar row");
                assertEquals("RIGHT", zoneOf(loaded, "RightOnly"), "right anchor = the right column");
                assertEquals("ABOVE_SEARCH", zoneOf(loaded, "BottomOnly"), "bottom anchor = the search-bar row");
                assertEquals("LEFT", zoneOf(loaded, "West"), "x < 0 = the left column");
                assertEquals("TOP", zoneOf(loaded, "North"), "y < 0 = the top row");
                assertEquals("UNDER_CRAFTING", zoneOf(loaded, "Crafting"), "the crafting area's pixels");
                assertEquals("TOP_RIGHT", zoneOf(loaded, "Corner"), "the canvas's top-right corner");
                assertEquals("UNDER_HOTBAR", zoneOf(loaded, "Floor"), "everything else = under the hotbar");
        }

        @Test
        void legacyOrdersAreSequentialPerZoneInStoredOrder() throws IOException {
                write("""
                                {"version":3,"buttons":[
                                  {"name":"A","command":"a","icon":"minecraft:stone","x":2,"y":4,"anchorRight":true},
                                  {"name":"B","command":"b","icon":"minecraft:stone","x":-20,"y":60},
                                  {"name":"C","command":"c","icon":"minecraft:stone","x":6,"y":6,"anchorRight":true},
                                  {"name":"D","command":"d","icon":"minecraft:stone","x":-22,"y":80}
                                ]}
                                """);

                List<InventoryButton> loaded = newStore().load();

                assertEquals(4, loaded.size());
                // A → C share the right zone (orders 0, 1); B → D share the left
                // column (orders 0, 1). The migrated list is grouped per zone in
                // enum order, and each zone's members are sequential in stored order.
                assertEquals("LEFT", loaded.get(0).zone());
                assertEquals(0, loaded.get(0).order());
                assertEquals("LEFT", loaded.get(1).zone());
                assertEquals(1, loaded.get(1).order());
                assertEquals("RIGHT", loaded.get(2).zone());
                assertEquals(0, loaded.get(2).order());
                assertEquals("RIGHT", loaded.get(3).zone());
                assertEquals(1, loaded.get(3).order());
        }

        @Test
        void unknownZoneNamesInCurrentFilesFallBackToTheDefaultZone() throws IOException {
                write("""
                                {"version":4,"buttons":[
                                  {"id":"11111111-1111-1111-1111-111111111111","name":"Lost","command":"home",
                                   "icon":"minecraft:compass","zone":"NOWHERE","order":3}
                                ]}
                                """);

                List<InventoryButton> loaded = newStore().load();

                assertEquals(1, loaded.size());
                // A stale hand edit must not strand a button nowhere: it is
                // re-filed into the default zone's first slot.
                assertEquals("Lost", loaded.get(0).name());
                assertEquals(InventoryButton.DEFAULT_ZONE, loaded.get(0).zone());
                assertEquals(0, loaded.get(0).order());
        }

        @Test
        void currentVersionFilesLoadTheirZonesAsAuthored() throws IOException {
                write("""
                                {"version":4,"buttons":[
                                  {"id":"11111111-1111-1111-1111-111111111111","name":"Kept","command":"home",
                                   "icon":"minecraft:compass","zone":"LEFT","order":2}
                                ]}
                                """);

                List<InventoryButton> loaded = newStore().load();

                assertEquals(1, loaded.size());
                assertEquals("LEFT", loaded.get(0).zone());
                assertEquals(2, loaded.get(0).order());
        }

        @Test
        void savingRewritesTheFileInTheCurrentShape() throws IOException {
                InventoryButtonStore store = newStore();
                store.save(List.of(usable("Home")));

                String json = Files.readString(file());
                assertTrue(json.contains("\"version\": " + InventoryButtonStore.CURRENT_VERSION));
                assertTrue(json.contains("\"zone\""));
                assertTrue(json.contains("\"order\""));
                assertTrue(InventoryButtonStore.CURRENT_VERSION == 4, "the dev.36 zone model is version 4");
        }

        private static String zoneOf(List<InventoryButton> buttons, String name) {
                return buttons.stream().filter(b -> b.name().equals(name)).findFirst().orElseThrow().zone();
        }
}
