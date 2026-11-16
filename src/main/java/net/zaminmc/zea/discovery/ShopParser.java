package net.zaminmc.zea.discovery;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The EarthMC shop-information parser: turns the server's shop block —
 * as captured plain text — into a {@link ShopObservation}'s fields. Pure
 * and side-effect-free, so the live block format is pinned by tests.
 *
 * <p>The observed block:
 *
 * <pre>
 * +---------------------------------------------------+
 * | Shop Information:
 * | Owner: Ayskow
 * | Item: Diamond Pickaxe   [Item Preview]
 * | 100% remaining.
 * | Stock: 33
 * | Price per Diamond Pickaxe - 2G
 * | This shop is SELLING items.
 * +--------------------Enchantments--------------------+
 * | Fortune III
 * | Unbreaking III
 * | Efficiency V
 * +---------------------------------------------------+
 *
 * Enter in chat, how many you wish to BUY.
 * You can buy 16. Enter all to buy them all.
 * </pre>
 *
 * <p>Direction semantics, which must never reverse: "This shop is
 * SELLING items" means the customer BUYS from it; "BUYING" means the
 * customer SELLS to it. Stock and per-transaction maximum are stored as
 * separate fields — the shop may have 33 in stock while the transaction
 * is capped at 16. The price stays raw ("2G") with its numeric value and
 * currency normalized when they parse; no conversion is ever assumed.
 */
public final class ShopParser {
        private static final Pattern OWNER = Pattern.compile("\\|\\s*Owner:\\s*(.+)");
        private static final Pattern ITEM = Pattern.compile("\\|\\s*Item:\\s*(.+?)(?:\\s+\\[[^\\]]*\\])?\\s*$");
        private static final Pattern REMAINING = Pattern.compile("\\|\\s*(\\d{1,3})% remaining");
        private static final Pattern STOCK = Pattern.compile("\\|\\s*Stock:\\s*(\\d+)");
        private static final Pattern PRICE = Pattern.compile("\\|\\s*Price(?:\\s+per\\s+.+?)?\\s*-\\s*(\\S+)");
        private static final Pattern PRICE_VALUE = Pattern.compile("^([0-9]+(?:\\.[0-9]+)?)(.*)$");
        private static final Pattern SELLING = Pattern.compile("This shop is SELLING items", Pattern.CASE_INSENSITIVE);
        private static final Pattern BUYING = Pattern.compile("This shop is BUYING items", Pattern.CASE_INSENSITIVE);
        private static final Pattern MAX_TRANSACTION =
                        Pattern.compile("You can (?:buy|sell) (\\d+)", Pattern.CASE_INSENSITIVE);
        private static final Pattern PROMPT =
                        Pattern.compile("Enter in chat, how many you wish to (BUY|SELL)", Pattern.CASE_INSENSITIVE);
        private static final Pattern BORDER = Pattern.compile("\\+[-]+(?:-+(\\w+))?[-]*\\+");
        private static final Pattern ENCHANT_BORDER = Pattern.compile("\\+-+\\s*Enchantments\\s*-+\\+",
                        Pattern.CASE_INSENSITIVE);
        private static final Pattern ENTRY_LINE = Pattern.compile("\\|\\s*(.+)");

        private ShopParser() {
        }

        /** Whether the line opens a shop-information block. */
        public static boolean isShopInfoStart(String line) {
                return line != null && line.contains("Shop Information");
        }

        /** Whether the line is a shop quantity prompt (the transaction's arming signal). */
        public static boolean isQuantityPrompt(String line) {
                return line != null && PROMPT.matcher(line).find();
        }

        /** The customer action the prompt asks for: BUY or SELL, uppercase; null when not a prompt. */
        public static String promptAction(String line) {
                if (line == null) {
                        return null;
                }
                Matcher matcher = PROMPT.matcher(line);
                return matcher.find() ? matcher.group(1).toUpperCase(Locale.ROOT) : null;
        }

