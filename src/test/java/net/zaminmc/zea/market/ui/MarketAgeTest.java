package net.zaminmc.zea.market.ui;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarketAgeTest {
	private static final Instant NOW = Instant.ofEpochMilli(10_000_000_000L);

	@Test
	void freshObservationsReadAsJustNow() {
		assertEquals("just now", MarketAge.format(NOW.minusSeconds(30), NOW));
	}

	@Test
	void minutesAndHoursAndDays() {
		assertEquals("12 minutes ago", MarketAge.format(NOW.minusSeconds(12 * 60), NOW));
		assertEquals("1 minute ago", MarketAge.format(NOW.minusSeconds(60), NOW));
		assertEquals("3 hours ago", MarketAge.format(NOW.minusSeconds(3 * 3600), NOW));
		assertEquals("5 days ago", MarketAge.format(NOW.minusSeconds(5L * 24 * 3600), NOW));
	}

	@Test
	void clockSkewNeverProducesANegativeAge() {
		assertEquals("just now", MarketAge.format(NOW.plusSeconds(60), NOW));
	}

	@Test
	void theSincePhraseSpeaksOfObservationsNotOfFreshness() {
		assertEquals("12 minutes old", MarketAge.sincePhrase(NOW.minusSeconds(12 * 60), NOW));
		assertEquals("just now", MarketAge.sincePhrase(NOW, NOW));
	}

	@Test
	void olderThanADayIsOnlyAHint() {
		assertTrue(MarketAge.isOlderThanADay(NOW.minusSeconds(2L * 24 * 3600), NOW));
		assertFalse(MarketAge.isOlderThanADay(NOW.minusSeconds(3600), NOW));
	}
}
