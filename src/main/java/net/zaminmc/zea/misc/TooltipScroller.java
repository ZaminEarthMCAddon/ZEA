package net.zaminmc.zea.misc;

import net.minecraft.client.Minecraft;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector2i;
import org.joml.Vector2ic;
import net.zaminmc.zea.config.ZeaConfig;
import net.zaminmc.zea.duck.HoveredSlotAccess;
import net.zaminmc.zea.ZeaClient;

/**
 * Native scrollable tooltips — ZEA's implementation of the behavior the
 * ScrollableTooltips port provides as a separate mod: when an item tooltip
 * is taller (or wider) than the screen, the mouse wheel scrolls it instead
 * of doing whatever else the screen would do, and the tooltip stays pinned
 * inside the screen bounds.
 *
 * <p>Mechanics, adapted from the port's approach to 26.3's rendering
 * pipeline: vanilla resolves the tooltip position through a
 * {@code ClientTooltipPositioner} and then draws background and lines at
 * that resolved position. A mixin redirect on that single resolution call
 * hands us the position, the tooltip's size and the screen size in one
 * place — this class clamps a scroll offset to the screen margins and
 * returns the shifted position, so background and content move together
 * and the tooltip can never leave the screen. A second mixin catches the
 * wheel event before the screen sees it and consumes it while a tooltip is
 * scrollable.
 *
 * <p>State is tiny and per-frame: the scroll offsets plus a reset flag.
 * The reset fires when the hovered stack changes or a screen closes, so a
 * new tooltip always starts at its top (the port's "start at top"
 * behavior, which its config defaults to on). Only overflowing tooltips
 * scroll — tooltips that fit are never moved.
 */
public final class TooltipScroller {
        /** Screen-edge margin the tooltip keeps while scrolled, in pixels. */
        static final int SCREEN_MARGIN = 6;

        /** Pixels per wheel notch, matching the port's step. */
        private static final double SCROLL_STEP = 10.0;

        /** The hovered stack the scroll state belongs to; EMPTY when none. */
        private static ItemStack previousStack = ItemStack.EMPTY;

        /** Whether the next position resolution must reset the offsets. */
        private static boolean resetPending = true;

        /** Whether the currently drawn tooltip overflows the screen. */
        private static boolean overflow = false;

        /** Current vertical offset, clamped to the screen margins. */
        private static double scrollY;

        /** Current horizontal offset (Shift-scroll), clamped likewise. */
        private static double scrollX;

        private TooltipScroller() {
        }

        /**
         * Tracks the hovered slot so the offsets reset when the item under
         * the cursor changes — the container-screen tooltip extraction
         * runs once per frame before the tooltip is queued. A null or
         * empty slot clears the tracked stack.
         */
        public static void trackSlot(Slot slot) {
                ItemStack stack = slot == null ? ItemStack.EMPTY : slot.getItem();
                boolean changed = !ItemStack.isSameItemSameComponents(stack, previousStack);
                if (changed) {
                        previousStack = stack;
                        resetPending = true;
                }
        }

        /** Forgets everything — a screen closed or the hover ended. */
        public static void reset() {
                previousStack = ItemStack.EMPTY;
                resetPending = true;
                overflow = false;
                scrollX = 0;
                scrollY = 0;
        }

        /**
         * Adjusts vanilla's resolved tooltip position by the clamped
         * scroll offsets. Vanilla's positioner already clamps the
         * unscrolled tooltip to the screen; this shifts within those
         * bounds and keeps a margin. {@code guiWidth}/{@code guiHeight}
         * are the scaled GUI dimensions the tooltip draws in.
         *
         * @return the position to draw at — {@code vanilla} itself when
         *         the feature is off or the tooltip fits
         */
        public static Vector2ic adjustPosition(Vector2ic vanilla, int guiWidth, int guiHeight,
                        int width, int height) {
                if (!engaged()) {
                        overflow = false;
                        return vanilla;
                }
                overflow = isOverflowing(guiWidth, guiHeight, width, height);
                if (!overflow) {
                        return vanilla;
                }
                if (resetPending) {
                        scrollX = 0;
                        // Start at the top: push the tooltip down until its
                        // top edge sits at the screen margin.
                        scrollY = SCREEN_MARGIN - vanilla.y();
                        resetPending = false;
                }
                Adjustment adjustment = computeAdjustment(
                                                vanilla.x(), vanilla.y(), width, height, guiWidth, guiHeight,
                                                scrollX, scrollY);
                scrollX = adjustment.scrollX();
                scrollY = adjustment.scrollY();
                return new Vector2i(vanilla.x() + (int) scrollX, vanilla.y() + (int) scrollY);
        }

