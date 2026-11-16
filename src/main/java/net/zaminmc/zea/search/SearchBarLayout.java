package net.zaminmc.zea.search;

import net.minecraft.client.gui.components.AbstractWidget;

/**
 * The shared layout math for the inventory search bar — the one place
 * that answers where the bar sits for a container GUI. The runtime
 * container mixin and any preview (the button editor's canvas) both go
 * through these functions, so a preview shows the exact rectangle the
 * bar occupies during real play; a per-screen offset can never drift
 * back in.
 *
 * <p>Horizontally the bar is centered on the container GUI's own bounds
 * — its left edge is {@code guiLeft + MARGIN} and its width the GUI
 * width minus two margins — never on the framebuffer, so it stays
 * centered on the interface it searches at every GUI scale and
 * resolution. Vertically it sits near the screen's bottom edge, one
 * {@link SearchBarWidget#BOTTOM_MARGIN} above it, clamped so the bar
 * never leaves the screen — the same footprint the button geometry's
 * above-search zone is laid out against.
 */
public final class SearchBarLayout {
        private SearchBarLayout() {
        }

        /** The bar's left edge for a container GUI anchored at {@code guiLeft}. */
        public static int left(int guiLeft) {
                return guiLeft + SearchBarWidget.MARGIN;
        }

        /** The bar's width — the GUI width minus the centered side margins. */
        public static int width(int guiWidth) {
                return Math.max(8, guiWidth - 2 * SearchBarWidget.MARGIN);
        }

        /**
         * The bar's top edge — {@link SearchBarWidget#BOTTOM_MARGIN} above
         * the screen's bottom edge, clamped so a short screen never pushes
         * the bar off its top.
         */
        public static int top(int screenHeight) {
                return Math.clamp(
                                screenHeight - SearchBarWidget.HEIGHT - SearchBarWidget.BOTTOM_MARGIN,
                                0,
                                Math.max(0, screenHeight - SearchBarWidget.HEIGHT));
        }

        /**
         * Applies the layout to a widget for the given container GUI
         * rectangle — the single call the runtime mixin and any preview
         * use.
         */
        public static void apply(AbstractWidget widget, int guiLeft, int guiWidth, int screenHeight) {
                widget.setX(left(guiLeft));
                widget.setY(top(screenHeight));
                widget.setWidth(width(guiWidth));
        }
}
