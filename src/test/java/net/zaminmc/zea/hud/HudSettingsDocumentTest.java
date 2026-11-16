package net.zaminmc.zea.hud;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The HUD settings document: defaults fill missing elements, unknown
 * elements carry over (a future element's settings survive a
 * downgrade), and the dirty check only fires on real changes.
 */
class HudSettingsDocumentTest {
        private static HudElementSpec spec(String id) {
                return new HudElementSpec(
                                net.minecraft.resources.Identifier.fromNamespaceAndPath("zea", id),
                                id, 100, 100, HudAnchor.TOP_LEFT, 0, 0, true);
        }

        @Test
        void missingElementsFallBackToDefaults() {
                HudElementHandle handle = new HudElementHandle(spec("minimap"), null, null);
                var document = HudSettingsDocument.merge(List.of(handle), new HudSettingsDocument());
                HudElementState state = document.state(handle.spec().id());
                assertTrue(state.visible());
                assertEquals(HudAnchor.TOP_LEFT, state.anchor());
        }

        @Test
        void previousStatesCarryOver() {
                HudElementHandle handle = new HudElementHandle(spec("minimap"), null, null);
                var previous = new HudSettingsDocument();
                HudElementState saved = HudElementState.ofDefaults(handle.spec());
                saved.visible(false);
                saved.anchor(HudAnchor.BOTTOM_LEFT);
                saved.offsetX(21);
                saved.offsetY(34);
                saved.scalePercent(150);
                previous.elements.put(handle.spec().id().toString(), saved);

                var document = HudSettingsDocument.merge(List.of(handle), previous);
                HudElementState state = document.state(handle.spec().id());
                assertFalse(state.visible());
                assertEquals(HudAnchor.BOTTOM_LEFT, state.anchor());
                assertEquals(21, state.offsetX());
                assertEquals(34, state.offsetY());
                assertEquals(150, state.scalePercent());
        }

        @Test
        void unknownElementEntriesSurvive() {
                var document = HudSettingsDocument.merge(List.of(), new HudSettingsDocument());
                document.elements.put("zea:hud/future", new HudElementState());
                var merged = HudSettingsDocument.merge(List.of(), document);
                assertTrue(merged.elements.containsKey("zea:hud/future"),
                                "a future element's settings must survive a downgrade");
        }

        @Test
        void dirtyCheckFiresOnlyOnRealChanges() {
                HudElementHandle handle = new HudElementHandle(spec("minimap"),
                                null, HudElementState.ofDefaults(spec("minimap")));
                var saved = HudSettingsDocument.merge(List.of(handle), new HudSettingsDocument());

                var current = HudSettingsDocument.merge(List.of(handle), null);
                assertFalse(saved.differsFrom(current), "unchanged states must not be dirty");

                handle.state().offsetX(12);
                var changed = HudSettingsDocument.merge(List.of(handle), null);
                changed.elements.put(handle.spec().id().toString(), HudElementState.copyOf(handle.state()));
                assertTrue(changed.differsFrom(saved), "a moved element is dirty");
        }
}
