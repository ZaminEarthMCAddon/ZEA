package net.zaminmc.zea.buttons;

import net.zaminmc.zea.persistence.JsonStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
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
 * offsets; version 2 stores a placement region and an order within it.
 * A v1 file migrates on load — the four corners collapse into the top
 * and bottom regions, order derived from the old horizontal offset —
 * and the first save rewrites the file in v2 shape.
 */
public final class InventoryButtonStore {
        private static final Logger LOGGER = LoggerFactory.getLogger(InventoryButtonStore.class);

        /** Bump when the file shape changes; add the matching migration below. */
        public static final int CURRENT_VERSION = 2;

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
         * One v2 entry as it appears in the file. Every field is optional:
         * absent or null values become the model's defaults, so a partial
         * entry degrades into an incomplete button (dropped with a warning
         * if unusable) instead of a parse failure that would quarantine the
         * whole file.
         */
        private record ButtonEntry(
                        UUID id,
                        String name,
                        String command,
                        String icon,
                        String text,
                        String tooltip,
                        ButtonRegion region,
                        Integer order,
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
                                        region,
                                        order == null ? 0 : order,
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
                                        button.region(),
                                        button.order(),
                                        button.closeOnExecute(),
                                        button.enabled());
                }
        }

        /**
         * One version 1 entry, kept only for the migration: the corner
         * anchor and free offsets the v1 file stored.
         */
        private record LegacyEntry(
                        UUID id,
                        String name,
                        String command,
                        String icon,
                        ButtonAnchor anchor,
                        Integer offsetX,
                        Integer offsetY,
                        Boolean enabled) {
        }

        /** Loads the button set; missing or corrupt files yield an empty set. */
        public List<InventoryButton> load() {
                ButtonFile data = json.load(file, ButtonFile.class, () -> new ButtonFile(CURRENT_VERSION, List.of()));
                List<InventoryButton> loaded;
                if (data.version() < 1) {
                        LOGGER.warn("Buttons file carries no version; treating it as version 1");
                        loaded = migrateV1(readLegacy());
                } else if (data.version() == 1) {
                        LOGGER.info("Migrating inventory buttons from version 1 (corner anchors) to version 2 (regions)");
                        loaded = migrateV1(readLegacy());
                } else {
                        loaded = data.buttons().stream()
                                        .filter(java.util.Objects::nonNull)
                                        .map(ButtonEntry::toButton)
                                        .toList();
                }
                return sanitized(loaded);
        }

        /**
         * The v1 entries in the same file. The store's JSON layer has
         * already parsed the envelope; v1 bodies parse permissively here
         * (every field optional, unknown v2 fields ignored by Gson).
         */
        private List<LegacyEntry> readLegacy() {
            return json.load(file, LegacyFile.class, () -> new LegacyFile(null)).buttons();
        }

        private record LegacyFile(List<LegacyEntry> buttons) {
                LegacyFile {
                        buttons = buttons == null ? List.of() : buttons;
                }
        }

        /**
         * The v1 → v2 mapping: top corners become the top region, bottom
         * corners the bottom region, ordered by horizontal offset so a
         * left-to-right row stays left-to-right. Buttons that lived beside
         * the GUI (offsets that escaped the container rectangle) move to
         * the right region — closest to where they used to sit. Their exact
         * old pixels are not representable in the region model; keeping
         * them visible in a sane place beats keeping the file untouched.
         */
        private static List<InventoryButton> migrateV1(List<LegacyEntry> legacy) {
                List<InventoryButton> migrated = new ArrayList<>(legacy.size());
                for (LegacyEntry entry : legacy) {
                        if (entry == null) {
                                continue;
                        }
                        ButtonAnchor anchor = entry.anchor() == null ? ButtonAnchor.TOP_LEFT : entry.anchor();
                        int offsetX = entry.offsetX() == null ? InventoryButton.DEFAULT_OFFSET_X_V1 : entry.offsetX();
                        int offsetY = entry.offsetY() == null ? InventoryButton.DEFAULT_OFFSET_Y_V1 : entry.offsetY();
                        ButtonRegion region = regionForLegacy(anchor, offsetX, offsetY);
                        int order = orderForLegacy(region, anchor, offsetX, offsetY);
                        migrated.add(new InventoryButton(
                                        entry.id(),
                                        entry.name(),
                                        entry.command(),
                                        entry.icon(),
                                        "",
                                        "",
                                        region,
                                        order,
                                        false,
                                        entry.enabled() == null || entry.enabled()));
                }
                // Order fields derived from offsets can repeat or exceed capacity;
                // the layout tolerates that, but tidy numbers survive round-trips.
                return repack(migrated);
        }

        /** The standard container texture width, the migration's escape threshold. */
        private static final int LEGACY_GUI_WIDTH = 176;

        /**
         * The v1 region mapping: a button whose rectangle escapes the GUI's
         * right edge (the v1 default position sat beside it) becomes a
         * right-column button; anything else keeps its corner's half — top
         * corners to the top row, bottom corners to the bottom row. Their
         * exact old pixels are not representable in the region model;
         * keeping them visible in a sane place beats keeping the file
         * untouched.
         */
        private static ButtonRegion regionForLegacy(ButtonAnchor anchor, int offsetX, int offsetY) {
                boolean rightOfGui = switch (anchor) {
                        case TOP_RIGHT, BOTTOM_RIGHT -> offsetX >= ButtonGeometry.SIZE;
                        case TOP_LEFT, BOTTOM_LEFT -> offsetX > LEGACY_GUI_WIDTH - ButtonGeometry.SIZE;
                };
                if (rightOfGui) {
                        return ButtonRegion.RIGHT;
                }
                return ButtonRegion.fromLegacyAnchor(anchor);
        }

        /** The v1 index within the region: horizontal for rows, vertical for columns. */
        private static int orderForLegacy(ButtonRegion region, ButtonAnchor anchor, int offsetX, int offsetY) {
                int coordinate = switch (region) {
                        case TOP, BOTTOM -> anchor == ButtonAnchor.TOP_RIGHT || anchor == ButtonAnchor.BOTTOM_RIGHT
                                        ? 154 + offsetX
                                        : offsetX;
                        default -> offsetY;
                };
                if (coordinate < 0) {
                        return 0;
                }
                return coordinate / (ButtonGeometry.SIZE + ButtonGeometry.GAP);
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
                return List.copyOf(repack(clean));
        }

        /**
         * Renumbers each region's buttons 0..n-1 in (order, id) sequence so
         * stored orders stay dense and comparable across edits.
         */
        public static List<InventoryButton> repack(List<InventoryButton> buttons) {
                List<InventoryButton> sorted = new ArrayList<>(buttons);
                sorted.sort(Comparator
                                .comparing(InventoryButton::region)
                                .thenComparingInt(InventoryButton::order)
                                .thenComparing(button -> button.id().toString()));
                List<InventoryButton> out = new ArrayList<>(sorted.size());
                int[] nextOrder = new int[ButtonRegion.values().length];
                for (InventoryButton button : sorted) {
                        out.add(button.withOrder(nextOrder[button.region().ordinal()]++));
                }
                return out;
        }
}
