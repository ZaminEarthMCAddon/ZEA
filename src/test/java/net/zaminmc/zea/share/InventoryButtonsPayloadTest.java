package net.zaminmc.zea.share;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.zaminmc.zea.buttons.ButtonRegion;
import net.zaminmc.zea.buttons.InventoryButton;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InventoryButtonsPayloadTest {
	private static InventoryButton button(String name, String command) {
		return new InventoryButton(UUID.randomUUID(), name, command, "minecraft:compass",
				"", "", ButtonRegion.RIGHT, 0, false, true);
	}

	@Test
	void exportsRoundTripThroughImports() throws ShareException {
		InventoryButtonsPayload payload = InventoryButtonsPayload.of(List.of(
				button("Home", "spawn"),
				button("Shop", "shop")), null);

		InventoryButtonsPayload decoded = InventoryButtonsPayload.fromJson(payload.toJson());

		assertEquals(2, decoded.buttons().size());
		assertEquals("Home", decoded.buttons().get(0).name());
		assertEquals("spawn", decoded.buttons().get(0).command());
		assertEquals("Shop", decoded.buttons().get(1).name());
		assertEquals(ButtonRegion.RIGHT, decoded.buttons().get(0).region());
		assertEquals(0, decoded.buttons().get(0).order());
	}

	@Test
	void exportsSkipUnusableButtons() {
		int[] skipped = new int[1];
		InventoryButton unusable = button("Draft", "");

		InventoryButtonsPayload payload = InventoryButtonsPayload.of(
				List.of(button("Home", "spawn"), unusable), skipped);

		assertEquals(1, skipped[0]);
		assertEquals(1, payload.buttons().size());
	}

	@Test
	void importsRejectEntriesWithoutACommand() {
		JsonObject data = JsonParser.parseString("""
                                {"buttons":[{"id":"00000000-0000-0000-0000-000000000001",
                                  "name":"Broken","command":"","icon":"minecraft:compass"}]}
                                """).getAsJsonObject();

		ShareException e = assertThrows(ShareException.class,
				() -> InventoryButtonsPayload.fromJson(data));

		assertTrue(e.getMessage().contains("Broken"), e.getMessage());
	}

	@Test
	void importsRejectEntriesWithoutAnIcon() {
		JsonObject data = JsonParser.parseString("""
                                {"buttons":[{"name":"NoIcon","command":"spawn"}]}
                                """).getAsJsonObject();

		assertThrows(ShareException.class, () -> InventoryButtonsPayload.fromJson(data));
	}

	@Test
	void importsRejectDuplicatedIdentities() {
		JsonObject data = JsonParser.parseString("""
                                {"buttons":[
                                  {"id":"00000000-0000-0000-0000-000000000001","name":"A","command":"a","icon":"minecraft:compass"},
                                  {"id":"00000000-0000-0000-0000-000000000001","name":"B","command":"b","icon":"minecraft:compass"}]}
                                """).getAsJsonObject();

		ShareException e = assertThrows(ShareException.class,
				() -> InventoryButtonsPayload.fromJson(data));

		assertTrue(e.getMessage().contains("one identity"), e.getMessage());
	}

	@Test
	void importsRejectOversizedSets() {
		StringBuilder json = new StringBuilder("{\"buttons\":[");
		for (int i = 0; i < InventoryButtonsPayload.MAX_BUTTONS + 1; i++) {
			if (i > 0) {
				json.append(',');
			}
			json.append("{\"name\":\"B").append(i).append("\",\"command\":\"c\",\"icon\":\"i\"}");
		}
		json.append("]}");
		JsonObject data = JsonParser.parseString(json.toString()).getAsJsonObject();

		ShareException e = assertThrows(ShareException.class,
				() -> InventoryButtonsPayload.fromJson(data));

		assertTrue(e.getMessage().contains("at most"), e.getMessage());
	}

	@Test
	void missingButtonsMeansAnEmptySet() throws ShareException {
		assertEquals(0, InventoryButtonsPayload.fromJson(new JsonObject()).buttons().size());
		assertEquals(0, InventoryButtonsPayload.fromJson(JsonParser.parseString(
				"{\"buttons\":null}").getAsJsonObject()).buttons().size());
	}

	@Test
	void nonListButtonsIsRejected() {
		JsonObject data = JsonParser.parseString("{\"buttons\":7}").getAsJsonObject();

		assertThrows(ShareException.class, () -> InventoryButtonsPayload.fromJson(data));
	}

	@Test
	void damagedEntriesRejectTheImport() {
		JsonObject data = JsonParser.parseString("{\"buttons\":[null]}").getAsJsonObject();

		assertThrows(ShareException.class, () -> InventoryButtonsPayload.fromJson(data));
	}

	@Test
	void partialEntriesGetTheModelDefaults() throws ShareException {
		JsonObject data = JsonParser.parseString("""
                                {"buttons":[{"name":"Minimal","command":"spawn","icon":"minecraft:compass"}]}
                                """).getAsJsonObject();

		List<InventoryButton> buttons = InventoryButtonsPayload.fromJson(data).buttons();

		assertEquals(1, buttons.size());
		InventoryButton button = buttons.get(0);
		assertEquals(ButtonRegion.RIGHT, button.region());
		assertEquals(0, button.order());
		assertFalse(button.closeOnExecute());
		assertTrue(button.enabled());
	}

	@Test
	void unknownFieldsAreIgnoredOnImport() throws ShareException {
		JsonObject data = JsonParser.parseString("""
                                {"buttons":[{"name":"Future","command":"spawn","icon":"minecraft:compass",
                                  "somethingNew":true}],"extra":1}
                                """).getAsJsonObject();

		assertEquals(1, InventoryButtonsPayload.fromJson(data).buttons().size());
	}

	@Test
	void summariesNameTheFirstButtonsAndCountTheRest() throws ShareException {
		List<InventoryButton> many = new java.util.ArrayList<>();
		for (int i = 0; i < 7; i++) {
			many.add(button("B" + i, "c" + i));
		}

		String summary = InventoryButtonsPayload.of(many, null).summary();

		assertTrue(summary.startsWith("7 buttons:"), summary);
		assertTrue(summary.contains("and 2 more"), summary);
		assertEquals("No buttons", InventoryButtonsPayload.of(List.of(), null).summary());
	}

	@Test
	void exportsNeverCarryAForwardSlashCommand() {
		InventoryButton withSlash = new InventoryButton(UUID.randomUUID(), "Slash", "/spawn",
				"minecraft:compass", "", "", ButtonRegion.LEFT, 0, false, true);

		List<InventoryButton> exported = InventoryButtonsPayload.of(List.of(withSlash), null).buttons();

		assertEquals("spawn", exported.get(0).command());
	}
}
