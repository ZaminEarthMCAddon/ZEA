package net.zaminmc.zea.chat.render;

import net.minecraft.util.FormattedCharSequence;

import java.util.HashSet;
import java.util.Set;

/**
 * The smooth-chat arrival animation, matching Polyfrost's Chatting:
 * each arriving message opens one animation window — the whole chat
 * starts one line lower than its settled position and glides up while
 * the new lines fade in.
 *
 * <p>The window is per message: {@link #start} clears the tracked line
 * set, so when two messages arrive back to back the second restarts the
 * glide and the first's lines stop fading (they are already settled —
 * that is Chatting's exact behavior). The vertical offset covers a
 * single line height no matter how many lines the message wrapped; the
 * fade is what hides the remainder of the jump for the new content.
 *
 * <p>Easing is quartic ease-out ({@code 1 - (1 - x)^4}): fast at first,
 * settling gently. While the player is scrolled up the offset stays at
 * zero — vanilla pins the view there and translating it would push
 * pinned content around — but the fade still applies, so new lines
 * appear gently in place. The line height comes in per call so the
 * chat-line-spacing option stays honored.
 *
 * <p>Pure and headless-testable: time comes in as a nanosecond stamp,
 * never read from the clock.
 */
public final class SmoothChat {
	private long startNanos;
	private int durationMillis;
	private boolean animating;
	private final Set<FormattedCharSequence> newLines = new HashSet<>();

	/**
	 * Opens a new animation window for one arriving message.
	 *
	 * @param nowNanos the time the message was added
	 * @param durationMillis window length; zero or less disables the
	 *                       effect entirely (the window closes at once)
	 */
	public void start(long nowNanos, int durationMillis) {
		newLines.clear();
		animating = durationMillis > 0;
		this.durationMillis = durationMillis;
		startNanos = nowNanos;
	}

	/**
	 * Records one wrapped line of the message currently being added —
	 * the lines that fade in over the window.
	 */
	public void addLine(FormattedCharSequence content) {
		if (animating) {
			newLines.add(content);
		}
	}

	/**
	 * The animation progress: {@code 1 - (1 - x)^4} over the window,
	 * exactly 1 once it has finished (or when disabled). Progress 1
	 * closes the window.
	 */
	public float percent(long nowNanos) {
		if (!animating) {
			return 1f;
		}
		float x = (nowNanos - startNanos) / 1_000_000f / Math.max(durationMillis, 1);
		if (x >= 1f) {
			animating = false;
			return 1f;
		}
		return (float) easeOutQuartic(Math.max(x, 0f));
	}

	/**
	 * The vertical offset to translate the chat by, in unscaled chat
	 * units: one line height shrinking to zero over the window — the
	 * glide. Zero while scrolled up (vanilla pins the view) and when
	 * the window has closed.
	 *
	 * @param lineHeightUnits the entry height in chat units
	 * @param scrolled whether the chat is scrolled above its newest line
	 * @param nowNanos the current time
	 */
	public float translateUnits(double lineHeightUnits, boolean scrolled, long nowNanos) {
		if (scrolled || !animating) {
			return 0f;
		}
		float remaining = 1f - percent(nowNanos);
		if (remaining <= 0f) {
			return 0f;
		}
		return (float) (lineHeightUnits * remaining);
	}

	/**
	 * The alpha factor for one rendered line: below 1 only for the
	 * lines the current window added, 1 for everything else.
	 */
	public float fadeAlpha(FormattedCharSequence content, long nowNanos) {
		if (!animating || !newLines.contains(content)) {
			return 1f;
		}
		return percent(nowNanos);
	}

	/** Quartic ease-out: {@code 1 - (1 - t)^4}. */
	static double easeOutQuartic(double t) {
		double remaining = 1 - t;
		return 1 - remaining * remaining * remaining * remaining;
	}
}
