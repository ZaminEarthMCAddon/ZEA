package net.zaminmc.zea.map.source.squaremap;

import net.zaminmc.zea.map.model.MapWorld;

/**
 * The URL surface a {@link SquaremapClient} talks to. Production wiring
 * uses {@link SquaremapEndpoints}; tests substitute a local server.
 */
public interface SquaremapUrls {

	String markers(MapWorld world);

	String worlds();

	String players();

	/**
	 * A tile image URL. Defaulted to the live surface because tiles are
	 * fetched verbatim; tests that exercise the tile pipeline override
	 * it.
	 */
	default String tile(MapWorld world, int zoom, int x, int z) {
		return SquaremapEndpoints.tile(world, zoom, x, z);
	}

	/** The production URLs, verified live against map.earthmc.net. */
	static SquaremapUrls live() {
		return new SquaremapUrls() {
			@Override
			public String markers(MapWorld world) {
				return SquaremapEndpoints.markers(world);
			}

			@Override
			public String worlds() {
				return SquaremapEndpoints.worlds();
			}

			@Override
			public String players() {
				return SquaremapEndpoints.PLAYERS_URL;
			}
		};
	}
}
