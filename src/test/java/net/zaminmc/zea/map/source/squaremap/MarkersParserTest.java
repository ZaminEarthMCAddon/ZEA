package net.zaminmc.zea.map.source.squaremap;

import net.zaminmc.zea.map.model.MapWorld;
import net.zaminmc.zea.map.model.TownClaim;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarkersParserTest {

	/**
	 * The fixture is a trimmed copy of the live markers.json captured
	 * 2026-11-15: four real towns (including one multi-ring town with a
	 * hole and one nationless town), an icon marker that must be skipped,
	 * and the verbatim "chunky" World Border layer.
	 */
	private static String fixture() {
		try (InputStream in = MarkersParserTest.class.getResourceAsStream("/map/markers-fixture.json")) {
			assertNotNull(in, "fixture must be on the test classpath");
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new AssertionError(e);
		}
	}

	@Test
	void parsesTheLiveDocumentShape() {
		MarkersDocument document = MarkersParser.parse(MapWorld.EARTH, fixture());
		assertEquals(4, document.towns().size(), "icons are not towns");
	}

	@Test
	void townNameAndNationComeFromTheTooltip() {
		MarkersDocument document = MarkersParser.parse(MapWorld.EARTH, fixture());
		Optional<TownClaim> etna = document.towns().stream()
				.filter(t -> t.name().equals("Etna")).findFirst();
		assertTrue(etna.isPresent());
		assertEquals("Italy", etna.get().nation());
		assertEquals("etna", etna.get().key());
	}

	@Test
	void nationlessTownHasNullNation() {
		MarkersDocument document = MarkersParser.parse(MapWorld.EARTH, fixture());
		Optional<TownClaim> baldy = document.towns().stream()
				.filter(t -> t.name().equals("Mount_Baldy")).findFirst();
		assertTrue(baldy.isPresent());
		assertNull(baldy.get().nation());
	}

	@Test
	void holedTownKeepsEveryRing() {
		MarkersDocument document = MarkersParser.parse(MapWorld.EARTH, fixture());
		Optional<TownClaim> middelburg = document.towns().stream()
				.filter(t -> t.name().equals("Middelburg")).findFirst();
		assertTrue(middelburg.isPresent());
		TownClaim claim = middelburg.get();
		assertTrue(claim.rings().size() >= 2,
				"the fixture town has a hole; both rings must survive");
		for (int[][] ring : claim.rings()) {
			assertTrue(ring.length >= 3);
			for (int[] point : ring) {
				assertTrue(point[0] >= claim.minX() && point[0] <= claim.maxX());
				assertTrue(point[1] >= claim.minZ() && point[1] <= claim.maxZ());
			}
		}
	}

	@Test
	void colorsComeFromStrokeAndFill() {
		MarkersDocument document = MarkersParser.parse(MapWorld.EARTH, fixture());
		TownClaim etna = document.towns().stream()
				.filter(t -> t.name().equals("Etna")).findFirst().orElseThrow();
		// From the captured marker: color #aa0000, fillColor #ffffff.
		assertEquals(0xAA0000, etna.outlineRgb());
		assertEquals(0xFFFFFF, etna.fillRgb());
	}

	@Test
	void worldBorderPolylineIsKept() {
		MarkersDocument document = MarkersParser.parse(MapWorld.EARTH, fixture());
		List<int[]> border = document.worldBorder();
		assertEquals(5, border.size());
		assertEquals(-64512, border.get(1)[0]);
		assertEquals(-32256, border.get(0)[1]);
		assertEquals(64511, border.get(4)[0]);
	}

	@Test
	void malformedMarkerIsSkippedNotFatal() {
		String base = fixture();
		// Inject a polygon marker with no points right before the icon
		// marker; the parser must skip it and keep the rest.
		String broken = base.replaceFirst("(\\{\"type\":\"icon\")",
				"{\"type\":\"polygon\",\"tooltip\":\"<b>Broken</b> (Member of Nowhere)\"},$1");
		assertTrue(!broken.equals(base), "fixture must contain the injection anchor");
		MarkersDocument document = MarkersParser.parse(MapWorld.EARTH, broken);
		assertEquals(4, document.towns().size(), "the broken polygon adds nothing; the rest survive");
	}

	@Test
	void garbageInputYieldsEmpty() {
		assertEquals(MarkersDocument.EMPTY, MarkersParser.parse(MapWorld.EARTH, null));
		assertEquals(MarkersDocument.EMPTY, MarkersParser.parse(MapWorld.EARTH, ""));
		assertEquals(MarkersDocument.EMPTY, MarkersParser.parse(MapWorld.EARTH, "not json"));
		assertEquals(MarkersDocument.EMPTY, MarkersParser.parse(MapWorld.EARTH, "[]"));
		assertEquals(MarkersDocument.EMPTY, MarkersParser.parse(MapWorld.EARTH, "{\"object\":true}"));
	}

	@Test
	void parserNationExtraction() {
		assertNull(MarkersParser.nationOf(null));
		assertNull(MarkersParser.nationOf("<div><b>Town</b></div>"));
		assertEquals("Italy", MarkersParser.nationOf("<b>Etna</b> (Member of Italy)"));
		assertEquals("Bigland", MarkersParser.nationOf("<b>Cap</b> (Capital of Bigland)"));
	}
}
