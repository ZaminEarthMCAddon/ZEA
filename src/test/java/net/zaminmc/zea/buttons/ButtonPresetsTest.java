package net.zaminmc.zea.buttons;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ButtonPresetsTest {
        @Test
        void allThreeBuiltInPresetsExist() {
                var all = ButtonPresets.all();

                assertEquals(3, all.size());
                assertEquals(ButtonPresets.DEFAULT, all.get(0).id());
                assertEquals(ButtonPresets.EVERYWHERE, all.get(1).id());
                assertEquals(ButtonPresets.EMPTY, all.get(2).id());
                assertNotNull(ButtonPresets.byId(ButtonPresets.DEFAULT));
                assertNotNull(ButtonPresets.byId(ButtonPresets.EVERYWHERE));
                assertNotNull(ButtonPresets.byId(ButtonPresets.EMPTY));
        }

        @Test
        void presetButtonsAreInertTemplates() {
                for (ButtonPresets.Preset preset : ButtonPresets.all()) {
                        for (InventoryButton button : preset.buttons()) {
                                assertEquals("", button.command(),
                                                preset.id() + " preset must not invent commands");
                                assertFalse(button.isUsable(), "templates render nowhere until configured");
                                assertTrue(button.enabled());
                        }
                }
        }

        @Test
        void everywhereFillsEveryRegionAtCapacity() {
                Map<ButtonRegion, Integer> counts = new HashMap<>();
                for (InventoryButton button : ButtonPresets.byId(ButtonPresets.EVERYWHERE).buttons()) {
                        counts.merge(button.region(), 1, Integer::sum);
                }

                for (ButtonRegion region : ButtonRegion.values()) {
                        assertEquals(region.capacity(), counts.getOrDefault(region, 0),
                                        region + " should be full in the everywhere preset");
                }
        }

        @Test
        void defaultIsAModestStarterCanvas() {
                Map<ButtonRegion, Integer> counts = new HashMap<>();
                for (InventoryButton button : ButtonPresets.byId(ButtonPresets.DEFAULT).buttons()) {
                        counts.merge(button.region(), 1, Integer::sum);
                }

                assertEquals(4, counts.getOrDefault(ButtonRegion.TOP, 0));
                assertEquals(4, counts.getOrDefault(ButtonRegion.BOTTOM, 0));
                assertEquals(4, counts.getOrDefault(ButtonRegion.RIGHT, 0));
                assertFalse(counts.containsKey(ButtonRegion.LEFT));
                assertFalse(counts.containsKey(ButtonRegion.UNDER_SEARCH));
        }

        @Test
        void emptyHasNoButtons() {
                assertTrue(ButtonPresets.byId(ButtonPresets.EMPTY).buttons().isEmpty());
        }

        @Test
        void presetButtonsHaveUniqueIdentities() {
                List<InventoryButton> everywhere = ButtonPresets.byId(ButtonPresets.EVERYWHERE).buttons();
                assertEquals(everywhere.size(),
                                everywhere.stream().map(InventoryButton::id).distinct().count());
        }

        @Test
        void everyLoadBuildsFreshTemplates() {
                UUID first = ButtonPresets.byId(ButtonPresets.DEFAULT).buttons().get(0).id();
                UUID second = ButtonPresets.byId(ButtonPresets.DEFAULT).buttons().get(0).id();

                assertFalse(first.equals(second), "each load mints fresh identities — applying twice never collides");
        }
}
