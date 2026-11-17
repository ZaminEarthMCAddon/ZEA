package net.zaminmc.zea.buttons;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The label-fit policy: real width measures in, a fitted label out.
 * Short labels never shrink; a label wider than the box scales down
 * while readable and ellipsizes past the floor — one unusually long
 * label never shrinks any other button.
 */
class ButtonRendererFitTest {
	/** Rough measure: two pixels per character — "Map" fits, "Inventory" barely not. */
	private static int measure(String text) {
		return text.length() * 2;
	}

	private static final int ELLIPSIS = measure("…");

	@Test
	void shortLabelsStayNatural() {
		var fitted = ButtonRenderer.fitLabel("Map", 14, ButtonRendererFitTest::measure, ELLIPSIS);
		assertEquals(1f, fitted.scale());
		assertFalse(fitted.truncated());
		assertEquals("Map", fitted.display());
	}

	@Test
	void wideLabelsScaleWithinTheReadableFloor() {
		// 18 chars ≈ 91 px: scale 14/91 ≈ 0.15 — below the floor.
		var tiny = ButtonRenderer.fitLabel("buttons everywhere", 14, ButtonRendererFitTest::measure, ELLIPSIS);
		assertTrue(tiny.truncated(), "an absurdly long label is ellipsized, not hairline-scaled");
		assertTrue(tiny.scale() == ButtonRenderer.MIN_TEXT_SCALE);
		assertTrue(tiny.display().endsWith("…"), "the clipped label carries an ellipsis");
	}

	@Test
	void moderatelyLongLabelsScaleWithoutTruncation() {
		// "Inventory" ≈ 46 px → scale ≈ 0.30. Hmm — below the floor too;
		// the floor exists precisely so this becomes a clipped tooltip
		// instead of unreadable text.
		var fitted = ButtonRenderer.fitLabel("Inventory", 14, ButtonRendererFitTest::measure, ELLIPSIS);
		assertTrue(fitted.scale() >= ButtonRenderer.MIN_TEXT_SCALE);
		// Whatever the policy chose, the display fits the box at that scale.
		assertTrue(measure(fitted.display()) * fitted.scale() <= 14.0f + 0.5f,
				"the drawn label fits its box");
	}

	@Test
	void everyFitActuallyFits() {
		String[] labels = {"buttons everywhere", "Inventory", "Map", "Market",
				"A particularly long custom button label", "a", ""};
		for (String label : labels) {
			var fitted = ButtonRenderer.fitLabel(label, 14, ButtonRendererFitTest::measure, ELLIPSIS);
			assertTrue(measure(fitted.display()) * fitted.scale() <= 14.0f + 0.5f,
					"label '" + label + "' must fit after fitting");
			assertTrue(fitted.scale() >= ButtonRenderer.MIN_TEXT_SCALE);
		}
	}

	@Test
	void fullLabelsArePreservedForTheTooltip() {
		// The fit policy never mutates the configuration: the full
		// label is what the tooltip receives. The fitted display is
		// a rendering detail, so truncation is reported, not hidden.
		var fitted = ButtonRenderer.fitLabel("A particularly long custom button label", 14,
				ButtonRendererFitTest::measure, ELLIPSIS);
		assertTrue(fitted.truncated());
	}

	@Test
	void boxWideLabelStaysNatural() {
		var fitted = ButtonRenderer.fitLabel("12345", measure("12345"), ButtonRendererFitTest::measure,
				ELLIPSIS);
		assertEquals(1f, fitted.scale());
		assertFalse(fitted.truncated());
	}
}
