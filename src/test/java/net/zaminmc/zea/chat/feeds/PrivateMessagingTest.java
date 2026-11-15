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
}
