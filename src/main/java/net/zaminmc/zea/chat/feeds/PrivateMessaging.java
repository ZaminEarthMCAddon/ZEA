package net.zaminmc.zea.chat.feeds;

import net.zaminmc.zea.chat.model.PrivateMessage;

import java.util.Optional;

/**
 * Who the player's next {@code /r} goes to: the partner of their most
 * recent private conversation, incoming or outgoing. The PMs tab sends
 * {@code /r <msg>} — the server replies to the last person it saw a PM
 * exchange with — so the client only needs to mirror that rule: an
 * incoming PM sets the sender, the echo of the player's own PM (sent
 * from the tab or typed as {@code /msg}) sets the recipient.
 *
 * <p>Until a PM exists there is nobody to reply to, and the PMs tab
 * refuses the send instead of falling through to plain chat — plain
 * chat on EarthMC goes to the writing channel, which is never what
 * someone typing on a PMs tab means. The refusal is also the fix for
 * the live crash report: the PMs tab no longer touches the vanilla
 * plain-chat path at all.
 *
 * <p>Pure model, no Minecraft types; the subsystem feeds it classified
 * PMs, the send path reads it. Cleared on disconnect.
 */
public final class PrivateMessaging {
	private String partner;

	/** Records one private message, incoming or outgoing, as the new reply target. */
	public void onPrivateMessage(PrivateMessage message) {
		if (message == null) {
			return;
		}
		partner = message.direction() == PrivateMessage.Direction.INCOMING
						? message.sender()
						: message.recipient();
	}

	/** Whom {@code /r} would reach right now. */
	public Optional<String> partner() {
		return Optional.ofNullable(partner);
	}

	/** Forgets the partner; the disconnect reset. */
	public void clear() {
		partner = null;
	}
}