        /**
         * Consumes a wheel event while a tooltip is scrollable. Shift
         * scrolls horizontally, like the port. Returns whether the event
         * was consumed (the caller then cancels vanilla's dispatch).
         */
        public static boolean scroll(double deltaY, boolean shiftDown) {
                if (!engaged() || !overflow) {
                        return false;
                }
                double delta = Math.signum(deltaY);
                if (shiftDown) {
                        scrollX += SCROLL_STEP * delta;
                } else {
                        scrollY += SCROLL_STEP * delta;
                }
                return true;
        }

        /**
         * Whether wheel events should even be offered to the scroller:
         * the feature on, a container screen under the cursor and a
         * non-empty hovered slot — the same gating the port applies.
         */
        private static boolean engaged() {
                ZeaConfig cfg = ZeaClient.config();
                if (cfg == null || !cfg.scrollableTooltipsEnabled()) {
                        return false;
                }
                Minecraft minecraft = Minecraft.getInstance();
                if (!(minecraft.gui.screen() instanceof HoveredSlotAccess access)) {
                        return false;
                }
                Slot hovered = access.zea$hoveredSlot();
                return hovered != null && hovered.hasItem();
        }

        /** Whether the tooltip cannot fit the screen with margins on both sides. */
        private static boolean isOverflowing(int guiWidth, int guiHeight, int width, int height) {
                return height + 2 * SCREEN_MARGIN > guiHeight
                                || width + 2 * SCREEN_MARGIN > guiWidth;
        }

        /**
         * The pure bounds math behind the scroll: clamps a desired offset
         * so the tooltip stays within the screen margins. When the
         * tooltip is smaller than the screen, the clamp window is
         * inverted and the offset collapses to the in-range value; when
         * it is larger, the offset is free between the extremes.
         * Extracted from the drawing path so it stays unit-testable.
         */
        public static Adjustment computeAdjustment(int tooltipX, int tooltipY, int width, int height,
                        int guiWidth, int guiHeight, double desiredX, double desiredY) {
                double clampedX = clampOffset(desiredX,
                                                SCREEN_MARGIN - tooltipX,
                                                guiWidth - SCREEN_MARGIN - tooltipX - width);
                double clampedY = clampOffset(desiredY,
                                                SCREEN_MARGIN - tooltipY,
                                                guiHeight - SCREEN_MARGIN - tooltipY - height);
                return new Adjustment(clampedX, clampedY);
        }

        /**
         * One axis of the clamped scroll: {@code min} is the offset that
         * puts the content's leading edge at the margin, {@code max} the
         * offset that puts its trailing edge at the other margin. When
         * {@code min > max} (content smaller than the screen) the valid
         * range is empty and the offset collapses to the boundary that
         * keeps the content visible, mirroring the port's handling.
         */
        private static double clampOffset(double desired, double min, double max) {
                if (min <= max) {
                        return Math.clamp(desired, min, max);
                }
                // Range inverted: the content fits without scrolling. Any
                // offset between max and min would leave one edge floating;
                // pull toward the nearest boundary.
                if (desired < max) {
                        return max;
                }
                if (desired > min) {
                        return min;
                }
                return desired;
        }

        /** The clamped offsets the tooltip draws at. */
        public record Adjustment(double scrollX, double scrollY) {
        }
}
