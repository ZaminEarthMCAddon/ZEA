package net.zaminmc.zea.chat.render;

import java.util.List;
import java.util.function.ToIntFunction;

/**
 * The chat tab strip's layout policy: how tab labels share a maximum
 * width. A pure computation, so the behavior is pinned by tests without
 * Minecraft.
 *
 * <p>Labels are never shortened — a strip of "G…" tabs tells the player
 * nothing (the dev.34 live report, where a mis-sized reserve left the
 * whole strip ~45 px). Instead the policy is two stages: the natural
 * layout (label plus padding), then padding compressed toward
 * {@code minPadding} — a denser strip beats hiding tabs. If the tabs
 * still do not fit, that is the caller's business: the strip scrolls
 * by whole tabs, and this class answers the scrolling questions — how
 * many tabs fit a window ({@link #window}), the last scroll position
 * worth having ({@link #maxScrollOffset}), and where to scroll to bring
 * one tab into view with minimal movement ({@link #revealOffset}).
 *
 * <p>The strip is clamped to the chat panel's on-screen width. In 26.3
 * that width is {@code ChatComponent.getWidth(chatWidthOption)} —
 * {@code floor(option * 280 + 40)} screen pixels, independent of chat
 * scale, because the renderer scales by {@code 1/scale} internally
 * (verified against the 26.3 bytecode). An earlier note assumed the
 * on-screen width scaled with chat scale; it does not.
 *
 * @param widths     the drawn width of each tab, parallel to {@code labels}
 * @param textWidths the drawn width of each label alone — so the strip
 *                   can center a label in its tab at any padding
 * @param labels     the full label each tab draws
 */
public record TabLayout(int[] widths, int[] textWidths, String[] labels) {
	/** Sum of tab widths and gaps; the strip's full drawn extent. */
	public int total(int gap) {
		int sum = 0;
		for (int width : widths) {
			sum += width;
		}
		return sum + (widths.length > 1 ? gap * (widths.length - 1) : 0);
	}

	/**
	 * Lays out {@code labels} for a strip at most {@code maxWidth} wide.
	 *
	 * @param labels     the full tab labels, in display order
	 * @param maxWidth   the widest the strip may be
	 * @param padding    the natural inner padding of a tab, each side
	 * @param minPadding the floor padding compression stops at
	 * @param gap        pixels between tabs
	 * @param measure    how wide a string renders
	 */
	public static TabLayout compute(List<String> labels, int maxWidth, int padding, int minPadding,
			int gap, ToIntFunction<String> measure) {
		int count = labels.size();
		String[] display = labels.toArray(String[]::new);
		int[] textWidths = new int[count];
		for (int i = 0; i < count; i++) {
			textWidths[i] = measure.applyAsInt(display[i]);
		}

		int pad = padding;
		if (count > 0 && total(textWidths, pad, gap) > maxWidth) {
			// Compress the padding toward its floor before giving up.
			int room = maxWidth - gaps(count, gap);
			int text = 0;
			for (int width : textWidths) {
				text += width;
			}
			pad = Math.max(minPadding, Math.min(padding, (room - text) / (2 * count)));
		}

		int[] widths = new int[count];
		for (int i = 0; i < count; i++) {
			widths[i] = textWidths[i] + 2 * pad;
		}
		return new TabLayout(widths, textWidths, display);
	}

	/**
	 * How many tabs fit {@code budget} starting at {@code from}: the
	 * widest prefix of the remaining strip. Always at least one — a
	 * single tab overflows the budget rather than hiding, the same
	 * "overflow beats hiding" rule the strip has always had. Tabs past
	 * the end, or a {@code from} out of range, yield zero.
	 */
	public int window(int from, int budget, int gap) {
		if (from < 0 || from >= widths.length) {
			return 0;
		}
		int sum = 0;
		int count = 0;
		for (int i = from; i < widths.length; i++) {
			int next = sum + widths[i] + (count > 0 ? gap : 0);
			if (count > 0 && next > budget) {
				break;
			}
			sum = next;
			count++;
		}
		return count;
	}

	/**
	 * The furthest the strip may scroll: the offset whose window shows
	 * the final tab — the last tab always reachable, never a blank
	 * strip one past it. If even the final tab alone overflows the
	 * budget, it still shows (alone), so the answer is
	 * {@code widths.length - 1}.
	 */
	public int maxScrollOffset(int budget, int gap) {
		if (widths.length == 0) {
			return 0;
		}
		int sum = 0;
		for (int start = widths.length - 1; start >= 0; start--) {
			int addition = widths[start] + (start < widths.length - 1 ? gap : 0);
			if (sum + addition > budget && start < widths.length - 1) {
				return start + 1;
			}
			sum += addition;
		}
		return 0;
	}

	/**
	 * The offset that brings tab {@code index} into a window of
	 * {@code budget}, moving as little as possible from
	 * {@code current}: no move when it is already visible; the index
	 * itself when it fell off the left; and when it fell off the
	 * right, the smallest offset whose window still covers it — the
	 * tab docks at the window's right edge with as many earlier tabs
	 * as fit kept in view.
	 */
	public int revealOffset(int index, int current, int budget, int gap) {
		if (widths.length == 0) {
			return 0;
		}
		int target = Math.clamp(index, 0, widths.length - 1);
		int from = Math.clamp(current, 0, widths.length - 1);
		if (target < from) {
			return target;
		}
		if (target < from + window(from, budget, gap)) {
			return from;
		}
		// Right-align: walk left while the window still covers the tab.
		// Covering fails monotonically — one step left needs one more
		// tab in the window — so the first failure ends the walk.
		int best = target;
		for (int offset = target; offset >= 0; offset--) {
			if (offset + window(offset, budget, gap) - 1 >= target) {
				best = offset;
			} else {
				break;
			}
		}
		return best;
	}

	private static int total(int[] textWidths, int padding, int gap) {
		int sum = 0;
		for (int width : textWidths) {
			sum += width;
		}
		return sum + 2 * padding * textWidths.length + gaps(textWidths.length, gap);
	}

	private static int gaps(int count, int gap) {
		return count > 1 ? gap * (count - 1) : 0;
	}
}
