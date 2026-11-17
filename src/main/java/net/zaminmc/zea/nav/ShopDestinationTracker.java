package net.zaminmc.zea.nav;

import org.jspecify.annotations.Nullable;

/**
 * Holds the player's one shop destination and runs its lifecycle —
 * reaching and inspecting the shop, leaving its nation, replacing it,
 * the world session ending. Exactly one destination lives here at a
 * time: a new navigation replaces the old one, and nothing accumulates.
 *
 * <p>The ticks carry the two facts the lifecycle rules read — the
 * player's world and their resolved nation (from claim geometry, where
 * it is known). An unknown player nation never clears a destination:
 * leaving the target nation is an affirmative finding, not a default.
 */
public final class ShopDestinationTracker {
	@Nullable
	private ShopDestination destination;

	/** The active destination, or {@code null} when none. */
	public @Nullable ShopDestination destination() {
		return destination;
	}

	/** Sets (or replaces) the destination. The previous one is gone. */
	public void set(ShopDestination destination) {
		this.destination = destination;
	}

	/**
	 * Clears the destination when the player has reached and inspected
	 * the shop it points at — the sign click that produced the shop
	 * response. The waypoint has done its job.
	 */
	public void clearFor(String shopId) {
		if (destination != null && destination.shopId().equals(shopId)) {
			destination = null;
		}
	}

	/** Deliberately clears the destination. */
	public void clear() {
		destination = null;
	}

	/**
	 * Runs the session rules: the world ended (disconnect or dimension
	 * change), or the player affirmatively left the target nation.
	 * Unknown nation evidence changes nothing.
	 */
	public void tick(@Nullable String playerWorld, @Nullable String playerNation) {
		if (destination == null) {
			return;
		}
		if (playerWorld != null && !playerWorld.equals(destination.world())) {
			destination = null;
			return;
		}
		if (playerNation != null && destination.nation() != null
				&& !playerNation.equalsIgnoreCase(destination.nation())) {
			destination = null;
		}
	}

	/** Forgets everything (disconnect). */
	public void reset() {
		destination = null;
	}
}
