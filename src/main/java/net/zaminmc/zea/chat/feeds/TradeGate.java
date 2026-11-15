package net.zaminmc.zea.chat.feeds;

import java.util.ArrayDeque;
import java.util.List;

/**
 * The trade channel's send pacing, synced from the server. EarthMC
 * allows one trade message per player every five minutes; a send inside
 * the window is rejected with a red "You must wait another N seconds
 * before sending your next message in Trade." line. That line is the
 * only clock the client trusts: the window here starts exclusively
 * from the server's reports ({@link #onServerWait(long, long)}), never
 * from a client-side guess about when a send "must have" restarted it.
 *
 * <p>So a trade send is never held speculatively. While no window is
 * known, every trade message goes straight out — the server is the
 * referee — and the send is remembered as <b>unresolved</b>. If the
 * server answers with a wait line, the window syncs to the reported
 * remaining time and the oldest unresolved message moves into the
 * queue: the words the player typed are never lost. If the message
 * echoes back in the trade channel instead, it was accepted and the
 * unresolved entry is dropped ({@link #onOwnTradeAccepted()}); entries
 * the server has had long enough to answer without a word are settled
 * the same way ({@link #settleUnresolved(long)}), so a nickname the
 * echo check cannot match can never leave a ghost that later re-queues
 * an already-delivered message.
 *
 * <p>While a window <b>is</b> known, new trade messages queue (with
 * auto-send on) or are refused with the server-reported wait (off) —
 * exactly the old behavior, but timed by the server's numbers. The
 * drain ({@link #pollDue(long)}) sends the oldest queued message once
 * the window expires, through the same choke point every trade send
 * passes, so its outcome is tracked like any other: accepted, or
 * rejected with a fresh wait line that re-syncs the window and
 * re-queues it. The loop converges on the server's own clock.
 *
 * <p>The queue is inspectable and editable — the player can see what
 * waits and delete any of it ({@link #queuedSnapshot()},
 * {@link #removeQueued(int)}) — and everything clears on disconnect.
 *
 * <p>Pure model, no Minecraft types and no clock: time arrives as an
 * argument so tests run in an instant.
 */
public final class TradeGate {
	/** How many messages may wait for the window at once. */
	public static final int MAX_QUEUED = 5;

	/**
	 * How long a sent trade message stays unresolved before it is
	 * presumed accepted — the safety net under the echo check.
	 */
	public static final long DEFAULT_SETTLE_MILLIS = 15_000L;

	/** The command every trade message is sent with, without the slash. */
	private static final String TRADE_COMMAND = "tradechat ";

	/** What {@link #offer} decided for one send attempt. */
	public enum Outcome {
		/** No window is known — send now; the send is tracked until the server answers. */
		SEND_NOW,
		/** A window is running — the message was queued for the auto-send drain. */
		QUEUED,
		/** A window is running and auto-send is off — the send is refused. */
		BLOCKED,
		/** A window is running and the queue is full — the send is refused. */
		FULL
	}

	/**
	 * What the status line shows: whether a server-reported window is
	 * running, how much of it is left, and how many messages wait.
	 *
	 * @param coolingDown     whether the server's window is still running
	 * @param remainingMillis milliseconds left in the window, 0 when none
	 * @param queued          messages waiting for the auto-send drain
	 */
	public record Snapshot(boolean coolingDown, long remainingMillis, int queued) {
		/** Whether the status line has anything to show at all. */
		public boolean active() {
			return coolingDown || queued > 0;
		}
	}

	/** One trade message sent while no window was known, outcome not yet learned. */
	private record Pending(String commandLine, long sentAtMillis) {
	}

	private final long settleMillis;
	/** When the server last said it would stop refusing; {@code Long#MIN_VALUE} means no report this session. */
	private long windowExpiresAtMillis = Long.MIN_VALUE;
	private final ArrayDeque<String> queuedCommandLines = new ArrayDeque<>();
	private final ArrayDeque<Pending> unresolved = new ArrayDeque<>();

	/**
	 * @param settleMillis how long a sent message may stay unresolved;
	 *                     production uses {@link #DEFAULT_SETTLE_MILLIS},
	 *                     tests shrink it
	 */
	public TradeGate(long settleMillis) {
		if (settleMillis <= 0) {
			throw new IllegalArgumentException("settle window must be positive");
		}
		this.settleMillis = settleMillis;
	}

	/** The production gate: server-synced window, 15-second settle. */
	public TradeGate() {
		this(DEFAULT_SETTLE_MILLIS);
	}

	/**
	 * Whether a command line is a trade message: the trade command with a
	 * non-blank message after it. A bare {@code /tradechat} is a channel
	 * switch, not a message — the pacing does not apply to it, so it
	 * passes the gate untouched. EarthMC command names are
	 * case-insensitive, so is this check.
	 */
	public static boolean isTradeMessage(String commandLine) {
		if (commandLine == null || commandLine.length() <= TRADE_COMMAND.length()) {
			return false;
		}
		if (!commandLine.regionMatches(true, 0, TRADE_COMMAND, 0, TRADE_COMMAND.length())) {
			return false;
		}
		return !commandLine.substring(TRADE_COMMAND.length()).isBlank();
	}

