package net.zaminmc.zea.map.projection;

import net.zaminmc.zea.map.model.MapCamera;
import net.zaminmc.zea.map.model.MapWorld;
import net.zaminmc.zea.map.model.TileId;

/**
 * The single coordinate frame conversion every map surface uses: world
 * blocks ⇄ screen pixels for one camera, surface size and rotation.
 *
 * <p>One projection, one frame naming (the reference implementation's
 * hardest-won lesson, {@code 8ce45767} and {@code 7ea2bb8d}): callers
 * name which world they mean by handing in a {@link MapCamera} bound to
 * that world, and this class never guesses. EarthMC's map worlds are
 * 1:1 — no dimension scaling exists anywhere in here, by policy.
 *
 * <p>Rotation is expressed once, as the angle {@code A} by which map
 * north is rotated away from screen up. {@code A = 0} is north-up. The
 * screen direction of a world-space direction {@code (dx, dz)} is
 * {@code (dx·cosA + dz·sinA, −dx·sinA + dz·cosA)} — so at
 * {@code A = π/2} north points screen-left. Icons and labels are drawn
 * counter-rotated by {@code −A} so they stay upright on a turning map
 * (NEU's {@code orientIcons}). {@link #rotationForYaw} converts a
 * Minecraft yaw into this convention; the math is pinned by tests.
 */
public final class MapProjection {

	/** Tile edge length in pixels, verified live against map.earthmc.net. */
	public static final int TILE_SIZE_PX = 512;

	/** The finest published pyramid level: zoom 5 renders 1:1. */
	public static final int MAX_ZOOM = 5;

	/**
	 * Prefer tiles a little sharper than strictly needed, so imagery
	 * stays crisp through downscaling instead of being upscaled the
	 * moment the camera sits between zoom steps.
	 */
	public static final int QUALITY_ZOOM_BIAS = 2;

	/** The whole of a tile's texture, for quads that draw a tile directly. */
	public static final TileRegion FULL_REGION = new TileRegion(0.0, 0.0, 1.0, 1.0);

	private final MapCamera camera;
	private final int surfaceWidth;
	private final int surfaceHeight;
	private final double rotationRadians;
	private final double cos;
	private final double sin;

	public MapProjection(MapCamera camera, int surfaceWidth, int surfaceHeight, double rotationRadians) {
		this.camera = camera;
		this.surfaceWidth = surfaceWidth;
		this.surfaceHeight = surfaceHeight;
		this.rotationRadians = rotationRadians;
		this.cos = Math.cos(rotationRadians);
		this.sin = Math.sin(rotationRadians);
	}

	/** A north-up projection of the given surface. */
	public static MapProjection northUp(MapCamera camera, int surfaceWidth, int surfaceHeight) {
		return new MapProjection(camera, surfaceWidth, surfaceHeight, 0.0);
	}

	/**
	 * The rotation that keeps a player with the given Minecraft yaw
	 * facing screen-up: their heading is
	 * {@code (−sin(yaw), cos(yaw))} in world space (yaw 0 = south),
	 * which is up on screen when {@code A = yaw + 180°}.
	 */
	public static double rotationForYaw(float yawDegrees) {
		return Math.toRadians(yawDegrees + 180.0);
	}

	public MapCamera camera() {
		return camera;
	}

	public int surfaceWidth() {
		return surfaceWidth;
	}

	public int surfaceHeight() {
		return surfaceHeight;
	}

	public double rotationRadians() {
		return rotationRadians;
	}

	/** Screen X of a world point. */
	public double screenX(double worldX, double worldZ) {
		double dx = worldX - camera.centerX();
		double dz = worldZ - camera.centerZ();
		return surfaceWidth / 2.0 + (dx * cos + dz * sin) * camera.pixelsPerBlock();
	}

	/** Screen Y of a world point. */
	public double screenY(double worldX, double worldZ) {
		double dx = worldX - camera.centerX();
		double dz = worldZ - camera.centerZ();
		return surfaceHeight / 2.0 + (-dx * sin + dz * cos) * camera.pixelsPerBlock();
	}

	/** World X of a screen point — the exact inverse of {@link #screenX}/{@link #screenY}. */
	public double worldX(double screenX, double screenY) {
		double rx = (screenX - surfaceWidth / 2.0) / camera.pixelsPerBlock();
		double ry = (screenY - surfaceHeight / 2.0) / camera.pixelsPerBlock();
		return camera.centerX() + rx * cos - ry * sin;
	}

	/** World Z of a screen point — the exact inverse of {@link #screenX}/{@link #screenY}. */
	public double worldZ(double screenX, double screenY) {
		double rx = (screenX - surfaceWidth / 2.0) / camera.pixelsPerBlock();
		double ry = (screenY - surfaceHeight / 2.0) / camera.pixelsPerBlock();
		return camera.centerZ() + rx * sin + ry * cos;
	}

	/** The camera rotation a counter-rotated icon needs to stay upright. */
	public double iconRotation() {
		return -rotationRadians;
	}

