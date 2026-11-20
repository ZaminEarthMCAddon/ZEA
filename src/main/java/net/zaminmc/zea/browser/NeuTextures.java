package net.zaminmc.zea.browser;

import net.minecraft.resources.Identifier;

/**
 * The browser pane's textures. Most of these files are NotEnoughUpdates'
 * own GUI textures, copied unmodified from the NEU repository (LGPL-3.0;
 * the attribution lives in {@code assets/zea/textures/browser/NOTICE.txt})
 * so the pane carries NEU's exact visual language rather than an
 * imitation of it: the nine-slice search bar, the wide arrow buttons,
 * the rounded item mask, the red close disc, the edge tab, the order
 * icons (alphabetical, rarity, value), the direction arrow overlays and
 * the category filter icons (all, weapon). The {@code zea_} prefixed
 * files are the two icon shapes NEU has no counterpart for (the
 * general-goods beacon and the server-items globe), drawn in NEU's icon
 * language — the same frame colors, the same gray inactive glyphs, the
 * same gold active treatment — by {@code scripts/gen_browser_icons.py}.
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
        /** The rounded per-tile mask behind every item. */
        public static final Identifier ITEM_MASK = id("item_mask.png");
        /** The edge tab's chevron. */
        public static final Identifier ITEM_PANE_TAB_ARROW = id("item_pane_tab_arrow.png");
        /** The ascending/descending overlays drawn over the order icons. */
        public static final Identifier ASCENDING_OVERLAY = id("ascending_overlay.png");
        public static final Identifier DESCENDING_OVERLAY = id("descending_overlay.png");

        // Order icons (alphabetical, rarity, price) — NEU's own three:
        public static final Identifier ORDER_ALPHABETICAL = id("order_alphabetical.png");
        public static final Identifier ORDER_ALPHABETICAL_ACTIVE = id("order_alphabetical_active.png");
        public static final Identifier ORDER_RARITY = id("order_rarity.png");
        public static final Identifier ORDER_RARITY_ACTIVE = id("order_rarity_active.png");
        public static final Identifier ORDER_VALUE = id("order_value.png");
        public static final Identifier ORDER_VALUE_ACTIVE = id("order_value_active.png");

        // Category filters (all, weapons and tools, armor, general, server)
        // — NEU's all and weapon icons, ZEA's beacon and globe for the
        // buckets NEU has no counterpart for:
        public static final Identifier FILTER_ALL = id("sort_all.png");
        public static final Identifier FILTER_ALL_ACTIVE = id("sort_all_active.png");
        public static final Identifier FILTER_WEAPONS = id("sort_weapon.png");
        public static final Identifier FILTER_WEAPONS_ACTIVE = id("sort_weapon_active.png");
        public static final Identifier FILTER_ARMOR = id("sort_armor.png");
        public static final Identifier FILTER_ARMOR_ACTIVE = id("sort_armor_active.png");
        public static final Identifier FILTER_GENERAL = id("zea_filter_general.png");
        public static final Identifier FILTER_GENERAL_ACTIVE = id("zea_filter_general_active.png");
        public static final Identifier FILTER_EARTH = id("zea_filter_earth.png");
        public static final Identifier FILTER_EARTH_ACTIVE = id("zea_filter_earth_active.png");
}
