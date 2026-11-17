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

/**
 * The editor's persistence flows: every mutation the editor screen
 * performs — create, edit-in-place, move, delete — lands in the file
 * through the safe-write store, so a crash mid-edit loses at most the
 * working copy in front of the screen.
 */
class InventoryButtonsEditingTest {
	@TempDir
	Path tempDir;

	private Path file() {
		return tempDir.resolve("buttons.json");
	}

	private InventoryButtons buttons() {
		return new InventoryButtons(file(), new ButtonActivator(command -> { }, 0));
	}

	@Test
	void upsertReplacesByIdAndPersists() throws IOException {
		InventoryButtons buttons = buttons();
		InventoryButton home = InventoryButton.newButton(1, "RIGHT", 0).withCommand("home");

		buttons.upsert(home);
		buttons.upsert(home.withCommand("town spawn").withZone("TOP", 2));

		assertEquals(1, buttons.buttons().size());
		assertEquals("town spawn", buttons.buttons().get(0).command());
		// And it is the stored form that came back.
		assertEquals("town spawn", reload().get(0).command());
		assertEquals("TOP", reload().get(0).zone());
		assertEquals(2, reload().get(0).order());
	}

	@Test
	void upsertAppendsUnknownIds() throws IOException {
		InventoryButtons buttons = buttons();
		buttons.upsert(InventoryButton.newButton(1, "RIGHT", 0).withCommand("one"));

		buttons.upsert(InventoryButton.newButton(2, "RIGHT", 1).withCommand("two"));

		assertEquals(2, buttons.buttons().size());
		assertEquals(2, reload().size());
	}

	@Test
	void deleteRemovesByIdAndPersists() throws IOException {
		InventoryButtons buttons = buttons();
		InventoryButton first = InventoryButton.newButton(1, "RIGHT", 0).withCommand("one");
		InventoryButton second = InventoryButton.newButton(2, "RIGHT", 1).withCommand("two");
		buttons.upsert(first);
		buttons.upsert(second);

		buttons.delete(first.id());

		assertEquals(1, buttons.buttons().size());
		assertEquals("two", reload().get(0).command());
	}

	@Test
	void editorMovesPersistTheZoneSlot() throws IOException {
		InventoryButtons buttons = buttons();
		InventoryButton home = InventoryButton.newButton(1, "RIGHT", 0).withCommand("home");
		buttons.upsert(home);

		assertTrue(buttons.move(home.id(), "LEFT", 3));

		InventoryButton reloaded = reload().get(0);
		assertEquals("LEFT", reloaded.zone());
		assertEquals(3, reloaded.order());
	}

	@Test
	void aMoveIntoAnOccupiedSlotIsRefused() throws IOException {
		InventoryButtons buttons = buttons();
		InventoryButton first = InventoryButton.newButton(1, "RIGHT", 0).withCommand("one");
		InventoryButton second = InventoryButton.newButton(2, "LEFT", 0).withCommand("two");
		buttons.upsert(first);
		buttons.upsert(second);

		assertFalse(buttons.move(second.id(), "RIGHT", 0), "the slot is taken");
		assertEquals("LEFT", reload().get(1).zone(), "a refused move leaves the button where it was");
	}

	@Test
	void createdButtonsAreFiledIntoTheirZone() throws IOException {
		InventoryButtons buttons = buttons();

		// The editor's right-click-to-create flow: the coordinator picks
		// the zone's first free slot.
		buttons.add(InventoryButton.newButton(1, "UNDER_HOTBAR", 0)
				.withCommand("home"));

		InventoryButton reloaded = reload().get(0);
		assertEquals("home", reloaded.command());
		assertEquals("UNDER_HOTBAR", reloaded.zone());
		assertEquals(0, reloaded.order());
	}

	@Test
	void aCreatedButtonFallsBackToTheRightColumnWithoutAZone() throws IOException {
		InventoryButtons buttons = buttons();

		buttons.add(InventoryButton.newButton(1, null, 0).withCommand("home"));

		InventoryButton reloaded = reload().get(0);
		assertEquals("RIGHT", reloaded.zone());
		assertEquals(0, reloaded.order());
	}

	private List<InventoryButton> reload() {
		return new InventoryButtons(file(), new ButtonActivator(command -> { }, 0)).buttons();
	}
}
