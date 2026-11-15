package net.zaminmc.zea.buttons;

import net.zaminmc.zea.persistence.JsonStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Loads and saves the inventory buttons through the safe-write protocol:
 * serialize in memory, verify the round-trip, atomically replace. A
 * corrupt file is preserved as {@code *.corrupted} and defaults are used
 * instead.
 *
 * <p>Per-entry policy: a hand-edited file may contain one damaged
 * button. {@link ButtonEntry} tolerates absent fields (and explicit
 * nulls, which Gson rejects on primitive record components), unusable
 * entries are dropped with a warning, and duplicated identities are
 * re-issued — one bad entry never takes the whole set down. Buttons are
 * inert data here: nothing in this class, or anything it calls, can send
 * a command; execution lives behind the click path only.
 *
 * <p>Schema history: version 1 stored corner anchors with free pixel
 * offsets; version 2 stored a placement region and an order within it;
 * version 3 — the NEU / Inventory-Buttons model — stores free pixel
 * offsets again, now with right/bottom edge anchors and a background
 * style. Migrations run on load and the first save rewrites the file in
 * the current shape: v1 corners translate to their anchors directly
 * (both models are free offsets, so positions survive exactly); v2
 * regions resolve to the exact pixels the region layout computed for
 * those orders, anchored to the edges they hugged, so no existing button
 * moves.
 */
public final class InventoryButtonStore {
	private static final Logger LOGGER = LoggerFactory.getLogger(InventoryButtonStore.class);

	/** Bump when the file shape changes; add the matching migration below. */
	public static final int CURRENT_VERSION = 3;

	private final Path file;
	private final JsonStore json = new JsonStore();

	public InventoryButtonStore(Path file) {
		this.file = file;
	}

	/** The file shape on disk. */
	private record ButtonFile(int version, List<ButtonEntry> buttons) {
		ButtonFile {
			buttons = buttons == null ? List.of() : buttons;
		}
	}

	/**
	 * One entry as it appears in the file, carrying the v3 fields plus
	 * the v2 and v1 leftovers the migrations read. Every field is
	 * optional: absent or null values become the model's defaults, so a
	 * partial entry degrades into an incomplete button (dropped with a
	 * warning if unusable) instead of a parse failure that would
	 * quarantine the whole file.
	 */
	private record ButtonEntry(
			UUID id,
			String name,
			String command,
			String icon,
			String text,
			String tooltip,
			Integer x,
			Integer y,
			Boolean anchorRight,
			Boolean anchorBottom,
			Integer style,
			String region,
			Integer order,
			String anchor,
			Integer offsetX,
			Integer offsetY,
			Boolean closeOnExecute,
			Boolean enabled) {
		InventoryButton toButton() {
			return new InventoryButton(
					id,
					name,
					command,
					icon,
					text,
					tooltip,
					x == null ? 0 : x,
					y == null ? 0 : y,
					anchorRight != null && anchorRight,
					anchorBottom != null && anchorBottom,
					style == null ? 0 : style,
					closeOnExecute != null && closeOnExecute,
					enabled == null || enabled);
		}

		static ButtonEntry from(InventoryButton button) {
			return new ButtonEntry(
					button.id(),
					button.name(),
					button.command(),
					button.icon(),
					button.text(),
					button.tooltip(),
					button.x(),
					button.y(),
					button.anchorRight(),
					button.anchorBottom(),
					button.style(),
					null,
					null,
					null,
					null,
					null,
					button.closeOnExecute(),
					button.enabled());
		}
	}

	/** Loads the button set; missing or corrupt files yield an empty set. */
	public List<InventoryButton> load() {
		ButtonFile data = json.load(file, ButtonFile.class, () -> new ButtonFile(CURRENT_VERSION, List.of()));
		List<InventoryButton> loaded;
		if (data.version() <= 1) {
			LOGGER.info("Migrating inventory buttons from version 1 (corner anchors) to version 3 (free placement)");
			loaded = migrateV1(readEntries());
		} else if (data.version() == 2) {
			LOGGER.info("Migrating inventory buttons from version 2 (regions) to version 3 (free placement)");
			loaded = migrateV2(readEntries());
		} else {
			loaded = readEntries().stream()
					.filter(java.util.Objects::nonNull)
					.map(ButtonEntry::toButton)
					.toList();
		}
		return sanitized(loaded);
	}

	/**
	 * The raw entries in the same file. The JSON layer has already parsed
	 * the envelope; every era's fields parse permissively here (unknown
	 * fields are ignored by Gson), so one reader serves every version.
	 */
	private List<ButtonEntry> readEntries() {
		return json.load(file, ButtonFile.class, () -> new ButtonFile(CURRENT_VERSION, List.of())).buttons();
	}

