package net.zaminmc.zea.map.model;

/**
 * Data-freshness accounting for one live data feed (claims, players).
 *
 * <p>The three timestamps answer different questions and must never be
 * collapsed into one:
 *
 * <ul>
 * <li>{@link #lastSuccessMs} — when good data last landed. A run of
 *     failures keeps bumping the attempt time while the on-screen data
 *     goes stale, and it is <b>this</b> age the user must see, not the
 *     attempt age. A failed refresh never resets it.
 * <li>{@link #lastAttemptMs} — when we last tried.
 * <li>{@link #failing} — whether the newest attempt failed, so a stale
 *     feed reads as "stale (refresh failed)" rather than silently looking
 *     current.
 * </ul>
 *
 * <p>Immutable; callers hold the latest instance and replace it wholesale
 * so a reader can never see a half-updated state.
 */
public record Freshness(long lastAttemptMs, long lastSuccessMs, boolean failing, int lastRefusedStatus) {

	public static final Freshness NEVER = new Freshness(0, 0, false, 0);

	public Freshness {
		if (lastSuccessMs > lastAttemptMs) {
			throw new IllegalArgumentException("success cannot precede its attempt");
		}
	}

	/** Records the start of an attempt. */
	public Freshness attempting(long nowMs) {
		return new Freshness(nowMs, lastSuccessMs, failing, lastRefusedStatus);
	}

	/** Records a successful refresh landing at {@code nowMs}. */
	public Freshness succeeded(long nowMs) {
		return new Freshness(nowMs, nowMs, false, 0);
	}

	/** Records a failed refresh; the success timestamp is untouched. */
	public Freshness failed(long nowMs) {
		return new Freshness(nowMs, lastSuccessMs, true, lastRefusedStatus);
	}

	/** Records a refused HTTP status alongside the failure. */
	public Freshness refused(long nowMs, int status) {
		return new Freshness(nowMs, lastSuccessMs, true, status);
	}

	public boolean hasData() {
		return lastSuccessMs > 0;
	}

	/** Seconds since the last successful refresh; 0 when none landed yet. */
	public long ageSeconds(long nowMs) {
		return lastSuccessMs <= 0 ? 0 : Math.max(0, (nowMs - lastSuccessMs) / 1000L);
	}

	/** Human-readable age, e.g. {@code "12s"}, {@code "4m"}, {@code "2h"}. */
	public String ageText(long nowMs) {
		long seconds = ageSeconds(nowMs);
		if (seconds < 60) {
			return seconds + "s";
		}
		if (seconds < 3600) {
			return seconds / 60 + "m";
		}
		return seconds / 3600 + "h";
	}

	/** A one-line status: what is on screen and how old it is. */
	public String statusText(long nowMs, String subject) {
		if (!hasData()) {
			return failing ? subject + " unavailable" : "Loading " + subject.toLowerCase() + "...";
		}
		if (failing) {
			return subject + " " + ageText(nowMs) + " ago (refresh failed)";
		}
		return subject + " " + ageText(nowMs) + " ago";
	}
}
