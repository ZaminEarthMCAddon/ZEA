package net.zaminmc.zea.hud;

/**
 * One registered HUD element: its registration spec, its renderer, and
 * its live persisted state. The manager lists handles; the live HUD and
 * the manager's preview render through the same renderer; the state is
 * the single mutable truth both read.
 */
public final class HudElementHandle {
        private final HudElementSpec spec;
        private final HudRenderer renderer;
        private HudElementState state;

        public HudElementHandle(HudElementSpec spec, HudRenderer renderer, HudElementState state) {
                this.spec = spec;
                this.renderer = renderer;
                this.state = state;
        }

        public HudElementSpec spec() {
                return spec;
        }

        public HudRenderer renderer() {
                return renderer;
        }

        public HudElementState state() {
                return state;
        }

        /** Rebinds the live state — the store's reload path. */
        public void bind(HudElementState state) {
                this.state = state;
        }
}
