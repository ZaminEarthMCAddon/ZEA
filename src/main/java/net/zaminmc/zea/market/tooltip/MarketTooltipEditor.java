package net.zaminmc.zea.market.tooltip;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.zaminmc.zea.ZeaClient;
import net.zaminmc.zea.market.ItemFingerprints;
import net.zaminmc.zea.market.MarketData;
import net.zaminmc.zea.market.MarketSubsystem;
import net.zaminmc.zea.market.config.MarketConfig;
import net.zaminmc.zea.market.model.PhysicalShop;
import net.zaminmc.zea.market.pricing.MarketOffer;
import net.zaminmc.zea.market.pricing.OfferResolver;
import net.zaminmc.zea.market.ui.MarketAge;

import java.time.Instant;
import java.util.List;

/**
 * The market section at the very end of an item tooltip, in the existing
 * tooltip pipeline — no second renderer.
 *
 * <p>Default behavior: shown while Shift is held, so ordinary tooltips
 * stay clean; the config can make it always visible or hide it. Only
 * exact matches show anything: the hovered stack's identity token must
 * equal an observation's token, and no side appears without a real
 * observation for it — the market never substitutes another enchantment
 * combination and never fabricates a price.
 *
 * <p>Semantics stay straight: "Buy" is the player buying from an EarthMC
 * SELLING shop (cheapest observed), "Sell" is the player selling to a
 * BUYING shop (highest observed). The age line speaks of observations,
 * never of current availability.
 */
public final class MarketTooltipEditor {
	private static final OfferResolver RESOLVER = new OfferResolver();

	private MarketTooltipEditor() {
	}

	/**
	 * Appends the market section to the tooltip lines. The list is the
	 * vanilla-built copy the mixin already owns; the stack is never
	 * written back to.
	 */
	public static void edit(List<Component> lines, ItemStack stack) {
		MarketConfig config = marketConfig();
		MarketData data = marketData();
		if (config == null || data == null || lines == null || stack.isEmpty()) {
			return;
		}
		if (config.tooltipMode == MarketConfig.TooltipMode.NEVER) {
			return;
		}
		if (config.tooltipMode == MarketConfig.TooltipMode.WHEN_SHIFT_HELD && !hasShift()) {
			return;
		}
		String token = ItemFingerprints.ofStack(stack).token();
		if (token.isEmpty()) {
			return;
		}
		MarketOffer buy = RESOLVER.bestBuyOffer(data, token).orElse(null);
		MarketOffer sell = RESOLVER.bestSellOffer(data, token).orElse(null);
		if (buy == null && sell == null) {
			return;
		}
		lines.add(Component.empty());
		lines.add(Component.translatable("zea.market.tooltip.header")
				.withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD));
		Instant observedAt = null;
		if (buy != null) {
			lines.add(Component.translatable("zea.market.tooltip.buy",
					nationLabel(buy.shop()),
					buy.unitPrice().stripTrailingZeros().toPlainString())
					.withStyle(ChatFormatting.GREEN));
			observedAt = buy.observation().observedAt();
		}
		if (sell != null) {
			lines.add(Component.translatable("zea.market.tooltip.sell",
					nationLabel(sell.shop()),
					sell.unitPrice().stripTrailingZeros().toPlainString())
					.withStyle(ChatFormatting.GOLD));
			observedAt = laterOf(observedAt, sell.observation().observedAt());
		}
		if (observedAt != null) {
			lines.add(Component.translatable("zea.market.tooltip.age",
					MarketAge.sincePhrase(observedAt, Instant.now()))
					.withStyle(ChatFormatting.DARK_GRAY));
		}
	}

	private static String nationLabel(PhysicalShop shop) {
		if (shop.nation() != null && !shop.nation().isBlank()) {
			return shop.nation();
		}
		return shop.owner() != null ? shop.owner() : "unknown";
	}

	private static Instant laterOf(Instant a, Instant b) {
		return a == null ? b : b != null && b.isAfter(a) ? b : a;
	}

	private static boolean hasShift() {
		return net.minecraft.client.Minecraft.getInstance().hasShiftDown();
	}

	private static MarketConfig marketConfig() {
		return ZeaClient.config() == null ? null : ZeaClient.config().market;
	}

	private static MarketData marketData() {
		MarketSubsystem market = ZeaClient.market();
		return market == null ? null : market.data();
	}
}
