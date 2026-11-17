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
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * The inventory shop-index browser: the NEU-style item panel on the
 * container GUI's right side, showing every exact item variant observed
 * in EarthMC shops, and the physical shops behind the one selected.
 *
 * <p>The panel renders from the {@link ItemVariantIndex} over the
 * market's recorded observations; the icons are the actual variants —
 * each token rebuilds its real display stack through
 * {@link VariantStacks}, glint included. The search bar ZEA already
 * draws under the GUI drives the filter: the panel reads the same
 * session query the slot matching reads, so there is one search
 * system, not two. A tile's tooltip carries the identity, the shop and
 * owner counts and the best observed prices per side; clicking a tile
 * opens the variant's detail — its market summary, its offers grouped
 * by transaction direction, each offer independently selectable.
 * Selecting an offer never issues a command; navigation is a separate
 * action.
 *
 * <p>Rendering reads caches only: the variant results rebuild when the
 * market revision or the search query changes, the detail's town names
 * resolve when the detail opens, and the display stacks cache by
 * token. Nothing here touches disk, the network or the world.
 */
public final class ItemBrowser {
	private static final int FILL = 0xF0101018;
	private static final int BORDER = 0xFF505050;
	private static final int HEADER_FILL = 0xFF181822;
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

	// Per-frame geometry, recomputed from the live GUI rectangle.
	private int panelLeft;
	private int panelTop;
	private int panelWidth;
	private int panelHeight;
	private int columns;
	// The detail overlay's geometry while it shows.
	private int detailLeft;
	private int detailTop;
	private int detailWidth;
	private int detailHeight;
	private int detailListTop;
	private int detailRowHeight = 22;
	private int detailRows;
	// The offer rows' geometry for hit-testing and tooltips.
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
	 * Draws the panel for this frame. Called from the container mixin's
	 * render tail, after ZEA's buttons — the panel draws over vanilla
	 * furniture on narrow screens by design.
	 */
	public void render(GuiGraphicsExtractor graphics, AbstractContainerScreen<?> screen,
			int mouseX, int mouseY) {
		MarketSubsystem market = ZeaClient.market();
		if (market == null) {
			return;
		}
		computePanelGeometry(screen);
		refreshResults(market.data());
		graphics.fill(panelLeft, panelTop, panelLeft + panelWidth, panelTop + panelHeight, FILL);
		drawBorder(graphics);
		drawHeader(graphics, screen.getFont(), mouseX, mouseY);
		if (state.view() == ItemBrowserState.View.DETAIL) {
			drawGrid(graphics, screen.getFont(), mouseX, mouseY);
			drawDetail(graphics, screen, mouseX, mouseY);
		} else {
			drawGrid(graphics, screen.getFont(), mouseX, mouseY);
		}
	}

	private void computePanelGeometry(AbstractContainerScreen<?> screen) {
		// The GUI rectangle comes through the duck interface: protected
		// fields are the mixin's business, the browser reads the bridge.
		net.zaminmc.zea.duck.ContainerScreenAccess gui =
				(net.zaminmc.zea.duck.ContainerScreenAccess) screen;
		int guiRight = gui.zea$leftPos() + gui.zea$imageWidth();
		columns = BrowserGeometry.columns(guiRight, screen.width);
		panelWidth = BrowserGeometry.width(columns);
		panelLeft = BrowserGeometry.left(guiRight, screen.width, columns);
		panelTop = BrowserGeometry.top(gui.zea$topPos(), screen.height);
		panelHeight = BrowserGeometry.height(gui.zea$topPos(), gui.zea$imageHeight(), screen.height);
	}

	private void drawBorder(GuiGraphicsExtractor graphics) {
		graphics.fill(panelLeft, panelTop, panelLeft + panelWidth, panelTop + 1, BORDER);
		graphics.fill(panelLeft, panelTop + panelHeight - 1, panelLeft + panelWidth,
				panelTop + panelHeight, BORDER);
		graphics.fill(panelLeft, panelTop, panelLeft + 1, panelTop + panelHeight, BORDER);
		graphics.fill(panelLeft + panelWidth - 1, panelTop, panelLeft + panelWidth,
				panelTop + panelHeight, BORDER);
	}

