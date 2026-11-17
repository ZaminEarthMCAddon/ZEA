package net.zaminmc.zea.browser;

import net.zaminmc.zea.market.index.ItemVariantIndex;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * The inventory shop-index browser's session state — what is open,
 * what is selected, and the cached variant results. The state survives
 * screen re-inits (a resize or a recipe-book toggle rebuilds the
 * screen but not the session), exactly like the search bar's session,
 * and resets when the panel closes.
 *
 * <p>The variant results are cached against the market's revision and
 * the search query: the caller recomputes them when either changes,
 * never per frame — a still screen does zero index work. Selection is
 * by variant token and shop id — never by row index — so a refresh or
 * re-sort cannot move the selection.
 */
public final class ItemBrowserState {
	/** Which view the panel shows. */
	public enum View {
		/** The item grid. */
		GRID,
		/** One variant's detail: market summary and physical shops. */
		DETAIL
	}

	private View view = View.GRID;
	private int scrollRows;
	/** The selected variant's fingerprint token (detail view). */
	private @Nullable String selectedToken;
	/** The selected offer's shop id (detail view); selection is not navigation. */
	private @Nullable String selectedShopId;
	private int detailScroll;

	// The cached results: rebuilt only when the market or query changes.
	private long resultsRevision = -1;
	private @Nullable String resultsQuery;
	private List<ItemVariantIndex.Variant> results = List.of();
	private List<ItemVariantIndex.Variant> allResults = List.of();

	/** The view the panel currently shows. */
	public View view() {
		return view;
	}

	/** The grid's scroll offset in rows. */
	public int scrollRows() {
		return scrollRows;
	}

	public void setScrollRows(int scrollRows) {
		this.scrollRows = Math.max(0, scrollRows);
	}

	/** The selected variant token, or null in grid view. */
	public @Nullable String selectedToken() {
		return selectedToken;
	}

	/** The selected offer's shop id, or null when no offer is selected. */
	public @Nullable String selectedShopId() {
		return selectedShopId;
	}

	/** The detail view's scroll offset in rows. */
	public int detailScroll() {
		return detailScroll;
	}

	public void setDetailScroll(int detailScroll) {
		this.detailScroll = Math.max(0, detailScroll);
	}

	/** The cached variants the grid shows (already search-filtered). */
	public List<ItemVariantIndex.Variant> results() {
		return results;
	}

	/** The cached variants before the search filter. */
	public List<ItemVariantIndex.Variant> allResults() {
		return allResults;
	}

	/** Whether the cached results are current for this revision and query. */
	public boolean isCurrent(long revision, @Nullable String query) {
		boolean sameQuery = query == null ? resultsQuery == null : query.equals(resultsQuery);
		return revision == resultsRevision && sameQuery;
	}

	/** Replaces the cached results (recomputed on revision or query change). */
	public void replaceResults(List<ItemVariantIndex.Variant> filtered,
			List<ItemVariantIndex.Variant> all, long revision, @Nullable String query) {
		this.results = List.copyOf(filtered);
		this.allResults = List.copyOf(all);
		this.resultsRevision = revision;
		this.resultsQuery = query;
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

	/** Closes the panel entirely; the next inventory reopens it. */
	public void close() {
		closeDetail();
	}
}
