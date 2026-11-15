package net.zaminmc.zea.chat.feeds;

import java.util.ArrayDeque;
import java.util.Locale;

/**
 * The trade channel's send pacing. EarthMC allows one trade message per
 * player every five minutes; a second message inside the window is
 * rejected by the server and simply never appears. This model makes the
 * client respect that rule instead: every outgoing trade send passes
 * {@link #offer(String, long, boolean)} first — outside the cooldown it
 * goes out and the window starts, inside it the message is either queued
 * for the auto-send drain (up to {@link #MAX_QUEUED}) or refused outright
 * when auto-send is off, with the reason reported to the caller so it can
 * explain itself.
 *
 * <p>Every path funnels through the same choke point — the player's own
 * {@code /tradechat}, ZEA's channel routing, the up-arrow recall and the
 * auto-send drain all reach the vanilla
 * {@code ClientPacketListener.sendCommand} — so the gate is enforced
 * there and the window restarts on exactly the sends that really went
 * out. The drain itself polls {@link #pollDue(long)} from the subsystem
 * tick; a polled message is sent through the same choke point, so its
 * allowance re-arms the window like any other send.
 *
 * <p>Pure model, no Minecraft types and no clock: time arrives as an
 * argument so tests run in an instant. The queue holds full command
 * lines ("tradechat hello world") and survives until disconnect, so
 * turning auto-send off mid-queue parks the messages rather than
 * dropping the player's words.
 */
public final class TradeGate {
        /** EarthMC's rule: one trade message every five minutes. */
        public static final long COOLDOWN_MILLIS = 300_000L;

        /** How many messages may wait for the cooldown at once. */
        public static final int MAX_QUEUED = 5;

        /** The command every trade message is sent with, without the slash. */
        private static final String TRADE_COMMAND = "tradechat ";

        /** What {@link #offer} decided for one send attempt. */
        public enum Outcome {
                /** No cooldown is running — send now, and the window starts. */
                SEND_NOW,
                /** The message was queued for the auto-send drain. */
                QUEUED,
                /** The cooldown is running and auto-send is off — the send is refused. */
                BLOCKED,
                /** The cooldown is running and the queue is full — the send is refused. */
                FULL
        }

        /**
         * What the status line shows: whether a cooldown is running, how much
         * of it is left, and how many messages are waiting. When no cooldown
         * runs but messages wait (auto-send off), the line reads "ready".
         *
         * @param coolingDown    whether the five-minute window is running
         * @param remainingMillis milliseconds left in the window, 0 when none
         * @param queued         messages waiting for the auto-send drain
         */
        public record Snapshot(boolean coolingDown, long remainingMillis, int queued) {
                /** Whether the status line has anything to show at all. */
                public boolean active() {
                        return coolingDown || queued > 0;
                }
        }

        private final long cooldownMillis;
        /** When the window started; {@link Long#MIN_VALUE} means no window has run since clear. */
        private long windowStartedAtMillis = Long.MIN_VALUE;
        private final ArrayDeque<String> queuedCommandLines = new ArrayDeque<>();

        /**
         * @param cooldownMillis the cooldown window's length; production uses
         *                       {@link #COOLDOWN_MILLIS}, tests shrink it
         */
        public TradeGate(long cooldownMillis) {
                if (cooldownMillis <= 0) {
                        throw new IllegalArgumentException("cooldown must be positive");
                }
                this.cooldownMillis = cooldownMillis;
        }

        /**
         * Whether a command line is a trade message: the trade command with a
         * non-blank message after it. A bare {@code /tradechat} is a channel
         * switch, not a message — the five-minute rule does not apply to it,
         * so it passes the gate untouched. EarthMC command names are
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

        /** Whether the five-minute window is still running at {@code now}. */
        public boolean isCoolingDown(long now) {
                return windowStartedAtMillis != Long.MIN_VALUE
                                && now - windowStartedAtMillis < cooldownMillis;
        }

        /** Milliseconds left in the window, 0 when none is running. */
        public long remainingMillis(long now) {
                return isCoolingDown(now) ? cooldownMillis - (now - windowStartedAtMillis) : 0L;
        }

        /**
         * Offers one trade send to the gate. {@code SEND_NOW} means the caller
         * must send and then report success with {@link #onSent(long)} — the
         * window starts from the report, so a send that never happens does not
         * burn the allowance.
         */
        public Outcome offer(String commandLine, long now, boolean autoQueue) {
                if (!isCoolingDown(now)) {
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

        /** Reports that a trade send really went out; the window starts now. */
        public void onSent(long now) {
                windowStartedAtMillis = now;
        }

        /** How many messages wait for the auto-send drain. */
        public int queuedCount() {
                return queuedCommandLines.size();
        }

        /**
         * Pops the next message to send now, or {@code null} when the window
         * is still running or nothing waits. The caller sends the returned
         * line through the same choke point, whose allowance re-arms the
         * window.
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

        /** Forgets the window and every queued message; the disconnect reset. */
        public void clear() {
                windowStartedAtMillis = Long.MIN_VALUE;
                queuedCommandLines.clear();
        }

        /**
         * Formats a remaining time as {@code m:ss} — "4:59", "0:12" — the
         * shape the status line and the toasts show. Values past the window's
         * end clamp to "0:00".
         */
        public static String formatRemaining(long remainingMillis) {
                long seconds = Math.max(0L, remainingMillis) / 1000L;
                return String.format(Locale.ROOT, "%d:%02d", seconds / 60L, seconds % 60L);
        }
}
