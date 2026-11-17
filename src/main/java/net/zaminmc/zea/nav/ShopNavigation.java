package net.zaminmc.zea.nav;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.zaminmc.zea.ZeaClient;
import net.zaminmc.zea.earthmc.ServerIdentity;
import net.zaminmc.zea.market.MarketSubsystem;
import net.zaminmc.zea.market.index.ItemVariantIndex.Offer;
import net.zaminmc.zea.market.model.PhysicalShop;
import org.jspecify.annotations.Nullable;

import java.time.Instant;

/**
 * Executes one navigation action the browser offers: from the selected
 * physical shop to a planned spawn command and a temporary waypoint.
 *
 * <p>The decision is {@link ShopNavigator}'s; this class only gathers
 * the facts it needs (the shop, its resolved town context, the player's
 * current nation from claim geometry) and carries the plan out:
 * dispatching the spawn command — through the ordinary send path, where
 * the spawn observer records it — and setting the waypoint. The
 * explanation is shown as a client system message, honestly: it never
 * claims the command teleports, and a selection without navigation
 * still never reaches this class.
 */
public final class ShopNavigation {
	private ShopNavigation() {
	}

	/**
	 * Navigates to the offer's shop. The town context comes from the
	 * browser's resolved cache ({@code resolvedTown}/{@code
	 * resolvedNation}, the server's own claim names); the player's
	 * nation resolves from claim geometry at their current coordinates,
	 * or stays unknown.
	 */
	public static void navigate(Offer offer, @Nullable String resolvedTown,
			@Nullable String resolvedNation) {
		Minecraft client = Minecraft.getInstance();
		ClientPacketListener connection = client.getConnection();
		if (client.player == null || connection == null) {
			return;
		}
		PhysicalShop shop = offer.shop();
		String serverId = ZeaClient.lifecycle().earthMcServer()
				.map(ServerIdentity::host).orElse("unknown");
		// The waypoint points at the chest when one was verified, the
		// sign otherwise — the exact shop, not the town square.
		BlockPos target = shop.chestPos() != null ? shop.chestPos() : shop.signPos();
		ShopDestination destination = new ShopDestination(shop.shopId(), serverId,
				shop.world(), target, shop.chestPos(), shop.owner(),
				resolvedTown, resolvedNation, Instant.now());
		String playerNation = playerNation(shop.world());
		ShopNavigator.Plan plan = ShopNavigator.plan(destination, playerNation, shop.world());
		ShopDestinationTracker tracker = ZeaClient.destinationTracker();
		if (tracker != null) {
			tracker.set(destination);
		}
		if (plan.spawnCommand() != null) {
			// The ordinary send path: no interception, and the spawn
			// observer records the dispatch like any other.
			connection.sendCommand(plan.spawnCommand());
		}
		if (plan.explanation() != null) {
			client.gui.hud.getChat().addClientSystemMessage(Component.literal(plan.explanation()));
		}
	}

	/**
	 * The nation the player stands in, from claim geometry at their
	 * current coordinates. {@code null} when no claims are loaded — an
	 * unknown nation is unknown, never guessed.
	 */
	public static @Nullable String playerNation(@Nullable String world) {
		MarketSubsystem market = ZeaClient.market();
		Minecraft client = Minecraft.getInstance();
		if (market == null || market.townLookup() == null || client.player == null) {
			return null;
		}
		var resolved = market.townLookup().at(
				world == null ? client.level == null ? "unknown"
						: client.level.dimension().identifier().toString() : world,
				(int) Math.floor(client.player.getX()), (int) Math.floor(client.player.getZ()));
		return resolved == null ? null : resolved.nation();
	}
}
