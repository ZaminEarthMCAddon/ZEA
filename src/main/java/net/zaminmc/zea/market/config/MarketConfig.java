package net.zaminmc.zea.market.config;

import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorDropdown;
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption;

/**
 * The Market category: everything about the physical-shop marketplace.
 *
 * <p>Observations record automatically as shops are inspected — no
 * screen and no command is involved in collection; the inventory shop
 * index presents them. Nothing is ever sent on a click alone.
 */
public final class MarketConfig {

        @ConfigOption(name = "Tooltip market info", desc = "Show the market section at the end of "
                        + "item tooltips: always, only while Shift is held (default), or never.")
        @ConfigEditorDropdown
        public TooltipMode tooltipMode = TooltipMode.WHEN_SHIFT_HELD;

        @ConfigOption(name = "Capture review prompt", desc = "After ZEA captures a shop, put a "
                        + "Review line in chat: one click opens the market editor with the captured "
                        + "details, where wrong details can be fixed and missing ones added.")
        @ConfigEditorBoolean
        public boolean captureReviewPrompt = true;

        /** How much market information item tooltips carry. */
        public enum TooltipMode {
                /** Never show the market section. */
                NEVER,
                /** Show only while Shift is held (the default). */
                WHEN_SHIFT_HELD,
                /** Always show the market section. */
                ALWAYS
        }
}
