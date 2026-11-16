package net.zaminmc.zea.hud;

import com.google.gson.annotations.SerializedName;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The persisted HUD settings file ({@code hud.json}): one state entry per
 * element plus a schema version for future migrations. Entries for
 * elements that are not currently registered are preserved verbatim, so
 * a new element's settings survive a downgrade-and-upgrade cycle, and a
 * missing entry is filled from its spec's defaults, so adding elements
 * never needs a migration.
 */
public final class HudSettingsDocument {
        public static final int CURRENT_VERSION = 1;

        @SerializedName("schemaVersion")
        public int schemaVersion = CURRENT_VERSION;

        @SerializedName("elements")
        public Map<String, HudElementState> elements = new LinkedHashMap<>();

        /**
         * The persisted states merged with the registration list: every
         * registered element has an entry (defaults fill the gaps), and
         * entries for unregistered ids carry over untouched.
         */
        public static HudSettingsDocument merge(List<HudElementHandle> handles, HudSettingsDocument previous) {
                HudSettingsDocument document = new HudSettingsDocument();
                Map<String, HudElementState> carried = previous == null || previous.elements == null
                                ? Map.of()
                                : previous.elements;
                for (HudElementHandle handle : handles) {
                        String id = handle.spec().id().toString();
                        HudElementState state = carried.get(id);
                        if (state == null) {
                                state = HudElementState.ofDefaults(handle.spec());
                        }
                        document.elements.put(id, HudElementState.copyOf(state));
                }
                for (Map.Entry<String, HudElementState> entry : carried.entrySet()) {
                        document.elements.putIfAbsent(entry.getKey(), HudElementState.copyOf(entry.getValue()));
                }
                return document;
        }

        /** Whether any entry differs from the given document — the save scheduler's dirty check. */
        public boolean differsFrom(HudSettingsDocument other) {
                if (other == null || other.elements == null || other.elements.size() != elements.size()) {
                        return true;
                }
                for (Map.Entry<String, HudElementState> entry : elements.entrySet()) {
                        HudElementState mine = entry.getValue();
                        HudElementState theirs = other.elements.get(entry.getKey());
                        if (theirs == null
                                        || mine.visible() != theirs.visible()
                                        || mine.anchor() != theirs.anchor()
                                        || mine.offsetX() != theirs.offsetX()
                                        || mine.offsetY() != theirs.offsetY()
                                        || mine.scalePercent() != theirs.scalePercent()) {
                                return true;
                        }
                }
                return false;
        }

        /** A defensive copy for the dirty checks. */
        public HudSettingsDocument copy() {
                HudSettingsDocument out = new HudSettingsDocument();
                out.schemaVersion = schemaVersion;
                out.elements = new LinkedHashMap<>(elements);
                return out;
        }

        /** Unmodifiable view of the entries, in file order. */
        public List<Map.Entry<String, HudElementState>> entries() {
                return new ArrayList<>(elements.entrySet());
        }

        /** The state of one element id, or null. */
        public HudElementState state(Identifier id) {
                return elements.get(id.toString());
        }
}
