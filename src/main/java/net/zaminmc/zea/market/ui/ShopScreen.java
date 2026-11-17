package net.zaminmc.zea.market.ui;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.zaminmc.zea.market.model.ItemFingerprint;
import net.zaminmc.zea.market.model.PhysicalShop;
import net.zaminmc.zea.market.model.PriceScope;
import net.zaminmc.zea.market.model.ShopDirection;
import net.zaminmc.zea.market.model.ShopObservation;
import net.zaminmc.zea.market.model.ShopResponse;
import net.zaminmc.zea.market.pricing.PriceMath;
import net.zaminmc.zea.market.tx.ShopTransaction;
import net.zaminmc.zea.market.tx.TransactionCalculator;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * The enhanced physical shop screen: what EarthMC's raw response says,
 * translated into what the player actually needs — which side of the
 * deal they are on, what the item is, what it costs, and exactly what
 * will be sent if they confirm.
 *
 * <p>Layout (understated, house style): item and identity up top, the
 * shop facts below, then the quantity row and its computed totals, then
 * review/confirm, then the exact input that will leave. Empty fields
 * are not drawn; a {@code PARTIAL} response disables the transaction
 * controls its missing numbers would feed. The {@code all} action
 * appears only when the server's response explicitly confirmed it.
 *
 * <p>Nothing is ever sent on open, on quantity change, or on close —
 * only the confirm button, reachable only through the review step, and
 * dead once a send has left.
 */
public final class ShopScreen extends Screen {
	private static final int PANEL_WIDTH = 240;
	private static final int FIELD_HEIGHT = 16;
	private static final int BUTTON_WIDTH = 60;
	private static final int BUTTON_HEIGHT = 18;

	private static final int FILL = 0xF0101018;
	private static final int BORDER = 0xFF505050;
	private static final int LABEL = 0xFF8A8A8A;
	private static final int VALUE = 0xFFE0E0E0;
	private static final int ACCENT = 0xFF55FFAA;
	private static final int WARN = 0xFFFFAA55;
	private static final int IDLE_FILL = 0xFF2A2A34;
	private static final int HOVER_FILL = 0xFF3A3A48;
	private static final int DISABLED_FILL = 0xFF1A1A22;

