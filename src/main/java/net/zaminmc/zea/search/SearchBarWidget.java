package net.zaminmc.zea.search;

import net.minecraft.client.gui.ComponentPath;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.navigation.FocusNavigationEvent;
import net.minecraft.network.chat.Component;

/**
 * The inventory search bar: a borderless, flat-background edit box shown
 * beneath the container GUI. Text editing, cursor and selection are all
 * vanilla {@link EditBox} behavior — this class adds the flat
 * background, the gold outline while inventory-matching mode is active
 * (NEU tinted its bar gold the same way), and the right-aligned
 * calculator result.
 *
 * <p>The calculator result is an overlay, not the field's value: the
 * expression stays exactly as typed and the {@code = result} rides on
 * top with its own dark backdrop, so it stays readable even under the
 * caret. It is computed at most once per distinct input — the same
 * input replays the cached result every frame.
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
        private static final int MATCHING_BORDER = 0xFFE8B14A;
        private static final int RESULT_EQUALS_COLOR = 0xFFFFFF55;
        private static final int RESULT_VALUE_COLOR = 0xFF55FF55;

        private final InventorySearch search;
        private final Font font;
        private boolean calculatorEnabled;
        private String cachedInput = "";
        private String cachedResult;

        public SearchBarWidget(Font font, int x, int y, int width, InventorySearch search) {
                super(font, x, y, width, HEIGHT, Component.translatable("zea.search.bar"));
                setBordered(false);
                setHeight(HEIGHT);
                setMaxLength(Calculator.MAX_INPUT_LENGTH);
                setHint(Component.translatable("zea.search.hint"));
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

        /** Draws the cached calculator result, right-aligned inside the bar. */
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
                extractor.fill(x - 2, getY(), getX() + getWidth(), getY() + HEIGHT, RESULT_BACKDROP);
                extractor.text(font, equals, x, y, RESULT_EQUALS_COLOR, true);
                extractor.text(font, cachedResult, x + font.width(equals), y, RESULT_VALUE_COLOR, true);
        }

        /** Focus moves here only by explicit request; see the class javadoc. */
        @Override
        public ComponentPath nextFocusPath(FocusNavigationEvent navigationEvent) {
                return null;
        }
}
