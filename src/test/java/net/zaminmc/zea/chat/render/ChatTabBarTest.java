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
	void selectedWinsOverEverything() {
		assertEquals(ChatFormatting.WHITE, ChatTabBar.labelColor(true, false, false));
		assertEquals(ChatFormatting.WHITE, ChatTabBar.labelColor(true, true, false));
		assertEquals(ChatFormatting.WHITE, ChatTabBar.labelColor(true, false, true));
		assertEquals(ChatFormatting.WHITE, ChatTabBar.labelColor(true, true, true));
	}

	@Test
	void unreadIsYellowAndOutranksTargeting() {
		assertEquals(ChatFormatting.YELLOW, ChatTabBar.labelColor(false, false, true));
		assertEquals(ChatFormatting.YELLOW, ChatTabBar.labelColor(false, true, true));
	}

	@Test
	void targetedIsAquaAndIdleIsGray() {
		assertEquals(ChatFormatting.AQUA, ChatTabBar.labelColor(false, true, false));
		assertEquals(ChatFormatting.GRAY, ChatTabBar.labelColor(false, false, false));
	}
}
