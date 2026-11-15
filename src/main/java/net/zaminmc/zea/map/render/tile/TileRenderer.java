package net.zaminmc.zea.map.render.tile;

import net.zaminmc.zea.map.cache.TileCache;
import net.zaminmc.zea.map.model.MapCamera;
import net.zaminmc.zea.map.model.MapWorld;
import net.zaminmc.zea.map.model.TileId;
import net.zaminmc.zea.map.projection.MapProjection;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Plans one frame of tile imagery: which quads to draw, in which layer,
 * and which tiles to ask the fetcher for — as a pure function of the
 * camera, the cache state and the view's motion. No Minecraft types,
 * so every visual policy here is pinned by unit tests.
 *
 * <p><b>Frame naming.</b> Quads are emitted in <i>map-frame</i>
 * coordinates: pixels relative to the view center, with world north up,
 * <b>unrotated</b>. The drawing adapter sets up the rotated pose once
 * (translate to the surface center, rotate by the projection's angle)
 * and then draws every quad axis-aligned — one transform per frame
 * instead of one per quad, and the projection's rotation math stays
 * exactly where its tests pinned it.
 *
 * <p><b>Layers.</b> The Quick Map (zoom 0) is always planned first —
 * it is the under-layer that guarantees something drawable exists. The
 * Detailed layer at {@link MapProjection#chooseTileZoom} activates only
 * through the {@link Coverage} gate (full visible coverage, sticky).
 * Missing slots inside an active Detailed layer fall back to their
 * nearest drawable ancestor <b>above zoom 0</b>; a zoom-0 fallback
 * would exactly duplicate the under-layer beneath it.
 *
 * <p><b>Requests.</b> Network is bounded per frame
 * ({@link #REQUEST_BUDGET_IDLE} idle, {@link #REQUEST_BUDGET_MOVING}
 * while the view moves), prioritised centre-out, and never includes
 * tiles outside the world's published extent — those answer 404 today
 * and forever. The fetcher and cache deduplicate anything the planner
 * double-submits; the budget exists to keep frame cost flat, not to
 * guard correctness. Stale-tile ETag revalidation stays with the
 * scheduler by design ("network is scheduled, never frame-driven").
 *
 * <p><b>Prefetch.</b> Idle frames prefetch one zoom level finer and one
 * coarser (parent-fallback sources), plus a one-tile margin ring around
 * the view. Moving frames drop the zoom prefetch and instead lead the
 * pan direction by {@link #PAN_LEAD_VIEWPORTS} viewports.
 */
public final class TileRenderer {

	/** One textured rectangle to draw: source tile, region within it, map-frame position. */
	public record Quad(TileId texture, MapProjection.TileRegion region, double x, double y,
					double width, double height) {
	}

	/** Camera motion since the previous frame, in world blocks. */
	public record Motion(double deltaX, double deltaZ) {

		/** No motion: idle budgets, zoom prefetch. */
		public static final Motion STILL = new Motion(0.0, 0.0);

		public boolean moving() {
			return deltaX != 0.0 || deltaZ != 0.0;
		}
	}

	/** The immutable output of one planned frame. */
	public record FramePlan(int zoom, boolean detailedActive, List<Quad> overview, List<Quad> detailed,
					List<TileId> requests, int coveredSlots, int totalSlots) {

		private static final FramePlan EMPTY = new FramePlan(0, false, List.of(), List.of(), List.of(), 0, 0);

		/** The plan for a view that cannot show tiles at all. */
		public static FramePlan empty() {
			return EMPTY;
		}
	}

	/** Tile requests issued per idle frame (the reference implementation's budget). */
	public static final int REQUEST_BUDGET_IDLE = 28;
	/** Tile requests issued while the view is panning or zooming. */
	public static final int REQUEST_BUDGET_MOVING = 2;
	/** Ring of extra tiles requested around the visible set. */
	public static final int PREFETCH_MARGIN = 1;
	/** How far ahead of the pan direction requests lead, in viewports. */
	public static final double PAN_LEAD_VIEWPORTS = 0.75;

	private final TileCache<?> cache;
	private final Coverage coverage = new Coverage();
	private final TileId.TileSource source;

	public TileRenderer(TileCache<?> cache) {
		this(cache, TileId.TileSource.SQUAREMAP);
	}

	public TileRenderer(TileCache<?> cache, TileId.TileSource source) {
		this.cache = java.util.Objects.requireNonNull(cache, "cache");
		this.source = java.util.Objects.requireNonNull(source, "source");
	}

	/** The sticky-activation state this renderer owns; for resets on cache clears. */
	public Coverage coverage() {
		return coverage;
	}

	/**
	 * Plans the frame for one map surface. Call once per frame on the
	 * render thread; the cache synchronizes its own state.
	 */
	public FramePlan planFrame(MapProjection projection, Motion motion) {
		MapCamera camera = projection.camera();
		if (projection.surfaceWidth() <= 0 || projection.surfaceHeight() <= 0 || !(camera.pixelsPerBlock() > 0)) {
			return FramePlan.empty();
		}
		int zoom = MapProjection.chooseTileZoom(camera.pixelsPerBlock());
		List<TileId> visible = projection.visibleTiles(zoom, 0, source);

		int covered = 0;
		for (TileId tile : visible) {
			if (isCovered(tile)) {
				covered++;
			}
		}
		boolean detailedActive = coverage.update(camera.world(), zoom, covered, visible.size());

		List<Quad> overview = planOverviewLayer(projection);
		List<Quad> detailed = detailedActive && zoom > 0 ? planDetailedLayer(projection, visible, zoom) : List.of();
		List<TileId> requests = planRequests(projection, motion, zoom);
		return new FramePlan(zoom, detailedActive, overview, detailed, requests, covered, visible.size());
	}

	/** A slot is covered when it is drawable, or confirmed absent (404: no finer imagery exists). */
	private boolean isCovered(TileId tile) {
		TileCache.State state = cache.stateOf(tile);
		return state == TileCache.State.READY
						|| (state == TileCache.State.FAILED && cache.refusalStatusOf(tile) == 404);
	}

	/** The always-drawn zoom-0 under-layer: the Quick Map. */
	private List<Quad> planOverviewLayer(MapProjection projection) {
		List<TileId> tiles = projection.visibleTiles(0, 0, source);
		List<Quad> out = new ArrayList<>(tiles.size());
		for (TileId tile : tiles) {
			if (withinWorld(tile) && cache.ready(tile) != null) {
				out.add(quadOf(tile, tile, MapProjection.FULL_REGION, projection));
			}
		}
		return out;
	}

	/**
	 * The Detailed layer over an active view: drawable slots draw
	 * themselves, missing slots their nearest drawable ancestor above
	 * zoom 0 — the zoom-0 case is the under-layer's job.
	 */
	private List<Quad> planDetailedLayer(MapProjection projection, List<TileId> visible, int zoom) {
		List<Quad> out = new ArrayList<>(visible.size());
		for (TileId tile : visible) {
			if (!withinWorld(tile)) {
				continue;
			}
			if (cache.ready(tile) != null) {
				out.add(quadOf(tile, tile, MapProjection.FULL_REGION, projection));
				continue;
			}
			TileId ancestor = fallbackFor(tile);
			if (ancestor != null) {
				out.add(quadOf(tile, ancestor, MapProjection.regionWithin(tile, ancestor), projection));
			}
		}
		return out;
	}

	/** The nearest ancestor at zoom ≥ 1 whose image is drawable, or null. */
	private TileId fallbackFor(TileId tile) {
		for (TileId ancestor = tile.parent(); ancestor != null && ancestor.zoom() > 0; ancestor = ancestor.parent()) {
			if (cache.ready(ancestor) != null) {
				return ancestor;
			}
		}
		return null;
	}

	/** Screen-rect of {@code slot} in map-frame pixels, textured by {@code region} of {@code texture}. */
	private static Quad quadOf(TileId slot, TileId texture, MapProjection.TileRegion region,
					MapProjection projection) {
		MapCamera camera = projection.camera();
		double ppb = camera.pixelsPerBlock();
		double span = MapProjection.tileWorldSpan(slot.zoom());
		return new Quad(texture, region,
						(slot.x() * span - camera.centerX()) * ppb,
						(slot.z() * span - camera.centerZ()) * ppb,
						span * ppb, span * ppb);
	}

	/**
	 * The frame's tile requests, as ordered priority segments: the pan
	 * lead first while moving (the tiny moving budget exists to serve
	 * what is about to scroll in — spending it on tiles about to scroll
	 * <i>out</i> is the stutter it prevents), the view's margin ring on
	 * idle frames, adjacent-zoom prefetch after that. Each segment is
	 * centre-out; the budget caps the concatenation; nothing outside
	 * the world's extent is ever requested.
	 */
	private List<TileId> planRequests(MapProjection projection, Motion motion, int zoom) {
		List<List<TileId>> segments = new ArrayList<>();
		if (motion.moving()) {
			segments.add(panLeadTiles(projection, motion, zoom));
			segments.add(projection.visibleTiles(zoom, 0, source));
		} else {
			segments.add(projection.visibleTiles(zoom, PREFETCH_MARGIN, source));
			if (zoom + 1 <= MapProjection.MAX_ZOOM) {
				segments.add(projection.visibleTiles(zoom + 1, 0, source));
			}
			if (zoom - 1 >= 0) {
				segments.add(projection.visibleTiles(zoom - 1, 0, source));
			}
		}

		TileId center = projection.centerTile(zoom);
		Comparator<TileId> centreOut = Comparator
						.comparingInt((TileId tile) -> Math.max(Math.abs(tile.x() - center.x()),
										Math.abs(tile.z() - center.z())))
						.thenComparingInt(TileId::x)
						.thenComparingInt(TileId::z);
		Set<TileId> seen = new LinkedHashSet<>();
		for (List<TileId> segment : segments) {
			List<TileId> wanted = new ArrayList<>();
			for (TileId tile : segment) {
				if (withinWorld(tile) && cache.stateOf(tile) != TileCache.State.READY) {
					wanted.add(tile);
				}
			}
			wanted.sort(centreOut);
			seen.addAll(wanted);
		}
		int budget = motion.moving() ? REQUEST_BUDGET_MOVING : REQUEST_BUDGET_IDLE;
		List<TileId> ordered = List.copyOf(seen);
		return ordered.size() <= budget ? ordered : List.copyOf(ordered.subList(0, budget));
	}

	/** Tiles intersecting a viewport-sized area displaced along the pan direction. */
	private List<TileId> panLeadTiles(MapProjection projection, Motion motion, int zoom) {
		MapCamera camera = projection.camera();
		double length = Math.hypot(motion.deltaX(), motion.deltaZ());
		if (length == 0.0 || !(camera.pixelsPerBlock() > 0)) {
			return List.of();
		}
		double lead = PAN_LEAD_VIEWPORTS * projection.diagonalBlocks();
		double dirX = motion.deltaX() / length * lead;
		double dirZ = motion.deltaZ() / length * lead;
		double half = projection.diagonalBlocks() / 2.0;
		return tilesBetween(camera.world(), zoom,
						camera.centerX() + dirX - half, camera.centerZ() + dirZ - half,
						camera.centerX() + dirX + half, camera.centerZ() + dirZ + half, source);
	}

	/** All tiles whose world rectangle intersects the given world-space rectangle. */
	private static List<TileId> tilesBetween(MapWorld world, int zoom, double minX, double minZ,
					double maxX, double maxZ, TileId.TileSource source) {
		long span = MapProjection.tileWorldSpan(zoom);
		long fromX = Math.floorDiv((long) Math.floor(minX), span);
		long toX = Math.floorDiv((long) Math.floor(maxX), span);
		long fromZ = Math.floorDiv((long) Math.floor(minZ), span);
		long toZ = Math.floorDiv((long) Math.floor(maxZ), span);
		List<TileId> out = new ArrayList<>();
		for (long z = fromZ; z <= toZ; z++) {
			for (long x = fromX; x <= toX; x++) {
				out.add(new TileId(world, source, zoom, (int) x, (int) z));
			}
		}
		return out;
	}

	/** Whether the tile's world rectangle intersects the world's published extent at all. */
	private static boolean withinWorld(TileId tile) {
		MapWorld world = tile.world();
		long span = MapProjection.tileWorldSpan(tile.zoom());
		double minX = tile.x() * (double) span;
		double minZ = tile.z() * (double) span;
		double halfW = world.worldWidth / 2.0;
		double halfH = world.worldHeight / 2.0;
		return minX < halfW && minX + span > -halfW && minZ < halfH && minZ + span > -halfH;
	}
}
