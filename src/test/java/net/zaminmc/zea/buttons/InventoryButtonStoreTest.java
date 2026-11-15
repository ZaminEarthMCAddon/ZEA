package net.zaminmc.zea.buttons;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InventoryButtonStoreTest {
	@TempDir
	Path tempDir;

	private Path file() {
		return tempDir.resolve("buttons.json");
	}

	private InventoryButtonStore newStore() {
		return new InventoryButtonStore(file());
	}

	private static InventoryButton usable(String name) {
		return InventoryButton.newButton(1).withName(name).withCommand("town spawn");
	}

	private void write(String json) throws IOException {
		Files.write(file(), json.getBytes(UTF_8));
	}

	@Test
	void missingFileYieldsNoButtons() {
		assertTrue(newStore().load().isEmpty());
	}

	@Test
	void roundTripsButtons() {
		InventoryButtonStore store = newStore();
		List<InventoryButton> buttons = List.of(
				usable("Home").withIcon("minecraft:compass"),
				usable("Trade").withPosition(40, 100, false, true).withEnabled(false),
				usable("Hub").withText("Hub").withCloseOnExecute(true).withStyle(3));

		store.save(buttons);
		List<InventoryButton> loaded = store.load();

		assertEquals(3, loaded.size());
		InventoryButton home = loaded.stream().filter(b -> b.name().equals("Home")).findFirst().orElseThrow();
		InventoryButton trade = loaded.stream().filter(b -> b.name().equals("Trade")).findFirst().orElseThrow();
		InventoryButton hub = loaded.stream().filter(b -> b.name().equals("Hub")).findFirst().orElseThrow();
		assertEquals("minecraft:compass", home.icon());
		assertTrue(home.anchorRight(), "the new-button default placement survives");
		assertEquals(40, trade.x());
		assertEquals(100, trade.y());
		assertTrue(trade.anchorBottom());
		assertFalse(trade.enabled());
		assertEquals("Hub", hub.text());
		assertTrue(hub.closeOnExecute());
		assertEquals(3, hub.style());
		// And a second round trip is the identity.
		store.save(loaded);
		assertEquals(loaded, store.load());
	}

	@Test
	void versionThreeFilesLoadDirectly() throws IOException {
		write("""
				{"version":3,"buttons":[
				  {"id":"11111111-1111-1111-1111-111111111111","name":"Home","command":"home",
				   "icon":"minecraft:compass","x":2,"y":4,"anchorRight":true,"anchorBottom":false,
				   "style":2,"closeOnExecute":false,"enabled":true}
				]}
				""");

		List<InventoryButton> loaded = newStore().load();

		assertEquals(1, loaded.size());
		assertEquals("Home", loaded.get(0).name());
		assertEquals(2, loaded.get(0).x());
		assertTrue(loaded.get(0).anchorRight());
		assertEquals(2, loaded.get(0).style());
	}

	@Test
	void partialEntriesDegradeInsteadOfFailing() throws IOException {
		write("""
				{"version":3,"buttons":[
				  {"name":"Home","command":"home"},
				  null,
				  {"name":"Broken","command":"","icon":""}
				]}
				""");

		List<InventoryButton> loaded = newStore().load();

		// A null entry is dropped (it carries nothing to keep); a damaged
		// but present entry degrades into inert data instead of failing
		// the whole file.
		assertEquals(2, loaded.size());
		assertEquals("Home", loaded.get(0).name());
		assertFalse(loaded.get(1).isUsable());
	}

	@Test
	void duplicateIdentitiesAreReissued() throws IOException {
		write("""
				{"version":3,"buttons":[
				  {"id":"11111111-1111-1111-1111-111111111111","name":"A","command":"a","icon":"minecraft:stone"},
				  {"id":"11111111-1111-1111-1111-111111111111","name":"B","command":"b","icon":"minecraft:stone"}
				]}
				""");

		List<InventoryButton> loaded = newStore().load();

		assertEquals(2, loaded.size());
		long distinct = loaded.stream().map(InventoryButton::id).distinct().count();
		assertEquals(2, distinct);
	}

	@Test
	void versionOneCornerAnchorsTranslateExactly() throws IOException {
		write("""
				{"version":1,"buttons":[
				  {"id":"11111111-1111-1111-1111-111111111111","name":"TL","command":"a","icon":"minecraft:stone",
				   "anchor":"TOP_LEFT","offsetX":10,"offsetY":-20},
				  {"name":"BR","command":"b","icon":"minecraft:stone",
				   "anchor":"BOTTOM_RIGHT","offsetX":30,"offsetY":40}
				]}
				""");

		List<InventoryButton> loaded = newStore().load();

		assertEquals(2, loaded.size());
		InventoryButton tl = loaded.get(0);
		InventoryButton br = loaded.get(1);
		assertEquals(10, tl.x());
		assertEquals(-20, tl.y());
		assertFalse(tl.anchorRight());
		assertFalse(tl.anchorBottom());
		assertEquals(30, br.x());
		assertEquals(40, br.y());
		assertTrue(br.anchorRight());
		assertTrue(br.anchorBottom());
	}

	@Test
	void versionOneDefaultsFillMissingOffsets() throws IOException {
		write("""
				{"version":0,"buttons":[
				  {"name":"Old","command":"old","icon":"minecraft:stone"}
				]}
				""");

		List<InventoryButton> loaded = newStore().load();

		assertEquals(1, loaded.size());
		assertEquals(InventoryButton.DEFAULT_OFFSET_X_V1, loaded.get(0).x());
		assertEquals(InventoryButton.DEFAULT_OFFSET_Y_V1, loaded.get(0).y());
	}

	@Test
	void versionTwoRegionsResolveToTheirExactPixels() throws IOException {
		write("""
				{"version":2,"buttons":[
				  {"id":"11111111-1111-1111-1111-111111111111","name":"Top0","command":"a","icon":"minecraft:stone",
				   "region":"TOP","order":0},
				  {"name":"Right3","command":"b","icon":"minecraft:stone",
				   "region":"RIGHT","order":3},
				  {"name":"Left1","command":"c","icon":"minecraft:stone",
				   "region":"LEFT","order":1}
				]}
				""");

		List<InventoryButton> loaded = newStore().load();

		assertEquals(3, loaded.size());
		InventoryButton top = loaded.get(0);
		InventoryButton right = loaded.get(1);
		InventoryButton left = loaded.get(2);
		// Top row: order 0 at x = 0, y = -18 - 2, unanchored.
		assertEquals(0, top.x());
		assertEquals(-ButtonGeometry.SIZE - ButtonGeometry.GAP, top.y());
		assertFalse(top.anchorRight());
		// Right column: order 3 at x = +2 (anchored right), y = 3 * 20.
		assertEquals(ButtonGeometry.GAP, right.x());
		assertEquals(3 * 20, right.y());
		assertTrue(right.anchorRight());
		// Left column: order 1 at x = -18 - 2, y = 1 * 20.
		assertEquals(-ButtonGeometry.SIZE - ButtonGeometry.GAP, left.x());
		assertEquals(20, left.y());
	}

	@Test
	void versionTwoBottomRowsStackUnderTheSearchBar() throws IOException {
		write("""
				{"version":2,"buttons":[
				  {"name":"Under","command":"a","icon":"minecraft:stone","region":"UNDER_SEARCH","order":0},
				  {"name":"Bottom","command":"b","icon":"minecraft:stone","region":"BOTTOM","order":2}
				]}
				""");

		List<InventoryButton> loaded = newStore().load();

		InventoryButton under = loaded.get(0);
		InventoryButton bottom = loaded.get(1);
		// Under-search: below the GUI plus bar row, anchored bottom.
		assertEquals(0, under.x());
		assertEquals(ButtonGeometry.GAP + 12 + ButtonGeometry.GAP, under.y());
		assertTrue(under.anchorBottom());
		// Bottom: one more button row below the occupied under-search row.
		assertEquals(2 * 20, bottom.x());
		assertEquals(ButtonGeometry.GAP + 12 + ButtonGeometry.GAP + 20, bottom.y());
		assertTrue(bottom.anchorBottom());
	}

	@Test
	void savingRewritesTheFileInTheCurrentShape() throws IOException {
		InventoryButtonStore store = newStore();
		store.save(List.of(usable("Home")));

		String json = Files.readString(file());
		assertTrue(json.contains("\"version\": 3"));
		assertTrue(json.contains("\"anchorRight\""));
	}
}
