package net.zaminmc.zea.buttons;

import net.minecraft.resources.Identifier;

/**
 * The editor panel's decision logic, extracted from
 * {@code ButtonEditorScreen} so it runs in unit tests without a live
 * client.
 *
 * <p>The form owns the working copy of the button: the style and flag
 * controls mutate it directly, while {@link #save} applies the text
 * fields and validates in a fixed order — command first, icon second —
 * refusing with the first failure so the panel shows one inline error,
 * never a wall of them. A button with display text passes without an
 * icon; the icon only matters when there is no text to draw. Deleting
 * needs two clicks: the first arms the confirmation, the second is
 * required before anything is removed. Nothing here executes commands
 * or persists data; the screen decides what a successful save or a
 * confirmed delete is applied to.
 */
public final class ButtonEditForm {

	/** Why a save was refused; {@link #key()} completes the error translation key. */
	public enum Rejection {
		BAD_COMMAND("command"),
		BAD_ICON("icon");

		private final String key;

		Rejection(String key) {
			this.key = key;
		}

		/** Suffix of the translation key, e.g. {@code zea.buttons.edit.error.command}. */
		public String key() {
			return key;
		}
	}

	/** Either the button to save, or the refusal reason to display. */
	public sealed interface Result {
		record Saved(InventoryButton button) implements Result {
		}

		record Refused(Rejection reason) implements Result {
		}
	}

	private InventoryButton button;
	private boolean deleteArmed;

	public ButtonEditForm(InventoryButton button) {
		this.button = button;
	}

	/** The working copy the controls mutate. */
	public InventoryButton button() {
		return button;
	}

	/** Sets the working copy's background style. */
	public void style(int style) {
		button = button.withStyle(style);
	}

	/** Flips the close-on-execute flag and reports the new state. */
	public boolean toggleCloseOnExecute() {
		button = button.withCloseOnExecute(!button.closeOnExecute());
		return button.closeOnExecute();
	}

	/** Flips the enabled flag and reports the new state. */
	public boolean toggleEnabled() {
		button = button.withEnabled(!button.enabled());
		return button.enabled();
	}

	/**
	 * Applies the text field values and validates them. A refused save
	 * leaves the working copy untouched — the fields stay the source of
	 * truth for name, command, icon and text until a save succeeds.
	 */
	public Result save(String name, String command, String icon, String text, String tooltip) {
		InventoryButton candidate = button
				.withName(name)
				.withCommand(command)
				.withIcon(icon)
				.withText(text)
				.withTooltip(tooltip);
		if (!InventoryButton.validCommand(candidate.command())) {
			return new Result.Refused(Rejection.BAD_COMMAND);
		}
		if (candidate.text().isBlank() && Identifier.tryParse(candidate.icon()) == null
				&& !ButtonIcons.isCustomTexture(candidate.icon())) {
			return new Result.Refused(Rejection.BAD_ICON);
		}
		button = candidate;
		return new Result.Saved(candidate);
	}

	/** First click arms the confirmation; the second deletes. */
	public boolean requestDelete() {
		if (deleteArmed) {
			return true;
		}
		deleteArmed = true;
		return false;
	}

	public boolean deleteArmed() {
		return deleteArmed;
	}

	/** Un-arms a armed delete (the panel closed or the click moved on). */
	public void disarmDelete() {
		deleteArmed = false;
	}
}
