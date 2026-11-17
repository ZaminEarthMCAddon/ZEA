package net.zaminmc.zea.search;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The shared search-bar layout, pinned to NEU's anchor: the settings
 * plate, the bar and the search-mode plate sit as one bottom-centered
 * group — NEU's {@code BOTMID} anchor at {@code -searchBarYOffset} —
 * with the bar's height following NEU's
 * {@code max(searchBarHeight / scaleFactor, ITEM_SIZE)}. The same
 * numbers the runtime mixin and any preview both use, so the two can
 * never drift apart.
 */
class SearchBarLayoutTest {
	@Test
	void theBarHeightFollowsNeusScaleFormula() {
		// NEU: max(searchBarHeight / scaleFactor, 16), default 40 raw.
		assertEquals(40, SearchBarLayout.height(1));
		assertEquals(20, SearchBarLayout.height(2));
		assertEquals(16, SearchBarLayout.height(4));
	}

	@Test
	void theGroupSitsBottomCenteredOnTheScreen() {
		// A scale-2 1280-wide screen: the group is 252 wide, so its
		// left edge is 514 and the bar starts 26 into the group.
		int scaleFactor = 2;
		int screenWidth = 1280;
		int groupLeft = SearchBarLayout.groupLeft(screenWidth, scaleFactor);
		assertEquals((1280 - 252) / 2, groupLeft);
		assertEquals(groupLeft + 22 + 4, SearchBarLayout.left(screenWidth, scaleFactor));
		assertEquals(200, SearchBarLayout.BAR_WIDTH);
		// NEU's searchBarYOffset: the group's bottom edge ten pixels
		// above the screen's bottom edge.
		assertEquals(800 - 10 - 20, SearchBarLayout.top(800, scaleFactor));
	}

	@Test
	void theFlankingPlatesFlankTheBar() {
		int scaleFactor = 2;
		int screenWidth = 1280;
		int screenHeight = 800;
		int[] settings = SearchBarLayout.settingsButton(screenWidth, screenHeight, scaleFactor);
		int[] mode = SearchBarLayout.modeButton(screenWidth, screenHeight, scaleFactor);
		// The settings plate sits left of the bar, same width as its
		// side; the mode plate sits right of the bar's right edge
		// with the group gap.
		assertEquals(groupLeft(scaleFactor, screenWidth), settings[0]);
		assertEquals(SearchBarLayout.left(screenWidth, scaleFactor) + 200 + 4, mode[0]);
		assertEquals(22, settings[2]);
		assertEquals(22, mode[2]);
		// The plates rise one padding above the bar's top — NEU's
		// button box is the bar plus its padding pair.
		assertEquals(SearchBarLayout.top(screenHeight, scaleFactor) - 1, settings[1]);
		assertEquals(22, settings[3]);
	}

	@Test
	void narrowScreensKeepTheBarOnScreen() {
		int scaleFactor = 2;
		// The group clamps to the screen's left edge instead of
		// going negative.
		assertEquals(0, SearchBarLayout.groupLeft(200, scaleFactor));
		assertTrue(SearchBarLayout.top(30, scaleFactor) >= 0);
	}

	private static int groupLeft(int scaleFactor, int screenWidth) {
		return Math.max(0, (screenWidth - SearchBarLayout.groupWidth(scaleFactor)) / 2);
	}
}
