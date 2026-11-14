package net.zaminmc.zea.share;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.zaminmc.zea.config.ZeaConfig;
import net.zaminmc.zea.notify.Notification;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeneralConfigPayloadTest {
	@Test
	void exportsRoundTripThroughImports() throws ShareException {
		ZeaConfig config = new ZeaConfig();
		config.chat.multiSendEnabled = true;
		config.chat.dispatchIntervalMillis = 2500;
		config.chat.chatInterface.smoothChat = false;
		config.chat.chatInterface.smoothChatDurationMillis = 120;
		config.chat.tabs.discord = true;
		config.chat.tabs.deaths = true;
		config.chat.notifications.pmDestination = Notification.Destination.TITLE;
		config.chat.notifications.mentionSound = false;
		config.chat.mentions.autoRespond = true;
		config.chat.mentions.autoResponseText = "hi there";
		config.chat.mentions.autoResponseCooldownSeconds = 45;
		config.inventoryButtons.enabled = false;

		GeneralConfigPayload payload = GeneralConfigPayload.of(config);
		GeneralConfigPayload decoded = GeneralConfigPayload.fromJson(payload.toJson());

		ZeaConfig applied = new ZeaConfig();
		decoded.applyTo(applied);

		assertEquals(true, applied.chat.multiSendEnabled);
		assertEquals(2500, applied.chat.dispatchIntervalMillis);
		assertEquals(false, applied.chat.chatInterface.smoothChat);
		assertEquals(120, applied.chat.chatInterface.smoothChatDurationMillis);
		assertEquals(true, applied.chat.tabs.discord);
		assertEquals(true, applied.chat.tabs.deaths);
		assertEquals(Notification.Destination.TITLE, applied.chat.notifications.pmDestination);
		assertEquals(false, applied.chat.notifications.mentionSound);
		assertEquals(true, applied.chat.mentions.autoRespond);
		assertEquals("hi there", applied.chat.mentions.autoResponseText);
		assertEquals(45, applied.chat.mentions.autoResponseCooldownSeconds);
		assertEquals(false, applied.inventoryButtons.enabled);
	}

	@Test
	void exportsExcludeLocalDataByConstruction() {
		ZeaConfig config = new ZeaConfig();
		config.chat.history.enabled = true;
		config.chat.history.maxEntries = 9000;

		String json = GeneralConfigPayload.of(config).toJson().toString();

		assertFalse(json.contains("history"), "local storage policy must not be shared: " + json);
		assertFalse(json.contains("configVersion"), json);
		assertFalse(json.contains("9000"), json);
	}

	@Test
	void absentFieldsKeepTheCurrentValue() throws ShareException {
		GeneralConfigPayload empty = GeneralConfigPayload.fromJson(new JsonObject());

		ZeaConfig config = new ZeaConfig();
		config.chat.multiSendEnabled = true;
		empty.applyTo(config);

		assertEquals(true, config.chat.multiSendEnabled);
	}

	@Test
	void wrongTypesAreRejectedWithTheFieldName() {
		JsonObject data = JsonParser.parseString(
				"{\"chat\":{\"multiSendEnabled\":\"yes\"}}").getAsJsonObject();

		ShareException e = assertThrows(ShareException.class,
				() -> GeneralConfigPayload.fromJson(data));

		assertTrue(e.getMessage().contains("chat.multiSendEnabled"), e.getMessage());
	}

	@Test
	void nonNumbersAreRejectedWithTheFieldName() {
		JsonObject data = JsonParser.parseString(
				"{\"chat\":{\"dispatchIntervalMillis\":\"soon\"}}").getAsJsonObject();

		ShareException e = assertThrows(ShareException.class,
				() -> GeneralConfigPayload.fromJson(data));

		assertTrue(e.getMessage().contains("chat.dispatchIntervalMillis"), e.getMessage());
	}

	@Test
	void unknownDestinationsAreRejected() {
		JsonObject data = JsonParser.parseString(
				"{\"notifications\":{\"pmDestination\":\"HOLOGRAM\"}}").getAsJsonObject();

		ShareException e = assertThrows(ShareException.class,
				() -> GeneralConfigPayload.fromJson(data));

		assertTrue(e.getMessage().contains("HOLOGRAM"), e.getMessage());
	}

	@Test
	void numbersOutOfRangeAreClampedOnApplyNotRejected() throws ShareException {
		JsonObject data = JsonParser.parseString("""
                                {"chat":{"dispatchIntervalMillis":99999},
                                 "mentions":{"autoResponseCooldownSeconds":1}}
                                """).getAsJsonObject();

		ZeaConfig config = new ZeaConfig();
		GeneralConfigPayload.fromJson(data).applyTo(config);

		assertEquals(ZeaConfig.MAX_DISPATCH_INTERVAL_MILLIS, config.chat.dispatchIntervalMillis);
		assertEquals(ZeaConfig.MIN_AUTO_RESPONSE_COOLDOWN_SECONDS, config.chat.mentions.autoResponseCooldownSeconds);
	}

	@Test
	void autoResponseTextIsCleanedAndCapped() throws ShareException {
		// JSON escapes: \n (newline) and \u0007 (BEL) both decode to
		// control characters, which the import strips.
		JsonObject data = JsonParser.parseString(
				"{\"mentions\":{\"autoResponseText\":\"a\\nb\\u0007 c\"}}").getAsJsonObject();
		ZeaConfig config = new ZeaConfig();
		GeneralConfigPayload.fromJson(data).applyTo(config);

		assertEquals("ab c", config.chat.mentions.autoResponseText);
	}

	@Test
	void overlongAutoResponseTextIsTruncated() throws ShareException {
		JsonObject data = JsonParser.parseString(
				"{\"mentions\":{\"autoResponseText\":\"" + "x".repeat(400) + "\"}}")
				.getAsJsonObject();
		ZeaConfig config = new ZeaConfig();
		GeneralConfigPayload.fromJson(data).applyTo(config);

		assertEquals(GeneralConfigPayload.MAX_AUTO_RESPONSE_LENGTH,
				config.chat.mentions.autoResponseText.length());
	}

	@Test
	void unknownGroupsAndFieldsAreIgnored() throws ShareException {
		JsonObject data = JsonParser.parseString("""
                                {"chat":{"multiSendEnabled":true,"futureField":9},
                                 "futureGroup":{"a":1}}
                                """).getAsJsonObject();

		ZeaConfig config = new ZeaConfig();
		GeneralConfigPayload.fromJson(data).applyTo(config);

		assertEquals(true, config.chat.multiSendEnabled);
	}

	@Test
	void summariesListGroupedSettings() throws ShareException {
		JsonObject data = JsonParser.parseString(
				"{\"chat\":{\"multiSendEnabled\":true},\"tabs\":{\"deaths\":true}}")
				.getAsJsonObject();

		String summary = GeneralConfigPayload.fromJson(data).summary();

		assertTrue(summary.contains("chat"), summary);
		assertTrue(summary.contains("tabs"), summary);
		assertEquals("An empty configuration", GeneralConfigPayload.fromJson(new JsonObject()).summary());
	}

	@Test
	void importedAutoResponsesStayDisabledUnlessShared() throws ShareException {
		// An import never enables the auto-response on its own —
		// only an explicit true in the share does.
		JsonObject data = JsonParser.parseString(
				"{\"mentions\":{\"autoResponseText\":\"hi\"}}").getAsJsonObject();

		ZeaConfig config = new ZeaConfig();
		GeneralConfigPayload.fromJson(data).applyTo(config);

		assertFalse(config.chat.mentions.autoRespond);
	}
}
