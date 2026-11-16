package net.zaminmc.zea.market;

import net.zaminmc.zea.map.cache.ClaimStore;
import net.zaminmc.zea.map.model.MapWorld;
import net.zaminmc.zea.map.model.TownClaim;
import org.jspecify.annotations.Nullable;

/**
 * Resolves the town and nation a shop stands in, from the claim data
 * the map subsystem publishes — the project's one authoritative source
 * for claim geometry. A point-in-claim test at the sign's position is
 * the whole idea: no text guessing, no approximate "nearest town".
 *
 * <p>The claim store may hold nothing yet (its fetch pipeline is the
 * map subsystem's own backlog); an empty store resolves nothing and
 * the market leaves town and nation unavailable rather than inventing
 * them. Resolution cost is a bounding-box prefilter plus the claim's
 * own even-odd test per shop inspection — no scanning, no render-time
 * world access.
 */
public final class ClaimTownResolver implements MarketListings.TownLookup {
	private final ClaimStore claims;

	public ClaimTownResolver(ClaimStore claims) {
		this.claims = claims;
	}

	@Override
	/** The resolved town and nation, or {@code null} when the claims hold nothing there. */
	public MarketListings.ResolvedTown at(String world, int x, int z) {
		MapWorld mapWorld = MapWorld.ofDimension(namespace(world), path(world));
		if (mapWorld == null) {
			return null;
		}
		for (TownClaim claim : claims.claims(mapWorld)) {
			if (claim.coversBlock(x, z)) {
				String nation = claim.nation();
				return new MarketListings.ResolvedTown(claim.name(),
						nation == null || nation.isBlank() ? null : nation);
			}
		}
		return null;
	}

	private static String namespace(String world) {
		int separator = world.indexOf(':');
		return separator < 0 ? "minecraft" : world.substring(0, separator);
	}

	private static String path(String world) {
		int separator = world.indexOf(':');
		return separator < 0 ? world : world.substring(separator + 1);
	}
}
