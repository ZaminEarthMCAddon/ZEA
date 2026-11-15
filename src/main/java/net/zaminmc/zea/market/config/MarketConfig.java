package net.zaminmc.zea.market.config;

import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorDropdown;
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption;

/**
 * The Market category: everything about the physical-shop marketplace.
 *
 * <p>The shop screen opens when a shop is inspected because that is the
 * feature's core flow (inspect → understand → decide); it can be turned
 * off for players who only want the observations recorded for the
 * market browser. Every transaction still requires explicit review and
 * confirmation inside that screen — nothing is ever sent on a click
 * alone, and a timed-out transaction never retries on its own.
 */
public final class MarketConfig {

	@ConfigOption(name = "Open shop screen", desc = "Open ZEA's structured shop screen when a "
			+ "shop sign is inspected. Off keeps the observations recording while the "
			+ "server's own response stays the interface.")
	@ConfigEditorBoolean
	public boolean openShopScreen = true;

	@ConfigOption(name = "Tooltip market info", desc = "Show the market section at the end of "
			+ "item tooltips: always, only while Shift is held (default), or never.")
	@ConfigEditorDropdown
	public TooltipMode tooltipMode = TooltipMode.WHEN_SHIFT_HELD;

	@ConfigOption(name = "Show raw shop responses", desc = "Keep a debug line on the shop screen "
			+ "linking the original server response, so the parsed view can always be "
			+ "checked against what the server actually said.")
	@ConfigEditorBoolean
	public boolean showRawResponses = false;

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
