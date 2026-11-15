package net.zaminmc.zea.market;

import net.minecraft.client.Minecraft;
import net.zaminmc.zea.market.model.PhysicalShop;
import org.jspecify.annotations.Nullable;

/**
 * Getting the player to the shop — a button, never a copyable command.
 *
 * <p>The one transport that exists today is the nation spawn command,
 * sent through the same client command pathway as manual typing. Exact
 * shop coordinates are always recorded and always displayed, but a
 * waypoint system is still being built by the map subsystem, so until
 * it exists the coordinate row is information, not a teleport.
 *
 * <p>Nation resolution is the open dependency: no authorized source of
 * "which nation is this sign in" is wired yet (ZEA-REPO's nation
 * dataset is an empty placeholder, and the API path is not verified),
 * so a shop without a resolved nation shows a disabled travel button
 * and the reason. The capability is complete; the data source is the
 * blocker. Navigation never triggers a shop transaction.
 */
public final class MarketTravel {
	private MarketTravel() {
	}

	/** The spawn command for the shop's nation, when one is resolved. */
	public static @Nullable String spawnCommand(PhysicalShop shop) {
		if (shop.nation() == null || shop.nation().isBlank()) {
			return null;
		}
		return "/n spawn " + shop.nation();
	}

	/**
	 * Sends the nation spawn command. Returns {@code false} when there
	 * is nothing verified to travel to — the caller shows the reason
	 * instead of pretending a navigation happened.
	 */
	public static boolean travelToNation(PhysicalShop shop) {
		String command = spawnCommand(shop);
		if (command == null) {
			return false;
		}
		Minecraft client = Minecraft.getInstance();
		if (client.getConnection() == null) {
			return false;
		}
		client.getConnection().sendCommand(command);
		return true;
	}
}
