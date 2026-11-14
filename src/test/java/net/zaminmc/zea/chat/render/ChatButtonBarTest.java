package net.zaminmc.zea.chat.render;

import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import com.mojang.blaze3d.platform.InputConstants;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The chat action row's pure behavior: which buttons a configuration
 * yields, where each button's hit region sits, and which callback a
 * click reaches. Rendering needs a live frame graph, so the visual half
 * is covered by the live checklist instead; everything here runs
 * headless — the widget measures no text, so unlike the tab strip it
 * needs no Font to exist.
 */
class ChatButtonBarTest {
	private static final int X = 300;
	private static final int Y = 188;

	@Test
	void theRowOccupiesNinePerPixelButtonPlusGaps() {
		assertEquals(0, ChatButtonBar.rowWidth(false, false),
				"a row with no buttons must occupy nothing");
		assertEquals(9, ChatButtonBar.rowWidth(true, false));
		assertEquals(9, ChatButtonBar.rowWidth(false, true));
		assertEquals(2 * 9 + ChatButtonBar.BUTTON_GAP, ChatButtonBar.rowWidth(true, true));
	}

	@Test
	void theWidgetSizesItselfFromTheButtons() {
		ChatButtonBar both = bar(true, true, () -> false);
		assertEquals(ChatButtonBar.rowWidth(true, true), both.getWidth());
		assertEquals(ChatTabBar.TAB_HEIGHT, both.getHeight());
	}

	@Test
	void leftClicksRouteToTheButtonUnderThem() {
		AtomicInteger searches = new AtomicInteger();
		AtomicInteger clears = new AtomicInteger();
		ChatButtonBar bar = new ChatButtonBar(X, Y, true, true, () -> false,
				searches::incrementAndGet, clears::incrementAndGet);

		bar.onClick(left(X), false);
		bar.onClick(left(X + ChatButtonBar.BUTTON_SIZE - 1), false);

		bar.onClick(left(X + ChatButtonBar.BUTTON_SIZE + ChatButtonBar.BUTTON_GAP), false);
		bar.onClick(left(X + ChatButtonBar.rowWidth(true, true) - 1), false);

		assertEquals(2, searches.get(), "the search button's whole width toggles search");
		assertEquals(2, clears.get(), "the clear button's whole width clears history");
	}

	@Test
	void theGapBetweenButtonsActivatesNothing() {
		AtomicInteger actions = new AtomicInteger();
		ChatButtonBar bar = new ChatButtonBar(X, Y, true, true, () -> false,
				actions::incrementAndGet, actions::incrementAndGet);

		int gap = X + ChatButtonBar.BUTTON_SIZE;
		bar.onClick(left(gap), false);
		bar.onClick(left(gap + ChatButtonBar.BUTTON_GAP - 1), false);

		assertEquals(0, actions.get(), "the gap is dead space, not a button");
	}

	@Test
	void clicksOutsideTheRowActivateNothing() {
		AtomicInteger actions = new AtomicInteger();
		ChatButtonBar bar = new ChatButtonBar(X, Y, true, true, () -> false,
				actions::incrementAndGet, actions::incrementAndGet);

		bar.onClick(left(X - 1), false);
		bar.onClick(left(X + ChatButtonBar.rowWidth(true, true)), false);

		assertEquals(0, actions.get());
	}

	@Test
	void onlyTheLeftButtonActivatesActions() {
		AtomicInteger actions = new AtomicInteger();
		ChatButtonBar bar = new ChatButtonBar(X, Y, true, true, () -> false,
				actions::incrementAndGet, actions::incrementAndGet);

		bar.onClick(button(InputConstants.MOUSE_BUTTON_RIGHT, X), false);
		bar.onClick(button(InputConstants.MOUSE_BUTTON_MIDDLE, X), false);

		assertEquals(0, actions.get(),
				"right stays free for message copying; middle is not a shortcut either");
	}

	@Test
	void aSingleButtonRowStillRoutesItsOneButton() {
		AtomicInteger clears = new AtomicInteger();
		ChatButtonBar clearOnly = new ChatButtonBar(X, Y, false, true, () -> false,
				() -> { throw new AssertionError("the search button is not shown"); },
				clears::incrementAndGet);

		clearOnly.onClick(left(X), false);

		assertEquals(1, clears.get());
	}

	@Test
	void hitTestingFollowsTheButtonsNotTheWidgetBox() {
		ChatButtonBar bar = bar(true, true, () -> false);

		assertTrue(bar.isMouseOver(X, Y + 1));
		assertFalse(bar.isMouseOver(X + ChatButtonBar.BUTTON_SIZE, Y + 1),
				"the gap is not hit");
		assertTrue(bar.isMouseOver(X + ChatButtonBar.BUTTON_SIZE + ChatButtonBar.BUTTON_GAP, Y + 5));
		assertFalse(bar.isMouseOver(X - 1, Y + 5));
	}

	@Test
	void nullCallbacksAreRejectedUpFront() {
		assertThrows(NullPointerException.class, () -> new ChatButtonBar(X, Y, true, true, null,
				() -> { }, () -> { }));
		assertThrows(NullPointerException.class, () -> new ChatButtonBar(X, Y, true, true, () -> false,
				null, () -> { }));
		assertThrows(NullPointerException.class, () -> new ChatButtonBar(X, Y, true, true, () -> false,
				() -> { }, null));
		assertDoesNotThrow(() -> new ChatButtonBar(X, Y, true, true, () -> false, () -> { }, () -> { }));
	}

	@Test
	void narrationNamesEveryShownAction() {
		// Headless, a translatable resolves to its key — which is exactly
		// the pin: the narration must carry the shown buttons' translation
		// keys (and only those).
		assertTrue(bar(true, true, () -> false).createNarrationMessage().getString()
				.contains("zea.chat.button.search"));
		assertTrue(bar(true, true, () -> false).createNarrationMessage().getString()
				.contains("zea.chat.button.clear"));
		assertTrue(bar(false, true, () -> false).createNarrationMessage().getString()
				.contains("zea.chat.button.clear"));
		assertFalse(bar(false, true, () -> false).createNarrationMessage().getString()
				.contains("zea.chat.button.search"),
				"a hidden button must not narrate itself");
	}

	private static ChatButtonBar bar(boolean search, boolean clear, java.util.function.BooleanSupplier open) {
		return new ChatButtonBar(X, Y, search, clear, open, () -> { }, () -> { });
	}

	private static MouseButtonEvent left(int x) {
		return button(InputConstants.MOUSE_BUTTON_LEFT, x);
	}

	private static MouseButtonEvent button(int mouseButton, int x) {
		return new MouseButtonEvent(x, Y + 5, new MouseButtonInfo(mouseButton, 0));
	}
}
