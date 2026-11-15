package net.zaminmc.zea.map.render.tile;

import net.zaminmc.zea.map.cache.TileCache;
import net.zaminmc.zea.map.model.MapCamera;
import net.zaminmc.zea.map.model.MapWorld;
import net.zaminmc.zea.map.model.TileId;
import net.zaminmc.zea.map.projection.MapProjection;
import org.joml.Matrix3x2f;
import org.joml.Vector2f;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The drawing adapter's two policies that must never drift: the pixel
 * snap (adjacent tiles stay adjacent — the seam bug class) and the
 * frame pose (the adapter's transform is the projection's transform —
 * the "one projection, one frame naming" principle). Both are pinned
 * against {@link MapProjection}'s own math, which its tests pinned
 * first, so planner, projection and adapter are locked together.
 */
class TileFrameDrawerTest {

	private final AtomicLong clock = new AtomicLong(1_000_000);
	private final TileCache<byte[]> cache = new TileCache<>(256, clock::get);
	private final TileRenderer renderer = new TileRenderer(cache);

	/** Loads a tile to READY through the two threads' joint protocol. */
	private void ready(TileId id) {
		TileCache.LoadTicket ticket = cache.beginLoad(id);
		assertNotNull(ticket, "load must start for " + id);
		assertTrue(cache.complete(id, new byte[] { 1 }, null, null, ticket.generation()));
		TileCache.Upload<byte[]> upload;
		while ((upload = cache.claimForUpload()) != null) {
			cache.markUploaded(upload);
		}
	}

	@Test
	void snappedEdgesStayWithinHalfAPixel() {
		double[] coords = { 0.0, 0.3, 0.5, 0.51, 0.99, -0.3, -0.5, -0.51, 128.257, -4095.7 };
		for (double x : coords) {
			for (double y : coords) {
				for (double w = 100.4; w <= 400.7; w += 150.15) {
					for (double h = 100.6; h <= 300.2; h += 99.8) {
						TileFrameDrawer.Rect rect = TileFrameDrawer.snap(x, y, w, h);
						assertTrue(Math.abs(rect.x() - x) <= 0.5, "left edge moved more than half a pixel");
						assertTrue(Math.abs(rect.y() - y) <= 0.5, "top edge moved more than half a pixel");
						assertTrue(Math.abs(rect.x() + rect.width() - (x + w)) <= 0.5,
										"right edge moved more than half a pixel");
						assertTrue(Math.abs(rect.y() + rect.height() - (y + h)) <= 0.5,
										"bottom edge moved more than half a pixel");
					}
				}
			}
		}
	}

	@Test
	void snappedAdjacentQuadsShareTheirEdgeExactly() {
		// Two quads that share a double-space edge — the tile-grid
		// situation for every pair of neighbours — must snap to the
		// same integer edge, whatever the fractional position is.
		for (double offset = 0.0; offset <= 2.0; offset += 0.13) {
			for (double w = 100.4; w <= 300.9; w += 100.25) {
				double sharedX = offset;
				double sharedY = offset * 7.0 - 3.0;
				TileFrameDrawer.Rect west = TileFrameDrawer.snap(sharedX - w, sharedY - 50.3, w, 100.6);
				TileFrameDrawer.Rect east = TileFrameDrawer.snap(sharedX, sharedY - 50.3, w, 100.6);
				assertEquals(west.x() + west.width(), east.x(),
								"vertical seam opened at offset " + offset);
				TileFrameDrawer.Rect north = TileFrameDrawer.snap(sharedX - 50.3, sharedY - w, 100.6, w);
				TileFrameDrawer.Rect south = TileFrameDrawer.snap(sharedX - 50.3, sharedY, 100.6, w);
				assertEquals(north.y() + north.height(), south.y(),
								"horizontal seam opened at offset " + offset);
			}
		}
	}

	@Test
	void snapRoundsHalvesTowardPositiveInfinity() {
		// Math.round's tie behaviour, pinned so a future "improvement"
		// to the snap cannot silently pick a different rule.
		assertEquals(1, TileFrameDrawer.snap(0.5, 0.5, 10.0, 10.0).x());
		assertEquals(0, TileFrameDrawer.snap(-0.5, -0.5, 10.0, 10.0).x());
		assertEquals(11, TileFrameDrawer.snap(0.5, 0.5, 10.0, 10.0).x() + 10);
		// A right edge at exactly 10.5 from a left edge at 0.5: the
		// width follows from the edges, never rounds alone.
		assertEquals(10, TileFrameDrawer.snap(0.5, 0.0, 10.0, 10.0).width());
	}

