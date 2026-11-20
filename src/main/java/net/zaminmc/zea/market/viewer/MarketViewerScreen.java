package net.zaminmc.zea.market.viewer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.zaminmc.zea.ZeaClient;
import net.zaminmc.zea.browser.VariantStacks;
import net.zaminmc.zea.market.MarketSubsystem;
import net.zaminmc.zea.market.index.ItemVariantIndex.Offer;
import net.zaminmc.zea.market.index.ItemVariantIndex.Variant;
import net.zaminmc.zea.market.index.VariantPricing;
import net.zaminmc.zea.market.model.ShopDirection;
import net.zaminmc.zea.market.model.ShopObservation;
import net.zaminmc.zea.market.model.SourceType;
import net.zaminmc.zea.market.ui.MarketAge;
import net.zaminmc.zea.nav.ShopNavigation;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Comparator;

/**
 * The market viewer: the screen that opens over the inventory when an
 * item is clicked in the item browser — JEI's RecipesGui rebuilt for a
 * live market, studied from the upstream source with its history, not
 * approximated from screenshots.
 *
 * <p>What JEI does, this does. Opening: JEI's {@code open()} captures
 * the current screen as {@code parentScreen} and swaps itself in — the
 * inventory stays alive behind the viewer and returns exactly as it
 * was when the viewer closes; closing with Esc <em>or the inventory
 * key</em> (JEI's "Close inventory" command) restores the parent. The
 * screen is JEI's {@code isPauseScreen = false}, {@code isInGameUi =
 * true}. The geometry is JEI's: the panel sizes to JEI's defaults
 * (width 198, height at most 350, the screen minus 58 pixels of
 * breathing room), centers horizontally and sits 45 pixels down — JEI's
 * {@code guiTop} — and the content area is JEI's {@code
 * getRecipeLayoutsArea}: six pixels of frame padding, the two nav rows
 * above. The chrome is JEI's to the pixel: the {@code
 * gui_background_v2} nine-slice panel, the 13 px icon buttons carrying
 * JEI's 9×7 arrows, the dark page strip between them with the centered
 * "1/3" string, the white shadowed title that truncates with an
 * ellipsis and shows the full name as a tooltip (JEI's {@code
 * RecipeCategoryTitle}), and JEI's 24 px tabs riding the panel's top
 * edge with their three-pixel overlap. The textures are JEI's own
 * files, copied unmodified (MIT; the attribution lives in
 * {@code assets/zea/textures/market/NOTICE.txt}).
 *
 * <p>What JEI shows for recipes, this shows for a market: the title row
 * cycles the previous/next item of the browser's current results —
 * JEI's previous/next category — the tabs pick the market's side (all,
 * buy from, sell to) the way JEI's tabs pick a recipe category, and the
 * paginated content lists one offer per row in JEI's slot language: the
 * item in a vanilla slot plate, the owner and unit price on the first
 * line, stock and where on the second, the age right-aligned. The
 * identity block under the header carries the item's enchantment
 * summary and both sides' aggregate statistics, the footer carries the
 * one action — navigating to the selected shop — as a vanilla button
 * drawn with JEI's own button sprites, disabled until an offer row is
 * selected. Selecting an offer never issues a command.
 *
 * <p>Rendering reads caches only: the offer rows and their pagination
 * are built when the variant, the side or the market revision changes —
 * a still screen does zero index work.
 */
public final class MarketViewerScreen extends Screen {
        // ------------------------------------------------------------------
        // Textures — JEI's own files, copied unmodified (see NOTICE.txt)
        // ------------------------------------------------------------------

        private static final String BASE = "zea:textures/market/";
        private static Identifier id(String name) {
                return Identifier.parse(BASE + name);
        }
        /** The recipe gui's nine-slice panel (64×64, 16 px border). */
        private static final Identifier PANEL = id("panel.png");
        /** The vanilla-look button plates (20×20, 6 px border). */
        private static final Identifier BUTTON = id("button.png");
        private static final Identifier BUTTON_HOVER = id("button_hover.png");
        private static final Identifier BUTTON_DISABLED = id("button_disabled.png");
        /** JEI's 9×7 arrow icons. */
        private static final Identifier ARROW_NEXT = id("icons/arrow_next.png");
        private static final Identifier ARROW_PREVIOUS = id("icons/arrow_previous.png");
        /** JEI's 24×24 category tabs. */
        private static final Identifier TAB_SELECTED = id("tab_selected.png");
        private static final Identifier TAB_UNSELECTED = id("tab_unselected.png");

        // ------------------------------------------------------------------
        // JEI's geometry
        // ------------------------------------------------------------------

        /** JEI's {@code borderPadding}. */
        private static final int BORDER_PADDING = 6;
        /** JEI's {@code navBarPadding}. */
        private static final int NAV_BAR_PADDING = 2;
        /** JEI's {@code titleInnerPadding}. */
        private static final int TITLE_INNER_PADDING = 14;
        /** JEI's {@code smallButtonWidth}/{@code smallButtonHeight}. */
        private static final int SMALL_BUTTON = 13;
        /** JEI's {@code minRecipeGuiWidth}. */
        private static final int MIN_GUI_WIDTH = 198;
        /** JEI's {@code minRecipeGuiHeight}. */
        private static final int MIN_GUI_HEIGHT = 175;
        /** JEI's {@code defaultRecipeGuiHeight}. */
        private static final int MAX_GUI_HEIGHT = 350;
        /** JEI's {@code RecipeGuiTab.TAB_WIDTH}/{@code TAB_HEIGHT}. */
        private static final int TAB_SIZE = 24;
        /** JEI's {@code TAB_GUI_OVERLAP}: the tabs ride the top edge by three pixels. */
        private static final int TAB_OVERLAP = 3;
        /** JEI's {@code TAB_HORIZONTAL_INSET}. */
        private static final int TAB_INSET = 2;
        /** JEI's nine-slice sprite facts: the panel sprite is 64 px with a 16 px border. */
        private static final int PANEL_SPRITE = 64;
        private static final int PANEL_BORDER = 16;
        /** The button sprite is 20 px with a 6 px border. */
        private static final int BUTTON_SPRITE = 20;
        private static final int BUTTON_BORDER = 6;

