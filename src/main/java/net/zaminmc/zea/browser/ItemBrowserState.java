package net.zaminmc.zea.browser;

import net.zaminmc.zea.market.index.ItemVariantIndex;
import org.jspecify.annotations.Nullable;

import java.util.Comparator;
import java.util.List;

/**
 * The inventory item browser's session state — what is open, what is
 * selected, and the cached variant results. The state survives screen
 * re-inits (a resize or a recipe-book toggle rebuilds the screen but
 * not the session), exactly like the search bar's session, and resets
 * when the pane closes.
 *
 * <p>The variant results are cached against the market's revision, the
 * search query and the view's filter and order: the caller recomputes
 * them when any of those change, never per frame — a still screen does
 * zero index work. Selection is by variant token and shop id — never by
 * row index — so a refresh or re-sort cannot move the selection.
 *
 * <p>The grid paginates like NEU's item list: a page is a fixed grid of
 * tiles, Prev and Next turn it, and the wheel turns it too.
 */
public final class ItemBrowserState {
	/** Which view the pane shows. */
	public enum View {
		/** The item grid. */
		GRID,
		/** One variant's detail: market summary and physical shops. */
		DETAIL
	}

	/** The summary order — NEU's three order icons. */
	public enum Order {
		/** Display name, alphabetical. */
		ALPHABETICAL("Alphabetically"),
		/** Best BUY FROM price, cheapest first. */
		PRICE("by Price"),
		/** Most recently observed first. */
		NEWEST("by Observation");

		private final String label;

		Order(String label) {
			this.label = label;
		}

		/** The label the bar's tooltip shows. */
		public String label() {
			return label;
		}

		/** The comparator this order sorts a variant list by. */
		public Comparator<ItemVariantIndex.Variant> comparator() {
			return switch (this) {
				case ALPHABETICAL -> Comparator.comparing(ItemVariantIndex.Variant::display,
						String.CASE_INSENSITIVE_ORDER);
				case PRICE -> (a, b) -> {
					java.math.BigDecimal pa = bestBuyPrice(a);
					java.math.BigDecimal pb = bestBuyPrice(b);
					if (pa == null && pb == null) {
						return a.display().compareToIgnoreCase(b.display());
					}
					if (pa == null) {
						return 1;
					}
					if (pb == null) {
						return -1;
					}
					int byPrice = pa.compareTo(pb);
					return byPrice != 0 ? byPrice : a.display().compareToIgnoreCase(b.display());
				};
				case NEWEST -> (a, b) -> {
					java.time.Instant ta = latestObservation(a);
					java.time.Instant tb = latestObservation(b);
					if (ta == null && tb == null) {
						return a.display().compareToIgnoreCase(b.display());
					}
					if (ta == null) {
						return 1;
					}
					if (tb == null) {
						return -1;
					}
					int byTime = tb.compareTo(ta);
					return byTime != 0 ? byTime : a.display().compareToIgnoreCase(b.display());
				};
			};
		}

		/** A variant's best BUY FROM unit price, or null when it has none. */
		private static java.math.@Nullable BigDecimal bestBuyPrice(ItemVariantIndex.Variant variant) {
			java.math.BigDecimal best = null;
			for (ItemVariantIndex.Offer offer : variant.offers()) {
				if (offer.observation().direction() != net.zaminmc.zea.market.model.ShopDirection.SELLING
						|| offer.unitPrice() == null) {
					continue;
				}
				if (best == null || offer.unitPrice().compareTo(best) < 0) {
					best = offer.unitPrice();
				}
			}
			return best;
		}

		/** A variant's latest observation instant, or null when it has none. */
		private static java.time.@Nullable Instant latestObservation(ItemVariantIndex.Variant variant) {
			java.time.Instant latest = null;
			for (ItemVariantIndex.Offer offer : variant.offers()) {
				java.time.Instant observedAt = offer.observation().observedAt();
				if (observedAt != null && (latest == null || observedAt.isAfter(latest))) {
					latest = observedAt;
				}
			}
			return latest;
		}
	}

	/** The direction filter — NEU's category icons, mapped to shop sides. */
	public enum Filter {
		/** Every variant the market observed. */
		ALL("All items"),
		/** Only variants at least one shop sells. */
		BUY_FROM("Buy from shops"),
		/** Only variants at least one shop buys. */
		SELL_TO("Sell to shops");

		private final String label;

		Filter(String label) {
			this.label = label;
		}

		/** The label the bar's tooltip shows. */
		public String label() {
			return label;
		}

