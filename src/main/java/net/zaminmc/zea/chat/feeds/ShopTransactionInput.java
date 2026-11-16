package net.zaminmc.zea.chat.feeds;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * The one-shot pending shop-quantity state. EarthMC's physical shops
 * collect the transaction quantity through plain chat: clicking a shop
 * sign makes the server print the shop information and end with an
 * explicit prompt — "Enter in chat, how many you wish to BUY." (or
 * SELL) — and the player's next ordinary message is the raw quantity
 * the server consumes as shop input.
 *
 * <p>ZEA's channel routing rewrites exactly that message (a plain
 * "16" typed on the Global tab becomes "/global 16"), which destroys
 * the transaction — the server never receives a bare shop input. This
 * class holds the narrowly scoped fix: the prompt arms a pending state,
 * the <b>next eligible plain message</b> consumes it, and the chat
 * screen lets vanilla send that message unmodified instead of routing
 * it through the selected channel. Eligible means non-blank and not a
 * slash command — a command is the player doing something else, never
 * shop input, and must keep reaching the server as the command it is.
 *
 * <p>The state is deliberately one-shot and short-lived: it is armed
 * only by the explicit quantity prompt (never by the surrounding shop
 * information, and never by "This shop is SELLING items."), it expires
 * so a half-open transaction can never redirect an unrelated message
 * minutes later, and the chat subsystem clears it on disconnect — the
 * pending input belongs to one server connection. Re-arming with a new
 * prompt refreshes the same state, so consecutive shops work without
 * stale state.
 */
public final class ShopTransactionInput {
        /**
         * How long an armed prompt stays eligible. A shop prompt sits in
         * the chat until the player answers; long enough for any reading
         * and typing pause, short enough that a forgotten prompt cannot
         * hijack an unrelated message. The server also expires the
         * transaction itself well inside this window.
         */
        public static final long PROMPT_TIMEOUT_MILLIS = 30_000;

        /**
         * The quantity prompt, normalized: EarthMC sends it with heavy
         * per-character formatting, which flattens away in the captured
         * plain text, but spacing varies between BUY and SELL flows and
         * the line can carry surrounding punctuation. Match the sentence
         * shape, not one exact string.
         */
        private static final Pattern QUANTITY_PROMPT = Pattern.compile(
                        "enter\\s+in\\s+chat\\s*,\\s*how\\s+many\\s+(?:do\\s+)?you\\s+wish\\s+to\\s+(buy|sell)");

        /** Sentinel "disarmed" stamp; a real arm is a wall-clock millis value. */
        private static final long DISARMED = Long.MIN_VALUE;

        private long armedAt = DISARMED;

        /** Arms the bypass — the server just asked for the quantity. */
        public void arm(long nowMillis) {
                armedAt = nowMillis;
        }

        /** Disarms the bypass (disconnect, or the input was consumed). */
        public void clear() {
                armedAt = DISARMED;
        }

        /** Whether a prompt is currently pending and not expired. */
        public boolean isArmed(long nowMillis) {
                return armedAt != DISARMED && nowMillis - armedAt <= PROMPT_TIMEOUT_MILLIS;
        }

        /**
         * Whether {@code message} is the transaction input this state is
         * waiting for — and if so, consumes the state: exactly one
         * eligible message goes through unmodified. Ineligible messages
         * (slash commands, blank text) leave the state armed, so the
         * player's actual quantity still bypasses after they looked up a
         * command mid-transaction.
         */
        public boolean shouldBypass(String message, long nowMillis) {
                if (!isArmed(nowMillis)) {
                        return false;
                }
                if (message == null || message.isBlank() || message.startsWith("/")) {
                        return false;
                }
                clear();
                return true;
        }

        /**
         * Whether the captured plain text is a shop quantity prompt — the
         * only signal that arms the bypass. Tolerant of formatting runs
         * (already flattened by capture), case, and padding; a plain
         * conversation cannot plausibly produce this exact sentence, and
         * anything short of it ("This shop is SELLING items.", the shop
         * information block itself) never arms the state.
         */
        public static boolean isQuantityPrompt(String plainText) {
                if (plainText == null || plainText.isBlank()) {
                        return false;
                }
                String normalized = plainText.toLowerCase(Locale.ROOT)
                                .replaceAll("\\s+", " ")
                                .trim();
                return QUANTITY_PROMPT.matcher(normalized).find();
        }
}
