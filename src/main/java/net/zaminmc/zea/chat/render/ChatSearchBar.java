package net.zaminmc.zea.chat.render;

import net.minecraft.client.gui.ComponentPath;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.navigation.FocusNavigationEvent;
import net.minecraft.network.chat.Component;

/**
 * The chat search field, matching Polyfrost's Chatting: a plain vanilla
 * edit box, one quarter of the screen wide, standing on the row above
 * the input (the tab strip's row) to the left of the chat action
 * buttons. It appears when a search opens — the search button, Ctrl+F
 * or the search hotkey — and its text filters the visible messages live.
 *
 * <p>Text editing, cursor and selection are all vanilla {@link EditBox}
 * behavior; the class only fixes the grid: Chatting's 12-pixel row
 * height and its 100-character query ceiling (the default 32 runs out
 * mid-name). No hint text — Chatting's field ships without one, and the
 * open search's purpose is obvious from the filtering it does.
 *
 * <p>Focus arrives programmatically — the toggle and the restore across
 * screen rebuilds both call {@code setFocused} directly — so the field
 * refuses keyboard focus navigation (Tab and the arrow keys, which
 * 26.3 screens use to move focus between widgets). As a navigation
 * target it would steal the caret from the chat input on every
 * history-recall keypress; refusing keeps typing uninterrupted, and
 * closing the search still returns focus to the input explicitly.
 */
public final class ChatSearchBar extends EditBox {
	/** Height of the search row; the chat screen positions the bar with it. */
	public static final int SEARCH_HEIGHT = 12;
	/** Chatting's ceiling for a query; the default 32 runs out mid-name. */
	public static final int MAX_QUERY_LENGTH = 100;

	public ChatSearchBar(Font font, int x, int y, int width) {
		super(font, x, y, width, SEARCH_HEIGHT, Component.empty());
		setMaxLength(MAX_QUERY_LENGTH);
	}

	/** Focus moves here only by explicit request; see the class javadoc. */
	@Override
	public ComponentPath nextFocusPath(FocusNavigationEvent navigationEvent) {
		return null;
	}
}
