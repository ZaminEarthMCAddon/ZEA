package net.zaminmc.zea.market.pricing;

import net.zaminmc.zea.market.model.PriceScope;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

/**
 * Exact-decimal price arithmetic for the market.
 *
 * <p>Normalization converts an offer's price to a per-individual-item
 * basis so different bulk sizes compare fairly. The division is a true
 * division ({@code 1G / 7} does not terminate), so it is carried out in
 * BigDecimal at a fixed documented scale with half-even rounding —
 * enough resolution to rank prices fairly, never a binary float, and
 * always deterministic. Transaction totals, in contrast, are exact
 * integer/decimal multiplications with no rounding at all.
 */
public final class PriceMath {
	/** The scale normalized unit prices carry. */
	public static final int NORMALIZED_SCALE = 6;

	private PriceMath() {
	}

	/**
	 * The per-individual-item price of an offer, when the offer carries
	 * enough information: a per-bulk price normalizes through the bulk
	 * size, a per-item price is already unit. An offer without a price,
	 * without a scope, or per-bulk without a bulk size is incomparable
	 * and yields {@code empty} — it can be listed but never ranked.
	 */
	public static Optional<BigDecimal> normalize(@Nullable BigDecimal price,
			@Nullable PriceScope scope, @Nullable Integer bulkSize) {
		if (price == null || scope == null || price.signum() < 0) {
			return Optional.empty();
		}
		return switch (scope) {
			case PER_ITEM -> Optional.of(price);
			case PER_BULK -> {
				if (bulkSize == null || bulkSize <= 0) {
					yield Optional.empty();
				}
				yield Optional.of(price.divide(BigDecimal.valueOf(bulkSize), NORMALIZED_SCALE, RoundingMode.HALF_EVEN));
			}
		};
	}

	/** The exact total cost or payout of a whole number of units. */
	public static BigDecimal total(BigDecimal unitPrice, int quantity) {
		return unitPrice.multiply(BigDecimal.valueOf(quantity));
	}

	/**
	 * The exact number of individual items a bulk quantity means, when
	 * the bulk size is known. {@code empty} when it is not — the market
	 * never assumes a bulk is a stack of 64.
	 */
	public static Optional<Integer> itemsFor(int bulkCount, @Nullable Integer bulkSize) {
		if (bulkSize == null || bulkSize <= 0 || bulkCount < 0) {
			return Optional.empty();
		}
		return Optional.of(Math.multiplyExact(bulkCount, bulkSize));
	}
}
