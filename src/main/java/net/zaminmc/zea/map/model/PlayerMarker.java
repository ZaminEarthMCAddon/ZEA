package net.zaminmc.zea.map.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * One online player as published by EarthMC's player-position feed
 * ({@code https://api.earthmc.net/map/v1/players}).
 *
 * <p>The feed is the only position source: the v4 API has no player
 * coordinates. Visibility is enforced upstream — underground, sneaking,
 * invisibility-potion players and API opt-outs never appear in the feed,
 * and hidden positions are never inferred.
 *
 * <p>Shape (verified live 2026-11-15):
 * {@code {"world":"minecraft_overworld","name":"…","x":8136,"y":64,
 * "z":-8876,"uuid":"…","yaw":-90,"premium":true}} — the {@code world}
 * field arrived with this endpoint and makes cross-world isolation exact.
 * A marker with a blank world (the legacy squaremap shape) is treated as
 * belonging to whatever world is asked for, matching the reference
 * implementation's compatibility rule.
 */
public record PlayerMarker(String name, String uuid, double x, double z, float yaw, String world) {

	public PlayerMarker {
		Objects.requireNonNull(name, "name");
	}

	/** Lower-cased player name — the stable comparison key. */
	public String key() {
		return name.toLowerCase(Locale.ROOT);
	}

	/**
	 * True if this marker belongs on a map showing {@code world}. A blank
	 * world field matches anything (legacy feed shape); a present field
	 * must match exactly.
	 */
	public boolean inWorld(String world) {
		return this.world == null || this.world.isBlank() || this.world.equals(world);
	}

	/** True if this marker belongs on a map showing {@code world}. */
	public boolean inWorld(MapWorld world) {
		return inWorld(world == null ? null : world.squaremapKey);
	}

	/**
	 * Parses the feed body's {@code players} array. Malformed entries are
	 * skipped rather than failing the whole feed — one bad record must not
	 * blank every marker.
	 */
	public static List<PlayerMarker> parseAll(JsonElement feed) {
		if (feed == null || !feed.isJsonObject()) {
			return List.of();
		}
		JsonElement players = feed.getAsJsonObject().get("players");
		if (players == null || !players.isJsonArray()) {
			return List.of();
		}
		List<PlayerMarker> out = new ArrayList<>(players.getAsJsonArray().size());
		for (JsonElement element : players.getAsJsonArray()) {
			if (!element.isJsonObject()) {
				continue;
			}
			JsonObject object = element.getAsJsonObject();
			String name = string(object, "name");
			if (name == null || name.isBlank()) {
				continue;
			}
			JsonArray position = object.has("position") && object.get("position").isJsonArray()
					? object.get("position").getAsJsonArray()
					: null;
			double x = position != null && position.size() >= 2 ? position.get(0).getAsDouble()
					: number(object, "x");
			double z = position != null && position.size() >= 2 ? position.get(1).getAsDouble()
					: number(object, "z");
			out.add(new PlayerMarker(name, string(object, "uuid"), x, z,
					(float) number(object, "yaw"), string(object, "world")));
		}
		return out;
	}

	private static String string(JsonObject object, String field) {
		JsonElement value = object.get(field);
		return value != null && value.isJsonPrimitive() ? value.getAsString() : null;
	}

	private static double number(JsonObject object, String field) {
		JsonElement value = object.get(field);
		return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()
				? value.getAsDouble()
				: 0.0;
	}
}
