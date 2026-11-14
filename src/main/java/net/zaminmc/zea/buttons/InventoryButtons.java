package net.zaminmc.zea.buttons;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Coordinates the inventory-button subsystem: it owns the loaded button
 * set, the edit-mode state, answers hit tests for the container-screen
 * integration, and forwards clicks to the activator — the only path
 * that can send a command. Edits (drag moves, the edit screens, preset
 * loads) flow back through the mutation methods below, which persist
 * through the safe-write store.
 *
 * <p>Placement is region-based: a move picks a region and an insertion
 * index, and this class re-packs the region's orders densely so two
 * buttons can never share a slot silently. The replaced set of a preset
 * load is kept for a one-step restore, so applying a preset is never a
 * silent loss of the player's own buttons.
 *
 * <p>All state lives on the client thread — rendering and input both
 * arrive there. The store file is the only persistence.
 */
public final class InventoryButtons {
        private final InventoryButtonStore store;
        private final ButtonActivator activator;
        private final ButtonDragSession drag = new ButtonDragSession();
        private List<InventoryButton> buttons = List.of();
        private boolean editMode;
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

        /** Whether button editing is active in container screens. */
        public boolean editMode() {
                return editMode;
        }

        /** Toggles button editing; leaving edit mode aborts any drag. */
        public void toggleEditMode() {
                editMode = !editMode;
                if (!editMode) {
                        drag.cancel();
                }
        }

        /** The drag session of the current edit mode. */
        public ButtonDragSession drag() {
                return drag;
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
         * Moves a button to an insertion index within a region. The target
         * region's buttons shift around the insertion and every region's
         * orders are re-packed densely; the moved button keeps its
         * identity.
         */
        public void move(UUID id, ButtonRegion region, int index) {
                if (id == null || region == null) {
                        return;
                }
                InventoryButton moved = null;
                List<InventoryButton> without = new ArrayList<>(buttons.size());
                for (InventoryButton button : buttons) {
                        if (button.id().equals(id)) {
                                moved = button;
                        } else {
                                without.add(button);
                        }
                }
                if (moved == null) {
                        return;
                }
                // The target region's siblings, in their current order; the
                // moved button slots in at the requested index.
                List<InventoryButton> siblings = new ArrayList<>();
                for (InventoryButton button : without) {
                        if (button.region() == region) {
                                siblings.add(button);
                        }
                }
                siblings.add(Math.clamp(index, 0, siblings.size()), moved.withRegion(region));
                java.util.Map<UUID, Integer> orders = new java.util.HashMap<>();
                for (int i = 0; i < siblings.size(); i++) {
                        orders.put(siblings.get(i).id(), i);
                }
                List<InventoryButton> next = new ArrayList<>(without.size() + 1);
                for (InventoryButton button : without) {
                        next.add(button.region() == region ? button.withOrder(orders.get(button.id())) : button);
                }
                next.add(moved.withRegion(region).withOrder(orders.get(id)));
                commit(next);
        }

        /** Moves a button one step earlier within its region (editor button). */
        public void moveEarlier(UUID id) {
                shift(id, -1);
        }

        /** Moves a button one step later within its region (editor button). */
        public void moveLater(UUID id) {
                shift(id, +1);
        }

        private void shift(UUID id, int delta) {
                InventoryButton target = null;
                for (InventoryButton button : buttons) {
                        if (button.id().equals(id)) {
                                target = button;
                                break;
                        }
                }
                if (target == null) {
                        return;
                }
                List<InventoryButton> region = regionButtons(target.region());
                int position = -1;
                for (int i = 0; i < region.size(); i++) {
                        if (region.get(i).id().equals(id)) {
                                position = i;
                                break;
                        }
                }
                int targetPosition = Math.clamp(position + delta, 0, region.size() - 1);
                if (targetPosition == position) {
                        return;
                }
                move(id, target.region(), targetPosition);
        }

        /** The buttons of one region, in order. */
        public List<InventoryButton> regionButtons(ButtonRegion region) {
                List<InventoryButton> out = new ArrayList<>();
                for (InventoryButton button : buttons) {
                        if (button.region() == region) {
                                out.add(button);
                        }
                }
                out.sort(java.util.Comparator.comparingInt(InventoryButton::order));
                return out;
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

        /** Applies the new set and re-packs region orders before saving. */
        private void commit(List<InventoryButton> next) {
                restorePoint = null;
                appliedPresetId = null;
                buttons = List.copyOf(InventoryButtonStore.repack(next));
                store.save(buttons);
        }
}
