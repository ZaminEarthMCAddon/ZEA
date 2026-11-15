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
		InventoryButton home = InventoryButton.newButton(1).withCommand("home");

		buttons.upsert(home);
		buttons.upsert(home.withCommand("town spawn").withPosition(10, 20, true, false));

		assertEquals(1, buttons.buttons().size());
		assertEquals("town spawn", buttons.buttons().get(0).command());
		// And it is the stored form that came back.
		assertEquals("town spawn", reload().get(0).command());
		assertEquals(10, reload().get(0).x());
		assertTrue(reload().get(0).anchorRight());
	}

	@Test
	void upsertAppendsUnknownIds() throws IOException {
		InventoryButtons buttons = buttons();
		buttons.upsert(InventoryButton.newButton(1).withCommand("one"));

		buttons.upsert(InventoryButton.newButton(2).withCommand("two"));

		assertEquals(2, buttons.buttons().size());
		assertEquals(2, reload().size());
	}

	@Test
	void deleteRemovesByIdAndPersists() throws IOException {
		InventoryButtons buttons = buttons();
		InventoryButton first = InventoryButton.newButton(1).withCommand("one");
		InventoryButton second = InventoryButton.newButton(2).withCommand("two");
		buttons.upsert(first);
		buttons.upsert(second);

		buttons.delete(first.id());

		assertEquals(1, buttons.buttons().size());
		assertEquals("two", reload().get(0).command());
	}

	@Test
	void editorMovesPersistAnchorsToo() throws IOException {
		InventoryButtons buttons = buttons();
		InventoryButton home = InventoryButton.newButton(1).withCommand("home");
		buttons.upsert(home);

		assertTrue(buttons.move(home.id(), -20, 168, false, true));

		InventoryButton reloaded = reload().get(0);
		assertEquals(-20, reloaded.x());
		assertEquals(168, reloaded.y());
		assertTrue(reloaded.anchorBottom());
	}

	@Test
	void createdButtonsPersistImmediately() throws IOException {
		InventoryButtons buttons = buttons();

		// The editor's right-click-to-create flow.
		buttons.add(InventoryButton.newButton(1)
				.withCommand("home")
				.withPosition(90, 60, false, false));

		InventoryButton reloaded = reload().get(0);
		assertEquals("home", reloaded.command());
		assertEquals(90, reloaded.x());
		assertEquals(60, reloaded.y());
	}

	private List<InventoryButton> reload() {
		return new InventoryButtons(file(), new ButtonActivator(command -> { }, 0)).buttons();
	}
}
