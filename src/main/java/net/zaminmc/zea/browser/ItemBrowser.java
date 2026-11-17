package net.zaminmc.zea.browser;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.zaminmc.zea.ZeaClient;
import net.zaminmc.zea.market.MarketData;
import net.zaminmc.zea.market.MarketSubsystem;
import net.zaminmc.zea.market.index.ItemVariantIndex;
import net.zaminmc.zea.market.index.ItemVariantIndex.Offer;
import net.zaminmc.zea.market.index.ItemVariantIndex.Variant;
import net.zaminmc.zea.market.index.VariantPricing;
import net.zaminmc.zea.market.model.ShopDirection;
import net.zaminmc.zea.market.model.ShopObservation;
import net.zaminmc.zea.market.model.SourceType;
import net.zaminmc.zea.market.ui.MarketAge;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashMap;
import java.util.Optional;

/**
 * The inventory item browser: NEU's item pane on the screen's right,
 * showing every exact item variant observed in EarthMC shops, and the
 * physical shops behind the one selected.
 *
 * <p>The layout is NEU's item-pane model, reproduced from
 * {@code NEUOverlay} and adapted to the modern API: the pane anchors to
 * the screen's right edge and spans the screen's full height, the nav
 * element (Prev, the page pill, Next) sits across the pane's top, the
 * grid paginates like NEU's item list — a page is the pane's tile grid,
 * never a scroll offset — and the sort bar carries the summary order and
 * the direction filter at the pane's bottom. The grid's columns and
 * rows come from NEU's own span math, so the density matches NEU at
 * every scale and resolution.
 *
 * <p>The icons are the actual variants — each tile rebuilds its real
 * display stack through {@link VariantStacks}, glint included. The
 * search bar ZEA already draws under the GUI drives the filter: the
 * pane reads the same session query the slot matching reads, so there
 * is one search system, not two. Selecting a tile opens the variant's
 * detail in the pane itself — NEU's info-pane model, never a centered
 * modal: the header carries the back arrow and the identity, the body
 * carries the market summary and the offer rows grouped by transaction
 * direction, and the footer carries the explicit Navigate action.
 * Selecting an offer never issues a command.
 *
 * <p>Rendering reads caches only: the variant results rebuild when the
 * market revision, the search query, the order or the filter changes,
 * the detail's town names resolve when the detail opens, and the
 * display stacks cache by token. Nothing here touches disk, the network
 * or the world.
 */
public final class ItemBrowser {
        private static final int FILL = 0xF0101018;
        private static final int BORDER = 0xFF505050;
        private static final int BUTTON_FILL = 0xFF181822;
        private static final int BUTTON_HOVER = 0xFF2A2A38;
        private static final int LABEL = 0xFF8A8A8A;
        private static final int VALUE = 0xFFE0E0E0;
        private static final int DIM = 0xFF606060;
        private static final int ACCENT = 0xFF55FFAA;
        private static final int SELL_TAG = 0xFFFFAA55;
        private static final int HOVER_FILL = 0xFF32323E;
        private static final int SELECTED_FILL = 0xFF2E3E44;
        private static final int APPROX = 0xFFFF8888;

        /** The state singleton, wired like the search bar's session. */
        private final ItemBrowserState state = new ItemBrowserState();

        // Per-frame geometry, recomputed from the live screen rectangle.
        private int paneLeft;
        private int paneWidth;
        private int screenHeight;
        private int screenWidth;

        // The detail overlay's offer-row geometry for hit-testing and tooltips.
        private final List<Row> detailRowsGeom = new ArrayList<>();
        // The detail's town context, resolved once when the detail opens:
        // shop id → town, and shop id → nation. Unknown stays absent.
        private final Map<String, String> townByShop = new HashMap<>();
        private final Map<String, String> nationByShop = new HashMap<>();
        // The Navigate footer's hit box, redrawn per frame.
        private int @Nullable [] navigateButton;

        public ItemBrowserState state() {
                return state;
        }

        /**
         * Draws the pane for this frame. Called from the container mixin's
         * render tail, after ZEA's buttons — the pane draws over vanilla
         * furniture on narrow screens by design.
         */
        public void render(GuiGraphicsExtractor graphics, AbstractContainerScreen<?> screen,
                        int mouseX, int mouseY) {
                MarketSubsystem market = ZeaClient.market();
                if (market == null) {
                        return;
                }
                screenHeight = screen.height;
                screenWidth = screen.width;
                paneWidth = BrowserGeometry.paneWidth(screen.width);
                paneLeft = BrowserGeometry.paneLeft(screen.width);
                if (!state.isOpen()) {
                        drawTab(graphics, screen, mouseX, mouseY);
                        return;
                }
                refreshResults(market.data());
                graphics.enableScissor(0, 0, screen.width, screenHeight);
                drawBackground(graphics);
                drawNav(graphics, screen.getFont(), mouseX, mouseY);
                if (state.view() == ItemBrowserState.View.DETAIL) {
                        drawDetail(graphics, screen, mouseX, mouseY);
                } else {
                        drawGrid(graphics, screen, mouseX, mouseY);
                        drawSortBar(graphics, screen, mouseX, mouseY);
                }
                graphics.disableScissor();
        }