		/** Whether a variant passes this filter. */
		public boolean accepts(ItemVariantIndex.Variant variant) {
			return switch (this) {
				case ALL -> true;
				case BUY_FROM -> variant.offers().stream()
						.anyMatch(offer -> offer.observation().direction()
								== net.zaminmc.zea.market.model.ShopDirection.SELLING);
				case SELL_TO -> variant.offers().stream()
						.anyMatch(offer -> offer.observation().direction()
								== net.zaminmc.zea.market.model.ShopDirection.BUYING);
			};
		}
	}

	private View view = View.GRID;
	/** Whether the pane shows at all; the close button hides it, the edge tab reopens it. */
	private boolean open = true;
	private int page;
	private Order order = Order.ALPHABETICAL;
	/** NEU's per-order ascending toggle; the overlay arrows show the direction. */
	private boolean ascending = true;
	private Filter filter = Filter.ALL;

	/** The selected variant's fingerprint token (detail view). */
	private @Nullable String selectedToken;
	/** The selected offer's shop id (detail view); selection is not navigation. */
	private @Nullable String selectedShopId;
	private int detailScroll;

	// The cached results: rebuilt only when the market or the view changes.
	private long resultsRevision = -1;
	private @Nullable String resultsQuery;
	private @Nullable Order resultsOrder;
	private boolean resultsAscending = true;
	private @Nullable Filter resultsFilter;
	private List<ItemVariantIndex.Variant> results = List.of();

	/** The view the pane currently shows. */
	public View view() {
		return view;
	}

	/** Whether the pane shows at all. */
	public boolean isOpen() {
		return open;
	}

	/** Opens the pane again after the close button hid it. */
	public void open() {
		open = true;
	}

	/** Hides the pane; the edge tab or the next close-reopen cycle brings it back. */
	public void close() {
		open = false;
		closeDetail();
	}

	/** The grid's current page, zero-based. */
	public int page() {
		return page;
	}

	public void setPage(int page) {
		this.page = Math.max(0, page);
	}

	/** The grid's current summary order. */
	public Order order() {
		return order;
	}

	public void setOrder(Order order) {
		this.order = order;
	}

	/** Whether this order sorts ascending (NEU's right-click toggle). */
	public boolean ascending() {
		return ascending;
	}

	public void setAscending(boolean ascending) {
		this.ascending = ascending;
	}

	/** The grid's current direction filter. */
	public Filter filter() {
		return filter;
	}

	public void setFilter(Filter filter) {
		this.filter = filter;
	}

	/** The selected variant token, or null in grid view. */
	public @Nullable String selectedToken() {
		return selectedToken;
	}

	/** The selected offer's shop id, or null when no offer is selected. */
	public @Nullable String selectedShopId() {
		return selectedShopId;
	}

	/** The detail view's scroll offset in pixels. */
	public int detailScroll() {
		return detailScroll;
	}

	public void setDetailScroll(int detailScroll) {
		this.detailScroll = Math.max(0, detailScroll);
	}

	/** The cached variants the grid shows (filtered, ordered, search-filtered). */
	public List<ItemVariantIndex.Variant> results() {
		return results;
	}

	/**
	 * Whether the cached results are current for this revision, query,
	 * filter and order.
	 */
	public boolean isCurrent(long revision, @Nullable String query, Order order, boolean ascending, Filter filter) {
		boolean sameQuery = query == null ? resultsQuery == null : query.equals(resultsQuery);
		return revision == resultsRevision && sameQuery && order == resultsOrder
				&& ascending == resultsAscending && filter == resultsFilter;
	}

	/** Replaces the cached results (recomputed when the view's inputs change). */
	public void replaceResults(List<ItemVariantIndex.Variant> results,
			long revision, @Nullable String query, Order order, boolean ascending, Filter filter) {
		this.results = List.copyOf(results);
		this.resultsRevision = revision;
		this.resultsQuery = query;
		this.resultsOrder = order;
		this.resultsAscending = ascending;
		this.resultsFilter = filter;
	}

	/** Opens a variant's detail view. */
	public void openDetail(String token) {
		view = View.DETAIL;
		selectedToken = token;
		selectedShopId = null;
		detailScroll = 0;
	}

	/** Selects one offer inside the detail view; no navigation happens. */
	public void selectOffer(String shopId) {
		selectedShopId = shopId.equals(selectedShopId) ? null : shopId;
	}

	/** Back to the grid. */
	public void closeDetail() {
		view = View.GRID;
		selectedToken = null;
		selectedShopId = null;
		detailScroll = 0;
	}

}
