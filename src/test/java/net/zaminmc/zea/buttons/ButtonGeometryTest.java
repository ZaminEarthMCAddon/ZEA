package net.zaminmc.zea.buttons;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ButtonGeometryTest {
	private InventoryButton at(int x, int y) {
		return new InventoryButton(UUID.randomUUID(), "b", "cmd", "minecraft:stone", "", "",
				x, y, false, false, 0, false, true);
	}

	private InventoryButton anchored(int x, int y, boolean right, boolean bottom) {
		return new InventoryButton(UUID.randomUUID(), "b", "cmd", "minecraft:stone", "", "",
				x, y, right, bottom, 0, false, true);
	}

	private ButtonGeometry.Inputs canvas(boolean playerInventory) {
		return new ButtonGeometry.Inputs(100, 50, 176, 166, 400, 300, playerInventory, false);
	}

	@Test
	void positionsResolveAgainstTheCornerTheAnchorsPick() {
		ButtonGeometry.Inputs inputs = canvas(false);
		// The plain case sits in the inventory band, where foreign
		// containers do render unanchored buttons.
		ButtonGeometry.Placement plain = ButtonGeometry.placements(
				List.of(at(2, 100)), inputs).get(0);
		assertEquals(102, plain.x());
		assertEquals(150, plain.y());
		assertTrue(plain.rendered());

		ButtonGeometry.Placement right = ButtonGeometry.placements(
				List.of(anchored(2, 4, true, false)), inputs).get(0);
		assertEquals(100 + 176 + 2, right.x());
		assertEquals(54, right.y());

		ButtonGeometry.Placement bottom = ButtonGeometry.placements(
				List.of(anchored(2, 4, false, true)), inputs).get(0);
		assertEquals(102, bottom.x());
		assertEquals(50 + 166 + 4, bottom.y());
	}

	@Test
	void thePlayerInventoryRendersEverythingAsAuthored() {
		ButtonGeometry.Placement inBand = ButtonGeometry.placements(
				List.of(at(80, 10)), canvas(true)).get(0);

		assertTrue(inBand.rendered(), "the container band rule does not apply to the player's own inventory");
	}

	@Test
	void foreignContainersHideUnanchoredButtonsInTheSlotBand() {
		ButtonGeometry.Placement inBand = ButtonGeometry.placements(
				List.of(at(80, 10)), canvas(false)).get(0);

		assertFalse(inBand.rendered(), "an unanchored button over live slots must not render");

		// Anchored buttons measure from the edges and sit outside the band.
		ButtonGeometry.Placement anchoredOut = ButtonGeometry.placements(
				List.of(anchored(-20, 10, true, false)), canvas(false)).get(0);
		assertTrue(anchoredOut.rendered());
	}

	@Test
	void theInventoryBandRidesDownOnTallerContainers() {
		ButtonGeometry.Inputs tall = new ButtonGeometry.Inputs(100, 20, 176, 222, 400, 300, false, false);
		ButtonGeometry.Placement inInventoryBand = ButtonGeometry.placements(
				List.of(at(2, 100)), tall).get(0);

		// 100 (canvas y) + (222 - 166) taller = rides down 56.
		assertEquals(20 + 100 + 56, inInventoryBand.y());
	}

	@Test
	void theRecipeBookClaimsTheLeftStripWhileOpen() {
		ButtonGeometry.Inputs withBook = new ButtonGeometry.Inputs(100, 50, 176, 166, 400, 300, false, true);
		ButtonGeometry.Placement left = ButtonGeometry.placements(
				List.of(at(-20, 10)), withBook).get(0);

		assertFalse(left.rendered());
	}

	@Test
	void offScreenPositionsDoNotRender() {
		ButtonGeometry.Placement bottom = ButtonGeometry.placements(
				List.of(anchored(2, 200, false, true)), canvas(false)).get(0);
		ButtonGeometry.Placement right = ButtonGeometry.placements(
				List.of(anchored(200, 2, true, false)), canvas(false)).get(0);

		assertFalse(bottom.rendered());
		assertFalse(right.rendered());
	}

	@Test
	void hitTestsFindTheTopmostRenderedButton() {
		List<ButtonGeometry.Placement> placements = ButtonGeometry.placements(
				List.of(at(2, 100), at(2, 100)), canvas(false));

		assertNotNull(ButtonGeometry.at(placements, 103, 151));
		assertEquals(placements.get(1).button().id(),
				ButtonGeometry.at(placements, 103, 151).button().id());
		assertNull(ButtonGeometry.at(placements, 5, 5));
	}

	@Test
	void editableAtFindsUnrenderedButtonsToo() {
		List<ButtonGeometry.Placement> placements = ButtonGeometry.placements(
				List.of(at(80, 10)), canvas(false));

		assertNotNull(ButtonGeometry.editableAt(placements, 181, 61));
	}

	@Test
	void overlapComparesAnchorsInAbsoluteCanvasSpace() {
		InventoryButton existing = anchored(-20, 10, true, false);
		List<InventoryButton> buttons = List.of(existing);

		// Same absolute spot expressed unanchored: 176 - 20 = 156.
		assertTrue(ButtonGeometry.overlapsAny(buttons, at(0, 0), 156, 10, false, false));
		// A full button width apart is fine.
		assertFalse(ButtonGeometry.overlapsAny(buttons, at(0, 0), 156 + 18, 10, false, false));
		// The button itself never blocks its own move.
		assertFalse(ButtonGeometry.overlapsAny(buttons, existing, -20, 10, true, false));
		// Vertical overlap alone is not a collision.
		assertFalse(ButtonGeometry.overlapsAny(buttons, at(0, 0), 0, 12, false, false));
	}

	@Test
	void gridSnapSnapsInsideToFixedSlots() {
		ButtonGeometry.Snapped snapped = ButtonGeometry.snap(90, 60, true);

		// (98, 61) is the nearest fixed slot.
		assertEquals(98, snapped.x());
		assertEquals(61, snapped.y());
	}

	@Test
	void gridSnapSnapsOutsideToTheRingGrid() {
		// The ring grid steps by 20 from just outside the canvas; Java's
		// truncating division means x = -25 lands on the -40 slot (the
		// reference port's exact arithmetic).
		ButtonGeometry.Snapped left = ButtonGeometry.snap(-25, 10, true);
		ButtonGeometry.Snapped right = ButtonGeometry.snap(200, 10, true);
		ButtonGeometry.Snapped top = ButtonGeometry.snap(50, -25, true);
		ButtonGeometry.Snapped bottom = ButtonGeometry.snap(50, 200, true);

		assertEquals(-40, left.x());
		assertEquals(2, left.y());
		assertEquals(198, right.x());
		assertEquals(2, right.y());
		assertEquals(48, top.x());
		assertEquals(-40, top.y());
		assertEquals(48, bottom.x());
		assertEquals(188, bottom.y());
	}

	@Test
	void freeSnapMagnetsTheBottomAndLeftZonesToFixedSlots() {
		// The armor/offhand/crafting area collects buttons into its slots.
		ButtonGeometry.Snapped armor = ButtonGeometry.snap(20, 10, false);
		ButtonGeometry.Snapped free = ButtonGeometry.snap(120, 10, false);

		assertEquals(25, armor.x());
		assertEquals(8, armor.y());
		assertEquals(120, free.x(), "the top band does not magnet");
		assertEquals(10, free.y());
	}

	@Test
	void atCanvasPositionDocksWithTheEdgesItPasses() {
		InventoryButton plain = at(10, 10);
		InventoryButton docked = ButtonGeometry.atCanvasPosition(plain, 200, 170);

		assertTrue(docked.anchorRight());
		assertTrue(docked.anchorBottom());
		assertEquals(200 - ButtonGeometry.CANVAS_WIDTH, docked.x());
		assertEquals(170 - ButtonGeometry.CANVAS_HEIGHT, docked.y());

		InventoryButton inside = ButtonGeometry.atCanvasPosition(plain, 10, 10);
		assertFalse(inside.anchorRight());
		assertFalse(inside.anchorBottom());
	}
}