        /** The pane's background: the full-height fill and its one-pixel frame. */
        private void drawBackground(GuiGraphicsExtractor graphics) {
                int top = Math.max(0, BrowserGeometry.paneTop() - BrowserGeometry.BACKGROUND_BLEED);
                int bottom = Math.min(screenHeight,
                                BrowserGeometry.paneBottom(screenHeight) + BrowserGeometry.BACKGROUND_BLEED);
                int left = paneLeft - 1;
                int right = paneLeft + paneWidth + 1;
                graphics.fill(left, top, right, bottom, FILL);
                graphics.fill(left, top, right, top + 1, BORDER);
                graphics.fill(left, bottom - 1, right, bottom, BORDER);
                graphics.fill(left, top, left + 1, bottom, BORDER);
                graphics.fill(right - 1, top, right, bottom, BORDER);
        }

        // ------------------------------------------------------------------
        // Nav element
        // ------------------------------------------------------------------

        /**
         * The pane's top strip — NEU's {@code renderNavElement}: the Prev
         * and Next buttons flanking the centered page pill, plus the close
         * button the old glyph header carried.
         */
        private void drawNav(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
                List<int[]> buttons = BrowserGeometry.navButtons(paneLeft, paneWidth, screenHeight);
                int maxPages = BrowserGeometry.maxPages(state.results().size(), paneWidth, screenHeight);
                String pageLabel = "Page: " + Math.min(maxPages, state.page() + 1) + "/" + maxPages;
                drawPaneButton(graphics, font, buttons.get(0), "◀ Prev", mouseX, mouseY);
                drawPaneButton(graphics, font, buttons.get(2), "Next ▶", mouseX, mouseY);
                int[] pill = buttons.get(1);
                graphics.fill(pill[0], pill[1], pill[0] + pill[2], pill[1] + pill[3], BUTTON_FILL);
                graphics.text(font, pageLabel, pill[0] + pill[2] / 2 - font.width(pageLabel) / 2,
                                pill[1] + (pill[3] - font.lineHeight) / 2 + 1, ACCENT, true);
                int[] close = BrowserGeometry.closeButton(paneLeft, paneWidth, screenHeight);
                boolean closeHovered = BrowserGeometry.contains(close[0], close[1], close[2], close[3], mouseX, mouseY);
                graphics.text(font, "✕", close[0] + 4, close[1] + 2, closeHovered ? VALUE : DIM, true);
        }

        /** One of the nav's buttons: the fill, the label and the hover state. */
        private void drawPaneButton(GuiGraphicsExtractor graphics, Font font, int[] button,
                        String label, int mouseX, int mouseY) {
                boolean hovered = BrowserGeometry.contains(button[0], button[1], button[2], button[3], mouseX, mouseY);
                graphics.fill(button[0], button[1], button[0] + button[2], button[1] + button[3],
                                hovered ? BUTTON_HOVER : BUTTON_FILL);
                graphics.text(font, label, button[0] + button[2] / 2 - font.width(label) / 2,
                                button[1] + (button[3] - font.lineHeight) / 2 + 1, VALUE, true);
        }

        // ------------------------------------------------------------------
        // Grid
        // ------------------------------------------------------------------

