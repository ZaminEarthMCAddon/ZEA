package net.zaminmc.zea.chat.feeds;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TradeGateTest {
        /** A one-second cooldown: fast to walk through, same semantics. */
        private static final long COOLDOWN = 1_000L;

        private final TradeGate gate = new TradeGate(COOLDOWN);

        @Test
        void theFirstOfferOutsideACooldownSendsNow() {
                assertEquals(TradeGate.Outcome.SEND_NOW, gate.offer("tradechat hello", 100L, true));
                assertEquals(TradeGate.Outcome.SEND_NOW, gate.offer("tradechat hello", 100L, false));
                assertFalse(gate.isCoolingDown(100L));
        }

        @Test
        void aSendStartsTheCountdown() {
                gate.onSent(1_000L);

                assertTrue(gate.isCoolingDown(1_500L));
                assertEquals(COOLDOWN - 500L, gate.remainingMillis(1_500L));
                assertFalse(gate.isCoolingDown(1_000L + COOLDOWN));
                assertEquals(0L, gate.remainingMillis(1_000L + COOLDOWN));
        }

        @Test
        void offersInsideTheCooldownQueueWhenAutoSendIsOn() {
                gate.onSent(0L);

                assertEquals(TradeGate.Outcome.QUEUED, gate.offer("tradechat one", 100L, true));
                assertEquals(TradeGate.Outcome.QUEUED, gate.offer("tradechat two", 200L, true));
                assertEquals(2, gate.queuedCount());
                assertEquals(TradeGate.Outcome.QUEUED, gate.offer("tradechat three", 300L, true));
                assertEquals(TradeGate.Outcome.QUEUED, gate.offer("tradechat four", 400L, true));
                assertEquals(TradeGate.Outcome.QUEUED, gate.offer("tradechat five", 500L, true));
                assertEquals(5, gate.queuedCount());
                assertEquals(TradeGate.Outcome.FULL, gate.offer("tradechat six", 600L, true));
                assertEquals(5, gate.queuedCount(), "the refused message is not kept");
        }

        @Test
        void offersInsideTheCooldownAreBlockedWhenAutoSendIsOff() {
                gate.onSent(0L);

                assertEquals(TradeGate.Outcome.BLOCKED, gate.offer("tradechat hello", 100L, false));
                assertEquals(0, gate.queuedCount());
        }

        @Test
        void theQueueDrainsOneMessagePerWindow() {
                gate.onSent(0L);
                gate.offer("tradechat one", 100L, true);
                gate.offer("tradechat two", 200L, true);

                assertNull(gate.pollDue(500L), "the window is still running");

                // The window expires; the oldest message goes out.
                assertEquals("tradechat one", gate.pollDue(COOLDOWN + 1L));
                assertEquals(1, gate.queuedCount());
                // The send itself restarts the window (reported through onSent).
                gate.onSent(COOLDOWN + 1L);
                assertNull(gate.pollDue(COOLDOWN + 2L));
                assertEquals("tradechat two", gate.pollDue(2 * COOLDOWN + 1L));
                assertNull(gate.pollDue(3 * COOLDOWN), "nothing waits anymore");
        }

        @Test
        void polledMessagesKeepTheirExactCommandLine() {
                gate.onSent(0L);
                gate.offer("tradechat selling diamonds, 5g each  ", 100L, true);

                assertEquals("tradechat selling diamonds, 5g each  ",
                                gate.pollDue(COOLDOWN + 1L));
        }

        @Test
        void clearResetsTheWindowAndTheQueue() {
                gate.onSent(0L);
                gate.offer("tradechat one", 100L, true);
                gate.offer("tradechat two", 200L, true);

                gate.clear();

                assertEquals(0, gate.queuedCount());
                assertFalse(gate.isCoolingDown(300L));
                assertEquals(TradeGate.Outcome.SEND_NOW, gate.offer("tradechat hello", 300L, true));
        }

        @Test
        void snapshotsReportWhatTheStatusLineShows() {
                TradeGate.Snapshot idle = gate.snapshot(0L);
                assertFalse(idle.active());
                assertFalse(idle.coolingDown());

                gate.onSent(0L);
                TradeGate.Snapshot cooling = gate.snapshot(500L);
                assertTrue(cooling.active());
                assertTrue(cooling.coolingDown());
                assertEquals(500L, cooling.remainingMillis());
                assertEquals(0, cooling.queued());

                gate.offer("tradechat one", 100L, true);
                TradeGate.Snapshot waiting = gate.snapshot(500L);
                assertTrue(waiting.active());
                assertEquals(1, waiting.queued());

                // Auto-send off, cooldown over, messages parked: still worth showing.
                TradeGate parked = new TradeGate(COOLDOWN);
                parked.onSent(0L);
                parked.offer("tradechat one", 100L, true);
                TradeGate.Snapshot afterWindow = parked.snapshot(2 * COOLDOWN);
                assertFalse(afterWindow.coolingDown());
                assertEquals(1, afterWindow.queued());
                assertTrue(afterWindow.active());
        }

        @Test
        void aBareTradeChatCommandIsNotAMessage() {
                assertFalse(TradeGate.isTradeMessage("tradechat"));
                assertFalse(TradeGate.isTradeMessage("tradechat "));
                assertFalse(TradeGate.isTradeMessage("tradechat   "));
                assertFalse(TradeGate.isTradeMessage("g hello"));
                assertFalse(TradeGate.isTradeMessage("tradechathello"));
                assertFalse(TradeGate.isTradeMessage(""));
                assertFalse(TradeGate.isTradeMessage(null));

                assertTrue(TradeGate.isTradeMessage("tradechat hello"));
                assertTrue(TradeGate.isTradeMessage("TradeChat selling diamonds"));
                assertTrue(TradeGate.isTradeMessage("TRADECHAT x"));
        }

        @Test
        void remainingTimeFormatsAsMinutesAndSeconds() {
                assertEquals("5:00", TradeGate.formatRemaining(300_000L));
                assertEquals("4:59", TradeGate.formatRemaining(299_999L));
                assertEquals("4:12", TradeGate.formatRemaining(252_000L));
                assertEquals("1:05", TradeGate.formatRemaining(65_000L));
                assertEquals("0:12", TradeGate.formatRemaining(12_345L));
                assertEquals("0:00", TradeGate.formatRemaining(0L));
                assertEquals("0:00", TradeGate.formatRemaining(-5_000L));
        }

        @Test
        void theCooldownMustBePositive() {
                assertThrows(IllegalArgumentException.class, () -> new TradeGate(0L));
                assertThrows(IllegalArgumentException.class, () -> new TradeGate(-1L));
        }
}
