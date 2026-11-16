package net.zaminmc.zea.market;

import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.Locale;

/**
 * The market browser's sort orders — with the direction rule built in,
 * so a price sort always puts the player's best offer first and the two
 * sides of the counter are never compared as if interchangeable:
 *
 * <ul>
 *   <li>{@code PRICE} on BUY FROM sorts cheapest first (best place to
 *       buy); on SELL TO it sorts highest first (best payout). In the
 *       mixed ALL view it sorts cheapest first and the direction tags
 *       keep the sides readable.</li>
 *   <li>{@code PRICE_PER_ITEM} applies the same rule to the normalized
 *       per-item price, the only fair comparison across different bulk
 *       sizes.</li>
 *   <li>{@code RECENTLY_OBSERVED} puts fresh verifications first.</li>
 *   <li>{@code ITEM_NAME} is the neutral, alphabetical view.</li>
 * </ul>
 *
 * <p>Listings a sort cannot judge — no price, or a price without a
 * scope, for the price sorts — sink below every judged one in both
 * directions instead of pretending to a value. Every order breaks ties
 * by shop id, so the same data always renders in the same order.
 */
public enum MarketSort {
	ITEM_NAME("Name"),
	PRICE("Price"),
	PRICE_PER_ITEM("Price per item"),
	RECENTLY_OBSERVED("Recently observed");

	private final String label;

	MarketSort(String label) {
		this.label = label;
	}

	/** The button label; the UI appends the active direction's meaning. */
	public String label() {
		return label;
	}

	/** The next sort in cycle order, for the browser's cycling control. */
	public MarketSort next() {
		return values()[(ordinal() + 1) % values().length];
	}

	/** The comparator for one view; {@code filter} decides the price direction. */
	public Comparator<MarketListings.Listing> comparator(MarketListings.Filter filter) {
		Comparator<MarketListings.Listing> judged = switch (this) {
			case ITEM_NAME -> Comparator
					.comparing((MarketListings.Listing listing) -> displayName(listing))
					.thenComparing(listing -> listing.shop().shopId());
			case PRICE -> pricedOrder(PriceValue.RawPrice, filter);
			case PRICE_PER_ITEM -> pricedOrder(PriceValue.UnitPrice, filter);
			case RECENTLY_OBSERVED -> Comparator
					.comparing((MarketListings.Listing listing) -> listing.observation().observedAt(),
							Comparator.reverseOrder())
					.thenComparing(listing -> listing.shop().shopId());
		};
		return judged;
	}

	private static String displayName(MarketListings.Listing listing) {
		String display = listing.observation().itemDisplay();
		// Unnamed rows sort after every named one, deterministically.
		return display == null || display.isBlank()
				? "\uFFFF" + listing.shop().shopId()
				: display.toLowerCase(Locale.ROOT);
	}

	/**
	 * The price orders share one shape: judgeable rows (price + scope
	 * for the raw sort, a normalized unit price for the per-item sort)
	 * order by value — ascending when buying, descending when selling —
	 * and unjudgeable rows sink below them in both directions.
	 */
	private static Comparator<MarketListings.Listing> pricedOrder(PriceValue value,
			MarketListings.Filter filter) {
		Comparator<MarketListings.Listing> byValue = Comparator.comparing(value::of);
		Comparator<MarketListings.Listing> judged = filter == MarketListings.Filter.SELL_TO
				? byValue.reversed()
				: byValue;
		return Comparator
				.comparing((MarketListings.Listing listing) -> value.judgeable(listing) ? 0 : 1)
				.thenComparing(judged)
				.thenComparing(listing -> listing.shop().shopId());
	}

	/** Which price a sort reads, and what makes it judgeable. */
	private enum PriceValue {
		/** The price exactly as the shop reported it. */
		RawPrice {
			@Override
			boolean judgeable(MarketListings.Listing listing) {
				return listing.observation().price() != null && listing.observation().priceScope() != null;
			}

			@Override
			BigDecimal of(MarketListings.Listing listing) {
				BigDecimal price = listing.observation().price();
				return price != null ? price : BigDecimal.valueOf(Long.MAX_VALUE);
			}
		},
		/** The normalized per-item price. */
		UnitPrice {
			@Override
			boolean judgeable(MarketListings.Listing listing) {
				return listing.unitPrice() != null;
			}

			@Override
			BigDecimal of(MarketListings.Listing listing) {
				return listing.unitPrice() != null ? listing.unitPrice() : BigDecimal.valueOf(Long.MAX_VALUE);
			}
		};

		abstract boolean judgeable(MarketListings.Listing listing);

		abstract BigDecimal of(MarketListings.Listing listing);
	}
}
