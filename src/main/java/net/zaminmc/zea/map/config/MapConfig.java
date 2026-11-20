package net.zaminmc.zea.map.config;

import io.github.notenoughupdates.moulconfig.annotations.Category;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorDropdown;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorSlider;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorText;
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption;
import io.github.notenoughupdates.moulconfig.annotations.ConfigOrder;

/**
 * The Map category: everything about the native world map and minimap.
 *
 * <p>Layout follows the house pattern (see Chat): the gates and data
 * settings sit directly on the Map category, with Minimap, World map and
 * Shared display as subcategories. The shared group exists because
 * duplicating an identical setting per surface would guarantee drift —
 * a shared group is for the settings where independent control makes no
 * sense.
 *
 * <p>Not exposed here: the minimap's on-screen position and the map
 * editor. Positions are pixel data the drag editor owns; making them
 * manually typed numbers invites off-screen maps. The editor button
 * arrives with the editor screen itself.
 *
 * <p>Option philosophy (NEU's lesson): semantic choices, not rendering
 * knobs — "square or round", "rotate with me", "how big" — never
 * framebuffers or texture internals.
 */
public final class MapConfig {

	@ConfigOption(name = "Enable map", desc = "Master switch for the whole map subsystem: "
			+ "world map, minimap and all background data.")
	@ConfigEditorBoolean
	@ConfigOrder(0)
	public boolean enabled = true;

	@ConfigOption(name = "Enable world map", desc = "The full-screen map, opened with /zeamap "
			+ "or its keybind.")
	@ConfigEditorBoolean
	@ConfigOrder(1)
	public boolean worldMapEnabled = true;

	@ConfigOption(name = "Enable minimap", desc = "The HUD minimap showing the world you are in.")
	@ConfigEditorBoolean
	@ConfigOrder(2)
	public boolean minimapEnabled = true;

	@ConfigOption(name = "Only on EarthMC", desc = "Run the map system only on EarthMC "
			+ "(earthmc.net and its subdomains). Off allows testing elsewhere; the data "
			+ "will not match another server's world.")
	@ConfigEditorBoolean
	@ConfigOrder(3)
	public boolean earthMcOnly = true;

	@ConfigOption(name = "Default world", desc = "Which world the full map opens on. Auto "
			+ "follows the dimension you are standing in.")
	@ConfigEditorDropdown
	@ConfigOrder(4)
	public WorldMode defaultWorld = WorldMode.AUTO;

	@ConfigOption(name = "API key", desc = "Optional EarthMC API key, needed for the live "
			+ "player feed once the server enforces keys. Sent only to api.earthmc.net. "
			+ "Get one with /api in-game.")
	@ConfigEditorText
	@ConfigOrder(5)
	public String apiKey = "";

	@ConfigOption(name = "Show data freshness", desc = "A subtle line showing how old the "
			+ "claims and player data are, and when a refresh is failing.")
	@ConfigEditorBoolean
	@ConfigOrder(6)
	public boolean showFreshness = true;

	@ConfigOption(name = "Hide minimap when unsupported", desc = "In dimensions without a map "
			+ "(the Nether), hide the minimap entirely instead of showing a disabled panel. "
			+ "Cave and Nether mapping is not permitted on EarthMC.")
	@ConfigEditorBoolean
	@ConfigOrder(7)
	public boolean hideInUnsupportedDimensions = false;

	@ConfigOption(name = "Community map layer", desc = "Download the community-discovered "
			+ "map tiles published through ZEA-REPO. Terrain imagery only; claims and "
			+ "players always come live from EarthMC.")
	@ConfigEditorBoolean
	@ConfigOrder(8)
	public boolean repoLayerEnabled = true;

	@Category(name = "Minimap", desc = "The HUD minimap: shape, size, rotation and what it shows.")
	@ConfigOrder(9)
	public Minimap minimap = new Minimap();

	@Category(name = "World map", desc = "The full-screen map (/zeamap): zoom, camera and layers.")
	@ConfigOrder(10)
	public WorldMap worldMap = new WorldMap();

