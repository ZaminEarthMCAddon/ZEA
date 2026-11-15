package net.zaminmc.zea.chat.feeds;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The server-synced trade pacing. The old model forced a client-side
 * five-minute window on every send; the dev.36 live request replaced it
 * with the server's own numbers: nothing is held speculatively, a send
 * goes out and is remembered, and only the server's rejection line —
 * "You must wait another N seconds before sending your next message in
 * Trade." — starts a window (and queues the message it refused).
 */
class TradeGateTest {
        private static final long SETTLE = 1_000L;

        private final TradeGate gate = new TradeGate(SETTLE);

        @Test
        void tradeMessagesAreRecognized() {
                assertTrue(TradeGate.isTradeMessage("tradechat selling dirt"));
                assertTrue(TradeGate.isTradeMessage("TRADECHAT selling dirt"));
                assertFalse(TradeGate.isTradeMessage("tradechat"));
                assertFalse(TradeGate.isTradeMessage("tradechat "));
                assertFalse(TradeGate.isTradeMessage("global hello"));
                assertFalse(TradeGate.isTradeMessage(null));
        }

        @Test
        void aFirstSendGoesStraightOutAndIsRemembered() {
                assertEquals(TradeGate.Outcome.SEND_NOW, gate.offer("tradechat hello", 0L, true));

                assertFalse(gate.snapshot(0L).active(), "no window is known before the server speaks");
                assertFalse(gate.isCoolingDown(0L), "no window is known before the server speaks");
        }

        @Test
        void theServersWaitLineStartsTheWindowAndQueuesTheRefusedMessage() {
                gate.offer("tradechat hello", 0L, true);

                gate.onServerWait(1_000L, 298_000L);

                assertTrue(gate.isCoolingDown(2_000L));
                assertEquals(1, gate.queuedCount());
                assertEquals(List.of("tradechat hello"), gate.queuedSnapshot());
                assertEquals(297_000L, gate.remainingMillis(2_000L));
        }

        @Test
        void aWaitLineWithoutAnUnresolvedSendStillSyncsTheWindow() {
                // A rejection the client did not send — a macro, a pre-gate message.
                gate.onServerWait(0L, 60_000L);

                assertTrue(gate.isCoolingDown(1L));
                assertEquals(0, gate.queuedCount());
        }

        @Test
        void whileAWindowRunsNewSendsQueueOrAreRefused() {
                gate.onServerWait(0L, 60_000L);

                assertEquals(TradeGate.Outcome.QUEUED, gate.offer("tradechat two", 1L, true));
                assertEquals(TradeGate.Outcome.BLOCKED, gate.offer("tradechat three", 2L, false));
                assertEquals(List.of("tradechat two"), gate.queuedSnapshot());
        }

        @Test
        void theQueueRefusesMoreThanTheMaximum() {
                gate.onServerWait(0L, 60_000L);

                for (int i = 0; i < TradeGate.MAX_QUEUED; i++) {
                        assertEquals(TradeGate.Outcome.QUEUED, gate.offer("tradechat m" + i, 1L, true));
                }
                assertEquals(TradeGate.Outcome.FULL, gate.offer("tradechat overflow", 2L, true));
                assertEquals(TradeGate.MAX_QUEUED, gate.queuedCount());
        }

        @Test
        void theDrainSendsOnlyWhenTheServerSaysTheWindowIsOver() {
                gate.onServerWait(0L, 60_000L);
                gate.offer("tradechat two", 1L, true);

                assertNull(gate.pollDue(59_999L));
                assertEquals("tradechat two", gate.pollDue(60_000L));
                assertEquals(0, gate.queuedCount());
        }

        @Test
        void queuedMessagesCanBeDeleted() {
                gate.onServerWait(0L, 60_000L);
                gate.offer("tradechat one", 1L, true);
                gate.offer("tradechat two", 2L, true);
                gate.offer("tradechat three", 3L, true);

                assertTrue(gate.removeQueued(1));
                assertEquals(List.of("tradechat one", "tradechat three"), gate.queuedSnapshot());
                assertTrue(gate.removeQueued(0));
                assertEquals(List.of("tradechat three"), gate.queuedSnapshot());
                assertTrue(gate.removeQueued(0));
                assertEquals(0, gate.queuedCount());
        }

        @Test
        void deletingOutOfRangeIndexesRefusesInsteadOfThrowing() {
                gate.onServerWait(0L, 60_000L);
                gate.offer("tradechat one", 1L, true);

                assertFalse(gate.removeQueued(-1));
                assertFalse(gate.removeQueued(1));
                assertFalse(gate.removeQueued(99));
                assertEquals(1, gate.queuedCount());
        }

        @Test
        void thePlayersOwnEchoResolvesTheOldestSendAsAccepted() {
                gate.offer("tradechat accepted", 0L, true);

                gate.onOwnTradeAccepted();

                // A later rejection has nothing unresolved to queue: the accepted
                // message must not come back as a duplicate.
                gate.onServerWait(1_000L, 60_000L);
                assertEquals(0, gate.queuedCount());
        }

        @Test
        void unsettledSendsArePresumedAcceptedAfterTheSettleWindow() {
                gate.offer("tradechat accepted", 0L, true);

                gate.settleUnresolved(SETTLE - 1);
                gate.onServerWait(SETTLE - 1, 60_000L);
                assertEquals(1, gate.queuedCount(), "the send is still unresolved");

                // New gate, same scenario past the settle window.
                TradeGate settling = new TradeGate(SETTLE);
                settling.offer("tradechat accepted", 0L, true);
                settling.settleUnresolved(SETTLE);
                settling.onServerWait(SETTLE, 60_000L);
                assertEquals(0, settling.queuedCount(), "a settled send is accepted, never re-queued");
        }

        @Test
        void aRejectedAutoSendReQueuesItself() {
                // The drain's send goes through the same choke point, so its
                // rejection re-queues it with the fresh server time.
                gate.onServerWait(0L, 60_000L);
                gate.offer("tradechat held", 1L, true);
                String due = gate.pollDue(60_000L);
                assertEquals("tradechat held", due);

                // The drain sends it; the server says two more seconds.
                gate.offer(due, 61_000L, true);
                gate.onServerWait(61_500L, 2_000L);

                assertTrue(gate.isCoolingDown(62_000L));
                assertEquals(List.of("tradechat held"), gate.queuedSnapshot());
                assertEquals("tradechat held", gate.pollDue(63_500L));
        }

        @Test
        void theNewestServerReportWins() {
                gate.onServerWait(0L, 300_000L);
                gate.onServerWait(1_000L, 4_000L);

                assertEquals(3_000L, gate.remainingMillis(2_000L));
        }

        @Test
        void clearForgetsEverything() {
                gate.onServerWait(0L, 60_000L);
                gate.offer("tradechat held", 1L, true);
                gate.offer("tradechat out", 2L, true);

                gate.clear();

                assertFalse(gate.isCoolingDown(3L));
                assertEquals(0, gate.queuedCount());
                assertEquals(TradeGate.Outcome.SEND_NOW, gate.offer("tradechat fresh", 4L, true));
        }

        @Test
        void remainingTimeFormatsAsMinutesAndSeconds() {
                assertEquals("4:59", TradeGate.formatRemaining(299_999L));
                assertEquals("0:12", TradeGate.formatRemaining(12_000L));
                assertEquals("0:00", TradeGate.formatRemaining(0L));
                assertEquals("0:00", TradeGate.formatRemaining(-5_000L));
        }
}
