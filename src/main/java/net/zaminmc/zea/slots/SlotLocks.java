package net.zaminmc.zea.slots;

import java.util.Map;

/**
 * The slot lock and binding state, in the player inventory's own slot
 * numbering (0-8 hotbar, 9-35 main inventory, 36-39 armor, 40 off-hand —
 * the numbering {@code Slot.getContainerSlot()} reports when the slot's
 * container is the player inventory). That numbering is stable across
 * every screen a player inventory appears in, which is exactly why NEU
 * keyed locks this way: the same slot keeps its lock in the survival
 * inventory, a chest's player-inventory rows, and a crafting table.
 *
 * <p>Contexts: locks belong to a server (by address), the modern
 * counterpart of NEU's per-SkyBlock-profile and per-dimension
 * separation — inventories on different EarthMC servers are unrelated,
 * so their locks must be too. {@code generic} covers menus and
 * singleplayer. The context switches with the connection; state is kept
 * in memory and written through on every change.
 *
 * <p>Bindings pair a non-hotbar player slot with a hotbar slot; the
 * interaction to use one is a right-click on the bound slot, which
 * performs the same swap the number key would (see
 * {@link SlotGuard}). NEU's historical binding bugs are absorbed by
 * construction here: the swap is synthesized against the live menu's
 * slot index, so armor slots and the player-inventory rows of chest
 * GUIs resolve correctly in every screen (NEU needed two separate
 * commits for exactly those two cases).
 */
public final class SlotLocks {
        /** The context key used when no server connection identifies one. */
        public static final String GENERIC_CONTEXT = "generic";

        private final SlotLockStore store;
        private final Map<String, SlotLockStore.ContextState> contexts;
        private String context = GENERIC_CONTEXT;

        public SlotLocks(SlotLockStore store) {
                this.store = store;
                this.contexts = SlotLockStore.mutableCopy(store.load());
        }

        /** Switches the active context (server join / leave). */
        public void context(String serverContext) {
                context = serverContext == null || serverContext.isBlank() ? GENERIC_CONTEXT : serverContext;
                pairingSlot = -1;
        }

        /** The active context key. */
        public String context() {
                return context;
        }

        private SlotLockStore.ContextState state() {
                return contexts.computeIfAbsent(context, key -> SlotLockStore.emptyState());
        }

        private void save() {
                store.save(contexts);
        }

        /**
         * Toggles the lock of a player-inventory slot. Toggling clears any
         * binding on it — a slot is either locked or bound, never both.
         * Returns the new lock state.
         */
        public boolean toggleLock(int containerSlot) {
                SlotLockStore.ContextState state = state();
                boolean next = !state.locked()[containerSlot];
                state.locked()[containerSlot] = next;
                if (next) {
                        state.boundTo()[containerSlot] = -1;
                }
                save();
                return next;
        }

        /**
         * Binds a player-inventory slot to a hotbar slot. Binding clears
         * the slot's lock (NEU's rule: bound and locked are exclusive) and
         * removes any other binding to the same hotbar slot, so a hotbar
         * slot has at most one partner.
         */
        public void bind(int containerSlot, int hotbarIndex) {
                if (!bindable(containerSlot) || hotbarIndex < 0 || hotbarIndex > 8) {
                        return;
                }
                SlotLockStore.ContextState state = state();
                for (int i = 0; i < state.boundTo().length; i++) {
                        if (state.boundTo()[i] == hotbarIndex) {
                                state.boundTo()[i] = -1;
                        }
                }
                state.locked()[containerSlot] = false;
                state.boundTo()[containerSlot] = hotbarIndex;
                save();
        }

        /** Removes a slot's binding. */
        public void clearBind(int containerSlot) {
                SlotLockStore.ContextState state = state();
                if (state.boundTo()[containerSlot] != -1) {
                        state.boundTo()[containerSlot] = -1;
                        save();
                }
        }

        /** Whether a player-inventory slot index is locked in the active context. */
        public boolean isLocked(int containerSlot) {
                if (containerSlot < 0 || containerSlot >= SlotLockStore.SLOT_COUNT) {
                        return false;
                }
                return state().locked()[containerSlot];
        }

        /** The hotbar slot a player-inventory slot is bound to, or -1. */
        public int bindTarget(int containerSlot) {
                if (containerSlot < 0 || containerSlot >= SlotLockStore.SLOT_COUNT) {
                        return -1;
                }
                return state().boundTo()[containerSlot];
        }

        /** Whether binding a slot is allowed: any player-inventory slot except the hotbar itself. */
        public static boolean bindable(int containerSlot) {
                return containerSlot >= 9 && containerSlot < SlotLockStore.SLOT_COUNT;
        }

        // ---- The lock key's pairing flow (NEU's hold-to-bind interaction). ----

        /** The slot the lock key was last pressed over, or -1 when the pairing ended. */
        private int pairingSlot = -1;

        /**
         * Begins the pairing: the lock key was pressed over this slot.
         * While the key stays down, a left-click on a hotbar slot binds
         * the two.
         */
        public void beginPairing(int containerSlot) {
                this.pairingSlot = containerSlot;
        }

        /** Whether a pairing is in progress. */
        public boolean pairingActive() {
                return pairingSlot != -1;
        }

        /** The slot the pairing binds from, or -1. */
        public int pairingSlot() {
                return pairingSlot;
        }

        /** Ends the pairing (the key was released, a binding was made, or the screen closed). */
        public void endPairing() {
                pairingSlot = -1;
        }
}