	private static final DateTimeFormatter CLOCK =
			DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).withLocale(Locale.getDefault());

	private final PhysicalShop shop;
	private final ShopResponse response;
	private final ShopObservation observation;
	private final ShopTransaction transaction = new ShopTransaction();
	private final Consumer<String> chatSender;
	private final @Nullable Runnable reopenParent;

	private EditBox quantityField;
	private int left;
	private int top;
	private @Nullable ButtonBox reviewButton;
	private @Nullable ButtonBox confirmButton;
	private @Nullable ButtonBox cancelButton;
	private @Nullable ButtonBox allButton;
	private @Nullable PlanLine plan;
	private boolean quantityInvalid;

	public ShopScreen(PhysicalShop shop, ShopObservation observation, @Nullable ShopResponse response,
			Consumer<String> chatSender, @Nullable Runnable reopenParent) {
		super(Component.translatable("zea.market.shop.title"));
		this.shop = shop;
		this.observation = observation;
		this.response = response != null ? response : responseless(observation);
		this.chatSender = chatSender;
		this.reopenParent = reopenParent;
	}

	/** A minimal response view for sign-only inspections: facts only. */
	private static ShopResponse responseless(ShopObservation observation) {
		return new ShopResponse(null, observation.direction(), null, observation.itemDisplay(),
				null, null, null, observation.stockRaw(), observation.stockUnit(), null,
				null, null, null, null, false, observation.enchantments(),
				ShopResponse.Completeness.PARTIAL, List.of(), observation.observedAt());
	}

	@Override
	protected void init() {
		left = (width - PANEL_WIDTH) / 2;
		top = Math.max(10, (height - 210) / 2);
		quantityField = new EditBox(font, 0, 0, 60, FIELD_HEIGHT, Component.literal("Quantity"));
		quantityField.setValue("");
		quantityField.setResponder(text -> recomputePlan());
		layoutWidgets();
	}

	/** Positions the widgets, which move with the review/cancel row. */
	private void layoutWidgets() {
		if (quantityField == null) {
			return;
		}
		boolean reviewing = transaction.phase() == ShopTransaction.Phase.REVIEWING;
		int quantityRow = rowQuantity();
		quantityField.setX(left + 150);
		quantityField.setY(top + quantityRow);
		quantityField.setEditable(!reviewing && transaction.phase() != ShopTransaction.Phase.SUBMITTED);
	}

	// ------------------------------------------------------------------
	// Plan computation
	// ------------------------------------------------------------------

	private void recomputePlan() {
		plan = null;
		quantityInvalid = false;
		if (transaction.phase() != ShopTransaction.Phase.ENTERING || quantityField == null) {
			layoutWidgets();
			return;
		}
		String text = quantityField.getValue().trim();
		if (text.isEmpty()) {
			layoutWidgets();
			return;
		}
		int quantity;
		try {
			quantity = Integer.parseInt(text);
		} catch (NumberFormatException e) {
			quantityInvalid = true;
			layoutWidgets();
			return;
		}
		Optional<TransactionCalculator.BulkPlan> buy = TransactionCalculator.buy(
				response.direction(), response.price(), response.bulkSize(),
				response.maxBuyBulks(), quantity);
		if (buy.isPresent()) {
			plan = new PlanLine(buy.get().itemCount(), buy.get().totalCost(), null, buy.get().inputToSend());
			layoutWidgets();
			return;
		}
		Optional<TransactionCalculator.ItemPlan> sell = TransactionCalculator.sell(
				response.direction(), response.price(), response.maxSellItems(), quantity);
		if (sell.isPresent()) {
			plan = new PlanLine(sell.get().itemCount(), null, sell.get().totalPayout(), sell.get().inputToSend());
			layoutWidgets();
			return;
		}
		quantityInvalid = true;
		layoutWidgets();
	}

	/** One computed plan: individual items involved, totals, exact input. */
	private record PlanLine(int itemCount, @Nullable BigDecimal cost, @Nullable BigDecimal payout,
			String inputToSend) {
	}

	// ------------------------------------------------------------------
	// Layout rows
	// ------------------------------------------------------------------

	private int rowQuantity() {
		return 118;
	}

	private int rowButtons() {
		return 148;
	}

	// ------------------------------------------------------------------
	// Rendering
	// ------------------------------------------------------------------

	@Override
	public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
		context.fill(left - 1, top - 1, left + PANEL_WIDTH + 1, top + 180, BORDER);
		context.fill(left, top, left + PANEL_WIDTH, top + 179, FILL);

		drawHeader(context, mouseX, mouseY);
		drawFacts(context);
		drawTransactionRow(context);
		drawButtons(context, mouseX, mouseY);
		drawOutcome(context);
	}

	private void drawHeader(GuiGraphicsExtractor context, int mouseX, int mouseY) {
		int x = left + 8;
		int y = top + 8;
		if (response.preview() != null) {
			context.item(response.preview().create(), x, y);
		}
		String name = response.itemDisplay() != null ? response.itemDisplay()
				: Component.translatable("zea.market.shop.unknown.item").getString();
		context.text(font, Component.literal(name), x + 20, y, VALUE, true);
		context.text(font, Component.literal(directionLabel()),
				x + 20, y + 10, response.direction() == ShopDirection.SELLING ? ACCENT : WARN, false);
		if (!transaction.phase().equals(ShopTransaction.Phase.ENTERING)
				&& transaction.phase() != ShopTransaction.Phase.REVIEWING) {
			return;
		}
	}

	private String directionLabel() {
		return Component.translatable(response.direction() == ShopDirection.SELLING
				? "zea.market.shop.direction.buy_from"
				: "zea.market.shop.direction.sell_to").getString();
	}

	private void drawFacts(GuiGraphicsExtractor context) {
		int y = top + 34;
		y = fact(context, y, "zea.market.shop.owner", shop.owner());
		y = fact(context, y, "zea.market.shop.nation", shop.nation());
		y = fact(context, y, "zea.market.shop.price", priceLabel());
		y = fact(context, y, "zea.market.shop.stock", stockLabel());
		y = fact(context, y, "zea.market.shop.limit", limitLabel());
		y = fact(context, y, "zea.market.shop.location", locationLabel());
		fact(context, y, "zea.market.shop.observed", observation.observedAt() == null
				? null : CLOCK.format(observation.observedAt().atZone(ZoneId.systemDefault())));
	}

	private int fact(GuiGraphicsExtractor context, int y, String labelKey, @Nullable String value) {
		if (value == null || value.isBlank()) {
			return y;
		}
		context.text(font, Component.translatable(labelKey), left + 8, y, LABEL, false);
		context.text(font, Component.literal(value), left + 70, y, VALUE, false);
		return y + 11;
	}

	private @Nullable String priceLabel() {
		if (response.price() == null || response.priceScope() == null) {
			return null;
		}
		if (response.priceScope() == PriceScope.PER_BULK && response.bulkSize() != null) {
			return response.price().stripTrailingZeros().toPlainString() + "G / "
					+ response.bulkSize() + " items";
		}
		return response.price().stripTrailingZeros().toPlainString() + "G each";
	}

	private @Nullable String stockLabel() {
		if (response.stockRaw() == null) {
			return null;
		}
		return switch (response.stockUnit()) {
			case SPACE -> Component.translatable("zea.market.shop.stock.space", response.stockRaw()).getString();
			case UNKNOWN -> Component.translatable("zea.market.shop.stock.raw", response.stockRaw()).getString();
			case ITEMS -> response.stockRaw() + " items";
			case STACKS -> response.stockRaw() + " stacks";
		};
	}

	private @Nullable String limitLabel() {
		if (response.direction() == ShopDirection.SELLING) {
			return response.maxBuyBulks() == null ? null
					: Component.translatable("zea.market.shop.limit.buy", response.maxBuyBulks()).getString();
		}
		return response.maxSellItems() == null ? null
				: Component.translatable("zea.market.shop.limit.sell", response.maxSellItems()).getString();
	}

	private String locationLabel() {
		BlockPos sign = shop.signPos();
		String location = sign.getX() + ", " + sign.getY() + ", " + sign.getZ();
		if (shop.chestPos() != null) {
			location += " (" + Component.translatable("zea.market.shop.location.chest").getString() + ")";
		}
		return location;
	}

	private void drawTransactionRow(GuiGraphicsExtractor context) {
		int y = top + rowQuantity();
		if (transaction.phase() == ShopTransaction.Phase.ENTERING) {
			context.text(font, Component.translatable(quantityPromptKey()), left + 8, y + 4, LABEL, false);
			if (quantityInvalid) {
				context.text(font, Component.translatable("zea.market.shop.quantity.invalid"),
						left + 8, y + FIELD_HEIGHT + 3, WARN, false);
			} else if (plan != null) {
				context.text(font, Component.translatable(quantityTotalsKey(), plan.itemCount()),
						left + 8, y + FIELD_HEIGHT + 3, ACCENT, false);
				BigDecimal total = plan.cost() != null ? plan.cost() : plan.payout();
				if (total != null) {
					context.text(font, Component.translatable("zea.market.shop.total",
									total.stripTrailingZeros().toPlainString() + "G"),
							left + 8, y + FIELD_HEIGHT + 14, ACCENT, false);
				}
			}
		} else if (transaction.phase() == ShopTransaction.Phase.REVIEWING && plan != null) {
			context.text(font, Component.translatable("zea.market.shop.review"), left + 8, y, ACCENT, false);
			context.text(font, Component.translatable(quantityTotalsKey(), plan.itemCount()),
					left + 8, y + 11, VALUE, false);
			BigDecimal total = plan.cost() != null ? plan.cost() : plan.payout();
			if (total != null) {
				context.text(font, Component.translatable("zea.market.shop.total",
								total.stripTrailingZeros().toPlainString() + "G"),
						left + 8, y + 22, VALUE, false);
			}
			context.text(font, Component.translatable("zea.market.shop.review.input", plan.inputToSend()),
					left + 8, y + 33, WARN, false);
		}
	}

	private String quantityPromptKey() {
		return response.direction() == ShopDirection.SELLING
				? "zea.market.shop.quantity.bulks"
				: "zea.market.shop.quantity.items";
	}

	private String quantityTotalsKey() {
		return quantityTotalsKey(0);
	}

	private String quantityTotalsKey(int ignored) {
		return response.direction() == ShopDirection.SELLING
				? "zea.market.shop.totals.buy"
				: "zea.market.shop.totals.sell";
	}

	private void drawButtons(GuiGraphicsExtractor context, int mouseX, int mouseY) {
		int y = top + rowButtons();
		boolean reviewing = transaction.phase() == ShopTransaction.Phase.REVIEWING;
		boolean entered = transaction.phase() == ShopTransaction.Phase.ENTERING;

		if (entered) {
			reviewButton = button(context, mouseX, mouseY, left + 8, y,
					Component.translatable("zea.market.shop.review.open"),
					plan != null);
			allButton = TransactionCalculator.all(response.supportsAll(), response.direction()).isPresent()
					? button(context, mouseX, mouseY, left + 8 + 70, y,
							Component.translatable(allKey()), true)
					: null;
			confirmButton = null;
			cancelButton = null;
		} else if (reviewing) {
			confirmButton = button(context, mouseX, mouseY, left + 8, y,
					Component.translatable("zea.market.shop.confirm"), plan != null);
			cancelButton = button(context, mouseX, mouseY, left + 8 + 70, y,
					Component.translatable("zea.market.shop.cancel"), true);
			reviewButton = null;
			allButton = null;
		} else {
			reviewButton = null;
			confirmButton = null;
			cancelButton = null;
			allButton = null;
		}
	}

	private String allKey() {
		return response.direction() == ShopDirection.SELLING
				? "zea.market.shop.all.buy"
				: "zea.market.shop.all.sell";
	}

	private ButtonBox button(GuiGraphicsExtractor context, int mouseX, int mouseY,
			int x, int y, Component label, boolean enabled) {
		boolean hovered = enabled && mouseX >= x && mouseX < x + BUTTON_WIDTH && mouseY >= y && mouseY < y + BUTTON_HEIGHT;
		context.fill(x, y, x + BUTTON_WIDTH, y + BUTTON_HEIGHT, !enabled ? DISABLED_FILL : hovered ? HOVER_FILL : IDLE_FILL);
		context.text(font, label, x + 4, y + 5, !enabled ? 0xFF707070 : VALUE, false);
		return new ButtonBox(x, y, enabled);
	}

	private void drawOutcome(GuiGraphicsExtractor context) {
		int y = top + rowButtons() + BUTTON_HEIGHT + 6;
		switch (transaction.phase()) {
			case SUBMITTED -> context.text(font, Component.translatable("zea.market.shop.submitted"),
					left + 8, y, ACCENT, false);
			case TIMED_OUT -> context.text(font, Component.translatable("zea.market.shop.timedout"),
					left + 8, y, WARN, false);
			default -> {
			}
		}
	}

	private record ButtonBox(int x, int y, boolean enabled) {
		boolean contains(double mouseX, double mouseY) {
			return enabled && mouseX >= x && mouseX < x + BUTTON_WIDTH && mouseY >= y && mouseY < y + BUTTON_HEIGHT;
		}
	}

	// ------------------------------------------------------------------
	// Interaction
	// ------------------------------------------------------------------

	@Override
	public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
		double mouseX = click.x();
		double mouseY = click.y();
		if (reviewButton != null && reviewButton.contains(mouseX, mouseY)) {
			transaction.beginReview();
			layoutWidgets();
			return true;
		}
		if (confirmButton != null && confirmButton.contains(mouseX, mouseY)) {
			confirm();
			return true;
		}
		if (cancelButton != null && cancelButton.contains(mouseX, mouseY)) {
			transaction.cancelReview();
			layoutWidgets();
			return true;
		}
		if (allButton != null && allButton.contains(mouseX, mouseY)) {
			sendAll();
			return true;
		}
		return super.mouseClicked(click, doubled);
	}

	/** The explicit confirm: the only path that ever sends anything. */
	private void confirm() {
		if (plan == null || !transaction.canConfirm()) {
			return;
		}
		transaction.markSubmitted(plan.inputToSend())
				.ifPresent(chatSender::accept);
		layoutWidgets();
	}

	/**
	 * The server's {@code all} input, offered only when the response
	 * explicitly confirmed it. It is still a transaction: it goes
	 * through the same review discipline as a number.
	 */
	private void sendAll() {
		TransactionCalculator.all(response.supportsAll(), response.direction())
				.filter(ignored -> transaction.canConfirm() || transaction.phase() == ShopTransaction.Phase.ENTERING)
				.ifPresent(allPlan -> {
					transaction.beginReview();
					transaction.markSubmitted(allPlan.inputToSend()).ifPresent(chatSender::accept);
					layoutWidgets();
				});
	}

	@Override
	public boolean keyPressed(KeyEvent input) {
		if (quantityField != null && quantityField.isFocused() && quantityField.keyPressed(input)) {
			return true;
		}
		if (isEscape(input)) {
			onClose();
			return true;
		}
		return super.keyPressed(input);
	}

/** The GLFW keycode of Escape; the house constant style, as in the button editor. */
	private static final int KEY_ESCAPE = 256;

	private boolean isEscape(KeyEvent input) {
		return input.key() == KEY_ESCAPE;
	}

	@Override
	public boolean charTyped(CharacterEvent input) {
		if (quantityField != null && quantityField.isFocused() && quantityField.charTyped(input)) {
			return true;
		}
		return super.charTyped(input);
	}

	@Override
	public void tick() {
		if (transaction.phase() == ShopTransaction.Phase.SUBMITTED
				&& transaction.markTimedOut(Instant.now())) {
			layoutWidgets();
		}
	}

	@Override
	public void onClose() {
		if (reopenParent != null) {
			reopenParent.run();
		}
		super.onClose();
	}
}
