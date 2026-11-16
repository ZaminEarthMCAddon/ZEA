package net.zaminmc.zea.search;

import net.zaminmc.zea.buttons.ButtonGeometry;

/**
 * The shared layout math for the inventory search bar — the one place
 * that answers where the bar sits for a container GUI. The runtime
 * container mixin and the button editor's preview both go through these
 * functions, so the editor previews the exact rectangle the bar occupies
 * during real play; a per-screen offset can never drift back in.
 *
 * <p>The bar is centered on the container GUI's own bounds — its left
 * edge is {@code leftPos + MARGIN} and its width the GUI width minus two
 * margins — never on the framebuffer, so it stays centered at every GUI
 * scale and resolution and reads as part of the interface it searches.
 * Vertically it sits one gap below the GUI's bottom edge.
 */
public final class SearchBarLayout {
        private SearchBarLayout() {
        }

        /** The bar's left edge for a container GUI anchored at {@code guiLeft}. */
        public static int left(int guiLeft) {
                return guiLeft + SearchBarWidget.MARGIN;
        }

        /** The bar's top edge — one gap below the GUI's bottom edge. */
        public static int top(int guiTop, int guiHeight) {
                return guiTop + guiHeight + ButtonGeometry.GAP;
        }

        /** The bar's width — the GUI width minus the centered side margins. */
        public static int width(int guiWidth) {
                return Math.max(8, guiWidth - 2 * SearchBarWidget.MARGIN);
        }

        /**
         * Applies the layout to a widget for the given container GUI
         * rectangle — the single call both the runtime mixin and the
         * editor preview use.
         */
        public static void apply(net.minecraft.client.gui.components.AbstractWidget widget,
                        int guiLeft, int guiTop, int guiWidth, int guiHeight) {
                widget.setX(left(guiLeft));
                widget.setY(top(guiTop, guiHeight));
                widget.setWidth(width(guiWidth));
        }
}
