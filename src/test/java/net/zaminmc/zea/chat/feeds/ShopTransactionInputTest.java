package net.zaminmc.zea.chat.feeds;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The shop transaction's one-shot bypass: armed by the explicit quantity
 * prompt, consumed by exactly one eligible plain message, expired and
 * clearable. This is the state behind the release-blocking fix — a
 * pending prompt must make "16" reach the server unmodified while every
 * other message keeps the selected channel's prefix.
 */
class ShopTransactionInputTest {
        private static final long NOW = 1_000_000L;

        @Test
        void recognizesBuyPrompt() {
                assertTrue(ShopTransactionInput.isQuantityPrompt(
                                "Enter in chat, how many you wish to BUY."));
        }

        @Test
        void recognizesSellPrompt() {
                assertTrue(ShopTransactionInput.isQuantityPrompt(
                                "Enter in chat, how many you wish to SELL."));
        }

        @Test
        void recognizesPromptRegardlessOfCaseAndPadding() {
                assertTrue(ShopTransactionInput.isQuantityPrompt(
                                "ENTER IN CHAT, HOW MANY YOU WISH TO BUY."));
                assertTrue(ShopTransactionInput.isQuantityPrompt(
                                "enter in chat , how many you wish to sell ."));
                assertTrue(ShopTransactionInput.isQuantityPrompt(
                                "  Enter in chat,   how many you wish to BUY.  "));
        }

        @Test
        void recognizesPromptFlattenedFromFormattedText() {
                // CapturedMessage text is Component.getString(): the server's
                // per-character formatting runs vanish, leaving plain prose.
                assertTrue(ShopTransactionInput.isQuantityPrompt(
                                "Enter in chat, how many you wish to BUY."));
        }

        @Test
        void shopInformationDoesNotArm() {
                assertFalse(ShopTransactionInput.isQuantityPrompt(
                                "| This shop is SELLING items."));
                assertFalse(ShopTransactionInput.isQuantityPrompt(
                                "| Owner: Ayskow"));
                assertFalse(ShopTransactionInput.isQuantityPrompt(
                                "You can buy 16. Enter all to buy them all."));
        }

        @Test
        void ordinaryConversationDoesNotArm() {
                assertFalse(ShopTransactionInput.isQuantityPrompt(
                                "how many do you wish to trade me for that pickaxe?"));
                assertFalse(ShopTransactionInput.isQuantityPrompt(
                                "I wish to buy some diamonds"));
                assertFalse(ShopTransactionInput.isQuantityPrompt(""));
                assertFalse(ShopTransactionInput.isQuantityPrompt(null));
        }

        @Test
        void armedStateConsumedByOneEligibleMessage() {
                ShopTransactionInput input = new ShopTransactionInput();
                input.arm(NOW);
                assertTrue(input.shouldBypass("16", NOW + 500));
                // One-shot: the next message is ordinary chat again.
                assertFalse(input.shouldBypass("one more message", NOW + 600));
        }

        @Test
        void quantitiesBypassExactlyAsTyped() {
                for (String quantity : new String[] {"16", "1", "all", "ALL"}) {
                        ShopTransactionInput input = new ShopTransactionInput();
                        input.arm(NOW);
                        assertTrue(input.shouldBypass(quantity, NOW + 100),
                                        "quantity " + quantity + " must bypass");
                }
        }

        @Test
        void slashCommandsAreNeverShopInput() {
                ShopTransactionInput input = new ShopTransactionInput();
                input.arm(NOW);
                assertFalse(input.shouldBypass("/help", NOW + 100));
                assertFalse(input.shouldBypass("/local hello", NOW + 200));
                // The state stays armed: the actual quantity still bypasses.
                assertTrue(input.shouldBypass("16", NOW + 300));
        }

        @Test
        void blankMessageDoesNotConsume() {
                ShopTransactionInput input = new ShopTransactionInput();
                input.arm(NOW);
                assertFalse(input.shouldBypass("", NOW + 100));
                assertFalse(input.shouldBypass("   ", NOW + 200));
                assertTrue(input.shouldBypass("16", NOW + 300));
        }

        @Test
        void unarmedStateNeverBypasses() {
                ShopTransactionInput input = new ShopTransactionInput();
                assertFalse(input.shouldBypass("16", NOW));
        }

        @Test
        void promptExpires() {
                ShopTransactionInput input = new ShopTransactionInput();
                input.arm(NOW);
                assertFalse(input.shouldBypass("16", NOW + ShopTransactionInput.PROMPT_TIMEOUT_MILLIS + 1));
        }

        @Test
        void disconnectionClearsPendingState() {
                ShopTransactionInput input = new ShopTransactionInput();
                input.arm(NOW);
                input.clear();
                assertFalse(input.shouldBypass("16", NOW + 100));
                assertFalse(input.isArmed(NOW + 100));
        }

        @Test
        void reArmingRefreshesTheSameState() {
                ShopTransactionInput input = new ShopTransactionInput();
                input.arm(NOW);
                input.arm(NOW + 20_000);
                // The second shop's prompt refreshed the deadline.
                assertTrue(input.shouldBypass(
                                "16", NOW + 20_000 + ShopTransactionInput.PROMPT_TIMEOUT_MILLIS - 1));
        }
}
