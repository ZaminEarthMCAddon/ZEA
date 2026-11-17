package net.zaminmc.zea.search;

/**
 * The matching-mode visuals' colors and fill decisions, kept pure so the
 * exact values are pinned by tests. Matching slots get a subtle colored
 * fill under the item plus a one-pixel border around the slot's 16×16
 * area; non-matching stacks get a dark overlay on top that leaves the
 * silhouette visible. Empty slots dim like everything else — a blank
 * slot must not keep shouting while the items around it go dark — but
 * they never carry the match highlight, since there is nothing in them
 * to match.
 */
public final class SearchOverlay {
	/** The dropdown's highlight color choices. */
	public enum HighlightColor {
		GREEN("Green"),
		BLUE("Blue"),
		YELLOW("Yellow"),
		ORANGE("Orange");

		private final String label;

		HighlightColor(String label) {
			this.label = label;
		}

		/** The label the dropdown shows. */
		public String label() {
			return label;
		}

		@Override
		public String toString() {
			return label;
		}

		/** The choice for a stored name, falling back to green. */
		public static HighlightColor of(@org.jspecify.annotations.Nullable String name) {
			if (name != null) {
				for (HighlightColor color : values()) {
					if (color.name().equalsIgnoreCase(name)) {
						return color;
					}
				}
			}
			return GREEN;
		}
	}

	/** The fill under a matching item: color at ~20 % alpha. */
	public static int fillArgb(HighlightColor color) {
		return base(color) | 0x33000000;
	}

	/** The border around a matching slot: color at ~70 % alpha. */
	public static int borderArgb(HighlightColor color) {
		return base(color) | 0xB3000000;
	}

	/** The overlay over a non-matching stack: black at the configured intensity. */
	public static int dimArgb(int intensityPercent) {
		int alpha = (int) Math.clamp(intensityPercent, MIN_DIM_INTENSITY, MAX_DIM_INTENSITY) * 255 / 100;
		return alpha << 24;
	}

	/** Dim intensity bounds, in percent of opacity. */
	public static final int MIN_DIM_INTENSITY = 30;
	public static final int MAX_DIM_INTENSITY = 90;
	/** The default dim intensity: clearly darker, still identifiable. */
	public static final int DEFAULT_DIM_INTENSITY = 62;

	private static int base(HighlightColor color) {
		return switch (color) {
			case GREEN -> 0x00C850;
			case BLUE -> 0x3C8CFF;
			case YELLOW -> 0xE8C832;
			case ORANGE -> 0xE8862A;
		};
	}
}
