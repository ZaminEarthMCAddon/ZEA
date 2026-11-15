package net.zaminmc.zea.map.source.squaremap;

import net.zaminmc.zea.map.model.MapWorld;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SquaremapEndpointsTest {

	@Test
	void markersUrlCarriesTheWorld() {
		assertEquals("https://map.earthmc.net/tiles/minecraft_overworld/markers.json",
				SquaremapEndpoints.markers(MapWorld.EARTH));
		assertEquals("https://map.earthmc.net/tiles/earthmc_moon/markers.json",
				SquaremapEndpoints.markers(MapWorld.MOON));
	}

	@Test
	void settingsUrlIsFixed() {
		assertEquals("https://map.earthmc.net/tiles/settings.json", SquaremapEndpoints.worlds());
	}

	@Test
	void tileUrlMatchesTheLivePyramid() {
		assertEquals("https://map.earthmc.net/tiles/minecraft_overworld/3/12_-5.png",
				SquaremapEndpoints.tile(MapWorld.EARTH, 3, 12, -5));
		assertEquals("https://map.earthmc.net/tiles/earthmc_moon/0/0_0.png",
				SquaremapEndpoints.tile(MapWorld.MOON, 0, 0, 0));
	}

	@Test
	void playersFeedIsOnTheApiHostNotSquaremap() {
		// The feed needs an API key; the key must never travel to the
		// squaremap host, so the URL is a fixed constant.
		assertEquals("https://api.earthmc.net/map/v1/players", SquaremapEndpoints.PLAYERS_URL);
	}
}
