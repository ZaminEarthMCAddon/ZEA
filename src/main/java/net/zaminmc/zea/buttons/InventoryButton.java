package net.zaminmc.zea.buttons;

import java.util.UUID;

/**
 * One configurable inventory button: a free-placed control beside or over
 * the container GUI that runs a chat command when clicked — the NEU /
 * Inventory-Buttons placement model, where the player drags buttons
 * wherever they want them.
 *
 * <p>Placement is a stored offset plus two edge anchors: {@code x} and
 * {@code y} are pixels relative to the container rectangle's top-left
 * corner, and the anchors flip the reference edge — an anchored-right
 * button measures from the container's right edge, an anchored-bottom one
 * from its bottom edge — so a button docked beside the GUI stays beside
 * it on every container size. Positions are edited on the standard
 * inventory canvas (176×166, see {@code ButtonEditorScreen}) and apply to
 * every container screen; {@link ButtonGeometry} decides per screen
 * whether a position renders there at all.
 *
 * <p>The record tolerates partial data (null or malformed fields become
 * safe defaults in the compact constructor) because it is deserialized
 * straight from a user-editable file — a single damaged entry must never
 * crash loading. Whether a button is complete enough to keep is decided
 * by {@link #isUsable()}, applied at the store boundary.
 *
 * @param id             stable identity, used by the editor and share imports
 * @param name           tooltip title; blank normalizes to {@link #DEFAULT_NAME}
 * @param command        chat command without the leading slash
 * @param icon           item id of the icon, or a ZEA custom texture id
 * @param text           optional display text drawn instead of the icon
 * @param tooltip        optional description shown under the name
 * @param zone           the placement zone's name ({@code ButtonGeometry.Zone});
 *                       unknown names render nowhere
 * @param order          the button's position within its zone, 0-based
 * @param style          background texture variant, 0..{@link #STYLE_COUNT}-1
 * @param closeOnExecute close the inventory after the command dispatches
 * @param enabled        disabled buttons are kept but not rendered or clickable
 */
