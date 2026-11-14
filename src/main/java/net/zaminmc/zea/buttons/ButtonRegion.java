package net.zaminmc.zea.buttons;

/**
 * The placement region a button belongs to. Regions — not free pixel
 * offsets — are the unit of placement: the layout calculator owns where
 * each region's row or column sits, and a button only chooses a region
 * and its order within it. This is the structural answer to NEU's
 * historical overlap bugs (buttons drifting onto accessory icons, menu
 * titles or each other): a button cannot be positioned anywhere the
 * layout does not explicitly allow.
 *
 * <p>Capacity per region is bounded so a full row always fits beside the
 * standard container width; the layout still clips anything the screen
 * cannot accommodate (small windows, huge GUI scales).
 */
public enum ButtonRegion {
        /** A horizontal row above the container GUI. */
        TOP(8),
        /** A horizontal row below the container GUI (below the search row when present). */
        BOTTOM(8),
        /** The row of up to five buttons beneath the inventory search bar. */
        UNDER_SEARCH(5),
        /** A vertical column left of the container GUI; hidden while the recipe book is open. */
        LEFT(8),
        /** A vertical column right of the container GUI. */
        RIGHT(8);

        /** How many buttons the region holds at most. */
        private final int capacity;

        ButtonRegion(int capacity) {
                this.capacity = capacity;
        }

        /** The maximum number of buttons placed in this region. */
        public int capacity() {
                return capacity;
        }

        /** Whether this region is rendered as a horizontal row. */
        public boolean isHorizontal() {
                return this == TOP || this == BOTTOM || this == UNDER_SEARCH;
        }

        /**
         * The region a legacy corner anchor maps to in the v2 store
         * migration. Corner anchors carried no order, so migrated order
         * comes from the old horizontal offset.
         */
        public static ButtonRegion fromLegacyAnchor(ButtonAnchor anchor) {
                return switch (anchor) {
                        case TOP_LEFT, TOP_RIGHT -> TOP;
                        case BOTTOM_LEFT, BOTTOM_RIGHT -> BOTTOM;
                };
        }
}
