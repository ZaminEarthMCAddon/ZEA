package net.zaminmc.zea.map.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MapWorldTest {

	@Test
	void overworldResolvesToEarth() {
		assertEquals(MapWorld.EARTH, MapWorld.ofDimension("minecraft", "overworld"));
	}

	@Test
	void netherAndEndHaveNoMapOfTheirOwn() {
		assertEquals(MapWorld.EARTH, MapWorld.ofDimension("minecraft", "the_nether"));
		assertEquals(MapWorld.EARTH, MapWorld.ofDimension("minecraft", "the_end"));
	}

	@Test
	void bothLunarDimensionsResolveToMoon() {
		// EarthMC has earthmc:space and earthmc:moon; squaremap publishes
		// one world for them. Resolution must not depend on a fetched list.
		assertEquals(MapWorld.MOON, MapWorld.ofDimension("earthmc", "space"));
		assertEquals(MapWorld.MOON, MapWorld.ofDimension("earthmc", "moon"));
	}

	@Test
	void exactSquaremapKeyShapeMatches() {
		assertEquals(MapWorld.MOON, MapWorld.ofDimension("earthmc", "earthmc_moon"));
		assertEquals(MapWorld.EARTH, MapWorld.ofDimension("minecraft", "minecraft_overworld"));
	}

	@Test
	void unknownNamespacesFallBackToEarth() {
		assertEquals(MapWorld.EARTH, MapWorld.ofDimension("somewhere", "else"));
		assertEquals(MapWorld.EARTH, MapWorld.ofDimension(null, "overworld"));
		assertEquals(MapWorld.EARTH, MapWorld.ofDimension("minecraft", null));
	}

	@Test
	void dimensionIdsAreCaseInsensitive() {
		assertEquals(MapWorld.MOON, MapWorld.ofDimension("EarthMC", "Space"));
		assertEquals(MapWorld.EARTH, MapWorld.ofDimension("Minecraft", "Overworld"));
	}

	@Test
	void squaremapKeysParseBothWorlds() {
		assertEquals(MapWorld.EARTH, MapWorld.fromSquaremapKey("minecraft_overworld"));
		assertEquals(MapWorld.MOON, MapWorld.fromSquaremapKey("earthmc_moon"));
		assertNull(MapWorld.fromSquaremapKey("unknown"));
		assertNull(MapWorld.fromSquaremapKey(null));
	}

	@Test
	void publishedKeysAreTheLiveOnes() {
		assertEquals("minecraft_overworld", MapWorld.EARTH.squaremapKey);
		assertEquals("earthmc_moon", MapWorld.MOON.squaremapKey);
	}

	@Test
	void otherSwitchesWorlds() {
		assertEquals(MapWorld.MOON, MapWorld.EARTH.other());
		assertEquals(MapWorld.EARTH, MapWorld.MOON.other());
	}
}
