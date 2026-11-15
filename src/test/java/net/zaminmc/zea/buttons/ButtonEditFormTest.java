package net.zaminmc.zea.buttons;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ButtonEditFormTest {
	private ButtonEditForm form() {
		return new ButtonEditForm(new InventoryButton(null, "Home", "home", "minecraft:compass", "", "",
				2, 4, true, false, 1, false, true));
	}

	@Test
	void savesApplyEveryField() {
		ButtonEditForm.Result result = form().save("Warp", "warp home", "minecraft:ender_pearl", "", "Warps home");

		assertTrue(result instanceof ButtonEditForm.Result.Saved);
		InventoryButton saved = ((ButtonEditForm.Result.Saved) result).button();
		assertEquals("Warp", saved.name());
		assertEquals("warp home", saved.command());
		assertEquals("minecraft:ender_pearl", saved.icon());
		assertEquals("Warps home", saved.tooltip());
		// Untouched placement survives the save.
		assertEquals(2, saved.x());
		assertEquals(4, saved.y());
		assertTrue(saved.anchorRight());
		assertEquals(1, saved.style());
	}

	@Test
	void aBlankCommandRefusesFirst() {
		ButtonEditForm.Result result = form().save("Warp", "   ", "minecraft:compass", "", "");

		assertTrue(result instanceof ButtonEditForm.Result.Refused);
		assertEquals(ButtonEditForm.Rejection.BAD_COMMAND, ((ButtonEditForm.Result.Refused) result).reason());
	}

	@Test
	void aBadIconRefusesOnlyWithoutText() {
		ButtonEditForm form = form();

		assertTrue(form.save("Warp", "warp", "not an identifier", "", "")
				instanceof ButtonEditForm.Result.Refused);
		assertTrue(form.save("Warp", "warp", "not an identifier", "GO", "")
				instanceof ButtonEditForm.Result.Saved);
	}

	@Test
	void aCustomTextureIconIsValid() {
		ButtonEditForm.Result result = form().save("Settings", "settings", "custom:settings", "", "");

		assertTrue(result instanceof ButtonEditForm.Result.Saved);
	}

	@Test
	void refusedSavesLeaveTheWorkingCopyUntouched() {
		ButtonEditForm form = form();
		InventoryButton before = form.button();

		form.save("Warp", "", "minecraft:compass", "", "");

		assertEquals(before, form.button());
	}

	@Test
	void styleMutatesTheWorkingCopy() {
		ButtonEditForm form = form();

		form.style(3);

		assertEquals(3, form.button().style());
	}

	@Test
	void togglesFlipAndReport() {
		ButtonEditForm form = form();

		assertFalse(form.button().closeOnExecute());
		assertTrue(form.toggleCloseOnExecute());
		assertTrue(form.button().closeOnExecute());

		assertTrue(form.button().enabled());
		assertFalse(form.toggleEnabled());
		assertFalse(form.button().enabled());
	}

	@Test
	void deleteNeedsTwoClicks() {
		ButtonEditForm form = form();

		assertFalse(form.requestDelete());
		assertTrue(form.deleteArmed());
		assertTrue(form.requestDelete());

		form.disarmDelete();
		assertFalse(form.deleteArmed());
		assertFalse(form.requestDelete());
	}
}
