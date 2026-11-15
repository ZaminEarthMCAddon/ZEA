package net.zaminmc.zea.map.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FreshnessTest {

	@Test
	void failedRefreshDoesNotResetTheSuccessTimestamp() {
		Freshness fresh = Freshness.NEVER.attempting(1000).succeeded(1000);
		fresh = fresh.attempting(60_000).failed(61_000);
		assertEquals(1000, fresh.lastSuccessMs());
		assertTrue(fresh.failing());
		assertEquals(59, fresh.ageSeconds(60_000));
	}

	@Test
	void successClearsTheFailingFlag() {
		Freshness fresh = new Freshness(0, 0, false, 0).attempting(1).failed(2);
		assertTrue(fresh.failing());
		fresh = fresh.attempting(3).succeeded(4);
		assertFalse(fresh.failing());
		assertEquals(4, fresh.lastSuccessMs());
	}

	@Test
	void refusedRecordsStatusAlongsideFailure() {
		Freshness fresh = Freshness.NEVER.attempting(1).refused(2, 429);
		assertTrue(fresh.failing());
		assertEquals(429, fresh.lastRefusedStatus());
		assertEquals(0, fresh.lastSuccessMs());
	}

	@Test
	void ageTextFormatsHumanUnits() {
		assertEquals("0s", new Freshness(0, 0, false, 0).ageText(50_000));
		assertEquals("12s", Freshness.NEVER.attempting(1000).succeeded(1000).ageText(13_000));
		assertEquals("4m", Freshness.NEVER.attempting(1000).succeeded(1000).ageText(250_000));
		assertEquals("2h", Freshness.NEVER.attempting(1000).succeeded(1000).ageText(7_201_000));
	}

	@Test
	void statusTextDistinguishesLoadingStaleAndCurrent() {
		Freshness never = new Freshness(0, 0, false, 0);
		assertEquals("Loading claims...", never.statusText(10_000, "Claims"));

		Freshness loadingAndFailing = Freshness.NEVER.attempting(1).failed(2);
		assertEquals("Claims unavailable", loadingAndFailing.statusText(10_000, "Claims"));

		Freshness current = Freshness.NEVER.attempting(1000).succeeded(1000);
		assertEquals("Claims 5s ago", current.statusText(6_000, "Claims"));

		Freshness stale = Freshness.NEVER.attempting(1000).succeeded(1000)
				.attempting(300_000).failed(300_100);
		assertEquals("Claims 5m ago (refresh failed)", stale.statusText(301_000, "Claims"));
	}

	@Test
	void successCannotPrecedeItsAttempt() {
		boolean threw = false;
		try {
			new Freshness(1000, 2000, false, 0);
		} catch (IllegalArgumentException expected) {
			threw = true;
		}
		assertTrue(threw);
	}

	@Test
	void attemptingKeepsPreviousOutcome() {
		Freshness failing = Freshness.NEVER.attempting(1).failed(2);
		Freshness midAttempt = failing.attempting(3);
		assertTrue(midAttempt.failing());
		assertEquals(0, midAttempt.lastSuccessMs());
		assertEquals(3, midAttempt.lastAttemptMs());
	}
}