	@Category(name = "Shared display", desc = "Marker and claim appearance used by both maps.")
	@ConfigOrder(11)
	public SharedDisplay sharedDisplay = new SharedDisplay();

	/** Which world a map surface shows. */
	public enum WorldMode {
		AUTO("Auto (follow me)"),
		EARTH("Terra Nostra"),
		MOON("Moon");

		private final String label;

		WorldMode(String label) {
			this.label = label;
		}

		@Override
		public String toString() {
			return label;
		}
	}

	/** Minimap silhouette. */
	public enum MinimapShape {
		SQUARE("Square"),
		CIRCLE("Circle");

		private final String label;

		MinimapShape(String label) {
			this.label = label;
		}

		@Override
		public String toString() {
			return label;
		}
	}

	/** How players appear on the maps. */
	public enum PlayerIconStyle {
		ARROW("Marker"),
		HEAD("Head"),
		HEAD_BORDER("Head with border");

		private final String label;

		PlayerIconStyle(String label) {
			this.label = label;
		}

		@Override
		public String toString() {
			return label;
		}
	}

	/** HUD minimap. */
	public static final class Minimap {
		public static final int MIN_SIZE = 64;
		public static final int MAX_SIZE = 320;
		public static final int DEFAULT_SIZE = 160;

		@ConfigOption(name = "Shape", desc = "Square or circular minimap.")
		@ConfigEditorDropdown
		@ConfigOrder(0)
		public MinimapShape shape = MinimapShape.SQUARE;

		@ConfigOption(name = "Size", desc = "Edge length (or diameter) in GUI pixels.")
		@ConfigEditorSlider(minValue = MIN_SIZE, maxValue = MAX_SIZE, minStep = 8)
		@ConfigOrder(1)
		public int size = DEFAULT_SIZE;

		@ConfigOption(name = "Zoom", desc = "How many blocks fit across the minimap. Lower is "
				+ "more zoomed in.")
		@ConfigEditorSlider(minValue = 64, maxValue = 1024, minStep = 32)
		@ConfigOrder(2)
		public int blocksAcross = 256;

		@ConfigOption(name = "Rotate with player", desc = "North-up by default; on, the map "
				+ "turns to face your direction and icons stay upright.")
		@ConfigEditorBoolean
		@ConfigOrder(3)
		public boolean rotateWithPlayer = false;

		@ConfigOption(name = "Center on player", desc = "Keep your marker centered. Off, the "
				+ "minimap pans with a fixed offset.")
		@ConfigEditorBoolean
		@ConfigOrder(4)
		public boolean centerPlayer = true;

		@ConfigOption(name = "Opacity", desc = "Background opacity of the minimap, in percent.")
		@ConfigEditorSlider(minValue = 0, maxValue = 100, minStep = 5)
		@ConfigOrder(5)
		public int opacity = 100;

		@ConfigOption(name = "Smooth players", desc = "Interpolate other players' markers "
				+ "between position updates so they glide instead of jumping.")
		@ConfigEditorBoolean
		@ConfigOrder(6)
		public boolean interpolatePlayers = true;

		@ConfigOption(name = "Show town claims", desc = "Claim fills and borders on the minimap.")
		@ConfigEditorBoolean
		@ConfigOrder(7)
		public boolean showClaims = true;

		@ConfigOption(name = "Show town names", desc = "Names of nearby towns on the minimap.")
		@ConfigEditorBoolean
		@ConfigOrder(8)
		public boolean showTownNames = true;

		@ConfigOption(name = "Show online players", desc = "Other players' markers.")
		@ConfigEditorBoolean
		@ConfigOrder(9)
		public boolean showPlayers = true;

		@ConfigOption(name = "Show player names", desc = "Names next to nearby player markers.")
		@ConfigEditorBoolean
		@ConfigOrder(10)
		public boolean showPlayerNames = false;

		@ConfigOption(name = "Show coordinates", desc = "Your coordinates under the minimap.")
		@ConfigEditorBoolean
		@ConfigOrder(11)
		public boolean showCoordinates = true;

