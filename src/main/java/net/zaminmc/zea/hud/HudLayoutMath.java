package net.zaminmc.zea.hud;

/**
 * Pure layout math for HUD elements: anchored positions, screen
 * clamping, and the drag-to-anchor conversion. Everything here is a pure
 * function of the live values, so the manager's drag preview and the
 * live HUD resolve identical rectangles, and stored positions behave
 * consistently at every resolution and GUI scale.
 */
public final class HudLayoutMath {
        private HudLayoutMath() {
        }

        /** A resolved on-screen rectangle. */
        public record Rect(int x, int y, int width, int height) {
        }

        /** An element's stored position: anchor plus edge-relative offsets. */
        public record Placement(HudAnchor anchor, int offsetX, int offsetY) {
        }

        /**
         * The on-screen rectangle for a placement at the current screen
         * size. Offsets measure from the anchor's edges inward; a
         * {@code TOP_RIGHT} element with offset (8, 8) sits 8 pixels in
         * from the right and top edges, whatever the window size.
         */
        public static Rect rectFor(HudAnchor anchor, int offsetX, int offsetY,
                        int width, int height, int screenWidth, int screenHeight) {
                int x = anchor.fromRight()
                                ? screenWidth - width - offsetX
                                : offsetX;
                int y = anchor.fromBottom()
                                ? screenHeight - height - offsetY
                                : offsetY;
                return new Rect(x, y, width, height);
        }

        /**
         * Keeps a rectangle inside the usable screen area: an element may
         * sit at the very edge (docked is the normal look) but never off
         * it — at least a grabbable strip always stays reachable.
         */
        public static Rect clamped(Rect rect, int screenWidth, int screenHeight) {
                int width = Math.min(rect.width(), screenWidth);
                int height = Math.min(rect.height(), screenHeight);
                int x = Math.clamp(rect.x(), 0, screenWidth - width);
                int y = Math.clamp(rect.y(), 0, screenHeight - height);
                return new Rect(x, y, width, height);
        }

        /**
         * The placement a dropped rectangle means: the anchor is the corner
         * the rectangle currently sits nearest, and the offsets measure
         * from that corner — the same numbers {@link #rectFor} resolves
         * back to the identical rectangle, so a drag round-trips exactly.
         */
        public static Placement placementFor(Rect rect, int screenWidth, int screenHeight) {
                boolean right = rect.x() + rect.width() / 2 > screenWidth / 2;
                boolean bottom = rect.y() + rect.height() / 2 > screenHeight / 2;
                HudAnchor anchor = right ? (bottom ? HudAnchor.BOTTOM_RIGHT : HudAnchor.TOP_RIGHT)
                                : (bottom ? HudAnchor.BOTTOM_LEFT : HudAnchor.TOP_LEFT);
                int offsetX = anchor.fromRight()
                                ? screenWidth - rect.width() - rect.x()
                                : rect.x();
                int offsetY = anchor.fromBottom()
                                ? screenHeight - rect.height() - rect.y()
                                : rect.y();
                return new Placement(anchor, offsetX, offsetY);
        }
}
