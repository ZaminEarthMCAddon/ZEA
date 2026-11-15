package net.zaminmc.zea.map.model;

import java.util.Objects;

/**
 * Identity of one map imagery tile. A bare coordinate pair is not an
 * identity: the same numbers are valid on both worlds and at every zoom,
 * and the community-repo and squaremap layers are distinct assets for the
 * same spot. Every cache entry, in-flight request, GPU texture name and
 * validator is keyed by this record.
 *
 * @param world  the world the tile belongs to — never inferred
 * @param source where the imagery comes from
 * @param zoom   pyramid level; {@code pixelsPerBlock = 2^(zoom − maxZoom)}
 * @param x      tile column (floor division of world X by the tile's
 *               world span; negative values are normal)
 * @param z      tile row
 */
public record TileId(MapWorld world, TileSource source, int zoom, int x, int z) {

	/** Where a tile's imagery comes from. */
	public enum TileSource {
		/** Live squaremap imagery from map.earthmc.net. */
		SQUAREMAP("squaremap"),
		/** Community-discovered tiles distributed through ZEA-REPO. */
		REPO("repo");

		public final String pathSegment;

		TileSource(String pathSegment) {
			this.pathSegment = pathSegment;
		}
	}

	public TileId {
		Objects.requireNonNull(world, "world");
		Objects.requireNonNull(source, "source");
		if (zoom < 0) {
			throw new IllegalArgumentException("zoom must be >= 0: " + zoom);
		}
	}

	/**
	 * The parent tile in the pyramid — the tile one zoom coarser whose
	 * top-left quadrant contains this tile. Used for fallback rendering
	 * while a detailed tile loads.
	 */
	public TileId parent() {
		if (zoom == 0) {
			return null;
		}
		return new TileId(world, source, zoom - 1, x >> 1, z >> 1);
	}

	/**
	 * Stable textual key for GPU texture names and disk cache paths:
	 * {@code squaremap/earthmc_moon/3/12_-5}. World and source included
	 * precisely so two worlds can never share one texture name — the
	 * cross-world collision rendered as the missing-texture checkerboard
	 * in the reference implementation.
	 */
	public String texturePath() {
		return source.pathSegment + "/" + world.squaremapKey + "/" + zoom + "/" + x + "_" + z;
	}

	@Override
	public String toString() {
		return texturePath();
	}
}