        // ------------------------------------------------------------------
        // Colors — JEI's defaults, plus ZEA's market tags in the dark
        // variants the light panel needs
        // ------------------------------------------------------------------

        /** JEI's {@code PAGE_NAVIGATION_BACKGROUND}. */
        private static final int PAGE_NAV_BACKGROUND = 0x30000000;
        /** JEI's {@code RECIPE_CATEGORY_TITLE_TEXT}. */
        private static final int TITLE_TEXT = 0xFFFFFFFF;
        /** The body text on the light panel — JEI's black widget text. */
        private static final int TEXT = 0xFF000000;
        /** The secondary line — a mid gray that holds on the gray panel. */
        private static final int TEXT_DIM = 0xFF4C4C4C;
        /** ZEA's BUY/SELL tags, darkened for the light panel. */
        private static final int BUY_COLOR = 0xFF9C5000;
        private static final int SELL_COLOR = 0xFF0F7A3C;
        /** ZEA's selected accent — the gold the browser and the map use. */
        private static final int SELECTED_FRAME = 0xFFE8C14A;
        private static final int SELECTED_WASH = 0x30E8C14A;
        /** The row hover wash — a white lift on the gray panel. */
        private static final int HOVER_WASH = 0x48FFFFFF;
        /** The vanilla slot plate: dark top/left, white bottom/right, gray interior. */
        private static final int SLOT_DARK = 0xFF373737;
        private static final int SLOT_LIGHT = 0xFFFFFFFF;
        private static final int SLOT_INTERIOR = 0xFF8B8B8B;
        /** The vanilla button label colors. */
        private static final int BUTTON_TEXT = 0xFFE0E0E0;
        private static final int BUTTON_TEXT_DISABLED = 0xFFA0A0A0;

        /** One offer row's height. */
        private static final int ROW_HEIGHT = 22;
        /** The footer button's height. */
        private static final int NAV_BUTTON_HEIGHT = 16;

        // ------------------------------------------------------------------
        // The market sides — the tabs
        // ------------------------------------------------------------------

        /** A side of the market, JEI's tab model. */
        public enum Side {
                ALL("All offers", null),
                BUY_FROM("Buy from shops", ShopDirection.SELLING),
                SELL_TO("Sell to shops", ShopDirection.BUYING);

                private final String label;
                private final @Nullable ShopDirection direction;

                Side(String label, @Nullable ShopDirection direction) {
                        this.label = label;
                        this.direction = direction;
                }

                /** The tab's tooltip. */
                public String label() {
                        return label;
                }

                /** The shop direction this side lists, or null for all. */
                public @Nullable ShopDirection direction() {
                        return direction;
                }
        }

        // ------------------------------------------------------------------
        // State
        // ------------------------------------------------------------------

        /** JEI's {@code parentScreen} — restored on close. */
        private final Screen parent;
        /** The browser's current results, for JEI's previous/next category. */
        private final List<Variant> cycleItems;

        private Variant variant;
        /** The market's revision the offer cache was built against. */
        private long offersRevision = -1;
        /** The side whose offers the list shows. */
        private Side side = Side.ALL;
        private int page;
        /** The selected offer's shop id — selection is not navigation. */
        private @Nullable String selectedShopId;

        // The town context, resolved when the entry list rebuilds:
        // shop id → town, and shop id → nation. Unknown stays absent.
        private final Map<String, String> townByShop = new HashMap<>();
        private final Map<String, String> nationByShop = new HashMap<>();

        // The cached list entries: header and offer rows, in display order.
        private sealed interface Entry permits HeaderEntry, OfferEntry {
        }
        private record HeaderEntry(String text, int color) implements Entry {
        }
        private record OfferEntry(Offer offer) implements Entry {
        }
        private List<Entry> entries = List.of();
        /** The side the entry cache was built for. */
        private Side entriesSide = Side.ALL;
        private int rowsPerPage = 1;
        private int pageCount = 1;

        // Per-frame geometry, recomputed in init.
        private int guiLeft;
        private int guiTop;
        private int xSize;
        private int ySize;
        private int row1Y;
        private int row2Y;
        private int contentLeft;
        private int contentTop;
        private int contentRight;
        private int contentBottom;
        private int footerTop;

        // Hit boxes, recomputed per frame for tooltips and clicks.
        private int @Nullable [] prevItemBox;
        private int @Nullable [] nextItemBox;
        private int @Nullable [] prevPageBox;
        private int @Nullable [] nextPageBox;
        private int @Nullable [] navigateBox;
        private int @Nullable [] titleBox;
        private final List<int[]> tabBoxes = new ArrayList<>();
        private boolean navigateActive;

        /** The truncated title and its tooltip, rebuilt with the entries. */
        private FormattedText visibleTitle = FormattedText.EMPTY;
        private @Nullable String titleTooltip;

        public MarketViewerScreen(Screen parent, Variant variant, List<Variant> cycleItems) {
                super(Component.literal("ZEA Market"));
                this.parent = parent;
                this.variant = variant;
                this.cycleItems = List.copyOf(cycleItems);
        }

        /**
         * JEI's {@code open()}: capture the current screen as the parent
         * and swap the viewer in — the inventory stays alive behind it
         * and returns when the viewer closes.
         */
        public static void open(Variant variant, List<Variant> cycleItems) {
                Minecraft client = Minecraft.getInstance();
                Screen parent = client.gui.screen();
                client.gui.setScreen(new MarketViewerScreen(parent, variant, cycleItems));
        }

