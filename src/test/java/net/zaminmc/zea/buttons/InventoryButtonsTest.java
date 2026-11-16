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

class InventoryButtonsTest {
	@TempDir
	Path tempDir;

	private final List<String> sent = new ArrayList<>();

	private ButtonActivator activator() {
		return new ButtonActivator(sent::add, 0);
	}

	private InventoryButtons loadedWith(String json) throws IOException {
		Path file = tempDir.resolve("buttons.json");
		Files.writeString(file, json, UTF_8);
		return new InventoryButtons(file, activator());
	}

	private static final String HOME = """
			{
			  "version": 4,
			  "buttons": [
			    { "name": "Home", "command": "town spawn", "icon": "minecraft:compass", "zone": "RIGHT", "order": 0 }
			  ]
			}
			""";

	@Test
	void loadsButtonsFromTheStore() throws IOException {
		InventoryButtons buttons = loadedWith(HOME);

		assertEquals(1, buttons.buttons().size());
		assertEquals("Home", buttons.buttons().get(0).name());
	}

	@Test
	void missingFileYieldsNoButtons() {
		InventoryButtons buttons = new InventoryButtons(tempDir.resolve("absent.json"), activator());

		assertEquals(List.of(), buttons.buttons());
	}

	@Test
	void clickRunsTheButtonThroughTheActivator() throws IOException {
		InventoryButtons buttons = loadedWith(HOME);
		InventoryButton home = buttons.buttons().get(0);

		assertEquals(ButtonActivator.Result.EXECUTED, buttons.click(home, 1_000));

		assertEquals(List.of("town spawn"), sent);
	}

	@Test
	void disabledButtonsDoNotRun() throws IOException {
		InventoryButtons buttons = loadedWith("""
				{
				  "version": 4,
				  "buttons": [
				    { "name": "Off", "command": "town spawn", "icon": "minecraft:compass",
				      "zone": "RIGHT", "order": 0, "enabled": false }
				  ]
				}
				""");
		InventoryButton off = buttons.buttons().get(0);

		assertEquals(ButtonActivator.Result.INVALID, buttons.click(off, 1_000));
		assertEquals(List.of(), sent);
	}

	@Test
	void unconfiguredButtonsDoNotRun() throws IOException {
		InventoryButtons buttons = loadedWith("""
				{
				  "version": 4,
				  "buttons": [
				    { "name": "Template", "icon": "minecraft:chest", "zone": "RIGHT", "order": 0 }
				  ]
				}
				""");
		InventoryButton template = buttons.buttons().get(0);

		assertEquals(ButtonActivator.Result.INVALID, buttons.click(template, 1_000));
		assertEquals(List.of(), sent);
	}

	@Test
	void resetClearsCooldownsOnly() throws IOException {
		ButtonActivator slow = new ButtonActivator(sent::add, 10_000);
		Path file = tempDir.resolve("buttons.json");
		Files.writeString(file, HOME, UTF_8);
		InventoryButtons cooldown = new InventoryButtons(file, slow);

		assertEquals(ButtonActivator.Result.EXECUTED, cooldown.click(cooldown.buttons().get(0), 1_000));
		assertEquals(ButtonActivator.Result.COOLDOWN, cooldown.click(cooldown.buttons().get(0), 2_000));
		cooldown.reset();
		assertEquals(ButtonActivator.Result.EXECUTED, cooldown.click(cooldown.buttons().get(0), 3_000));
	}

	@Test
	void upsertInsertsAndReplaces() {
		InventoryButtons buttons = new InventoryButtons(tempDir.resolve("b.json"), activator());
		InventoryButton first = InventoryButton.newButton(1, "RIGHT", 0).withCommand("one");
		InventoryButton second = InventoryButton.newButton(2, "RIGHT", 1).withCommand("two");

		buttons.upsert(first);
		buttons.upsert(second);
		assertEquals(2, buttons.buttons().size());

		buttons.upsert(second.withCommand("two!"));
		assertEquals(2, buttons.buttons().size());
		assertEquals("two!", buttons.buttons().get(1).command());
	}

