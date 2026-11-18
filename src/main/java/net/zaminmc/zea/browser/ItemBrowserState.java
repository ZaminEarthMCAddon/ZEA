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

        /**
         * The summary order — the sort bar's three cycle buttons plus the
         * unsorted state. Each of the three buttons cycles: first click
         * sorts in the button's primary direction, second click in the
         * opposite one, third click returns to {@link #NONE} — NEU's
         * relevance order, the index's own — with the direction arrow
         * visible only while that button owns the order.
         */
        public enum Order {
                /** The index's natural order — no button active, no arrow. */
                NONE("Relevance"),
                /** Display name, alphabetical: A→Z ascending, Z→A descending. */
                ALPHABETICAL("Alphabetically"),
                /** ZEA's rarity engine: Common→Legendary ascending, back descending. */
                RARITY("by Rarity"),
                /** Best BUY FROM price: highest first descending, cheapest first ascending. */
                PRICE("by Price");

                private final String label;

                Order(String label) {
                        this.label = label;
                }

                /** The label the bar's tooltip shows. */
                public String label() {
                        return label;
                }

                /**
                 * The comparator this order sorts a variant list by —
                 * ascending sense; the caller reverses it for the
                 * descending state. {@link #NONE} never sorts.
                 */
                public Comparator<ItemVariantIndex.Variant> comparator() {
                        return switch (this) {
                                case NONE -> (a, b) -> 0;
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
                                case RARITY -> (a, b) -> {
                                        int ra = rarityRank(a);
                                        int rb = rarityRank(b);
                                        // Unrated items sink below every rated one.
                                        if (ra != rb) {
                                                return Integer.compare(ra, rb);
                                        }
                                        return a.display().compareToIgnoreCase(b.display());
                                };
                        };
                }

                /**
                 * A variant's rarity rank through ZEA's rarity engine —
                 * {@link net.zaminmc.zea.rarity.ItemRarity#ordinal()} —
                 * with unrated items ranked above every rated one so they
                 * sink last. When the system is off nothing is ever rated:
                 * every variant is unrated, and the stack is never even
                 * resolved — a sort must not pay resolution for a no-op.
                 */
                private static int rarityRank(ItemVariantIndex.Variant variant) {
                        if (!net.zaminmc.zea.rarity.RarityEngine.isSystemEnabled()) {
                                return Integer.MAX_VALUE;
                        }
                        net.zaminmc.zea.rarity.ItemRarity rarity = net.zaminmc.zea.rarity.RarityEngine.rarityOf(
                                        VariantStacks.of(variant.token()).stack());
                        return rarity == null ? Integer.MAX_VALUE : rarity.ordinal();
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
        }

        /**
         * The item-category filter — NEU's right-side sort icons, carrying
         * ZEA's own buckets. Every variant lands in one {@link
         * BrowserCategory}: a weapon or tool (sword, axe, pickaxe, shovel,
         * hoe, spear, mace, bow, crossbow, trident), a piece of armor, a
         * server item (EarthMC's moon rocks, oxygen, rocket fuel — marked
         * by the server's own custom data inside the fingerprint), or
         * general goods (ores, food, blocks, everything else). These are
         * live shop items from the market's curated observations, not a
         * recipe index — the filter only decides which tiles show.
         */
        public enum Filter {
                /** Every variant the market observed. */
                ALL("All items", null),
                /** Tools and weapons: sword, axe, pickaxe, shovel, hoe, trident, bow, mace… */
                WEAPONS("Weapons and tools", BrowserCategory.WEAPONS),
                /** Worn armor pieces only. */
                ARMOR("Armor", BrowserCategory.ARMOR),
                /** Everything that is neither equipment nor a server item. */
                GENERAL("General items", BrowserCategory.GENERAL),
                /** The server's own items — EarthMC's moon, oxygen and rocket goods. */
                EARTH("Server items", BrowserCategory.EARTH);

                private final String label;
                private final @Nullable BrowserCategory category;

                Filter(String label, @Nullable BrowserCategory category) {
                        this.label = label;
                        this.category = category;
                }

                /** The label the bar's tooltip shows. */
                public String label() {
                        return label;
                }

                /** The bucket this filter shows, or null for {@link #ALL}. */
                public @Nullable BrowserCategory category() {
                        return category;
                }

                /** Whether a variant passes this filter. */
                public boolean accepts(ItemVariantIndex.Variant variant) {
                        if (category == null) {
                                return true;
                        }
                        return BrowserCategory.of(variant.token()) == category;
                }
        }

        private View view = View.GRID;
        /** Whether the pane shows at all; the close button hides it, the edge tab reopens it. */
        private boolean open = true;
        private int page;
        /** The grid's current order — relevance until a sort button is clicked. */
        private Order order = Order.NONE;
        /**
         * NEU's per-order direction: {@code true} sorts by the comparator's
         * own sense (A→Z, Common→Legendary, cheapest first) and shows NEU's
         * green ascending arrow; {@code false} reverses it (Z→A,
         * Legendary→Common, highest first) and shows NEU's red descending
         * arrow. The price button opens on {@code false} — highest price
         * first — while the other two open on {@code true}.
         */
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

        /**
         * The sort button's three-state cycle — the behavior the buttons
         * promise: clicking a button that does not own the order starts
         * it at its primary direction ({@code opensAscending}); clicking
         * it again flips to the opposite direction; clicking it a third
         * time returns to {@link Order#NONE}, NEU's relevance, where no
         * arrow shows. Alphabetical opens A→Z (NEU's green arrow), price
         * opens highest-first (the red one) — the caller passes the
         * button's own opening direction.
         */
        public void cycleOrder(Order clicked, boolean opensAscending) {
                if (order != clicked) {
                        order = clicked;
                        ascending = opensAscending;
                } else if (ascending == opensAscending) {
                        ascending = !opensAscending;
                } else {
                        order = Order.NONE;
                }
        }

        /**
         * Selects a category filter; clicking the already-active one
         * returns to {@link Filter#ALL}, NEU's no-filter state.
         */
        public void cycleFilter(Filter clicked) {
                filter = filter == clicked ? Filter.ALL : clicked;
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
