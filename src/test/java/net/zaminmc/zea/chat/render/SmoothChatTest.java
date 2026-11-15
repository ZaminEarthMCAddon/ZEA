package net.zaminmc.zea.chat.render;

import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The arrival window's pure behavior: one glide per arriving message —
 * the whole chat one line low easing up while the message's own lines
 * fade in — with Chatting's exact curves and gates.
 */
class SmoothChatTest {
	private static final int DURATION = 500;
	private static final double LINE = 10.0;

	/** The model takes nanosecond stamps; the specs read in milliseconds. */
	private static long ms(long millis) {
		return millis * 1_000_000;
	}

	private static FormattedCharSequence aLine() {
		// Each call returns a fresh instance — the fade set keys on identity,
		// so "fresh" and "old" lines must be distinct objects.
		return FormattedCharSequence.forward("line", Style.EMPTY);
	}

	@Test
	void theGlideStartsOneLineLowAndSettlesExactly() {
		SmoothChat smooth = new SmoothChat();
		long now = ms(1000);
		smooth.start(now, DURATION);
		smooth.addLine(aLine());

		assertEquals(LINE, smooth.translateUnits(LINE, false, now), 1e-9,
				"the chat starts one full line below its anchor");
		assertEquals(0.0, smooth.translateUnits(LINE, false, now + ms(DURATION)), 0.0,
				"the window ends exactly settled");
		assertEquals(0.0, smooth.translateUnits(LINE, false, now + ms(DURATION) + ms(5000)), 0.0);
	}

	@Test
	void theGlideEasesOutQuartically() {
		SmoothChat smooth = new SmoothChat();
		smooth.start(0, DURATION);
		smooth.addLine(aLine());

		double quarter = smooth.translateUnits(LINE, false, ms(DURATION / 4));
		double half = smooth.translateUnits(LINE, false, ms(DURATION / 2));

		// Remaining fraction is (1 - t)^4: 0.75^4 and 0.5^4.
		assertEquals(LINE * 0.31640625, quarter, 1e-6);
		assertEquals(LINE * 0.0625, half, 1e-6);
		// Fast start, gentle settle.
		assertTrue(quarter < LINE * 0.4 && quarter > LINE * 0.25, "quarter: " + quarter);
		assertTrue(half < quarter);
	}

	@Test
	void scrollingUpStopsTheGlideButKeepsTheFade() {
		SmoothChat smooth = new SmoothChat();
		smooth.start(0, DURATION);
		FormattedCharSequence line = aLine();
		smooth.addLine(line);

		assertEquals(0.0, smooth.translateUnits(LINE, true, 0),
				"a pinned view does not translate");
		assertTrue(smooth.fadeAlpha(line, 0) < 1f,
				"the fade still applies while scrolled up");
	}

	@Test
	void onlyTheWindowLinesFade() {
		SmoothChat smooth = new SmoothChat();
		smooth.start(0, DURATION);
		FormattedCharSequence fresh = aLine();
		FormattedCharSequence old = aLine();
		smooth.addLine(fresh);

		assertEquals(1f, smooth.fadeAlpha(old, 0),
				"a line the window did not add renders at full alpha");
		assertTrue(smooth.fadeAlpha(fresh, 0) < 1f);
		assertEquals(1f, smooth.fadeAlpha(fresh, ms(DURATION)),
				"after the window the fade is exactly done");
	}

	@Test
	void aSecondMessageRestartsTheWindowAndDropsTheFirstLines() {
		SmoothChat smooth = new SmoothChat();
		smooth.start(0, DURATION);
		FormattedCharSequence first = aLine();
		smooth.addLine(first);

		long second = ms(DURATION / 2);
		smooth.start(second, DURATION);
		FormattedCharSequence secondLine = aLine();
		smooth.addLine(secondLine);

		// Chatting's model: the second start clears the first message's
		// lines, so the first stops fading and the glide restarts full.
		assertEquals(1f, smooth.fadeAlpha(first, second),
				"the first message's lines are settled for good");
		assertTrue(smooth.fadeAlpha(secondLine, second) < 1f);
		assertEquals(LINE, smooth.translateUnits(LINE, false, second), 1e-9,
				"the glide restarts from a full line");
	}

	@Test
	void aZeroDurationDisablesTheWindow() {
		SmoothChat smooth = new SmoothChat();
		smooth.start(0, 0);
		FormattedCharSequence line = aLine();
		smooth.addLine(line);

		assertEquals(0.0, smooth.translateUnits(LINE, false, 0));
		assertEquals(1f, smooth.fadeAlpha(line, 0),
				"nothing fades when the window is closed");
		assertFalse(smooth.percent(0) < 1f);
	}

	@Test
	void thePercentCurveIsQuarticEaseOut() {
		SmoothChat smooth = new SmoothChat();
		smooth.start(0, DURATION);
		smooth.addLine(aLine());

		assertEquals(1f - (float) Math.pow(0.75, 4), smooth.percent(ms(DURATION / 4)), 1e-6);
		assertEquals(1f - (float) Math.pow(0.5, 4), smooth.percent(ms(DURATION / 2)), 1e-6);
		assertEquals(1f, smooth.percent(ms(DURATION)), 0f);
	}
}
