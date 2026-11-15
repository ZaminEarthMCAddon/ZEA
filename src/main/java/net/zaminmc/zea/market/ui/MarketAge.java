package net.zaminmc.zea.market.ui;

import java.time.Duration;
import java.time.Instant;

/**
 * Human observation ages, phrased exactly as the market speaks: how
 * long ago a shop was observed — never a claim that the observation is
 * still true. There is deliberately no expiry threshold here: freshness
 * is data (the timestamp), not a policy.
 */
public final class MarketAge {
	private MarketAge() {
	}

	/** {@code just now}, {@code 12 minutes ago}, {@code 3 hours ago}, {@code 5 days ago}. */
	public static String format(Instant observedAt, Instant now) {
		if (observedAt == null || now == null) {
			return "";
		}
		Duration age = Duration.between(observedAt, now);
		if (age.isNegative()) {
			age = Duration.ZERO;
		}
		long minutes = age.toMinutes();
		if (minutes < 1) {
			return "just now";
		}
		if (minutes < 60) {
			return minutes + " minute" + plural(minutes) + " ago";
		}
		long hours = age.toHours();
		if (hours < 24) {
			return hours + " hour" + plural(hours) + " ago";
		}
		long days = age.toDays();
		return days + " day" + plural(days) + " ago";
	}

	/** The lowercase phrase used after a "Based on observations" lead-in. */
	public static String sincePhrase(Instant observedAt, Instant now) {
		String age = format(observedAt, now);
		return age.equals("just now") ? age : age.replace(" ago", "") + " old";
	}

	private static String plural(long count) {
		return count == 1 ? "" : "s";
	}

	/** Whether an observation is older than a day — display-only hinting. */
	public static boolean isOlderThanADay(Instant observedAt, Instant now) {
		return observedAt != null && now != null
				&& Duration.between(observedAt, now).compareTo(Duration.ofDays(1)) > 0;
	}
}
