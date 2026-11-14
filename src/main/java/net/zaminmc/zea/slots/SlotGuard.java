package net.zaminmc.zea.slots;

import net.minecraft.world.inventory.ContainerInput;

/**
 * The pure decisions behind intercepting a slot interaction. Every
 * click path in the container screen funnels through
 * {@code AbstractContainerScreen.slotClicked(Slot, int, int,
 * ContainerInput)} — mouse picks, shift-clicks, number-key swaps, the
 * drop key over a slot, quick-craft drags and double-click collection
 * — so one decision point here covers them all.
 *
 * <p>The rules, each traceable to a NEU lesson:
 * <ul>
 * <li>Any input on a locked slot is blocked (NEU's {@code onWindowClick}
 * cancellation) — drop, move, swap, shift-click, drag, clone, collect.
 * <li>A number-key swap into a locked hotbar slot is blocked, so nothing
 * can land in or leave a locked hotbar slot from the number keys (NEU's
 * {@code checkHotbarKeys} cancellation).
 * <li>A right-click on a bound slot becomes the binding's swap action
 * instead of a pickup — the modern form of NEU's synthetic window click.
 * <li>Keybinds that do not touch slots are never intercepted: NEU once
 * broke every keybind while hovering a locked slot by nulling the
 * hovered slot; this design never touches the hover state, so the
 * inventory key, Esc and everything else keep working.
 * </ul>
 *
 * <p>Known limitation, shared with NEU: double-click collection
 * (PICKUP_ALL) on an unlocked slot can still pull matching stacks out of
 * locked slots, because the server decides which slots it drains.
 */
public final class SlotGuard {
        private SlotGuard() {
        }

        /** What the container mixin should do with a click. */
        public enum Action {
                /** Let vanilla handle it untouched. */
                PASS,
                /** Block the click entirely. */
                BLOCK,
                /** Run the binding swap instead of the vanilla click. */
                SWAP
        }

        /**
         * Decides the action for one click.
         *
         * @param clickedContainerSlot the clicked slot's player-inventory
         *                             index, or -1 when the slot is not in
         *                             the player inventory
         * @param input                the vanilla input kind
         * @param button               the click's button (hotbar index for
         *                             SWAP, 0/1 for PICKUP)
         * @param locks                the lock state
         * @return PASS, BLOCK or SWAP
         */
        public static Action onSlotClick(int clickedContainerSlot, ContainerInput input, int button,
                        SlotLocks locks) {
                // A number-key swap: the hovered slot exchanges with hotbar
                // slot `button`. Both ends must be free.
                if (input == ContainerInput.SWAP && button >= 0 && button <= 8
                                && locks.isLocked(button)) {
                        return Action.BLOCK;
                }
                if (clickedContainerSlot < 0) {
                        return Action.PASS;
                }
                if (locks.isLocked(clickedContainerSlot)) {
                        return Action.BLOCK;
                }
                // The binding's action: a right-click on a bound slot swaps it
                // with its hotbar partner.
                if (input == ContainerInput.PICKUP && button == 1 && locks.bindTarget(clickedContainerSlot) != -1) {
                        return Action.SWAP;
                }
                return Action.PASS;
        }

        /**
         * Whether a slot may join an in-progress quick-craft drag: locked
         * slots never do, so a drag begun elsewhere cannot take from them.
         */
        public static boolean mayJoinQuickCraft(int containerSlot, SlotLocks locks) {
                return containerSlot < 0 || !locks.isLocked(containerSlot);
        }

        /**
         * Whether the drop key's packet ({@code MultiPlayerGameMode.dropItem},
         * the path without a hovered slot) must be blocked: the currently
         * selected hotbar slot is locked.
         *
         * <p>NEU needed a timestamp race guard here because 1.8.9 could
         * drop the slot the server still had selected after a fast
         * scroll-away; modern Minecraft sends the carried-item sync before
         * the drop packet inside {@code dropItem} itself, so blocking on
         * the locked selected slot is sufficient.
         */
        public static boolean blocksDropKey(int selectedHotbarSlot, SlotLocks locks) {
                return selectedHotbarSlot >= 0 && selectedHotbarSlot <= 8 && locks.isLocked(selectedHotbarSlot);
        }
}
