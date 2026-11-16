package net.zaminmc.zea.market.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.zaminmc.zea.ZeaClient;
import net.zaminmc.zea.market.MarketData;
import net.zaminmc.zea.market.MarketListings;
import net.zaminmc.zea.market.MarketSort;
import net.zaminmc.zea.market.MarketSubsystem;
import net.zaminmc.zea.market.MarketTravel;
import net.zaminmc.zea.market.model.PhysicalShop;
import net.zaminmc.zea.market.model.PriceScope;
import net.zaminmc.zea.market.model.ShopDirection;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The ZEA Market browser: a full-height, two-panel interface over the
 * observed physical shops — the listing collection on the left, the
 * selected shop's details on the right.
 *
 * <p>Everything the interface shows comes from the live in-memory
 * market index through {@link MarketListings#query}: real shops the
 * player actually inspected, text- and direction-filtered, ordered by
 * the direction-aware sorts. Rendering never parses, reads disk,
 * touches the network or scans the world — the query is in-memory and
 * runs on user actions and a slow tick, never per frame. An empty
 * market says what to do about it; a stale observation says how old it
 * is; nothing is invented for a field the evidence does not carry.
 *
 * <p>Sizing is adaptive (about three quarters of the logical screen,
 * capped and floored) and centered, NEU-style: information-dense rows
 * with item icon, direction tag, offer and shop line; selection is
 * explicit and survives refreshes by shop identity; the only
 * transaction in the whole interface is the nation spawn command, and
 * only when the listing's nation is actually known.
 */
public final class MarketScreen extends Screen {
	// ---- adaptive window geometry -------------------------------------
	private static final int MAX_WIDTH = 1120;
	private static final int MAX_HEIGHT = 720;
	private static final int MIN_WIDTH = 560;
	private static final int MIN_HEIGHT = 360;
	private static final float SCREEN_FRACTION = 0.72f;

	// ---- layout metrics -----------------------------------------------
	private static final int HEADER_HEIGHT = 26;
	private static final int SEARCH_ROW_HEIGHT = 24;
	private static final int TABS_ROW_HEIGHT = 20;
	private static final int STATUS_BAR_HEIGHT = 16;
	private static final int PADDING = 8;
	private static final int DETAILS_WIDTH = 270;
	private static final int LIST_GAP = 6;
	private static final int ROW_HEIGHT = 40;
	private static final int TAB_WIDTH = 78;
	private static final int BUTTON_HEIGHT = 16;
	private static final int SEARCH_HEIGHT = 18;

	private static final int FILL = 0xF0101018;
	private static final int BORDER = 0xFF505050;
	private static final int SECTION_FILL = 0xFF181822;
	private static final int LABEL = 0xFF8A8A8A;
	private static final int VALUE = 0xFFE0E0E0;
	private static final int DIM = 0xFF606060;
	private static final int ACCENT = 0xFF55FFAA;
	private static final int WARN = 0xFFFFAA55;
	private static final int STALE = 0xFF909090;
	private static final int IDLE_FILL = 0xFF2A2A34;
	private static final int HOVER_FILL = 0xFF32323E;
	private static final int ACTIVE_FILL = 0xFF3E4E44;
	private static final int SELECTED_FILL = 0xFF2E3E44;

	private static final DateTimeFormatter CLOCK = DateTimeFormatter
			.ofLocalizedDateTime(FormatStyle.MEDIUM).withLocale(Locale.getDefault());

	// ---- state ---------------------------------------------------------
	private final MarketListings.@Nullable TownLookup towns;
	private List<MarketListings.Listing> results = List.of();
	// The selected listing's shop identity: selection survives refreshes
	// and re-sorts by identity, not by row index.
	private @Nullable String selectedShopId;
	private MarketListings.Filter filter = MarketListings.Filter.ALL;
	private MarketSort sort = MarketSort.RECENTLY_OBSERVED;
	private int scroll;
	private int refreshCooldown;
	private EditBox searchBox;

	// ---- per-frame interaction geometry --------------------------------
	private int left;
	private int top;
	private int panelWidth;
	private int panelHeight;
	private int listLeft;
	private int listTop;
	private int listWidth;
	private int listHeight;
	private int listBottom;
	private final List<RowBox> rows = new ArrayList<>();
	private final List<ButtonBox> tabBoxes = new ArrayList<>();
	private @Nullable ButtonBox refreshButton;
	private @Nullable ButtonBox closeButton;
	private @Nullable ButtonBox clearSearchButton;
	private @Nullable ButtonBox sortButton;
	private @Nullable ButtonBox clearResultsButton;
	private @Nullable ButtonBox travelButton;

	public MarketScreen() {
		super(Component.translatable("zea.market.browser.title"));
		MarketSubsystem market = ZeaClient.market();
		this.towns = market == null ? null : market.townLookup();
	}

	@Override
	protected void init() {
		computeGeometry();
		if (searchBox == null) {
			searchBox = new EditBox(font, left + PADDING, top + HEADER_HEIGHT + PADDING,
					10, SEARCH_HEIGHT, Component.translatable("zea.market.browser.search"));
			searchBox.setMaxLength(64);
			searchBox.setResponder(text -> refresh());
			setFocused(searchBox);
		}
		searchBox.setX(left + PADDING);
		searchBox.setY(top + HEADER_HEIGHT + PADDING);
		searchBox.setWidth(contentWidth() - 90);
		refresh();
	}

	/**
	 * About three quarters of the logical screen, capped at the design
	 * maximum, floored at a usable minimum, centered. Re-derived on
	 * every resize, so GUI-scale changes keep everything reachable.
	 */
	private void computeGeometry() {
		// The caps come first, the floors second: the design minimum is
		// itself capped by the available screen, so a small logical GUI
		// (large GUI scale) fills the screen instead of overflowing it.
		int widthCap = Math.min(MAX_WIDTH, this.width - 16);
		int heightCap = Math.min(MAX_HEIGHT, this.height - 16);
		panelWidth = clamp((int) (this.width * SCREEN_FRACTION),
				Math.min(MIN_WIDTH, widthCap), widthCap);
		panelHeight = clamp((int) (this.height * SCREEN_FRACTION),
				Math.min(MIN_HEIGHT, heightCap), heightCap);
		left = (this.width - panelWidth) / 2;
		top = (this.height - panelHeight) / 2;
		listLeft = left + PADDING;
		listTop = top + HEADER_HEIGHT + SEARCH_ROW_HEIGHT + TABS_ROW_HEIGHT + PADDING;
		listBottom = top + panelHeight - STATUS_BAR_HEIGHT - PADDING;
		listWidth = contentWidth() - DETAILS_WIDTH - LIST_GAP;
		listHeight = listBottom - listTop;
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}

	private int contentWidth() {
		return panelWidth - PADDING * 2;
	}

	// ------------------------------------------------------------------
	// Data pipeline
	// ------------------------------------------------------------------

	private void refresh() {
		MarketSubsystem market = ZeaClient.market();
		MarketData data = market == null ? null : market.data();
		List<MarketListings.Listing> fresh = data == null ? List.of()
				: MarketListings.query(data, query(), filter, sort, towns);
		// Selection follows the shop, not the row: a refresh that keeps
		// the selected shop keeps it selected, wherever it now sorts.
		String selected = selectedShopId;
		results = fresh;
		selectedShopId = selected != null && fresh.stream().anyMatch(row -> row.shop().shopId().equals(selected))
				? selected : null;
		scroll = Math.min(scroll, Math.max(0, results.size() - visibleRows()));
	}

	private String query() {
		return searchBox == null ? "" : searchBox.getValue();
	}

	private int visibleRows() {
		return Math.max(1, listHeight / ROW_HEIGHT);
	}

	private int maxScroll() {
		return Math.max(0, results.size() - visibleRows());
	}

	// ------------------------------------------------------------------
	// Rendering
	// ------------------------------------------------------------------

	@Override
	public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
		context.fill(left - 1, top - 1, left + panelWidth + 1, top + panelHeight + 1, BORDER);
		context.fill(left, top, left + panelWidth, top + panelHeight, FILL);

		drawHeader(context, mouseX, mouseY);
		drawSearchRow(context, mouseX, mouseY);
		drawTabsRow(context, mouseX, mouseY);
		drawList(context, mouseX, mouseY);
		drawDetails(context, mouseX, mouseY);
		drawStatusBar(context);
	}

	private void drawHeader(GuiGraphicsExtractor context, int mouseX, int mouseY) {
		// The market's icon: an emerald — the trading item, drawn from
		// the registry like every other icon in this screen.
		ItemStack icon = iconOf("minecraft:emerald");
		if (!icon.isEmpty()) {
			context.item(icon, left + PADDING, top + 5);
		}
		context.text(font, Component.translatable("zea.market.browser.title"),
				left + PADDING + 20, top + 9, VALUE, true);
		int right = left + panelWidth - PADDING;
		refreshButton = headerButton(context, mouseX, mouseY, right - 58,
				Component.translatable("zea.market.browser.refresh"));
		closeButton = headerButton(context, mouseX, mouseY, right - 26,
				Component.translatable("zea.market.browser.close"));
	}

	private ButtonBox headerButton(GuiGraphicsExtractor context, int mouseX, int mouseY,
			int x, Component label) {
		int width = font.width(label) + 10;
		boolean hovered = mouseX >= x && mouseX < x + width
				&& mouseY >= top + 5 && mouseY < top + 5 + BUTTON_HEIGHT;
		context.fill(x, top + 5, x + width, top + 5 + BUTTON_HEIGHT, hovered ? HOVER_FILL : IDLE_FILL);
		context.text(font, label, x + 5, top + 9, VALUE, false);
		return new ButtonBox(x, top + 5, width, BUTTON_HEIGHT, true);
	}

	private void drawSearchRow(GuiGraphicsExtractor context, int mouseX, int mouseY) {
		int y = top + HEADER_HEIGHT + PADDING;
		if (searchBox != null) {
			searchBox.extractRenderState(context, mouseX, mouseY, 0);
		}
		// The clear action exists exactly while there is something to clear.
		if (!query().isBlank()) {
			int x = searchBox.getX() + searchBox.getWidth() + 6;
			int width = font.width(Component.translatable("zea.market.browser.clear")) + 10;
			boolean hovered = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + SEARCH_HEIGHT;
			context.fill(x, y, x + width, y + SEARCH_HEIGHT, hovered ? HOVER_FILL : IDLE_FILL);
			context.text(font, Component.translatable("zea.market.browser.clear"),
					x + 5, y + 5, VALUE, false);
			clearSearchButton = new ButtonBox(x, y, width, SEARCH_HEIGHT, true);
		} else {
			clearSearchButton = null;
		}
	}

	private void drawTabsRow(GuiGraphicsExtractor context, int mouseX, int mouseY) {
		tabBoxes.clear();
		int y = top + HEADER_HEIGHT + SEARCH_ROW_HEIGHT + 2;
		int x = left + PADDING;
		String[] keys = {"zea.market.browser.filter.all", "zea.market.browser.filter.buy",
				"zea.market.browser.filter.sell"};
		MarketListings.Filter[] filters = MarketListings.Filter.values();
		for (int index = 0; index < keys.length; index++) {
			boolean active = filter == filters[index];
			context.fill(x, y, x + TAB_WIDTH, y + TABS_ROW_HEIGHT - 4,
					active ? ACTIVE_FILL : IDLE_FILL);
			context.text(font, Component.translatable(keys[index]), x + 6, y + 4,
					active ? VALUE : LABEL, false);
			tabBoxes.add(new ButtonBox(x, y, TAB_WIDTH, TABS_ROW_HEIGHT - 4, true));
			x += TAB_WIDTH + 4;
		}
		// The sort control cycles; its label names the active order.
		String sortLabel = Component.translatable("zea.market.browser.sort",
				Component.translatable("zea.market.browser.sort." + sort.name().toLowerCase(Locale.ROOT))).getString();
		int sortWidth = font.width(sortLabel) + 12;
		int sortX = left + panelWidth - PADDING - sortWidth;
		boolean hovered = mouseX >= sortX && mouseX < sortX + sortWidth
				&& mouseY >= y && mouseY < y + TABS_ROW_HEIGHT - 4;
		context.fill(sortX, y, sortX + sortWidth, y + TABS_ROW_HEIGHT - 4, hovered ? HOVER_FILL : IDLE_FILL);
		context.text(font, Component.literal(sortLabel), sortX + 6, y + 4, VALUE, false);
		sortButton = new ButtonBox(sortX, y, sortWidth, TABS_ROW_HEIGHT - 4, true);
	}

	private void drawList(GuiGraphicsExtractor context, int mouseX, int mouseY) {
		context.fill(listLeft - 2, listTop - 2, listLeft + listWidth + 2, listBottom + 2, SECTION_FILL);
		rows.clear();
		if (results.isEmpty()) {
			drawEmptyState(context, mouseX, mouseY);
			return;
		}
		int y = listTop - scroll * ROW_HEIGHT;
		for (int index = 0; index < results.size(); index++) {
			if (y + ROW_HEIGHT < listTop || y > listBottom) {
				y += ROW_HEIGHT;
				continue;
			}
			MarketListings.Listing listing = results.get(index);
			boolean isSelected = listing.shop().shopId().equals(selectedShopId);
			boolean hovered = mouseX >= listLeft && mouseX < listLeft + listWidth
					&& mouseY >= y && mouseY < y + ROW_HEIGHT - 2;
			int fill = isSelected ? SELECTED_FILL : hovered ? HOVER_FILL : FILL;
			context.fill(listLeft, y, listLeft + listWidth, y + ROW_HEIGHT - 2, fill);
			if (isSelected) {
				drawBorder(context, listLeft, y, listWidth, ROW_HEIGHT - 2, ACCENT);
			}
			drawRow(context, listing, y);
			rows.add(new RowBox(y, listing));
			y += ROW_HEIGHT;
		}
		if (maxScroll() > 0) {
			// A minimal scroll indicator: position, not decoration.
			int barHeight = Math.max(12, listHeight * visibleRows() / results.size());
			int barY = listTop + (listHeight - barHeight) * scroll / maxScroll();
			context.fill(listLeft + listWidth - 2, barY, listLeft + listWidth, barY + barHeight, 0xFF808080);
		}
	}

	private void drawRow(GuiGraphicsExtractor context, MarketListings.Listing listing, int y) {
		int x = listLeft + 6;
		ItemStack icon = iconFor(listing);
		if (!icon.isEmpty()) {
			context.item(icon, x, y + (ROW_HEIGHT - 2 - 16) / 2);
		} else {
			context.fill(x, y + 12, x + 16, y + 28, IDLE_FILL);
		}
		int textX = x + 22;
		// Line 1: item name + direction tag (text first, color second —
		// the sides are never communicated by color alone).
		int tagWidth = font.width(directionLabel(listing)) + 8;
		context.text(font, Component.literal(displayName(listing)), textX, y + 4, VALUE, true);
		drawDirectionTag(context, listing, listLeft + listWidth - tagWidth - 6, y + 4, tagWidth);
		// Line 2: the offer, in the shop's own units plus the honest per-item view.
		context.text(font, Component.literal(offerLine(listing)), textX, y + 15, offerColor(listing), false);
		// Line 3: who and where — only what is actually known.
		context.text(font, Component.literal(shopLine(listing)), textX, y + 26, shopLineColor(listing), false);
	}

	private String directionLabel(MarketListings.Listing listing) {
		return Component.translatable(listing.direction() == ShopDirection.SELLING
				? "zea.market.browser.tag.buy_from" : "zea.market.browser.tag.sell_to").getString();
	}

	private void drawDirectionTag(GuiGraphicsExtractor context, MarketListings.Listing listing,
			int x, int y, int width) {
		int color = listing.direction() == ShopDirection.SELLING ? ACCENT : WARN;
		context.fill(x, y - 1, x + width, y + 10, SECTION_FILL);
		context.text(font, Component.literal(directionLabel(listing)), x + 4, y, color, false);
	}

	private String displayName(MarketListings.Listing listing) {
		String display = listing.observation().itemDisplay();
		return display == null || display.isBlank()
				? Component.translatable("zea.market.browser.unknown.item").getString()
				: display;
	}

	private String offerLine(MarketListings.Listing listing) {
		if (listing.observation().price() == null) {
			return Component.translatable("zea.market.browser.no_price").getString();
		}
		StringBuilder line = new StringBuilder(priceText(listing.observation().price()));
		if (listing.observation().priceScope() == PriceScope.PER_BULK && listing.observation().bulkSize() != null) {
			line.append(" per ").append(listing.observation().bulkSize());
		} else if (listing.observation().priceScope() == PriceScope.PER_ITEM) {
			line.append(" each");
		}
		if (listing.unitPrice() != null) {
			line.append("  ·  ").append(priceText(listing.unitPrice())).append("/item");
		}
		return line.toString();
	}

	private static String priceText(BigDecimal price) {
		return price.stripTrailingZeros().toPlainString() + "G";
	}

	private int offerColor(MarketListings.Listing listing) {
		return listing.observation().price() == null ? DIM : VALUE;
	}

	private String shopLine(MarketListings.Listing listing) {
		StringBuilder line = new StringBuilder();
		if (listing.shop().owner() != null) {
			line.append(listing.shop().owner());
		} else {
			line.append(Component.translatable("zea.market.browser.unknown.owner").getString());
		}
		String place = placeLine(listing);
		if (!place.isEmpty()) {
			line.append("  ·  ").append(place);
		}
		return line.toString();
	}

	private String placeLine(MarketListings.Listing listing) {
		StringBuilder place = new StringBuilder();
		if (listing.town() != null) {
			place.append(listing.town());
			if (listing.nation() != null) {
				place.append(", ").append(listing.nation());
			}
		} else if (listing.nation() != null) {
			place.append(listing.nation());
		} else {
			BlockPos sign = listing.shop().signPos();
			place.append(sign.getX()).append(", ").append(sign.getY()).append(", ").append(sign.getZ());
		}
		if (MarketAge.isOlderThanADay(listing.observation().observedAt(), Instant.now())) {
			place.append("  ·  ").append(Component.translatable("zea.market.browser.stale").getString());
		}
		return place.toString();
	}

	private int shopLineColor(MarketListings.Listing listing) {
		return MarketAge.isOlderThanADay(listing.observation().observedAt(), Instant.now()) ? STALE : LABEL;
	}

	/**
	 * The row icon, from the observation's identity: exact tokens carry
	 * a registry id, display-only tokens carry a slug that may still
	 * resolve. Anything unresolvable draws a placeholder box instead of
	 * a wrong item.
	 */
	private ItemStack iconFor(MarketListings.Listing listing) {
		String token = listing.observation().fingerprint().token();
		int separator = token.indexOf('|');
		String id = separator >= 0 ? token.substring(0, separator) : token;
		return iconOf(id);
	}

	private static ItemStack iconOf(String id) {
		Identifier identifier = id.contains(":")
				? Identifier.tryParse(id.toLowerCase(Locale.ROOT))
				: Identifier.tryParse("minecraft:" + id.toLowerCase(Locale.ROOT));
		if (identifier == null) {
			return ItemStack.EMPTY;
		}
		Item item = BuiltInRegistries.ITEM.getValue(identifier);
		return item == null ? ItemStack.EMPTY : new ItemStack(item);
	}

	private void drawEmptyState(GuiGraphicsExtractor context, int mouseX, int mouseY) {
		clearResultsButton = null;
		MarketSubsystem market = ZeaClient.market();
		boolean marketEmpty = market != null && market.data() != null && market.data().shopCount() == 0;
		if (marketEmpty && query().isBlank()) {
			context.centeredText(font, Component.translatable("zea.market.browser.empty.title"),
					listLeft + listWidth / 2, listTop + 20, VALUE);
			context.centeredText(font, Component.translatable("zea.market.browser.empty.observe1"),
					listLeft + listWidth / 2, listTop + 42, LABEL);
			context.centeredText(font, Component.translatable("zea.market.browser.empty.observe2"),
					listLeft + listWidth / 2, listTop + 54, LABEL);
			context.centeredText(font, Component.translatable("zea.market.browser.empty.observe3"),
					listLeft + listWidth / 2, listTop + 66, LABEL);
			return;
		}
		boolean hasQuery = !query().isBlank();
		String titleKey = hasQuery ? "zea.market.browser.empty.nomatch"
				: filter == MarketListings.Filter.BUY_FROM ? "zea.market.browser.empty.nobuy"
				: filter == MarketListings.Filter.SELL_TO ? "zea.market.browser.empty.nosell"
				: "zea.market.browser.empty.none";
		context.centeredText(font, Component.translatable(titleKey),
				listLeft + listWidth / 2, listTop + 30, VALUE);
		if (hasQuery) {
			String clearLabel = Component.translatable("zea.market.browser.empty.clear").getString();
			int width = font.width(clearLabel) + 16;
			int x = listLeft + (listWidth - width) / 2;
			int y = listTop + 50;
			boolean hovered = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + BUTTON_HEIGHT + 4;
			context.fill(x, y, x + width, y + BUTTON_HEIGHT + 4, hovered ? HOVER_FILL : IDLE_FILL);
			context.text(font, Component.literal(clearLabel), x + 8, y + 6, VALUE, false);
			clearResultsButton = new ButtonBox(x, y, width, BUTTON_HEIGHT + 4, true);
		}
	}

	private void drawDetails(GuiGraphicsExtractor context, int mouseX, int mouseY) {
		int detailsLeft = listLeft + listWidth + LIST_GAP;
		int detailsWidth = DETAILS_WIDTH;
		context.fill(detailsLeft - 2, listTop - 2, detailsLeft + detailsWidth + 2, listBottom + 2, SECTION_FILL);

		MarketListings.Listing selected = selected();
		if (selected == null) {
			context.centeredText(font, Component.translatable("zea.market.browser.details.none"),
					detailsLeft + detailsWidth / 2, listTop + 24, LABEL);
			context.centeredText(font, Component.translatable("zea.market.browser.details.hint"),
					detailsLeft + detailsWidth / 2, listTop + 38, DIM);
			travelButton = null;
			return;
		}
		travelButton = null;
		int x = detailsLeft + PADDING;
		int y = listTop + 6;
		ItemStack icon = iconFor(selected);
		if (!icon.isEmpty()) {
			context.item(icon, x, y);
		}
		context.text(font, Component.literal(displayName(selected)), x + 22, y + 1, VALUE, true);
		context.text(font, Component.literal(directionLabel(selected)), x + 22, y + 11,
				selected.direction() == ShopDirection.SELLING ? ACCENT : WARN, false);
		y += 28;

		y = detailLine(context, x, y, "zea.market.browser.details.price", offerLine(selected), VALUE);
		if (selected.observation().stockRaw() != null) {
			String stockKey = selected.observation().stockUnit() == net.zaminmc.zea.market.model.StockUnit.SPACE
					? "zea.market.browser.details.space" : "zea.market.browser.details.stock";
			y = detailLine(context, x, y, stockKey, selected.observation().stockRaw(), VALUE);
		}
		y = detailLine(context, x, y, "zea.market.browser.details.limit", limitLine(selected), VALUE);
		y = detailLine(context, x, y, "zea.market.browser.details.owner", selected.shop().owner(), VALUE);
		y = detailLine(context, x, y, "zea.market.browser.details.town", selected.town(), VALUE);
		y = detailLine(context, x, y, "zea.market.browser.details.nation", selected.shop().nation(), VALUE);
		y = detailLine(context, x, y, "zea.market.browser.details.world", worldLabel(selected.shop().world()), VALUE);
		y = detailLine(context, x, y, "zea.market.browser.details.sign", coords(selected.shop().signPos()), VALUE);
		y = detailLine(context, x, y, "zea.market.browser.details.chest", coords(selected.shop().chestPos()), VALUE);
		y = detailLine(context, x, y, "zea.market.browser.details.observed",
				selected.observation().observedAt() == null ? null
						: CLOCK.format(selected.observation().observedAt().atZone(ZoneId.systemDefault())), VALUE);
		y = detailLine(context, x, y, "zea.market.browser.details.age",
				MarketAge.format(selected.observation().observedAt(), Instant.now()), VALUE);
		y = detailLine(context, x, y, "zea.market.browser.details.source",
				Component.translatable(selected.isServerVerified()
						? "zea.market.browser.details.source.verified"
						: "zea.market.browser.details.source.advert").getString(),
				selected.isServerVerified() ? ACCENT : STALE);

		// The one navigation action: nation spawn, only when the nation
		// is actually known, dispatched as a real command — never text
		// to copy, never a claimed teleport.
		if (selected.shop().nation() != null) {
			String spawnLabel = Component.translatable("zea.market.browser.details.spawn",
					selected.shop().nation()).getString();
			int width = Math.min(detailsWidth - PADDING * 2, font.width(spawnLabel) + 12);
			int buttonY = Math.min(y + 8, listBottom - BUTTON_HEIGHT - 8);
			boolean hovered = mouseX >= x && mouseX < x + width
					&& mouseY >= buttonY && mouseY < buttonY + BUTTON_HEIGHT + 4;
			context.fill(x, buttonY, x + width, buttonY + BUTTON_HEIGHT + 4, hovered ? HOVER_FILL : IDLE_FILL);
			context.text(font, Component.literal(spawnLabel), x + 6, buttonY + 6, ACCENT, false);
			travelButton = new ButtonBox(x, buttonY, width, BUTTON_HEIGHT + 4, true);
		}
	}

	private int detailLine(GuiGraphicsExtractor context, int x, int y, String labelKey,
			@Nullable String value, int color) {
		if (value == null || value.isBlank()) {
			return y;
		}
		context.text(font, Component.translatable(labelKey), x, y, LABEL, false);
		context.text(font, Component.literal(value), x + 68, y, color, false);
		return y + 11;
	}

	private String limitLine(MarketListings.Listing listing) {
		if (listing.direction() == ShopDirection.SELLING) {
			return listing.observation().maxBuyBulks() == null ? null
					: Component.translatable("zea.market.browser.details.limit.buy",
							listing.observation().maxBuyBulks()).getString();
		}
		return listing.observation().maxSellItems() == null ? null
				: Component.translatable("zea.market.browser.details.limit.sell",
						listing.observation().maxSellItems()).getString();
	}

	private String worldLabel(String world) {
		return world == null ? null : world.replace("minecraft:", "").replace("earthmc:", "");
	}

	private String coords(@Nullable BlockPos pos) {
		return pos == null ? null : pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
	}

	private void drawStatusBar(GuiGraphicsExtractor context) {
		int y = top + panelHeight - STATUS_BAR_HEIGHT + 4;
		MarketSubsystem market = ZeaClient.market();
		int shops = market != null && market.data() != null ? market.data().shopCount() : 0;
		context.text(font, Component.translatable("zea.market.browser.status", shops, results.size()),
				left + PADDING, y, DIM, false);
	}

	private void drawBorder(GuiGraphicsExtractor context, int x, int y, int width, int height, int color) {
		context.fill(x, y, x + width, y + 1, color);
		context.fill(x, y + height - 1, x + width, y + height, color);
		context.fill(x, y, x + 1, y + height, color);
		context.fill(x + width - 1, y, x + width, y + height, color);
	}

	/** The selected listing, or {@code null} before a selection is made. */
	private MarketListings.Listing selected() {
		if (selectedShopId == null) {
			return null;
		}
		return results.stream()
				.filter(row -> row.shop().shopId().equals(selectedShopId))
				.findFirst().orElse(null);
	}

	// ------------------------------------------------------------------
	// Interaction
	// ------------------------------------------------------------------

	private record RowBox(int y, MarketListings.Listing listing) {
	}

	private record ButtonBox(int x, int y, int width, int height, boolean enabled) {
		boolean contains(double mouseX, double mouseY) {
			return enabled && mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
		double mouseX = click.x();
		double mouseY = click.y();
		if (closeButton != null && closeButton.contains(mouseX, mouseY)) {
			onClose();
			return true;
		}
		if (refreshButton != null && refreshButton.contains(mouseX, mouseY)) {
			refresh();
			return true;
		}
		if ((clearSearchButton != null && clearSearchButton.contains(mouseX, mouseY))
				|| (clearResultsButton != null && clearResultsButton.contains(mouseX, mouseY))) {
			searchBox.setValue("");
			refresh();
			return true;
		}
		if (sortButton != null && sortButton.contains(mouseX, mouseY)) {
			sort = sort.next();
			refresh();
			return true;
		}
		for (int index = 0; index < tabBoxes.size(); index++) {
			if (tabBoxes.get(index).contains(mouseX, mouseY)) {
				filter = MarketListings.Filter.values()[index];
				refresh();
				return true;
			}
		}
		if (travelButton != null && travelButton.contains(mouseX, mouseY)) {
			MarketListings.Listing selected = selected();
			if (selected != null) {
				MarketTravel.travelToNation(selected.shop());
			}
			return true;
		}
		for (RowBox row : rows) {
			if (mouseY >= row.y() && mouseY < row.y() + ROW_HEIGHT - 2
					&& mouseX >= listLeft && mouseX < listLeft + listWidth) {
				selectedShopId = row.listing().shop().shopId();
				return true;
			}
		}
		// Clicking the search field focuses it; clicking elsewhere lets
		// the arrow keys drive the list.
		if (searchBox != null && mouseX >= searchBox.getX() && mouseX < searchBox.getX() + searchBox.getWidth()
				&& mouseY >= searchBox.getY() && mouseY < searchBox.getY() + SEARCH_HEIGHT) {
			setFocused(searchBox);
			return searchBox.mouseClicked(click, doubled);
		}
		setFocused(null);
		return super.mouseClicked(click, doubled);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		if (!results.isEmpty() && maxScroll() > 0
				&& mouseX >= listLeft && mouseX < listLeft + listWidth
				&& mouseY >= listTop && mouseY < listBottom) {
			scroll = Math.max(0, Math.min(maxScroll(), scroll - (int) Math.signum(verticalAmount)));
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
	}

	@Override
	public boolean keyPressed(KeyEvent input) {
		if (searchBox != null && searchBox.isFocused() && searchBox.keyPressed(input)) {
			return true;
		}
		if (input.key() == 256) {
			onClose();
			return true;
		}
		// Arrow keys walk the listings when the search field is not
		// consuming input; Enter is never a transaction.
		if (!results.isEmpty() && (input.key() == 265 || input.key() == 264)) {
			int current = -1;
			for (int index = 0; index < results.size(); index++) {
				if (results.get(index).shop().shopId().equals(selectedShopId)) {
					current = index;
					break;
				}
			}
			int step = input.key() == 265 ? -1 : 1;
			int next = Math.max(0, Math.min(results.size() - 1, current < 0 ? 0 : current + step));
			if (next != current) {
				selectedShopId = results.get(next).shop().shopId();
				ensureVisible(next);
				return true;
			}
		}
		return super.keyPressed(input);
	}

	private void ensureVisible(int index) {
		if (index < scroll) {
			scroll = index;
		} else if (index >= scroll + visibleRows()) {
			scroll = Math.max(0, index - visibleRows() + 1);
		}
	}

	@Override
	public boolean charTyped(CharacterEvent input) {
		if (searchBox != null && searchBox.isFocused() && searchBox.charTyped(input)) {
			return true;
		}
		return super.charTyped(input);
	}

	@Override
	public void tick() {
		// The index changes while the screen is open (responses keep
		// landing); a slow re-query keeps the view honest without doing
		// work per frame. The search box's responder covers typing.
		if (refreshCooldown-- <= 0) {
			refreshCooldown = 40;
			refresh();
		}
	}
}
