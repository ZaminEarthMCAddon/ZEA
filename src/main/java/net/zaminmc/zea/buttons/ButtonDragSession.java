package net.zaminmc.zea.buttons;

/**
 * One in-progress button drag in edit mode: the button is grabbed at a
 * point and follows the cursor at a preview position. Releasing converts
 * the preview position into a region move (see
 * {@link ButtonGeometry#dropTarget}) handled by the coordinator; this
 * class only tracks the grab and the preview.
 *
 * <p>A press that never moves beyond {@link #JITTER_PIXELS} is not a
 * drag — releasing it leaves the button untouched.
 */
public final class ButtonDragSession {
        /** Wiggles up to this many pixels do not count as movement. */
        private static final int JITTER_PIXELS = 3;

        private InventoryButton button;
        private int grabX;
        private int grabY;
        private int startMouseX;
        private int startMouseY;
        private int previewX;
        private int previewY;
        private boolean moved;

        /** Whether a drag is in progress. */
        public boolean isActive() {
                return button != null;
        }

        /** The button being dragged. */
        public InventoryButton button() {
                return button;
        }

        /** The dragged button's current left edge. */
        public int previewX() {
                return previewX;
        }

        /** The dragged button's current top edge. */
        public int previewY() {
                return previewY;
        }

        /** Whether the pointer has moved beyond the jitter threshold. */
        public boolean moved() {
                return moved;
        }

        /** Grabs the button; the cursor keeps its position inside it. */
        public void begin(InventoryButton button, int buttonX, int buttonY, double mx, double my) {
                if (button == null) {
                        throw new NullPointerException("button must not be null");
                }
                this.button = button;
                this.grabX = (int) Math.floor(mx) - buttonX;
                this.grabY = (int) Math.floor(my) - buttonY;
                this.startMouseX = (int) Math.floor(mx);
                this.startMouseY = (int) Math.floor(my);
                this.previewX = buttonX;
                this.previewY = buttonY;
                this.moved = false;
        }

        /** Moves the preview to the cursor, tracking whether this is a real drag. */
        public void moveTo(double mx, double my) {
                if (!isActive()) {
                        return;
                }
                int mouseX = (int) Math.floor(mx);
                int mouseY = (int) Math.floor(my);
                previewX = mouseX - grabX;
                previewY = mouseY - grabY;
                if (Math.abs(mouseX - startMouseX) >= JITTER_PIXELS || Math.abs(mouseY - startMouseY) >= JITTER_PIXELS) {
                        moved = true;
                }
        }

        /**
         * Ends the drag. Returns the dragged button when the press became a
         * real drag, {@code null} when it stayed a tap — the caller turns a
         * real drag's preview position into a region move.
         */
        public InventoryButton end() {
                if (!isActive()) {
                        return null;
                }
                InventoryButton grabbed = button;
                button = null;
                return moved ? grabbed : null;
        }

        /** Aborts the drag, leaving the button where it was. */
        public void cancel() {
                button = null;
                moved = false;
        }
}
