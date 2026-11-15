package net.zaminmc.zea.map.source.squaremap;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.zaminmc.zea.map.model.MapWorld;
import net.zaminmc.zea.map.model.TownClaim;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses squaremap's {@code markers.json} into {@link MarkersDocument}.
 *
 * <p>Verified shape (live, 2026-11-15): a top-level <b>array of layers</b>.
 * The {@code towny} layer ("Territory") carries polygon markers whose
 * {@code points} nest three levels — polygons → rings → vertices — with
 * vertices as {@code {"x":…,"z":…}} objects (or {@code [x, z]} arrays);
 * ring 0 of a polygon is the outer boundary, further rings are holes.
 * The {@code chunky} layer ("World Border") carries one polyline. Town
 * name and nation come from the marker's HTML {@code tooltip}
 * ({@code <b>Name</b> (Member of Nation)} or {@@code (Capital of Nation)});
 * stroke color is {@code color}, fill color is {@code fillColor}.
 *
 * <p>Robustness rules inherited from the reference implementation: one
 * malformed marker is skipped, never the document — the towny layer
 * carries ~5,800 towns and an outer catch would discard them all.
 */
public final class MarkersParser {

	private static final Pattern NAME = Pattern.compile("<b>\\s*(.*?)\\s*</b>");
	private static final Pattern NATION = Pattern.compile("\\(\\s*(?:Member|Capital)\\s+of\\s+(.*?)\\s*\\)");
	private static final Pattern ANY_TAG = Pattern.compile("(?s)<[^>]*>");

	private MarkersParser() {
	}

	/** Parses a markers.json body. Returns {@link MarkersDocument#EMPTY} on any structural failure. */
	public static MarkersDocument parse(MapWorld world, String json) {
		if (json == null || json.isBlank()) {
			return MarkersDocument.EMPTY;
		}
		try {
			JsonElement root = JsonParser.parseString(json);
			if (!root.isJsonArray()) {
				return MarkersDocument.EMPTY;
			}
			List<TownClaim> towns = new ArrayList<>();
			List<int[]> worldBorder = List.of();
			int failures = 0;
			for (JsonElement layerElement : root.getAsJsonArray()) {
				if (!layerElement.isJsonObject()) {
					continue;
				}
				JsonObject layer = layerElement.getAsJsonObject();
				JsonArray markers = array(layer, "markers");
				if (markers == null) {
					continue;
				}
				for (JsonElement markerElement : markers) {
					if (!markerElement.isJsonObject()) {
						continue;
					}
					try {
						JsonObject marker = markerElement.getAsJsonObject();
						String type = string(marker, "type");
						if ("polygon".equalsIgnoreCase(type)) {
							TownClaim claim = parseTown(world, marker);
							if (claim != null) {
								towns.add(claim);
							}
						} else if ("polyline".equalsIgnoreCase(type) && worldBorder.isEmpty()) {
							worldBorder = parsePolyline(marker);
						}
					} catch (RuntimeException malformed) {
						failures++;
					}
				}
			}
			if (failures > 0 && towns.isEmpty() && worldBorder.isEmpty()) {
				return MarkersDocument.EMPTY;
			}
			return new MarkersDocument(towns, worldBorder);
		} catch (RuntimeException malformed) {
			return MarkersDocument.EMPTY;
		}
	}

	private static TownClaim parseTown(MapWorld world, JsonObject marker) {
		String tooltip = string(marker, "tooltip");
		String popup = string(marker, "popup");
		String name = firstBoldText(tooltip);
		if (name == null) {
			name = firstBoldText(popup);
		}
		if (name == null || name.isBlank() || "?".equals(name)) {
			return null;
		}
		int outline = parseColor(string(marker, "color"), parseColor(string(marker, "fillColor"), 0x3BFF3B));
		int fill = parseColor(string(marker, "fillColor"), outline);

		List<int[][]> rings = new ArrayList<>();
		JsonArray points = array(marker, "points");
		if (points == null) {
			return null;
		}
		for (JsonElement polygonElement : points) {
			if (!polygonElement.isJsonArray()) {
				continue;
			}
			for (JsonElement ringElement : polygonElement.getAsJsonArray()) {
				if (!ringElement.isJsonArray()) {
					continue;
				}
				int[][] ring = parseRing(ringElement.getAsJsonArray());
				if (ring != null && ring.length >= 3) {
					rings.add(ring);
				}
			}
		}
		if (rings.isEmpty()) {
			return null;
		}
		TownClaim claim = TownClaim.of(world, name, nationOf(tooltip), outline, fill, rings);
		return claim;
	}

	/** The nation name from "(Member of X)" / "(Capital of X)", or null. */
	static String nationOf(String tooltip) {
		if (tooltip == null) {
			return null;
		}
		Matcher matcher = NATION.matcher(tooltip);
		if (!matcher.find()) {
			return null;
		}
		String nation = stripTags(matcher.group(1)).trim();
		return nation.isBlank() ? null : nation;
	}

	static String firstBoldText(String html) {
		if (html == null) {
			return null;
		}
		Matcher matcher = NAME.matcher(html);
		return matcher.find() ? stripTags(matcher.group(1)).trim() : null;
	}

	private static String stripTags(String html) {
		return ANY_TAG.matcher(html).replaceAll("");
	}

	private static List<int[]> parsePolyline(JsonObject marker) {
		JsonArray points = array(marker, "points");
		if (points == null) {
			return List.of();
		}
		List<int[]> out = new ArrayList<>(points.size());
		for (JsonElement point : points) {
			int[] vertex = vertexOf(point);
			if (vertex != null) {
				out.add(vertex);
			}
		}
		return out;
	}

	private static int[][] parseRing(JsonArray ring) {
		List<int[]> points = new ArrayList<>(ring.size());
		for (JsonElement element : ring) {
			int[] vertex = vertexOf(element);
			if (vertex != null) {
				points.add(vertex);
			}
		}
		return points.isEmpty() ? null : points.toArray(new int[0][]);
	}

	private static int[] vertexOf(JsonElement element) {
		if (element.isJsonObject()) {
			JsonObject object = element.getAsJsonObject();
			if (object.has("x") && object.has("z")) {
				return new int[] {object.get("x").getAsInt(), object.get("z").getAsInt()};
			}
			return null;
		}
		if (element.isJsonArray()) {
			JsonArray pair = element.getAsJsonArray();
			if (pair.size() >= 2) {
				return new int[] {pair.get(0).getAsInt(), pair.get(1).getAsInt()};
			}
		}
		return null;
	}

	private static int parseColor(String hex, int fallback) {
		if (hex == null || !hex.startsWith("#") || hex.length() != 7) {
			return fallback;
		}
		try {
			return Integer.parseInt(hex.substring(1), 16) & 0x00FFFFFF;
		} catch (NumberFormatException invalid) {
			return fallback;
		}
	}

	private static String string(JsonObject object, String field) {
		JsonElement value = object.get(field);
		return value != null && value.isJsonPrimitive() ? value.getAsString() : null;
	}

	private static JsonArray array(JsonObject object, String field) {
		JsonElement value = object.get(field);
		return value != null && value.isJsonArray() ? value.getAsJsonArray() : null;
	}
}