        // ------------------------------------------------------------------
        // Lifecycle — JEI's semantics
        // ------------------------------------------------------------------

        @Override
        public boolean isPauseScreen() {
                return false;
        }

        @Override
        public boolean isInGameUi() {
                return true;
        }

        @Override
        protected void init() {
                updateSize();
        }

        /** JEI's {@code updateSize}: the panel's JEI-default footprint. */
        private void updateSize() {
                int maxWidth = Math.max(MIN_GUI_WIDTH, this.width - 2 * BORDER_PADDING);
                xSize = Math.min(MIN_GUI_WIDTH, maxWidth);
                int idealHeight = this.height - 58;
                int extraSpace = 0;
                ySize = Math.max(MIN_GUI_HEIGHT, idealHeight);
                if (ySize > MAX_GUI_HEIGHT) {
                        extraSpace = ySize - MAX_GUI_HEIGHT;
                        ySize = MAX_GUI_HEIGHT;
                }
                guiLeft = (this.width - xSize) / 2;
                guiTop = TAB_SIZE + 21 + (extraSpace / 2);
                layoutHeader();
                rebuildEntries(true);
        }

        /** JEI's {@code updateNavigation}: the two nav rows inside the frame. */
        private void layoutHeader() {
                int titleHeight = font.lineHeight + BORDER_PADDING;
                row1Y = guiTop + titleHeight - SMALL_BUTTON + NAV_BAR_PADDING;
                row2Y = row1Y + SMALL_BUTTON + NAV_BAR_PADDING;
                contentTop = row2Y + SMALL_BUTTON + NAV_BAR_PADDING;
                contentLeft = guiLeft + BORDER_PADDING;
                contentRight = guiLeft + xSize - BORDER_PADDING;
                contentBottom = guiTop + ySize - BORDER_PADDING;
                footerTop = contentBottom - NAV_BUTTON_HEIGHT;
        }

        @Override
        public void onClose() {
                if (minecraft != null) {
                        minecraft.gui.setScreen(parent);
                } else {
                        super.onClose();
                }
        }

        @Override
        public boolean keyPressed(KeyEvent input) {
                // JEI closes the recipe gui with Esc and with the inventory
                // key — the "Close inventory" command.
                if (input.isEscape()) {
                        onClose();
                        return true;
                }
                if (minecraft != null && minecraft.options.keyInventory.matches(input)) {
                        onClose();
                        return true;
                }
                return super.keyPressed(input);
        }

        // ------------------------------------------------------------------
        // Data preparation
        // ------------------------------------------------------------------

        /**
         * The per-frame cache check: the entries rebuild when the market's
         * revision moved past what they were built against — a still
         * screen does zero index work.
         */
        private void refreshEntries() {
                MarketSubsystem market = ZeaClient.market();
                long revision = market == null ? -1 : market.data().revision();
                if (revision != offersRevision) {
                        rebuildEntries(false);
                }
        }

        /** The offers of one side, in that side's display order. */
        private List<Offer> sideOffers(Side side) {
                ShopDirection direction = side.direction();
                List<Offer> offers = new ArrayList<>();
                for (Offer offer : variant.offers()) {
                        if (direction == null || offer.observation().direction() == direction) {
                                offers.add(offer);
                        }
                }
                if (direction == ShopDirection.SELLING) {
                        // BUY FROM: cheapest first.
                        offers.sort(Comparator.comparing(Offer::unitPrice,
                                        Comparator.nullsLast(Comparator.naturalOrder())));
                } else if (direction == ShopDirection.BUYING) {
                        // SELL TO: highest payout first.
                        offers.sort(Comparator.comparing(Offer::unitPrice,
                                        Comparator.nullsLast(Comparator.reverseOrder())));
                }
                return offers;
        }

        /**
         * Rebuilds the town context, the entry list, the pagination and
         * the truncated title. Called when the variant or the side
         * changes, when the market's revision moves and when the panel's
         * size changes.
         */
        private void rebuildEntries(boolean keepPage) {
                MarketSubsystem market = ZeaClient.market();
                offersRevision = market == null ? -1 : market.data().revision();
                entriesSide = side;
                townByShop.clear();
                nationByShop.clear();
                if (market != null && market.townLookup() != null) {
                        for (Offer offer : variant.offers()) {
                                var resolved = market.townLookup().at(offer.shop().world(),
                                                offer.shop().signPos().getX(), offer.shop().signPos().getZ());
                                if (resolved != null) {
                                        if (resolved.town() != null) {
                                                townByShop.put(offer.shop().shopId(), resolved.town());
                                        }
                                        if (resolved.nation() != null) {
                                                nationByShop.put(offer.shop().shopId(), resolved.nation());
                                        }
                                }
                        }
                }
                List<Entry> built = new ArrayList<>();
                if (side == Side.ALL) {
                        appendSide(built, "BUY FROM — cheapest first", BUY_COLOR, ShopDirection.SELLING);
                        appendSide(built, "SELL TO — highest payout first", SELL_COLOR, ShopDirection.BUYING);
                } else if (side == Side.BUY_FROM) {
                        appendSide(built, "BUY FROM — cheapest first", BUY_COLOR, ShopDirection.SELLING);
                } else {
                        appendSide(built, "SELL TO — highest payout first", SELL_COLOR, ShopDirection.BUYING);
                }
                entries = List.copyOf(built);
                rebuildPagination();
                rebuildTitle();
                if (!keepPage && page >= pageCount) {
                        page = pageCount - 1;
                }
        }

