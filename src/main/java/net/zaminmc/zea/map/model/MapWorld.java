package net.zaminmc.zea.map.model;

import java.util.Locale;

/**
 * The map worlds ZEA supports. EarthMC publishes exactly two squaremap
 * worlds (verified live against {@code /tiles/settings.json}):
 * {@code minecraft_overworld} (Terra Nostra) and {@code earthmc_moon}
 * (the Moon).
 *
 * <p>The world is a dimension of every map cache key, tile key, claim
 * collection, marker collection and camera state. Earth and Moon
 * coordinates overlap numerically, so nothing cached for one may ever be
 * reused for the other — including GPU texture names and HTTP validators.
 * A town name is not an identity either: every Moon town is an outpost of
 * an Earth town and shares its name, so town identity is
 * {@code (world, townKey)}.
 *
 * <p>Pure type: no Minecraft classes, so the resolution rules are unit
 * tested without a client. The dimension adapter lives in the lifecycle
 * package and feeds {@link #ofDimension(String, String)}.
 */
public enum MapWorld {
	/** Terra Nostra, the Earth overworld. The fallback world. */
	EARTH("minecraft_overworld", "Terra Nostra", 129_024, 64_512),

	/** The Moon: 35k × 17.5k blocks, one outpost per town, 1:1 coordinates. */
	MOON("earthmc_moon", "Moon", 35_000, 17_500);

	/** The squaremap world key, as published by settings.json. */
	public final String squaremapKey;

	/** Human-readable name shown in the world selector. */
	public final String displayName;

	/** World extent along X, in blocks (used only for camera clamping). */
	public final int worldWidth;

	/** World extent along Z, in blocks. */
	public final int worldHeight;

	MapWorld(String squaremapKey, String displayName, int worldWidth, int worldHeight) {
		this.squaremapKey = squaremapKey;
		this.displayName = displayName;
		this.worldWidth = worldWidth;
		this.worldHeight = worldHeight;
	}

	/**
	 * Resolves a dimension id to its map world. Rules, in order:
	 *
	 * <ol>
	 * <li>Exact match against the squaremap world keys —
	 *     {@code minecraft:overworld} projects onto {@code minecraft_overworld},
	 *     a {@code earthmc:earthmc_moon}-shaped id onto {@code earthmc_moon}.
	 * <li>{@code minecraft:*} dimensions are Earth — the Nether and the End
	 *     have no map of their own, and EarthMC policy forbids mapping them.
	 * <li>{@code earthmc:*} dimensions are the Moon. EarthMC has two lunar
	 *     dimensions ({@code earthmc:space} and {@code earthmc:moon}) but one
	 *     squaremap world, so both resolve here. Deliberately not conditional
	 *     on a fetched world list: requiring it made stepping onto the Moon
	 *     before the list landed resolve to Earth.
	 * <li>Anything else: Earth, the safe fallback.
	 * </ol>
	 */
	public static MapWorld ofDimension(String namespace, String path) {
		if (namespace == null || path == null) {
			return EARTH;
		}
		String key = namespace.toLowerCase(Locale.ROOT) + '_' + path.toLowerCase(Locale.ROOT);
		for (MapWorld world : values()) {
			if (world.squaremapKey.equals(key)) {
				return world;
			}
		}
		if ("minecraft".equalsIgnoreCase(namespace)) {
			return EARTH;
		}
		if ("earthmc".equalsIgnoreCase(namespace)) {
			return MOON;
		}
		return EARTH;
	}

	/** Parses a squaremap world key from settings.json; null when unknown. */
	public static MapWorld fromSquaremapKey(String squaremapKey) {
		if (squaremapKey == null) {
			return null;
		}
		for (MapWorld world : values()) {
			if (world.squaremapKey.equals(squaremapKey)) {
				return world;
			}
		}
		return null;
	}

	/** The other world, for the world-switch control. */
	public MapWorld other() {
		return this == EARTH ? MOON : EARTH;
	}
}
