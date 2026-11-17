package net.zaminmc.zea.search;

import net.minecraft.client.gui.ComponentPath;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.navigation.FocusNavigationEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.zaminmc.zea.buttons.ButtonGeometry;
import net.zaminmc.zea.mixin.EditBoxAccessor;

import java.util.ArrayList;
import java.util.List;

/**
 * The inventory search bar, drawn as NEU's search bar: the
 * nine-slice {@code search_bar} texture (a 20×20 plate with a 4-pixel
 * border, tiled as NEU tiles it — corners fixed, edges and center
 * stretched), the white value five pixels in from the left and centered
 * on the bar's height, the blinking white cursor, the light-gray
 * selection with the black text drawn over it, and the gold variant of
 * the texture while matching mode is active. Flanking square plates —
 * NEU's {@code quickcommand_background} with the settings gear and the
 * search-mode magnifier — are drawn by
 * {@link #extractWidgetRenderState} too and hit-tested through
 * {@link SearchBarLayout}; the settings plate opens ZEA's config, the
 * mode plate toggles matching mode exactly like NEU's search-mode
 * button.
 *
 * <p>Underneath the NEU skin it is still a real {@link EditBox}: the
 * keyboard machinery — caret movement, selection, clipboard, the held
 * key repeat, the focus rules the inventory depends on — stays vanilla's,
 * and the calculator coloring rides the same formatter it always did.
 * Only the drawing is NEU's. The calculator result overlay keeps its
 * placement after the visible text (the field scrolls long input and
 * vanilla exposes neither {@code displayPos} nor the drawn {@code textX},
 * hence the accessor), with NEC's floating box as the fallback when the
 * bar runs out of room.
 *
 * <p>Focus arrives programmatically (click routing in the container
 * mixin) and Tab/arrows are refused like the chat search bar, so the
 * field never joins the screen's focus navigation.
 */
public final class SearchBarWidget extends EditBox {
	/** The nine-slice texture's edge width, in texture pixels — NEU's 4-of-20 slices. */
	private static final float SLICE = 4f / 20f;
	private static final int TEXT_COLOR = 0xFFFFFFFF;
	private static final int CURSOR_COLOR = 0xFFFFFFFF;
	private static final int SELECTION_COLOR = 0xFFC8C8C8;
	private static final int SELECTED_TEXT_COLOR = 0xFF000000;
	private static final int RESULT_BACKDROP = 0xB0000000;
	private static final int FLOATING_BACKDROP = 0xEE000000;
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

	public SearchBarWidget(Font font, int x, int y, int width, int height, InventorySearch search) {
		super(font, x, y, width, height, Component.translatable("zea.search.bar"));
		setMaxLength(Calculator.MAX_INPUT_LENGTH);
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
		if (!visible) {
			return;
		}
		drawFlankingButtons(extractor);
		drawBarBackground(extractor);
		drawValue(extractor);
		if (calculatorEnabled && search.mode() == InventorySearch.Mode.CALCULATOR) {
			drawResult(extractor);
		}
	}

	/** The settings and search-mode plates flanking the bar — NEU's group. */
	private void drawFlankingButtons(GuiGraphicsExtractor extractor) {
		int scaleFactor = net.minecraft.client.Minecraft.getInstance().getWindow().getGuiScale();
		int[] settings = SearchBarLayout.settingsButton(getScreenWidth(), getScreenHeight(), scaleFactor);
		int[] mode = SearchBarLayout.modeButton(getScreenWidth(), getScreenHeight(), scaleFactor);
		drawPlate(extractor, settings);
		drawPlate(extractor, mode);
		int inset = SearchBarLayout.buttonPadding(scaleFactor);
		extractor.blit(net.zaminmc.zea.browser.NeuTextures.SETTINGS,
				settings[0] + inset, settings[1] + inset,
				settings[0] + inset + settings[2] - 2 * inset,
				settings[1] + inset + settings[3] - 2 * inset, 0f, 1f, 0f, 1f);
		extractor.blit(net.zaminmc.zea.browser.NeuTextures.SEARCH_MODE_BUTTON,
				mode[0] + inset, mode[1] + inset,
				mode[0] + inset + mode[2] - 2 * inset,
				mode[1] + inset + mode[3] - 2 * inset, 0f, 1f, 0f, 1f);
	}

	/** One square button plate — NEU's quickcommand_background. */
	private static void drawPlate(GuiGraphicsExtractor extractor, int[] rect) {
		extractor.blit(net.zaminmc.zea.browser.NeuTextures.QUICKCOMMAND_BACKGROUND,
				rect[0], rect[1], rect[0] + rect[2], rect[1] + rect[3], 0f, 1f, 0f, 1f);
	}

