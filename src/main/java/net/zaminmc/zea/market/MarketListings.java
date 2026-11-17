package net.zaminmc.zea.market;

import org.jspecify.annotations.Nullable;

/**
 * The town-resolution seam the market reads its geographic context
 * through: which town and nation a world coordinate sits in, as
 * resolved from the map subsystem's claim data.
 *
 * <p>Deliberately an interface: the claim store lives in the map
 * subsystem and may hold no data yet — a null lookup simply leaves
 * town and nation unavailable, and nothing is fabricated for them.
 * The shop's sign coordinates stay the authoritative location; this
 * seam only names the area around them.
 */
public final class MarketListings {
	private MarketListings() {
	}

	/** Town and nation as resolved from claim data, when available. */
	public record ResolvedTown(String town, @Nullable String nation) {
		public ResolvedTown {
			if (town == null || town.isBlank()) {
				throw new IllegalArgumentException("town must not be blank");
			}
		}
	}

	/**
	 * The town/nation lookup the market consults. Implementations may
	 * return {@code null} when no claim is known at the coordinate —
	 * an unresolved area stays unresolved.
	 */
	public interface TownLookup {
		/** The resolved town and nation, or {@code null} when nothing is known there. */
		ResolvedTown at(String world, int x, int z);
	}
}
