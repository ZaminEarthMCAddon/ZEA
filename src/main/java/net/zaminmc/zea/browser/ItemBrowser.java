package net.zaminmc.zea.browser;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.zaminmc.zea.ZeaClient;
import net.zaminmc.zea.market.MarketData;
import net.zaminmc.zea.market.MarketSubsystem;
import net.zaminmc.zea.market.index.ItemVariantIndex;
import net.zaminmc.zea.market.index.ItemVariantIndex.Variant;
import net.zaminmc.zea.market.index.VariantPricing;
import net.zaminmc.zea.market.model.ShopDirection;
import net.zaminmc.zea.market.ui.MarketAge;
import net.zaminmc.zea.market.viewer.MarketViewerScreen;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * The inventory item browser: NEU's item pane, rebuilt on NEUOverlay's
 * actual rendering and interaction model — studied from the upstream
 * source with its history, not approximated from screenshots.
 *
 * <p>What NEU does, this does: the pane slides in from the screen's
 * right on NEU's 200 ms lerp ({@code LerpingFloat} ported verbatim),
 * resting with its left edge at two-thirds of the screen; the edge tab —
 * NEU's 64 px {@code item_pane_tab_arrow} at 30% alpha — slides away as
 * the pane arrives and returns when the pane hides, and hovering it
 * reopens. The background is NEU's: the dark {@code 0xc8101010} backdrop
 * plus the nearly-invisible config tint (NEU's default {@code alpha 6}
 * blue stands in for the blur NEU layers beneath it — vanilla's blur is
 * a once-per-frame whole-screen effect, not a region one). The nav
 * element is NEU's {@code renderNavElement} to the pixel: the wide
 * textured Prev/Next buttons (the 480×160 arrow with its hover/press
 * overlay, the press shifting the arrow one pixel down like NEU's),
 * flanking the three-layer page pill with its bold black label. The
 * grid is NEU's: 16 px items on a 20 px stride, each on the rounded
 * {@code item_mask} plate tinted the foreground gray, centered by the
 * split remainder, paginated by the ceiling column and row counts, the
 * hit box the item plus its one-pixel gutter. The sort bar is NEU's:
 * the order icons from the left, the direction filters from the right,
 * scaled down on narrow panes, the active icon gold, the ascending
 * arrows drawn over the order icons, the labels surfacing only after
 * the 400 ms hover NEU requires. Tooltips carry NEU's yellow bold action
 * hints. Button presses play the vanilla click.
 *
 * <p>Choosing a variant opens the market viewer — JEI's RecipesGui
 * rebuilt for a live market ({@code MarketViewerScreen}): the screen
 * captures the container as its parent, swaps itself in and returns the
 * inventory exactly as it was when it closes — JEI's open/close
 * semantics. Its tabs pick the market's side, its arrows cycle the
 * browser's results and paginate the offers, and its footer carries the
 * explicit Navigate action. Selecting an offer never issues a command.
 *
 * <p>The search bar ZEA already draws drives the filter — the pane reads
 * the same session query the slot matching reads, one search system, and
 * a non-empty query opens the pane the way NEU's
 * {@code openWhenSearching} does. The pane itself starts closed, NEU's
 * {@code itemPaneOpen = false}: it never greets a screen wide open. It
 * slides in when the search asks for it or the edge tab is hovered, and
 * it slides back out when a genuinely new screen binds without an
 * active search (NEU's overlay-reset rule) or the close-panes keybind
 * fires. Rendering reads caches only: the
 * variant results rebuild when the market revision, the search query,
 * the order, its direction or the filter changes; nothing here touches
 * disk, the network or the world.
 */
public final class ItemBrowser {
        // NEU's default itemlist colours (config defaults, decoded from the
        // SpecialColour strings): foreground #646464 opaque, background the
        // nearly-invisible alpha-6 blue.
        //
        // NEU's panel is ONE translucent dark rect — rgba(10,10,10,200) —
        // over the raw world, and nothing else. On 26.3 vanilla already
        // draws exactly that behind every in-game screen:
        // Screen.extractBackground fills the full screen with the gradient
        // 0xC0101010 → 0xD0101010 (alpha 192→208 of rgb(16,16,16)), which
        // is NEU's 200-alpha panel transparency level to within a step of
        // the gradient. Stacking ZEA's own dark fills on top of it is what
        // made the pane read as an opaque purple-black slab — so the pane
        // adds nothing but NEU's alpha-6 blue tint, and the world shows
        // through at NEU's level.
        private static final int FG = 0xFF646464;
        private static final int FG_CUSTOM = 0xFF646464;
        private static final int PANE_TINT = 0x060000FF;
        /** The page pill's bevel, NEU's Color(177,177,177) and Color(50,50,50). */
        private static final int PILL_LIGHT = 0xFFB1B1B1;
        private static final int PILL_DARK = 0xFF323232;
        /** NEU's selected-item-group frame grays, reused for the selected offer row. */
        private static final int ROW_FRAME = 0xFFB4B4B4;
        private static final int ROW_DARK = 0xFF1E1E1E;
        private static final int ROW_SHADOW = 0xA0000000;
        private static final int ROW_SELECTED = 0xFF3C4A50;
        private static final int ROW_HOVER = 0x6432323E;
        /** The approximate-icon marker stays ZEA's own — red, at NEU's has-child underline position. */
        private static final int APPROX = 0xFFFF8888;
        private static final int TEXT = 0xFFFFFFFF;
        private static final int TEXT_DIM = 0xFF909090;
        private static final int HINT = 0xFFFFFF55;
        private static final int BUY_COLOR = 0xFFFFAA55;
        private static final int SELL_COLOR = 0xFF55FFAA;

        /** NEU's hover delay before the sort/order labels surface. */
        private static final long ICON_TOOLTIP_DELAY = 400;

        /** The state singleton, wired like the search bar's session. */
        private final ItemBrowserState state = new ItemBrowserState();

        // NEU's three sliding elements, its LerpingFloat ported verbatim.
        private final Lerp paneOffset = new Lerp(1);
        private final Lerp tabOffset = new Lerp(BrowserGeometry.TAB_CLOSED_OFFSET, 50);

        // Per-frame geometry, recomputed from the live screen rectangle.
        private BrowserGeometry.@Nullable Metrics metrics;
        private float itemFactor = 1;
        private int paneLeft;
        /**
         * One pane unit expressed in container gui units — the pose scale
         * the pane draws through. NEU's pushed scale makes the pane's
         * pixels smaller than the container's; this is ZEA's equivalent.
         */
        private float unitToGui = 1f;

        // The 400 ms icon-hover clock, NEU's millisLastMouseMove discipline.
        private long iconHoverStart = -1;
        private int iconHoverKind = -1;

        /** The screen the pane last bound to — a different instance is a genuinely new screen. */
        private @Nullable AbstractContainerScreen<?> boundScreen;

        public ItemBrowserState state() {
                return state;
        }

        /**
         * Binds the pane to a screen instance — NEU's overlay-reset
         * rule: a genuinely new screen (not a re-init of the same
         * instance, which a resize or a recipe-book toggle performs)
         * closes the pane unless a search is already asking for it, so
         * the pane never greets a screen wide open; it waits behind its
         * edge tab. Called after the search session binds, so the query
         * this rule reads is the new screen's own.
         */
        public void bindScreen(AbstractContainerScreen<?> screen) {
                if (boundScreen == screen) {
                        return;
                }
                boundScreen = screen;
                if (normalizedQuery().isEmpty()) {
                        state.close();
                }
        }

        /**
         * Draws the pane for this frame. Called from the container mixin's
         * extractContents head — before the GUI's widgets, labels and
         * slots extract — so the pane sits UNDER the GUI and the search
         * bar, exactly NEU's itemlist layering.
         */
        public void render(GuiGraphicsExtractor graphics, AbstractContainerScreen<?> screen,
                        int mouseX, int mouseY) {
                MarketSubsystem market = ZeaClient.market();
                if (market == null) {
                        return;
                }
                Minecraft client = Minecraft.getInstance();
                // NEU's pushed pane scale: the geometry runs in pane units —
                // the screen rectangle as NEU's pushed ScaledResolution sees
                // it — and everything draws through one pose scale, so the
                // tiles keep NEU's pixel pitch instead of the container's.
                float containerScale = client.getWindow().getGuiScale();
                int paneScale = BrowserGeometry.paneScale(containerScale);
                unitToGui = paneScale / containerScale;
                metrics = BrowserGeometry.metrics(
                                Math.round(screen.width * containerScale / paneScale),
                                Math.round(screen.height * containerScale / paneScale),
                                paneScale, ORDER_ICON_COUNT, FILTER_ICON_COUNT);
                refreshResults(market.data());
                tickAnimations();
                paneLeft = BrowserGeometry.paneLeft(metrics.screenWidth, itemFactor);

                // The mouse in pane units for the hit tests; the tooltip keeps
                // the container-space coordinates it queues with.
                int mx = Math.round(mouseX / unitToGui);
                int my = Math.round(mouseY / unitToGui);

                // NEU's openWhenSearching: a non-empty query opens the pane.
                if (!normalizedQuery().isEmpty() && !state.isOpen()) {
                        state.open();
                }
                // NEU's tab-open rule: while the pane is closed, hovering the
                // protruding strip reopens it.
                if (!state.isOpen() && BrowserGeometry.tabHover(metrics.screenWidth,
                                Math.round(tabOffset.getValue()), mx, my, metrics.screenHeight)) {
                        state.open();
                }

                graphics.pose().pushMatrix();
                graphics.pose().scale(unitToGui, unitToGui);
                drawTab(graphics, screen, mx, my);

                if (itemFactor < 0.99f) {
                        drawPaneBackground(graphics);
                        drawNav(graphics, screen, mx, my);
                        drawGrid(graphics, screen, mx, my, mouseX, mouseY);
                        drawSortBar(graphics, screen, mx, my);
                }
                graphics.pose().popMatrix();
        }

        /** NEU's animation state machine: tab and pane chase their targets. */
        private void tickAnimations() {
                boolean open = state.isOpen();
                if (open) {
                        if (tabOffset.getValue() == 0) {
                                if (paneOffset.getTarget() != BrowserGeometry.OPEN_OFFSET) {
                                        paneOffset.setTarget(BrowserGeometry.OPEN_OFFSET);
                                }
                        } else if (tabOffset.getTarget() != 0) {
                                tabOffset.setTarget(0);
                        }
                } else {
                        if (paneOffset.getValue() == 1) {
                                if (tabOffset.getTarget() != BrowserGeometry.TAB_CLOSED_OFFSET) {
                                        tabOffset.setTarget(BrowserGeometry.TAB_CLOSED_OFFSET);
                                }
                        } else if (paneOffset.getTarget() != 1f) {
                                paneOffset.setTarget(1f);
                        }
                }
                paneOffset.tick();
                tabOffset.tick();
                itemFactor = paneOffset.getValue();
        }

        // ------------------------------------------------------------------
        // Pane background — NEU's own panel dark, vanilla-supplied
        // ------------------------------------------------------------------

        /**
         * The pane's background: NEU's own — the vanilla in-game gradient
         * beneath already supplies the panel's dark (NEU's 200-alpha
         * near-black level), so the pane draws only NEU's nearly-invisible
         * alpha-6 blue tint over it. Nothing else: the extra slate wash and
         * dark fills that used to stack here are what made the pane opaque
         * and purple.
         */
        private void drawPaneBackground(GuiGraphicsExtractor graphics) {
                BrowserGeometry.Metrics m = metrics;
                if (m == null) {
                        return;
                }
                int left = paneLeft + m.boxPadding - 5;
                int right = paneLeft + m.paneWidth - m.boxPadding + 5;
                int top = Math.max(0, m.boxPadding - 5);
                int bottom = Math.min(m.screenHeight, m.screenHeight - m.boxPadding + 5);
                graphics.fill(left, top, right, bottom, PANE_TINT);
        }

        // ------------------------------------------------------------------
        // Nav element — NEU's renderNavElement
        // ------------------------------------------------------------------

        /**
         * The pane's top strip — NEU's {@code renderNavElement}: the textured
         * Prev and Next buttons flanking the beveled page pill.
         */
        private void drawNav(GuiGraphicsExtractor graphics, AbstractContainerScreen<?> screen,
                        int mouseX, int mouseY) {
                BrowserGeometry.Metrics m = metrics;
                if (m == null) {
                        return;
                }
                Font font = screen.getFont();
                int maxPages = BrowserGeometry.maxPages(state.results().size(), m);
                List<int[]> buttons = BrowserGeometry.navButtons(m, paneLeft, maxPages,
                                text -> font.width(Component.literal(text).withStyle(style -> style.withBold(true))));
                int[] prev = buttons.get(0);
                int[] pill = buttons.get(1);
                int[] next = buttons.get(2);

                boolean pressing = isMouseButtonPressed();
                int prevPressed = pressing && BrowserGeometry.contains(prev[0], prev[1], prev[2], prev[3], mouseX, mouseY)
                                ? 1 : 0;
                int nextPressed = pressing && BrowserGeometry.contains(next[0], next[1], next[2], next[3], mouseX, mouseY)
                                ? 1 : 0;

                drawArrowButton(graphics, prev, true, prevPressed);
                drawArrowButton(graphics, next, false, nextPressed);

                // The pill: NEU's three-layer bevel and its bold black label.
                graphics.fill(pill[0] - 1, pill[1], pill[0] + pill[2] + 1, pill[1] + pill[3], PILL_LIGHT);
                graphics.fill(pill[0], pill[1] + 1, pill[0] + pill[2] + 1, pill[1] + pill[3], PILL_DARK);
                graphics.fill(pill[0], pill[1] + 1, pill[0] + pill[2], pill[1] + pill[3] - 1, FG);
                String pageText = "Page: " + (Math.min(maxPages, state.page() + 1)) + "/" + maxPages;
                Component pageComponent = Component.literal(pageText).withStyle(style -> style.withBold(true));
                // NEU's pill label: centered on the strip's middle, scaled
                // down only when it would exceed the strip minus both buttons
                // minus NEU's 10-unit reserve.
                int navLeftEdge = prev[0];
                int navRightEdge = next[0] + next[2];
                drawCenteredScaledMaxWidth(graphics, font, pageComponent,
                                (navLeftEdge + navRightEdge) / 2f, pill[1] + pill[3] / 2f,
                                navRightEdge - navLeftEdge - 2 * prev[2] - 10,
                                0xFF000000, false);
        }

        /**
         * NEU's {@code drawStringScaled}: the string scaled by factor from a
         * top-left point.
         */
        private static void drawScaled(GuiGraphicsExtractor graphics, Font font, Component text,
                        float left, float top, float factor, int color, boolean shadow) {
                graphics.pose().pushMatrix();
                graphics.pose().translate(left, top);
                graphics.pose().scale(factor, factor);
                graphics.text(font, text, 0, 0, color, shadow);
                graphics.pose().popMatrix();
        }

        /**
         * NEU's {@code drawStringCenteredScaled}: the string scaled so its
         * width equals the allowance — up as well as down — its span centered
         * on the point, its 8-unit glyph box centered vertically. The nav
         * buttons' labels use this, which is why NEU's Prev/Next text fills
         * its side of the button instead of shrinking inside it.
         */
        private static void drawCenteredScaled(GuiGraphicsExtractor graphics, Font font, Component text,
                        float centerX, float centerY, float len, int color, boolean shadow) {
                int width = font.width(text);
                float factor = len / width;
                float fontHeight = 8 * factor;
                drawScaled(graphics, font, text, centerX - len / 2f, centerY - fontHeight / 2f,
                                factor, color, shadow);
        }

        /**
         * NEU's {@code drawStringCenteredScaledMaxWidth}: centered, scaled
         * down when it would overflow the maximum width, never up.
         */
        private static void drawCenteredScaledMaxWidth(GuiGraphicsExtractor graphics, Font font, Component text,
                        float centerX, float centerY, float maxLen, int color, boolean shadow) {
                int width = font.width(text);
                float factor = Math.min(1f, maxLen / width);
                float newLen = Math.min(width, maxLen);
                float fontHeight = 8 * factor;
                drawScaled(graphics, font, text, centerX - newLen / 2f, centerY - fontHeight / 2f,
                                factor, color, shadow);
        }

        /**
         * One of the nav's arrow buttons: the foreground plate, the 480×160
         * arrow texture (Prev draws it mirrored), the hover/press overlay and
         * the bold black label — NEU's exact layering, the press shifting the
         * arrow one pixel down.
         */
        private void drawArrowButton(GuiGraphicsExtractor graphics, int[] button, boolean mirrored, int pressed) {
                int x = button[0];
                int y = button[1];
                int w = button[2];
                int h = button[3];
                graphics.fill(x, y, x + w, y + h, FG);
                if (mirrored) {
                        graphics.blit(NeuTextures.RIGHT_ARROW, x + pressed, y + pressed,
                                        x + pressed + w, y + pressed + h, 1f, 0f, 0f, 1f);
                        graphics.blit(NeuTextures.RIGHT_ARROW_OVERLAY, x, y, x + w, y + h,
                                        1f - pressed, pressed, 1f - pressed, pressed);
                } else {
                        graphics.blit(NeuTextures.RIGHT_ARROW, x + pressed, y + pressed,
                                        x + pressed + w, y + pressed + h, 0f, 1f, 0f, 1f);
                        graphics.blit(NeuTextures.RIGHT_ARROW_OVERLAY, x, y, x + w, y + h,
                                        1f - pressed, pressed, 1f - pressed, pressed);
                }
                Font font = Minecraft.getInstance().font;
                String label = mirrored ? "Prev" : "Next";
                Component comp = Component.literal(label).withStyle(style -> style.withBold(true));
                // NEU anchors each label at 62.5% of the button's width —
                // from the left for Prev, from the right for Next — and
                // scales it to fill 50% of the width.
                float anchor = mirrored
                                ? x + w * 300 / 480f + pressed
                                : x + w - w * 300 / 480f + pressed;
                drawCenteredScaled(graphics, font, comp,
                                anchor, y + h / 2f + pressed, w * 240 / 480f,
                                0xFF000000, false);
        }

        /** Whether any mouse button is currently held — NEU's Mouse.isButtonDown probe. */
        private static boolean isMouseButtonPressed() {
                Minecraft client = Minecraft.getInstance();
                return client.mouseHandler.isLeftPressed() || client.mouseHandler.isMiddlePressed()
                                || client.mouseHandler.isRightPressed();
        }

        // ------------------------------------------------------------------
        // Grid
        // ------------------------------------------------------------------

        /**
         * The tile grid — NEU's {@code iterateItemSlots} over the current
         * page of results, each tile on its rounded foreground plate, the
         * tooltip on hover with NEU's action hints.
         */
        private void drawGrid(GuiGraphicsExtractor graphics, AbstractContainerScreen<?> screen,
                        int mouseX, int mouseY, int guiMouseX, int guiMouseY) {
                BrowserGeometry.Metrics m = metrics;
                if (m == null) {
                        return;
                }
                Font font = screen.getFont();
                List<Variant> results = state.results();
                int pageSlots = m.slotsPerPage();
                int first = state.page() * pageSlots;
                int gridLeft = BrowserGeometry.gridLeft(m, paneLeft);
                int gridTop = BrowserGeometry.gridTop(m);
                int gridRight = BrowserGeometry.gridRight(m, paneLeft);
                int gridBottom = BrowserGeometry.gridBottom(m);
                graphics.enableScissor(paneLeft, gridTop - 2, paneLeft + m.paneWidth, m.sortIconTop + 2);
                for (int slot = 0; slot < pageSlots && first + slot < results.size(); slot++) {
                        Variant variant = results.get(first + slot);
                        int x = BrowserGeometry.cellLeft(gridLeft, slot % m.columns);
                        int y = BrowserGeometry.cellTop(gridTop, slot / m.columns);
                        if (x + BrowserGeometry.ITEM_SIZE < gridLeft || x > gridRight) {
                                continue;
                        }
                        if (y + BrowserGeometry.ITEM_SIZE < gridTop || y > gridBottom) {
                                continue;
                        }
                        // The rounded plate: NEU's item_mask at 18×18 tinted the
                        // foreground colour, one pixel larger than the item on every
                        // side.
                        graphics.blit(RenderPipelines.GUI_TEXTURED, NeuTextures.ITEM_MASK, x - 1, y - 1,
                                        0f, 0f, BrowserGeometry.ITEM_SIZE + 2, BrowserGeometry.ITEM_SIZE + 2,
                                        36, 36, 36, 36, FG);
                        drawVariantIcon(graphics, variant, x, y);
                        if (BrowserGeometry.contains(x - 1, y - 1, BrowserGeometry.ITEM_SIZE + 2,
                                        BrowserGeometry.ITEM_SIZE + 2, mouseX, mouseY)) {
                                drawVariantTooltip(graphics, font, variant, guiMouseX, guiMouseY);
                        }
                }
                graphics.disableScissor();
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
                // NEU's action hints: yellow, bold, after a blank line.
                lines.add(Component.literal(""));
                lines.add(hint("LMB : View offers!"));
                graphics.setTooltipForNextFrame(font, lines, Optional.empty(), mouseX, mouseY);
        }

        /** NEU's hint line style: yellow and bold. */
        private static Component hint(String text) {
                return Component.literal(text).withStyle(style -> style.withColor(HINT).withBold(true));
        }

        private static String priceOrDash(@Nullable BigDecimal price) {
                return price == null ? "—" : format(price);
        }

        private static String format(BigDecimal price) {
                return price.setScale(2, java.math.RoundingMode.HALF_UP)
                                .stripTrailingZeros().toPlainString() + "G";
        }

        // ------------------------------------------------------------------
        // Sort bar
        // ------------------------------------------------------------------

        /**
         * The sort bar's icon counts: the three order buttons —
         * alphabetical, rarity, price — and the five category filters:
         * all, weapons and tools, armor, general, server items.
         */
        static final int ORDER_ICON_COUNT = 3;
        static final int FILTER_ICON_COUNT = 5;

        /**
         * The pane's bottom strip — NEU's sort bar: the foreground plate,
         * the three order buttons from the left (alphabetical, rarity,
         * price) with NEU's direction arrow over the active one only, and
         * the five category filters to the right (all, weapons and tools,
         * armor, general, server items), the selected icon gold.
         */
        private void drawSortBar(GuiGraphicsExtractor graphics, AbstractContainerScreen<?> screen,
                        int mouseX, int mouseY) {
                BrowserGeometry.Metrics m = metrics;
                if (m == null) {
                        return;
                }
                int barLeft = BrowserGeometry.gridLeft(m, paneLeft);
                int barRight = paneLeft + m.paneWidth - m.boxPadding - m.itemBoxXPadding;
                int barTop = BrowserGeometry.sortBarTop(m);
                graphics.fill(barLeft - 1, barTop, barRight + 1, m.screenHeight - m.boxPadding, FG_CUSTOM);

                drawOrderIcons(graphics, m, mouseX, mouseY);
                drawFilterIcons(graphics, m, mouseX, mouseY);
        }

        private void drawOrderIcons(GuiGraphicsExtractor graphics, BrowserGeometry.Metrics m,
                        int mouseX, int mouseY) {
                Identifier[] icons = {
                                NeuTextures.ORDER_ALPHABETICAL, NeuTextures.ORDER_RARITY, NeuTextures.ORDER_VALUE
                };
                Identifier[] active = {
                                NeuTextures.ORDER_ALPHABETICAL_ACTIVE, NeuTextures.ORDER_RARITY_ACTIVE,
                                NeuTextures.ORDER_VALUE_ACTIVE
                };
                ItemBrowserState.Order[] orders = {
                                ItemBrowserState.Order.ALPHABETICAL,
                                ItemBrowserState.Order.RARITY,
                                ItemBrowserState.Order.PRICE
                };
                List<int[]> boxes = BrowserGeometry.orderIcons(m, paneLeft, ORDER_ICON_COUNT);
                for (int i = 0; i < boxes.size(); i++) {
                        int[] icon = boxes.get(i);
                        int size = icon[2];
                        boolean selected = state.order() == orders[i];
                        graphics.fill(icon[0], icon[1], icon[0] + size, icon[1] + size, FG);
                        graphics.blit(selected ? active[i] : icons[i], icon[0], icon[1],
                                        icon[0] + size, icon[1] + size, 0f, 1f, 0f, 1f);
                        if (selected) {
                                // NEU's direction arrow — and it shows ONLY while this
                                // button owns the order: relevance state has no arrow.
                                graphics.blit(state.ascending() ? NeuTextures.ASCENDING_OVERLAY
                                                : NeuTextures.DESCENDING_OVERLAY,
                                                icon[0], icon[1], icon[0] + size, icon[1] + size, 0f, 1f, 0f, 1f);
                        }
                        noteIconHover(i, icon[0], icon[1], size, mouseX, mouseY);
                        if (iconHoverKind == i && hoverSurfaced()) {
                                orderTooltip(graphics, orders[i], mouseX, mouseY);
                        }
                }
        }

        private void drawFilterIcons(GuiGraphicsExtractor graphics, BrowserGeometry.Metrics m,
                        int mouseX, int mouseY) {
                Identifier[] icons = {
                                NeuTextures.FILTER_ALL, NeuTextures.FILTER_WEAPONS, NeuTextures.FILTER_ARMOR,
                                NeuTextures.FILTER_GENERAL, NeuTextures.FILTER_EARTH
                };
                Identifier[] active = {
                                NeuTextures.FILTER_ALL_ACTIVE, NeuTextures.FILTER_WEAPONS_ACTIVE,
                                NeuTextures.FILTER_ARMOR_ACTIVE, NeuTextures.FILTER_GENERAL_ACTIVE,
                                NeuTextures.FILTER_EARTH_ACTIVE
                };
                ItemBrowserState.Filter[] filters = {
                                ItemBrowserState.Filter.ALL,
                                ItemBrowserState.Filter.WEAPONS,
                                ItemBrowserState.Filter.ARMOR,
                                ItemBrowserState.Filter.GENERAL,
                                ItemBrowserState.Filter.EARTH
                };
                int orderBase = ORDER_ICON_COUNT;
                List<int[]> boxes = BrowserGeometry.filterIcons(m, paneLeft, FILTER_ICON_COUNT);
                for (int i = 0; i < boxes.size(); i++) {
                        int[] icon = boxes.get(i);
                        int size = icon[2];
                        int kind = orderBase + i;
                        boolean selected = state.filter() == filters[i];
                        graphics.fill(icon[0], icon[1], icon[0] + size, icon[1] + size, FG);
                        graphics.blit(selected ? active[i] : icons[i], icon[0], icon[1],
                                        icon[0] + size, icon[1] + size, 0f, 1f, 0f, 1f);
                        noteIconHover(kind, icon[0], icon[1], size, mouseX, mouseY);
                        if (iconHoverKind == kind && hoverSurfaced()) {
                                filterTooltip(graphics, filters[i], mouseX, mouseY);
                        }
                }
        }

        /** Tracks the 400 ms hover clock for one icon, NEU's rule. */
        private void noteIconHover(int kind, int x, int y, int size, int mouseX, int mouseY) {
                boolean over = BrowserGeometry.contains(x, y, size, size, mouseX, mouseY);
                if (over) {
                        if (iconHoverKind != kind) {
                                iconHoverKind = kind;
                                iconHoverStart = System.currentTimeMillis();
                        }
                } else if (iconHoverKind == kind) {
                        iconHoverKind = -1;
                        iconHoverStart = -1;
                }
        }

        /** Whether the current icon hover has lasted past NEU's 400 ms. */
        private boolean hoverSurfaced() {
                return iconHoverStart > 0
                                && System.currentTimeMillis() - iconHoverStart > ICON_TOOLTIP_DELAY;
        }

        private void orderTooltip(GuiGraphicsExtractor graphics, ItemBrowserState.Order order,
                        int mouseX, int mouseY) {
                String text = order == ItemBrowserState.Order.NONE
                                ? "Relevance"
                                : "Order " + order.label();
                graphics.setTooltipForNextFrame(Minecraft.getInstance().font,
                                List.of(Component.literal(text).withStyle(style -> style.withColor(0xFFAAAAAA))),
                                Optional.empty(), mouseX, mouseY);
        }

        private void filterTooltip(GuiGraphicsExtractor graphics, ItemBrowserState.Filter filter,
                        int mouseX, int mouseY) {
                String text = filter == ItemBrowserState.Filter.ALL ? "No Filter"
                                : "Filter " + filter.label();
                graphics.setTooltipForNextFrame(Minecraft.getInstance().font,
                                List.of(Component.literal(text).withStyle(style -> style.withColor(0xFFAAAAAA))),
                                Optional.empty(), mouseX, mouseY);
        }

        // ------------------------------------------------------------------
        // Edge tab — NEU's 64 px item_pane_tab_arrow
        // ------------------------------------------------------------------

        /**
         * The reopen tab — NEU's edge tab: the 64 px arrow texture at 30%
         * alpha, vertically centered at the screen's right edge; it slides
         * away as the pane opens and hovering it, while closed, reopens.
         */
        private void drawTab(GuiGraphicsExtractor graphics, AbstractContainerScreen<?> screen,
                        int mouseX, int mouseY) {
                BrowserGeometry.Metrics m = metrics;
                if (m == null) {
                        return;
                }
                int tabOffsetValue = Math.round(tabOffset.getValue());
                int[] tab = BrowserGeometry.tabBox(m.screenWidth, m.screenHeight, tabOffsetValue);
                // NEU draws the arrow at 30% alpha while it waits at the edge.
                graphics.blit(RenderPipelines.GUI_TEXTURED, NeuTextures.ITEM_PANE_TAB_ARROW,
                                tab[0], tab[1], 0f, 0f, tab[2], tab[3], tab[2], tab[3], tab[2], tab[3],
                                0x4DFFFFFF);
        }

        // ------------------------------------------------------------------
        // Data
        // ------------------------------------------------------------------

        private void refreshResults(MarketData data) {
                String query = normalizedQuery();
                ItemBrowserState.Order order = state.order();
                boolean ascending = state.ascending();
                ItemBrowserState.Filter filter = state.filter();
                if (!state.isCurrent(data.revision(), query, order, ascending, filter)) {
                        List<Variant> all = ItemVariantIndex.variants(data);
                        String needle = query.toLowerCase(Locale.ROOT);
                        List<Variant> matches = all.stream()
                                        .filter(filter::accepts)
                                        .filter(variant -> needle.isEmpty() || variant.searchText().contains(needle))
                                        .toList();
                        List<Variant> filtered = matches;
                        if (order != ItemBrowserState.Order.NONE) {
                                // NONE is relevance: the index's own order, untouched —
                                // sorting with a no-op comparator would be identical but
                                // wasteful, and a stable sort would still permute nothing.
                                java.util.Comparator<Variant> comparator = order.comparator();
                                java.util.Comparator<Variant> directed = ascending ? comparator : comparator.reversed();
                                filtered = matches.stream().sorted(directed).toList();
                        }
                        state.replaceResults(filtered, data.revision(), query, order, ascending, filter);
                }
                // NEU's render-time page clamp: a shrinking result set or a resize
                // pulls the page back onto the reachable range.
                if (metrics != null) {
                        state.setPage(BrowserGeometry.clampPage(state.page(), state.results().size(), metrics));
                }
        }

        /** The search bar's live query — one search system, shared with slot matching. */
        private static String normalizedQuery() {
                return ZeaClient.search() == null ? "" : ZeaClient.search().query().trim();
        }

        // ------------------------------------------------------------------
        // Interaction
        // ------------------------------------------------------------------

        /**
         * Routes a click. Returns true when the pane consumed it — over
         * the pane, the tab or the sort bar — so the click never reaches a
         * slot. A tile click opens the market viewer (NEU's LMB action); a
         * selection never issues a command.
         */
        public boolean click(AbstractContainerScreen<?> screen, double mouseX, double mouseY) {
                int mx = paneMouse(mouseX);
                int my = paneMouse(mouseY);
                BrowserGeometry.Metrics m = metrics;
                if (m == null) {
                        return false;
                }
                // NEU's factor gate: everything right of the pane's animated edge
                // is the pane's — handled when open, swallowed while it slides.
                if (!BrowserGeometry.inPane(itemFactor, m, mx, my)) {
                        return false;
                }
                if (!state.isOpen()) {
                        return true;
                }
                for (int[] button : BrowserGeometry.navButtons(m, paneLeft,
                                BrowserGeometry.maxPages(state.results().size(), m), boldWidths(screen.getFont()))) {
                        if (BrowserGeometry.contains(button[0], button[1], button[2], button[3], mx, my)) {
                                handleNavButton(button[4]);
                                playPressSound();
                                return true;
                        }
                }
                int index = BrowserGeometry.indexAt(mx, my, m, paneLeft, state.page(), state.results().size());
                if (index >= 0 && index < state.results().size()) {
                        // The viewer opens over the container and restores it
                        // on close — JEI's open() semantics.
                        MarketViewerScreen.open(state.results().get(index), state.results());
                        return true;
                }
                clickSortBar(m, mx, my);
                return true; // clicks inside the pane never reach slots
        }

        /** One container-gui coordinate in pane units — the render's inverse mapping. */
        private int paneMouse(double gui) {
                return Math.round((float) (gui / unitToGui));
        }

        /** The bold-text width seam the nav math needs. */
        private static BrowserGeometry.FontMetrics boldWidths(Font font) {
                return text -> font.width(Component.literal(text).withStyle(style -> style.withBold(true)));
        }

        private void handleNavButton(int kind) {
                if (kind == 0) {
                        state.setPage(state.page() - 1);
                } else if (kind == 2) {
                        state.setPage(state.page() + 1);
                }
        }

        /**
         * The sort bar's click cycles, the behavior the buttons promise:
         *
         * <ul>
         *   <li><b>Alphabetical</b> — relevance → A→Z → Z→A → relevance.
         *       NEU's green arrow (pointing down) marks A→Z, the red one
         *       (pointing up) marks Z→A, and relevance shows no arrow.</li>
         *   <li><b>Rarity</b> — relevance → Common→Legendary →
         *       Legendary→Common → relevance, through ZEA's rarity
         *       engine.</li>
         *   <li><b>Price</b> — relevance → highest price first → cheapest
         *       first → relevance.</li>
         *   <li><b>Category filters</b> — select the bucket; clicking the
         *       active one (or All) returns to everything.</li>
         * </ul>
         */
        private void clickSortBar(BrowserGeometry.Metrics m, int mx, int my) {
                ItemBrowserState.Order[] orders = {
                                ItemBrowserState.Order.ALPHABETICAL,
                                ItemBrowserState.Order.RARITY,
                                ItemBrowserState.Order.PRICE
                };
                boolean[] opensAscending = {true, true, false};
                for (int[] icon : BrowserGeometry.orderIcons(m, paneLeft, ORDER_ICON_COUNT)) {
                        if (BrowserGeometry.contains(icon[0], icon[1], icon[2], icon[2], mx, my)
                                        && icon[3] < orders.length) {
                                state.cycleOrder(orders[icon[3]], opensAscending[icon[3]]);
                                playPressSound();
                                return;
                        }
                }
                ItemBrowserState.Filter[] filters = {
                                ItemBrowserState.Filter.ALL,
                                ItemBrowserState.Filter.WEAPONS,
                                ItemBrowserState.Filter.ARMOR,
                                ItemBrowserState.Filter.GENERAL,
                                ItemBrowserState.Filter.EARTH
                };
                for (int[] icon : BrowserGeometry.filterIcons(m, paneLeft, FILTER_ICON_COUNT)) {
                        if (BrowserGeometry.contains(icon[0], icon[1], icon[2], icon[2], mx, my)
                                        && icon[3] < filters.length) {
                                state.cycleFilter(filters[icon[3]]);
                                playPressSound();
                                return;
                        }
                }
        }

        /** The pane's UI click, shared with the search bar's mode button. */
        public static void playUiClick() {
                playPressSound();
        }

        /** NEU's press sound on the pane's buttons. */
        private static void playPressSound() {
                Minecraft.getInstance().getSoundManager().play(
                                SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        }

        /**
         * A wheel scroll over the pane turns the grid's page, NEU's paging
         * with the wheel added. The return says whether the scroll was
         * consumed.
         */
        public boolean scroll(double mouseX, double mouseY, double delta) {
                int mx = paneMouse(mouseX);
                int my = paneMouse(mouseY);
                BrowserGeometry.Metrics m = metrics;
                if (m == null) {
                        return false;
                }
                if (!state.isOpen() || !BrowserGeometry.inPane(itemFactor, m, mx, my)) {
                        return false;
                }
                state.setPage(state.page() + (delta < 0 ? 1 : -1));
                return true;
        }

        /** Whether the point sits over the pane (or the closed pane's tab). */
        public boolean hoversPanel(double mouseX, double mouseY) {
                BrowserGeometry.Metrics m = metrics;
                if (m == null) {
                        return false;
                }
                int mx = paneMouse(mouseX);
                int my = paneMouse(mouseY);
                if (!state.isOpen()) {
                        return BrowserGeometry.tabHover(m.screenWidth,
                                        Math.round(tabOffset.getValue()), mx, my, m.screenHeight);
                }
                return BrowserGeometry.inPane(itemFactor, m, mx, my);
        }
}
