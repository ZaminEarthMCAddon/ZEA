package net.zaminmc.zea.chat.render;

import net.minecraft.ChatFormatting;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pins the tab label color's priority order. The strip itself needs a
 * real Font to construct (it measures its labels), so the color rule is
 * extracted as a pure static — the same headless-model approach the
 * house style uses everywhere else.
 */
class ChatTabBarTest {
        @Test
        void selectedIsYellowAndWinsOverEverything() {
                assertEquals(ChatFormatting.YELLOW, ChatTabBar.labelColor(true, false, false));
                assertEquals(ChatFormatting.YELLOW, ChatTabBar.labelColor(true, true, false));
                assertEquals(ChatFormatting.YELLOW, ChatTabBar.labelColor(true, false, true));
                assertEquals(ChatFormatting.YELLOW, ChatTabBar.labelColor(true, true, true));
        }

        @Test
        void unreadIsWhite() {
                assertEquals(ChatFormatting.WHITE, ChatTabBar.labelColor(false, false, true));
                assertEquals(ChatFormatting.WHITE, ChatTabBar.labelColor(false, true, true));
        }

        /**
         * The live player's palette (dev.36): yellow selected, white unread,
         * gray unselected and read — the target marker strip carries the
         * targeting state, so a targeted-but-read tab reads gray like any
         * other idle tab.
         */
        @Test
        void unselectedAndReadIsGrayTargetedOrNot() {
                assertEquals(ChatFormatting.GRAY, ChatTabBar.labelColor(false, true, false));
                assertEquals(ChatFormatting.GRAY, ChatTabBar.labelColor(false, false, false));
        }
}
