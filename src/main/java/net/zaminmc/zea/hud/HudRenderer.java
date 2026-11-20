package net.zaminmc.zea.hud;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * One HUD element's drawing implementation. The manager and the live HUD
 * call the same method — an element renders into the rectangle the layout
 * math resolved for it and nowhere else, and {@code editorPreview} lets
 * it relax live-only gates (a destination waypoint with no live data
 * still draws its diamond) so the manager always shows where the element
 * sits.
 */
public interface HudRenderer {
        /**
         * Draws the element inside {@code x},{@code y},{@code width},
         * {@code height} — GUI pixels. Implementations must not draw
         * outside the rectangle and must stay cheap: this runs on the
         * render thread every frame.
         */
        void render(GuiGraphicsExtractor graphics, int x, int y, int width, int height, boolean editorPreview);
}
