package net.zaminmc.zea.map.model;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerMarkerTest {

	private static final String FEED = """
			{"max":800,"current":327,"players":[
			  {"world":"minecraft_overworld","name":"kingMIN0S","x":8136,"y":64,"z":-8876,
			   "uuid":"d4edccc3ae2a43b1b1dcc386659b3a7f","yaw":-90},
			  {"world":"earthmc_moon","name":"LunarFox","x":8136,"y":64,"z":-8876,
			   "uuid":"abc","yaw":157,"premium":true},
			  {"name":"LegacyShape","x":10,"z":20,"yaw":0,"uuid":"def"},
			  {"world":"minecraft_overworld","name":"","x":1,"z":1},
			  "not-an-object",
			  {"world":"minecraft_overworld","name":"Ok","position":[123.5,-45.25],"yaw":90}
			]}""";

	@Test
	void parsesTheLiveFeedShape() {
		List<PlayerMarker> markers = PlayerMarker.parseAll(JsonParser.parseString(FEED));
		assertEquals(4, markers.size(), "blank names and non-objects are skipped");
		PlayerMarker first = markers.get(0);
		assertEquals("kingMIN0S", first.name());
		assertEquals(8136, first.x());
		assertEquals(-8876, first.z());
		assertEquals(-90f, first.yaw());
		assertEquals("minecraft_overworld", first.world());
	}

	@Test
	void positionArrayFormIsAccepted() {
		List<PlayerMarker> markers = PlayerMarker.parseAll(JsonParser.parseString(FEED));
		PlayerMarker ok = markers.get(3);
		assertEquals("Ok", ok.name());
		assertEquals(123.5, ok.x());
		assertEquals(-45.25, ok.z());
	}

	@Test
	void worldMembershipIsExactWhenPresent() {
		List<PlayerMarker> markers = PlayerMarker.parseAll(JsonParser.parseString(FEED));
		PlayerMarker earth = markers.get(0);
		PlayerMarker moon = markers.get(1);
		PlayerMarker legacy = markers.get(2);

		assertTrue(earth.inWorld(MapWorld.EARTH));
		assertTrue(!earth.inWorld(MapWorld.MOON));
		assertTrue(moon.inWorld(MapWorld.MOON));
		assertTrue(!moon.inWorld(MapWorld.EARTH));
		// A blank world (legacy feed shape) matches whatever is asked —
		// the same compatibility rule the reference implementation uses.
		assertTrue(legacy.inWorld(MapWorld.EARTH));
		assertTrue(legacy.inWorld(MapWorld.MOON));
	}

	@Test
	void coordinatesNeverCrossWorlds() {
		// Same numbers on both worlds — the isolation is the world field,
		// not the coordinates.
		List<PlayerMarker> markers = PlayerMarker.parseAll(JsonParser.parseString(FEED));
		assertEquals(8136, markers.get(0).x());
		assertEquals(8136, markers.get(1).x());
		assertEquals(markers.get(0).x(), markers.get(1).x());
		assertTrue(markers.get(0).inWorld(MapWorld.EARTH) != markers.get(1).inWorld(MapWorld.EARTH));
	}

	@Test
	void keyIsLowerCasedName() {
		PlayerMarker marker = new PlayerMarker("SomePlayer", "uuid", 0, 0, 0, null);
		assertEquals("someplayer", marker.key());
	}

	@Test
	void malformedFeedYieldsEmptyList() {
		assertEquals(List.of(), PlayerMarker.parseAll(null));
		assertEquals(List.of(), PlayerMarker.parseAll(JsonParser.parseString("[]")));
		assertEquals(List.of(), PlayerMarker.parseAll(JsonParser.parseString("{}")));
		assertEquals(List.of(), PlayerMarker.parseAll(JsonParser.parseString("{\"players\":42}")));
	}
}
