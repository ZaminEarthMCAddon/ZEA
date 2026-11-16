package net.zaminmc.zea.search;

import net.minecraft.client.gui.ComponentPath;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.navigation.FocusNavigationEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.zaminmc.zea.mixin.EditBoxAccessor;

import java.util.ArrayList;
import java.util.List;

/**
 * The inventory search bar: a borderless, flat-background edit box shown
 * beneath the container GUI. Text editing, cursor and selection are all
 * vanilla {@link EditBox} behavior — this class adds the flat
 * background, the gold outline while inventory-matching mode is active
 * (NEU tinted its bar gold the same way), NEC-style syntax highlighting
 * and the calculator result overlay.
 *
 * <p>The result is an overlay, never the field's value: the expression
 * stays exactly as typed and the {@code = result} rides on top, drawn
 * <b>immediately after the typed text</b> — {@code 2+2 = 4} reads as
 * one line, instead of the result floating at the far end of the bar.
 * The placement follows the visible text (the field scrolls), and when
 * the expression grows long enough that the result no longer fits
 * beside it, the result moves into a small floating box under the bar
 * — NotEnoughCalculator's fallback — with the same slow marquee scroll
 * NEC uses for long numbers. A bare number or a plain word shows
 * nothing at all ({@link Calculator} gates that), so typing {@code 2}
 * no longer echoes {@code = 2}.
 *
 * <p>While the calculator is active the field also carries a
 * {@link EditBox.TextFormatter} that colors the expression by token
 * class — NEC's palette: numbers, units, functions, operators,
 * variables — computed from the full value so tokens split by the
 * caret still color correctly.
 *
 * <p>Focus arrives programmatically (click routing in the container
 * mixin) and Tab/arrows are refused like the chat search bar, so the
 * field never joins the screen's focus navigation.
 */
public final class SearchBarWidget extends EditBox {
        /** Height of the bar. */
        public static final int HEIGHT = 12;

        private static final int BACKGROUND = 0x99000000;
        private static final int RESULT_BACKDROP = 0xB0000000;
        private static final int FLOATING_BACKDROP = 0xEE000000;
        private static final int MATCHING_BORDER = 0xFFE8B14A;
        private static final int RESULT_EQUALS_COLOR = 0xFFFFFF55;
        private static final int RESULT_VALUE_COLOR = 0xFF55FF55;
        /** The NEC result marquee period, in milliseconds. */
        private static final long MARQUEE_PERIOD_MILLIS = 4000;

        private final InventorySearch search;
        private final Font font;
        private boolean calculatorEnabled;
        private String cachedInput = "";
        private String cachedResult;
        private String highlightInput;
        private int[] highlightColors;

