package net.zaminmc.zea.browser;

import net.minecraft.resources.Identifier;

/**
 * The browser pane's textures. Most of these files are NotEnoughUpdates'
 * own GUI textures, copied unmodified from the NEU repository (LGPL-3.0;
 * the attribution lives in {@code assets/zea/textures/browser/NOTICE.txt})
 * so the pane carries NEU's exact visual language rather than an
 * imitation of it: the nine-slice search bar, the wide arrow buttons,
 * the rounded item mask, the red close disc, the edge tab, the order and
 * filter icons. The {@code zea_} prefixed files are the two icon shapes
 * NEU has no counterpart for (observation-age ordering and the two
 * transaction directions), drawn in NEU's icon language — the same frame
 * colors, the same gray inactive glyphs, the same gold active treatment
 * — by {@code scripts/gen_zea_icons.py}.
 */
public final class NeuTextures {
	private NeuTextures() {
	}

	private static final String BASE = "zea:textures/browser/";

	static Identifier id(String name) {
		return Identifier.parse(BASE + name);
	}

	/** The search bar's nine-slice background (plain). */
	public static final Identifier SEARCH_BAR = id("search_bar.png");
	/** The search bar's nine-slice background in matching mode. */
	public static final Identifier SEARCH_BAR_GOLD = id("search_bar_gold.png");
	/** The search-mode toggle's magnifier icon. */
	public static final Identifier SEARCH_MODE_BUTTON = id("search_mode_button.png");
	/** The square button plate behind the search bar's flanking buttons. */
	public static final Identifier QUICKCOMMAND_BACKGROUND = id("quickcommand_background.png");
	/** The settings gear. */
	public static final Identifier SETTINGS = id("settings.png");
	/** The wide nav arrow (480x160); Prev draws it mirrored. */
	public static final Identifier RIGHT_ARROW = id("rightarrow.png");
	/** The arrow's hover/press border overlay. */
	public static final Identifier RIGHT_ARROW_OVERLAY = id("rightarrow_overlay.png");
	/** The red close disc on the info pane. */
	public static final Identifier CLOSE = id("close.png");
	/** The rounded per-tile mask behind every item. */
	public static final Identifier ITEM_MASK = id("item_mask.png");
	/** The edge tab's chevron. */
	public static final Identifier ITEM_PANE_TAB_ARROW = id("item_pane_tab_arrow.png");
	/** The ascending/descending overlays drawn over the order icons. */
	public static final Identifier ASCENDING_OVERLAY = id("ascending_overlay.png");
	public static final Identifier DESCENDING_OVERLAY = id("descending_overlay.png");

	// Order icons (alphabetical, price, observation age):
	public static final Identifier ORDER_ALPHABETICAL = id("order_alphabetical.png");
	public static final Identifier ORDER_ALPHABETICAL_ACTIVE = id("order_alphabetical_active.png");
	public static final Identifier ORDER_VALUE = id("order_value.png");
	public static final Identifier ORDER_VALUE_ACTIVE = id("order_value_active.png");
	public static final Identifier ORDER_AGE = id("zea_order_age.png");
	public static final Identifier ORDER_AGE_ACTIVE = id("zea_order_age_active.png");

	// Direction filters (all, buy-from, sell-to):
	public static final Identifier FILTER_ALL = id("sort_all.png");
	public static final Identifier FILTER_ALL_ACTIVE = id("sort_all_active.png");
	public static final Identifier FILTER_BUY = id("zea_filter_buy.png");
	public static final Identifier FILTER_BUY_ACTIVE = id("zea_filter_buy_active.png");
	public static final Identifier FILTER_SELL = id("zea_filter_sell.png");
	public static final Identifier FILTER_SELL_ACTIVE = id("zea_filter_sell_active.png");
}
