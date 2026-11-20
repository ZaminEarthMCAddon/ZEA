package net.zaminmc.zea.chat.feeds;

import net.zaminmc.zea.chat.model.PrivateMessage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Whom the PMs tab replies to: the partner of the most recent private
 * exchange, incoming or outgoing — mirroring the server's own /r rule,
 * so a manual {@code /msg} changes the answer target exactly like a
 * received PM does.
 */
class PrivateMessagingTest {
	private final PrivateMessaging messaging = new PrivateMessaging();

	private PrivateMessage incoming(String sender, String content) {
		return new PrivateMessage(sender, "me", PrivateMessage.Direction.INCOMING, content);
	}

	private PrivateMessage outgoing(String recipient, String content) {
		return new PrivateMessage("me", recipient, PrivateMessage.Direction.OUTGOING, content);
	}

	@Test
	void noPartnerBeforeAnyPm() {
		assertTrue(messaging.partner().isEmpty());
	}

	@Test
	void anIncomingPmSetsTheSenderAsPartner() {
		messaging.onPrivateMessage(incoming("KoenigLuis", "hello"));

		assertEquals("KoenigLuis", messaging.partner().orElseThrow());
	}

	@Test
	void theEchoOfThePlayersOwnPmSetsTheRecipient() {
		messaging.onPrivateMessage(incoming("KoenigLuis", "hello"));
		messaging.onPrivateMessage(outgoing("GavriloPrincip", "trade?"));

		assertEquals("GavriloPrincip", messaging.partner().orElseThrow());
	}

	@Test
	void nullAndBlankAreTolerated() {
		messaging.onPrivateMessage(null);
		assertTrue(messaging.partner().isEmpty());
	}

	@Test
	void clearForgetsThePartner() {
		messaging.onPrivateMessage(incoming("KoenigLuis", "hello"));

		messaging.clear();

		assertTrue(messaging.partner().isEmpty());
	}

	@Test
	void clearForgetsTheContacts() {
		messaging.onPrivateMessage(incoming("KoenigLuis", "hello"), 1000);
		messaging.onPrivateMessage(incoming("KoenigLuis", "again"), 2000);

		messaging.clear();

		assertTrue(messaging.recentContacts().isEmpty());
	}

	// ------------------------------------------------------------------
	// The recent-partners hint: session contacts with counts and recency
	// ------------------------------------------------------------------

	@Test
	void oneExchangeIsNotAFrequentPartner() {
		messaging.onPrivateMessage(incoming("KoenigLuis", "hello"), 1000);

		assertTrue(messaging.recentContacts().isEmpty(),
						"a one-off ping never reaches the hint");
	}

	@Test
	void twoExchangesMakeAFrequentPartner() {
		messaging.onPrivateMessage(incoming("KoenigLuis", "hello"), 1000);
		messaging.onPrivateMessage(outgoing("KoenigLuis", "trade?"), 2000);

		assertEquals(1, messaging.recentContacts().size());
		assertEquals("KoenigLuis", messaging.recentContacts().get(0).name());
		assertEquals(2, messaging.recentContacts().get(0).exchanges());
		assertEquals(2000, messaging.recentContacts().get(0).lastMs());
	}

	@Test
	void countsAccumulateAcrossBothDirections() {
		messaging.onPrivateMessage(incoming("KoenigLuis", "a"), 1000);
		messaging.onPrivateMessage(outgoing("KoenigLuis", "b"), 2000);
		messaging.onPrivateMessage(incoming("KoenigLuis", "c"), 3000);

		assertEquals(3, messaging.recentContacts().get(0).exchanges(),
						"one partner, one count — direction does not split it");
	}

	@Test
	void theMostRecentlyHeardFromPartnerComesFirst() {
		messaging.onPrivateMessage(incoming("KoenigLuis", "a"), 1000);
		messaging.onPrivateMessage(incoming("KoenigLuis", "b"), 2000);
		messaging.onPrivateMessage(incoming("GavriloPrincip", "a"), 3000);
		messaging.onPrivateMessage(incoming("GavriloPrincip", "b"), 4000);

		assertEquals("GavriloPrincip", messaging.recentContacts().get(0).name());
		assertEquals("KoenigLuis", messaging.recentContacts().get(1).name());
	}

	@Test
	void theHintNeverNamesMoreThanThreePartners() {
		for (int round = 0; round < 2; round++) {
			messaging.onPrivateMessage(incoming("Alpha", "hi"), round);
			messaging.onPrivateMessage(incoming("Bravo", "hi"), round);
			messaging.onPrivateMessage(incoming("Charlie", "hi"), round);
			messaging.onPrivateMessage(incoming("Delta", "hi"), round);
		}

		assertEquals(3, messaging.recentContacts().size(),
						"the hint stays quiet: at most three names, ever");
	}
}
