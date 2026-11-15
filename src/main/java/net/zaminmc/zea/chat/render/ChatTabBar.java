package net.zaminmc.zea.chat.render;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.ComponentPath;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.TextAlignment;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.navigation.FocusNavigationEvent;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;
import net.zaminmc.zea.chat.feeds.Feed;
import net.zaminmc.zea.chat.feeds.FeedSelection;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

/**
 * The chat tab strip: one quiet flat tab per visible feed, drawn above the
 * chat input. A plain click selects the display feed (visual only — server
 * channels are a separate state, never changed here). Shift-click toggles
 * the feed's channel as a multi-send target (General and the event feeds
 * have no channel and the toggle explains that); targeting is also a
 * separate state, reflected here as a thin marker strip and aqua label.
 *
 * <p>A tab holding messages that arrived while another tab was selected
 * shows its label in yellow instead of gray — the unread indicator — until
 * the player selects it again. Yellow outranks the aqua target color for
 * the label (the thin target marker still shows); the selected tab stays
 * white, and by definition nothing unread can arrive on it.
 *
 * <p>The strip never extends past the chat panel's on-screen width (see
 * {@link TabLayout} for the 26.3 width math), and labels are never
 * shortened — a row of "G…" tabs identifies nothing. Tabs that do not
 * fit compress their padding and then the strip scrolls by whole tabs:
 * small flat arrows appear at its right end (the action buttons' 9×9
 * idiom) and the mouse wheel over the strip scrolls it; the selected
 * tab is always kept in view, wherever the player last scrolled.
 *
 * <p>The strip can also be re-budgeted live — the chat screen shrinks it
 * when a search opens beside it and gives the width back when the search
 * closes — so the maximum width is a setter, not just a constructor
 * argument.
 *
 * <p>Rendering is deliberately restrained: flat translucent backgrounds, no
 * borders, no icons, no animation. The selected tab is simply the most
 * solid and the only one with white text.
 *
 * <p>The strip is mouse-driven and refuses keyboard focus navigation: 26.3
 * screens navigate focus with Tab and the arrow keys, and with this bar
 * registered as a focusable child, pressing up or down while typing in the
 * chat input (history recall) would move screen focus onto the strip and
 * silently eat every following keystroke — the dev.24 live report of losing
 * the caret mid-sentence. Vanilla chat survives those keys only because
 * its input is the sole widget on the screen.
 */
public final class ChatTabBar extends AbstractWidget {
	/** Height of the tab strip; the chat screen positions the bar with it. */
	public static final int TAB_HEIGHT = 12;
	private static final int HORIZONTAL_PADDING = 5;
	/** The floor padding compression stops at before the strip scrolls. */
	private static final int MIN_HORIZONTAL_PADDING = 1;
	private static final int TAB_GAP = 1;
	private static final int TEXT_Y_OFFSET = 2;
	private static final int BACKGROUND = 0x66000000;
	private static final int BACKGROUND_SELECTED = 0xAA000000;
	/** The GLFW modifier bit for shift in MouseEvent.modifiers(). */
	private static final int SHIFT_MODIFIER = 1;
	/** Height of the marker strip under a multi-send target tab. */
	private static final int TARGET_MARKER_HEIGHT = 1;
	private static final int TARGET_MARKER = 0xFF3FF5E0;

	/** The scroll arrows borrow the action buttons' grid and colors. */
	private static final int SCROLL_BUTTON_SIZE = ChatButtonBar.BUTTON_SIZE;
	private static final int SCROLL_BUTTON_GAP = ChatButtonBar.BUTTON_GAP;
	private static final int SCROLL_EDGE_INSET = 1;
	private static final int SCROLL_BUTTON_Y_OFFSET = 1;
	private static final int TAB_TO_ARROW_GAP = 2;
	/**
	 * Width the arrows own at the strip's right end whenever it
	 * overflows — both arrows' worth, so the tab window does not
	 * resize as individual arrows appear and disappear.
	 */
	private static final int SCROLL_RESERVE = TAB_TO_ARROW_GAP
			+ 2 * SCROLL_BUTTON_SIZE + SCROLL_BUTTON_GAP + SCROLL_EDGE_INSET;
	private static final int ICON = 0xFFAAAAAA;
	private static final int ICON_ACTIVE = 0xFFFFFFFF;

