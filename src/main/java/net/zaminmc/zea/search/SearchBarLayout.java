package net.zaminmc.zea.search;

import net.minecraft.client.gui.components.AbstractWidget;

/**
 * The shared layout math for the inventory search bar — the one place
 * that answers where the bar sits for a screen. The runtime container
 * mixin and any preview (the button editor's canvas) both go through
 * these functions, so a preview shows the exact rectangle the bar
 * occupies during real play; a per-screen offset can never drift back
 * in.
 *
 * <p>The anchor is NEU's: the search bar is a screen element, not a GUI
 * attachment — the group of settings button, bar and search-mode button
 * sits at the bottom-center of the screen, its bottom edge
 * {@link #BOTTOM_MARGIN} above the screen's bottom edge
 * (NEU's {@code BOTMID} anchor with {@code -searchBarYOffset}). The bar
 * itself is {@link #BAR_WIDTH} GUI pixels wide (NEU's default
 * {@code searchBarWidth}) and the buttons are square plates the height
 * of the bar (NEU's {@code quickcommand_background} buttons, sized
 * {@code searchBarYSize + 2 * padding}); the children sit
 * {@link #GROUP_PADDING} apart (NEU's {@code paddingUnscaled * 4}).
 *
 * <p>The bar's height follows NEU's {@code getSearchBarYSize()}:
 * {@code max(searchBarHeight / scaleFactor, ITEM_SIZE)} with the default
 * {@code searchBarHeight} of 40 raw pixels — the bar keeps a roughly
 * constant on-screen size at every GUI scale.
 */
public final class SearchBarLayout {
	private SearchBarLayout() {
	}

	/** NEU's default {@code toolbar.searchBarWidth}, in GUI pixels. */
	public static final int BAR_WIDTH = 200;

	/**
	 * The bar's horizontal text inset — NEU draws the value five
	 * pixels from the bar's left edge.
	 */
	public static final int TEXT_INSET = 5;

	/** The distance from the screen's bottom edge — NEU's searchBarYOffset. */
	public static final int BOTTOM_MARGIN = 10;

	/** The gap between the group's children — NEU's {@code paddingUnscaled * 4}. */
	public static final int GROUP_PADDING = 4;

	/** NEU's default {@code toolbar.searchBarHeight}, raw pixels. */
	public static final int RAW_HEIGHT = 40;

	/** The bar's height at a GUI scale factor — NEU's getSearchBarYSize. */
	public static int height(int scaleFactor) {
		return Math.max(RAW_HEIGHT / Math.max(1, scaleFactor), 16);
	}

	/** The flanking buttons' padding — NEU's {@code getPaddingUnscaled}. */
	public static int buttonPadding(int scaleFactor) {
		return Math.max(1, 2 / Math.max(1, scaleFactor));
	}

	/** One flanking button's side — the bar's height plus its padding pair. */
	public static int buttonSize(int scaleFactor) {
		return height(scaleFactor) + 2 * buttonPadding(scaleFactor);
	}

	/** The whole group's width: bar plus both buttons plus the gaps. */
	public static int groupWidth(int scaleFactor) {
		return BAR_WIDTH + 2 * (buttonSize(scaleFactor) + GROUP_PADDING);
	}

	/** The group's left edge — the bottom-centered group's start. */
	public static int groupLeft(int screenWidth, int scaleFactor) {
		return Math.max(0, (screenWidth - groupWidth(scaleFactor)) / 2);
	}

	/**
	 * The bar's left edge for a screen at a GUI scale factor — between
	 * the settings button and the search bar.
	 */
	public static int left(int screenWidth, int scaleFactor) {
		return groupLeft(screenWidth, scaleFactor) + buttonSize(scaleFactor) + GROUP_PADDING;
	}

	/** The bar's top edge — the group's bottom pinned above the screen's bottom. */
	public static int top(int screenHeight, int scaleFactor) {
		return Math.max(0, screenHeight - BOTTOM_MARGIN - height(scaleFactor));
	}

	/**
	 * The settings button's rectangle — the plate left of the bar.
	 * {@code {x, y, w, h}}.
	 */
	public static int[] settingsButton(int screenWidth, int screenHeight, int scaleFactor) {
		int size = buttonSize(scaleFactor);
		return new int[] {groupLeft(screenWidth, scaleFactor),
				top(screenHeight, scaleFactor) - buttonPadding(scaleFactor),
				size, size};
	}

	/**
	 * The search-mode button's rectangle — the plate right of the bar.
	 * {@code {x, y, w, h}}.
	 */
	public static int[] modeButton(int screenWidth, int screenHeight, int scaleFactor) {
		int size = buttonSize(scaleFactor);
		int barRight = left(screenWidth, scaleFactor) + BAR_WIDTH;
		return new int[] {barRight + GROUP_PADDING,
				top(screenHeight, scaleFactor) - buttonPadding(scaleFactor),
				size, size};
	}

	/**
	 * Applies the layout to a widget for the given screen — the single
	 * call the runtime mixin and any preview use.
	 */
	public static void apply(AbstractWidget widget, int screenWidth, int screenHeight,
			int scaleFactor) {
		widget.setX(left(screenWidth, scaleFactor));
		widget.setY(top(screenHeight, scaleFactor));
		widget.setWidth(BAR_WIDTH);
		widget.setHeight(height(scaleFactor));
	}
}
