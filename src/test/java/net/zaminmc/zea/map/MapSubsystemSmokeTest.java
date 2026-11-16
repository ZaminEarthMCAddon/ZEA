package net.zaminmc.zea.map;

import net.zaminmc.zea.config.ZeaConfig;
import net.zaminmc.zea.map.model.MapWorld;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Headless smoke of the subsystem's non-GL paths: construction wires the
 * caches and clients without a client instance, and the camera math
 * resolves through the config's semantic choices.
 */
class MapSubsystemSmokeTest {

	private static final MapSubsystem MAP = new MapSubsystem(new ZeaConfig(), () -> true);

	@AfterAll
	static void shutdown() {
		MAP.shutdown();
	}

	@Test
	void constructionWiresWithoutAClient() {
		assertTrue(MAP.active());
		assertEquals(MapWorld.EARTH, MAP.playerWorld());
	}

	@Test
	void minimapCameraUsesTheConfiguredSpan() {
		// 160 px showing 256 blocks: 0.625 px per block.
		var camera = MAP.minimapCamera(1000.5, -2000.25);
		assertEquals(0.625, camera.pixelsPerBlock(), 1e-9);
		assertEquals(1000.5, camera.centerX(), 1e-9);
		assertEquals(-2000.25, camera.centerZ(), 1e-9);
	}

	@Test
	void worldCameraPersistsPerWorld() {
		var first = MAP.worldCamera(MapWorld.MOON, 10, 20);
		var second = MAP.worldCamera(MapWorld.MOON, 99, 99);
		assertEquals(first, second, "the stored camera survives, it is not re-created at the player");
	}
}