	private void drawHeader(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
		graphics.fill(panelLeft, panelTop, panelLeft + panelWidth, panelTop + BrowserGeometry.HEADER,
				HEADER_FILL);
		graphics.text(font, "Shop Index", panelLeft + BrowserGeometry.PADDING,
				panelTop + 4, ACCENT, true);
		List<int[]> buttons = BrowserGeometry.headerButtons(panelLeft, panelTop, panelWidth);
		// Page arrows, then the close: ‹ › ✕, drawn as glyphs.
		graphics.text(font, "‹", buttons.get(2)[0] + 3, buttons.get(2)[1], VALUE, true);
		graphics.text(font, "›", buttons.get(1)[0] + 3, buttons.get(1)[1], VALUE, true);
		graphics.text(font, "✕", buttons.get(0)[0] + 3, buttons.get(0)[1], DIM, true);
	}

	private void drawGrid(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
		List<Variant> results = state.results();
		int rows = BrowserGeometry.rows(panelHeight);
		int gridLeft = BrowserGeometry.gridLeft(panelLeft);
		int gridTop = BrowserGeometry.gridTop(panelTop);
		int scroll = state.scrollRows();
		int visible = BrowserGeometry.visibleRows(panelHeight, scroll, results.size());
		graphics.enableScissor(panelLeft + 1, gridTop - 1, panelLeft + panelWidth - 1,
				panelTop + panelHeight - 1);
		for (int index = scroll * columns; index < (scroll + rows) * columns
				&& index < results.size(); index++) {
			Variant variant = results.get(index);
			int row = index / columns - scroll;
			int column = index % columns;
			int x = BrowserGeometry.cellLeft(gridLeft, column);
			int y = BrowserGeometry.cellTop(gridTop, row, scroll);
			boolean hovered = BrowserGeometry.contains(x, y, BrowserGeometry.CELL, BrowserGeometry.CELL,
					mouseX, mouseY);
			if (hovered) {
				graphics.fill(x, y, x + BrowserGeometry.CELL, y + BrowserGeometry.CELL, HOVER_FILL);
			}
			// The actual variant icon: the token's own display stack.
			ItemBrowser.drawVariantIcon(graphics, variant, x, y);
			if (hovered) {
				drawVariantTooltip(graphics, font, variant, mouseX, mouseY);
			}
		}
		graphics.disableScissor();
		// Scroll position hint when the index outgrows the panel.
		if (visible < results.size()) {
			String page = (scroll + 1) + "/" + Math.max(1, (results.size() + rows - 1) / rows);
			graphics.text(font, page, panelLeft + panelWidth - BrowserGeometry.PADDING
					- font.width(page), panelTop + panelHeight - 10, DIM, true);
		}
		if (results.isEmpty()) {
			graphics.text(font, "No shops observed yet", gridLeft + 2, gridTop + 4, DIM, true);
		}
	}