        /** One side's header (when its rows exist) and rows into the list. */
        private void appendSide(List<Entry> into, String header, int color, ShopDirection direction) {
                List<Offer> offers = sideOffers(direction == ShopDirection.SELLING
                                ? Side.BUY_FROM : Side.SELL_TO);
                if (offers.isEmpty()) {
                        return;
                }
                into.add(new HeaderEntry(header, color));
                for (Offer offer : offers) {
                        into.add(new OfferEntry(offer));
                }
        }

        /** The pagination: rows per page from the live list height, pages counted. */
        private void rebuildPagination() {
                int listHeight = footerTop - NAV_BAR_PADDING - listTop();
                rowsPerPage = Math.max(1, listHeight / ROW_HEIGHT);
                pageCount = Math.max(1, (entries.size() + rowsPerPage - 1) / rowsPerPage);
                page = Math.min(page, pageCount - 1);
        }

        /** The list's top: under the identity block. */
        private int listTop() {
                return contentTop + identityHeight() + 6;
        }

        /** The identity block's height: slot, enchantment line and the stat lines. */
        private int identityHeight() {
                int lines = 0;
                VariantPricing.SideStats buyFrom = VariantPricing.stats(variant.offers(), ShopDirection.SELLING);
                VariantPricing.SideStats sellTo = VariantPricing.stats(variant.offers(), ShopDirection.BUYING);
                if (buyFrom.shops() > 0) {
                        lines++;
                }
                if (sellTo.shops() > 0) {
                        lines++;
                }
                // The slot's 18 px plus the summary lines beneath it.
                return 18 + (lines > 0 ? 2 + lines * 10 + 4 : 0);
        }

        /** The header title, truncated with an ellipsis like JEI's title. */
        private void rebuildTitle() {
                int titleWidth = titleAreaWidth();
                FormattedText name = FormattedText.of(variant.display());
                int ellipsisWidth = font.width("...");
                titleTooltip = null;
                if (font.width(name) > titleWidth) {
                        FormattedText cut = font.substrByWidth(name, Math.max(0, titleWidth - ellipsisWidth));
                        visibleTitle = FormattedText.composite(cut, FormattedText.of("..."));
                        titleTooltip = variant.display();
                } else {
                        visibleTitle = name;
                }
        }

        /** The title's width: between the two arrows, cropped like JEI's title area. */
        private int titleAreaWidth() {
                int left = guiLeft + BORDER_PADDING + SMALL_BUTTON + TITLE_INNER_PADDING;
                int right = guiLeft + xSize - BORDER_PADDING - SMALL_BUTTON - TITLE_INNER_PADDING;
                return Math.max(0, right - left);
        }

        /** The current variant's index in the cycle list, or -1. */
        private int cycleIndex() {
                for (int i = 0; i < cycleItems.size(); i++) {
                        if (cycleItems.get(i).token().equals(variant.token())) {
                                return i;
                        }
                }
                return -1;
        }

        /** JEI's previous/next category: cycle the browser's results. */
        private void cycleItem(int delta) {
                int index = cycleIndex();
                if (index < 0 || cycleItems.size() < 2) {
                        return;
                }
                int next = (index + delta + cycleItems.size()) % cycleItems.size();
                selectedShopId = null;
                page = 0;
                variant = cycleItems.get(next);
                rebuildEntries(false);
        }

        // ------------------------------------------------------------------
        // Rendering
        // ------------------------------------------------------------------

