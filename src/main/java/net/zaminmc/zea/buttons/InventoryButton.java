package net.zaminmc.zea.buttons;

import java.util.UUID;

/**
 * One configurable inventory button: a small control beside the container
 * GUI that runs a chat command when clicked.
 *
 * <p>The record tolerates partial data (null or malformed fields become
 * safe defaults in the compact constructor) because it is deserialized
 * straight from a user-editable file — a single damaged entry must never
 * crash loading. Whether a button is complete enough to keep is decided
 * by {@link #isUsable()}, applied at the store boundary.
 *
 * <p>Placement is (region, order): the region picks the row/column, the
 * order picks the slot within it. There are deliberately no pixel
 * offsets — the layout calculator in {@link ButtonGeometry} computes
 * every rectangle, so buttons cannot overlap each other, the GUI, or
 * vanilla furniture.
 *
 * @param id             stable identity, used by the editor and share imports
 * @param name           tooltip title; blank normalizes to {@link #DEFAULT_NAME}
 * @param command        chat command without the leading slash
 * @param icon           item id of the icon, e.g. {@code minecraft:compass}
 * @param text           optional display text drawn instead of the icon
 * @param tooltip        optional description shown under the name
 * @param region         the placement region
 * @param order          position within the region, 0-based
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
                ButtonRegion region,
                int order,
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

        /** The v1 default offset: beside the container's right edge. Kept for the store migration. */
        public static final int DEFAULT_OFFSET_X_V1 = 22;
        public static final int DEFAULT_OFFSET_Y_V1 = 2;

        public InventoryButton {
                id = id == null ? UUID.randomUUID() : id;
                name = cleaned(name, MAX_NAME_LENGTH, DEFAULT_NAME);
                command = withoutLeadingSlash(cleaned(command, MAX_COMMAND_LENGTH, ""));
                icon = cleaned(icon, MAX_ICON_LENGTH, "");
                text = cleaned(text, MAX_TEXT_LENGTH, "");
                tooltip = cleaned(tooltip, MAX_TOOLTIP_LENGTH, "");
                region = region == null ? ButtonRegion.RIGHT : region;
                order = Math.clamp(order, 0, region.capacity() - 1);
                enabled = enabled;
        }

        /**
         * Creates a starting point for the editor: usable icon and region,
         * empty command so it cannot be saved or run until filled in.
         */
        public static InventoryButton newButton(int index) {
                return new InventoryButton(
                                UUID.randomUUID(),
                                DEFAULT_NAME + " " + index,
                                "",
                                "minecraft:compass",
                                "",
                                "",
                                ButtonRegion.RIGHT,
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
                return new InventoryButton(id, name, command, icon, text, tooltip, region, order, closeOnExecute, enabled);
        }

        /** A copy with this name. */
        public InventoryButton withName(String name) {
                return new InventoryButton(id, name, command, icon, text, tooltip, region, order, closeOnExecute, enabled);
        }

        /** A copy with this icon. */
        public InventoryButton withIcon(String icon) {
                return new InventoryButton(id, name, command, icon, text, tooltip, region, order, closeOnExecute, enabled);
        }

        /** A copy with this display text. */
        public InventoryButton withText(String text) {
                return new InventoryButton(id, name, command, icon, text, tooltip, region, order, closeOnExecute, enabled);
        }

        /** A copy with this description. */
        public InventoryButton withTooltip(String tooltip) {
                return new InventoryButton(id, name, command, icon, text, tooltip, region, order, closeOnExecute, enabled);
        }

        /** A copy in this region, keeping the order if the region allows it. */
        public InventoryButton withRegion(ButtonRegion region) {
                return new InventoryButton(id, name, command, icon, text, tooltip, region, order, closeOnExecute, enabled);
        }

        /** A copy with this order. */
        public InventoryButton withOrder(int order) {
                return new InventoryButton(id, name, command, icon, text, tooltip, region, order, closeOnExecute, enabled);
        }

        /** A copy with this close-on-execute flag. */
        public InventoryButton withCloseOnExecute(boolean closeOnExecute) {
                return new InventoryButton(id, name, command, icon, text, tooltip, region, order, closeOnExecute, enabled);
        }

        /** A copy with this enabled flag. */
        public InventoryButton withEnabled(boolean enabled) {
                return new InventoryButton(id, name, command, icon, text, tooltip, region, order, closeOnExecute, enabled);
        }

        /** A copy of this button with a fresh identity. */
        public InventoryButton withNewId() {
                return new InventoryButton(UUID.randomUUID(), name, command, icon, text, tooltip, region, order,
                                closeOnExecute, enabled);
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
