package net.zaminmc.zea.market.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.zaminmc.zea.ZeaClient;
import net.zaminmc.zea.market.MarketData;
import net.zaminmc.zea.market.MarketSubsystem;
import net.zaminmc.zea.market.MarketTravel;
import net.zaminmc.zea.market.model.PhysicalShop;
import net.zaminmc.zea.market.model.ShopDirection;
import net.zaminmc.zea.market.pricing.MarketOffer;
import net.zaminmc.zea.market.search.MarketSearch;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * The ZEA Market browser: search over every observed physical shop,
 * ranked by the best observed offers.
 *
 * <p>Two views. The results list groups the market by item identity and
 * shows each group's best buy and sell offers; selecting a row opens
 * the item's page with every known shop for it — price and unit, owner,
 * location, observation age, and a travel action where a verified
 * destination exists. The market is the local persistent index, so the
 * browser works offline by construction; an empty market says so
 * instead of inventing results.
 *
 * <p>Understated by house rules: no decorative charts, no badges, one
 * accent color per direction. The best offer is prominent; the
 * alternatives are one click away and never hidden.
 */
public final class MarketScreen extends Screen {
	private static final int PANEL_WIDTH = 260;
	private static final int PANEL_HEIGHT = 200;
	private static final int ROW_HEIGHT = 12;
	private static final int LIST_HEIGHT = 150;

	private static final int FILL = 0xF0101018;
	private static final int BORDER = 0xFF505050;
	private static final int LABEL = 0xFF8A8A8A;
	private static final int VALUE = 0xFFE0E0E0;
	private static final int ACCENT = 0xFF55FFAA;
	private static final int WARN = 0xFFFFAA55;
	private static final int IDLE_FILL = 0xFF2A2A34;
	private static final int HOVER_FILL = 0xFF3A3A48;
	private static final int ACTIVE_FILL = 0xFF454558;

	private final MarketSearch search = new MarketSearch();
	private MarketSearch.Filter filter = MarketSearch.Filter.ALL;
	private List<MarketSearch.Grouping> results = List.of();
	private final List<RowBox> resultRows = new ArrayList<>();
	private final List<TravelBox> travelRows = new ArrayList<>();
	// The open item page; null while the results list shows.
	private MarketSearch.Grouping selected;
	private int scroll;
	private int left;
	private int top;

	public MarketScreen() {
		super(Component.translatable("zea.market.browser.title"));
	}

	// One EditBox, like the house screens: the list is drawn text with
	// hand-routed input, not a widget stack.
	private EditBox searchBox;

	@Override
	protected void init() {
		left = (width - PANEL_WIDTH) / 2;
		top = Math.max(8, (height - PANEL_HEIGHT) / 2);
		searchBox = new EditBox(font, left + 8, top + 12, PANEL_WIDTH - 70, 14,
				Component.translatable("zea.market.browser.search"));
		searchBox.setMaxLength(64);
		searchBox.setResponder(text -> refresh());
		refresh();
	}

	private void refresh() {
		MarketSubsystem market = ZeaClient.market();
		MarketData data = market == null ? null : market.data();
		results = data == null ? List.of() : search.search(data, query(), filter);
		scroll = 0;
	}

	private String query() {
		return searchBox == null ? "" : searchBox.getValue();
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
		context.fill(left - 1, top - 1, left + PANEL_WIDTH + 1, top + PANEL_HEIGHT + 1, BORDER);
		context.fill(left, top, left + PANEL_WIDTH, top + PANEL_HEIGHT, FILL);
		if (searchBox != null) {
			searchBox.setX(left + 8);
			searchBox.setY(top + 12);
			searchBox.extractRenderState(context, mouseX, mouseY, delta);
		}

		context.text(font, Component.translatable("zea.market.browser.title"),
				left + 8, top + 3, VALUE, true);
		drawFilterTabs(context, mouseX, mouseY);

		resultRows.clear();
		if (selected != null) {
			drawDetail(context, mouseX, mouseY);
			return;
		}
		if (results.isEmpty()) {
			context.text(font, Component.translatable("zea.market.browser.empty"),
					left + 8, top + 40, LABEL, false);
			return;
		}
		int y = top + 32;
		for (int index = scroll; index < results.size() && y < top + 32 + LIST_HEIGHT; index++) {
			MarketSearch.Grouping grouping = results.get(index);
			boolean hovered = mouseX >= left + 4 && mouseX < left + PANEL_WIDTH - 4
					&& mouseY >= y && mouseY < y + ROW_HEIGHT;
			if (hovered) {
				context.fill(left + 4, y - 1, left + PANEL_WIDTH - 4, y + ROW_HEIGHT - 1, HOVER_FILL);
			}
			context.text(font, Component.literal(display(grouping)), left + 8, y, VALUE, true);
			bestText(context, grouping, left + PANEL_WIDTH - 100, y);
			resultRows.add(new RowBox(y, grouping));
			y += ROW_HEIGHT;
		}
		if (results.size() > visibleRows()) {
			context.text(font, Component.literal("... " + (results.size() - scroll - visibleRows()) + " more"),
					left + 8, top + 32 + LIST_HEIGHT + 2, LABEL, false);
		}
	}

	private int visibleRows() {
		return LIST_HEIGHT / ROW_HEIGHT;
	}

	private String display(MarketSearch.Grouping grouping) {
		return grouping.display().isBlank() ? grouping.token() : grouping.display();
	}

