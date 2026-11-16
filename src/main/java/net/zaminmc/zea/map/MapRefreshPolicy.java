package net.zaminmc.zea.map;

/**
 * The refresh cadences of the map's live data, as one pure policy so the
 * numbers are pinned by tests and the subsystem stays dumb.
 *
 * <p>Two rules drive every period here:
 *
 * <ul>
 * <li><b>Network is scheduled, never frame-driven.</b> Claims and the
 *     player feed refresh on their own timers, read from the freshness
 *     bookkeeping the data clients already maintain; a render frame
 *     never starts a request.
 * <li><b>Idle backs off.</b> When no map surface has been drawn
 *     recently, the player feed — the one feed whose data goes stale in
 *     seconds — slows to the idle cadence. Claims change on the scale
 *     of minutes and keep one calm period either way.
 * </ul>
 */
public final class MapRefreshPolicy {

	/** The player feed's period while a map surface is on screen. */
	public static final long PLAYERS_ACTIVE_MS = 1_000L;

	/** The player feed's period while nothing watches it. */
	public static final long PLAYERS_IDLE_MS = 15_000L;

	/** The claims document's period, both worlds alike. */
	public static final long MARKERS_MS = 60_000L;

	/** How long after the last surface draw the active cadence survives. */
	public static final long ACTIVITY_WINDOW_MS = 5_000L;

	private MapRefreshPolicy() {
	}

	/** The players period for the current activity: live while watched. */
	public static long playersPeriod(boolean surfaceRecentlyDrawn) {
		return surfaceRecentlyDrawn ? PLAYERS_ACTIVE_MS : PLAYERS_IDLE_MS;
	}

	/**
	 * Whether a feed is due: the elapsed time since its last attempt has
	 * reached the period. A never-attempted feed (last attempt 0, clock 0)
	 * is due immediately.
	 */
	public static boolean due(long nowMs, long lastAttemptMs, long periodMs) {
		return lastAttemptMs <= 0 || nowMs - lastAttemptMs >= periodMs;
	}

	/**
	 * Whether a surface counts as recently drawn: within
	 * {@link #ACTIVITY_WINDOW_MS} of now, or never drawn (in which case
	 * there is nothing to be idle about yet and the first poll should not
	 * stall behind the idle cadence).
	 */
	public static boolean active(long nowMs, long lastSurfaceDrawMs) {
		return lastSurfaceDrawMs <= 0 || nowMs - lastSurfaceDrawMs <= ACTIVITY_WINDOW_MS;
	}
}
