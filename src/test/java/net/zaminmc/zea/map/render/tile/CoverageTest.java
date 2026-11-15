package net.zaminmc.zea.map.render.tile;

import net.zaminmc.zea.map.model.MapWorld;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The sticky gate: activation only at full coverage, never on an empty
 * view, re-armed by a zoom or world change, re-armed explicitly for
 * cache clears.
 */
class CoverageTest {

	@Test
	void activatesOnlyAtFullCoverage() {
		Coverage coverage = new Coverage();
		assertFalse(coverage.update(MapWorld.EARTH, 5, 3, 4));
		assertFalse(coverage.active(), "partial coverage must not activate");
		assertTrue(coverage.update(MapWorld.EARTH, 5, 4, 4), "full coverage activates");
	}

	@Test
	void emptyViewNeverActivates() {
		Coverage coverage = new Coverage();
		assertFalse(coverage.update(MapWorld.EARTH, 5, 0, 0));
		assertFalse(coverage.update(MapWorld.EARTH, 5, 7, 0), "vacuous coverage is not coverage");
	}

	@Test
	void staysStickyWhenCoverageDips() {
		Coverage coverage = new Coverage();
		coverage.update(MapWorld.EARTH, 5, 4, 4);
		assertTrue(coverage.update(MapWorld.EARTH, 5, 1, 4), "sticky through a dip");
	}

	@Test
	void zoomChangeRearms() {
		Coverage coverage = new Coverage();
		coverage.update(MapWorld.EARTH, 5, 4, 4);
		assertTrue(coverage.active());
		// The new zoom starts over: partial coverage must not activate,
		// however sticky the old zoom's gate was.
		assertFalse(coverage.update(MapWorld.EARTH, 4, 3, 9));
		assertFalse(coverage.active());
		assertTrue(coverage.update(MapWorld.EARTH, 4, 9, 9),
					"full coverage at the new zoom activates on its first frame");
	}

	@Test
	void worldChangeRearms() {
		Coverage coverage = new Coverage();
		coverage.update(MapWorld.EARTH, 5, 4, 4);
		assertFalse(coverage.update(MapWorld.MOON, 5, 1, 2),
					"the other world's partial coverage must not activate");
		assertTrue(coverage.update(MapWorld.MOON, 5, 2, 2));
	}

	@Test
	void rearmKeepsViewIdentity() {
		Coverage coverage = new Coverage();
		coverage.update(MapWorld.MOON, 3, 2, 2);
		coverage.rearm();
		assertFalse(coverage.active());
		assertTrue(coverage.update(MapWorld.MOON, 3, 2, 2), "same view can re-activate");
		assertEquals(MapWorld.MOON, coverage.world());
		assertEquals(3, coverage.zoom());
	}

	@Test
	void resetForgetsEverything() {
		Coverage coverage = new Coverage();
		coverage.update(MapWorld.EARTH, 5, 4, 4);
		coverage.reset();
		assertFalse(coverage.active());
		assertEquals(null, coverage.world());
		assertEquals(-1, coverage.zoom());
	}
}
