package net.zaminmc.zea.slots;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * The lock and binding indicators drawn on slots, plus the tooltip hint
 * lines appended to an item's tooltip. Pure presentation decisions — no
 * state, no Minecraft input types beyond the graphics sink.
 *
 * <p>The lock is a small procedural padlock in the slot's lower-left
 * corner (body plus shackle, drawn with fills — no texture dependency),
 * translucent so the item underneath stays readable. The binding shows
 * its hotbar partner's digit in the slot's top-right corner, the same
 * convention vanilla's number-key hover hint uses, so "press 3" and
 * "bound to 3" read alike.
 */
public final class SlotIndicators {
        private SlotIndicators() {
        }

        /** Body color of the padlock. */
        private static final int LOCK_BODY = 0xB8E8E8F0;

        /** Shackle color of the padlock. */
        private static final int LOCK_SHACKLE = 0x909098A8;

        /** Color of the binding digit. */
        private static final int BIND_DIGIT = 0xFFF0F0F0;

        /** Draws the padlock for a locked slot at (x, y), the slot's top-left corner. */
        public static void drawLock(GuiGraphicsExtractor graphics, int x, int y) {
                // Shackle: an outlined arch, rows 3..6.
                graphics.fill(x + 4, y + 3, x + 5, y + 7, LOCK_SHACKLE);
                graphics.fill(x + 10, y + 3, x + 11, y + 7, LOCK_SHACKLE);
                graphics.fill(x + 4, y + 3, x + 11, y + 4, LOCK_SHACKLE);
                // Body: rows 6..11.
                graphics.fill(x + 3, y + 6, x + 12, y + 12, LOCK_BODY);
                // Keyhole.
                graphics.fill(x + 7, y + 8, x + 8, y + 10, 0x90000000);
        }

        /** Draws the binding's hotbar digit at (x, y), the slot's top-left corner. */
        public static void drawBindDigit(GuiGraphicsExtractor graphics, Font font, int hotbarIndex, int x, int y) {
                String digit = String.valueOf(hotbarIndex + 1);
                graphics.text(font, digit, x + 16 - font.width(digit) - 1, y + 1, BIND_DIGIT, true);
        }

        /**
         * The tooltip hint lines for a slot: what the lock or binding does,
         * appended under the item's own tooltip.
         */
        public static List<Component> tooltipLines(int containerSlot, SlotLocks locks) {
                List<Component> out = new ArrayList<>(2);
                if (locks.isLocked(containerSlot)) {
                        out.add(Component.translatable("zea.slots.locked_hint"));
                }
                int target = locks.bindTarget(containerSlot);
                if (target != -1) {
                        out.add(Component.translatable("zea.slots.bound_hint", target + 1));
                }
                return out;
        }
}
