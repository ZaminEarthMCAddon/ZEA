package net.zaminmc.zea.buttons;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ButtonPresetsTest {
	@Test
	void presetsCarryTheirIdsInEditorOrder() {
		List<String> ids = ButtonPresets.all().stream().map(ButtonPresets.Preset::id).toList();

		assertEquals(List.of("default", "everywhere", "empty"), ids);
		assertNotNull(ButtonPresets.byId("default"));
		assertNotNull(ButtonPresets.byId("everywhere"));
		assertNotNull(ButtonPresets.byId("empty"));
		assertNull(ButtonPresets.byId("nonsense"));
	}

	@Test
	void templateButtonsAreInertButPositioned() {
		for (ButtonPresets.Preset preset : ButtonPresets.all()) {
			for (InventoryButton button : preset.buttons()) {
				assertEquals("", button.command(), preset.id() + " buttons carry no commands");
				assertTrue(button.enabled());
				assertTrue(button.icon().startsWith("minecraft:"), "a recognizable icon");
				assertFalse(button.icon().isBlank());
				assertNotNull(ButtonGeometry.Zone.byName(button.zone()),
						preset.id() + " buttons must name a real zone");
			}
		}
	}

	@Test
	void defaultIsAModestRightColumn() {
		List<InventoryButton> buttons = ButtonPresets.byId("default").buttons();

		assertEquals(4, buttons.size());
		for (int i = 0; i < buttons.size(); i++) {
			InventoryButton button = buttons.get(i);
			assertEquals("RIGHT", button.zone(), "the starter column lives in the right zone");
			assertEquals(i, button.order(), "the column fills its slots in order");
		}
	}

	@Test
	void everywhereFillsTheFullRingOfZones() {
		List<InventoryButton> buttons = ButtonPresets.byId("everywhere").buttons();

		assertEquals(32, buttons.size());
		// Each of the four ring zones fills its eight slots exactly once.
		for (ButtonGeometry.Zone zone : List.of(ButtonGeometry.Zone.TOP, ButtonGeometry.Zone.RIGHT,
				ButtonGeometry.Zone.UNDER_HOTBAR, ButtonGeometry.Zone.LEFT)) {
			for (int order = 0; order < 8; order++) {
				final int slot = order;
				assertTrue(buttons.stream().anyMatch(b -> zone.name().equals(b.zone()) && b.order() == slot),
						zone + " slot " + order + " must be filled by the everywhere preset");
			}
		}
		// And no two of them share a zone slot — the zones' own discipline.
		for (int i = 0; i < buttons.size(); i++) {
			for (int j = i + 1; j < buttons.size(); j++) {
				boolean sameZone = buttons.get(i).zone().equals(buttons.get(j).zone());
				boolean sameOrder = buttons.get(i).order() == buttons.get(j).order();
				assertFalse(sameZone && sameOrder, "ring buttons never share a slot");
			}
		}
	}

	@Test
	void emptyHasNoButtons() {
		assertTrue(ButtonPresets.byId("empty").buttons().isEmpty());
	}

	@Test
	void everyButtonHasAUniqueIdentity() {
		List<InventoryButton> buttons = ButtonPresets.byId("everywhere").buttons();
		long distinct = buttons.stream().map(InventoryButton::id).distinct().count();

		assertEquals(buttons.size(), distinct);
		assertEquals(32, buttons.stream().map(b -> b.id()).filter(id -> id instanceof UUID).count());
	}
}
