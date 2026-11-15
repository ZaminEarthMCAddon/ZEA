package net.zaminmc.zea.chat.render;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The tab strip's layout policy: natural fit, padding compression toward
 * its floor, labels that are never shortened no matter how narrow the
 * strip (the dev.35 live report showed a row of "G…" tabs — unreadable),
 * and the scrolling questions that follow from never shortening: how many
 * tabs fit a window, the last offset worth scrolling to, and where to
 * scroll to reveal one tab with minimal movement. The measurer is a fake
 * (every character 6 px) so the tests pin the arithmetic, not a font.
 */
class TabLayoutTest {
	private static final int PADDING = 5;
	private static final int MIN_PADDING = 1;
	private static final int GAP = 1;
	private static final int CHAR = 6;

	private static int measure(String text) {
		return text.length() * CHAR;
	}

	@Test
	void naturalLayoutWhenItFits() {
		TabLayout layout = TabLayout.compute(List.of("All", "Global"), 200,
				PADDING, MIN_PADDING, GAP, TabLayoutTest::measure);

		assertEquals("All", layout.labels()[0]);
		assertEquals("Global", layout.labels()[1]);
		assertEquals(3 * CHAR, layout.textWidths()[0]);
		assertEquals(6 * CHAR, layout.textWidths()[1]);
		assertEquals(3 * CHAR + 2 * PADDING, layout.widths()[0]);
		assertEquals(6 * CHAR + 2 * PADDING, layout.widths()[1]);
		assertEquals(3 * CHAR + 6 * CHAR + 2 * (2 * PADDING) + GAP, layout.total(GAP));
	}

	@Test
	void paddingCompressesBeforeAnythingElse() {
		// Text is 54 px, gaps 1; 66 leaves 11 px of padding across 2 tabs:
		// floor(11 / 4) = 2 px per side, above the floor.
		TabLayout layout = TabLayout.compute(List.of("All", "Global"), 66,
				PADDING, MIN_PADDING, GAP, TabLayoutTest::measure);

		assertEquals(3 * CHAR + 2 * 2, layout.widths()[0]);
		assertEquals(6 * CHAR + 2 * 2, layout.widths()[1]);
		assertEquals(54 + 4 * 2 + GAP, layout.total(GAP)); // 63 <= 66
	}

	@Test
	void paddingStopsAtItsFloor() {
		// 58 leaves no room for even 2 px per side (Java's truncating
		// division of -3/4 is 0): the floor of 1 applies.
		TabLayout layout = TabLayout.compute(List.of("All", "Global"), 58,
				PADDING, MIN_PADDING, GAP, TabLayoutTest::measure);

		assertEquals(3 * CHAR + 2 * MIN_PADDING, layout.widths()[0]);
	}

	@Test
	void labelsAreNeverShortenedNoMatterHowNarrow() {
		// Absurdly narrow: the labels survive intact, the padding bottoms
		// out, and the strip overflows — scrolling is the caller's
		// answer, not ellipses. (The dev.35 report: every tab "G…".)
		TabLayout layout = TabLayout.compute(List.of("All", "Global", "Trade"), 20,
				PADDING, MIN_PADDING, GAP, TabLayoutTest::measure);

		assertEquals("All", layout.labels()[0]);
		assertEquals("Global", layout.labels()[1]);
		assertEquals("Trade", layout.labels()[2]);
		for (int i = 0; i < 3; i++) {
			assertEquals(2 * MIN_PADDING, layout.widths()[i] - layout.textWidths()[i]);
		}
		assertTrue(layout.total(GAP) > 20);
	}

	@Test
	void windowCoversEverythingWhenItFits() {
		TabLayout layout = TabLayout.compute(List.of("All", "Global"), 200,
				PADDING, MIN_PADDING, GAP, TabLayoutTest::measure);

		assertEquals(2, layout.window(0, 200, GAP));
		assertEquals(0, layout.maxScrollOffset(200, GAP));
	}

	@Test
	void windowStopsAtTheBudget() {
		// At floor padding: All = 20 px, Global = 38 px, gap 1.
		// 59 fits both exactly; 58 fits only the first.
		TabLayout layout = TabLayout.compute(List.of("All", "Global"), 58,
				PADDING, MIN_PADDING, GAP, TabLayoutTest::measure);

		assertEquals(2, layout.window(0, 59, GAP));
		assertEquals(1, layout.window(0, 58, GAP));
		assertEquals(1, layout.window(1, 58, GAP));
	}