        /**
         * The tile grid — NEU's {@code iterateItemSlots} over the current
         * page of results, with the actual variant stack per tile and the
         * tooltip on hover.
         */
        private void drawGrid(GuiGraphicsExtractor graphics, AbstractContainerScreen<?> screen,
                        int mouseX, int mouseY) {
                Font font = screen.getFont();
                List<Variant> results = state.results();
                int pageSlots = BrowserGeometry.slotsPerPage(paneWidth, screenHeight);
                int first = state.page() * pageSlots;
                int gridLeft = BrowserGeometry.gridLeft(paneLeft, paneWidth);
                int gridTop = BrowserGeometry.gridTop(screenHeight);
                graphics.enableScissor(paneLeft, BrowserGeometry.gridTop(screenHeight) - 2,
                                paneLeft + paneWidth, BrowserGeometry.sortBarTop(screenHeight,
                                                BrowserGeometry.sortIconSize(paneWidth, SORT_ICON_COUNT)) + 2);
                for (int slot = 0; slot < pageSlots && first + slot < results.size(); slot++) {
                        Variant variant = results.get(first + slot);
                        int x = BrowserGeometry.cellLeft(gridLeft, slot % BrowserGeometry.columns(paneWidth));
                        int y = BrowserGeometry.cellTop(gridTop, slot / BrowserGeometry.columns(paneWidth));
                        boolean hovered = BrowserGeometry.contains(x - 1, y - 1, BrowserGeometry.ITEM_SIZE + 2,
                                        BrowserGeometry.ITEM_SIZE + 2, mouseX, mouseY);
                        if (hovered) {
                                graphics.fill(x - 1, y - 1, x + BrowserGeometry.ITEM_SIZE + 1,
                                                y + BrowserGeometry.ITEM_SIZE + 1, HOVER_FILL);
                        }
                        drawVariantIcon(graphics, variant, x, y);
                        if (hovered) {
                                drawVariantTooltip(graphics, font, variant, mouseX, mouseY);
                        }
                }
                graphics.disableScissor();
                if (results.isEmpty()) {
                        int midX = paneLeft + paneWidth / 2;
                        graphics.text(font, "No shops observed yet", midX - font.width("No shops observed yet") / 2,
                                        gridTop + 12, DIM, true);
                        graphics.text(font, "Open a shop sign to record one",
                                        midX - font.width("Open a shop sign to record one") / 2, gridTop + 24, LABEL, true);
                }
        }

        /** The tile's icon: the variant's own display stack, glint included. */
        private static void drawVariantIcon(GuiGraphicsExtractor graphics, Variant variant, int x, int y) {
                VariantStacks.Resolved resolved = VariantStacks.of(variant.token());
                if (!resolved.stack().isEmpty()) {
                        graphics.item(resolved.stack(), x, y);
                }
                if (!resolved.exact()) {
                        // An approximate icon is never presented as the exact one.
                        graphics.fill(x, y + 13, x + 6, y + 14, APPROX);
                }
        }

        private void drawVariantTooltip(GuiGraphicsExtractor graphics, Font font,
                        Variant variant, int mouseX, int mouseY) {
                List<Component> lines = new ArrayList<>();
                lines.add(Component.literal(variant.display()));
                VariantPricing.SideStats buyFrom = VariantPricing.stats(variant.offers(), ShopDirection.SELLING);
                VariantPricing.SideStats sellTo = VariantPricing.stats(variant.offers(), ShopDirection.BUYING);
                if (buyFrom.shops() > 0) {
                        lines.add(Component.literal("Buy from: " + priceOrDash(buyFrom.best())
                                        + " best · " + buyFrom.shops() + " shop" + (buyFrom.shops() == 1 ? "" : "s")));
                }
                if (sellTo.shops() > 0) {
                        lines.add(Component.literal("Sell to: " + priceOrDash(sellTo.best())
                                        + " best · " + sellTo.shops() + " shop" + (sellTo.shops() == 1 ? "" : "s")));
                }
                int owners = variant.owners().size();
                lines.add(Component.literal(variant.shops().size() + " shop"
                                + (variant.shops().size() == 1 ? "" : "s") + " · " + owners
                                + " owner" + (owners == 1 ? "" : "s")));
                lines.add(Component.literal("Observed " + MarketAge.format(variant.lastObservedAt(), Instant.now()))
                                .withStyle(style -> style.withColor(0xFF909090)));
                graphics.setTooltipForNextFrame(font, lines, Optional.empty(), mouseX, mouseY);
        }

        // ------------------------------------------------------------------
        // Sort bar
        // ------------------------------------------------------------------

        /** The sort bar's icon counts: three order icons, three direction filters. */
        static final int ORDER_ICON_COUNT = 3;
        static final int FILTER_ICON_COUNT = 3;
        static final int SORT_ICON_COUNT = ORDER_ICON_COUNT + FILTER_ICON_COUNT;

        /**
         * The pane's bottom strip — NEU's sort bar: the order icons from the
         * left, the direction filters to the right, the selected icon lit.
         */
        private void drawSortBar(GuiGraphicsExtractor graphics, AbstractContainerScreen<?> screen,
                        int mouseX, int mouseY) {
                Font font = screen.getFont();
                int size = BrowserGeometry.sortIconSize(paneWidth, SORT_ICON_COUNT);
                int barTop = BrowserGeometry.sortBarTop(screenHeight, size);
                int barLeft = BrowserGeometry.gridLeft(paneLeft, paneWidth);
                int barWidth = BrowserGeometry.columns(paneWidth) * BrowserGeometry.STRIDE
                                - BrowserGeometry.ITEM_PADDING;
                graphics.fill(barLeft - 1, barTop - 1, barLeft + barWidth + 1,
                                barTop + BrowserGeometry.ITEM_SIZE + 1, BUTTON_FILL);
                drawOrderIcons(graphics, font, size, mouseX, mouseY);
                drawFilterIcons(graphics, font, size, mouseX, mouseY);
        }