public record InventoryButton(
                UUID id,
                String name,
                String command,
                String icon,
                String text,
                String tooltip,
                String zone,
                int order,
                int style,
                boolean closeOnExecute,
                boolean enabled) {
        /** Fallback title when a name is missing or blank. */
        public static final String DEFAULT_NAME = "Button";

        /** Longest command the chat packet accepts. */
        public static final int MAX_COMMAND_LENGTH = 256;

        /** Longest name kept; longer names are truncated, not rejected. */
        public static final int MAX_NAME_LENGTH = 64;

        /** Longest item id kept. */
        public static final int MAX_ICON_LENGTH = 128;

        /** Longest display text kept (a button is 18 pixels wide; text is scaled to fit). */
        public static final int MAX_TEXT_LENGTH = 8;

        /** Longest tooltip description kept. */
        public static final int MAX_TOOLTIP_LENGTH = 128;

        /** How many background texture variants the button sheet carries. */
        public static final int STYLE_COUNT = 5;

        /** The default zone: beside the GUI, right column. Kept for the store migration. */
        public static final String DEFAULT_ZONE = "RIGHT";

        /** The v1 default offsets. Kept for the store migration. */
        public static final int DEFAULT_OFFSET_X_V1 = 22;
        public static final int DEFAULT_OFFSET_Y_V1 = 2;

        public InventoryButton {
                id = id == null ? UUID.randomUUID() : id;
                name = cleaned(name, MAX_NAME_LENGTH, DEFAULT_NAME);
                command = withoutLeadingSlash(cleaned(command, MAX_COMMAND_LENGTH, ""));
                icon = cleaned(icon, MAX_ICON_LENGTH, "");
                text = cleaned(text, MAX_TEXT_LENGTH, "");
                tooltip = cleaned(tooltip, MAX_TOOLTIP_LENGTH, "");
                zone = zone == null || zone.isBlank() ? DEFAULT_ZONE : zone.strip();
                order = Math.clamp(order, 0, 64);
                style = Math.floorMod(style, STYLE_COUNT);
        }

        /**
         * Creates a starting point for the editor: a recognizable icon beside
         * the GUI's right edge, empty command so it cannot be saved or run
         * until filled in.
         */
        /**
         * Creates a starting point for the editor: a recognizable icon parked in
         * the zone slot the coordinator picked (see {@code firstFreeOrder}),
         * empty command so it cannot be saved or run until filled in.
         */
        public static InventoryButton newButton(int index, String zone, int order) {
                return new InventoryButton(
                                UUID.randomUUID(),
                                DEFAULT_NAME + " " + index,
                                "",
                                "minecraft:compass",
                                "",
                                "",
                                zone == null ? DEFAULT_ZONE : zone,
                                order,
                                0,
                                false,
                                true);
        }

        /** Whether this button has the data needed to render and run. */
        public boolean isUsable() {
                return validCommand(command) && (validIcon(icon) || !text.isBlank());
        }

        /** A copy with this command. */
        public InventoryButton withCommand(String command) {
                return new InventoryButton(id, name, command, icon, text, tooltip, zone, order, style,
                                closeOnExecute, enabled);
        }

        /** A copy with this name. */
        public InventoryButton withName(String name) {
                return new InventoryButton(id, name, command, icon, text, tooltip, zone, order, style,
                                closeOnExecute, enabled);
        }

        /** A copy with this icon. */
        public InventoryButton withIcon(String icon) {
                return new InventoryButton(id, name, command, icon, text, tooltip, zone, order, style,
                                closeOnExecute, enabled);
        }

        /** A copy with this display text. */
        public InventoryButton withText(String text) {
                return new InventoryButton(id, name, command, icon, text, tooltip, zone, order, style,
                                closeOnExecute, enabled);
        }

        /** A copy with this description. */
        public InventoryButton withTooltip(String tooltip) {
                return new InventoryButton(id, name, command, icon, text, tooltip, zone, order, style,
                                closeOnExecute, enabled);
        }

        /** A copy in this zone slot. */
        public InventoryButton withZone(String zone, int order) {
                return new InventoryButton(id, name, command, icon, text, tooltip, zone, order, style,
                                closeOnExecute, enabled);
        }

        /** A copy with this background style. */
        public InventoryButton withStyle(int style) {
                return new InventoryButton(id, name, command, icon, text, tooltip, zone, order, style,
                                closeOnExecute, enabled);
        }

        /** A copy with this close-on-execute flag. */
        public InventoryButton withCloseOnExecute(boolean closeOnExecute) {
                return new InventoryButton(id, name, command, icon, text, tooltip, zone, order, style,
                                closeOnExecute, enabled);
        }

        /** A copy with this enabled flag. */
        public InventoryButton withEnabled(boolean enabled) {
                return new InventoryButton(id, name, command, icon, text, tooltip, zone, order, style,
                                closeOnExecute, enabled);
        }

        /** A copy of this button with a fresh identity. */
        public InventoryButton withNewId() {
                return new InventoryButton(UUID.randomUUID(), name, command, icon, text, tooltip, zone, order,
                                style, closeOnExecute, enabled);
        }

        /**
         * Whether a command can be sent: non-blank, within the packet limit
         * and free of control characters (newlines could smuggle extra lines
         * into one packet).
         */
        public static boolean validCommand(String command) {
                if (command == null || command.isBlank()) {
                        return false;
                }
                if (command.length() > MAX_COMMAND_LENGTH) {
                        return false;
                }
                return command.chars().allMatch(c -> !Character.isISOControl(c));
        }

        /** Whether an icon id is well-formed (existing items are checked at render). */
        public static boolean validIcon(String icon) {
                return icon != null && !icon.isBlank() && icon.length() <= MAX_ICON_LENGTH
                                && !icon.chars().anyMatch(Character::isISOControl);
        }

        /**
         * Trims, strips control characters and truncates. Blank input becomes
         * {@code fallback}; a value that is entirely control characters falls
         * back too.
         */
        private static String cleaned(String value, int maxLength, String fallback) {
                if (value == null) {
                        return fallback;
                }
                StringBuilder cleaned = new StringBuilder(value.length());
                value.chars().filter(c -> !Character.isISOControl(c)).limit(maxLength).forEach(cleaned::appendCodePoint);
                String trimmed = cleaned.toString().strip();
                return trimmed.isEmpty() ? fallback : trimmed;
        }

        private static String withoutLeadingSlash(String command) {
                return command.startsWith("/") ? command.substring(1) : command;
        }
}
