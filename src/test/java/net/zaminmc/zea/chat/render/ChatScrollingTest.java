package net.zaminmc.zea.chat.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The smooth-scroll model's pure behavior, Chatting's exact rules: the
 * anchor stays the truth, a scroll-armed jump of more than one line
 * glides on a quadratic ease-out, a single notch snaps, an unarmed
 * anchor change snaps, and mid-animation anchor shifts ride along.
 */
class ChatScrollingTest {
	private static final int DURATION = 150;

	/** The model takes nanosecond stamps; the specs read in milliseconds. */
	private static long ms(long millis) {
		return millis * 1_000_000;
	}

	@Test
	void anArmedMultiLineJumpStartsAGlideFromTheCurrentPosition() {
		ChatScrolling scrolling = new ChatScrolling();
		long now = 0;
		// First step initializes at the anchor; nothing animates yet.
		scrolling.step(0, DURATION, ms(now));

		scrolling.arm();
		now += 16;
		scrolling.step(5, DURATION, ms(now));
		assertEquals(0f, scrolling.current(), 1e-6,
				"the jump frame still renders where the player was");
		now += 16;
		scrolling.step(5, DURATION, ms(now));
		assertTrue(scrolling.pos() > 0 && scrolling.pos() < 5,
				"mid-glide the rendered position is between the endpoints");
	}

	@Test
	void theGlideFollowsQuadraticEaseOut() {
		ChatScrolling scrolling = new ChatScrolling();
		scrolling.step(0, DURATION, ms(0));
		scrolling.arm();
		scrolling.step(10, DURATION, ms(1000));

		// Stepping with an unchanged anchor advances the animation clock.
		scrolling.step(10, DURATION, ms(1000 + DURATION / 4));
		float quarter = scrolling.current();
		scrolling.step(10, DURATION, ms(1000 + DURATION / 2));
		float half = scrolling.current();

		// 1 - (1 - x)^2 with the stamp's own int-divided progress.
		assertEquals(10 * ChatScrolling.easeOutQuad((float) (DURATION / 4) / DURATION), quarter, 0.02);
		assertEquals(10 * ChatScrolling.easeOutQuad((float) (DURATION / 2) / DURATION), half, 0.02);
		assertTrue(half > quarter);
	}

	@Test
	void aSingleNotchSnapsInsteadOfGliding() {
		ChatScrolling scrolling = new ChatScrolling();
		scrolling.step(0, DURATION, ms(0));
		scrolling.arm();
		scrolling.step(1, DURATION, ms(1000));

		assertEquals(1, scrolling.pos(),
				"Chatting's rule: a one-line jump lands instantly");
	}

	@Test
	void anUnarmedJumpSnaps() {
		ChatScrolling scrolling = new ChatScrolling();
		scrolling.step(0, DURATION, ms(0));
		scrolling.step(5, DURATION, ms(1000));

		assertEquals(5, scrolling.pos(),
				"anchor moves that did not come from a scroll never glide");
	}

	@Test
	void theGlideSettlesExactlyOnTheAnchor() {
		ChatScrolling scrolling = new ChatScrolling();
		scrolling.step(0, DURATION, ms(0));
		scrolling.arm();
		scrolling.step(5, DURATION, ms(0));
		scrolling.step(5, DURATION, ms(DURATION));

		assertEquals(5, scrolling.pos());
		// And stays there without further steps feeding it.
		assertEquals(5, scrolling.pos());
	}

	@Test
	void aMidFlightMessageShiftRidesTheAnimation() {
		ChatScrolling scrolling = new ChatScrolling();
		scrolling.step(0, DURATION, ms(0));
		scrolling.arm();
		scrolling.step(4, DURATION, ms(0));
		scrolling.step(4, DURATION, ms(60));

		int mid = scrolling.pos();
		assertTrue(mid > 0 && mid < 4, "precondition: mid-glide, mid=" + mid);

		// Two new lines pin the anchor down by two while the glide runs;
		// the whole animation (from, to, current) shifts with it.
		scrolling.shift(2);
		scrolling.step(6, DURATION, ms(80));

		assertTrue(scrolling.pos() >= mid,
				"the glide continues from where the shift put it");
		assertTrue(scrolling.pos() < 6, "still mid-glide toward the moved target");
		scrolling.step(6, DURATION, ms(DURATION + 97));
		assertEquals(6, scrolling.pos(), "the glide lands on the shifted anchor");
	}

	@Test
	void aShiftBeforeAnyStepIsInert() {
		ChatScrolling scrolling = new ChatScrolling();
		scrolling.shift(3);
		scrolling.step(0, DURATION, ms(0));
		assertEquals(0, scrolling.pos());
	}

	@Test
	void aZeroDurationSnapsEverywhere() {
		ChatScrolling scrolling = new ChatScrolling();
		scrolling.step(0, 0, ms(0));
		scrolling.arm();
		scrolling.step(5, 0, ms(1000));

		assertEquals(5, scrolling.pos(),
				"with the effect disabled the rendered position is the anchor");
	}

	@Test
	void negativePositionsClampToZero() {
		ChatScrolling scrolling = new ChatScrolling();
		scrolling.step(5, DURATION, ms(0));
		scrolling.arm();
		scrolling.step(0, DURATION, ms(0));
		scrolling.step(0, DURATION, ms(DURATION));
		assertEquals(0, scrolling.pos());

		// A shift pushing a half-settled glide below zero clamps, too.
		scrolling.arm();
		scrolling.step(3, DURATION, ms(DURATION + 1));
		scrolling.shift(-10);
		assertEquals(0, scrolling.pos());
	}

	@Test
	void consumingTheArmStopsItFromAnimatingLaterJumps() {
		ChatScrolling scrolling = new ChatScrolling();
		scrolling.step(0, DURATION, ms(0));
		scrolling.arm();
		// The first step consumes the arm; the jump still animates because
		// the arm was live at the step. A later armed-less jump must snap.
		scrolling.step(4, DURATION, ms(0));
		scrolling.step(8, DURATION, ms(DURATION / 2));

		assertEquals(8, scrolling.pos(),
				"one arm animates one jump, exactly Chatting's shouldSmooth");
	}
}