        private void drawOrderIcons(GuiGraphicsExtractor graphics, Font font, int size, int mouseX, int mouseY) {
                String[] glyphs = {"A", "G", "◔"};
                ItemBrowserState.Order[] orders = {
                                ItemBrowserState.Order.ALPHABETICAL,
                                ItemBrowserState.Order.PRICE,
                                ItemBrowserState.Order.NEWEST
                };
                List<int[]> icons = BrowserGeometry.orderIcons(paneLeft, paneWidth, screenHeight, ORDER_ICON_COUNT);
                for (int i = 0; i < icons.size(); i++) {
                        int[] icon = icons.get(i);
                        boolean selected = state.order() == orders[i];
                        boolean hovered = BrowserGeometry.contains(icon[0], icon[1], icon[2], icon[2], mouseX, mouseY);
                        graphics.fill(icon[0], icon[1], icon[0] + icon[2], icon[1] + icon[2],
                                        selected ? SELECTED_FILL : hovered ? HOVER_FILL : 0x00000000);
                        graphics.text(font, glyphs[i], icon[0] + icon[2] / 2 - font.width(glyphs[i]) / 2,
                                        icon[1] + icon[2] / 2 - 4, selected ? ACCENT : hovered ? VALUE : LABEL, true);
                        if (hovered) {
                                orderTooltip(graphics, font, orders[i], mouseX, mouseY);
                        }
                }
        }

        private void drawFilterIcons(GuiGraphicsExtractor graphics, Font font, int size, int mouseX, int mouseY) {
                String[] glyphs = {"▦", "▼", "▲"};
                ItemBrowserState.Filter[] filters = {
                                ItemBrowserState.Filter.ALL,
                                ItemBrowserState.Filter.BUY_FROM,
                                ItemBrowserState.Filter.SELL_TO
                };
                List<int[]> icons = BrowserGeometry.filterIcons(paneLeft, paneWidth, screenHeight, FILTER_ICON_COUNT);
                for (int i = 0; i < icons.size(); i++) {
                        int[] icon = icons.get(i);
                        boolean selected = state.filter() == filters[i];
                        boolean hovered = BrowserGeometry.contains(icon[0], icon[1], icon[2], icon[2], mouseX, mouseY);
                        graphics.fill(icon[0], icon[1], icon[0] + icon[2], icon[1] + icon[2],
                                        selected ? SELECTED_FILL : hovered ? HOVER_FILL : 0x00000000);
                        // The direction filters keep the offer rows' colors: orange for
                        // BUY FROM, green for SELL TO — color never carries it alone.
                        int lit = filters[i] == ItemBrowserState.Filter.SELL_TO ? ACCENT
                                        : filters[i] == ItemBrowserState.Filter.BUY_FROM ? SELL_TAG : ACCENT;
                        graphics.text(font, glyphs[i], icon[0] + icon[2] / 2 - font.width(glyphs[i]) / 2,
                                        icon[1] + icon[2] / 2 - 4, selected ? lit : hovered ? VALUE : LABEL, true);
                        if (hovered) {
                                filterTooltip(graphics, font, filters[i], mouseX, mouseY);
                        }
                }
        }

        private void orderTooltip(GuiGraphicsExtractor graphics, Font font, ItemBrowserState.Order order,
                        int mouseX, int mouseY) {
                graphics.setTooltipForNextFrame(font,
                                List.of(Component.literal("Order " + order.label())), Optional.empty(), mouseX, mouseY);
        }

        private void filterTooltip(GuiGraphicsExtractor graphics, Font font, ItemBrowserState.Filter filter,
                        int mouseX, int mouseY) {
                graphics.setTooltipForNextFrame(font,
                                List.of(Component.literal(filter.label())), Optional.empty(), mouseX, mouseY);
        }

        // ------------------------------------------------------------------
        // Detail view — NEU's info-pane model, inside the pane
        // ------------------------------------------------------------------

        private void drawDetail(GuiGraphicsExtractor graphics, AbstractContainerScreen<?> screen,
                        int mouseX, int mouseY) {
                Font font = screen.getFont();
                String token = state.selectedToken();
                Variant variant = token == null ? null : findVariant(token);
                if (variant == null) {
                        // The variant vanished from the index (fresh data replaced
                        // it): back to the grid.
                        state.closeDetail();
                        return;
                }
                drawDetailHeader(graphics, screen, variant, mouseX, mouseY);
                drawDetailSummary(graphics, font, variant);
                drawDetailOffers(graphics, font, variant, mouseX, mouseY);
                drawNavigateFooter(graphics, font, mouseX, mouseY);
        }