	/**
	 * The zoom level to request for the camera's scale: the coarsest
	 * level whose native resolution still meets the demand, biased
	 * {@link #QUALITY_ZOOM_BIAS} levels sharper, clamped to the
	 * published pyramid.
	 */
	public static int chooseTileZoom(double pixelsPerBlock) {
		if (!(pixelsPerBlock > 0) || Double.isNaN(pixelsPerBlock)) {
			return 0;
		}
		// floor(log2(ppb)) is exact as the binary exponent of the
		// double; a computed logarithm of an exact power of two can
		// round the wrong way at a step boundary. The bias then
		// prefers tiles sharper than strictly needed, shrinking as
		// the camera zooms out past the published pyramid's floor.
		int demand = Math.getExponent(pixelsPerBlock);
		return Math.max(0, Math.min(MAX_ZOOM, MAX_ZOOM + demand + QUALITY_ZOOM_BIAS));
	}

	/** Native pixels-per-block of a pyramid level: {@code 2^(zoom − MAX_ZOOM)}. */
	public static double pixelsPerBlockAt(int zoom) {
		return Math.pow(2.0, zoom - MAX_ZOOM);
	}

	/** World blocks spanned by one tile at a level: {@code 512 · 2^(MAX_ZOOM − zoom)}. */
	public static int tileWorldSpan(int zoom) {
		return TILE_SIZE_PX * (1 << (MAX_ZOOM - zoom));
	}

	/** The tile column containing a world X at a level; negative indices are normal. */
	public static int tileX(double worldX, int zoom) {
		return (int) Math.floorDiv((long) Math.floor(worldX), tileWorldSpan(zoom));
	}

	/** The tile row containing a world Z at a level. */
	public static int tileZ(double worldZ, int zoom) {
		return (int) Math.floorDiv((long) Math.floor(worldZ), tileWorldSpan(zoom));
	}

	/** The pyramid tile covering the camera center at a zoom level. */
	public TileId centerTile(int zoom) {
		return new TileId(camera.world(), TileId.TileSource.SQUAREMAP, zoom,
						tileX(camera.centerX(), zoom), tileZ(camera.centerZ(), zoom));
	}

	/**
	 * Tile indices covering the surface at a zoom level, padded by
	 * {@code margin} tiles. The range uses the surface diagonal, so it
	 * is a correct superset under any rotation; the renderer culls per
	 * quad. Order is row-major from the north-west corner.
	 */
	public java.util.List<TileId> visibleTiles(int zoom, int margin, TileId.TileSource source) {
		double radiusBlocks = Math.hypot(surfaceWidth, surfaceHeight) / 2.0 / camera.pixelsPerBlock();
		long span = tileWorldSpan(zoom);
		long minX = floorDiv(camera.centerX() - radiusBlocks, span) - margin;
		long maxX = floorDiv(camera.centerX() + radiusBlocks, span) + margin;
		long minZ = floorDiv(camera.centerZ() - radiusBlocks, span) - margin;
		long maxZ = floorDiv(camera.centerZ() + radiusBlocks, span) + margin;
		java.util.List<TileId> out = new java.util.ArrayList<>();
		for (long z = minZ; z <= maxZ; z++) {
			for (long x = minX; x <= maxX; x++) {
				out.add(new TileId(camera.world(), source, zoom, (int) x, (int) z));
			}
		}
		return out;
	}

	/**
	 * A sub-rectangle of an ancestor tile, in tile texture units
	 * (0..1), that shows exactly what {@code tile} would show. Used for
	 * the parent-fallback blit while a detailed tile loads.
	 *
	 * @throws IllegalArgumentException when {@code tile} is not a
	 *                                  descendant of {@code ancestor}
	 *                                  on the same branch
	 */
	public static TileRegion regionWithin(TileId tile, TileId ancestor) {
		int shift = tile.zoom() - ancestor.zoom();
		if (shift <= 0 || shift > 8
						|| (tile.x() >> shift) != ancestor.x()
						|| (tile.z() >> shift) != ancestor.z()) {
			throw new IllegalArgumentException(tile + " is not a descendant of " + ancestor);
		}
		int tileSpan = tileWorldSpan(tile.zoom());
		int ancestorSpan = tileWorldSpan(ancestor.zoom());
		long withinX = (long) tile.x() * tileSpan - (long) ancestor.x() * ancestorSpan;
		long withinZ = (long) tile.z() * tileSpan - (long) ancestor.z() * ancestorSpan;
		double size = 1.0 / (1 << shift);
		double u0 = (double) withinX / ancestorSpan;
		double v0 = (double) withinZ / ancestorSpan;
		return new TileRegion(u0, v0, u0 + size, v0 + size);
	}

	/** A rectangle in tile texture units (u0, v0) to (u1, v1), both in 0..1. */
	public record TileRegion(double u0, double v0, double u1, double v1) {
		public TileRegion {
			if (!(u0 >= 0 && v0 >= 0 && u1 > u0 && v1 > v0 && u1 <= 1 && v1 <= 1)) {
				throw new IllegalArgumentException("region out of tile bounds: " + u0 + "," + v0 + " " + u1 + "," + v1);
			}
		}
	}

	private static long floorDiv(double value, long span) {
		return Math.floorDiv((long) Math.floor(value), span);
	}

	/** The number of world blocks visible across the surface diagonal. */
	public double diagonalBlocks() {
		return Math.hypot(surfaceWidth, surfaceHeight) / camera.pixelsPerBlock();
	}

	/** The world this projection's camera views. */
	public MapWorld world() {
		return camera.world();
	}
}