	@Test
	void poseIsTheProjectionForEveryRotation() {
		MapWorld world = MapWorld.EARTH;
		double centerX = 1234.5;
		double centerZ = -987.25;
		double ppb = 1.75;
		int surfaceWidth = 854;
		int surfaceHeight = 480;
		double[] rotations = { 0.0, Math.PI / 6.0, Math.PI / 4.0, Math.PI / 2.0, Math.PI,
						-Math.PI / 3.0, 2.9, -0.0001 };
		for (double rotation : rotations) {
			MapProjection projection = new MapProjection(
							new MapCamera(world, centerX, centerZ, ppb), surfaceWidth, surfaceHeight, rotation);
			Matrix3x2f pose = new Matrix3x2f();
			TileFrameDrawer.composePose(pose, surfaceWidth / 2.0f, surfaceHeight / 2.0f, rotation);
			for (double mapX = -3000.0; mapX <= 3000.0; mapX += 750.0) {
				for (double mapY = -2100.0; mapY <= 2100.0; mapY += 700.0) {
					double worldX = centerX + mapX / ppb;
					double worldZ = centerZ + mapY / ppb;
					Vector2f screen = pose.transformPosition(new Vector2f((float) mapX, (float) mapY));
					assertEquals(projection.screenX(worldX, worldZ), screen.x, 1e-3,
									"screen x drifted at rotation " + rotation);
					assertEquals(projection.screenY(worldX, worldZ), screen.y, 1e-3,
									"screen y drifted at rotation " + rotation);
				}
			}
		}
	}

	@Test
	void snappedQuadsOfAPlanShareTheirEdgesExactly() {
		// The precondition snap exists for: a real plan's quads share
		// their grid edges exactly in double space, so the snapped
		// seams hold. The planner's own tests pin each quad's position;
		// this pins that neighbouring quads agree on the edge between
		// them — the property the seam bug would break first.
		MapProjection projection = MapProjection.northUp(new MapCamera(MapWorld.EARTH, 4096.25, -8192.5, 0.5),
						800, 600);
		int zoom = MapProjection.chooseTileZoom(0.5);
		Set<TileId> loaded = new LinkedHashSet<>();
		for (TileId tile : projection.visibleTiles(zoom, 0, TileId.TileSource.SQUAREMAP)) {
			if (loaded.add(tile)) {
				ready(tile);
			}
		}
		TileRenderer.FramePlan plan = renderer.planFrame(projection, TileRenderer.Motion.STILL);
		List<TileRenderer.Quad> quads = new ArrayList<>(plan.overview());
		quads.addAll(plan.detailed());
		assertTrue(quads.size() >= 2, "a full view plans several quads");
		List<TileFrameDrawer.Rect> rects = new ArrayList<>(quads.size());
		for (TileRenderer.Quad quad : quads) {
			rects.add(TileFrameDrawer.snap(quad.x(), quad.y(), quad.width(), quad.height()));
		}

		for (int i = 0; i < quads.size(); i++) {
			TileRenderer.Quad a = quads.get(i);
			TileFrameDrawer.Rect snappedA = rects.get(i);
			for (int j = 0; j < quads.size(); j++) {
				if (i == j) {
					continue;
				}
				TileRenderer.Quad b = quads.get(j);
				TileFrameDrawer.Rect snappedB = rects.get(j);
				if (a.texture().zoom() != b.texture().zoom()) {
					continue;
				}
				if (a.y() == b.y() && a.height() == b.height() && a.x() + a.width() == b.x()) {
					assertEquals(snappedA.x() + snappedA.width(), snappedB.x(),
							"vertical seam between " + a.texture() + " and " + b.texture());
				}
				if (a.x() == b.x() && a.width() == b.width() && a.y() + a.height() == b.y()) {
					assertEquals(snappedA.y() + snappedA.height(), snappedB.y(),
							"horizontal seam between " + a.texture() + " and " + b.texture());
				}
			}
		}
	}
}
