package net.zaminmc.zea.market.model;

import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * What one physical shop advertised at one moment.
 *
 * <p>A shop is a place; an observation is a measurement taken at that
 * place. Nothing here is overwritten by a later visit — observations
 * accumulate, so price, stock and availability changes stay visible, and
 * the market ranks offers by the timestamp of the measurement, never by
 * the shop's most recent visit alone.
 *
 * <p>Field rules carried over from the protocol:
 *
 * <ul>
 *   <li>{@code price} plus {@code priceScope}: a SELLING shop charges per
 *       bulk or per item exactly as the server words it, a BUYING shop
 *       pays per item. Sign-only observations may carry no usable price
 *       at all — both stay {@code null} then, and an unpriced observation
 *       is listed but never ranked.</li>
 *   <li>{@code bulkSize}: the server-reported items per bulk for a
 *       SELLING shop; never assumed, never defaulted to a stack of 64.
 *       {@code null} when the server did not report it.</li>
 *   <li>{@code stockRaw}: the server-reported value verbatim
 *       ({@code Stock: 78}, {@code Space: 38}), with {@code stockUnit}
 *       recording how far that value could be interpreted. A missing
 *       value is {@code null}, not {@code 0}.</li>
 *   <li>{@code remainingPercent}: the shelf-fill percentage the response
 *       reported ({@code 100% remaining.}); session state, never a stock
 *       count, and never used to derive stock.</li>
 *   <li>{@code maxBuyBulks} / {@code maxBuyItems} /
 *       {@code maxSellBulks} / {@code maxSellItems}: the server-reported
 *       transaction limits, each in the unit its sentence reported — a
 *       bulk limit is not an item limit; {@code null} when unreported.</li>
 *   <li>{@code enchantments}: the server's enchantment section verbatim,
 *       for presentation and cross-checks against the preview payload.
 *       The fingerprint, not this list, is the item identity.</li>
 * </ul>
 */
public record ShopObservation(
		String shopId,
		ShopDirection direction,
		ItemFingerprint fingerprint,
		@Nullable String itemDisplay,
		@Nullable BigDecimal price,
		@Nullable PriceScope priceScope,
		@Nullable Integer bulkSize,
		@Nullable String stockRaw,
		StockUnit stockUnit,
		@Nullable Integer remainingPercent,
		@Nullable Integer maxBuyBulks,
		@Nullable Integer maxBuyItems,
		@Nullable Integer maxSellBulks,
		@Nullable Integer maxSellItems,
		boolean supportsAll,
		List<String> enchantments,
		SourceType sourceType,
		Instant observedAt) {
	public ShopObservation {
		if (shopId == null || direction == null || fingerprint == null
				|| stockUnit == null || sourceType == null || observedAt == null) {
			throw new NullPointerException("observation fields must not be null");
		}
		enchantments = List.copyOf(enchantments == null ? List.of() : enchantments);
		if (price != null && price.signum() < 0) {
			throw new IllegalArgumentException("price must not be negative");
		}
		if (bulkSize != null && bulkSize <= 0) {
			throw new IllegalArgumentException("bulk size must be positive");
		}
		if (remainingPercent != null && (remainingPercent < 0 || remainingPercent > 100)) {
			throw new IllegalArgumentException("remaining percentage must be between 0 and 100");
		}
		if (maxBuyBulks != null && maxBuyBulks < 0
				|| maxBuyItems != null && maxBuyItems < 0
				|| maxSellBulks != null && maxSellBulks < 0
				|| maxSellItems != null && maxSellItems < 0) {
			throw new IllegalArgumentException("transaction limits must not be negative");
		}
	}
}
