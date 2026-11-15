package net.zaminmc.zea.map.cache;

import net.zaminmc.zea.map.model.MapWorld;
import net.zaminmc.zea.map.model.PlayerMarker;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the per-world marker store: world splitting with the
 * legacy blank-world rule, the memoisation that keeps instances when
 * the feed content repeats, and the previous-snapshot bookkeeping the
 * interpolator anchors to.
 */
class PlayerStoreTest {

	private final PlayerStore store = new PlayerStore();

	private static PlayerMarker at(String name, double x, double z, String world) {
		return new PlayerMarker(name, null, x, z, 0f, world);
	}

	@Test
	void markersAreSplitByWorld() {
		store.publish(List.of(
						at("Alice", 100, 200, "minecraft_overworld"),
						at("Bob", 300, 400, "earthmc_moon"),
						at("Carol", 500, 600, "minecraft_overworld")), 1_000);
		List<PlayerMarker> earth = store.markers(MapWorld.EARTH);
		List<PlayerMarker> moon = store.markers(MapWorld.MOON);
		assertEquals(2, earth.size());
		assertEquals(1, moon.size());
		assertEquals("Alice", earth.get(0).name());
		assertEquals("Carol", earth.get(1).name());
		assertEquals("Bob", moon.get(0).name());
		assertEquals(1_000, store.publishedAtMs());
		assertTrue(store.hasData());
	}

	@Test
	void blankWorldMarkersBelongToEveryWorld() {
		store.publish(List.of(
						at("Legacy", 0, 0, null),
						at("Modern", 1, 1, "earthmc_moon")), 1_000);
		assertEquals(1, store.markers(MapWorld.EARTH).size());
		assertEquals(2, store.markers(MapWorld.MOON).size());
	}

	@Test
	void identicalFeedsKeepInstancesAndTimestamps() {
		store.publish(List.of(at("Alice", 100, 200, "minecraft_overworld")), 1_000);
		List<PlayerMarker> first = store.markers(MapWorld.EARTH);
		PlayerMarker alice = first.get(0);
		List<PlayerMarker> firstPrevious = store.previous(MapWorld.EARTH);
		store.publish(List.of(at("Alice", 100, 200, "minecraft_overworld")), 2_000);
		assertSame(alice, store.markers(MapWorld.EARTH).get(0),
						"no churn on an identical document");
		assertSame(firstPrevious, store.previous(MapWorld.EARTH),
						"the interpolation anchor is not disturbed either");
		assertEquals(1_000, store.publishedAtMs(), "the publish clock did not move");
	}

	@Test
	void changedFeedsRotateTheSnapshots() {
		store.publish(List.of(at("Alice", 100, 200, "minecraft_overworld")), 1_000);
		List<PlayerMarker> before = store.markers(MapWorld.EARTH);
		store.publish(List.of(at("Alice", 120, 220, "minecraft_overworld")), 2_000);
		List<PlayerMarker> now = store.markers(MapWorld.EARTH);
		assertEquals(120, now.get(0).x());
		assertSame(before.get(0), store.previous(MapWorld.EARTH).get(0),
						"the renderer can anchor to the old snapshot");
		assertEquals(1_000, store.previousAtMs());
		assertEquals(2_000, store.publishedAtMs());
	}

	@Test
	void emptyFeedsClearTheMap() {
		store.publish(List.of(at("Alice", 1, 2, "minecraft_overworld")), 1_000);
		// A parsed-empty feed is an empty server, not a failure — a
		// failed fetch never reaches the store at all (the client's
		// future fails and freshness says so instead).
		store.publish(List.of(), 2_000);
		assertTrue(store.markers(MapWorld.EARTH).isEmpty());
		assertEquals("Alice", store.previous(MapWorld.EARTH).get(0).name());
	}

	@Test
	void theSelfMarkerLooksUpByName() {
		store.publish(List.of(
						at("Alice", 100, 200, "minecraft_overworld"),
						at("bob", 300, 400, "minecraft_overworld")), 1_000);
		assertSame(store.markers(MapWorld.EARTH).get(1), store.marker(MapWorld.EARTH, "Bob"));
		assertNull(store.marker(MapWorld.EARTH, "Nobody"));
		assertNull(store.marker(MapWorld.MOON, "Bob"), "Bob is on Earth in this feed");
		assertNull(store.marker(MapWorld.EARTH, null));
	}

	@Test
	void aWorldChangeNeedsNoClearing() {
		store.publish(List.of(
						at("Alice", 100, 200, "minecraft_overworld"),
						at("Bob", 300, 400, "earthmc_moon")), 1_000);
		assertEquals(1, store.markers(MapWorld.EARTH).size());
		assertEquals(1, store.markers(MapWorld.MOON).size());
		// The next poll refreshes both worlds at once; the store never
		// holds stale cross-world state to begin with.
		store.publish(List.of(
						at("Alice", 110, 210, "minecraft_overworld"),
						at("Bob", 310, 410, "earthmc_moon")), 2_000);
		assertEquals(110, store.markers(MapWorld.EARTH).get(0).x());
		assertEquals(310, store.markers(MapWorld.MOON).get(0).x());
		assertFalse(store.previous(MapWorld.EARTH).isEmpty());
		assertFalse(store.previous(MapWorld.MOON).isEmpty());
	}

	@Test
	void beforeAnyPollTheStoreIsEmpty() {
		PlayerStore fresh = new PlayerStore();
		assertTrue(fresh.markers(MapWorld.EARTH).isEmpty());
		assertTrue(fresh.previous(MapWorld.EARTH).isEmpty());
		assertFalse(fresh.hasData());
		assertEquals(0, fresh.publishedAtMs());
	}
}
