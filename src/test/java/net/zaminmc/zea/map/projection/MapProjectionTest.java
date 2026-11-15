package net.zaminmc.zea.map.projection;

import net.zaminmc.zea.map.model.MapCamera;
import net.zaminmc.zea.map.model.MapWorld;
import net.zaminmc.zea.map.model.TileId;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the projection math: identity round-trips under rotation, the
 * yaw convention, the tile pyramid numbers verified live against
 * map.earthmc.net, and the parent-fallback regions.
 */
class MapProjectionTest {

	private static final double EPSILON = 1e-9;

	@Test
	void northUpMapsEastAndSouthToScreenRightAndDown() {
		MapProjection projection = MapProjection.northUp(
						new MapCamera(MapWorld.EARTH, 0, 0, 1.0), 800, 600);
		assertEquals(400, projection.screenX(0, 0), EPSILON);
		assertEquals(300, projection.screenY(0, 0), EPSILON);
		// +X is east, on screen to the right; +Z is south, on screen down.
		assertEquals(500, projection.screenX(100, 0), EPSILON);
		assertEquals(400, projection.screenY(0, 100), EPSILON);
	}

	@Test
	void screenAndWorldAreExactInversesAtAnyRotation() {
		double[] rotations = {0.0, Math.PI / 4, Math.PI / 2, Math.PI, 2.4};
		for (double rotation : rotations) {
			MapProjection projection = new MapProjection(
							new MapCamera(MapWorld.EARTH, 1234.5, -987.5, 0.37), 800, 600, rotation);
			for (double[] point : new double[][]{{0, 0}, {800, 600}, {-40.25, 620.75}, {1234.5, -987.5}}) {
				double worldX = projection.worldX(point[0], point[1]);
				double worldZ = projection.worldZ(point[0], point[1]);
				assertEquals(point[0], projection.screenX(worldX, worldZ), EPSILON,
								"screenX round-trip at rotation " + rotation);
				assertEquals(point[1], projection.screenY(worldX, worldZ), EPSILON,
								"screenY round-trip at rotation " + rotation);
			}
		}
	}

	@Test
	void rotationTurnsNorthToLeftAtQuarterTurn() {
		MapProjection projection = new MapProjection(
						new MapCamera(MapWorld.EARTH, 0, 0, 1.0), 800, 600, Math.PI / 2);
		// North is -Z; at a quarter turn it must point screen-left.
		double northX = projection.screenX(0, -10) - projection.screenX(0, 0);
		double northY = projection.screenY(0, -10) - projection.screenY(0, 0);
		assertEquals(-10, northX, EPSILON);
		assertEquals(0, northY, EPSILON);
		assertEquals(-Math.PI / 2, projection.iconRotation(), EPSILON);
	}

	@Test
	void yawConventionPointsThePlayerHeadingUp() {
		// Yaw 0 faces south (+Z): south must be screen-up.
		MapProjection south = new MapProjection(new MapCamera(MapWorld.EARTH, 0, 0, 1.0),
						800, 600, MapProjection.rotationForYaw(0));
		assertEquals(-100, south.screenY(0, 100) - south.screenY(0, 0), EPSILON);
		// Yaw 90 faces west (-X): west must be screen-up.
		MapProjection west = new MapProjection(new MapCamera(MapWorld.EARTH, 0, 0, 1.0),
						800, 600, MapProjection.rotationForYaw(90));
		assertEquals(-100, west.screenY(-100, 0) - west.screenY(0, 0), EPSILON);
		assertEquals(0, west.screenX(-100, 0) - west.screenX(0, 0), EPSILON);
	}

	@Test
	void tileZoomChoosesSharpButPublishedLevels() {
		assertEquals(5, MapProjection.chooseTileZoom(1.0));
		assertEquals(5, MapProjection.chooseTileZoom(8.0));
		// The demand floor(log2(ppb)) plus the bias, clamped to the
		// published pyramid: bias +2 at the overview end shrinks as the
		// camera zooms out below zoom 0's native scale.
		assertEquals(2, MapProjection.chooseTileZoom(1.0 / 32));
		assertEquals(1, MapProjection.chooseTileZoom(1.0 / 64));
		assertEquals(3, MapProjection.chooseTileZoom(1.0 / 16));
		assertEquals(0, MapProjection.chooseTileZoom(1.0 / 128));
		assertEquals(0, MapProjection.chooseTileZoom(0));
		assertEquals(0, MapProjection.chooseTileZoom(Double.NaN));
	}

