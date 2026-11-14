package net.zaminmc.zea.buttons;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The three built-in button presets, loaded from the {@code /zeabuttons}
 * editor. Presets are templates, not command packs: like NEU's default
 * set, their buttons carry blank commands — they cost nothing on screen
 * (an unconfigured button renders nowhere) and exist so the editor has a
 * positioned canvas to fill in. No EarthMC commands are invented here.
 *
 * <ul>
 * <li><b>Default</b> — the factory canvas: a modest starter row in the
 * top, bottom and right regions.
 * <li><b>Buttons everywhere</b> — every region at full capacity.
 * <li><b>Empty</b> — no buttons at all.
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
                                List.of(new Count(ButtonRegion.TOP, 4),
                                                new Count(ButtonRegion.BOTTOM, 4),
                                                new Count(ButtonRegion.RIGHT, 4)))));
                out.add(new Preset(EVERYWHERE, "zea.buttons.preset.everywhere", canvas(
                                List.of(new Count(ButtonRegion.TOP, ButtonRegion.TOP.capacity()),
                                                new Count(ButtonRegion.BOTTOM, ButtonRegion.BOTTOM.capacity()),
                                                new Count(ButtonRegion.UNDER_SEARCH, ButtonRegion.UNDER_SEARCH.capacity()),
                                                new Count(ButtonRegion.LEFT, ButtonRegion.LEFT.capacity()),
                                                new Count(ButtonRegion.RIGHT, ButtonRegion.RIGHT.capacity())))));
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

        /** One region's contribution to a template canvas. */
        private record Count(ButtonRegion region, int buttons) {
        }

        /**
         * Builds a canvas: {@code buttons} fresh entries per listed region,
         * evenly cycling the template icons, all enabled with blank
         * commands — inert until the player fills them in.
         */
        private static List<InventoryButton> canvas(List<Count> counts) {
                List<InventoryButton> out = new ArrayList<>();
                int icon = 0;
                for (Count count : counts) {
                        for (int i = 0; i < count.buttons(); i++) {
                                out.add(new InventoryButton(
                                                UUID.randomUUID(),
                                                "Button " + (out.size() + 1),
                                                "",
                                                TEMPLATE_ICONS[icon++ % TEMPLATE_ICONS.length],
                                                "",
                                                "",
                                                count.region(),
                                                i,
                                                false,
                                                true));
                        }
                }
                return out;
        }
}