        /** The detail's header: the back arrow, the item's stack and its identity. */
        private void drawDetailHeader(GuiGraphicsExtractor graphics, AbstractContainerScreen<?> screen,
                        Variant variant, int mouseX, int mouseY) {
                Font font = screen.getFont();
                int top = BrowserGeometry.paneTop();
                int left = BrowserGeometry.gridLeft(paneLeft, paneWidth);
                int width = BrowserGeometry.columns(paneWidth) * BrowserGeometry.STRIDE
                                - BrowserGeometry.ITEM_PADDING;
                graphics.fill(left, top, left + width, top + BrowserGeometry.NAV_HEIGHT + 4, BUTTON_FILL);
                boolean backHovered = BrowserGeometry.contains(left, top, 14, BrowserGeometry.NAV_HEIGHT + 4,
                                mouseX, mouseY);
                graphics.text(font, "‹", left + 4, top + 4, backHovered ? ACCENT : VALUE, true);
                VariantStacks.Resolved resolved = VariantStacks.of(variant.token());
                graphics.item(resolved.stack(), left + 16, top + 1);
                String name = variant.display();
                graphics.text(font, name, left + 36, top + 2, ACCENT, true);
                if (!resolved.exact()) {
                        graphics.text(font, "icon approximate", left + 36, top + 12, APPROX, true);
                } else {
                        List<String> enchantments = variant.enchantments();
                        String sub = enchantments.isEmpty() ? "No enchantments"
                                        : String.join(", ", enchantments);
                        graphics.text(font, sub, left + 36, top + 12, LABEL, true);
                }
        }

        /** The detail's summary: one BUY FROM line, one SELL TO line, stacked. */
        private void drawDetailSummary(GuiGraphicsExtractor graphics, Font font, Variant variant) {
                int left = BrowserGeometry.gridLeft(paneLeft, paneWidth);
                int top = BrowserGeometry.paneTop() + BrowserGeometry.NAV_HEIGHT + 10;
                VariantPricing.SideStats buyFrom = VariantPricing.stats(variant.offers(), ShopDirection.SELLING);
                VariantPricing.SideStats sellTo = VariantPricing.stats(variant.offers(), ShopDirection.BUYING);
                graphics.text(font, "BUY FROM", left, top, SELL_TAG, true);
                graphics.text(font, summaryLine(buyFrom), left, top + 10, VALUE, true);
                graphics.text(font, "SELL TO", left, top + 22, ACCENT, true);
                graphics.text(font, summaryLine(sellTo), left, top + 32, VALUE, true);
        }

        private static String summaryLine(VariantPricing.SideStats stats) {
                String line = "best " + priceOrDash(stats.best());
                if (stats.median() != null) {
                        line += " · median " + format(stats.median());
                }
                return line + " · " + stats.shops() + " shop" + (stats.shops() == 1 ? "" : "s")
                                + " · " + stats.owners() + " owner" + (stats.owners() == 1 ? "" : "s");
        }