	@Test
	void tilePyramidNumbersMatchTheLiveInstance() {
		assertEquals(512, MapProjection.tileWorldSpan(5));
		assertEquals(1024, MapProjection.tileWorldSpan(4));
		assertEquals(16384, MapProjection.tileWorldSpan(0));
		assertEquals(1.0, MapProjection.pixelsPerBlockAt(5), EPSILON);
		assertEquals(1.0 / 32, MapProjection.pixelsPerBlockAt(0), EPSILON);
	}

	@Test
	void tileIndicesFloorDivideIncludingNegatives() {
		assertEquals(0, MapProjection.tileX(0, 5));
		assertEquals(0, MapProjection.tileX(511, 5));
		assertEquals(1, MapProjection.tileX(512, 5));
		assertEquals(-1, MapProjection.tileX(-1, 5));
		assertEquals(-1, MapProjection.tileX(-512, 5));
		assertEquals(-2, MapProjection.tileX(-513, 5));
		// The Earth world border's west edge: -64512 is tile -126.
		assertEquals(-126, MapProjection.tileX(-64512, 5));
		assertEquals(-127, MapProjection.tileX(-64513, 5));
	}

	@Test
	void visibleTilesCoversTheDiagonalUnderRotation() {
		MapCamera camera = new MapCamera(MapWorld.EARTH, 0, 0, 1.0);
		for (double rotation : new double[]{0.0, Math.PI / 3}) {
			MapProjection projection = new MapProjection(camera, 100, 100, rotation);
			List<TileId> tiles = projection.visibleTiles(5, 0, TileId.TileSource.SQUAREMAP);
			// The diagonal of a 100px surface is ~141 blocks, so the
			// range must at least cover tiles -1..0 on both axes.
			assertTrue(tiles.contains(new TileId(MapWorld.EARTH, TileId.TileSource.SQUAREMAP, 5, 0, 0)));
			assertTrue(tiles.contains(new TileId(MapWorld.EARTH, TileId.TileSource.SQUAREMAP, 5, -1, -1)));
			assertTrue(tiles.contains(new TileId(MapWorld.EARTH, TileId.TileSource.SQUAREMAP, 5, 0, -1)));
			assertTrue(tiles.contains(new TileId(MapWorld.EARTH, TileId.TileSource.SQUAREMAP, 5, -1, 0)));
			// At zoom 0 each tile spans 16384 blocks; the diagonal
			// superset still straddles the origin's tile seam.
			assertEquals(4, projection.visibleTiles(0, 0, TileId.TileSource.SQUAREMAP).size());
			assertEquals(new TileId(MapWorld.EARTH, TileId.TileSource.SQUAREMAP, 0, 0, 0),
							projection.centerTile(0));
		}
	}

	@Test
	void parentRegionIsTheExactQuadrant() {
		TileId parent = new TileId(MapWorld.EARTH, TileId.TileSource.SQUAREMAP, 4, 1, 1);
		TileId northWest = new TileId(MapWorld.EARTH, TileId.TileSource.SQUAREMAP, 5, 2, 2);
		TileId southEast = new TileId(MapWorld.EARTH, TileId.TileSource.SQUAREMAP, 5, 3, 3);
		MapProjection.TileRegion expectedNorthWest = MapProjection.regionWithin(northWest, parent);
		assertEquals(0.0, expectedNorthWest.u0(), EPSILON);
		assertEquals(0.0, expectedNorthWest.v0(), EPSILON);
		assertEquals(0.5, expectedNorthWest.u1(), EPSILON);
		assertEquals(0.5, expectedNorthWest.v1(), EPSILON);
		MapProjection.TileRegion expectedSouthEast = MapProjection.regionWithin(southEast, parent);
		assertEquals(0.5, expectedSouthEast.u0(), EPSILON);
		assertEquals(0.5, expectedSouthEast.v0(), EPSILON);
		assertEquals(1.0, expectedSouthEast.u1(), EPSILON);
		assertEquals(1.0, expectedSouthEast.v1(), EPSILON);
	}

