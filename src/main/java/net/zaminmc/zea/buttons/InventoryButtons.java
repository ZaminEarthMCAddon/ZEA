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
 * <p>Placement is zoned (the dev.36 spec): a move offers a zone and an
 * order within it, and the move is refused when that slot is occupied —
 * the one discipline the zone model keeps, so two buttons can never
 * collide and nothing floats outside the nine placement zones. The
 * replaced set of a preset load is kept for a one-step restore, so
 * applying a preset is never a silent loss of the player's own buttons.
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
	 * Moves a button into a zone slot, refused when the slot is occupied
	 * by another button or the arguments do not name a real slot. Returns
	 * whether the move happened; a refused move leaves the button where
	 * it was.
	 */
	public boolean move(UUID id, String zoneName, int order) {
		if (id == null) {
			return false;
		}
		ButtonGeometry.Zone zone = ButtonGeometry.Zone.byName(zoneName);
		if (zone == null || order < 0 || order >= zone.capacity) {
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
		if (ButtonGeometry.occupied(buttons, moved, zone, order)) {
			return false;
		}
		upsert(moved.withZone(zone.name(), order));
		return true;
	}

	/** Adds a button to the first free slot of its zone (falls back to the right column). */
	public void add(InventoryButton created) {
		if (created == null) {
			return;
		}
		ButtonGeometry.Zone zone = ButtonGeometry.Zone.byName(created.zone());
		int order = zone == null ? 0 : ButtonGeometry.firstFreeOrder(buttons, zone);
		InventoryButton placed = created;
		if (zone != null && order >= 0) {
			placed = created.withZone(zone.name(), order);
		} else if (zone == null) {
			placed = created.withZone(ButtonGeometry.Zone.RIGHT.name(),
					Math.max(0, ButtonGeometry.firstFreeOrder(buttons, ButtonGeometry.Zone.RIGHT)));
		}
		List<InventoryButton> next = new ArrayList<>(buttons);
		next.add(placed);
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
