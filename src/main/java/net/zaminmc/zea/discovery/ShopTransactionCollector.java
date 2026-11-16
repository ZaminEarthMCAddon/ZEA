package net.zaminmc.zea.discovery;

import java.util.ArrayList;
import java.util.List;

/**
 * Collects a multi-line shop-information response into one parseable
 * block. The server spreads the block over several chat components (and
 * lines within them); the collector buffers lines between the sign click
 * that armed it and the completion signal — the quantity prompt that
 * always closes a real shop response — with a timeout so a vanished
 * response cannot buffer unrelated chat forever.
 *
 * <p>Association discipline: only a sign click arms the collector, lines
 * that match no shop-field shape are dropped (they cannot corrupt the
 * record), a new sign click restarts the buffer, and the collector is
 * never armed by chat content alone.
 */
public final class ShopTransactionCollector {
        /** How long a collection may run before the partial block is parsed as-is. */
        public static final long COLLECTION_TIMEOUT_MS = 15_000L;

        private final List<String> buffer = new ArrayList<>();
        private String signPos;
        private long startedAtMs;
        private boolean armed;
        private boolean promptSeen;
        private String promptAction;

        /** A shop sign was just clicked: the next shop response belongs to it. */
        public void begin(String signPos, long nowMs) {
                this.signPos = signPos;
                this.startedAtMs = nowMs;
                this.armed = true;
                this.promptSeen = false;
                this.promptAction = null;
                buffer.clear();
        }

        /** Whether the collector is currently gathering a response. */
        public boolean isArmed() {
                return armed;
        }

        /** The sign position the armed collection belongs to. */
        public String signPos() {
                return signPos;
        }

        /**
         * Feeds one captured chat text. Returns true when the block is
         * complete — the collector saw the quantity prompt that closes a
         * shop response — and the caller should parse and disarm.
         */
        public boolean feed(String text, long nowMs) {
                if (!armed) {
                        return false;
                }
                if (nowMs - startedAtMs > COLLECTION_TIMEOUT_MS) {
                        // A response that never finished: parse what arrived.
                        return true;
                }
                if (text == null || text.isBlank()) {
                        return false;
                }
                for (String line : text.split("\n")) {
                        String trimmed = line.trim();
                        if (trimmed.isEmpty()) {
                                continue;
                        }
                        if (!isShopBlockLine(trimmed)) {
                                // Unrelated chat during the collection window is
                                // dropped: it can never corrupt the record.
                                continue;
                        }
                        if (ShopParser.isShopInfoStart(trimmed)) {
                                buffer.clear();
                        }
                        buffer.add(trimmed);
                        if (ShopParser.isQuantityPrompt(trimmed)) {
                                promptSeen = true;
                                promptAction = ShopParser.promptAction(trimmed);
                                return true;
                        }
                }
                return false;
        }

        /**
         * Whether a line belongs to a shop block: the bordered table lines,
         * the shop-information header, or the transaction prompt lines that
         * follow the table. Anything else is ordinary chat.
         */
        private static boolean isShopBlockLine(String line) {
                return line.startsWith("|") || line.startsWith("+")
                                || line.contains("Shop Information")
                                || line.toLowerCase(java.util.Locale.ROOT).startsWith("enter in chat")
                                || line.toLowerCase(java.util.Locale.ROOT).startsWith("you can buy")
                                || line.toLowerCase(java.util.Locale.ROOT).startsWith("you can sell");
        }

        /** Whether the collected block ended with a quantity prompt. */
        public boolean completedByPrompt() {
                return promptSeen;
        }

        /** The prompt's customer action (BUY/SELL), or null. */
        public String promptAction() {
                return promptAction;
        }

        /** The collected lines, in order. */
        public List<String> collected() {
                return List.copyOf(buffer);
        }

        /** Disarms and clears. */
        public void reset() {
                armed = false;
                promptSeen = false;
                promptAction = null;
                signPos = null;
                buffer.clear();
        }
}