        @Override
        public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
                super.extractBackground(graphics, mouseX, mouseY, a);
                drawNineSlice(graphics, PANEL, guiLeft, guiTop, xSize, ySize,
                                PANEL_SPRITE, PANEL_BORDER);
        }

        @Override
        public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
                refreshEntries();
                Font font = this.font;
                tabBoxes.clear();

                drawTabs(graphics, font, mouseX, mouseY);
                drawHeader(graphics, font, mouseX, mouseY);
                drawIdentity(graphics, font, mouseX, mouseY);
                drawOfferList(graphics, font, mouseX, mouseY);
                drawNavigateFooter(graphics, font, mouseX, mouseY);

                drawTooltips(graphics, font, mouseX, mouseY);
        }

        /** JEI's tabs: 24 px plates riding the panel's top edge. */
        private void drawTabs(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
                Side[] sides = Side.values();
                ItemStack[] icons = {
                                new ItemStack(Items.CHEST),
                                new ItemStack(Items.EMERALD),
                                new ItemStack(Items.GOLD_INGOT)
                };
                int tabY = guiTop - (TAB_SIZE - TAB_OVERLAP);
                int x = guiLeft + TAB_INSET;
                for (int i = 0; i < sides.length; i++) {
                        boolean selected = side == sides[i];
                        graphics.blit(selected ? TAB_SELECTED : TAB_UNSELECTED,
                                        x, tabY, x + TAB_SIZE, tabY + TAB_SIZE, 0f, 1f, 0f, 1f);
                        graphics.item(icons[i], x + 4, tabY + 3);
                        tabBoxes.add(new int[] {x, tabY, TAB_SIZE, TAB_SIZE});
                        x += TAB_SIZE;
                }
        }

        /** JEI's header: the two nav rows, the title, the page strip. */
        private void drawHeader(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
                int nextX = guiLeft + xSize - BORDER_PADDING - SMALL_BUTTON;
                boolean canCycle = cycleItems.size() > 1 && cycleIndex() >= 0;
                // Row 1: previous/next item — JEI's previous/next category.
                prevItemBox = drawNavButton(graphics, guiLeft + BORDER_PADDING, row1Y,
                                ARROW_PREVIOUS, mouseX, mouseY, canCycle && cycleIndex() > 0);
                nextItemBox = drawNavButton(graphics, nextX, row1Y,
                                ARROW_NEXT, mouseX, mouseY, canCycle && cycleIndex() < cycleItems.size() - 1);

                // The title, white with shadow, centered between the arrows.
                int titleLeft = guiLeft + BORDER_PADDING + SMALL_BUTTON + TITLE_INNER_PADDING;
                int titleRight = guiLeft + xSize - BORDER_PADDING - SMALL_BUTTON - TITLE_INNER_PADDING;
                int titleWidth = Math.max(0, titleRight - titleLeft);
                FormattedCharSequence title = net.minecraft.locale.Language.getInstance().getVisualOrder(visibleTitle);
                int textWidth = font.width(visibleTitle);
                int textX = titleLeft + (titleWidth - textWidth) / 2;
                int textY = row1Y + (SMALL_BUTTON - font.lineHeight) / 2 + 1;
                graphics.text(font, title, textX, textY, TITLE_TEXT, true);
                titleBox = new int[] {titleLeft, row1Y, titleWidth, SMALL_BUTTON};

                // Row 2: previous/next page — JEI's page arrows with the dark
                // strip and the centered page string between them.
                prevPageBox = drawNavButton(graphics, guiLeft + BORDER_PADDING, row2Y,
                                ARROW_PREVIOUS, mouseX, mouseY, page > 0);
                nextPageBox = drawNavButton(graphics, nextX, row2Y,
                                ARROW_NEXT, mouseX, mouseY, page < pageCount - 1);
                graphics.fill(guiLeft + BORDER_PADDING + SMALL_BUTTON, row2Y,
                                nextX, row2Y + SMALL_BUTTON, PAGE_NAV_BACKGROUND);
                String pageString = (page + 1) + "/" + pageCount;
                int pageWidth = font.width(pageString);
                int pageX = titleLeft + (titleWidth - pageWidth) / 2;
                int pageY = row2Y + (SMALL_BUTTON - font.lineHeight) / 2 + 1;
                graphics.text(font, pageString, pageX, pageY, TITLE_TEXT, true);
        }

        /**
         * One 13 px nav button: JEI's vanilla button plate — the enabled
         * sprite with the centered arrow when active, JEI's disabled
         * plate when not — and the hover plate over the enabled one.
         * Returns the hit box.
         */
        private int @Nullable [] drawNavButton(GuiGraphicsExtractor graphics,
                        int x, int y, Identifier arrow, int mouseX, int mouseY, boolean active) {
                if (!active) {
                        graphics.blit(BUTTON_DISABLED,
                                        x, y, x + SMALL_BUTTON, y + SMALL_BUTTON, 0f, 1f, 0f, 1f);
                        return new int[] {x, y, SMALL_BUTTON, SMALL_BUTTON};
                }
                boolean hovered = mouseX >= x && mouseX < x + SMALL_BUTTON
                                && mouseY >= y && mouseY < y + SMALL_BUTTON;
                graphics.blit(hovered ? BUTTON_HOVER : BUTTON,
                                x, y, x + SMALL_BUTTON, y + SMALL_BUTTON, 0f, 1f, 0f, 1f);
                // The 9×7 arrow centered in the 13 px plate.
                graphics.blit(arrow, x + 2, y + 3, x + 11, y + 10, 0f, 1f, 0f, 1f);
                return new int[] {x, y, SMALL_BUTTON, SMALL_BUTTON};
        }

        /** The identity block: the item's slot, enchantment line and both stat lines. */
        private void drawIdentity(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
                int y = contentTop;
                int x = contentLeft;
                // The vanilla slot plate: dark top/left, white bottom/right.
                graphics.fill(x, y, x + 18, y + 18, SLOT_DARK);
                graphics.fill(x + 1, y + 1, x + 18, y + 18, SLOT_LIGHT);
                graphics.fill(x + 1, y + 1, x + 17, y + 17, SLOT_INTERIOR);
                graphics.item(VariantStacks.of(variant.token()).stack(), x + 1, y + 1);

                int textX = x + 22;
                String sub = variant.enchantments().isEmpty() ? "No enchantments"
                                : String.join(", ", variant.enchantments());
                sub = font.plainSubstrByWidth(sub, contentRight - textX);
                graphics.text(font, sub, textX, y + 1, TEXT, false);

                int lineY = y + 20;
                VariantPricing.SideStats buyFrom = VariantPricing.stats(variant.offers(), ShopDirection.SELLING);
                VariantPricing.SideStats sellTo = VariantPricing.stats(variant.offers(), ShopDirection.BUYING);
                if (buyFrom.shops() > 0) {
                        drawStatLine(graphics, font, "BUY FROM", BUY_COLOR, summaryLine(buyFrom), x, lineY);
                        lineY += 10;
                }
                if (sellTo.shops() > 0) {
                        drawStatLine(graphics, font, "SELL TO", SELL_COLOR, summaryLine(sellTo), x, lineY);
                }
        }

        /** One stat line: the colored tag, then the black summary. */
        private void drawStatLine(GuiGraphicsExtractor graphics, Font font,
                        String tag, int tagColor, String summary, int x, int y) {
                graphics.text(font, tag, x, y, tagColor, false);
                int summaryX = x + font.width(tag) + 6;
                String trimmed = trimSections(font, summary, contentRight - summaryX);
                graphics.text(font, trimmed, summaryX, y, TEXT, false);
        }

        /**
         * Trims a summary to the width, backing the cut up to the last
         * whole section ("·" between facts, ", " between enchantments) —
         * a half-word like "1 owne" reads worse than the section it
         * belongs to, which the tooltip already carries.
         */
        private static String trimSections(Font font, String text, int width) {
                String trimmed = font.plainSubstrByWidth(text, width);
                if (trimmed.length() >= text.length()) {
                        return text;
                }
                int cut = Math.max(trimmed.lastIndexOf('·'), trimmed.lastIndexOf(','));
                if (cut > 0) {
                        trimmed = trimmed.substring(0, cut).stripTrailing();
                        if (!trimmed.isEmpty() && trimmed.charAt(trimmed.length() - 1) == '·') {
                                trimmed = trimmed.substring(0, trimmed.length() - 1).stripTrailing();
                        }
                }
                return trimmed;
        }

        /** The paginated offer list — one offer per row, in JEI's slot language. */
        private void drawOfferList(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
                int start = page * rowsPerPage;
                int end = Math.min(entries.size(), start + rowsPerPage);
                int y = listTop();
                ItemStack icon = VariantStacks.of(variant.token()).stack();
                for (int i = start; i < end; i++) {
                        Entry entry = entries.get(i);
                        if (entry instanceof HeaderEntry header) {
                                graphics.text(font, header.text(), contentLeft, y + 1, header.color(), false);
                                y += 12;
                        } else if (entry instanceof OfferEntry offerEntry) {
                                drawOfferRow(graphics, font, offerEntry.offer(), icon, contentLeft,
                                                contentRight - contentLeft, y, mouseX, mouseY);
                                y += ROW_HEIGHT;
                        }
                }
        }

        /**
         * One offer row: the item in a vanilla slot plate, the owner and
         * unit price on the first line, stock and where on the second, the
         * age right-aligned; the selected row carries ZEA's gold frame,
         * the hovered row a white lift.
         */
        private void drawOfferRow(GuiGraphicsExtractor graphics, Font font, Offer offer,
                        ItemStack icon, int left, int width, int y, int mouseX, int mouseY) {
                boolean selected = offer.shop().shopId().equals(selectedShopId);
                boolean hovered = !selected && mouseX >= left && mouseX < left + width
                                && mouseY >= y && mouseY < y + ROW_HEIGHT - 2;
                if (selected) {
                        graphics.fill(left - 1, y - 1, left + width + 1, y + ROW_HEIGHT - 2, SELECTED_FRAME);
                        graphics.fill(left, y, left + width, y + ROW_HEIGHT - 3, SELECTED_WASH);
                } else if (hovered) {
                        graphics.fill(left, y, left + width, y + ROW_HEIGHT - 2, HOVER_WASH);
                }
                graphics.fill(left, y + 2, left + 18, y + 20, SLOT_DARK);
                graphics.fill(left + 1, y + 3, left + 18, y + 20, SLOT_LIGHT);
                graphics.fill(left + 1, y + 3, left + 17, y + 19, SLOT_INTERIOR);
                graphics.item(icon, left + 1, y + 3);

                int textX = left + 22;
                int textWidth = left + width - textX - 2;

                // Line 1: the owner, the unit price right-aligned.
                String owner = offer.shop().owner() == null ? "?" : offer.shop().owner();
                String price = priceOrDash(offer.unitPrice());
                int priceWidth = font.width(price);
                int ownerWidth = textWidth - priceWidth - 6;
                if (ownerWidth > 8) {
                        String trimmed = font.plainSubstrByWidth(owner, ownerWidth);
                        graphics.text(font, trimmed, textX, y + 3, TEXT, false);
                }
                graphics.text(font, price, left + width - 2 - priceWidth, y + 3, TEXT, false);

                // Line 2: stock and where, the age right-aligned.
                ShopObservation observation = offer.observation();
                String stock = stockLine(observation);
                String where = whereLine(offer);
                String detail = (stock.isEmpty() ? "" : stock + " · ") + where;
                String age = MarketAge.format(observation.observedAt(), Instant.now())
                                + (observation.sourceType() == SourceType.REMOTE_SHARED ? " · shared" : "");
                int ageWidth = font.width(age);
                int detailWidth = textWidth - ageWidth - 8;
                if (detailWidth > 8) {
                        String trimmed = trimSections(font, detail, detailWidth);
                        graphics.text(font, trimmed, textX, y + 12, TEXT_DIM, false);
                }
                graphics.text(font, age, left + width - 2 - ageWidth, y + 12, TEXT_DIM, false);
        }

        /** The footer: JEI's button sprites carrying the one navigation action. */
        private void drawNavigateFooter(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
                navigateActive = selectedShopId != null;
                Component label = Component.literal("Navigate to selected shop");
                int labelWidth = font.width(label);
                int buttonWidth = Math.max(60, labelWidth + 16);
                int buttonX = contentLeft + ((contentRight - contentLeft) - buttonWidth) / 2;
                boolean hovered = navigateActive && mouseX >= buttonX && mouseX < buttonX + buttonWidth
                                && mouseY >= footerTop && mouseY < footerTop + NAV_BUTTON_HEIGHT;
                graphics.blit(navigateActive ? (hovered ? BUTTON_HOVER : BUTTON) : BUTTON_DISABLED,
                                buttonX, footerTop, buttonX + buttonWidth, footerTop + NAV_BUTTON_HEIGHT,
                                0f, 1f, 0f, 1f);
                int textX = buttonX + (buttonWidth - labelWidth) / 2;
                int textY = footerTop + (NAV_BUTTON_HEIGHT - font.lineHeight) / 2 + 1;
                graphics.text(font, label, textX, textY,
                                navigateActive ? BUTTON_TEXT : BUTTON_TEXT_DISABLED, false);
                navigateBox = new int[] {buttonX, footerTop, buttonWidth, NAV_BUTTON_HEIGHT};
        }

        /** The tooltips, JEI's last pass. */
        private void drawTooltips(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
                // The truncated title shows the full name.
                if (titleTooltip != null && titleBox != null && contains(titleBox, mouseX, mouseY)) {
                        graphics.setTooltipForNextFrame(font,
                                        List.of(Component.literal(titleTooltip)),
                                        Optional.empty(), mouseX, mouseY);
                        return;
                }
                // The tabs name their side.
                Side[] sides = Side.values();
                for (int i = 0; i < tabBoxes.size() && i < sides.length; i++) {
                        if (contains(tabBoxes.get(i), mouseX, mouseY)) {
                                graphics.setTooltipForNextFrame(font,
                                                List.of(Component.literal(sides[i].label())),
                                                Optional.empty(), mouseX, mouseY);
                                return;
                        }
                }
                // The arrows name their action.
                if (prevItemBox != null && contains(prevItemBox, mouseX, mouseY)) {
                        graphics.setTooltipForNextFrame(font,
                                        List.of(Component.literal("Previous item")),
                                        Optional.empty(), mouseX, mouseY);
                        return;
                }
                if (nextItemBox != null && contains(nextItemBox, mouseX, mouseY)) {
                        graphics.setTooltipForNextFrame(font,
                                        List.of(Component.literal("Next item")),
                                        Optional.empty(), mouseX, mouseY);
                        return;
                }
                if (prevPageBox != null && contains(prevPageBox, mouseX, mouseY)) {
                        graphics.setTooltipForNextFrame(font,
                                        List.of(Component.literal("Previous page")),
                                        Optional.empty(), mouseX, mouseY);
                        return;
                }
                if (nextPageBox != null && contains(nextPageBox, mouseX, mouseY)) {
                        graphics.setTooltipForNextFrame(font,
                                        List.of(Component.literal("Next page")),
                                        Optional.empty(), mouseX, mouseY);
                        return;
                }
                // A hovered offer row shows the full observation.
                int hoveredRow = rowAt(mouseX, mouseY);
                if (hoveredRow >= 0 && entries.get(hoveredRow) instanceof OfferEntry offerEntry) {
                        graphics.setTooltipForNextFrame(font,
                                        offerTooltip(offerEntry.offer()),
                                        Optional.empty(), mouseX, mouseY);
                }
        }

        /** The visible offer entry under the point, as an entries index, or -1. */
        private int rowAt(int mouseX, int mouseY) {
                int start = page * rowsPerPage;
                int end = Math.min(entries.size(), start + rowsPerPage);
                int y = listTop();
                for (int i = start; i < end; i++) {
                        Entry entry = entries.get(i);
                        if (entry instanceof HeaderEntry) {
                                y += 12;
                        } else if (entry instanceof OfferEntry) {
                                if (mouseY >= y && mouseY < y + ROW_HEIGHT - 2
                                                && mouseX >= contentLeft && mouseX < contentRight) {
                                        return i;
                                }
                                y += ROW_HEIGHT;
                        }
                }
                return -1;
        }

        /** The full observation as a vanilla tooltip. */
        private List<Component> offerTooltip(Offer offer) {
                ShopObservation observation = offer.observation();
                boolean buying = observation.direction() == ShopDirection.SELLING;
                List<Component> lines = new ArrayList<>();
                lines.add(Component.literal((buying ? "[BUY FROM] " : "[SELL TO] ")
                                + (offer.shop().owner() == null ? "?" : offer.shop().owner()))
                                .withStyle(style -> style.withColor(buying ? 0xFFFFAA55 : 0xFF55FFAA)));
                lines.add(Component.literal("Unit price: " + priceOrDash(offer.unitPrice())));
                String stock = stockLine(observation);
                if (!stock.isEmpty()) {
                        lines.add(Component.literal(stock));
                }
                if (observation.priceScope() == net.zaminmc.zea.market.model.PriceScope.PER_BULK
                                && observation.bulkSize() != null) {
                        lines.add(Component.literal("Bulk: " + observation.bulkSize() + "x "
                                        + observation.price() + "G"));
                }
                String town = townByShop.get(offer.shop().shopId());
                String nation = nationByShop.get(offer.shop().shopId());
                if (town != null) {
                        lines.add(Component.literal(town + (nation == null ? "" : " (" + nation + ")")));
                }
                lines.add(Component.literal(offer.shop().signPos().getX() + ", "
                                + offer.shop().signPos().getY() + ", "
                                + offer.shop().signPos().getZ())
                                .withStyle(style -> style.withColor(0xFF909090)));
                lines.add(Component.literal("Observed "
                                + MarketAge.format(observation.observedAt(), Instant.now())
                                + (observation.sourceType() == SourceType.REMOTE_SHARED ? " · shared" : ""))
                                .withStyle(style -> style.withColor(0xFF909090)));
                lines.add(Component.literal(""));
                lines.add(Component.literal("LMB : Select offer!").withStyle(
                                style -> style.withColor(0xFFFFFF55).withBold(true)));
                return lines;
        }

        // ------------------------------------------------------------------
        // Input
        // ------------------------------------------------------------------

        @Override
        public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
                int mouseX = (int) click.x();
                int mouseY = (int) click.y();
                // The tabs switch the side.
                Side[] sides = Side.values();
                for (int i = 0; i < tabBoxes.size() && i < sides.length; i++) {
                        if (contains(tabBoxes.get(i), mouseX, mouseY)) {
                                if (side != sides[i]) {
                                        side = sides[i];
                                        page = 0;
                                        selectedShopId = null;
                                        rebuildEntries(false);
                                }
                                playPressSound();
                                return true;
                        }
                }
                // JEI's previous/next category.
                boolean canCycle = cycleItems.size() > 1 && cycleIndex() >= 0;
                if (prevItemBox != null && contains(prevItemBox, mouseX, mouseY)
                                && canCycle && cycleIndex() > 0) {
                        cycleItem(-1);
                        playPressSound();
                        return true;
                }
                if (nextItemBox != null && contains(nextItemBox, mouseX, mouseY)
                                && canCycle && cycleIndex() < cycleItems.size() - 1) {
                        cycleItem(1);
                        playPressSound();
                        return true;
                }
                // The page arrows.
                if (prevPageBox != null && contains(prevPageBox, mouseX, mouseY) && page > 0) {
                        setPage(page - 1);
                        playPressSound();
                        return true;
                }
                if (nextPageBox != null && contains(nextPageBox, mouseX, mouseY)
                                && page < pageCount - 1) {
                        setPage(page + 1);
                        playPressSound();
                        return true;
                }
                // An offer row: select it; a selection never issues a command.
                int hovered = rowAt(mouseX, mouseY);
                if (hovered >= 0 && entries.get(hovered) instanceof OfferEntry offerEntry) {
                        String shopId = offerEntry.offer().shop().shopId();
                        selectedShopId = shopId.equals(selectedShopId) ? null : shopId;
                        playPressSound();
                        return true;
                }
                // The footer's one action.
                if (navigateBox != null && contains(navigateBox, mouseX, mouseY) && navigateActive) {
                        navigateToSelected();
                        return true;
                }
                return super.mouseClicked(click, doubled);
        }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount,
                        double verticalAmount) {
                // The wheel turns the list's page, the browser pane's paging.
                if (pageCount > 1 && mouseX >= guiLeft && mouseX < guiLeft + xSize
                                && mouseY >= guiTop && mouseY < guiTop + ySize) {
                        setPage(page + (verticalAmount < 0 ? 1 : -1));
                        return true;
                }
                return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        }

        private void setPage(int page) {
                this.page = Math.clamp(page, 0, pageCount - 1);
        }

        /** The one action: navigate to the selected shop. */
        private void navigateToSelected() {
                if (selectedShopId == null) {
                        return;
                }
                for (Entry entry : entries) {
                        if (entry instanceof OfferEntry offerEntry
                                        && offerEntry.offer().shop().shopId().equals(selectedShopId)) {
                                String shopId = selectedShopId;
                                ShopNavigation.navigate(offerEntry.offer(),
                                                townByShop.get(shopId), nationByShop.get(shopId));
                                return;
                        }
                }
        }

        // ------------------------------------------------------------------
        // Shared drawing helpers and formatting
        // ------------------------------------------------------------------

        /** A nine-slice blit: ZEA's three-by-three pass, the sprite's border fractions. */
        private static void drawNineSlice(GuiGraphicsExtractor graphics, Identifier texture,
                        int x, int y, int width, int height, int spriteSize, int border) {
                float slice = (float) border / spriteSize;
                for (int yIndex = 0; yIndex <= 2; yIndex++) {
                        for (int xIndex = 0; xIndex <= 2; xIndex++) {
                                float uMin = 0;
                                float uMax = slice;
                                int partX = x;
                                int partW = border;
                                if (xIndex == 1) {
                                        partX = x + border;
                                        uMin = slice;
                                        uMax = 1 - slice;
                                        partW = width - 2 * border;
                                } else if (xIndex == 2) {
                                        partX = x + width - border;
                                        uMin = 1 - slice;
                                        uMax = 1f;
                                }
                                float vMin = 0;
                                float vMax = slice;
                                int partY = y;
                                int partH = border;
                                if (yIndex == 1) {
                                        partY = y + border;
                                        vMin = slice;
                                        vMax = 1 - slice;
                                        partH = height - 2 * border;
                                } else if (yIndex == 2) {
                                        partY = y + height - border;
                                        vMin = 1 - slice;
                                        vMax = 1f;
                                }
                                graphics.blit(texture, partX, partY, partX + partW, partY + partH,
                                                uMin, uMax, vMin, vMax);
                        }
                }
        }

        private static boolean contains(int @Nullable [] box, int x, int y) {
                return box != null && x >= box[0] && x < box[0] + box[2]
                                && y >= box[1] && y < box[1] + box[3];
        }

        private static void playPressSound() {
                Minecraft.getInstance().getSoundManager().play(
                                net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                                                net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
        }

        private static String priceOrDash(@Nullable BigDecimal price) {
                return price == null ? "—" : format(price);
        }

        private static String format(BigDecimal price) {
                return price.setScale(2, java.math.RoundingMode.HALF_UP)
                                .stripTrailingZeros().toPlainString() + "G";
        }

        private static String summaryLine(VariantPricing.SideStats stats) {
                String line = "best " + priceOrDash(stats.best());
                if (stats.median() != null) {
                        line += " · median " + format(stats.median());
                }
                return line + " · " + stats.shops() + " shop" + (stats.shops() == 1 ? "" : "s")
                                + " · " + stats.owners() + " owner" + (stats.owners() == 1 ? "" : "s");
        }

        private static String stockLine(ShopObservation observation) {
                if (observation.stockRaw() == null) {
                        return "";
                }
                String unit = switch (observation.stockUnit()) {
                        case SPACE -> " space";
                        case ITEMS -> " items";
                        case STACKS -> " stacks";
                        case UNKNOWN -> "";
                };
                return "Stock: " + observation.stockRaw() + unit
                                + (observation.maxBuyItems() != null ? " · can buy " + observation.maxBuyItems()
                                : observation.maxBuyBulks() != null ? " · can buy " + observation.maxBuyBulks() + " bulks"
                                : observation.maxSellItems() != null ? " · takes " + observation.maxSellItems()
                                : "");
        }

        private String whereLine(Offer offer) {
                String town = townByShop.get(offer.shop().shopId());
                String nation = nationByShop.get(offer.shop().shopId());
                int x = offer.shop().signPos().getX();
                int z = offer.shop().signPos().getZ();
                String coords = x + ", " + offer.shop().signPos().getY() + ", " + z;
                String where = town == null ? coords : town + (nation == null ? "" : " (" + nation + ")");
                return where + " · " + coords;
        }
}