	/**
	 * The bar's background: NEU's nine-slice, three by three — the
	 * 4-pixel corners fixed, the edges and center stretched — the gold
	 * texture while matching mode is active.
	 */
	private void drawBarBackground(GuiGraphicsExtractor extractor) {
		var texture = search.mode() == InventorySearch.Mode.MATCHING
				? net.zaminmc.zea.browser.NeuTextures.SEARCH_BAR_GOLD
				: net.zaminmc.zea.browser.NeuTextures.SEARCH_BAR;
		int x = getX();
		int y = getY();
		int w = getWidth();
		int h = getHeight();
		for (int yIndex = 0; yIndex <= 2; yIndex++) {
			for (int xIndex = 0; xIndex <= 2; xIndex++) {
				float uMin = 0;
				float uMax = SLICE;
				int partX = x;
				int partW = 4;
				if (xIndex == 1) {
					partX = x + 4;
					uMin = SLICE;
					uMax = 1 - SLICE;
					partW = w - 8;
				} else if (xIndex == 2) {
					partX = x + w - 4;
					uMin = 1 - SLICE;
					uMax = 1f;
				}
				float vMin = 0;
				float vMax = SLICE;
				int partY = y;
				int partH = 4;
				if (yIndex == 1) {
					partY = y + 4;
					vMin = SLICE;
					vMax = 1 - SLICE;
					partH = h - 8;
				} else if (yIndex == 2) {
					partY = y + h - 4;
					vMin = 1 - SLICE;
					vMax = 1f;
				}
				extractor.blit(texture, partX, partY, partX + partW, partY + partH,
						uMin, uMax, vMin, vMax);
			}
		}
	}

	/** The value, the selection and the blinking cursor — NEU's draw order. */
	private void drawValue(GuiGraphicsExtractor extractor) {
		int x = getX() + SearchBarLayout.TEXT_INSET;
		int y = getY() + (getHeight() - font.lineHeight) / 2 + 1;
		String value = getValue();
		int displayPos = zea$displayPos();
		String displayed = zea$visibleText();
		FormattedCharSequence styled = zea$highlight(displayed, displayPos);
		extractor.text(font, styled != null ? styled
				: FormattedCharSequence.forward(displayed, Style.EMPTY.withColor(TEXT_COLOR)),
				x, y, TEXT_COLOR, false);

		// The selection: the light-gray plate and the black text over it.
		String selectedText = getHighlighted();
		if (!selectedText.isEmpty()) {
			int selectionWidth = font.width(selectedText);
			int leftIndex = Math.min(getCursorPosition(), ((EditBoxAccessor) (Object) this).zea$highlightPos());
			int selX = x + font.width(value.substring(Math.min(displayPos, leftIndex),
					Math.max(displayPos, leftIndex)));
			extractor.fill(selX, y - 1, selX + selectionWidth, y + 9, SELECTION_COLOR);
			extractor.text(font, selectedText, selX, y, SELECTED_TEXT_COLOR, false);
		}

		// The cursor: a one-pixel white bar, blinking on the half-second.
		if (isFocused() && System.currentTimeMillis() % 1000 > 500) {
			int cursorPos = getCursorPosition();
			if (cursorPos >= displayPos && cursorPos <= displayPos + displayed.length()) {
				int cursorX = x + font.width(value.substring(displayPos, cursorPos));
				extractor.fill(cursorX, y - 1, cursorX + 1, y + 9, CURSOR_COLOR);
			}
		}
	}

	/** Where the visible substring starts — the EditBox scroll position. */
	private int zea$displayPos() {
		EditBoxAccessor self = (EditBoxAccessor) (Object) this;
		int pos = self.zea$displayPos();
		return Math.max(0, Math.min(pos, getValue().length()));
	}

	/** The displayed substring, clipped to the bar's inner width. */
	private String zea$visibleText() {
		String value = getValue();
		int innerWidth = getWidth() - 2 * SearchBarLayout.TEXT_INSET;
		return font.plainSubstrByWidth(value.substring(zea$displayPos()), innerWidth);
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
		String displayed = zea$visibleText();
		int textEndX = getX() + SearchBarLayout.TEXT_INSET + font.width(displayed);
		if (textEndX + 2 + totalWidth < getX() + getWidth()) {
			int textY = getY() + (getHeight() - font.lineHeight + 1) / 2;
			int x = textEndX + 2;
			extractor.fill(x - 2, getY(), x + totalWidth + 1, getY() + getHeight(), RESULT_BACKDROP);
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
		int boxHeight = getHeight() + 2;
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

	private int getScreenWidth() {
		return net.minecraft.client.Minecraft.getInstance().getWindow().getGuiScaledWidth();
	}

	private int getScreenHeight() {
		return net.minecraft.client.Minecraft.getInstance().getWindow().getGuiScaledHeight();
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