	/** Whether a server-reported window is still running at {@code now}. */
	public boolean isCoolingDown(long now) {
		return windowExpiresAtMillis != Long.MIN_VALUE && now < windowExpiresAtMillis;
	}

	/** Milliseconds left in the window, 0 when none is running. */
	public long remainingMillis(long now) {
		return isCoolingDown(now) ? windowExpiresAtMillis - now : 0L;
	}

	/**
	 * Offers one trade send to the gate. With no known window the send
	 * goes out and is remembered as unresolved — the caller must send it
	 * and let the server's answer (echo or wait line) resolve it. With a
	 * window running the message is queued for the drain (auto-send on,
	 * up to {@link #MAX_QUEUED}) or refused with the reason as the
	 * outcome.
	 */
	public Outcome offer(String commandLine, long now, boolean autoQueue) {
		if (!isCoolingDown(now)) {
			unresolved.addLast(new Pending(commandLine, now));
			return Outcome.SEND_NOW;
		}
		if (!autoQueue) {
			return Outcome.BLOCKED;
		}
		if (queuedCommandLines.size() >= MAX_QUEUED) {
			return Outcome.FULL;
		}
		queuedCommandLines.addLast(commandLine);
		return Outcome.QUEUED;
	}

	/**
	 * The server refused a trade send and reported the wait. The window
	 * syncs to the reported remaining time — the newest report is the
	 * truth, whenever its rejection happened — and the oldest unresolved
	 * send moves into the queue: that is the message the server just
	 * refused, and its words are not lost. With nothing unresolved the
	 * window still syncs (a rejection the client did not send, a macro,
	 * a pre-gate message); there is just nothing to hold.
	 */
	public void onServerWait(long now, long waitMillis) {
		if (waitMillis <= 0) {
			return;
		}
		windowExpiresAtMillis = now + waitMillis;
		Pending oldest = unresolved.pollFirst();
		if (oldest != null && queuedCommandLines.size() < MAX_QUEUED) {
			queuedCommandLines.addLast(oldest.commandLine());
		}
	}

	/**
	 * The player's own trade message echoed back — the oldest unresolved
	 * send was accepted. Sends resolve in order (the server answers in
	 * order), so the head is the one that made it.
	 */
	public void onOwnTradeAccepted() {
		unresolved.pollFirst();
	}

	/**
	 * Drops unresolved sends the server has had long enough to answer.
	 * A wait line arrives within moments of the rejection; anything
	 * still unresolved after the settle window was accepted, whatever
	 * the echo check could not see (a nickname, a silent relay).
	 */
	public void settleUnresolved(long now) {
		unresolved.removeIf(pending -> now - pending.sentAtMillis() >= settleMillis);
	}

	/** How many messages wait for the auto-send drain. */
	public int queuedCount() {
		return queuedCommandLines.size();
	}

	/** What waits, oldest first — the queue panel's rows. */
	public List<String> queuedSnapshot() {
		return List.copyOf(queuedCommandLines);
	}

	/**
	 * Deletes the queued message at {@code index}, as the queue panel's
	 * delete buttons ask. Out-of-range indexes are refused, not thrown:
	 * the panel reads a snapshot and the queue can change under it.
	 *
	 * @return whether a message was removed
	 */
	public boolean removeQueued(int index) {
		if (index < 0 || index >= queuedCommandLines.size()) {
			return false;
		}
		int walked = 0;
		for (var it = queuedCommandLines.iterator(); it.hasNext(); ) {
			it.next();
			if (walked++ == index) {
				it.remove();
				return true;
			}
		}
		return false;
	}

	/**
	 * Pops the next message to send now, or {@code null} when the window
	 * is still running or nothing waits. The caller sends the returned
	 * line through the same choke point, which tracks it like any other
	 * send — the drain cannot lose a message either.
	 */
	public String pollDue(long now) {
		if (isCoolingDown(now)) {
			return null;
		}
		return queuedCommandLines.pollFirst();
	}

	/** The status line's state at {@code now}. */
	public Snapshot snapshot(long now) {
		return new Snapshot(isCoolingDown(now), remainingMillis(now), queuedCommandLines.size());
	}

	/** Forgets the window, the queue and every unresolved send; the disconnect reset. */
	public void clear() {
		windowExpiresAtMillis = Long.MIN_VALUE;
		queuedCommandLines.clear();
		unresolved.clear();
	}

	/**
	 * Formats a remaining time as {@code m:ss} — "4:59", "0:12" — the
	 * shape the status line and the toasts show. Values past the window's
	 * end clamp to "0:00".
	 */
	public static String formatRemaining(long remainingMillis) {
		long seconds = Math.max(0L, remainingMillis) / 1000L;
		return String.format(java.util.Locale.ROOT, "%d:%02d", seconds / 60L, seconds % 60L);
	}
}
