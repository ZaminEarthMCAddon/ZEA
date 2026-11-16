package net.zaminmc.zea.buttons;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.function.ToIntFunction;

/**
 * Draws the buttons of a container screen from the layout's placements.
 * Pure presentation: positions come from {@link ButtonGeometry}, icons
 * from {@link ButtonIcons}, tooltip text from {@link ButtonTooltips};
 * this class owns only the sprite and the colors.
 *
 * <p>The background is a real texture — ZEA's own vanilla-styled button
 * sheet ({@code zea:textures/gui/buttons.png}, five 18×18 variants in a
 * 90×18 strip), drawn the way the reference port draws its sheet — so a
 * button reads as a GUI control instead of the flat box the first
 * iteration drew. The icon draws over it at 16×16 with one pixel of
 * padding: an item stack, or one of the custom texture glyphs. The
 * editor draws through {@link #drawFace} — the same method the runtime
 * uses — so the preview can never drift from the real rendering.
 *
 * <p>Labels fit their own button box: measured with the real font
 * metrics (never estimated from character counts), scaled down when the
 * label is wider than the fit box, and — past a readability floor —
 * truncated with an ellipsis, one unusually long label never shrinking
 * any other button. The full label stays in the configuration and is
 * reachable through the hover tooltip.
 */
public final class ButtonRenderer {
        /** ZEA's button background sheet: five 18×18 styles in a row. */
        public static final Identifier BUTTONS_TEXTURE =
                        Identifier.fromNamespaceAndPath("zea", "textures/gui/buttons.png");

        private static final int TEXTURE_WIDTH = 90;
        private static final int TEXTURE_HEIGHT = 18;

        /**
         * The readability floor: a label shrinks no further than this
         * before truncation takes over — a hairline-scaled label is less
         * readable than a clipped one with the full text on hover.
         */
        public static final float MIN_TEXT_SCALE = 0.75f;

        private static final int BRIGHT_TEXT = 0xFFFFFFFF;
        private static final int IDLE_TEXT = 0xFFC8C8E0;

        private ButtonRenderer() {
        }