        public SearchBarWidget(Font font, int x, int y, int width, InventorySearch search) {
                super(font, x, y, width, HEIGHT, Component.translatable("zea.search.bar"));
                setBordered(false);
                setHeight(HEIGHT);
                setMaxLength(Calculator.MAX_INPUT_LENGTH);
                setHint(Component.translatable("zea.search.hint"));
                addFormatter(this::zea$highlight);
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
                if (visible) {
                        extractor.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), BACKGROUND);
                        if (search.mode() == InventorySearch.Mode.MATCHING) {
                                extractor.fill(getX(), getY(), getX() + getWidth(), getY() + 1, MATCHING_BORDER);
                                extractor.fill(getX(), getY() + getHeight() - 1, getX() + getWidth(),
                                                getY() + getHeight(), MATCHING_BORDER);
                                extractor.fill(getX(), getY(), getX() + 1, getY() + getHeight(), MATCHING_BORDER);
                                extractor.fill(getX() + getWidth() - 1, getY(), getX() + getWidth(),
                                                getY() + getHeight(), MATCHING_BORDER);
                        }
                }
                super.extractWidgetRenderState(extractor, mouseX, mouseY, partialTick);
                if (visible && calculatorEnabled && search.mode() == InventorySearch.Mode.CALCULATOR) {
                        drawResult(extractor);
                }
        }

        /**
         * Draws the calculator result next to the typed text —
         * {@code 2+2 = 4} — or, when the bar has no room left after the
         * expression, in the floating box beneath it. Nothing is drawn
         * while the input is not a complete calculation.
         */
        private void drawResult(GuiGraphicsExtractor extractor) {
                String input = getValue();
                if (!input.equals(cachedInput)) {
                        cachedInput = input;
                        cachedResult = Calculator.result(input).orElse(null);
                }
                if (cachedResult == null) {
                        return;
                }
                String equals = " = ";
                int totalWidth = font.width(equals) + font.width(cachedResult);

                // Where the visible text ends: the field scrolls long input,
                // so the anchor is the displayed substring, not the value.
                EditBoxAccessor self = (EditBoxAccessor) (Object) this;
                String displayed = font.plainSubstrByWidth(
                                input.substring(self.zea$displayPos()), getInnerWidth());
                int textEndX = self.zea$textX() + font.width(displayed);
                if (textEndX + 2 + totalWidth < getX() + getWidth()) {
                        int textY = getY() + (HEIGHT - font.lineHeight + 1) / 2;
                        int x = textEndX + 2;
                        extractor.fill(x - 2, getY(), x + totalWidth + 1, getY() + HEIGHT, RESULT_BACKDROP);
                        extractor.text(font, equals, x, textY, RESULT_EQUALS_COLOR, true);
                        extractor.text(font, cachedResult, x + font.width(equals), textY, RESULT_VALUE_COLOR, true);
                        return;
                }
                zea$drawFloatingResult(extractor, equals, totalWidth);
        }

        /**
         * NEC's fallback result box: a tooltip-dark plate under the bar
         * (above it when the screen ends first), right-aligned with the
         * bar, scrolling sideways on a slow sine marquee when the number
         * is wider than the box.
         */
        private void zea$drawFloatingResult(GuiGraphicsExtractor extractor, String equals, int totalWidth) {
                int boxWidth = Math.min(totalWidth + 6, getWidth());
                int boxHeight = HEIGHT + 2;
                int boxY = getY() + getHeight() + 1;
                if (boxY + boxHeight > extractor.guiHeight()) {
                        boxY = getY() - boxHeight - 1;
                }
                int boxX = Math.max(getX(), getX() + getWidth() - boxWidth);
                int visible = boxWidth - 6;
                int scroll = 0;
                if (totalWidth > visible) {
                        double cycle = (System.currentTimeMillis() % MARQUEE_PERIOD_MILLIS)
                                        / (double) MARQUEE_PERIOD_MILLIS;
                        scroll = (int) ((totalWidth - visible) * (Math.sin(cycle * Math.PI * 2) + 1) / 2);
                }
                int textY = boxY + (boxHeight - font.lineHeight + 1) / 2;
                extractor.fill(boxX, boxY, boxX + boxWidth, boxY + boxHeight, FLOATING_BACKDROP);
                extractor.enableScissor(boxX, boxY, boxX + boxWidth, boxY + boxHeight);
                int x = boxX + 3 - scroll;
                extractor.text(font, equals, x, textY, RESULT_EQUALS_COLOR, true);
                extractor.text(font, cachedResult, x + font.width(equals), textY, RESULT_VALUE_COLOR, true);
                extractor.disableScissor();
        }

        /**
         * The syntax highlighter: colors the displayed substring from a
         * per-character color map computed over the whole value (so a
         * token the caret splits in two still colors correctly). Returns
         * {@code null} — plain rendering — outside calculator mode or for
         * input that does not tokenize.
         */
        private FormattedCharSequence zea$highlight(String text, int offset) {
                if (!calculatorEnabled || search.mode() != InventorySearch.Mode.CALCULATOR || text.isEmpty()) {
                        return null;
                }
                String value = getValue();
                if (!value.equals(highlightInput)) {
                        highlightInput = value;
                        highlightColors = Calculator.syntaxColors(value);
                }
                if (highlightColors == null) {
                        return null;
                }
                List<FormattedCharSequence> runs = new ArrayList<>();
                int index = 0;
                while (index < text.length()) {
                        int color = zea$colorAt(offset + index);
                        int end = index + 1;
                        while (end < text.length() && zea$colorAt(offset + end) == color) {
                                end++;
                        }
                        runs.add(FormattedCharSequence.forward(text.substring(index, end), Style.EMPTY.withColor(color)));
                        index = end;
                }
                return FormattedCharSequence.composite(runs);
        }

        /** The highlight color of a character position in the full value. */
        private int zea$colorAt(int absolute) {
                return absolute >= 0 && absolute < highlightColors.length ? highlightColors[absolute]
                                : Calculator.SyntaxPalette.WORD;
        }

        /** Focus moves here only by explicit request; see the class javadoc. */
        @Override
        public ComponentPath nextFocusPath(FocusNavigationEvent navigationEvent) {
                return null;
        }
}
