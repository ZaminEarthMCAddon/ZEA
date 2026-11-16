package net.zaminmc.zea.hud;

/**
 * The screen corner a HUD element measures its offset from. Anchored
 * positioning is what makes element positions survive resolution and GUI
 * scale changes: the stored numbers are distances from one edge, so a
 * minimap docked near the top-right stays near the top-right whether the
 * window is 854 or 2560 pixels wide.
 */
public enum HudAnchor {
        TOP_LEFT,
        TOP_RIGHT,
        BOTTOM_LEFT,
        BOTTOM_RIGHT;

        /** The horizontal anchor side: true when the element measures from the right edge. */
        public boolean fromRight() {
                return this == TOP_RIGHT || this == BOTTOM_RIGHT;
        }

        /** The vertical anchor side: true when the element measures from the bottom edge. */
        public boolean fromBottom() {
                return this == BOTTOM_LEFT || this == BOTTOM_RIGHT;
        }
}
