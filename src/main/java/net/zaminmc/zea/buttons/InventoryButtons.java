package net.zaminmc.zea.buttons;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Coordinates the inventory-button subsystem: it owns the loaded button
 * set, answers hit tests for the container-screen integration, and
 * forwards clicks to the activator — the only path that can send a
 * command. Edits happen in the editor screen ({@code ButtonEditorScreen}),
 * which mutates through the methods below; every one of them persists
 * through the safe-write store.
 *
 * <p>Placement is free, the NEU model: a move offers a stored position
 * (offset plus anchors), the move is refused when it would overlap
 * another button — the one discipline free placement keeps, because
 * overlapping buttons are the historical NEU bug this coordinator must
 * never reintroduce — and everything else lands exactly where the
 * player dropped it. The replaced set of a preset load is kept for a
 * one-step restore, so applying a preset is never a silent loss of the
 * player's own buttons.
 *
 * <p>All state lives on the client thread — rendering and input both
 * arrive there. The store file is the only persistence.
 */
public final class InventoryButtons {
	private final InventoryButtonStore store;
	private final ButtonActivator activator;
	private List<InventoryButton> buttons = List.of();
	private List<InventoryButton> restorePoint;
	private String appliedPresetId;

	public InventoryButtons(Path storeFile, ButtonActivator activator) {
		this.store = new InventoryButtonStore(storeFile);
		this.activator = activator;
		this.buttons = store.load();
	}

	/** The loaded buttons, in draw order. */
	public List<InventoryButton> buttons() {
		return buttons;
	}

	/** Runs the clicked button; see {@link ButtonActivator#activate}. */
	public ButtonActivator.Result click(InventoryButton button, long nowMillis) {
		return activator.activate(button, nowMillis);
	}

	/** Forgets click cooldowns (on disconnect). */
	public void reset() {
		activator.reset();
	}

	/** Inserts or replaces a button by identity, then persists. */
	public void upsert(InventoryButton updated) {
		if (updated == null) {
			throw new NullPointerException("updated must not be null");
		}
		List<InventoryButton> next = new ArrayList<>(buttons);
		boolean replaced = false;
		for (int i = 0; i < next.size(); i++) {
			if (next.get(i).id().equals(updated.id())) {
				next.set(i, updated);
				replaced = true;
				break;
			}
		}
		if (!replaced) {
			next.add(updated);
		}
		commit(next);
	}

	/** Removes a button by identity, then persists. */
	public void delete(UUID id) {
		if (id == null) {
			throw new NullPointerException("id must not be null");
		}
		List<InventoryButton> next = new ArrayList<>(buttons);
		next.removeIf(button -> button.id().equals(id));
		commit(next);
	}

	/**
	 * Moves a button to a free position (offset plus anchors), refused
	 * when it would overlap another button. Returns whether the move
	 * happened; a refused move leaves the button where it was.
	 */
	public boolean move(UUID id, int x, int y, boolean anchorRight, boolean anchorBottom) {
		if (id == null) {
			return false;
		}
		InventoryButton moved = null;
		for (InventoryButton button : buttons) {
			if (button.id().equals(id)) {
				moved = button;
				break;
			}
		}
		if (moved == null) {
			return false;
		}
		if (ButtonGeometry.overlapsAny(buttons, moved, x, y, anchorRight, anchorBottom)) {
			return false;
		}
		upsert(moved.withPosition(x, y, anchorRight, anchorBottom));
		return true;
	}

	/** Adds a button created by the editor (right-click in the canvas), then persists. */
	public void add(InventoryButton created) {
		if (created == null) {
			return;
		}
		List<InventoryButton> next = new ArrayList<>(buttons);
		next.add(created);
		commit(next);
	}

	/**
	 * Applies a preset: the current set is remembered (one-step
	 * restore) and the preset's template becomes the live set. The
	 * restore point is dropped by the next non-preset mutation.
	 */
	public void applyPreset(ButtonPresets.Preset preset) {
		if (preset == null) {
			return;
		}
		restorePoint = buttons;
		appliedPresetId = preset.id();
		buttons = List.copyOf(preset.buttons());
		store.save(buttons);
	}

	/**
	 * Restores the set a preset replaced. Returns whether a restore
	 * point existed.
	 */
	public boolean restorePrevious() {
		if (restorePoint == null) {
			return false;
		}
		buttons = restorePoint;
		restorePoint = null;
		appliedPresetId = null;
		store.save(buttons);
		return true;
	}

	/** Whether a preset was applied and can still be undone. */
	public boolean hasRestorePoint() {
		return restorePoint != null;
	}

	/** The id of the last applied preset, for the editor's undo hint. */
	public String appliedPresetId() {
		return appliedPresetId;
	}

	/** Drops the restore point (the editor's "keep the preset" choice). */
	public void keepPreset() {
		restorePoint = null;
		appliedPresetId = null;
	}

	/**
	 * Replaces the whole set with an imported one, then persists.
	 * The import path validates strictly beforehand; nothing here
	 * executes a command — buttons stay inert data until clicked.
	 */
	public void applyImport(List<InventoryButton> imported) {
		buttons = List.copyOf(imported == null ? List.of() : imported);
		restorePoint = null;
		appliedPresetId = null;
		store.save(buttons);
	}

	/** Applies the new set and saves. */
	private void commit(List<InventoryButton> next) {
		restorePoint = null;
		appliedPresetId = null;
		buttons = List.copyOf(next);
		store.save(buttons);
	}
}
