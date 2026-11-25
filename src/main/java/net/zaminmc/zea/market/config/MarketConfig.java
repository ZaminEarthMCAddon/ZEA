package net.zaminmc.zea.market.config;

import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean;
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption;

/**
 * The Market category: everything about the physical-shop marketplace.
 *
 * <p>Observations record automatically as shops are inspected — no
 * screen and no command is involved in collection; the inventory shop
 * index presents them. Nothing is ever sent on a click alone.
 */
public final class MarketConfig {

        @ConfigOption(name = "Capture prompts", desc = "When a capture is missing details the server "
                        + "never carried — its spawn, a price the block lacked — put a clickable prompt "
                        + "in chat that opens the completion screen for the captured listing.")
        @ConfigEditorBoolean
        public boolean capturePrompts = true;
}
