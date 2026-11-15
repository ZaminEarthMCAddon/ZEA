package net.zaminmc.zea.market.ui;

import net.minecraft.client.Minecraft;
import net.zaminmc.zea.market.model.PhysicalShop;
import net.zaminmc.zea.market.model.ShopObservation;
import net.zaminmc.zea.market.model.ShopResponse;
import org.jspecify.annotations.Nullable;

/**
 * Opening the shop screen. The deferral ritual is load-bearing: a
 * client command or an inspection callback runs mid-keystroke inside
 * another screen's input handling, whose follow-up close would wipe the
 * shop screen before a frame — so it is queued onto the client thread.
 *
 * <p>The screen's chat sender uses the ordinary play-connection chat
 * pathway — the exact channel EarthMC expects the quantity answer on.
 * The normal chat input stays available as a fallback by construction:
 * ZEA adds nothing to the protocol, it only types into it.
 */
public final class ShopScreens {
	private ShopScreens() {
	}

	/** Opens the shop screen for one inspection, deferred as required. */
	public static void open(PhysicalShop shop, ShopObservation observation,
			@Nullable ShopResponse response) {
		Minecraft client = Minecraft.getInstance();
		client.execute(() -> client.gui.setScreen(new ShopScreen(shop, observation, response,
				ShopScreens::sendChat, null)));
	}

	/**
	 * Sends a shop transaction input through the normal chat pathway.
	 * Without a connection (disconnect during review) nothing is sent —
	 * the review state stays exactly where it was.
	 */
	private static void sendChat(String input) {
		Minecraft client = Minecraft.getInstance();
		if (client.getConnection() != null) {
			client.getConnection().sendChat(input);
		}
	}
}