        /**
         * The detail's offer rows — both directions, grouped and sorted the
         * way the summary names them, scrolled inside the pane.
         */
        private void drawDetailOffers(GuiGraphicsExtractor graphics, Font font, Variant variant,
                        int mouseX, int mouseY) {
                int scrollPx = Math.max(0, state.detailScroll());
                List<Offer> buyFrom = new ArrayList<>(offersOf(variant, ShopDirection.SELLING));
                buyFrom.sort(java.util.Comparator.comparing(Offer::unitPrice,
                                java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())));
                List<Offer> sellTo = new ArrayList<>(offersOf(variant, ShopDirection.BUYING));
                sellTo.sort(java.util.Comparator.comparing(Offer::unitPrice,
                                java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder())));
                detailRowsGeom.clear();
                int left = BrowserGeometry.gridLeft(paneLeft, paneWidth);
                int width = BrowserGeometry.columns(paneWidth) * BrowserGeometry.STRIDE
                                - BrowserGeometry.ITEM_PADDING;
                int listTop = BrowserGeometry.paneTop() + BrowserGeometry.NAV_HEIGHT + 46;
                int listBottom = BrowserGeometry.paneBottom(screenHeight) - BrowserGeometry.SORT_BAR_HEIGHT - 18;
                graphics.enableScissor(left, listTop, left + width, listBottom);
                int y = listTop - scrollPx;
                int rowHeight = 22;
                if (!buyFrom.isEmpty()) {
                        y = drawOfferGroup(graphics, font, "BUY FROM — cheapest first", SELL_TAG, buyFrom,
                                        left, width, y, rowHeight, listTop, listBottom, mouseX, mouseY);
                        y += 8;
                }
                if (!sellTo.isEmpty()) {
                        drawOfferGroup(graphics, font, "SELL TO — highest payout first", ACCENT, sellTo,
                                        left, width, y, rowHeight, listTop, listBottom, mouseX, mouseY);
                }
                graphics.disableScissor();
        }

        /** One grouped direction block; returns the y below the group's last row. */
        private int drawOfferGroup(GuiGraphicsExtractor graphics, Font font, String title, int color,
                        List<Offer> offers, int left, int width, int y, int rowHeight,
                        int listTop, int listBottom, int mouseX, int mouseY) {
                if (y + 9 > listTop && y < listBottom) {
                        graphics.text(font, title, left, y, color, true);
                }
                y += 14;
                for (Offer offer : offers) {
                        if (y + rowHeight > listTop && y < listBottom) {
                                drawOfferRow(graphics, font, offer, left, width, y, rowHeight, mouseX, mouseY);
                                detailRowsGeom.add(new Row(left, y, width, rowHeight, offer));
                        }
                        y += rowHeight;
                }
                return y;
        }

        private void drawOfferRow(GuiGraphicsExtractor graphics, Font font, Offer offer,
                        int left, int width, int y, int rowHeight, int mouseX, int mouseY) {
                boolean selected = offer.shop().shopId().equals(state.selectedShopId());
                boolean hovered = BrowserGeometry.contains(left, y, width, rowHeight - 2, mouseX, mouseY);
                graphics.fill(left, y, left + width, y + rowHeight - 2,
                                selected ? SELECTED_FILL : hovered ? HOVER_FILL : 0x00000000);
                ShopObservation observation = offer.observation();
                // Direction first, by label — color never carries the meaning alone.
                String tag = observation.direction() == ShopDirection.SELLING ? "[BUY FROM]" : "[SELL TO]";
                graphics.text(font, tag, left + 2, y + 2,
                                observation.direction() == ShopDirection.SELLING ? SELL_TAG : ACCENT, true);
                String owner = offer.shop().owner() == null ? "?" : offer.shop().owner();
                int ownerWidth = font.width(owner);
                graphics.text(font, owner, left + 62, y + 2, VALUE, true);
                graphics.text(font, priceOrDash(offer.unitPrice()) + priceNote(observation),
                                left + 62 + ownerWidth + 8, y + 2, VALUE, true);
                String stock = stockLine(observation);
                graphics.text(font, stock, left + 2, y + 12, LABEL, true);
                graphics.text(font, whereLine(offer), left + 2 + stockWidth(font, stock) + 8, y + 12, LABEL, true);
                graphics.text(font, MarketAge.format(observation.observedAt(), Instant.now())
                                + (observation.sourceType() == SourceType.REMOTE_SHARED ? " · shared" : ""),
                                left + width - 4 - font.width(MarketAge.format(observation.observedAt(), Instant.now())
                                                + (observation.sourceType() == SourceType.REMOTE_SHARED ? " · shared" : "")),
                                y + 2, DIM, true);
        }

        /** The stock text's width, so the where-line never overlaps it. */
        private static int stockWidth(Font font, String stock) {
                return stock.isEmpty() ? 0 : font.width(stock);
        }

        /**
         * The navigation footer: the selected offer's Navigate action.
         * Selecting an offer never navigates — this button is the one thing
         * that does, and it is explicit.
         */
        private void drawNavigateFooter(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
                int left = BrowserGeometry.gridLeft(paneLeft, paneWidth);
                int by = BrowserGeometry.paneBottom(screenHeight) - BrowserGeometry.SORT_BAR_HEIGHT - 14;
                boolean selected = state.selectedShopId() != null;
                boolean hovered = BrowserGeometry.contains(left - 2, by - 2,
                                font.width("[Navigate to selected shop]") + 4, 14, mouseX, mouseY);
                int color = selected ? (hovered ? ACCENT : VALUE) : DIM;
                graphics.text(font, "[Navigate to selected shop]", left, by, color, true);
                navigateButton = new int[] {left - 2, by - 2, font.width("[Navigate to selected shop]") + 4, 14};
        }

        private String priceNote(ShopObservation observation) {
                if (observation.priceScope() == null) {
                        return "";
                }
                if (observation.priceScope() == net.zaminmc.zea.market.model.PriceScope.PER_BULK
                                && observation.bulkSize() != null) {
                        return " (" + observation.bulkSize() + "x " + observation.price() + "G)";
                }
                return "";
        }

        private String stockLine(ShopObservation observation) {
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

        // ------------------------------------------------------------------
        // Reopen tab
        // ------------------------------------------------------------------

        /**
         * The reopen tab — NEU's edge tab: a slim strip at the screen's right
         * edge, vertically centered; hovering it reopens the pane.
         */
        private void drawTab(GuiGraphicsExtractor graphics, AbstractContainerScreen<?> screen,
                        int mouseX, int mouseY) {
                int[] tab = BrowserGeometry.tabBox(screen.width, screenHeight);
                boolean hovered = BrowserGeometry.contains(tab[0], tab[1], tab[2], tab[3], mouseX, mouseY);
                graphics.fill(tab[0], tab[1], tab[0] + tab[2], tab[1] + tab[3],
                                hovered ? BUTTON_HOVER : BUTTON_FILL);
                graphics.fill(tab[0], tab[1], tab[0] + 1, tab[1] + tab[3], BORDER);
                graphics.fill(tab[0], tab[1], tab[0] + tab[2], tab[1] + 1, BORDER);
                graphics.fill(tab[0], tab[1] + tab[3] - 1, tab[0] + tab[2], tab[1] + tab[3], BORDER);
                graphics.text(screen.getFont(), "‹", tab[0] + 1, tab[1] + tab[3] / 2 - 4,
                                hovered ? ACCENT : DIM, true);
        }

        // ------------------------------------------------------------------
        // Data
        // ------------------------------------------------------------------

        private void refreshResults(MarketData data) {
                String query = normalizedQuery();
                ItemBrowserState.Order order = state.order();
                ItemBrowserState.Filter filter = state.filter();
                if (!state.isCurrent(data.revision(), query, order, filter)) {
                        List<Variant> all = ItemVariantIndex.variants(data);
                        String needle = query.toLowerCase(Locale.ROOT);
                        List<Variant> filtered = all.stream()
                                        .filter(filter::accepts)
                                        .filter(variant -> needle.isEmpty() || variant.searchText().contains(needle))
                                        .sorted(order.comparator())
                                        .toList();
                        state.replaceResults(filtered, data.revision(), query, order, filter);
                }
                // NEU's render-time page clamp: a shrinking result set or a resize
                // pulls the page back onto the reachable range.
                state.setPage(BrowserGeometry.clampPage(state.page(), state.results().size(),
                                paneWidth, screenHeight));
        }

        /** The search bar's live query — one search system, shared with slot matching. */
        private static String normalizedQuery() {
                return ZeaClient.search() == null ? "" : ZeaClient.search().query().trim();
        }

        private @Nullable Variant findVariant(String token) {
                for (Variant variant : state.results()) {
                        if (variant.token().equals(token)) {
                                return variant;
                        }
                }
                return null;
        }

        private static List<Offer> offersOf(Variant variant, ShopDirection direction) {
                return variant.offers().stream()
                                .filter(offer -> offer.observation().direction() == direction)
                                .toList();
        }

        private static String priceOrDash(@Nullable BigDecimal price) {
                return price == null ? "—" : format(price);
        }

        private static String format(BigDecimal price) {
                return price.stripTrailingZeros().toPlainString() + "G";
        }

        // ------------------------------------------------------------------
        // Interaction
        // ------------------------------------------------------------------

        /**
         * Routes a click. Returns true when the pane consumed it — over the
         * pane, the tab or the detail view — so the click never reaches a
         * slot. A tile click opens the detail; an offer click selects it;
         * a selection is never a command.
         */
        public boolean click(AbstractContainerScreen<?> screen, double mouseX, double mouseY) {
                int mx = (int) mouseX;
                int my = (int) mouseY;
                if (!state.isOpen()) {
                        int[] tab = BrowserGeometry.tabBox(screen.width, screenHeight);
                        if (BrowserGeometry.contains(tab[0], tab[1], tab[2], tab[3], mx, my)) {
                                state.open();
                                return true;
                        }
                        return false;
                }
                if (!BrowserGeometry.inPane(paneLeft, paneWidth, screenHeight, mx, my)) {
                        return false;
                }
                if (state.view() == ItemBrowserState.View.DETAIL) {
                        return clickDetail(mx, my);
                }
                for (int[] button : BrowserGeometry.navButtons(paneLeft, paneWidth, screenHeight)) {
                        if (BrowserGeometry.contains(button[0], button[1], button[2], button[3], mx, my)) {
                                handleNavButton(button[4]);
                                return true;
                        }
                }
                int[] close = BrowserGeometry.closeButton(paneLeft, paneWidth, screenHeight);
                if (BrowserGeometry.contains(close[0], close[1], close[2], close[3], mx, my)) {
                        state.close();
                        return true;
                }
                int index = BrowserGeometry.indexAt(mx, my, paneLeft, paneWidth, screenHeight,
                                state.page(), state.results().size());
                if (index >= 0 && index < state.results().size()) {
                        state.openDetail(state.results().get(index).token());
                        openDetailContext();
                        return true;
                }
                int size = BrowserGeometry.sortIconSize(paneWidth, SORT_ICON_COUNT);
                int barTop = BrowserGeometry.sortBarTop(screenHeight, size);
                if (my >= barTop - 1 && my < barTop + BrowserGeometry.ITEM_SIZE + 1) {
                        clickSortBar(mx, my);
                        return true;
                }
                return true; // clicks inside the pane never reach slots
        }

        private void handleNavButton(int kind) {
                if (kind == 0) {
                        state.setPage(state.page() - 1);
                } else if (kind == 2) {
                        state.setPage(state.page() + 1);
                }
        }

        private void clickSortBar(int mx, int my) {
                int size = BrowserGeometry.sortIconSize(paneWidth, SORT_ICON_COUNT);
                ItemBrowserState.Order[] orders = {
                                ItemBrowserState.Order.ALPHABETICAL,
                                ItemBrowserState.Order.PRICE,
                                ItemBrowserState.Order.NEWEST
                };
                for (int[] icon : BrowserGeometry.orderIcons(paneLeft, paneWidth, screenHeight, ORDER_ICON_COUNT)) {
                        if (BrowserGeometry.contains(icon[0], icon[1], icon[2], icon[2], mx, my)
                                        && icon[3] < orders.length) {
                                state.setOrder(orders[icon[3]]);
                                return;
                        }
                }
                ItemBrowserState.Filter[] filters = {
                                ItemBrowserState.Filter.ALL,
                                ItemBrowserState.Filter.BUY_FROM,
                                ItemBrowserState.Filter.SELL_TO
                };
                for (int[] icon : BrowserGeometry.filterIcons(paneLeft, paneWidth, screenHeight, FILTER_ICON_COUNT)) {
                        if (BrowserGeometry.contains(icon[0], icon[1], icon[2], icon[2], mx, my)
                                        && icon[3] < filters.length) {
                                state.setFilter(filters[icon[3]]);
                                return;
                        }
                }
        }

        private boolean clickDetail(int mx, int my) {
                // The back arrow: the header's left 14 px.
                int top = BrowserGeometry.paneTop();
                int left = BrowserGeometry.gridLeft(paneLeft, paneWidth);
                if (BrowserGeometry.contains(left, top, 14, BrowserGeometry.NAV_HEIGHT + 4, mx, my)) {
                        state.closeDetail();
                        return true;
                }
                // The Navigate footer: the explicit navigation action. Selecting
                // an offer never reaches here.
                if (navigateButton != null && state.selectedShopId() != null
                                && BrowserGeometry.contains(navigateButton[0], navigateButton[1],
                                navigateButton[2], navigateButton[3], mx, my)) {
                        navigateToSelected();
                        return true;
                }
                // Offer rows: selection only, never navigation.
                for (Row row : detailRowsGeom) {
                        if (BrowserGeometry.contains(row.x(), row.y(), row.w(), row.h(), mx, my)) {
                                state.selectOffer(row.offer().shop().shopId());
                                return true;
                        }
                }
                return true; // inside the pane, on no row: consumed
        }

        /** Runs the navigation action for the selected offer, if one is selected. */
        private void navigateToSelected() {
                String shopId = state.selectedShopId();
                if (shopId == null) {
                        return;
                }
                for (Row row : detailRowsGeom) {
                        if (row.offer().shop().shopId().equals(shopId)) {
                                net.zaminmc.zea.nav.ShopNavigation.navigate(row.offer(),
                                                townByShop.get(shopId), nationByShop.get(shopId));
                                return;
                        }
                }
        }

        /**
         * A wheel scroll over the pane turns the grid's page, NEU's paging
         * with the wheel added; over the detail it scrolls the offer list.
         * The return says whether the scroll was consumed.
         */
        public boolean scroll(double mouseX, double mouseY, double delta) {
                int mx = (int) mouseX;
                int my = (int) mouseY;
                if (!state.isOpen() || !BrowserGeometry.inPane(paneLeft, paneWidth, screenHeight, mx, my)) {
                        return false;
                }
                if (state.view() == ItemBrowserState.View.DETAIL) {
                        state.setDetailScroll(state.detailScroll() + (delta < 0 ? 12 : -12));
                        return true;
                }
                state.setPage(state.page() + (delta < 0 ? 1 : -1));
                return true;
        }

        /** Whether the point sits over the pane (or the closed pane's tab). */
        public boolean hoversPanel(double mouseX, double mouseY) {
                int mx = (int) mouseX;
                int my = (int) mouseY;
                if (!state.isOpen()) {
                        int[] tab = BrowserGeometry.tabBox(screenWidth, screenHeight);
                        return BrowserGeometry.contains(tab[0], tab[1], tab[2], tab[3], mx, my);
                }
                return BrowserGeometry.inPane(paneLeft, paneWidth, screenHeight, mx, my);
        }

        /** Resolves the detail's town context once, when the detail opens. */
        private void openDetailContext() {
                townByShop.clear();
                nationByShop.clear();
                MarketSubsystem market = ZeaClient.market();
                if (market == null || market.townLookup() == null) {
                        return;
                }
                Variant variant = state.selectedToken() == null ? null : findVariant(state.selectedToken());
                if (variant == null) {
                        return;
                }
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

        /** One offer row's hit box. */
        private record Row(int x, int y, int w, int h, Offer offer) {
        }
}
