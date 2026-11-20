package net.zaminmc.zea.market.tooltip;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.zaminmc.zea.market.index.ItemVariantIndex;
import net.zaminmc.zea.market.index.ItemVariantIndex.Offer;
import net.zaminmc.zea.market.model.ShopDirection;
import net.zaminmc.zea.market.model.ShopObservation;
import net.zaminmc.zea.market.model.StockUnit;
import net.zaminmc.zea.market.ui.MarketAge;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The market section at the end of an item tooltip, built from one
 * variant's offers — the aggregated answer, not one shop's advert:
 *
 * <pre>
 *     ▪ Sellers: 4
 *     ▪ Price: 1G-2.5G
 *     ▪ Stock: 324 items
 *     ▪ Buyers: 2
 *     ▪ Payout: 2G-3G
 *     ▪ Based on observations - 2 hours old
 * </pre>
 *
 * <p>Each line is a black bullet, a white label and a gold value — the
 * shape the whole market presentation follows. The seller side aggregates
 * the shops that sell the item to the player (EarthMC SELLING shops): the
 * seller count is the distinct sellers (one owner with several shops is
 * one seller), the price range spans every known unit price, and the
 * stock line sums the combined stock only when every stock report on the
 * side is item-denominated — a space figure, a stack figure or an
 * uninterpretable one can never be added into an item total, so it
 * leaves the line out rather than inventing a sum. The buyer side
 * (EarthMC BUYING shops) mirrors the same three aggregates when any
 * buying shop exists; its price range is the payout.
 *
 * <p>The section shows nothing that was not observed: no sellers means
 * no line, no prices means no range, no combinable stock means no stock
 * line. The age line speaks of observations, never of current
 * availability.
 */
public final class MarketTooltipLines {
        private MarketTooltipLines() {
        }

        /** The whole section, empty line and header included; empty when the variant has no offers. */
        public static List<Component> build(ItemVariantIndex.Variant variant, Instant now) {
                List<Component> lines = new ArrayList<>();
                List<Offer> selling = side(variant, ShopDirection.SELLING);
                List<Offer> buying = side(variant, ShopDirection.BUYING);
                if (selling.isEmpty() && buying.isEmpty()) {
                        return lines;
                }
                lines.add(Component.empty());
                lines.add(Component.translatable("zea.market.tooltip.header")
                                .withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD));
                if (!selling.isEmpty()) {
                        lines.add(line("zea.market.tooltip.sellers", String.valueOf(sellers(selling))));
                        addPriceLine(lines, "zea.market.tooltip.price", prices(selling));
                        addStockLine(lines, selling);
                }
                if (!buying.isEmpty()) {
                        lines.add(line("zea.market.tooltip.buyers", String.valueOf(sellers(buying))));
                        addPriceLine(lines, "zea.market.tooltip.payout", prices(buying));
                }
                lines.add(Component.translatable("zea.market.tooltip.age",
                                MarketAge.sincePhrase(variant.lastObservedAt(), now))
                                .withStyle(ChatFormatting.DARK_GRAY));
                return lines;
        }

        /** The offers of one side of the counter. */
        private static List<Offer> side(ItemVariantIndex.Variant variant, ShopDirection direction) {
                return variant.offers().stream()
                                .filter(offer -> offer.observation().direction() == direction)
                                .toList();
        }

        /**
         * The distinct sellers on one side: one owner with several shops is
         * one seller; a shop whose owner is unknown is its own seller and
         * never inflates another owner's count.
         */
        private static int sellers(List<Offer> offers) {
                Set<String> keys = new HashSet<>();
                for (Offer offer : offers) {
                        String owner = offer.shop().owner();
                        keys.add(owner == null || owner.isBlank()
                                        ? offer.shop().shopId()
                                        : owner.toLowerCase(Locale.ROOT));
                }
                return keys.size();
        }

        /** The known unit prices of one side; unpriced offers never contribute. */
        private static List<BigDecimal> prices(List<Offer> offers) {
                return offers.stream()
                                .map(Offer::unitPrice)
                                .filter(price -> price != null)
                                .sorted()
                                .toList();
        }

        private static void addPriceLine(List<Component> lines, String labelKey, List<BigDecimal> prices) {
                if (prices.isEmpty()) {
                        return;
                }
                BigDecimal min = prices.get(0);
                BigDecimal max = prices.get(prices.size() - 1);
                String range = min.compareTo(max) == 0 ? gold(min) : gold(min) + "-" + gold(max);
                lines.add(line(labelKey, range));
        }

        /**
         * The combined stock of the sellers on one side, summed only when
         * every stock report the side carries is item-denominated. Stacks
         * are not items, space is capacity, and an uninterpretable figure
         * is exactly that — none of them may join a sum, so any one of
         * them leaves the line out.
         */
        private static void addStockLine(List<Component> lines, List<Offer> offers) {
                long total = 0;
                boolean anyStock = false;
                for (Offer offer : offers) {
                        ShopObservation observation = offer.observation();
                        if (observation.stockRaw() == null) {
                                continue;
                        }
                        if (observation.stockUnit() != StockUnit.ITEMS) {
                                return;
                        }
                        long value;
                        try {
                                value = Long.parseLong(observation.stockRaw().trim());
                        } catch (NumberFormatException e) {
                                return;
                        }
                        if (value < 0) {
                                return;
                        }
                        total += value;
                        anyStock = true;
                }
                if (anyStock) {
                        lines.add(line("zea.market.tooltip.stock",
                                        Component.translatable("zea.market.tooltip.stock.items", total)));
                }
        }

        /** One market line: black bullet, white label, gold value. */
        private static MutableComponent line(String labelKey, String value) {
                return line(labelKey, Component.literal(value));
        }

        /** One market line whose value is itself a component (the stock's unit shape). */
        private static MutableComponent line(String labelKey, Component value) {
                return bullet()
                                .append(Component.translatable(labelKey).withStyle(ChatFormatting.WHITE))
                                .append(Component.literal(": ").withStyle(ChatFormatting.WHITE))
                                .append(value.copy().withStyle(ChatFormatting.GOLD));
        }

        private static MutableComponent bullet() {
                return Component.literal("▪ ").withStyle(ChatFormatting.BLACK);
        }

        /** The gold price shape: exact decimal text plus the gold suffix. */
        private static String gold(BigDecimal price) {
                return price.stripTrailingZeros().toPlainString() + "G";
        }
}
