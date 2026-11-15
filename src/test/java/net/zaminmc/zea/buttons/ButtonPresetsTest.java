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
			}
		}
	}

	@Test
	void defaultIsAModestRightEdgeColumn() {
		List<InventoryButton> buttons = ButtonPresets.byId("default").buttons();

		assertEquals(4, buttons.size());
		for (InventoryButton button : buttons) {
			assertTrue(button.anchorRight(), "the starter column hugs the GUI's right edge");
			assertEquals(ButtonGeometry.OUTER_PADDING, button.x());
		}
	}

	@Test
	void everywhereFillsTheRingAroundTheCanvas() {
		List<InventoryButton> buttons = ButtonPresets.byId("everywhere").buttons();

		assertEquals(32, buttons.size());
		// The ring's rows and columns never enter the canvas.
		for (InventoryButton button : buttons) {
			boolean insideX = !button.anchorRight() && button.x() >= 0
					&& button.x() + ButtonGeometry.SIZE <= ButtonGeometry.CANVAS_WIDTH;
			boolean insideY = !button.anchorBottom() && button.y() >= 0
					&& button.y() + ButtonGeometry.SIZE <= ButtonGeometry.CANVAS_HEIGHT;
			assertFalse(insideX && insideY, "a ring button sits outside the canvas");
		}
		// And no two of them overlap.
		for (int i = 0; i < buttons.size(); i++) {
			for (int j = i + 1; j < buttons.size(); j++) {
				int ax = buttons.get(i).x() + (buttons.get(i).anchorRight() ? ButtonGeometry.CANVAS_WIDTH : 0);
				int ay = buttons.get(i).y() + (buttons.get(i).anchorBottom() ? ButtonGeometry.CANVAS_HEIGHT : 0);
				int bx = buttons.get(j).x() + (buttons.get(j).anchorRight() ? ButtonGeometry.CANVAS_WIDTH : 0);
				int by = buttons.get(j).y() + (buttons.get(j).anchorBottom() ? ButtonGeometry.CANVAS_HEIGHT : 0);
				assertFalse(Math.abs(ax - bx) < ButtonGeometry.SIZE && Math.abs(ay - by) < ButtonGeometry.SIZE,
						"ring buttons never overlap");
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
