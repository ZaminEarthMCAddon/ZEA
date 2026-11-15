package net.zaminmc.zea.market.model;

import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.time.Instant;

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
 *       bulk, a BUYING shop pays per item. Sign-only observations may
 *       carry no usable price at all — both stay {@code null} then, and
 *       an unpriced observation is listed but never ranked.</li>
 *   <li>{@code bulkSize}: the server-reported items per bulk for a
 *       SELLING shop; never assumed, never defaulted to a stack of 64.
 *       {@code null} when the server did not report it.</li>
 *   <li>{@code stockRaw}: the server-reported value verbatim
 *       ({@code Stock: 78}, {@code Space: 38}), with {@code stockUnit}
 *       recording how far that value could be interpreted. A missing
 *       value is {@code null}, not {@code 0}.</li>
 *   <li>{@code maxBuyBulks} / {@code maxSellItems}: the server-reported
 *       transaction limits ({@code you can buy 64 bulks},
 *       {@code You can sell 0}); {@code null} when unreported.</li>
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
		@Nullable Integer maxBuyBulks,
		@Nullable Integer maxSellItems,
		boolean supportsAll,
		SourceType sourceType,
		Instant observedAt) {
	public ShopObservation {
		if (shopId == null || direction == null || fingerprint == null
				|| stockUnit == null || sourceType == null || observedAt == null) {
			throw new NullPointerException("observation fields must not be null");
		}
		if (price != null && price.signum() < 0) {
			throw new IllegalArgumentException("price must not be negative");
		}
		if (bulkSize != null && bulkSize <= 0) {
			throw new IllegalArgumentException("bulk size must be positive");
		}
		if (maxBuyBulks != null && maxBuyBulks < 0) {
			throw new IllegalArgumentException("buy limit must not be negative");
		}
		if (maxSellItems != null && maxSellItems < 0) {
			throw new IllegalArgumentException("sell limit must not be negative");
		}
	}
}