	private static void drawVariantIcon(GuiGraphicsExtractor graphics, Variant variant, int x, int y) {
		VariantStacks.Resolved resolved = VariantStacks.of(variant.token());
		if (!resolved.stack().isEmpty()) {
			graphics.item(resolved.stack(), x + 1, y + 1);
		}
		if (!resolved.exact()) {
			// An approximate icon is never presented as the exact one.
			graphics.fill(x + 1, y + 14, x + 7, y + 15, APPROX);
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
		computeDetailGeometry(screen);
		graphics.fill(detailLeft, detailTop, detailLeft + detailWidth, detailTop + detailHeight, FILL);
		drawDetailBorder(graphics);
		drawDetailHeader(graphics, font, variant, mouseX, mouseY);
		drawDetailBody(graphics, font, variant, mouseX, mouseY);
	}

	private void computeDetailGeometry(AbstractContainerScreen<?> screen) {
		detailWidth = Math.min(520, screen.width - 16);
		detailHeight = Math.min(340, screen.height - 16);
		detailLeft = (screen.width - detailWidth) / 2;
		detailTop = (screen.height - detailHeight) / 2;
		detailListTop = detailTop + 148;
		detailRowHeight = 22;
	}

	private void drawDetailBorder(GuiGraphicsExtractor graphics) {
		graphics.fill(detailLeft, detailTop, detailLeft + detailWidth, detailTop + 1, BORDER);
		graphics.fill(detailLeft, detailTop + detailHeight - 1, detailLeft + detailWidth,
				detailTop + detailHeight, BORDER);
		graphics.fill(detailLeft, detailTop, detailLeft + 1, detailTop + detailHeight, BORDER);
		graphics.fill(detailLeft + detailWidth - 1, detailTop, detailLeft + detailWidth,
				detailTop + detailHeight, BORDER);
	}

	private void drawDetailHeader(GuiGraphicsExtractor graphics, Font font, Variant variant,
			int mouseX, int mouseY) {
		graphics.fill(detailLeft, detailTop, detailLeft + detailWidth, detailTop + 20, HEADER_FILL);
		graphics.text(font, variant.display(), detailLeft + 40, detailTop + 6, ACCENT, true);
		int right = detailLeft + detailWidth - 6;
		graphics.text(font, "✕", right - 6, detailTop + 6, DIM, true);
	}

	private void drawDetailBody(GuiGraphicsExtractor graphics, Font font, Variant variant,
			int mouseX, int mouseY) {
		// Identity: the actual stack, its exactness, the variant text.
		VariantStacks.Resolved resolved = VariantStacks.of(variant.token());
		graphics.item(resolved.stack(), detailLeft + 10, detailTop + 28);
		int textX = detailLeft + 40;
		graphics.text(font, "Exact variant", textX, detailTop + 30, LABEL, true);
		List<String> enchantments = variant.enchantments();
		String variantText = enchantments.isEmpty() ? "No enchantments"
				: String.join(", ", enchantments);
		graphics.text(font, variantText, textX, detailTop + 42, VALUE, true);
		if (!resolved.exact()) {
			graphics.text(font, "Icon approximate — the exact components did not load",
					textX, detailTop + 54, APPROX, true);
		}
		drawDetailSummary(graphics, font, variant, detailTop + 74);
		drawDetailOffers(graphics, font, variant, mouseX, mouseY);
	}

	private void drawDetailSummary(GuiGraphicsExtractor graphics, Font font, Variant variant, int y) {
		VariantPricing.SideStats buyFrom = VariantPricing.stats(variant.offers(), ShopDirection.SELLING);
		VariantPricing.SideStats sellTo = VariantPricing.stats(variant.offers(), ShopDirection.BUYING);
		int left = detailLeft + 10;
		int right = detailLeft + detailWidth / 2 + 6;
		graphics.text(font, "BUY FROM", left, y, SELL_TAG, true);
		graphics.text(font, "best " + priceOrDash(buyFrom.best())
				+ (buyFrom.median() == null ? "" : " · median " + format(buyFrom.median()))
				+ " · " + buyFrom.shops() + " shops · " + buyFrom.owners() + " owners",
				left + 64, y, VALUE, true);
		graphics.text(font, "SELL TO", right, y, ACCENT, true);
		graphics.text(font, "best " + priceOrDash(sellTo.best())
				+ (sellTo.median() == null ? "" : " · median " + format(sellTo.median()))
				+ " · " + sellTo.shops() + " shops · " + sellTo.owners() + " owners",
				right + 56, y, VALUE, true);
	}

	private void drawDetailOffers(GuiGraphicsExtractor graphics, Font font, Variant variant,
			int mouseX, int mouseY) {
		int scrollPx = Math.max(0, state.detailScroll());
		List<Offer> buyFrom = new ArrayList<>(offersOf(variant, ShopDirection.SELLING));
		buyFrom.sort(Comparator.comparing(Offer::unitPrice,
				Comparator.nullsLast(Comparator.naturalOrder())));
		List<Offer> sellTo = new ArrayList<>(offersOf(variant, ShopDirection.BUYING));
		sellTo.sort(Comparator.comparing(Offer::unitPrice,
				Comparator.nullsLast(Comparator.reverseOrder())));
		detailRowsGeom.clear();
		int y = detailListTop - scrollPx;
		int listBottom = detailTop + detailHeight - 26;
		graphics.enableScissor(detailLeft + 1, detailListTop, detailLeft + detailWidth - 1, listBottom);
		if (!buyFrom.isEmpty()) {
			graphics.text(font, "BUY FROM — cheapest first", detailLeft + 10, y, SELL_TAG, true);
			y += 14;
			for (Offer offer : buyFrom) {
				if (y + detailRowHeight > detailListTop && y < listBottom) {
					drawOfferRow(graphics, font, offer, y, mouseX, mouseY);
					detailRowsGeom.add(new Row(detailLeft + 6, y, detailWidth - 12,
							detailRowHeight, offer));
				}
				y += detailRowHeight;
			}
			y += 8;
		}
		if (!sellTo.isEmpty()) {
			graphics.text(font, "SELL TO — highest payout first", detailLeft + 10, y, ACCENT, true);
			y += 14;
			for (Offer offer : sellTo) {
				if (y + detailRowHeight > detailListTop && y < listBottom) {
					drawOfferRow(graphics, font, offer, y, mouseX, mouseY);
					detailRowsGeom.add(new Row(detailLeft + 6, y, detailWidth - 12,
							detailRowHeight, offer));
				}
				y += detailRowHeight;
			}
		}
		graphics.disableScissor();
		drawNavigateFooter(graphics, font, mouseX, mouseY);
	}

	/**
	 * The navigation footer: the selected offer's Navigate action.
	 * Selecting an offer never navigates — this button is the one thing
	 * that does, and it is explicit.
	 */
	private void drawNavigateFooter(GuiGraphicsExtractor graphics, Font font,
			int mouseX, int mouseY) {
		int bx = detailLeft + 10;
		int by = detailTop + detailHeight - 20;
		boolean selected = state.selectedShopId() != null;
		int color = selected ? ACCENT : DIM;
		graphics.text(font, "[Navigate to selected shop]", bx, by, color, true);
		navigateButton = new int[] {bx - 2, by - 2, font.width("[Navigate to selected shop]") + 4, 16};
	}

	private void drawOfferRow(GuiGraphicsExtractor graphics, Font font, Offer offer, int y,
			int mouseX, int mouseY) {
		int x = detailLeft + 6;
		boolean selected = offer.shop().shopId().equals(state.selectedShopId());
		boolean hovered = BrowserGeometry.contains(x, y, detailWidth - 12, detailRowHeight,
				mouseX, mouseY);
		graphics.fill(x, y, x + detailWidth - 12, y + detailRowHeight - 2,
				selected ? SELECTED_FILL : hovered ? HOVER_FILL : 0x00000000);
		ShopObservation observation = offer.observation();
		// Direction first, by label — color never carries the meaning alone.
		String tag = observation.direction() == ShopDirection.SELLING ? "[BUY FROM]" : "[SELL TO]";
		graphics.text(font, tag, x + 2, y + 2,
				observation.direction() == ShopDirection.SELLING ? SELL_TAG : ACCENT, true);
		String owner = offer.shop().owner() == null ? "?" : offer.shop().owner();
		graphics.text(font, owner, x + 76, y + 2, VALUE, true);
		graphics.text(font, priceOrDash(offer.unitPrice()) + priceNote(observation),
				x + 76 + font.width(owner) + 8, y + 2, VALUE, true);
		String stock = stockLine(observation);
		if (!stock.isEmpty()) {
			graphics.text(font, stock, x + 2, y + 12, LABEL, true);
		}
		String where = whereLine(offer);
		graphics.text(font, where, x + 150, y + 12, LABEL, true);
		graphics.text(font, MarketAge.format(observation.observedAt(), Instant.now())
				+ (observation.sourceType() == SourceType.REMOTE_SHARED ? " · shared" : ""),
				detailLeft + detailWidth - 96, y + 2, DIM, true);
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
	// Data
	// ------------------------------------------------------------------

	private void refreshResults(MarketData data) {
		String query = normalizedQuery();
		if (!state.isCurrent(data.revision(), query)) {
			List<Variant> all = ItemVariantIndex.variants(data);
			String needle = query.toLowerCase(Locale.ROOT);
			List<Variant> filtered = needle.isEmpty() ? all
					: all.stream()
							.filter(variant -> variant.searchText().contains(needle))
							.toList();
			state.replaceResults(filtered, all, data.revision(), query);
			state.setScrollRows(Math.min(state.scrollRows(),
					BrowserGeometry.clampScroll(state.scrollRows(), filtered.size(), panelHeight)));
		}
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
	 * Routes a click. Returns true when the browser consumed it — over
	 * the panel or the detail overlay — so the click never reaches a
	 * slot. A tile click opens the detail; an offer click selects it;
	 * a selection is never a command.
	 */
	public boolean click(AbstractContainerScreen<?> screen, double mouseX, double mouseY) {
		int mx = (int) mouseX;
		int my = (int) mouseY;
		if (state.view() == ItemBrowserState.View.DETAIL) {
			return clickDetail(mx, my);
		}
		if (!insidePanel(mx, my)) {
			return false;
		}
		for (int[] button : BrowserGeometry.headerButtons(panelLeft, panelTop, panelWidth)) {
			if (BrowserGeometry.contains(button[0], button[1], button[2], button[3], mx, my)) {
				handleHeaderButton(button[4]);
				return true;
			}
		}
		int gridTop = BrowserGeometry.gridTop(panelTop);
		int index = BrowserGeometry.indexAt(mx, my, panelLeft, panelTop, columns,
				state.scrollRows(), state.results().size());
		if (index >= 0 && index < state.results().size()) {
			state.openDetail(state.results().get(index).token());
			openDetailContext();
			return true;
		}
		if (my >= gridTop && my < panelTop + panelHeight) {
			return true; // clicks inside the panel body never reach slots
		}
		return false;
	}

	private boolean clickDetail(int mx, int my) {
		// Close button.
		int right = detailLeft + detailWidth - 6;
		if (BrowserGeometry.contains(right - 10, detailTop + 2, 12, 16, mx, my)) {
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
		if (BrowserGeometry.contains(detailLeft, detailTop, detailWidth, detailHeight, mx, my)) {
			return true; // inside the overlay, on no row: consumed
		}
		// Outside the overlay closes it, like NEU's item views.
		state.closeDetail();
		return false;
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

	private void handleHeaderButton(int kind) {
		switch (kind) {
			case 0 -> state.close(); // the close: the next inventory reopens
			case 1 -> state.setScrollRows(state.scrollRows() - 1); // page up
			case 2 -> state.setScrollRows(state.scrollRows() + 1); // page down
			default -> {
			}
		}
	}

	/**
	 * A wheel scroll over the panel scrolls it; the return says whether
	 * the scroll was consumed. Panel scrolling never touches the GUI's
	 * own scrollables.
	 */
	public boolean scroll(double mouseX, double mouseY, double delta) {
		int mx = (int) mouseX;
		int my = (int) mouseY;
		if (state.view() == ItemBrowserState.View.DETAIL
				&& BrowserGeometry.contains(detailLeft, detailTop, detailWidth, detailHeight, mx, my)) {
			state.setDetailScroll(state.detailScroll() + (delta < 0 ? 12 : -12));
			return true;
		}
		if (insidePanel(mx, my)) {
			state.setScrollRows(state.scrollRows() + (delta < 0 ? 1 : -1));
			return true;
		}
		return false;
	}

	/** Whether the point sits over the panel (or the open detail overlay). */
	public boolean hoversPanel(double mouseX, double mouseY) {
		int mx = (int) mouseX;
		int my = (int) mouseY;
		if (state.view() == ItemBrowserState.View.DETAIL
				&& BrowserGeometry.contains(detailLeft, detailTop, detailWidth, detailHeight, mx, my)) {
			return true;
		}
		return insidePanel(mx, my);
	}

	private boolean insidePanel(int mx, int my) {
		return BrowserGeometry.contains(panelLeft, panelTop, panelWidth, panelHeight, mx, my);
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
