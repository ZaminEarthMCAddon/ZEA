package net.zaminmc.zea.map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The refresh cadences: due arithmetic, activity window, idle backoff. */
class MapRefreshPolicyTest {

	@Test
	void neverAttemptedFeedsAreDueImmediately() {
		assertTrue(MapRefreshPolicy.due(0, 0, MapRefreshPolicy.MARKERS_MS));
		assertTrue(MapRefreshPolicy.due(123, 0, MapRefreshPolicy.PLAYERS_ACTIVE_MS));
	}

	@Test
	void aFeedIsDueExactlyAtItsPeriod() {
		assertTrue(MapRefreshPolicy.due(61_000, 1_000, MapRefreshPolicy.MARKERS_MS));
		assertFalse(MapRefreshPolicy.due(59_999, 1_000, MapRefreshPolicy.MARKERS_MS));
		assertFalse(MapRefreshPolicy.due(159_999, 145_000, MapRefreshPolicy.PLAYERS_IDLE_MS));
		assertTrue(MapRefreshPolicy.due(160_000, 145_000, MapRefreshPolicy.PLAYERS_IDLE_MS));
		assertTrue(MapRefreshPolicy.due(146_000, 145_000, MapRefreshPolicy.PLAYERS_ACTIVE_MS));
		assertFalse(MapRefreshPolicy.due(145_500, 145_000, MapRefreshPolicy.PLAYERS_ACTIVE_MS));
	}

	@Test
	void thePlayersFeedSlowsWhenNothingWatchesIt() {
		assertEquals(MapRefreshPolicy.PLAYERS_ACTIVE_MS, MapRefreshPolicy.playersPeriod(true));
		assertEquals(MapRefreshPolicy.PLAYERS_IDLE_MS, MapRefreshPolicy.playersPeriod(false));
	}

	@Test
	void activitySurvivesTheWindowThenIdles() {
		assertTrue(MapRefreshPolicy.active(10_000, 10_000));
		assertTrue(MapRefreshPolicy.active(10_000 + MapRefreshPolicy.ACTIVITY_WINDOW_MS, 10_000));
		assertFalse(MapRefreshPolicy.active(10_000 + MapRefreshPolicy.ACTIVITY_WINDOW_MS + 1, 10_000));
	}

	@Test
	void aNeverDrawnSurfaceCountsAsActiveSoTheFirstPollDoesNotStall() {
		assertTrue(MapRefreshPolicy.active(50_000, 0));
	}
}
