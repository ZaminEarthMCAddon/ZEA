package net.zaminmc.zea.nav;

import net.minecraft.core.BlockPos;
import org.jspecify.annotations.Nullable;

import java.time.Instant;

/**
 * The player's current shop destination: the temporary waypoint that
 * points at one physical shop's exact sign (or chest) position — never
 * at a town center or a nation spawn.
 *
 * <p>The destination carries the shop's identity so the waypoint is
 * cleared when the player actually reaches and inspects that shop
 * (the sign click that produces the server's shop response), when the
 * player leaves the target nation, when it is deliberately replaced,
 * or when the world session ends. There is deliberately no timeout: a
 * waypoint that vanishes on a timer is a waypoint the player lost.
 */
public record ShopDestination(
		String shopId,
		String serverId,
		String world,
		BlockPos target,
		@Nullable BlockPos chest,
		@Nullable String owner,
		@Nullable String town,
		@Nullable String nation,
		Instant createdAt) {
	public ShopDestination {
		if (shopId == null || serverId == null || world == null || target == null
				|| createdAt == null) {
			throw new NullPointerException("shop destination fields must not be null");
		}
	}

	/** The line the HUD and toasts show: where, and whose shop it is. */
	public String label() {
		StringBuilder text = new StringBuilder();
		if (town != null) {
			text.append(town);
			if (nation != null) {
				text.append(" (").append(nation).append(')');
			}
		} else if (nation != null) {
			text.append(nation);
		} else {
			text.append("unknown area");
		}
		if (owner != null) {
			text.append(" · ").append(owner);
		}
		return text.toString();
	}
}
