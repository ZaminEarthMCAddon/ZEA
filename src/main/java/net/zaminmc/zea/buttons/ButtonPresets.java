package net.zaminmc.zea.buttons;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The three built-in button presets, offered from the editor's top-left
 * row. Presets are templates, not command packs: like NEU's default
 * set, their buttons carry blank commands — they cost nothing on screen
 * (an unconfigured button renders nowhere) and exist so the editor has
 * a positioned canvas to fill in. No EarthMC commands are invented here.
 *
 * <ul>
 * <li><b>Default</b> — the factory canvas: a modest starter column
 * beside the GUI's right edge.</li>
 * <li><b>Buttons everywhere</b> — the full ring: rows above and below,
 * columns on both sides.</li>
 * <li><b>Empty</b> — no buttons at all.</li>
 * </ul>
 *
 * <p>Applying a preset never destroys anything irrecoverably: the
 * coordinator keeps the replaced set for the editor's one-step restore
 * (see {@link InventoryButtons#applyPreset}).
 */
public final class ButtonPresets {
	private ButtonPresets() {
	}

	/** The icons the template canvas cycles through, chosen to read as "menu buttons". */
	private static final String[] TEMPLATE_ICONS = {
			"minecraft:compass",
			"minecraft:chest",
			"minecraft:book",
			"minecraft:emerald",
			"minecraft:clock",
			"minecraft:map",
			"minecraft:paper",
			"minecraft:gold_ingot",
	};

	/** A named preset and the buttons it installs. */
	public record Preset(String id, String nameKey, List<InventoryButton> buttons) {
	}

	/** The preset ids, in editor order. */
	public static final String DEFAULT = "default";
	public static final String EVERYWHERE = "everywhere";
	public static final String EMPTY = "empty";

	/** All built-in presets, in editor order. */
	public static List<Preset> all() {
		List<Preset> out = new ArrayList<>(3);
		out.add(new Preset(DEFAULT, "zea.buttons.preset.default", canvas(
				List.of(new Count(Anchor.RIGHT, 4)))));
		out.add(new Preset(EVERYWHERE, "zea.buttons.preset.everywhere", canvas(List.of(
				new Count(Anchor.TOP, 8),
				new Count(Anchor.RIGHT, 8),
				new Count(Anchor.BOTTOM, 8),
				new Count(Anchor.LEFT, 8)))));
		out.add(new Preset(EMPTY, "zea.buttons.preset.empty", List.of()));
		return out;
	}

	/** The preset with this id, or {@code null}. */
	public static Preset byId(String id) {
		for (Preset preset : all()) {
			if (preset.id().equals(id)) {
				return preset;
			}
		}
		return null;
	}

	/** Which edge of the canvas a template count occupies. */
	private enum Anchor {
		TOP, BOTTOM, LEFT, RIGHT
	}

	/** One edge's contribution to a template canvas. */
	private record Count(Anchor anchor, int buttons) {
	}

	/** The outside grid step the template ring uses, matching the editor's snap. */
	private static final int RING_STEP = ButtonGeometry.OUTER_GRID;

	/**
	 * Builds a canvas: {@code buttons} fresh entries per listed edge,
	 * evenly cycling the template icons on the 20-pixel ring grid,
	 * anchored to the edges they hug, all enabled with blank commands —
	 * inert until the player fills them in.
	 */
	private static List<InventoryButton> canvas(List<Count> counts) {
		List<InventoryButton> out = new ArrayList<>();
		int icon = 0;
		for (Count count : counts) {
			for (int i = 0; i < count.buttons(); i++) {
				int x = 0;
				int y = 0;
				boolean right = false;
				boolean bottom = false;
				switch (count.anchor()) {
					case TOP -> {
						x = ButtonGeometry.TOP_BOTTOM_START_X + i * RING_STEP;
						y = -ButtonGeometry.OUTER_PADDING - ButtonGeometry.SIZE;
					}
					case BOTTOM -> {
						x = ButtonGeometry.TOP_BOTTOM_START_X + i * RING_STEP;
						y = ButtonGeometry.CANVAS_HEIGHT + ButtonGeometry.OUTER_PADDING;
						bottom = true;
					}
					case LEFT -> {
						x = -ButtonGeometry.OUTER_PADDING - ButtonGeometry.SIZE;
						y = ButtonGeometry.OUTER_PADDING + i * RING_STEP;
					}
					case RIGHT -> {
						x = ButtonGeometry.OUTER_PADDING;
						y = ButtonGeometry.OUTER_PADDING + i * RING_STEP;
						right = true;
					}
				}
				out.add(new InventoryButton(
						UUID.randomUUID(),
						"Button " + (out.size() + 1),
						"",
						TEMPLATE_ICONS[icon++ % TEMPLATE_ICONS.length],
						"",
						"",
						x,
						y,
						right,
						bottom,
						0,
						false,
						true));
			}
		}
		return out;
	}
}