	@Test
	void windowAlwaysShowsAtLeastOneTab() {
		// No tab fits 5 px — the first still shows, overflowing, because
		// overflow beats hiding. Past the end there is nothing to show.
		TabLayout layout = TabLayout.compute(List.of("All", "Global"), 58,
				PADDING, MIN_PADDING, GAP, TabLayoutTest::measure);

		assertEquals(1, layout.window(0, 5, GAP));
		assertEquals(0, layout.window(2, 58, GAP));
		assertEquals(0, layout.window(-1, 58, GAP));
	}

	@Test
	void maxScrollOffsetKeepsTheLastTabReachable() {
		// Three tabs at floor padding: All = 20 px, Trade = 32, Local = 32,
		// gaps 1 — 65 fits Trade and Local together, so the furthest
		// window starts at Trade and still shows Local beside it.
		TabLayout layout = TabLayout.compute(List.of("All", "Trade", "Local"), 65,
				PADDING, MIN_PADDING, GAP, TabLayoutTest::measure);

		assertEquals(2, layout.window(0, 65, GAP));
		assertEquals(1, layout.maxScrollOffset(65, GAP));
		assertEquals(2, layout.window(1, 65, GAP));

		// A window that swallows everything scrolls nowhere.
		assertEquals(0, layout.maxScrollOffset(200, GAP));
	}

	@Test
	void maxScrollOffsetShowsTheLastTabAloneWhenNothingFitsBesideIt() {
		// At 59 the last tab's predecessor no longer fits (32 + 1 + 32
		// = 65 > 59), so the furthest window is the last tab alone.
		TabLayout layout = TabLayout.compute(List.of("All", "Trade", "Local"), 59,
				PADDING, MIN_PADDING, GAP, TabLayoutTest::measure);

		assertEquals(2, layout.maxScrollOffset(59, GAP));
		assertEquals(1, layout.window(2, 59, GAP));
	}

	@Test
	void maxScrollOffsetNeverLandsPastTheLastTab() {
		// A budget under one tab still reaches the last tab — alone.
		TabLayout layout = TabLayout.compute(List.of("All", "Global"), 58,
				PADDING, MIN_PADDING, GAP, TabLayoutTest::measure);

		assertEquals(1, layout.maxScrollOffset(5, GAP));
		assertEquals(1, layout.window(1, 5, GAP));
	}

	@Test
	void revealDoesNotMoveATabAlreadyInWindow() {
		TabLayout layout = TabLayout.compute(List.of("All", "Trade", "Local"), 65,
				PADDING, MIN_PADDING, GAP, TabLayoutTest::measure);

		assertEquals(0, layout.revealOffset(1, 0, 65, GAP)); // second tab visible at 0
	}

	@Test
	void revealScrollsBackToATabThatFellOffTheLeft() {
		TabLayout layout = TabLayout.compute(List.of("All", "Trade", "Local"), 65,
				PADDING, MIN_PADDING, GAP, TabLayoutTest::measure);

		assertEquals(0, layout.revealOffset(0, 1, 65, GAP));
	}

	@Test
	void revealDocksATabThatFellOffTheRightAtTheWindowEdge() {
		// From offset 0 the third tab is past the window (two fit in
		// 65); revealing it scrolls minimally — the window that covers
		// it with the second tab still in view ahead of it.
		TabLayout layout = TabLayout.compute(List.of("All", "Trade", "Local"), 65,
				PADDING, MIN_PADDING, GAP, TabLayoutTest::measure);

		assertEquals(1, layout.revealOffset(2, 0, 65, GAP));

		// In 59 nothing fits beside the third tab (32 + 1 + 32 = 65),
		// so revealing it shows it alone — still the minimal scroll.
		assertEquals(2, layout.revealOffset(2, 0, 59, GAP));
	}

	@Test
	void revealClampsWaywardArguments() {
		TabLayout layout = TabLayout.compute(List.of("All", "Global"), 200,
				PADDING, MIN_PADDING, GAP, TabLayoutTest::measure);

		assertEquals(1, layout.revealOffset(99, 99, 200, GAP));
		assertEquals(0, layout.revealOffset(-5, 1, 200, GAP));
	}

	@Test
	void noTabsLayOutEmpty() {
		TabLayout layout = TabLayout.compute(List.of(), 100,
				PADDING, MIN_PADDING, GAP, TabLayoutTest::measure);

		assertEquals(0, layout.widths().length);
		assertEquals(0, layout.total(GAP));
		assertEquals(0, layout.window(0, 100, GAP));
		assertEquals(0, layout.maxScrollOffset(100, GAP));
		assertEquals(0, layout.revealOffset(0, 0, 100, GAP));
	}

	@Test
	void singleTabHasNoGaps() {
		TabLayout layout = TabLayout.compute(List.of("Global"), 100,
				PADDING, MIN_PADDING, GAP, TabLayoutTest::measure);

		assertEquals(6 * CHAR + 2 * PADDING, layout.total(GAP));
	}
}