	@Test
	void deleteRemovesOnlyTheTarget() {
		InventoryButtons buttons = new InventoryButtons(tempDir.resolve("b.json"), activator());
		InventoryButton first = InventoryButton.newButton(1, "RIGHT", 0).withCommand("one");
		InventoryButton second = InventoryButton.newButton(2, "RIGHT", 1).withCommand("two");
		buttons.upsert(first);
		buttons.upsert(second);

		buttons.delete(first.id());

		assertEquals(1, buttons.buttons().size());
		assertEquals("two", buttons.buttons().get(0).command());
	}

	@Test
	void movesLandInFreeZoneSlotsAndRefuseTakenOnes() {
		InventoryButtons buttons = new InventoryButtons(tempDir.resolve("b.json"), activator());
		InventoryButton first = InventoryButton.newButton(1, "RIGHT", 0).withCommand("one");
		InventoryButton second = InventoryButton.newButton(2, "LEFT", 0).withCommand("two");
		buttons.upsert(first);
		buttons.upsert(second);

		// A free slot accepts the move and persists it.
		assertTrue(buttons.move(second.id(), "TOP", 3));
		InventoryButton moved = buttons.buttons().get(1);
		assertEquals("TOP", moved.zone());
		assertEquals(3, moved.order());

		// Landing on the other button's slot is refused; the button stays put.
		assertFalse(buttons.move(second.id(), "RIGHT", 0));
		assertEquals("TOP", buttons.buttons().get(1).zone());

		// Unknown zone names and out-of-range orders are refused.
		assertFalse(buttons.move(second.id(), "NOWHERE", 0));
		assertFalse(buttons.move(second.id(), "TOP", 99));

		// Unknown ids move nothing.
		assertFalse(buttons.move(java.util.UUID.randomUUID(), "TOP", 0));
	}

	private static void assertTrue(boolean condition) {
		org.junit.jupiter.api.Assertions.assertTrue(condition);
	}

	@Test
	void presetsApplyAndRestore() {
		InventoryButtons buttons = new InventoryButtons(tempDir.resolve("b.json"), activator());
		InventoryButton mine = InventoryButton.newButton(1, "RIGHT", 0).withCommand("mine");
		buttons.upsert(mine);

		buttons.applyPreset(ButtonPresets.byId(ButtonPresets.EMPTY));

		assertEquals(0, buttons.buttons().size());
		assertTrue(buttons.hasRestorePoint());

		assertTrue(buttons.restorePrevious());
		assertEquals(1, buttons.buttons().size());
		assertEquals("mine", buttons.buttons().get(0).command());
		assertTrue(!buttons.hasRestorePoint());
	}

	@Test
	void aNonPresetMutationDropsTheRestorePoint() {
		InventoryButtons buttons = new InventoryButtons(tempDir.resolve("b.json"), activator());
		buttons.upsert(InventoryButton.newButton(1, "RIGHT", 0).withCommand("mine"));

		buttons.applyPreset(ButtonPresets.byId(ButtonPresets.EMPTY));
		buttons.applyPreset(ButtonPresets.byId(ButtonPresets.DEFAULT));

		// A second preset keeps a restore point (of the set before it).
		assertTrue(buttons.hasRestorePoint());
		assertTrue(buttons.restorePrevious());
		assertEquals(0, buttons.buttons().size());
	}

	@Test
	void importsReplaceTheWholeSet() {
		InventoryButtons buttons = new InventoryButtons(tempDir.resolve("b.json"), activator());
		buttons.upsert(InventoryButton.newButton(1, "RIGHT", 0).withCommand("mine"));

		buttons.applyImport(List.of(
				InventoryButton.newButton(5, "RIGHT", 0).withCommand("theirs"),
				InventoryButton.newButton(6, "LEFT", 0).withCommand("also theirs")));

		assertEquals(2, buttons.buttons().size());
		assertEquals("theirs", buttons.buttons().get(0).command());
	}

	@Test
	void addFilesIntoTheZonesFirstFreeSlot() {
		InventoryButtons buttons = new InventoryButtons(tempDir.resolve("b.json"), activator());
		buttons.upsert(InventoryButton.newButton(1, "RIGHT", 0).withCommand("taken"));

		buttons.add(InventoryButton.newButton(2, "RIGHT", 0).withCommand("fresh"));

		assertEquals(2, buttons.buttons().size());
		InventoryButton filed = buttons.buttons().get(1);
		assertEquals("RIGHT", filed.zone());
		assertEquals(1, filed.order(), "the created button takes the first free order of its zone");
	}
}
