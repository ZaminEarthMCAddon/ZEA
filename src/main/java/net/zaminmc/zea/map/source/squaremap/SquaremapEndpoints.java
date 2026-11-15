package net.zaminmc.zea.map.source.squaremap;

import net.zaminmc.zea.map.model.MapWorld;

/**
 * URL construction for EarthMC's squaremap instance. Pure, so the exact
 * strings are unit-tested — a wrong URL is a silent blank map.
 *
 * <p>Verified live (2026-11-15) against {@code map.earthmc.net}.
 */
public final class SquaremapEndpoints {

	/** The live squaremap host. Fixed: the API key must never be sent here. */
	public static final String BASE = "https://map.earthmc.net";

	/**
	 * The live player-position feed. Fixed constant, deliberately not
	 * derived from {@link #BASE}: it lives on the API host and accepts an
	 * {@code Authorization: Bearer} key, and pointing it at a configurable
	 * base would send that key wherever the base says.
	 */
	public static final String PLAYERS_URL = "https://api.earthmc.net/map/v1/players";

	private SquaremapEndpoints() {
	}

	/** {@code <base>/tiles/<world>/markers.json} */
	public static String markers(MapWorld world) {
		return BASE + "/tiles/" + world.squaremapKey + "/markers.json";
	}

	/** {@code <base>/tiles/settings.json} — the published world list. */
	public static String worlds() {
		return BASE + "/tiles/settings.json";
	}

	/** {@code <base>/tiles/<world>/<zoom>/<x>_<z>.png} */
	public static String tile(MapWorld world, int zoom, int x, int z) {
		return BASE + "/tiles/" + world.squaremapKey + "/" + zoom + "/" + x + "_" + z + ".png";
	}
}