		@ConfigOption(name = "Show scale indicator", desc = "A short scale bar under the minimap.")
		@ConfigEditorBoolean
		@ConfigOrder(12)
		public boolean showScaleBar = true;
	}

	/** Full-screen map. */
	public static final class WorldMap {
		public static final int MIN_ZOOM = 64;
		public static final int MAX_ZOOM = 2048;

		@ConfigOption(name = "Default zoom", desc = "How many blocks fit across the screen when "
				+ "the map opens. Lower is more zoomed in.")
		@ConfigEditorSlider(minValue = MIN_ZOOM, maxValue = MAX_ZOOM, minStep = 64)
		@ConfigOrder(0)
		public int defaultBlocksAcross = 1024;

		@ConfigOption(name = "Show player markers", desc = "Other online players on the map.")
		@ConfigEditorBoolean
		@ConfigOrder(2)
		public boolean showPlayers = true;

		@ConfigOption(name = "Show player names", desc = "Names next to player markers, reduced "
				+ "automatically when many are visible.")
		@ConfigEditorBoolean
		@ConfigOrder(3)
		public boolean showPlayerLabels = true;

		@ConfigOption(name = "Show town fills", desc = "The colored area inside claims.")
		@ConfigEditorBoolean
		@ConfigOrder(4)
		public boolean showTownFills = true;

		@ConfigOption(name = "Show town outlines", desc = "Claim boundaries.")
		@ConfigEditorBoolean
		@ConfigOrder(5)
		public boolean showTownOutlines = true;

		@ConfigOption(name = "Show town labels", desc = "Town names at their claim center.")
		@ConfigEditorBoolean
		@ConfigOrder(6)
		public boolean showTownLabels = true;

		@ConfigOption(name = "Nation coloring", desc = "Color claims by nation. Off, claims use "
				+ "a neutral tone.")
		@ConfigEditorBoolean
		@ConfigOrder(7)
		public boolean nationColors = true;
	}

	/** Appearance shared by both surfaces. */
	public static final class SharedDisplay {
		@ConfigOption(name = "Player icon style", desc = "How players are drawn: the classic "
				+ "marker, their head, or a head with a border.")
		@ConfigEditorDropdown
		@ConfigOrder(0)
		public PlayerIconStyle playerIconStyle = PlayerIconStyle.ARROW;

		@ConfigOption(name = "Player icon scale", desc = "Icon size multiplier.")
		@ConfigEditorSlider(minValue = 50, maxValue = 300, minStep = 10)
		@ConfigOrder(1)
		public int playerIconScalePercent = 100;

		@ConfigOption(name = "Claim fill opacity", desc = "How solid the inside of a claim is, "
				+ "in percent.")
		@ConfigEditorSlider(minValue = 0, maxValue = 100, minStep = 5)
		@ConfigOrder(2)
		public int claimFillOpacityPercent = 15;

		@ConfigOption(name = "Claim outline opacity", desc = "How solid claim borders are, in "
				+ "percent.")
		@ConfigEditorSlider(minValue = 0, maxValue = 100, minStep = 5)
		@ConfigOrder(3)
		public int claimOutlineOpacityPercent = 85;

		@ConfigOption(name = "Label scale", desc = "Town and player name size multiplier.")
		@ConfigEditorSlider(minValue = 50, maxValue = 200, minStep = 10)
		@ConfigOrder(4)
		public int labelScalePercent = 100;

		@ConfigOption(name = "Map imagery", desc = "Background terrain imagery: the EarthMC map "
				+ "and the community layer. Off leaves a flat background with claims only.")
		@ConfigEditorBoolean
		@ConfigOrder(5)
		public boolean imageryEnabled = true;

		@ConfigOption(name = "Darken imagery", desc = "Darken background imagery so claims and "
				+ "markers stand out, in percent.")
		@ConfigEditorSlider(minValue = 0, maxValue = 75, minStep = 5)
		@ConfigOrder(6)
		public int imageryDarkenPercent = 0;
	}
	/**
	 * On-screen position state lives with the map subsystem, not here:
	 * it is pixel data the drag editor owns, and every config field must
	 * be a user-facing option.
	 */
}
