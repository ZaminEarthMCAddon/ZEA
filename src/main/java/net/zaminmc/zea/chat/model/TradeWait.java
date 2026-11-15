package net.zaminmc.zea.chat.model;

/**
 * The server's trade-cooldown report: {@code » You must wait another
 * 4 minutes and 58 seconds before sending your next message in Trade.}
 *
 * <p>EarthMC enforces one trade message per five minutes and answers a
 * rejected send with this line. It is the only authoritative source for
 * how much of the window is left — a client-side countdown drifts the
 * moment the server restarts the window on a send the client did not
 * know counted — so the trade pacing model syncs exclusively from these
 * reports: the remaining time is read here, the line itself is hidden
 * from the chat, and the message that was rejected queues for the
 * auto-send drain.
 *
 * @param remainingMillis how long the server will keep refusing trade
 *                        messages, from the moment the line arrived
 */
public record TradeWait(long remainingMillis) implements MessagePayload {
	public TradeWait {
		if (remainingMillis <= 0) {
			throw new IllegalArgumentException("remaining time must be positive");
		}
	}
}