	@Test
	void parentRegionHandlesNegativeQuadrants() {
		TileId parent = new TileId(MapWorld.EARTH, TileId.TileSource.SQUAREMAP, 4, -1, -1);
		// Child (-1, -1) is the parent's south-east quadrant: -1*512 - (-1*1024) = 512.
		MapProjection.TileRegion region = MapProjection.regionWithin(
						new TileId(MapWorld.EARTH, TileId.TileSource.SQUAREMAP, 5, -1, -1), parent);
		assertEquals(0.5, region.u0(), EPSILON);
		assertEquals(0.5, region.v0(), EPSILON);
		assertEquals(1.0, region.u1(), EPSILON);
		MapProjection.TileRegion northWest = MapProjection.regionWithin(
						new TileId(MapWorld.EARTH, TileId.TileSource.SQUAREMAP, 5, -2, -2), parent);
		assertEquals(0.0, northWest.u0(), EPSILON);
		assertEquals(0.0, northWest.v0(), EPSILON);
	}

	@Test
	void grandparentRegionIsASixteenth() {
		// Zoom 5 tile (3, 6) sits in zoom 3 tile (0, 1): column 3 of 4,
		// row 2 of 4 within the ancestor.
		TileId grandparent = new TileId(MapWorld.EARTH, TileId.TileSource.SQUAREMAP, 3, 0, 1);
		TileId tile = new TileId(MapWorld.EARTH, TileId.TileSource.SQUAREMAP, 5, 3, 6);
		MapProjection.TileRegion region = MapProjection.regionWithin(tile, grandparent);
		assertEquals(3.0 / 4, region.u0(), EPSILON);
		assertEquals(2.0 / 4, region.v0(), EPSILON);
		assertEquals(1.0, region.u1(), EPSILON);
		assertEquals(3.0 / 4, region.v1(), EPSILON);
	}

	@Test
	void nonDescendantTilesAreRejected() {
		TileId parent = new TileId(MapWorld.EARTH, TileId.TileSource.SQUAREMAP, 4, 1, 1);
		assertThrows(IllegalArgumentException.class, () -> MapProjection.regionWithin(
						new TileId(MapWorld.EARTH, TileId.TileSource.SQUAREMAP, 5, 4, 4), parent));
		assertThrows(IllegalArgumentException.class, () -> MapProjection.regionWithin(parent, parent));
		assertThrows(IllegalArgumentException.class, () -> MapProjection.regionWithin(parent,
						new TileId(MapWorld.EARTH, TileId.TileSource.SQUAREMAP, 5, 1, 1)));
	}

	@Test
	void regionsMustStayInsideTheTile() {
		assertThrows(IllegalArgumentException.class,
						() -> new MapProjection.TileRegion(-0.1, 0, 0.5, 0.5));
		assertThrows(IllegalArgumentException.class,
						() -> new MapProjection.TileRegion(0, 0, 1.1, 0.5));
		assertThrows(IllegalArgumentException.class,
						() -> new MapProjection.TileRegion(0.5, 0, 0.4, 0.5));
	}

	@Test
	void diagonalBlocksScalesWithZoom() {
		MapProjection zoomedIn = new MapProjection(
						new MapCamera(MapWorld.EARTH, 0, 0, 8.0), 100, 100, 0);
		MapProjection zoomedOut = MapProjection.northUp(
						new MapCamera(MapWorld.EARTH, 0, 0, 1.0 / 64), 100, 100);
		assertEquals(100 * Math.sqrt(2) / 8, zoomedIn.diagonalBlocks(), EPSILON);
		assertEquals(100 * Math.sqrt(2) * 64, zoomedOut.diagonalBlocks(), EPSILON);
	}
}
