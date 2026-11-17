package net.zaminmc.zea.market.model;

import net.minecraft.world.item.ItemStackTemplate;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * A parsed EarthMC shop response — the structured result of the server's
 * message after a sign inspection, before anything is persisted.
 *
 * <p>Every field is only as present as the server made it: a response
 * missing a field carries {@code null}, never a guessed value, and
 * {@link Completeness} records whether the fields a transaction needs
 * were all there. A {@code PARTIAL} response is still recorded (its
 * confidence is lower) but never presented as a fully functional shop
 * interface — the transaction controls stay disabled when the fields
 * they would calculate from are missing.
 *
 * <p>{@code preview} is the item template from the server's
 * {@code [Item Preview]} hover when one could be extracted — the template
 * rather than a resolved stack, because identity never needs to
 * instantiate the item and the wire data is exactly what fingerprints
 * encode. The display name alone is never trusted to identify an item.
 * {@code rawLines} keeps the original message text for the debug view
 * and for fixtures.
 *
 * <p>The transaction limits are four distinct server-reported values and
 * are never conflated: {@code maxBuyBulks} and {@code maxSellBulks} count
 * bulks, {@code maxBuyItems} and {@code maxSellItems} count individual
 * items. A per-item SELLING shop reports an item limit
 * ({@code You can buy 4.}), a bulk SELLING shop a bulk limit
 * ({@code you can buy 28 bulks.}) — a limit of the wrong unit would
 * oversell by the bulk size. {@code remainingPercent} is the shelf-fill
 * percentage some responses report ({@code 100% remaining.}); it is a
 * session state, not a stock count. {@code enchantments} is the server's
 * own enchantment section verbatim — presentation and cross-check
 * evidence only, never an item identity (the preview payload is).
 */
public record ShopResponse(
		@Nullable String owner,
		ShopDirection direction,
		@Nullable ItemStackTemplate preview,
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
		Completeness completeness,
		List<String> rawLines,
		Instant receivedAt) {
	public ShopResponse {
		if (direction == null || stockUnit == null || completeness == null
				|| rawLines == null || receivedAt == null) {
			throw new NullPointerException("shop response fields must not be null");
		}
		rawLines = List.copyOf(rawLines);
		enchantments = List.copyOf(enchantments == null ? List.of() : enchantments);
		if (price != null && price.signum() < 0) {
			throw new IllegalArgumentException("price must not be negative");
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

	/**
	 * How complete the parsed response is.
	 *
	 * <ul>
	 *   <li>{@code COMPLETE} — owner, item identity, direction and price
	 *       were all present, and the price carries the quantity it is
	 *       charged for (a per-bulk price with its reported bulk size, or
	 *       a per-item price that needs none); the response may back a
	 *       full transaction flow (quantity selection still respects the
	 *       reported limits).</li>
	 *   <li>{@code PARTIAL} — a real shop response with missing fields;
	 *       recorded with lower confidence, transaction controls disabled
	 *       where their inputs are absent.</li>
	 * </ul>
	 */
	public enum Completeness {
		COMPLETE,
		PARTIAL
	}
}