        /**
         * Renders the buttons. Unrendered placements (over the slot band of a
         * foreign container, under the recipe book, off-screen) are skipped;
         * unusable or disabled buttons never draw in the container view — the
         * editor is where their work-in-progress is shown.
         *
         * @param placements the layout's resolved placements, in draw order
         */
        public static void render(
                        GuiGraphicsExtractor graphics,
                        Font font,
                        List<ButtonGeometry.Placement> placements,
                        int left,
                        int top,
                        int mouseX,
                        int mouseY) {
                for (ButtonGeometry.Placement placement : placements) {
                        InventoryButton button = placement.button();
                        if (!placement.rendered() || !button.enabled() || !button.isUsable()) {
                                continue;
                        }
                        int x = placement.x();
                        int y = placement.y();
                        boolean hovered = ButtonGeometry.contains(x, y, ButtonGeometry.SIZE, ButtonGeometry.SIZE, mouseX, mouseY);

                        drawBackground(graphics, x, y, button.style());
                        boolean truncated = drawFace(graphics, font, button, x, y, hovered);
                        if (hovered) {
                                drawBorder(graphics, x, y, 0xFFFFFFFF);
                                List<Component> lines = ButtonTooltips.lines(button,
                                                ButtonIcons.isKnown(button.icon()), placement.rendered());
                                if (truncated && !button.text().equals(button.name())) {
                                        // The face clipped the label; the hover
                                        // tooltip carries what was cut away.
                                        lines.add(1, Component.literal(button.text())
                                                        .withStyle(ChatFormatting.DARK_GRAY));
                                }
                                graphics.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY);
                        }
                }
        }

        /**
         * Draws the button's face over its styled background: the fitted
         * display text when one is set, otherwise the custom glyph or the
         * item icon, and a "?" for an unknown icon. The shared face path
         * for runtime and editor.
         *
         * @return whether the display text had to be truncated to fit
         */
        public static boolean drawFace(GuiGraphicsExtractor graphics, Font font, InventoryButton button,
                        int x, int y, boolean bright) {
                if (!button.text().isBlank()) {
                        return drawFittedText(graphics, font, button.text(), x, y, bright);
                }
                Identifier custom = ButtonIcons.customTextureFor(button.icon());
                if (custom != null) {
                        graphics.blit(RenderPipelines.GUI_TEXTURED, custom, x + 1, y + 1,
                                        0.0f, 0.0f, 16, 16, 16, 16);
                        return false;
                }
                ItemStack stack = ButtonIcons.iconFor(button.icon());
                if (!stack.isEmpty()) {
                        graphics.item(stack, x + 1, y + 1);
                } else {
                        graphics.centeredText(font, Component.literal("?"), x + ButtonGeometry.SIZE / 2,
                                        y + (ButtonGeometry.SIZE - font.lineHeight) / 2,
                                        bright ? BRIGHT_TEXT : IDLE_TEXT);
                }
                return false;
        }

        /** One style swatch of the sheet — the editor's style picker. */
        public static void drawSwatch(GuiGraphicsExtractor graphics, int x, int y, int style) {
                drawBackground(graphics, x, y, style);
        }

        /** The styled background sprite of one button. */
        private static void drawBackground(GuiGraphicsExtractor graphics, int x, int y, int style) {
                graphics.blit(RenderPipelines.GUI_TEXTURED, BUTTONS_TEXTURE, x, y,
                                (float) (style * ButtonGeometry.SIZE), 0.0f,
                                ButtonGeometry.SIZE, ButtonGeometry.SIZE, TEXTURE_WIDTH, TEXTURE_HEIGHT);
        }

        /** A one-pixel frame around a button-sized square. */
        public static void drawBorder(GuiGraphicsExtractor graphics, int x, int y, int color) {
                drawBorder(graphics, x, y, ButtonGeometry.SIZE, ButtonGeometry.SIZE, color);
        }

        /** A one-pixel frame around an arbitrary rectangle. */
        public static void drawBorder(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int color) {
                graphics.fill(x, y, x + w, y + 1, color);
                graphics.fill(x, y + h - 1, x + w, y + h, color);
                graphics.fill(x, y + 1, x + 1, y + h - 1, color);
                graphics.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
        }

        /** One label as it should be drawn: the string, its scale, whether it was clipped. */
        public record FittedLabel(String display, float scale, boolean truncated) {
        }

        /**
         * The fitted label for {@code text} in a {@code boxWidth}-wide box,
         * by real font metrics: natural size when it fits, scaled down
         * while the shrink stays readable, ellipsized past the floor. The
         * policy is pure and pinned by tests without Minecraft.
         *
         * @param measure how wide a string renders in the live font
         * @param ellipsisWidth the width of the ellipsis glyph in the live font
         */
        public static FittedLabel fitLabel(String text, int boxWidth, ToIntFunction<String> measure,
                        int ellipsisWidth) {
                int natural = measure.applyAsInt(text);
                if (natural <= boxWidth) {
                        return new FittedLabel(text, 1f, false);
                }
                float scale = (float) boxWidth / natural;
                if (scale >= MIN_TEXT_SCALE) {
                        return new FittedLabel(text, scale, false);
                }
                scale = MIN_TEXT_SCALE;
                int budget = (int) (boxWidth / scale);
                return new FittedLabel(ellipsize(text, budget, measure, ellipsisWidth), scale, true);
        }

        /** The longest prefix of {@code text} plus an ellipsis that fits {@code budget}. */
        private static String ellipsize(String text, int budget, ToIntFunction<String> measure, int ellipsisWidth) {
                if (ellipsisWidth > budget) {
                        return ""; // degenerate box: nothing readable fits
                }
                for (int keep = text.length(); keep >= 0; keep--) {
                        String candidate = text.substring(0, keep) + "…";
                        if (measure.applyAsInt(candidate) <= budget) {
                                return candidate;
                        }
                }
                return "…";
        }

        /**
         * Draws a label scaled down (past the {@link #MIN_TEXT_SCALE}
         * readability floor, then ellipsized) to fit {@code boxWidth},
         * horizontally centered on {@code centerX} with its top at
         * {@code y} — the small fixed-width chrome (the editor's preset
         * row) that must never let text spill past its own border.
         *
         * @return whether the text was truncated
         */
        public static boolean drawFittedCenteredText(GuiGraphicsExtractor graphics, Font font, String text,
                        int centerX, int y, int boxWidth, int color) {
                FittedLabel fitted = fitLabel(text, boxWidth, font::width, font.width("…"));
                if (fitted.scale() >= 1f) {
                        graphics.centeredText(font, fitted.display(), centerX, y, color);
                        return fitted.truncated();
                }
                var pose = graphics.pose();
                pose.pushMatrix();
                pose.translate(centerX, y + font.lineHeight / 2f);
                pose.scale(fitted.scale(), fitted.scale());
                graphics.text(font, fitted.display(), -font.width(fitted.display()) / 2,
                                -font.lineHeight / 2, color, false);
                pose.popMatrix();
                return fitted.truncated();
        }

        /**
         * Draws text scaled down until it fits the button box, centered.
         * Past the readability floor the label is ellipsized instead; the
         * caller surfaces the full text through the tooltip.
         *
         * @return whether the text was truncated
         */
        private static boolean drawFittedText(GuiGraphicsExtractor graphics, Font font, String text, int x, int y,
                        boolean bright) {
                int color = bright ? BRIGHT_TEXT : IDLE_TEXT;
                int box = ButtonGeometry.SIZE - 4;
                FittedLabel fitted = fitLabel(text, box, font::width, font.width("…"));
                if (fitted.scale() >= 1f) {
                        graphics.centeredText(font, fitted.display(), x + ButtonGeometry.SIZE / 2,
                                        y + (ButtonGeometry.SIZE - font.lineHeight) / 2, color);
                        return fitted.truncated();
                }
                var pose = graphics.pose();
                pose.pushMatrix();
                pose.translate(x + ButtonGeometry.SIZE / 2f, y + ButtonGeometry.SIZE / 2f);
                pose.scale(fitted.scale(), fitted.scale());
                int textY = -font.lineHeight / 2;
                int textX = -font.width(fitted.display()) / 2;
                graphics.text(font, fitted.display(), textX, textY, color, true);
                pose.popMatrix();
                return fitted.truncated();
        }
}
