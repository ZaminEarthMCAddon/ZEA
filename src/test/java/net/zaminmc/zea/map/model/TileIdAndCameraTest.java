package net.zaminmc.zea.map.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TileIdAndCameraTest {

	@Test
	void parentWalksThePyramid() {
		TileId tile = new TileId(MapWorld.EARTH, TileId.TileSource.SQUAREMAP, 3, 12, -5);
		TileId parent = tile.parent();
		assertEquals(2, parent.zoom());
		assertEquals(6, parent.x());
		assertEquals(-3, parent.z());
		assertNull(new TileId(MapWorld.EARTH, TileId.TileSource.SQUAREMAP, 0, 0, 0).parent());
	}

	@Test
	void texturePathCarriesWorldAndSource() {
		String path = new TileId(MapWorld.MOON, TileId.TileSource.SQUAREMAP, 3, 12, -5).texturePath();
		assertEquals("squaremap/earthmc_moon/3/12_-5", path);
		assertEquals("repo/minecraft_overworld/0/0_0",
				new TileId(MapWorld.EARTH, TileId.TileSource.REPO, 0, 0, 0).texturePath());
	}

	@Test
	void negativeZoomIsRejected() {
		assertThrows(IllegalArgumentException.class,
				() -> new TileId(MapWorld.EARTH, TileId.TileSource.SQUAREMAP, -1, 0, 0));
	}

	@Test
	void sameCoordinatesAreDifferentTilesPerWorldAndSource() {
		TileId a = new TileId(MapWorld.EARTH, TileId.TileSource.SQUAREMAP, 2, 5, 5);
		TileId b = new TileId(MapWorld.MOON, TileId.TileSource.SQUAREMAP, 2, 5, 5);
		TileId c = new TileId(MapWorld.EARTH, TileId.TileSource.REPO, 2, 5, 5);
		assertTrue(!a.equals(b));
		assertTrue(!a.equals(c));
	}

	@Test
	void townClaimBoundingBoxAndChunks() {
		List<int[][]> rings = ChunkRingsTestHelper.twoByTwo();
		TownClaim claim = TownClaim.of(MapWorld.EARTH, "Testville", null, 0x123456, 0x654321, rings);
		assertEquals("testville", claim.key());
		assertEquals(0, claim.minX());
		assertEquals(32, claim.maxX());
		assertEquals(0, claim.minZ());
		assertEquals(32, claim.maxZ());
		assertEquals(16, claim.centerX());
		assertEquals(16, claim.centerZ());
		assertEquals(4, claim.approximateChunkCount());
	}

	@Test
	void townClaimSignatureFollowsGeometryOnly() {
		TownClaim a = TownClaim.of(MapWorld.EARTH, "T", null, 1, 2, ChunkRingsTestHelper.twoByTwo());
		TownClaim sameGeometry = TownClaim.of(MapWorld.EARTH, "T", null, 99, 98, ChunkRingsTestHelper.twoByTwo());
		TownClaim otherWorld = TownClaim.of(MapWorld.MOON, "T", null, 1, 2, ChunkRingsTestHelper.twoByTwo());
		TownClaim otherGeometry = TownClaim.of(MapWorld.EARTH, "T", null, 1, 2,
				singleChunkRing());
		assertEquals(a.geometrySignature(), sameGeometry.geometrySignature());
		assertTrue(a.geometrySignature() != otherWorld.geometrySignature());
		assertTrue(a.geometrySignature() != otherGeometry.geometrySignature());
	}

	@Test
	void withRingsKeepsIdentityAndColors() {
		TownClaim original = TownClaim.of(MapWorld.EARTH, "T", null, 1, 2, ChunkRingsTestHelper.twoByTwo());
		TownClaim rebuilt = original.withRings(singleChunkRing());
		assertEquals(original.world(), rebuilt.world());
		assertEquals(original.key(), rebuilt.key());
		assertEquals(original.name(), rebuilt.name());
		assertEquals(original.outlineRgb(), rebuilt.outlineRgb());
		assertEquals(1, rebuilt.approximateChunkCount());
		assertTrue(original.covers(rebuilt) == false);
	}

	@Test
	void coversDetectsWhenPublishedGeometryCatchesUp() {
		List<int[][]> bigger = ChunkRingsTestHelper.twoByTwo();
		List<int[][]> smaller = singleChunkRing();
		TownClaim published = TownClaim.of(MapWorld.EARTH, "T", null, 1, 2, bigger);
		TownClaim apiOverride = TownClaim.of(MapWorld.EARTH, "T", null, 1, 2, bigger);
		TownClaim smallerClaim = TownClaim.of(MapWorld.EARTH, "T", null, 1, 2, smaller);
		assertTrue(published.covers(apiOverride));
		assertTrue(!published.covers(smallerClaim));
	}

	@Test
	void argbColorsApplyAlpha() {
		TownClaim claim = TownClaim.of(MapWorld.EARTH, "T", null, 0xFF00FF, 0x00FF00,
				singleChunkRing());
		assertEquals(0x80FF00FF, claim.argbOutline(0x80));
		assertEquals(0xFF00FF00, claim.argbFill(255));
		assertEquals(0xFF00FF00, claim.argbFill(300), "alpha clamps to 255");
	}

	@Test
	void zoomKeepsTheAnchorPinned() {
		MapCamera camera = MapCamera.at(MapWorld.EARTH, 1000, 2000).zoom(1.0);
		MapCamera zoomed = camera.zoomedAround(1200, 2100, 2.0);
		assertEquals(2.0, zoomed.pixelsPerBlock());
		// The anchor stays at the same screen offset from the center.
		double before = (1200 - camera.centerX()) * camera.pixelsPerBlock();
		double after = (1200 - zoomed.centerX()) * zoomed.pixelsPerBlock();
		assertEquals(before, after, 1e-6);
	}

	@Test
	void panningIsInvertedWorldMovement() {
		MapCamera camera = MapCamera.at(MapWorld.MOON, 0, 0).zoom(2.0);
		MapCamera panned = camera.pannedBy(100, -50);
		assertEquals(-50, panned.centerX(), 1e-9);
		assertEquals(25, panned.centerZ(), 1e-9);
	}

	@Test
	void zoomIsClamped() {
		MapCamera camera = MapCamera.at(MapWorld.EARTH, 0, 0).zoom(100);
		assertEquals(MapCamera.MAX_PIXELS_PER_BLOCK, camera.pixelsPerBlock());
		MapCamera tiny = MapCamera.at(MapWorld.EARTH, 0, 0).zoom(1e-9);
		assertEquals(MapCamera.MIN_PIXELS_PER_BLOCK, tiny.pixelsPerBlock());
	}

	@Test
	void cameraCenterClampsToWorldBounds() {
		MapCamera far = MapCamera.at(MapWorld.EARTH, 1_000_000, -1_000_000).zoom(1);
		MapCamera clamped = far.centeredOn(1_000_000, -1_000_000);
		assertEquals(MapWorld.EARTH.worldWidth / 2.0, clamped.centerX());
		assertEquals(-MapWorld.EARTH.worldHeight / 2.0, clamped.centerZ());
	}

	@Test
	void screenPickingRoundTrips() {
		MapCamera camera = MapCamera.at(MapWorld.EARTH, 500, -500).zoom(4);
		int width = 1920, height = 1080;
		double worldX = camera.worldXAt(100, width);
		double worldZ = camera.worldZAt(700, height);
		// Pick the screen point back from the world point.
		double screenX = (worldX - camera.centerX()) * camera.pixelsPerBlock() + width / 2.0;
		double screenY = (worldZ - camera.centerZ()) * camera.pixelsPerBlock() + height / 2.0;
		assertEquals(100, screenX, 1e-6);
		assertEquals(700, screenY, 1e-6);
		assertArrayEquals(new double[] {500 - 240, -500 - 135},
				new double[] {camera.viewLeft(1920), camera.viewTop(1080)}, 1e-9);
	}


	/** One claimed chunk at the origin, as rings. */
	private static List<int[][]> singleChunkRing() {
		return net.zaminmc.zea.map.geometry.ChunkRings.trace(
				java.util.Set.of(net.zaminmc.zea.map.geometry.ChunkRings.key(0, 0)));
	}

	/** Small helper so the camera tests can build claims without importing geometry internals. */
	private static final class ChunkRingsTestHelper {
		static List<int[][]> twoByTwo() {
			return net.zaminmc.zea.map.geometry.ChunkRings.trace(
					java.util.Set.of(net.zaminmc.zea.map.geometry.ChunkRings.key(0, 0),
							net.zaminmc.zea.map.geometry.ChunkRings.key(1, 0),
							net.zaminmc.zea.map.geometry.ChunkRings.key(0, 1),
							net.zaminmc.zea.map.geometry.ChunkRings.key(1, 1)));
		}
	}

	@Test
	void helperSanity() {
		assertSame(MapWorld.EARTH, MapWorld.EARTH);
	}
}
