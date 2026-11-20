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
import net.zaminmc.zea.ZeaClient;
import net.zaminmc.zea.browser.VariantStacks;
import net.zaminmc.zea.earthmc.ServerIdentity;
import net.zaminmc.zea.market.MarketSubsystem;
import net.zaminmc.zea.market.index.ItemVariantIndex.Offer;
import net.zaminmc.zea.market.index.ItemVariantIndex.Variant;
import net.zaminmc.zea.market.index.VariantPricing;
import net.zaminmc.zea.market.model.PhysicalShop;
import net.zaminmc.zea.market.model.ShopDirection;
import net.zaminmc.zea.market.model.ShopObservation;
import net.zaminmc.zea.market.model.SourceType;
import net.zaminmc.zea.market.ui.MarketAge;
import net.zaminmc.zea.nav.ShopDestination;
import net.zaminmc.zea.nav.ShopNavigation;
import net.zaminmc.zea.nav.ShopNavigator;
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
 * live market, now laid out around one shop at a time.
 *
 * <p>The frame is still JEI's: the {@code gui_background_v2} nine-slice
 * panel, the vanilla-look button plates carrying JEI's 9×7 arrows, the
 * dark strip between them, JEI's own textures copied unmodified (MIT;
 * the attribution lives in {@code assets/zea/textures/market/
 * NOTICE.txt}). The category tabs are gone — the two market sides are
 * the top bar itself, a dark strip reading <em>Selling Shops</em> or
 * <em>Buying Shops</em> with JEI's arrows flipping between them, and a
 * side is only reachable when shops serve it. Under it a count line
 * names the side's seller or buyer count with the item, and the two
 * aggregate stat lines keep both sides' best and median prices on
 * screen at once.
 *
 * <p>The content is a carousel, not a list: one offer at a time, the
 * item in a large vanilla slot plate flanked by the previous/next shop
 * arrows, its enchantments beneath, then the shop's own facts as
 * labeled lines — seller, price per item, stock, bulk terms, where,
 * observed — and a "shop n of m" position line above the footer. The
 * mouse wheel turns the carousel; so do the arrows, wrapping at the
 * ends. The footer carries the one action — <em>Teleport</em> — which
 * runs the current listing's spawn command through {@link
 * ShopNavigation}; it is disabled, with the reason on hover, whenever
 * the navigator has no spawn command for that listing. A market with no
 * shops for the item says so in an empty carousel.
 *
 * <p>Opening, closing, the pause and in-game semantics stay JEI's:
 * {@code open()} swaps the viewer over the live inventory, Esc and the
 * inventory key restore the parent. Rendering reads caches only: the
 * offer lists and the teleport gate are rebuilt when the variant, the
 * side or the market revision changes — a still screen does zero index
 * work, and a shop flip only re-plans the teleport.
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

        // ------------------------------------------------------------------
        // JEI's geometry
        // ------------------------------------------------------------------

        /** JEI's {@code borderPadding}. */
        private static final int BORDER_PADDING = 6;
        /** JEI's {@code navBarPadding}. */
        private static final int NAV_BAR_PADDING = 2;
        /** JEI's {@code smallButtonWidth}/{@code smallButtonHeight}. */
        private static final int SMALL_BUTTON = 13;
        /** JEI's {@code minRecipeGuiWidth}. */
        private static final int MIN_GUI_WIDTH = 198;
        /** JEI's {@code minRecipeGuiHeight}. */
        private static final int MIN_GUI_HEIGHT = 175;
        /** JEI's {@code defaultRecipeGuiHeight}. */
        private static final int MAX_GUI_HEIGHT = 350;
        /** JEI's {@code guiTop}'s fixed part: the old tab band's lower half. */
        private static final int TOP_MARGIN = 21;
        /** The nine-slice sprite facts: the panel sprite is 64 px with a 16 px border. */
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
        /** ZEA's side tags, darkened for the light panel: the selling
         * side orange (it was BUY FROM's), the buying side green. */
        private static final int SELLING_COLOR = 0xFF9C5000;
        private static final int BUYING_COLOR = 0xFF0F7A3C;
        /** The vanilla slot plate: dark top/left, white bottom/right, gray interior. */
        private static final int SLOT_DARK = 0xFF373737;
        private static final int SLOT_LIGHT = 0xFFFFFFFF;
        private static final int SLOT_INTERIOR = 0xFF8B8B8B;
        /** The vanilla button label colors. */
        private static final int BUTTON_TEXT = 0xFFE0E0E0;
        private static final int BUTTON_TEXT_DISABLED = 0xFFA0A0A0;

        /** The carousel's large slot plate, one vanilla slot scaled up. */
        private static final int PLATE_SIZE = 24;
        /** The gap between the plate and its flanking arrows. */
        private static final int PLATE_ARROW_GAP = 8;
        /** The detail lines' left inset — the block reads centered under the plate. */
        private static final int DETAIL_INSET = 14;
        /** One detail line's height. */
        private static final int LINE_HEIGHT = 10;
        /** The most detail lines any listing can produce. */
        private static final int DETAIL_MAX = 6;
        /** The footer button's height. */
        private static final int NAV_BUTTON_HEIGHT = 16;

        // ------------------------------------------------------------------
        // The market sides — the top bar itself
        // ------------------------------------------------------------------

        /** A side of the market: the shops that sell the item, or the
         * shops that buy it. A side is only reachable when shops serve it. */
        public enum Side {
                SELLING_SHOPS("Selling Shops", ShopDirection.SELLING,
                                "Shops that sell this item — you buy from them"),
                BUYING_SHOPS("Buying Shops", ShopDirection.BUYING,
                                "Shops that buy this item — you sell to them");

                private final String label;
                private final @Nullable ShopDirection direction;
                private final String description;

                Side(String label, @Nullable ShopDirection direction, String description) {
                        this.label = label;
                        this.direction = direction;
                        this.description = description;
                }

                /** The strip's text. */
                public String label() {
                        return label;
                }

                /** The shop direction this side lists. */
                public ShopDirection direction() {
                        return direction;
                }

                /** The strip's tooltip. */
                public String description() {
                        return description;
                }

                /** The side's other half. */
                public Side other() {
                        return this == SELLING_SHOPS ? BUYING_SHOPS : SELLING_SHOPS;
                }

                /** The noun for one shop on this side: seller or buyer. */
                public String noun() {
                        return this == SELLING_SHOPS ? "seller" : "buyer";
                }
        }

        /** One labeled detail line under the plate: its text and color. */
        record DetailLine(String text, int color) {
        }

        // ------------------------------------------------------------------
        // State
        // ------------------------------------------------------------------

        /** JEI's {@code parentScreen} — restored on close. */
        private final Screen parent;

        private Variant variant;
        /** The market's revision the caches were built against. */
        private long offersRevision = -1;
        /** The side whose shops the carousel shows. */
        private Side side = Side.SELLING_SHOPS;
        /** The carousel position within the current side's offers. */
        private int listingIndex;

        // The current side's offers, in that side's display order, and
        // the other side's count — the flip arrows read it. Both sides'
        // sorted lists and pricing summaries are cached: a still screen
        // does zero index work.
        private List<Offer> currentOffers = List.of();
        private List<Offer> sellingOffers = List.of();
        private List<Offer> buyingOffers = List.of();
        private int otherSideCount;
        private VariantPricing.SideStats sellingStats = VariantPricing.stats(List.of(), ShopDirection.SELLING);
        private VariantPricing.SideStats buyingStats = VariantPricing.stats(List.of(), ShopDirection.BUYING);

        // The town context, resolved when the caches rebuild:
        // shop id → town, and shop id → nation. Unknown stays absent.
        private final Map<String, String> townByShop = new HashMap<>();
        private final Map<String, String> nationByShop = new HashMap<>();

        // The teleport gate for the current listing: the spawn command the
        // navigator plans, or why it plans none. Re-planned on every flip.
        private @Nullable String teleportCommand;
        private @Nullable String teleportReason;

        // The count line and its tooltip, rebuilt with the caches.
        private String countText = "";
        private @Nullable String countTooltip;

        // Per-frame geometry, recomputed in relayout.
        private int guiLeft;
        private int guiTop;
        private int xSize;
        private int ySize;
        private int row1Y;
        private int row2Y;
        private int statY;
        private int carouselTop;
        private int contentLeft;
        private int contentRight;
        private int positionY;
        private int footerTop;

        // Hit boxes, recomputed per frame for tooltips and clicks.
        private int @Nullable [] prevSideBox;
        private int @Nullable [] nextSideBox;
        private int @Nullable [] prevShopBox;
        private int @Nullable [] nextShopBox;
        private int @Nullable [] countBox;
        private int @Nullable [] plateBox;
        private int @Nullable [] teleportBox;

        public MarketViewerScreen(Screen parent, Variant variant) {
                super(Component.literal("ZEA Market"));
                this.parent = parent;
                this.variant = variant;
        }

        /**
         * JEI's {@code open()}: capture the current screen as the parent
         * and swap the viewer in — the inventory stays alive behind it
         * and returns when the viewer closes.
         */
        public static void open(Variant variant) {
                Minecraft client = Minecraft.getInstance();
                Screen parent = client.gui.screen();
                client.gui.setScreen(new MarketViewerScreen(parent, variant));
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
                rebuildCaches(true);
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
         * The per-frame cache check: the caches rebuild when the market's
         * revision moved past what they were built against — a still
         * screen does zero index work.
         */
        private void refreshCaches() {
                MarketSubsystem market = ZeaClient.market();
                long revision = market == null ? -1 : market.data().revision();
                if (revision != offersRevision) {
                        rebuildCaches(false);
                }
        }

        /** The offers of one side, in that side's display order. */
        private static List<Offer> sideOffers(List<Offer> offers, ShopDirection direction) {
                List<Offer> side = new ArrayList<>();
                for (Offer offer : offers) {
                        if (offer.observation().direction() == direction) {
                                side.add(offer);
                        }
                }
                if (direction == ShopDirection.SELLING) {
                        // Selling shops: cheapest first.
                        side.sort(Comparator.comparing(Offer::unitPrice,
                                        Comparator.nullsLast(Comparator.naturalOrder())));
                } else {
                        // Buying shops: highest payout first.
                        side.sort(Comparator.comparing(Offer::unitPrice,
                                        Comparator.nullsLast(Comparator.reverseOrder())));
                }
                return side;
        }

        /**
         * Rebuilds the town context, the side offers, the carousel
         * position, the teleport gate and the geometry. Called when the
         * variant changes, when the market's revision moves, when the
         * side flips and when the screen initializes.
         */
        private void rebuildCaches(boolean keepPosition) {
                MarketSubsystem market = ZeaClient.market();
                offersRevision = market == null ? -1 : market.data().revision();
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
                List<Offer> selling = sideOffers(variant.offers(), ShopDirection.SELLING);
                List<Offer> buying = sideOffers(variant.offers(), ShopDirection.BUYING);
                sellingOffers = selling;
                buyingOffers = buying;
                sellingStats = VariantPricing.stats(variant.offers(), ShopDirection.SELLING);
                buyingStats = VariantPricing.stats(variant.offers(), ShopDirection.BUYING);
                List<Offer> current = side == Side.SELLING_SHOPS ? selling : buying;
                if (current.isEmpty()) {
                        // The side's shops are gone; when the other side
                        // still serves the item it becomes the shown side.
                        List<Offer> other = side.other() == Side.SELLING_SHOPS ? selling : buying;
                        if (!other.isEmpty()) {
                                side = side.other();
                                current = other;
                        }
                }
                currentOffers = current;
                otherSideCount = (side == Side.SELLING_SHOPS ? buying : selling).size();
                if (!keepPosition) {
                        listingIndex = 0;
                }
                if (listingIndex >= currentOffers.size()) {
                        listingIndex = Math.max(0, currentOffers.size() - 1);
                }
                updateTeleportGate();
                relayout();
                rebuildCountLine();
        }

        /** The count line: the side's shop count with the item name. */
        private void rebuildCountLine() {
                countText = countLine(side, currentOffers.size(), variant.display());
                countTooltip = null;
                int width = contentRight - contentLeft;
                if (font.width(countText) > width) {
                        countText = font.plainSubstrByWidth(countText, Math.max(0, width - font.width("..."))) + "...";
                        countTooltip = countLine(side, currentOffers.size(), variant.display());
                }
        }

        /** The teleport gate for the current listing: the planned spawn
         * command, or the reason there is none. */
        private void updateTeleportGate() {
                teleportCommand = null;
                teleportReason = null;
                if (currentOffers.isEmpty() || minecraft == null || minecraft.player == null) {
                        return;
                }
                Offer offer = currentOffers.get(listingIndex);
                PhysicalShop shop = offer.shop();
                String serverId = ZeaClient.lifecycle().earthMcServer()
                                .map(ServerIdentity::host).orElse("unknown");
                ShopDestination destination = destinationOf(shop,
                                townByShop.get(shop.shopId()), nationByShop.get(shop.shopId()), serverId);
                String playerNation = ShopNavigation.playerNation(shop.world());
                ShopNavigator.Plan plan = ShopNavigator.plan(destination, playerNation, shop.world());
                if (plan.spawnCommand() != null) {
                        teleportCommand = plan.spawnCommand();
                } else {
                        teleportReason = teleportBlockReason(destination, playerNation);
                }
        }

        /** The destination the navigator plans against — the chest when
         * one was verified, the sign otherwise. */
        private static ShopDestination destinationOf(PhysicalShop shop,
                        @Nullable String town, @Nullable String nation, String serverId) {
                return new ShopDestination(shop.shopId(), serverId, shop.world(),
                                shop.chestPos() != null ? shop.chestPos() : shop.signPos(),
                                shop.chestPos(), shop.owner(), town, nation, Instant.now());
        }

        /**
         * Why the navigator plans no spawn command, in the viewer's own
         * words. Mirrors the navigator's two no-command branches for this
         * call pattern — the gate itself stays the plan's decision.
         */
        static String teleportBlockReason(ShopDestination destination, @Nullable String playerNation) {
                if (playerNation != null && destination.nation() != null
                                && playerNation.equalsIgnoreCase(destination.nation())) {
                        return "No spawn needed — you are already in " + destination.nation();
                }
                return "No spawn command — this shop's town and nation are unknown";
        }

        // ------------------------------------------------------------------
        // Geometry — the panel sizes to its content, not to a list
        // ------------------------------------------------------------------

        /** The panel's footprint and row origins, from the content's height. */
        private void relayout() {
                xSize = Math.min(MIN_GUI_WIDTH,
                                Math.max(MIN_GUI_WIDTH, this.width - 2 * BORDER_PADDING));
                int statLines = (sellingOffers.isEmpty() ? 0 : 1) + (buyingOffers.isEmpty() ? 0 : 1);
                int contentHeight = 4 + SMALL_BUTTON + NAV_BAR_PADDING + LINE_HEIGHT
                                + NAV_BAR_PADDING + statLines * LINE_HEIGHT
                                + 6 + PLATE_SIZE + NAV_BAR_PADDING + LINE_HEIGHT
                                + NAV_BAR_PADDING + DETAIL_MAX * LINE_HEIGHT
                                + NAV_BAR_PADDING + LINE_HEIGHT
                                + 6 + NAV_BUTTON_HEIGHT + BORDER_PADDING;
                ySize = Math.clamp(contentHeight, MIN_GUI_HEIGHT, MAX_GUI_HEIGHT);
                int idealHeight = this.height - 58;
                int extraSpace = Math.max(0, idealHeight - ySize);
                guiTop = Math.max(4, TOP_MARGIN + extraSpace / 2);
                guiLeft = (this.width - xSize) / 2;
                row1Y = guiTop + 4;
                row2Y = row1Y + SMALL_BUTTON + NAV_BAR_PADDING;
                statY = row2Y + LINE_HEIGHT + NAV_BAR_PADDING;
                carouselTop = statY + statLines * LINE_HEIGHT + 6;
                contentLeft = guiLeft + BORDER_PADDING;
                contentRight = guiLeft + xSize - BORDER_PADDING;
                footerTop = guiTop + ySize - BORDER_PADDING - NAV_BUTTON_HEIGHT;
                positionY = footerTop - LINE_HEIGHT - 6;
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
                refreshCaches();
                Font font = this.font;

                drawSideBar(graphics, font, mouseX, mouseY);
                drawCountLine(graphics, font);
                drawStats(graphics, font);
                if (currentOffers.isEmpty()) {
                        drawEmptyCarousel(graphics, font);
                } else {
                        drawCarousel(graphics, font, mouseX, mouseY);
                }
                drawTeleportFooter(graphics, font, mouseX, mouseY);
                drawTooltips(graphics, font, mouseX, mouseY);
        }

        /**
         * The top bar: the side strip — a dark strip reading Selling
         * Shops or Buying Shops between JEI's arrows, which flip to the
         * other side only when shops serve it.
         */
        private void drawSideBar(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
                int nextX = guiLeft + xSize - BORDER_PADDING - SMALL_BUTTON;
                boolean otherServes = otherSideCount > 0;
                prevSideBox = drawNavButton(graphics, guiLeft + BORDER_PADDING, row1Y,
                                ARROW_PREVIOUS, mouseX, mouseY, otherServes);
                nextSideBox = drawNavButton(graphics, nextX, row1Y,
                                ARROW_NEXT, mouseX, mouseY, otherServes);
                int stripLeft = guiLeft + BORDER_PADDING + SMALL_BUTTON;
                int stripRight = nextX;
                graphics.fill(stripLeft, row1Y, stripRight, row1Y + SMALL_BUTTON, PAGE_NAV_BACKGROUND);
                String label = side.label();
                int textX = stripLeft + ((stripRight - stripLeft) - font.width(label)) / 2;
                int textY = row1Y + (SMALL_BUTTON - font.lineHeight) / 2 + 1;
                graphics.text(font, label, textX, textY, TITLE_TEXT, true);
        }

        /** The count line: the side's shop count with the item, centered. */
        private void drawCountLine(GuiGraphicsExtractor graphics, Font font) {
                FormattedCharSequence text = net.minecraft.locale.Language.getInstance()
                                .getVisualOrder(FormattedText.of(countText));
                int textX = guiLeft + (xSize - font.width(countText)) / 2;
                graphics.text(font, text, textX, row2Y + 1, TEXT, false);
                countBox = new int[] {contentLeft, row2Y, contentRight - contentLeft, LINE_HEIGHT};
        }

        /** The two aggregate stat lines, the side tags in their colors. */
        private void drawStats(GuiGraphicsExtractor graphics, Font font) {
                int y = statY;
                if (!sellingOffers.isEmpty()) {
                        drawStatLine(graphics, font, "SELLING", SELLING_COLOR,
                                        summaryLine(sellingStats, sellingOffers.size()), y);
                        y += LINE_HEIGHT;
                }
                if (!buyingOffers.isEmpty()) {
                        drawStatLine(graphics, font, "BUYING", BUYING_COLOR,
                                        summaryLine(buyingStats, buyingOffers.size()), y);
                }
        }

        /** One stat line: the colored tag, then the black summary. */
        private void drawStatLine(GuiGraphicsExtractor graphics, Font font,
                        String tag, int tagColor, String summary, int y) {
                graphics.text(font, tag, contentLeft, y, tagColor, false);
                int summaryX = contentLeft + font.width(tag) + 6;
                String trimmed = trimSections(font, summary, contentRight - summaryX);
                graphics.text(font, trimmed, summaryX, y, TEXT, false);
        }

        /** The empty carousel: a market with no shops for this item. */
        private void drawEmptyCarousel(GuiGraphicsExtractor graphics, Font font) {
                String empty = "No shops for this item yet";
                int textX = guiLeft + (xSize - font.width(empty)) / 2;
                graphics.text(font, empty, textX, carouselTop + 20, TEXT_DIM, false);
        }

        /**
         * The carousel: the item in a large slot plate flanked by the
         * previous/next shop arrows, the enchantment line beneath, the
         * shop's labeled facts, and the position line above the footer.
         */
        private void drawCarousel(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
                Offer offer = currentOffers.get(listingIndex);
                int plateX = guiLeft + (xSize - PLATE_SIZE) / 2;
                int arrowY = carouselTop + (PLATE_SIZE - SMALL_BUTTON) / 2;
                boolean canFlip = currentOffers.size() > 1;
                prevShopBox = drawNavButton(graphics, plateX - PLATE_ARROW_GAP - SMALL_BUTTON, arrowY,
                                ARROW_PREVIOUS, mouseX, mouseY, canFlip);
                nextShopBox = drawNavButton(graphics, plateX + PLATE_SIZE + PLATE_ARROW_GAP, arrowY,
                                ARROW_NEXT, mouseX, mouseY, canFlip);

                // The large vanilla slot plate: dark top/left, white
                // bottom/right, gray interior, the item centered.
                graphics.fill(plateX, carouselTop, plateX + PLATE_SIZE, carouselTop + PLATE_SIZE, SLOT_DARK);
                graphics.fill(plateX + 1, carouselTop + 1, plateX + PLATE_SIZE, carouselTop + PLATE_SIZE, SLOT_LIGHT);
                graphics.fill(plateX + 1, carouselTop + 1, plateX + PLATE_SIZE - 1, carouselTop + PLATE_SIZE - 1,
                                SLOT_INTERIOR);
                graphics.item(VariantStacks.of(variant.token()).stack(), plateX + 4, carouselTop + 4);
                plateBox = new int[] {plateX, carouselTop, PLATE_SIZE, PLATE_SIZE};

                String enchant = enchantLine(variant.enchantments());
                enchant = trimSections(font, enchant, contentRight - contentLeft);
                int enchantX = guiLeft + (xSize - font.width(enchant)) / 2;
                graphics.text(font, enchant, enchantX, carouselTop + PLATE_SIZE + NAV_BAR_PADDING, TEXT_DIM, false);

                int detailsY = carouselTop + PLATE_SIZE + NAV_BAR_PADDING + LINE_HEIGHT + NAV_BAR_PADDING;
                int detailX = contentLeft + DETAIL_INSET;
                int slots = Math.max(0, (positionY - NAV_BAR_PADDING - detailsY) / LINE_HEIGHT);
                List<DetailLine> lines = detailLines(offer, townByShop.get(offer.shop().shopId()),
                                nationByShop.get(offer.shop().shopId()), Instant.now());
                int shown = Math.min(lines.size(), slots);
                for (int i = 0; i < shown; i++) {
                        DetailLine line = lines.get(i);
                        String trimmed = trimSections(font, line.text(),
                                        Math.max(0, contentRight - detailX));
                        graphics.text(font, trimmed, detailX, detailsY + i * LINE_HEIGHT, line.color(), false);
                }

                String position = "Shop " + (listingIndex + 1) + " of " + currentOffers.size();
                int positionX = guiLeft + (xSize - font.width(position)) / 2;
                graphics.text(font, position, positionX, positionY, TEXT_DIM, false);
        }

        /** The footer: JEI's button sprites carrying the one action —
         * Teleport — active only when the navigator plans a spawn
         * command for the current listing. */
        private void drawTeleportFooter(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
                teleportBox = drawFooterButton(graphics, font, "Teleport", mouseX, mouseY, teleportCommand != null);
        }

        /** One vanilla-look footer button, centered; returns the hit box. */
        private int @Nullable [] drawFooterButton(GuiGraphicsExtractor graphics, Font font, String label,
                        int mouseX, int mouseY, boolean active) {
                int labelWidth = font.width(label);
                int buttonWidth = Math.max(60, labelWidth + 16);
                int buttonX = contentLeft + ((contentRight - contentLeft) - buttonWidth) / 2;
                boolean hovered = active && mouseX >= buttonX && mouseX < buttonX + buttonWidth
                                && mouseY >= footerTop && mouseY < footerTop + NAV_BUTTON_HEIGHT;
                graphics.blit(active ? (hovered ? BUTTON_HOVER : BUTTON) : BUTTON_DISABLED,
                                buttonX, footerTop, buttonX + buttonWidth, footerTop + NAV_BUTTON_HEIGHT,
                                0f, 1f, 0f, 1f);
                int textX = buttonX + (buttonWidth - labelWidth) / 2;
                int textY = footerTop + (NAV_BUTTON_HEIGHT - font.lineHeight) / 2 + 1;
                graphics.text(font, label, textX, textY, active ? BUTTON_TEXT : BUTTON_TEXT_DISABLED, false);
                return new int[] {buttonX, footerTop, buttonWidth, NAV_BUTTON_HEIGHT};
        }

        /** The tooltips, JEI's last pass. */
        private void drawTooltips(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
                // The side strip names what it shows.
                if (inStrip(mouseX, mouseY)) {
                        graphics.setTooltipForNextFrame(font,
                                        List.of(Component.literal(side.description())),
                                        Optional.empty(), mouseX, mouseY);
                        return;
                }
                // The truncated count line shows the full text.
                if (countTooltip != null && contains(countBox, mouseX, mouseY)) {
                        graphics.setTooltipForNextFrame(font,
                                        List.of(Component.literal(countTooltip)),
                                        Optional.empty(), mouseX, mouseY);
                        return;
                }
                // The plate names the item.
                if (contains(plateBox, mouseX, mouseY)) {
                        List<Component> lines = new ArrayList<>();
                        lines.add(Component.literal(variant.display()));
                        for (String enchant : variant.enchantments()) {
                                lines.add(Component.literal(enchant)
                                                .withStyle(style -> style.withColor(0xFF5555FF)));
                        }
                        graphics.setTooltipForNextFrame(font, lines, Optional.empty(), mouseX, mouseY);
                        return;
                }
                // The arrows name their action.
                if (contains(prevSideBox, mouseX, mouseY)) {
                        graphics.setTooltipForNextFrame(font,
                                        List.of(Component.literal(side.other().description())),
                                        Optional.empty(), mouseX, mouseY);
                        return;
                }
                if (contains(nextSideBox, mouseX, mouseY)) {
                        graphics.setTooltipForNextFrame(font,
                                        List.of(Component.literal(side.other().description())),
                                        Optional.empty(), mouseX, mouseY);
                        return;
                }
                if (contains(prevShopBox, mouseX, mouseY)) {
                        graphics.setTooltipForNextFrame(font,
                                        List.of(Component.literal("Previous shop")),
                                        Optional.empty(), mouseX, mouseY);
                        return;
                }
                if (contains(nextShopBox, mouseX, mouseY)) {
                        graphics.setTooltipForNextFrame(font,
                                        List.of(Component.literal("Next shop")),
                                        Optional.empty(), mouseX, mouseY);
                        return;
                }
                // The Teleport plate: the command it runs, or why it can't.
                if (contains(teleportBox, mouseX, mouseY)) {
                        String line = teleportCommand != null ? "Runs /" + teleportCommand : teleportReason;
                        if (line != null) {
                                graphics.setTooltipForNextFrame(font,
                                                List.of(Component.literal(line)),
                                                Optional.empty(), mouseX, mouseY);
                        }
                }
        }

        /** Whether the point is over the side strip's text area. */
        private boolean inStrip(int mouseX, int mouseY) {
                int stripLeft = guiLeft + BORDER_PADDING + SMALL_BUTTON;
                int stripRight = guiLeft + xSize - BORDER_PADDING - SMALL_BUTTON;
                return mouseX >= stripLeft && mouseX < stripRight
                                && mouseY >= row1Y && mouseY < row1Y + SMALL_BUTTON;
        }

        // ------------------------------------------------------------------
        // Input
        // ------------------------------------------------------------------

        @Override
        public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
                int mouseX = (int) click.x();
                int mouseY = (int) click.y();
                // The side strip's arrows flip to the other side — only
                // when shops serve it.
                boolean otherServes = otherSideCount > 0;
                if (prevSideBox != null && contains(prevSideBox, mouseX, mouseY) && otherServes) {
                        flipSide();
                        playPressSound();
                        return true;
                }
                if (nextSideBox != null && contains(nextSideBox, mouseX, mouseY) && otherServes) {
                        flipSide();
                        playPressSound();
                        return true;
                }
                // The carousel arrows turn the shops, wrapping at the ends.
                if (prevShopBox != null && contains(prevShopBox, mouseX, mouseY)
                                && currentOffers.size() > 1) {
                        flipListing(-1);
                        playPressSound();
                        return true;
                }
                if (nextShopBox != null && contains(nextShopBox, mouseX, mouseY)
                                && currentOffers.size() > 1) {
                        flipListing(1);
                        playPressSound();
                        return true;
                }
                // The footer's one action: run the listing's spawn command.
                if (teleportBox != null && contains(teleportBox, mouseX, mouseY)
                                && teleportCommand != null) {
                        teleportToCurrent();
                        playPressSound();
                        return true;
                }
                return super.mouseClicked(click, doubled);
        }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount,
                        double verticalAmount) {
                // The wheel turns the carousel's shops.
                if (currentOffers.size() > 1 && mouseX >= guiLeft && mouseX < guiLeft + xSize
                                && mouseY >= guiTop && mouseY < guiTop + ySize) {
                        flipListing(verticalAmount < 0 ? 1 : -1);
                        return true;
                }
                return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        }

        /** Flips to the other side and rebuilds the caches. */
        private void flipSide() {
                side = side.other();
                rebuildCaches(false);
        }

        /** One carousel step, wrapping; the teleport gate re-plans. */
        private void flipListing(int delta) {
                int size = currentOffers.size();
                listingIndex = (listingIndex + delta + size) % size;
                updateTeleportGate();
        }

        /** The one action: navigate to the current listing's shop. */
        private void teleportToCurrent() {
                Offer offer = currentOffers.get(listingIndex);
                PhysicalShop shop = offer.shop();
                // The gate was planned against the same facts; a fresh
                // plan honors a nation the player entered since.
                String serverId = ZeaClient.lifecycle().earthMcServer()
                                .map(ServerIdentity::host).orElse("unknown");
                ShopDestination destination = destinationOf(shop,
                                townByShop.get(shop.shopId()), nationByShop.get(shop.shopId()), serverId);
                String playerNation = ShopNavigation.playerNation(shop.world());
                if (ShopNavigator.plan(destination, playerNation, shop.world()).spawnCommand() == null) {
                        updateTeleportGate();
                        return;
                }
                ShopNavigation.navigate(offer, townByShop.get(shop.shopId()),
                                nationByShop.get(shop.shopId()));
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

        /** The count line's text: the side's noun with the item name. */
        static String countLine(Side side, int count, String display) {
                return count + " " + side.noun() + (count == 1 ? "" : "s") + " · " + display;
        }

        /** The enchantment line: none, or the list joined. */
        private static String enchantLine(List<String> enchantments) {
                return enchantments.isEmpty() ? "No enchantments"
                                : String.join(", ", enchantments);
        }

        /**
         * The current listing's labeled facts, most important first:
         * seller, price per item, stock, bulk terms, where, observed.
         */
        static List<DetailLine> detailLines(Offer offer, @Nullable String town,
                        @Nullable String nation, Instant now) {
                ShopObservation observation = offer.observation();
                List<DetailLine> lines = new ArrayList<>();
                lines.add(new DetailLine("Seller: "
                                + (offer.shop().owner() == null ? "?" : offer.shop().owner()), TEXT));
                lines.add(new DetailLine("Price per item: " + priceOrDash(offer.unitPrice()), TEXT));
                String stock = stockLine(observation);
                if (!stock.isEmpty()) {
                        lines.add(new DetailLine(stock, TEXT));
                }
                if (observation.priceScope() == net.zaminmc.zea.market.model.PriceScope.PER_BULK
                                && observation.bulkSize() != null) {
                        lines.add(new DetailLine("Bulk: " + observation.bulkSize() + "x "
                                        + observation.price() + "G", TEXT));
                }
                lines.add(new DetailLine("Where: " + whereLine(offer, town, nation), TEXT_DIM));
                lines.add(new DetailLine("Observed " + MarketAge.format(observation.observedAt(), now)
                                + (observation.sourceType() == SourceType.REMOTE_SHARED ? " · shared" : ""),
                                TEXT_DIM));
                return lines;
        }

        /**
         * One side's summary line: best and median through the pricing
         * engine, then the side's own shop and owner counts — {@code
         * SideStats.shops()} is the variant-wide count, which would read
         * as "BUYING · 14 shops" on a side with one.
         */
        private static String summaryLine(VariantPricing.SideStats stats, int sideShops) {
                String line = "best " + priceOrDash(stats.best());
                if (stats.median() != null) {
                        line += " · median " + format(stats.median());
                }
                return line + " · " + sideShops + " shop" + (sideShops == 1 ? "" : "s")
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
                // A raw stock that already carries its unit word ("8 stacks")
                // never gets the parsed suffix repeated after it.
                String word = unit.strip();
                boolean carried = word.isEmpty()
                                || observation.stockRaw().toLowerCase(java.util.Locale.ROOT)
                                                .endsWith(word.toLowerCase(java.util.Locale.ROOT));
                return "Stock: " + observation.stockRaw() + (carried ? "" : unit)
                                + (observation.maxBuyItems() != null ? " · can buy " + observation.maxBuyItems()
                                : observation.maxBuyBulks() != null ? " · can buy " + observation.maxBuyBulks() + " bulks"
                                : observation.maxSellItems() != null ? " · takes " + observation.maxSellItems()
                                : "");
        }

        private static String whereLine(Offer offer, @Nullable String town, @Nullable String nation) {
                int x = offer.shop().signPos().getX();
                int z = offer.shop().signPos().getZ();
                String coords = x + ", " + offer.shop().signPos().getY() + ", " + z;
                // A townless shop is its coords alone — they never print twice.
                if (town == null) {
                        return coords;
                }
                return town + (nation == null ? "" : " (" + nation + ")") + " · " + coords;
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
                // Prefer a section boundary (·), then a word boundary: a
                // trimmed line never ends inside a word ("9 stacks ite").
                int cut = trimmed.lastIndexOf('·');
                if (cut > 0) {
                        trimmed = trimmed.substring(0, cut);
                        if (trimmed.endsWith("·")) {
                                trimmed = trimmed.substring(0, trimmed.length() - 1);
                        }
                        trimmed = trimmed.stripTrailing();
                }
                int len = trimmed.length();
                if (len > 0 && len < text.length()
                                && !Character.isWhitespace(text.charAt(len))
                                && text.charAt(len) != '·') {
                        int space = trimmed.lastIndexOf(' ');
                        if (space > 0) {
                                trimmed = trimmed.substring(0, space).stripTrailing();
                        }
                        if (trimmed.endsWith("·")) {
                                trimmed = trimmed.substring(0, trimmed.length() - 1).stripTrailing();
                        }
                }
                return trimmed;
        }
}
