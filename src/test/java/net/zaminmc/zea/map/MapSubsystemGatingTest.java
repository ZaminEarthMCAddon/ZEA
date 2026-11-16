package net.zaminmc.zea.map;

import net.zaminmc.zea.config.ZeaConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The map's gating matrix: the master switch, EarthMC recognition, and
 * how the map's own "Only on EarthMC" and the mod-wide "EarthMC Only"
 * combine on unrecognized servers.
 */
class MapSubsystemGatingTest {

	private ZeaConfig config(boolean mapEnabled, boolean mapEarthMcOnly, boolean modWideEarthMcOnly) {
		ZeaConfig config = new ZeaConfig();
		config.map.enabled = mapEnabled;
		config.map.earthMcOnly = mapEarthMcOnly;
		config.miscellaneous.earthMcOnly = modWideEarthMcOnly;
		return config;
	}

	@Test
	void masterSwitchOffDisablesEverything() {
		assertFalse(MapSubsystem.featureActive(config(false, false, false), true));
		assertFalse(MapSubsystem.featureActive(config(false, false, false), false));
	}

	@Test
	void onEarthMcTheMapRunsWhateverTheGates() {
		assertTrue(MapSubsystem.featureActive(config(true, true, true), true));
		assertTrue(MapSubsystem.featureActive(config(true, false, false), true));
	}

	@Test
	void offEarthMcTheMapsOwnGateHoldsItBack() {
		// The default: the map's earthMcOnly is on, so other servers idle.
		assertFalse(MapSubsystem.featureActive(config(true, true, false), false));
		// The map's gate off allows testing elsewhere …
		assertTrue(MapSubsystem.featureActive(config(true, false, false), false));
	}

	@Test
	void modWideEarthMcOnlyWinsEvenWhenTheMapWouldAllowTesting() {
		assertFalse(MapSubsystem.featureActive(config(true, false, true), false));
		// … but never fights the map's own gate being off on EarthMC itself.
		assertTrue(MapSubsystem.featureActive(config(true, false, true), true));
	}

	@Test
	void netherAndEndCarryNoMinimap() {
		assertFalse(MapSubsystem.minimapSupported("minecraft", "the_nether"));
		assertFalse(MapSubsystem.minimapSupported("minecraft", "the_end"));
	}

	@Test
	void overworldAndEarthMcDimensionsAreSupported() {
		assertTrue(MapSubsystem.minimapSupported("minecraft", "overworld"));
		assertTrue(MapSubsystem.minimapSupported("earthmc", "moon"));
		assertTrue(MapSubsystem.minimapSupported("earthmc", "space"));
	}
}
