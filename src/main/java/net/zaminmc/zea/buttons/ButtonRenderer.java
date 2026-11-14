package net.zaminmc.zea.buttons;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Draws the buttons of a container screen from the layout's placements.
 * Pure presentation: positions come from {@link ButtonGeometry}, icons
 * from {@link ButtonIcons}, tooltip text from {@link ButtonTooltips};
 * this class owns only the rectangles and colors.
 *
 * <p>Drawing is a slot-like box — a one-pixel border over a dark fill —
 * so buttons read as part of the GUI without new textures. A button with
 * display text draws the text instead of an icon, scaled down to fit the
 * 18-pixel box. In edit mode, buttons draw with a gold border,
 * unconfigured buttons stay visible with a hint, a ghost button offers
 * creating new ones, and an in-progress drag is drawn at its preview
 * position.
 */
public final class ButtonRenderer {
        /** Border color of an idle button (slate). */
        private static final int BORDER = 0xFF5A5A78;

        /** Border color of a hovered button (lighter slate). */
        private static final int HOVER_BORDER = 0xFF9A9AC8;

        /** Fill of an idle button (near-black). */
        private static final int BACKGROUND = 0xB0101014;

        /** Fill of a hovered button (dark slate). */
        private static final int HOVER_BACKGROUND = 0xB028283C;

        /** Border color while editing (gold). */
        private static final int EDIT_BORDER = 0xFFE8B14A;

        /** Border color of an unconfigured button in edit mode (dim gold). */
        private static final int UNCONFIGURED_BORDER = 0xFF8A6A2A;

        /** Overlay that dims disabled buttons in edit mode. */
        private static final int DISABLED_OVERLAY = 0x66101014;

        /** Where the edit-mode hint line sits above the container. */
        private static final int HINT_OFFSET_Y = -12;

        private ButtonRenderer() {
        }

        /**
         * Renders the buttons, plus edit-mode furniture when requested.
         * Unrendered placements (hidden by overflow, the recipe book, or a
         * missing search bar) are skipped outside edit mode; edit mode
         * still draws them so crowding is visible and fixable.
         *
         * @param placements the layout's resolved placements, in draw order
         * @param editMode   whether button editing is active
         * @param drag       the edit-mode drag session (idle when nothing is dragged)
         */
        public static void render(
                        GuiGraphicsExtractor graphics,
                        Font font,
                        List<ButtonGeometry.Placement> placements,
                        int left,
                        int top,
                        int mouseX,
                        int mouseY,
                        boolean editMode,
                        ButtonDragSession drag) {
                boolean dragging = drag.isActive();
                for (ButtonGeometry.Placement placement : placements) {
                        InventoryButton button = placement.button();
                        boolean isDragged = dragging && button.id().equals(drag.button().id());
                        int x = isDragged ? drag.previewX() : placement.x();
                        int y = isDragged ? drag.previewY() : placement.y();
                        if (!editMode) {
                                if (!placement.rendered() || !button.enabled() || !button.isUsable()) {
                                        continue;
                                }
                        }
                        boolean hovered = !dragging
                                        && ButtonGeometry.contains(x, y, ButtonGeometry.SIZE, ButtonGeometry.SIZE, mouseX, mouseY);

                        drawBox(graphics, x, y, hovered, editMode && button.isUsable());
                        if (editMode && !button.isUsable()) {
                                graphics.fill(x, y, x + ButtonGeometry.SIZE, y + ButtonGeometry.SIZE, UNCONFIGURED_BORDER);
                                graphics.fill(x + 1, y + 1, x + ButtonGeometry.SIZE - 1, y + ButtonGeometry.SIZE - 1, BACKGROUND);
                        }

                        if (!button.text().isBlank()) {
                                drawFittedText(graphics, font, button.text(), x, y, hovered || editMode);
                        } else {
                                graphics.item(ButtonIcons.iconFor(button.icon()), x + 1, y + 1);
                        }
                        if (!button.enabled()) {
                                graphics.fill(x, y, x + ButtonGeometry.SIZE, y + ButtonGeometry.SIZE, DISABLED_OVERLAY);
                        }

                        if (hovered) {
                                List<Component> lines = ButtonTooltips.lines(button,
                                                ButtonIcons.isKnown(button.icon()), placement.rendered());
                                graphics.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY);
                        }
                }

                if (editMode) {
                        graphics.text(
                                        font,
                                        Component.translatable("zea.buttons.editing_hint"),
                                        left,
                                        top + HINT_OFFSET_Y,
                                        0xFFFFFFFF,
                                        true);
                }
        }

        /** Draws text scaled down until it fits the button box, centered. */
        private static void drawFittedText(GuiGraphicsExtractor graphics, Font font, String text, int x, int y,
                        boolean bright) {
                int color = bright ? 0xFFFFFFFF : 0xFFC8C8E0;
                int box = ButtonGeometry.SIZE - 4;
                int width = font.width(text);
                if (width <= box) {
                        graphics.centeredText(font, text, x + ButtonGeometry.SIZE / 2,
                                        y + (ButtonGeometry.SIZE - font.lineHeight) / 2, color);
                        return;
                }
                // Scale to fit: draw at reduced size by trimming font scale in steps.
                float scale = Math.min(1f, (float) box / width);
                var pose = graphics.pose();
                pose.pushMatrix();
                pose.translate(x + ButtonGeometry.SIZE / 2f, y + ButtonGeometry.SIZE / 2f);
                pose.scale(scale, scale);
                int textY = -font.lineHeight / 2;
                int textX = -font.width(text) / 2;
                graphics.text(font, text, textX, textY, color, true);
                pose.popMatrix();
        }

        private static void drawBox(GuiGraphicsExtractor graphics, int x, int y, boolean hovered, boolean configured) {
                int border = !configured ? UNCONFIGURED_BORDER : hovered ? HOVER_BORDER : BORDER;
                int background = hovered ? HOVER_BACKGROUND : BACKGROUND;
                graphics.fill(x, y, x + ButtonGeometry.SIZE, y + ButtonGeometry.SIZE, border);
                graphics.fill(x + 1, y + 1, x + ButtonGeometry.SIZE - 1, y + ButtonGeometry.SIZE - 1, background);
        }
}