	private void bestText(GuiGraphicsExtractor context, MarketSearch.Grouping grouping, int x, int y) {
		MarketOffer buy = grouping.offers().stream()
				.filter(offer -> offer.observation().direction() == ShopDirection.SELLING
						&& offer.unitPrice() != null)
				.findFirst().orElse(null);
		MarketOffer sell = grouping.offers().stream()
				.filter(offer -> offer.observation().direction() == ShopDirection.BUYING
						&& offer.unitPrice() != null)
				.findFirst().orElse(null);
		int cursor = x;
		if (buy != null) {
			context.text(font, Component.literal("B " + buy.unitPrice().stripTrailingZeros().toPlainString() + "G"),
					cursor, y, ACCENT, false);
			cursor += 62;
		}
		if (sell != null) {
			context.text(font, Component.literal("S " + sell.unitPrice().stripTrailingZeros().toPlainString() + "G"),
					cursor, y, WARN, false);
		}
	}

	private void drawFilterTabs(GuiGraphicsExtractor context, int mouseX, int mouseY) {
		String[] labels = {"zea.market.browser.filter.all", "zea.market.browser.filter.buy",
				"zea.market.browser.filter.sell"};
		int x = left + 8;
		int y = top + PANEL_HEIGHT - 18;
		for (int index = 0; index < labels.length; index++) {
			boolean active = filter == MarketSearch.Filter.values()[index];
			int width = 46;
			context.fill(x, y, x + width, y + 14, active ? ACTIVE_FILL : IDLE_FILL);
			context.text(font, Component.translatable(labels[index]), x + 4, y + 4,
					active ? VALUE : LABEL, false);
			x += width + 4;
		}
	}

	private void drawDetail(GuiGraphicsExtractor context, int mouseX, int mouseY) {
		travelRows.clear();
		context.text(font, Component.literal(display(selected)), left + 8, top + 30, VALUE, true);
		context.text(font, Component.translatable("zea.market.browser.back"), left + 8,
				top + PANEL_HEIGHT - 14, LABEL, false);
		int y = top + 44;
		for (MarketOffer offer : selected.offers()) {
			if (y > top + PANEL_HEIGHT - 24) {
				break;
			}
			PhysicalShop shop = offer.shop();
			String price = offer.unitPrice() == null
					? Component.translatable("zea.market.browser.unpriced").getString()
					: offer.unitPrice().stripTrailingZeros().toPlainString() + "G/item";
			int sideColor = offer.observation().direction() == ShopDirection.SELLING ? ACCENT : WARN;
			context.text(font, Component.literal(offer.observation().direction() == ShopDirection.SELLING
							? Component.translatable("zea.market.browser.side.buy").getString()
							: Component.translatable("zea.market.browser.side.sell").getString()),
					left + 8, y, sideColor, false);
			context.text(font, Component.literal(price), left + 46, y, VALUE, false);
			y += ROW_HEIGHT;
			context.text(font, Component.literal(ownerLabel(shop)), left + 16, y, LABEL, false);
			context.text(font, Component.literal("x=" + shop.signPos().getX()
							+ " y=" + shop.signPos().getY() + " z=" + shop.signPos().getZ()),
					left + 130, y, LABEL, false);
			y += ROW_HEIGHT;
			context.text(font, Component.literal(MarketAge.format(offer.observation().observedAt(), Instant.now())),
					left + 16, y, LABEL, false);
			String spawn = MarketTravel.spawnCommand(shop);
			if (spawn != null) {
				context.text(font, Component.translatable("zea.market.browser.travel", shop.nation()),
						left + 130, y, ACCENT, false);
				travelRows.add(new TravelBox(y, shop));
			}
			y += 4;
		}
	}

	private String ownerLabel(PhysicalShop shop) {
		String owner = shop.owner() != null ? shop.owner()
				: Component.translatable("zea.market.browser.unknown.owner").getString();
		if (shop.nation() != null) {
			return owner + " - " + shop.nation();
		}
		return owner;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
		double mouseX = click.x();
		double mouseY = click.y();
		if (selected != null && mouseY >= top + PANEL_HEIGHT - 18 && mouseY < top + PANEL_HEIGHT - 4
				&& mouseX >= left + 8 && mouseX < left + 50) {
			selected = null;
			return true;
		}
		for (MarketScreen.TravelBox travel : travelRows) {
			if (mouseY >= travel.y() && mouseY < travel.y() + ROW_HEIGHT
					&& mouseX >= left + 130 && mouseX < left + PANEL_WIDTH - 4) {
				MarketTravel.travelToNation(travel.shop());
				return true;
			}
		}
		for (MarketScreen.RowBox row : resultRows) {
			if (mouseY >= row.y() && mouseY < row.y() + ROW_HEIGHT
					&& mouseX >= left + 4 && mouseX < left + PANEL_WIDTH - 4) {
				selected = row.grouping();
				return true;
			}
		}
		if (mouseY >= top + PANEL_HEIGHT - 18 && mouseY < top + PANEL_HEIGHT - 4 && selected == null) {
			int x = left + 8;
			for (MarketSearch.Filter candidate : MarketSearch.Filter.values()) {
				if (mouseX >= x && mouseX < x + 46) {
					filter = candidate;
					refresh();
					return true;
				}
				x += 50;
			}
		}
		return super.mouseClicked(click, doubled);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		if (selected == null && results.size() > visibleRows()) {
			int maxScroll = Math.max(0, results.size() - visibleRows());
			scroll = Math.max(0, Math.min(maxScroll, scroll - (int) Math.signum(verticalAmount)));
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
		return super.keyPressed(input);
	}

	@Override
	public boolean charTyped(CharacterEvent input) {
		if (searchBox != null && searchBox.isFocused() && searchBox.charTyped(input)) {
			return true;
		}
		return super.charTyped(input);
	}

	private record RowBox(int y, MarketSearch.Grouping grouping) {
	}

	/** A travel action row on the item page; only verified nations land here. */
	private record TravelBox(int y, PhysicalShop shop) {
	}
}
