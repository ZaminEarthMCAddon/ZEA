package net.zaminmc.zea.market.index;

import net.zaminmc.zea.market.model.ShopDirection;
import net.zaminmc.zea.market.pricing.PriceMath;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The market price index for one item variant — the aggregated answer to
 * "what does this item cost, and what does it pay out".
 *
 * <p>The two sides of the counter are computed separately and never
 * compared: <b>BUY FROM</b> aggregates the shops that sell the item to
 * the player (EarthMC SELLING shops), <b>SELL TO</b> aggregates the
 * shops that buy it (EarthMC BUYING shops). The best price is the
 * cheapest purchase but the highest payout — favorability is per side.
 *
 * <p>The aggregation is owner-balanced, and this is deliberate: one
 * owner operating many shops of one item must not gain a thousand
 * statistical votes. The method, exactly:
 *
 * <ol>
 *   <li>Take every offer of the variant for the side whose unit price
 *       could be normalized (see {@link PriceMath}); an unpriced or
 *       incomparable offer is listed but never enters the statistics.</li>
 *   <li>Per owner, keep that owner's most favorable price on this side —
 *       their cheapest shop for BUY FROM, their highest-paying shop for
 *       SELL TO. One owner, one vote. A shop whose owner is unknown is
 *       its own vote, keyed by shop; it can never inflate another
 *       owner's weight.</li>
 *   <li>The statistics run over the owner votes: best, worst and — when
 *       at least three owners voted — the median of the votes. The
 *       median deliberately needs more than one vote per side of the
 *       middle; a two-owner market has no meaningful middle.</li>
 * </ol>
 *
 * <p>Every individual shop stays visible in the variant's offers
 * regardless of the aggregation — the index explains the market, the
 * offer list is the market. Prices are exact decimals end to end; the
 * only rounding is the documented normalization scale.
 */
public final class VariantPricing {
	private VariantPricing() {
	}

	/** How many owners must have voted before a median is reported. */
	public static final int MIN_OWNERS_FOR_MEDIAN = 3;

	/** One side of a variant's market, aggregated per owner. */
	public record SideStats(
			@Nullable BigDecimal best,
			@Nullable BigDecimal worst,
			@Nullable BigDecimal median,
			int pricedShops,
			int shops,
			int owners) {
	}

	/**
	 * The player-side statistics of one variant for one side: {@code
	 * direction} is the <b>shop's</b> direction ({@code SELLING} shops
	 * are where the player buys), and the favorability rule follows the
	 * side — cheapest first when buying, highest payout first when
	 * selling.
	 */
	public static SideStats stats(List<ItemVariantIndex.Offer> offers, ShopDirection direction) {
		List<ItemVariantIndex.Offer> priced = offers.stream()
				.filter(offer -> offer.observation().direction() == direction)
				.filter(offer -> offer.unitPrice() != null)
				.toList();
		if (priced.isEmpty()) {
			return new SideStats(null, null, null, 0, offers.size(), 0);
		}
		// One vote per owner: their most favorable price on this side.
		Map<String, BigDecimal> votes = new LinkedHashMap<>();
		for (ItemVariantIndex.Offer offer : priced) {
			String owner = offer.shop().owner();
			String key = owner == null || owner.isBlank()
					? offer.shop().shopId()
					: owner.toLowerCase(java.util.Locale.ROOT);
			BigDecimal price = offer.unitPrice();
			BigDecimal incumbent = votes.get(key);
			if (incumbent == null || favorability(direction).compare(price, incumbent) < 0) {
				votes.put(key, price);
			}
		}
		List<BigDecimal> sorted = new ArrayList<>(votes.values());
		sorted.sort(Comparator.naturalOrder());
		BigDecimal best = favorableOf(sorted, direction);
		BigDecimal worst = favorableOf(reversed(sorted), direction);
		BigDecimal median = medianOf(sorted);
		int owners = votes.size();
		return new SideStats(best, worst, median, priced.size(), offers.size(), owners);
	}

	/** The comparison that decides which price "wins" on this side. */
	private static Comparator<BigDecimal> favorability(ShopDirection direction) {
		return direction == ShopDirection.SELLING
				? Comparator.naturalOrder()
				: Comparator.reverseOrder();
	}

	/** The most favorable price in the list, in either order. */
	private static BigDecimal favorableOf(List<BigDecimal> sorted, ShopDirection direction) {
		return direction == ShopDirection.SELLING ? sorted.get(0) : sorted.get(sorted.size() - 1);
	}

	/**
	 * The median vote, or {@code null} below the owner threshold. An
	 * even vote count averages the two middle votes exactly (half-even
	 * at the normalization scale) — the median is a representative
	 * price, never an invented one.
	 */
	private static @Nullable BigDecimal medianOf(List<BigDecimal> sorted) {
		int size = sorted.size();
		if (size < MIN_OWNERS_FOR_MEDIAN) {
			return null;
		}
		int middle = size / 2;
		if (size % 2 == 1) {
			return sorted.get(middle);
		}
		return sorted.get(middle - 1)
				.add(sorted.get(middle))
				.divide(BigDecimal.valueOf(2), PriceMath.NORMALIZED_SCALE, java.math.RoundingMode.HALF_EVEN);
	}

	private static List<BigDecimal> reversed(List<BigDecimal> sorted) {
		List<BigDecimal> copy = new ArrayList<>(sorted);
		java.util.Collections.reverse(copy);
		return copy;
	}

	/** The unit price of one offer, for presentation; empty when unpriced. */
	public static Optional<BigDecimal> unitPrice(ItemVariantIndex.Offer offer) {
		return Optional.ofNullable(offer.unitPrice());
	}
}
