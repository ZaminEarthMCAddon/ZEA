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
				2, 4, true, false, 3, false, true);
	}

	@Test
	void defaultsRepairMissingFields() {
		InventoryButton bare = new InventoryButton(null, null, null, null, null, null,
				0, 0, false, false, 0, false, true);

		assertNotEquals(null, bare.id());
		assertEquals(InventoryButton.DEFAULT_NAME, bare.name());
		assertEquals("", bare.command());
		assertEquals("", bare.icon());
		assertTrue(bare.enabled());
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
	void offsetsAreClampedToTheSanityRange() {
		InventoryButton far = new InventoryButton(null, "x", "cmd", "minecraft:stone", "", "",
				999_999, -999_999, false, false, 0, false, true);

		assertEquals(ButtonGeometry.MAX_OFFSET, far.x());
		assertEquals(-ButtonGeometry.MAX_OFFSET, far.y());
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
		assertEquals(original.x(), fresh.x());
		assertEquals(original.y(), fresh.y());
		assertEquals(original.anchorRight(), fresh.anchorRight());
		assertEquals(original.anchorBottom(), fresh.anchorBottom());
		assertEquals(original.style(), fresh.style());
	}

	@Test
	void withPositionMovesAndAnchors() {
		InventoryButton moved = button().withPosition(-20, -18, false, true);

		assertEquals(-20, moved.x());
		assertEquals(-18, moved.y());
		assertFalse(moved.anchorRight());
		assertTrue(moved.anchorBottom());
	}

	@Test
	void newButtonsStartBesideTheGui() {
		InventoryButton fresh = InventoryButton.newButton(1);

		assertEquals(InventoryButton.NEW_BUTTON_X, fresh.x());
		assertTrue(fresh.anchorRight());
		assertEquals("", fresh.command(), "a fresh button cannot run anything");
		assertFalse(fresh.isUsable());
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