        /**
         * Parses a collected shop block into the observation's optional
         * pieces. Only the fields the block carried are filled; everything
         * else stays null — a partial block is still a valid observation.
         */
        public static void apply(ShopObservation observation, List<String> lines) {
                if (observation == null || lines == null) {
                        return;
                }
                boolean inEnchantments = false;
                for (String line : lines) {
                        String trimmed = line == null ? "" : line.trim();
                        Matcher enchantBorder = ENCHANT_BORDER.matcher(trimmed);
                        if (enchantBorder.matches()) {
                                inEnchantments = true;
                                continue;
                        }
                        if (isBorder(trimmed)) {
                                // The closing border ends the enchantment section.
                                inEnchantments = false;
                                continue;
                        }
                        Matcher entry = ENTRY_LINE.matcher(trimmed);
                        String content = entry.matches() ? entry.group(1).trim() : trimmed;
                        Matcher owner = OWNER.matcher(trimmed);
                        if (owner.find()) {
                                observation.owner = owner.group(1).trim();
                                continue;
                        }
                        Matcher item = ITEM.matcher(trimmed);
                        if (item.matches()) {
                                observation.itemName = item.group(1).trim();
                                continue;
                        }
                        Matcher remaining = REMAINING.matcher(trimmed);
                        if (remaining.find()) {
                                observation.remainingPercent = Integer.parseInt(remaining.group(1));
                                continue;
                        }
                        Matcher stock = STOCK.matcher(trimmed);
                        if (stock.find()) {
                                observation.stock = Integer.parseInt(stock.group(1));
                                continue;
                        }
                        Matcher price = PRICE.matcher(trimmed);
                        if (price.find()) {
                                applyPrice(observation, price.group(1));
                                continue;
                        }
                        if (SELLING.matcher(trimmed).find()) {
                                observation.direction = ShopObservation.Direction.BUY_FROM;
                                continue;
                        }
                        if (BUYING.matcher(trimmed).find()) {
                                observation.direction = ShopObservation.Direction.SELL_TO;
                                continue;
                        }
                        Matcher max = MAX_TRANSACTION.matcher(trimmed);
                        if (max.find()) {
                                observation.maxTransaction = Integer.parseInt(max.group(1));
                                continue;
                        }
                        if (inEnchantments && entry.matches() && !content.isEmpty()
                                        && !content.startsWith("+")) {
                                observation.enchantments.add(content);
                        }
                }
        }

        /** Whether the line is one of the block's +---+ borders. */
        private static boolean isBorder(String line) {
                return BORDER.matcher(line).matches();
        }

        /** "2G" → value 2.0, currency "G"; no conversion is assumed beyond the split. */
        private static void applyPrice(ShopObservation observation, String raw) {
                observation.priceRaw = raw;
                Matcher value = PRICE_VALUE.matcher(raw);
                if (value.matches()) {
                        try {
                                observation.priceValue = Double.parseDouble(value.group(1));
                                String currency = value.group(2);
                                observation.currency = currency.isEmpty() ? null : currency;
                        } catch (NumberFormatException ignored) {
                                // A price that will not parse stays raw only.
                        }
                }
        }

        /** Convenience for tests and callers that want the parsed fields in one step. */
        public static ShopObservation parse(List<String> lines, String shopId, long observedAt) {
                ShopObservation observation = new ShopObservation();
                observation.shopId = shopId;
                observation.firstObservedAt = observedAt;
                observation.lastObservedAt = observedAt;
                observation.source = "local";
                apply(observation, lines);
                return observation;
        }

        /** The lines between the enchantment border and the closing border, for tests. */
        static List<String> enchantmentSection(List<String> lines) {
                List<String> out = new ArrayList<>();
                boolean inSection = false;
                for (String line : lines) {
                        String trimmed = line == null ? "" : line.trim();
                        if (ENCHANT_BORDER.matcher(trimmed).matches()) {
                                inSection = true;
                                continue;
                        }
                        if (inSection && isBorder(trimmed)) {
                                break;
                        }
                        if (inSection) {
                                Matcher entry = ENTRY_LINE.matcher(trimmed);
                                out.add(entry.matches() ? entry.group(1).trim() : trimmed);
                        }
                }
                return out;
        }
}
