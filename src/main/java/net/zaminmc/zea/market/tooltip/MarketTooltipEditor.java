package net.zaminmc.zea.market.tooltip;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.zaminmc.zea.ZeaClient;
import net.zaminmc.zea.market.ItemFingerprints;
import net.zaminmc.zea.market.MarketData;
import net.zaminmc.zea.market.MarketSubsystem;
import net.zaminmc.zea.market.config.MarketConfig;
import net.zaminmc.zea.market.index.ItemVariantIndex;
import net.zaminmc.zea.market.index.ItemVariantIndex.Variant;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The market section at the very end of an item tooltip, in the existing
 * tooltip pipeline — no second renderer.
 *
 * <p>Default behavior: shown while Shift is held, so ordinary tooltips
 * stay clean; the config can make it always visible or hide it. Only
 * exact matches show anything: the hovered stack's identity token must
 * equal a variant's token, and the lines are the variant's aggregates
 * (sellers, price range, combined stock — see
 * {@link MarketTooltipLines}). The market never substitutes another
 * enchantment combination and never fabricates a price.
 *
 * <p>The variant index is rebuilt when the market's revision moves — once
 * per change, not per hovered frame; a hover after that is a map lookup.
 */
public final class MarketTooltipEditor {
        /** The revision the cached variants were built against. */
        private static long cachedRevision = -1;
        private static Map<String, Variant> cachedVariants = Map.of();

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
                Variant variant = variants(data).get(token);
                if (variant == null) {
                        return;
                }
                lines.addAll(MarketTooltipLines.build(variant, Instant.now()));
        }

        /** The token → variant map for the market's current revision. */
        private static Map<String, Variant> variants(MarketData data) {
                long revision = data.revision();
                if (revision != cachedRevision) {
                        Map<String, Variant> byToken = new HashMap<>();
                        for (Variant variant : ItemVariantIndex.variants(data)) {
                                byToken.put(variant.token(), variant);
                        }
                        cachedVariants = Map.copyOf(byToken);
                        cachedRevision = revision;
                }
                return cachedVariants;
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
