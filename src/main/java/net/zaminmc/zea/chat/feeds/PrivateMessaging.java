package net.zaminmc.zea.chat.feeds;

import net.zaminmc.zea.chat.model.PrivateMessage;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Who the player's next {@code /r} goes to: the partner of their most
 * recent private conversation, incoming or outgoing. The PMs tab sends
 * {@code /r <msg>} — the server replies to the last person it saw a PM
 * exchange with — so the client only needs to mirror that rule: an
 * incoming PM sets the sender, the echo of the player's own PM (sent
 * from the tab or typed as {@code /msg}) sets the recipient. Either
 * direction can displace the other: a PM arriving mid-conversation
 * redirects the next {@code /r} to its sender, exactly like the
 * server's own reply target.
 *
 * <p>Alongside the reply target the model keeps the session's private
 * contacts — everyone a PM exchange happened with, how many exchanges,
 * when the last one was. The {@code Send to} indicator beside the chat
 * input reads this to show a quiet recent-partners hint while no reply
 * target is known, so the PMs tab is never a dead end on a fresh join.
 * The hint stays quiet by design: it only lists partners of a real
 * back-and-forth ({@value #FREQUENT_EXCHANGES} or more exchanges), not
 * one-off pings, and shows at most {@value #RECENT_LIMIT} names.
 *
 * <p>Until a PM exists there is nobody to reply to, and the PMs tab
 * refuses the send instead of falling through to plain chat — plain
 * chat on EarthMC goes to the writing channel, which is never what
 * someone typing on a PMs tab means. The refusal is also the fix for
 * the live crash report: the PMs tab no longer touches the vanilla
 * plain-chat path at all.
 *
 * <p>Pure model, no Minecraft types; the subsystem feeds it classified
 * PMs, the send path and the indicator read it. Cleared on disconnect —
 * a fresh server is a fresh PM state, and the server's own reply target
 * starts empty too.
 */
public final class PrivateMessaging {
	/** Exchanges a partner needs before the recent hint names them. */
	public static final int FREQUENT_EXCHANGES = 2;
	/** The most partners the recent hint ever names. */
	public static final int RECENT_LIMIT = 3;

	private String partner;

	/**
	 * One private-message partner this session. Immutable; a new exchange
	 * with the same name replaces the entry (never remove — the map never
	 * grows beyond the session's partners).
	 */
	public record Contact(String name, int exchanges, long lastMs) {
	}

	/** Insertion-ordered: ties in recency resolve to the first-seen partner. */
	private final Map<String, Contact> contacts = new LinkedHashMap<>();

	/** Records one private message, incoming or outgoing, as the new reply target. */
	public void onPrivateMessage(PrivateMessage message) {
		onPrivateMessage(message, System.currentTimeMillis());
	}

	/** The same, with the exchange's timestamp supplied — the deterministic form for tests. */
	public void onPrivateMessage(PrivateMessage message, long nowMs) {
		if (message == null) {
			return;
		}
		String name = message.direction() == PrivateMessage.Direction.INCOMING
						? message.sender()
						: message.recipient();
		partner = name;
		recordContact(name, nowMs);
	}

	/** Whom {@code /r} would reach right now. */
	public Optional<String> partner() {
		return Optional.ofNullable(partner);
	}

	/**
	 * The session's partners worth hinting about: a real back-and-forth
	 * ({@value #FREQUENT_EXCHANGES}+ exchanges), most recent first, at
	 * most {@value #RECENT_LIMIT}. One-off pings stay off the hint —
	 * that is what keeps it from becoming noise.
	 */
	public List<Contact> recentContacts() {
		return contacts.values().stream()
						.filter(contact -> contact.exchanges() >= FREQUENT_EXCHANGES)
						.sorted(Comparator.comparingLong(Contact::lastMs).reversed())
						.limit(RECENT_LIMIT)
						.toList();
	}

	/** Forgets the partner and the contacts; the disconnect reset. */
	public void clear() {
		partner = null;
		contacts.clear();
	}

	private void recordContact(String name, long nowMs) {
		Contact previous = contacts.get(name);
		int exchanges = previous == null ? 1 : previous.exchanges() + 1;
		// Re-put keeps insertion order fresh for the recency tie-break:
		// LinkedHashMap treats the re-put as an update, not a re-insert,
		// so the ordering already behaves like lastMs says.
		contacts.put(name, new Contact(name, exchanges, nowMs));
	}
}
