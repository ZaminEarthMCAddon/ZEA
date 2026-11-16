package net.zaminmc.zea.buttons;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InventoryButtonTest {
	private InventoryButton button() {
		return new InventoryButton(UUID.randomUUID(), "Home", "home", "minecraft:compass", "", "",
				"RIGHT", 2, 3, false, true);
	}

	@Test
	void defaultsRepairMissingFields() {
		InventoryButton bare = new InventoryButton(null, null, null, null, null, null,
				null, 0, 0, false, true);

		assertNotEquals(null, bare.id());
		assertEquals(InventoryButton.DEFAULT_NAME, bare.name());
		assertEquals("", bare.command());
		assertEquals("", bare.icon());
		assertTrue(bare.enabled());
	}

	@Test
	void blankAndUnknownZonesFallBackToTheDefaultZone() {
		assertEquals(InventoryButton.DEFAULT_ZONE,
				new InventoryButton(null, "x", "cmd", "minecraft:stone", "", "", null, 0, 0, false, true).zone());
		assertEquals(InventoryButton.DEFAULT_ZONE,
				new InventoryButton(null, "x", "cmd", "minecraft:stone", "", "", "  ", 0, 0, false, true).zone());
		assertEquals("TOP", button().withZone("TOP", 0).zone(), "a real zone name is kept as authored");
	}

	@Test
	void commandsLoseTheirLeadingSlash() {
		InventoryButton slashed = button().withCommand("/home");

		assertEquals("home", slashed.command());
	}

	@Test
	void controlCharactersAreStripped() {
		InventoryButton noisy = button().withCommand("hom\ne");

		assertEquals("home", noisy.command());
	}

	@Test
	void theOrderIsClampedToTheSanityRange() {
		InventoryButton far = new InventoryButton(null, "x", "cmd", "minecraft:stone", "", "",
				"RIGHT", 999_999, 0, false, true);
		InventoryButton negative = new InventoryButton(null, "x", "cmd", "minecraft:stone", "", "",
				"RIGHT", -999_999, 0, false, true);

		assertEquals(64, far.order());
		assertEquals(0, negative.order());
	}

	@Test
	void styleWrapsIntoTheSheetRange() {
		InventoryButton styled = button().withStyle(7);

		assertEquals(2, styled.style());
		assertEquals(0, button().withStyle(5).style());
		assertEquals(4, button().withStyle(-1).style());
	}

	@Test
	void usabilityNeedsACommandAndAnIconOrText() {
		assertTrue(button().isUsable());

		assertFalse(button().withCommand("").isUsable(), "no command");
		assertFalse(button().withCommand("").withText("ab").isUsable(), "text does not rescue the command");
		assertTrue(button().withText("ab").isUsable(), "text rescues the icon");
		assertFalse(button().withIcon("").isUsable(), "no icon and no text");
	}

	@Test
	void withNewIdKeepsEverythingButIdentity() {
		InventoryButton original = button();

		InventoryButton fresh = original.withNewId();

		assertNotEquals(original.id(), fresh.id());
		assertEquals(original.name(), fresh.name());
		assertEquals(original.command(), fresh.command());
		assertEquals(original.zone(), fresh.zone());
		assertEquals(original.order(), fresh.order());
		assertEquals(original.style(), fresh.style());
	}

	@Test
	void withZoneMovesTheSlot() {
		InventoryButton moved = button().withZone("TOP", 3);

		assertEquals("TOP", moved.zone());
		assertEquals(3, moved.order());
		assertEquals("RIGHT", button().zone(), "the original keeps its zone");
	}

	@Test
	void newButtonsStartInTheirZoneSlot() {
		InventoryButton fresh = InventoryButton.newButton(1, "LEFT", 2);

		assertEquals("LEFT", fresh.zone());
		assertEquals(2, fresh.order());
		assertEquals("", fresh.command(), "a fresh button cannot run anything");
		assertFalse(fresh.isUsable());
		assertTrue(fresh.enabled());
	}

	@Test
	void aNewButtonWithoutAZoneLandsInTheDefaultZone() {
		assertEquals(InventoryButton.DEFAULT_ZONE, InventoryButton.newButton(1, null, 0).zone());
	}

	@Test
	void commandValidationRejectsBlankLongAndControlled() {
		assertFalse(InventoryButton.validCommand(null));
		assertFalse(InventoryButton.validCommand(""));
		assertFalse(InventoryButton.validCommand("   "));
		assertFalse(InventoryButton.validCommand("a".repeat(257)));
		assertFalse(InventoryButton.validCommand("bad\ncommand"));
		assertTrue(InventoryButton.validCommand("home"));
		assertTrue(InventoryButton.validCommand("/home".substring(1)));
	}
}
