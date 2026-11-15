package net.zaminmc.zea.map.render.overlay;

import net.zaminmc.zea.map.model.MapCamera;
import net.zaminmc.zea.map.model.MapWorld;
import net.zaminmc.zea.map.model.PlayerMarker;
import net.zaminmc.zea.map.projection.MapProjection;
import net.zaminmc.zea.map.render.overlay.PlayerOverlay.Marker;
import net.zaminmc.zea.map.render.overlay.PlayerOverlay.SelfPosition;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The player marker planner: map-frame projection, the feed-interval
 * glide, the teleport guard, and the live self override.
 */
class PlayerOverlayTest {

	private final PlayerOverlay overlay = new PlayerOverlay();

	private static PlayerMarker marker(String name, double x, double z) {
		return new PlayerMarker(name, "00000000-0000-0000-0000-000000000000", x, z, 45.0f,
						MapWorld.EARTH.squaremapKey);
	}

	private static MapProjection view(double camX, double camZ, double ppb, int w, int h) {
		return MapProjection.northUp(new MapCamera(MapWorld.EARTH, camX, camZ, ppb), w, h);
	}

	private Marker planOne(List<PlayerMarker> current, List<PlayerMarker> previous, long nowMs,
					long currentAtMs, long previousAtMs) {
		List<Marker> markers = overlay.plan(current, previous, nowMs, currentAtMs, previousAtMs,
						"Self", null, view(0, 0, 1.0, 400, 300));
		assertEquals(1, markers.size());
		return markers.get(0);
	}

	@Test
	void markersProjectToCentreRelativeMapFrame() {
		// First publish: no previous feed, so no interpolation and the
		// marker sits exactly at its reported position.
		Marker marker = planOne(List.of(marker("Alice", 100, 200)), List.of(), 5_000, 5_000, 0);
		assertEquals(100.0, marker.x(), 1e-9);
		assertEquals(200.0, marker.y(), 1e-9);
		assertEquals("Alice", marker.name());
		assertEquals(45.0f, marker.yawDegrees());
		assertTrue(!marker.self(), "Alice is not the local player");
	}

	@Test
	void markersGlideBetweenFeedsOverTheFeedInterval() {
		PlayerMarker before = marker("Bob", 0, 0);
		PlayerMarker after = marker("Bob", 100, 0);
		// The glide starts when the new feed lands and finishes exactly
		// one feed interval later — smooth, never extrapolated.
		assertEquals(0.0, planOne(List.of(after), List.of(before), 1_000, 1_000, 0).x(), 1e-9);
		assertEquals(50.0, planOne(List.of(after), List.of(before), 1_500, 1_000, 0).x(), 1e-9);
		assertEquals(100.0, planOne(List.of(after), List.of(before), 2_000, 1_000, 0).x(), 1e-9);
		assertEquals(100.0, planOne(List.of(after), List.of(before), 9_000, 1_000, 0).x(), 1e-9,
						"the glide clamps at the newest position");
		assertEquals(0.0, planOne(List.of(after), List.of(before), 900, 1_000, 0).x(), 1e-9,
						"a clock behind the arrival clamps to the previous position");
	}

	@Test
	void teleportStepsSnapInsteadOfSweeping() {
		// The view's half span is max(400, 300) / 2 = 200 blocks: a step
		// within it is motion, a step beyond it is a teleport.
		PlayerMarker before = marker("Bob", 0, 0);
		PlayerMarker walk = marker("Bob", 200, 0);
		PlayerMarker teleport = marker("Bob", 500, 0);
		assertEquals(100.0, planOne(List.of(walk), List.of(before), 1_500, 1_000, 0).x(), 1e-9,
						"a step of exactly the half span still glides");
		assertEquals(500.0, planOne(List.of(teleport), List.of(before), 1_500, 1_000, 0).x(), 1e-9,
						"a teleport lands immediately");
	}

	@Test
	void selfOverrideReplacesTheFeedEntryAndFlagsIt() {
		MapProjection projection = view(0, 0, 1.0, 400, 300);
		PlayerMarker feedSelf = marker("Self", 10, 10);
		PlayerMarker other = marker("Alice", 100, 200);

		List<Marker> markers = overlay.plan(List.of(feedSelf, other), List.of(), 5_000, 5_000, 0,
						"Self", new SelfPosition(99, 99, 90.0f), projection);
		assertEquals(2, markers.size());
		Marker liveSelf = markers.stream().filter(Marker::self).findFirst().orElseThrow();
		assertEquals(99.0, liveSelf.x(), 1e-9);
		assertEquals(99.0, liveSelf.y(), 1e-9);
		assertEquals(90.0f, liveSelf.yawDegrees());
		assertEquals("Self", liveSelf.name());
		assertTrue(markers.stream().noneMatch(m -> m.x() == 10.0 && m.y() == 10.0),
						"the feed's stale self entry is gone");

		markers = overlay.plan(List.of(feedSelf, other), List.of(), 5_000, 5_000, 0,
						"Self", null, projection);
		Marker fromFeed = markers.stream().filter(Marker::self).findFirst().orElseThrow();
		assertEquals(10.0, fromFeed.x(), 1e-9, "without an override the feed entry draws");
	}

	@Test
	void emptyFeedStillDrawsTheOverride() {
		MapProjection projection = view(0, 0, 1.0, 400, 300);
		List<Marker> markers = overlay.plan(List.of(), List.of(), 5_000, 5_000, 0,
						"Self", new SelfPosition(-40, 60, 0.0f), projection);
		assertEquals(1, markers.size());
		assertEquals(-40.0, markers.get(0).x(), 1e-9);
		assertEquals(60.0, markers.get(0).y(), 1e-9);
		assertTrue(markers.get(0).self());
	}
}
