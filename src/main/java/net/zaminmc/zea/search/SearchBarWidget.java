package net.zaminmc.zea.search;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.ComponentPath;
import net.minecraft.client.gui.navigation.FocusNavigationEvent;
import net.minecraft.network.chat.Component;
import net.zaminmc.zea.buttons.ButtonGeometry;

/**
 * The inventory search bar: a real Minecraft text field shown beneath the
 * container GUI. It is a plain vanilla {@link EditBox} with its native
 * bordered look — the {@code widget/text_field} sprites, which draw the
 * background, the border on all four sides and swap to the highlighted
 * variant while the field is focused — so the field reads as part of the
 * GUI instead of a floating translucent strip. Vanilla also owns the text
 * layout: bordered fields indent the text four pixels from the left edge
 * and center it vertically by the font metrics ({@code (height - 8) / 2}),
 * which keeps the text, selection and cursor inside the field at every
 * GUI scale.
 *
 * <p>The bar keeps two state visuals of its own, drawn over the sprite:
 * the gold outline while inventory-matching mode is active (NEU tinted
 * its bar gold the same way), and a quiet white frame while the mouse is
 * over an unfocused field — the hovered state the vanilla sprites do not
 * carry. There is deliberately no placeholder text: an empty field is a
 * clean empty field, and the search button and double-click affordance
 * teach the interaction.
 *
 * <p>The calculator result is an overlay, not the field's value: the
 * expression stays exactly as typed and the {@code = result} rides on
 * top with its own dark backdrop, so it stays readable even under the
 * caret. It is computed at most once per distinct input — the same
 * input replays the cached result every frame.
 *
 * <p>Position and size are decided by the shared layout math
 * ({@link SearchBarLayout}) used identically by the runtime container
 * mixin and the button editor's preview — the editor previews this exact
 * widget, not a lookalike.
 *
 * <p>Focus arrives programmatically (click routing in the container
 * mixin) and Tab/arrows are refused like the chat search bar, so the
 * field never joins the screen's focus navigation.
 */
public final class SearchBarWidget extends EditBox {
        /** Height of the bar — vanilla's standard field height, which the field sprites are drawn for. */
        public static final int HEIGHT = 20;

        /**
         * Horizontal inset between the bar and the container GUI's side
         * edges: the bar is centered on the GUI's bounds, not the
         * framebuffer, and reads as attached to it.
         */
        public static final int MARGIN = 8;

        /**
         * Vertical space the bar occupies below the GUI, including its
         * gap: buttons anchored to the GUI's bottom edge are shifted down
         * by this much so the two never overlap (see
         * {@link ButtonGeometry.Inputs#bottomBandOffset}).
         */
        public static final int BOTTOM_BAND = HEIGHT + ButtonGeometry.GAP;

        private static final int MATCHING_BORDER = 0xFFE8B14A;
        private static final int HOVER_BORDER = 0x64FFFFFF;
        private static final int RESULT_BACKDROP = 0xB0000000;
        private static final int RESULT_EQUALS_COLOR = 0xFFFFFF55;
        private static final int RESULT_VALUE_COLOR = 0xFF55FF55;

        private final InventorySearch search;
        private final Font font;
        private boolean calculatorEnabled;
        private String cachedInput = "";
        private String cachedResult;

        public SearchBarWidget(Font font, int x, int y, int width, InventorySearch search) {
                super(font, x, y, width, HEIGHT, Component.translatable("zea.search.bar"));
                setMaxLength(Calculator.MAX_INPUT_LENGTH);
                this.search = search;
                this.font = font;
        }

        /** Whether the calculator result overlay is enabled (config). */
        public void calculatorEnabled(boolean enabled) {
                this.calculatorEnabled = enabled;
        }

        @Override
        public void extractWidgetRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY,
                        float partialTick) {
                super.extractWidgetRenderState(extractor, mouseX, mouseY, partialTick);
                if (!visible) {
                        return;
                }
                // State visuals go over the vanilla sprite, never under it:
                // the sprite would cover anything drawn first.
                if (search.mode() == InventorySearch.Mode.MATCHING) {
                        // One-pixel frame just outside the field's own border,
                        // so all four sides stay visible against both the
                        // sprite and the GUI behind it.
                        drawFrame(extractor, getX() - 1, getY() - 1, getWidth() + 2, getHeight() + 2,
                                        MATCHING_BORDER);
                } else if (!isFocused()
                                && ButtonGeometry.contains(getX(), getY(), getWidth(), getHeight(), mouseX, mouseY)) {
                        drawFrame(extractor, getX(), getY(), getWidth(), getHeight(), HOVER_BORDER);
                }
                if (calculatorEnabled && search.mode() == InventorySearch.Mode.CALCULATOR) {
                        drawResult(extractor);
                }
        }

        /** A one-pixel frame around a rectangle — the bar's own state outline. */
        private static void drawFrame(GuiGraphicsExtractor extractor, int x, int y, int w, int h, int color) {
                extractor.fill(x, y, x + w, y + 1, color);
                extractor.fill(x, y + h - 1, x + w, y + h, color);
                extractor.fill(x, y + 1, x + 1, y + h - 1, color);
                extractor.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
        }

        /** Draws the cached calculator result, right-aligned inside the field's padded area. */
        private void drawResult(GuiGraphicsExtractor extractor) {
                String input = getValue();
                if (input.equals(cachedInput)) {
                        // reuse cachedResult, including a deliberate null
                } else {
                        cachedInput = input;
                        cachedResult = Calculator.compute(input).map(Calculator::format).orElse(null);
                }
                if (cachedResult == null) {
                        return;
                }
                String equals = " = ";
                int totalWidth = font.width(equals) + font.width(cachedResult);
                if (totalWidth + 8 >= getWidth()) {
                        return; // no room; the expression owns the bar
                }
                int x = getX() + getWidth() - totalWidth - 3;
                int y = getY() + (HEIGHT - font.lineHeight + 1) / 2;
                extractor.fill(x - 2, getY() + 1, getX() + getWidth() - 1, getY() + getHeight() - 1, RESULT_BACKDROP);
                extractor.text(font, equals, x, y, RESULT_EQUALS_COLOR, true);
                extractor.text(font, cachedResult, x + font.width(equals), y, RESULT_VALUE_COLOR, true);
        }

        /** Focus moves here only by explicit request; see the class javadoc. */
        @Override
        public ComponentPath nextFocusPath(FocusNavigationEvent navigationEvent) {
                return null;
        }
}
