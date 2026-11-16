package net.zaminmc.zea.map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The minimap's on-screen geometry: berth, clamps, label rows, scale bar. */
class MinimapLayoutTest {

	@Test
	void thePanelSitsInTheTopRightCorner() {
		MinimapLayout.Rect panel = MinimapLayout.panel(160, 960, 540);
		assertEquals(960 - 160 - MinimapLayout.MARGIN, panel.x());
		assertEquals(MinimapLayout.MARGIN, panel.y());
		assertEquals(160, panel.width());
		assertEquals(160, panel.height());
	}

	@Test
	void thePanelClampsDownOnTinyScreens() {
		MinimapLayout.Rect panel = MinimapLayout.panel(320, 100, 100);
		assertTrue(panel.width() <= 100 - 2, "the panel fits its screen");
		assertTrue(panel.x() >= 0 && panel.y() >= 0);
	}

	@Test
	void labelRowsStackUnderThePanelAndDisappearWhenTheyCannotFit() {
		MinimapLayout.Rect panel = MinimapLayout.panel(160, 960, 540);
		int coordsY = MinimapLayout.coordinatesY(panel, 540);
		int scaleBarY = MinimapLayout.scaleBarY(panel, 540);
		assertEquals(panel.y() + panel.height() + 1, coordsY);
		assertEquals(coordsY + MinimapLayout.TEXT_ROW, scaleBarY);
		assertEquals(-1, MinimapLayout.coordinatesY(panel, panel.y() + panel.height() + 2));
	}

	@Test
	void theScaleBarUsesRoundBlockCounts() {
		// 160 px showing 256 blocks: 0.625 px/block; the longest round rung
		// that fits inside 152 px is 200 blocks at 125 px — the ladder
		// must never hand back 7 or 13.
		MinimapLayout.ScaleBar bar = MinimapLayout.scaleBar(160, 256);
		assertEquals(200, bar.blocks());
		assertEquals(125, bar.pixels());
	}

	@Test
	void aZoomedOutViewFallsBackToAShortRung() {
		MinimapLayout.ScaleBar bar = MinimapLayout.scaleBar(160, 1024);
		assertTrue(bar.blocks() >= 1);
		assertTrue(bar.pixels() >= 1);
		assertTrue(bar.pixels() <= 160);
	}
}