	private final List<Feed> tabs;
	private final List<String> labels;
	private final FeedSelection selection;
	private final Consumer<Feed> onSelect;
	private final Consumer<Feed> onToggleTarget;
	private final Predicate<Feed> isTargeted;
	private final Predicate<Feed> isUnread;
	private final ToIntFunction<String> measure;
	private TabLayout layout;
	private int maxWidth;
	private int scrollOffset;

	public ChatTabBar(Font font, int x, int y, int maxWidth, List<Feed> tabs,
			FeedSelection selection, Predicate<Feed> isTargeted, Consumer<Feed> onSelect,
			Consumer<Feed> onToggleTarget, Predicate<Feed> isUnread) {
		super(x, y, TAB_HEIGHT, TAB_HEIGHT, Component.literal("Chat tabs"));
		this.tabs = List.copyOf(tabs);
		this.labels = this.tabs.stream().map(Feed::label).toList();
		this.selection = selection;
		this.isTargeted = isTargeted;
		this.onSelect = onSelect;
		this.onToggleTarget = onToggleTarget;
		this.isUnread = isUnread;
		this.measure = font::width;
		this.maxWidth = maxWidth;
		setHeight(TAB_HEIGHT);
		relayout();
	}

	/** The tabs this bar shows, in display order. */
	public List<Feed> tabs() {
		return tabs;
	}

	/**
	 * Re-budgets the strip — the chat screen calls this when a search
	 * opens or closes beside it, and the strip reflows its padding and
	 * scroll position to the new width. The selected tab stays visible.
	 */
	public void setMaxWidth(int newMaxWidth) {
		if (newMaxWidth == maxWidth) {
			return;
		}
		maxWidth = newMaxWidth;
		relayout();
	}

	/**
	 * Whether the strip overflows and the point (in screen
	 * coordinates) lies inside its box — the screen's answer to "should
	 * the wheel scroll tabs instead of the chat log".
	 */
	public boolean wantsScroll(double x, double y) {
		return overflows() && super.isMouseOver(x, y);
	}

	/** Scrolls the strip by whole tabs, clamped to the last reachable tab. */
	public void scrollBy(int tabs) {
		if (!overflows()) {
			return;
		}
		scrollOffset = Math.clamp(scrollOffset + tabs, 0, layout.maxScrollOffset(tabBudget(), TAB_GAP));
	}

