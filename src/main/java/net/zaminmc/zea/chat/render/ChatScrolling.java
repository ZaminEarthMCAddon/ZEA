package net.zaminmc.zea.chat.render;

/**
 * The smooth-scroll model, matching Polyfrost's Chatting: the scroll
 * position the player sees is an animated float chasing the vanilla
 * integer anchor, eased with quadratic ease-out, so wheel and key
 * scrolling glide to their target instead of jumping line by line.
 *
 * <p>The vanilla anchor ({@code chatScrollbarPos}) stays the source of
 * truth — the renderer reads the eased value only where vanilla renders
 * lines and the scrollbar, so hit-testing, message pinning and the
 * "new messages" flag all keep working on the real anchor. Because the
 * animation moves content inside the chat's own clip, nothing outside
 * the chat element moves (the dev.26 drift that killed the previous
 * pose-translate experiment cannot recur).
 *
 * <p>Chatting's rules, kept exactly: an animation starts only when the
 * scroll was armed by an actual scroll event and the anchor jumped by
 * more than one line (a single notch snaps, the way Chatting ships);
 * new-message anchor shifts ride along mid-animation so a burst of
 * messages never fights the glide; the duration is re-read per start so
 * a config change applies to the next scroll immediately; and with the
 * effect disabled every step snaps, making the model a pass-through.
 *
 * <p>Pure and headless-testable: time comes in as a nanosecond stamp,
 * never read from the clock.
 */
public final class ChatScrolling {
	private float current;
	private float from;
	private float to;
	private long startNanos;
	private int durationMillis;
	private boolean initialized;
	private int frozen;
	private boolean shouldSmooth;

	/**
	 * Arms the next anchor jump for animation. Set by the scroll entry
	 * point (vanilla's {@code scrollChat}) — adds that shift the anchor
	 * internally do not arm, or every arriving message would glide the
	 * scrolled-up view on its own.
	 */
	public void arm() {
		shouldSmooth = true;
	}

	/**
	 * Advances the animation toward the anchor. Called once per frame
	 * from the render head; {@code nowNanos} must be non-decreasing
	 * between calls.
	 *
	 * @param actual the vanilla anchor this frame
	 * @param durationMillis animation length, re-read per call; zero or
	 *                      less snaps
	 * @param nowNanos the current time
	 */
	public void step(int actual, int durationMillis, long nowNanos) {
		if (!initialized) {
			current = actual;
			to = current;
			initialized = true;
		}
		if ((float) actual != to) {
			if (shouldSmooth && Math.abs((float) actual - current) > 1f) {
				from = current;
				to = actual;
				this.durationMillis = durationMillis;
				startNanos = nowNanos;
			} else {
				from = actual;
				to = actual;
				current = actual;
			}
		}
		shouldSmooth = false;
		current = durationMillis <= 0 ? to : eased(nowNanos);
		frozen = Math.round(current);
	}

	/**
	 * Tracks an anchor shift that happened outside a scroll event: when
	 * new messages push the pinned anchor down, the running animation's
	 * from/to/current ride along by the same amount, so the glide lands
	 * where the content now lives.
	 */
	public void shift(int delta) {
		if (!initialized || delta == 0) {
			return;
		}
		from = Math.max(0f, from + delta);
		to = Math.max(0f, to + delta);
		current = Math.max(0f, current + delta);
		frozen = Math.round(current);
	}

	/** The scroll position to render with, in anchor units. */
	public int pos() {
		return Math.max(frozen, 0);
	}

	/** The raw eased position; a test seam — the renderer sees {@link #pos()}. */
	float current() {
		return current;
	}

	private float eased(long nowNanos) {
		float x = (nowNanos - startNanos) / 1_000_000f / Math.max(durationMillis, 1);
		if (x >= 1f) {
			return to;
		}
		if (x <= 0f) {
			return from;
		}
		return from + (to - from) * easeOutQuad(x);
	}

	/** Quadratic ease-out, Chatting's curve: {@code 1 - (1 - x)^2}. */
	static float easeOutQuad(float x) {
		float remaining = 1 - x;
		return 1 - remaining * remaining;
	}
}
