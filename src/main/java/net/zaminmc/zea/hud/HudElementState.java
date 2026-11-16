package net.zaminmc.zea.hud;

/**
 * The persisted per-element state: visibility, anchor, offsets and
 * scale. One entry per registered element id; a state for an unknown id
 * is kept on disk (a future element's settings survive a downgrade)
 * and a missing state falls back to the spec's defaults, so adding an
 * element never needs a migration.
 *
 * <p>Gson-mutable by design: the store reads and writes this class
 * directly, and unknown fields in the file are dropped on the next save
 * the same way the main config handles them.
 */
public final class HudElementState {
        private String id;
        private boolean visible;
        private HudAnchor anchor = HudAnchor.TOP_LEFT;
        private int offsetX;
        private int offsetY;
        /** Element scale, percent; 100 is the spec's natural size. */
        private int scalePercent = 100;

        public HudElementState() {
        }

        /** The factory state of a spec — what a missing entry restores to. */
        public static HudElementState ofDefaults(HudElementSpec spec) {
                HudElementState state = new HudElementState();
                state.id = spec.id().toString();
                state.visible = spec.defaultVisible();
                state.anchor = spec.defaultAnchor();
                state.offsetX = spec.defaultOffsetX();
                state.offsetY = spec.defaultOffsetY();
                state.scalePercent = 100;
                return state;
        }

        /** A deep copy — documents snapshot states so dirty checks see real changes. */
        public static HudElementState copyOf(HudElementState other) {
                HudElementState state = new HudElementState();
                state.id = other.id;
                state.visible = other.visible;
                state.anchor = other.anchor();
                state.offsetX = other.offsetX();
                state.offsetY = other.offsetY();
                state.scalePercent = other.scalePercent();
                return state;
        }

        public String id() {
                return id;
        }

        public boolean visible() {
                return visible;
        }

        public void visible(boolean visible) {
                this.visible = visible;
        }

        public HudAnchor anchor() {
                return anchor == null ? HudAnchor.TOP_LEFT : anchor;
        }

        public void anchor(HudAnchor anchor) {
                this.anchor = anchor == null ? HudAnchor.TOP_LEFT : anchor;
        }

        public int offsetX() {
                return offsetX;
        }

        public void offsetX(int offsetX) {
                this.offsetX = offsetX;
        }

        public int offsetY() {
                return offsetY;
        }

        public void offsetY(int offsetY) {
                this.offsetY = offsetY;
        }

        public int scalePercent() {
                return scalePercent < 25 ? 25 : Math.min(scalePercent, 400);
        }

        public void scalePercent(int scalePercent) {
                this.scalePercent = scalePercent;
        }

        /** The rendered size at the stored scale. */
        public int scaledWidth(HudElementSpec spec) {
                return Math.max(8, Math.round(spec.defaultWidth() * scalePercent() / 100f));
        }

        /** The rendered height at the stored scale. */
        public int scaledHeight(HudElementSpec spec) {
                return Math.max(8, Math.round(spec.defaultHeight() * scalePercent() / 100f));
        }
}