	@Override
	protected void extractWidgetRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY,
			float partialTick) {
		Feed selected = selection.selected();
		int selectedIndex = tabs.indexOf(selected);
		if (overflows() && selectedIndex >= 0) {
			scrollOffset = layout.revealOffset(selectedIndex, scrollOffset, tabBudget(), TAB_GAP);
		}
		int count = visibleCount();
		int left = getX();
		for (int i = scrollOffset; i < scrollOffset + count; i++) {
			Feed tab = tabs.get(i);
			boolean isSelected = tab.equals(selected);
			boolean targeted = isTargeted.test(tab);
			extractor.fill(left, getY(), left + layout.widths()[i], getY() + getHeight(),
					isSelected ? BACKGROUND_SELECTED : BACKGROUND);
			if (targeted) {
				extractor.fill(left, getY() + getHeight() - TARGET_MARKER_HEIGHT,
						left + layout.widths()[i], getY() + getHeight(), TARGET_MARKER);
			}
			ChatFormatting labelColor = labelColor(isSelected, targeted, isUnread.test(tab));
			Component label = Component.literal(layout.labels()[i]).withStyle(labelColor);
			// Centered: at natural padding this is the old left + pad,
			// and it stays centered when the padding compresses.
			int textX = left + (layout.widths()[i] - layout.textWidths()[i]) / 2;
			extractor.textRendererForWidget(this, GuiGraphicsExtractor.HoveredTextEffects.NONE)
					.accept(TextAlignment.LEFT, textX, getY() + TEXT_Y_OFFSET, label);
			left += layout.widths()[i] + TAB_GAP;
		}
		// Labels are never shortened, so a tab needs no tooltip; the
		// scroll arrows carry the only ones.
		if (overflows()) {
			drawScrollButtons(extractor, mouseX, mouseY);
		} else {
			setTooltip(null);
		}
	}

	/** The scroll arrows: quiet 9×9 squares at the strip's right end, shown while actionable. */
	private void drawScrollButtons(GuiGraphicsExtractor extractor, int mouseX, int mouseY) {
		int y = getY() + SCROLL_BUTTON_Y_OFFSET;
		int rightX = getX() + getWidth() - SCROLL_EDGE_INSET - SCROLL_BUTTON_SIZE;
		int leftX = rightX - SCROLL_BUTTON_GAP - SCROLL_BUTTON_SIZE;
		boolean canLeft = scrollOffset > 0;
		boolean canRight = scrollOffset < layout.maxScrollOffset(tabBudget(), TAB_GAP);
		int hovered = arrowAt(mouseX);
		int hoveredY = mouseY >= y && mouseY < y + SCROLL_BUTTON_SIZE ? hovered : -1;
		if (canLeft) {
			boolean active = hoveredY == 0;
			extractor.fill(leftX, y, leftX + SCROLL_BUTTON_SIZE, y + SCROLL_BUTTON_SIZE,
					active ? BACKGROUND_SELECTED : BACKGROUND);
			drawChevron(extractor, leftX, y, true, active ? ICON_ACTIVE : ICON);
		}
		if (canRight) {
			boolean active = hoveredY == 1;
			extractor.fill(rightX, y, rightX + SCROLL_BUTTON_SIZE, y + SCROLL_BUTTON_SIZE,
					active ? BACKGROUND_SELECTED : BACKGROUND);
			drawChevron(extractor, rightX, y, false, active ? ICON_ACTIVE : ICON);
		}
		if (hoveredY == 0 && canLeft) {
			setTooltip(Tooltip.create(Component.translatable("zea.chat.tabs.scroll_left")));
		} else if (hoveredY == 1 && canRight) {
			setTooltip(Tooltip.create(Component.translatable("zea.chat.tabs.scroll_right")));
		} else {
			setTooltip(null);
		}
	}

	/**
	 * A chevron of two-pixel diagonal strokes — the magnifier and
	 * cross of the action row speak the same chunky 1-pixel idiom.
	 */
	private static void drawChevron(GuiGraphicsExtractor extractor, int x, int y, boolean left, int color) {
		for (int row = 0; row < 5; row++) {
			int inset = Math.min(row, 4 - row); // 0,1,2,1,0 — the chevron's point
			int col = left ? 5 - inset : 3 + inset;
			extractor.fill(x + col, y + 2 + row, x + col + 2, y + 3 + row, color);
		}
	}

	@Override
	public void onClick(MouseButtonEvent event, boolean doubleClick) {
		int arrow = arrowAt(event.x());
		if (arrow == 0) {
			scrollBy(-1);
			return;
		}
		if (arrow == 1) {
			scrollBy(1);
			return;
		}
		int index = tabAt(event.x());
		if (index < 0) {
			return;
		}
		Feed tab = tabs.get(index);
		if ((event.modifiers() & SHIFT_MODIFIER) != 0) {
			onToggleTarget.accept(tab);
		} else {
			onSelect.accept(tab);
		}
	}

	@Override
	public void playDownSound(SoundManager soundManager) {
		// Tab selection is a quiet visual action, not a button press.
	}

	/** Never a keyboard-navigation target; see the class javadoc. */
	@Override
	public ComponentPath nextFocusPath(FocusNavigationEvent navigationEvent) {
		return null;
	}

	@Override
	public boolean isMouseOver(double x, double y) {
		return super.isMouseOver(x, y) && (tabAt(x) >= 0 || arrowAt(x) >= 0);
	}

	/**
	 * The label color of one tab. Pure, so the priority order — selected,
	 * then unread, then targeted, then idle — is pinned by tests without
	 * Minecraft: the selected tab stays white; a tab with unseen messages
	 * shows yellow (the unread indicator, which outranks the target color
	 * for the label); a multi-send target shows aqua; everything else gray.
	 */
	public static ChatFormatting labelColor(boolean selected, boolean targeted, boolean unread) {
		if (selected) {
			return ChatFormatting.WHITE;
		}
		if (unread) {
			return ChatFormatting.YELLOW;
		}
		return targeted ? ChatFormatting.AQUA : ChatFormatting.GRAY;
	}

	/** Recomputes the layout for the current maximum width and re-clamps the scroll. */
	private void relayout() {
		layout = TabLayout.compute(labels, maxWidth, HORIZONTAL_PADDING,
				MIN_HORIZONTAL_PADDING, TAB_GAP, measure);
		if (overflows()) {
			scrollOffset = Math.clamp(scrollOffset, 0, layout.maxScrollOffset(tabBudget(), TAB_GAP));
		} else {
			scrollOffset = 0;
		}
		setWidth(overflows() ? maxWidth : layout.total(TAB_GAP));
	}

	private boolean overflows() {
		return layout.total(TAB_GAP) > maxWidth;
	}

	/** The tabs' budget: the strip's maximum less the arrows' reserve once it overflows. */
	private int tabBudget() {
		return overflows() ? maxWidth - SCROLL_RESERVE : maxWidth;
	}

	private int visibleCount() {
		return overflows() ? layout.window(scrollOffset, tabBudget(), TAB_GAP) : tabs.size();
	}

	/** Index of the visible tab under {@code mouseX}, or -1 in a gap / past the end. */
	private int tabAt(double mouseX) {
		if (mouseX < getX() || mouseX >= getX() + getWidth()) {
			return -1;
		}
		int left = getX();
		for (int i = scrollOffset; i < scrollOffset + visibleCount(); i++) {
			int right = left + layout.widths()[i];
			if (mouseX >= left && mouseX < right) {
				return i;
			}
			left = right + TAB_GAP;
		}
		return -1;
	}

	/** The scroll arrow under {@code mouseX}: 0 left, 1 right, or -1 — only one that is showing. */
	private int arrowAt(double mouseX) {
		if (!overflows()) {
			return -1;
		}
		int rightX = getX() + getWidth() - SCROLL_EDGE_INSET - SCROLL_BUTTON_SIZE;
		int leftX = rightX - SCROLL_BUTTON_GAP - SCROLL_BUTTON_SIZE;
		if (scrollOffset > 0 && mouseX >= leftX && mouseX < leftX + SCROLL_BUTTON_SIZE) {
			return 0;
		}
		if (scrollOffset < layout.maxScrollOffset(tabBudget(), TAB_GAP)
				&& mouseX >= rightX && mouseX < rightX + SCROLL_BUTTON_SIZE) {
			return 1;
		}
		return -1;
	}

	@Override
	protected void updateWidgetNarration(net.minecraft.client.gui.narration.NarrationElementOutput output) {
		output.add(NarratedElementType.TITLE, createNarrationMessage());
	}

	@Override
	protected net.minecraft.network.chat.MutableComponent createNarrationMessage() {
		List<String> names = new ArrayList<>();
		for (Feed tab : tabs) {
			String name = tab.label();
			if (isUnread.test(tab)) {
				name += " " + Component.translatable("zea.chat.unread.narration").getString();
			}
			if (isTargeted.test(tab)) {
				name += " " + Component.translatable("zea.chat.multisend.narration_target").getString();
			}
			names.add(name);
		}
		return Component.translatable("gui.narrate.tabList",
				Component.literal(String.join(", ", names)));
	}
}