	/**
	 * The v1 → v3 mapping: a straight translation. Both models store
	 * free offsets; v1's corner anchor becomes the matching edge anchor
	 * pair, and the offsets carry over untouched — every v1 button lands
	 * exactly where it used to sit.
	 */
	private static List<InventoryButton> migrateV1(List<ButtonEntry> legacy) {
		List<InventoryButton> migrated = new ArrayList<>(legacy.size());
		for (ButtonEntry entry : legacy) {
			if (entry == null) {
				continue;
			}
			boolean right = "TOP_RIGHT".equals(entry.anchor()) || "BOTTOM_RIGHT".equals(entry.anchor());
			boolean bottom = "BOTTOM_LEFT".equals(entry.anchor()) || "BOTTOM_RIGHT".equals(entry.anchor());
			migrated.add(new InventoryButton(
					entry.id(),
					entry.name(),
					entry.command(),
					entry.icon(),
					"",
					"",
					entry.offsetX() == null ? InventoryButton.DEFAULT_OFFSET_X_V1 : entry.offsetX(),
					entry.offsetY() == null ? InventoryButton.DEFAULT_OFFSET_Y_V1 : entry.offsetY(),
					right,
					bottom,
					0,
					false,
					entry.enabled() == null || entry.enabled()));
		}
		return migrated;
	}

	/** The v2 step between a region's buttons: 18 pixels plus the 2-pixel gap. */
	private static final int V2_STEP = ButtonGeometry.SIZE + ButtonGeometry.GAP;

	/**
	 * The v2 → v3 mapping: each region's layout formula evaluated for
	 * the button's order, anchored to the edge that region hugged. The
	 * v2 calculator computed (order × 20) steps from a region base, with
	 * the search bar's row between the GUI and the bottom rows; the
	 * bottom region stacked below the under-search row when that row
	 * held buttons. The formulas below reproduce those positions
	 * exactly, so nothing visibly moves.
	 */
	private static List<InventoryButton> migrateV2(List<ButtonEntry> legacy) {
		boolean underSearchOccupied = false;
		for (ButtonEntry entry : legacy) {
			if (entry != null && "UNDER_SEARCH".equals(entry.region())) {
				underSearchOccupied = true;
				break;
			}
		}
		List<InventoryButton> migrated = new ArrayList<>(legacy.size());
		for (ButtonEntry entry : legacy) {
			if (entry == null) {
				continue;
			}
			int order = Math.max(0, entry.order() == null ? 0 : entry.order());
			int x;
			int y;
			boolean right = false;
			boolean bottom = false;
			switch (entry.region() == null ? "RIGHT" : entry.region()) {
				case "TOP" -> {
					x = order * V2_STEP;
					y = -ButtonGeometry.SIZE - ButtonGeometry.GAP;
				}
				case "UNDER_SEARCH" -> {
					x = order * V2_STEP;
					y = ButtonGeometry.GAP + 12 + ButtonGeometry.GAP;
					bottom = true;
				}
				case "BOTTOM" -> {
					x = order * V2_STEP;
					y = ButtonGeometry.GAP + 12 + ButtonGeometry.GAP
							+ (underSearchOccupied ? V2_STEP : 0);
					bottom = true;
				}
				case "LEFT" -> {
					x = -ButtonGeometry.SIZE - ButtonGeometry.GAP;
					y = order * V2_STEP;
				}
				default -> {
					x = ButtonGeometry.GAP;
					y = order * V2_STEP;
					right = true;
				}
			}
			migrated.add(new InventoryButton(
					entry.id(),
					entry.name(),
					entry.command(),
					entry.icon(),
					entry.text() == null ? "" : entry.text(),
					entry.tooltip() == null ? "" : entry.tooltip(),
					x,
					y,
					right,
					bottom,
					0,
					entry.closeOnExecute() != null && entry.closeOnExecute(),
					entry.enabled() == null || entry.enabled()));
		}
		return migrated;
	}

	/** Saves the button set after sanitizing every entry. */
	public void save(List<InventoryButton> buttons) {
		List<InventoryButton> clean = sanitized(buttons == null ? List.of() : buttons);
		List<ButtonEntry> entries = clean.stream().map(ButtonEntry::from).toList();
		try {
			json.save(file, new ButtonFile(CURRENT_VERSION, entries));
		} catch (IOException e) {
			LOGGER.warn("Could not save inventory buttons {}", file, e);
		}
	}

	/**
	 * Normalizes entries, re-issues duplicated identities and drops
	 * null ones with a warning. Blank-command buttons are kept: they
	 * are the preset templates' shape and a half-finished edit that
	 * arrived here by hand-editing — inert data that renders nowhere
	 * and cannot run. The result is safe to hand to the click path.
	 */
	private static List<InventoryButton> sanitized(List<InventoryButton> buttons) {
		List<InventoryButton> clean = new ArrayList<>(buttons.size());
		Set<UUID> seenIds = new HashSet<>();
		for (InventoryButton button : buttons) {
			if (button == null) {
				LOGGER.warn("Dropping a damaged inventory button entry");
				continue;
			}
			InventoryButton normalized = button;
			if (!seenIds.add(normalized.id())) {
				LOGGER.warn("Inventory button '{}' reuses an id; issuing a new one", normalized.name());
				normalized = normalized.withNewId();
				seenIds.add(normalized.id());
			}
			clean.add(normalized);
		}
		return List.copyOf(clean);
	}
}
