package net.zaminmc.zea.duck;

import net.minecraft.world.inventory.Slot;

/**
 * Exposes {@code AbstractContainerScreen}'s protected {@code hoveredSlot}
 * field to non-mixin ZEA code — the tooltip scroller checks what is under
 * the cursor when a wheel event arrives. Like every duck interface here,
 * it is implemented by a mixin targeting the declaring class, because
 * Mixin resolves shadowed members only against a class's own declarations.
 */
public interface HoveredSlotAccess {
        /** The slot currently under the cursor, or {@code null} when none is. */
        Slot zea$hoveredSlot();
}
