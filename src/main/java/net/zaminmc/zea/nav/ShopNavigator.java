package net.zaminmc.zea.nav;

import org.jspecify.annotations.Nullable;

/**
 * Decides what navigating to one shop destination does — the smart
 * navigation rule set, pure and testable.
 *
 * <p>The rules, exactly as the product defines them:
 *
 * <ul>
 *   <li><b>The player is already in the destination nation</b>: no spawn
 *       command. Teleporting to a nation spawn the player is already
 *       inside would be an outright regression — the waypoint alone
 *       carries them to the shop, which is exactly what large-town
 *       navigation needs.</li>
 *   <li><b>The shop's town is confidently resolved</b> (actual claim
 *       geometry contains the sign, so the town name is the server's
 *       own): {@code /t spawn <town>} is preferred.</li>
 *   <li><b>Only the nation is resolved</b>: {@code /n spawn <nation>}.</li>
 *   <li><b>Neither town nor nation is known</b>: no command is issued
 *       and none is invented — the waypoint still guides, and the
 *       explanation says the area is unknown.</li>
 * </ul>
 *
 * <p>An unknown player nation never blocks the spawn commands: "already
 * in the destination nation" is an affirmative finding from claim
 * geometry, not a default. The explanation never claims the command
 * teleports the player — the server decides whether it succeeds.
 *
 * <p>The command argument is the server's own town or nation name,
 * preserved verbatim. Dispatch goes through the ordinary command-send
 * path, where the spawn observer records it like any other.
 */
public final class ShopNavigator {
	private ShopNavigator() {
	}

	/** What one navigation action does. */
	public record Plan(@Nullable String spawnCommand, boolean waypoint,
			@Nullable String explanation) {
	}

	/**
	 * Plans the navigation to {@code destination} for a player whose
	 * current nation — resolved from claim geometry, supplemented by
	 * tracked spawn arrivals — is {@code playerNation} ({@code null}
	 * when unknown). The town command is the server's own name for the
	 * shop's town; the nation command likewise.
	 */
	public static Plan plan(ShopDestination destination, @Nullable String playerNation,
			@Nullable String currentWorld) {
		boolean sameWorld = currentWorld == null || currentWorld.equals(destination.world());
		if (playerNation != null && destination.nation() != null
				&& playerNation.equalsIgnoreCase(destination.nation()) && sameWorld) {
			// Already inside the destination nation: the waypoint does
			// the rest. No spawn command, ever, in this case.
			return new Plan(null, true,
					"Already in " + destination.nation() + " — waypoint set for "
							+ destination.target().toShortString());
		}
		if (destination.town() != null && !destination.town().isBlank()) {
			return new Plan("t spawn " + destination.town(), true,
					"Travelling to " + destination.town() + " — the server decides whether "
							+ "the spawn succeeds; the waypoint marks the shop itself.");
		}
		if (destination.nation() != null && !destination.nation().isBlank()) {
			return new Plan("n spawn " + destination.nation(), true,
					"Travelling to " + destination.nation() + " — the server decides whether "
							+ "the spawn succeeds; the waypoint marks the shop itself.");
		}
		return new Plan(null, true,
				"Town and nation unknown for this shop — waypoint set; travel to it yourself.");
	}
}
