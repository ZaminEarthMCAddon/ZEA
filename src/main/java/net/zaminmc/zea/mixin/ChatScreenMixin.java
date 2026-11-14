package net.zaminmc.zea.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.zaminmc.zea.ZeaClient;
import net.zaminmc.zea.chat.ChatSubsystem;
import net.zaminmc.zea.chat.feeds.Feed;
import net.zaminmc.zea.chat.feeds.FeedFilter;
import net.zaminmc.zea.chat.feeds.Feeds;
import net.zaminmc.zea.chat.feeds.MultiSendInteraction;
import net.zaminmc.zea.chat.feeds.MultiSendResult;
import net.zaminmc.zea.chat.feeds.SendTargets;
import net.zaminmc.zea.chat.feeds.WriteChannel;
import net.zaminmc.zea.chat.render.ChatButtonBar;
import net.zaminmc.zea.chat.render.ChatCopier;
import net.zaminmc.zea.chat.render.ChatSearchBar;
import net.zaminmc.zea.chat.render.ChatTabBar;
import net.zaminmc.zea.chat.render.ClearHistoryToast;
import net.zaminmc.zea.chat.render.CopyToast;
import net.zaminmc.zea.chat.render.MultiSendToast;
import net.zaminmc.zea.duck.ScreenWidgetAccess;
import net.zaminmc.zea.keybind.ZeaKeys;
import com.mojang.blaze3d.platform.InputConstants;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.stream.Collectors;

/**
 * Adds ZEA's chat-screen behavior: the chat tab strip (with shift-click
 * multi-send targeting), the action buttons at its right end (search
 * toggle, clear history — the placement Polyfrost's Chatting made the
 * reference for), the search field (Ctrl+F or the button), right-click
 * message copying, and multi-target send interception. Active only
 * while connected to EarthMC — elsewhere the vanilla chat is untouched.
 */
@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin {
	/** The alt modifier bit in the GLFW bitmask MouseEvent.modifiers() reports. */
	private static final int ALT_MODIFIER = 4;

	/** Vanilla draws the input background this far above the screen bottom. */
	private static final int INPUT_STRIP_HEIGHT = 14;

	/**
	 * The shortcut-key code for F. {@code KeyEvent.shortcutKey()} reports
	 * letters as their lowercase ASCII code — vanilla's own narrator
	 * hotkey compares {@code == 98} ('b'), its copy/paste helpers 99/118 —
	 * while {@code InputConstants.KEY_F} holds the HID usage code (9).
	 * Comparing against the HID constant never matches, which is why
	 * Ctrl+F could not open the search: the dev.24 live report.
	 */
	private static final int SHORTCUT_KEY_F = 'f';

	/**
	 * The chat action buttons and the tab strip share one row: the
	 * strip on the left (never past the chat panel's width), the
	 * buttons at the screen's right edge, flush with the input's right
	 * edge. The strip's maximum is reduced by what the buttons occupy
	 * so the two can never run under each other, even on narrow
	 * windows.
	 */
	private static final int BUTTON_ROW_RIGHT_MARGIN = 4;
	/** Breathing room between the tab strip and the action buttons. */
	private static final int BUTTON_ROW_GAP = 2;

	@Shadow
	protected EditBox input;

	/** The search field of the currently built screen; rebuilt on resize. */
	private ChatSearchBar zea$searchBar;

	@Inject(method = "init", at = @At("TAIL"))
	private void zea$addChatTabs(CallbackInfo ci) {
		ChatSubsystem chat = ZeaClient.chat();
		if (chat == null || !ZeaClient.lifecycle().onEarthMc()) {
			return;
		}
		ChatScreen screen = (ChatScreen) (Object) this;
		Minecraft minecraft = Minecraft.getInstance();
		FeedFilter filter = chat.feedFilter();

		boolean searchButton = ZeaClient.config().searchButtonEnabled();
		boolean clearButton = ZeaClient.config().clearHistoryButtonEnabled();
		int buttonRowWidth = ChatButtonBar.rowWidth(searchButton, clearButton);
		int stripReserve = buttonRowWidth == 0 ? 0
				: BUTTON_ROW_RIGHT_MARGIN + buttonRowWidth + BUTTON_ROW_GAP;

		ChatTabBar tabBar = new ChatTabBar(
				minecraft.font,
				2,
				screen.height - INPUT_STRIP_HEIGHT - ChatTabBar.TAB_HEIGHT,
				zea$stripMaxWidth(minecraft, screen) - stripReserve,
				Feeds.visibleTabs(ZeaClient.config().enabledExtraFeedIds()),
				chat.feedSelection(),
				feed -> MultiSendInteraction.isTargeted(
					chat.sendTargets(), ZeaClient.config().multiSendEnabled(), feed),
				feed -> {
					chat.feedSelection().select(feed);
					filter.apply(minecraft.gui.hud.getChat());
				},
				feed -> {
					MultiSendInteraction.ToggleOutcome outcome = MultiSendInteraction.toggleTarget(
						chat.sendTargets(), feed, ZeaClient.config().multiSendEnabled());
					MultiSendToast.showToggle(minecraft, outcome, feed.label());
				});
		// addRenderableWidget is protected on Screen and inherited, so it
		// cannot be @Shadowed here — ScreenMixin exposes it instead.
		((ScreenWidgetAccess) (Object) this).zea$addRenderableWidget(tabBar);

		if (buttonRowWidth > 0) {
			ChatButtonBar buttonBar = new ChatButtonBar(
					screen.width - BUTTON_ROW_RIGHT_MARGIN - buttonRowWidth,
					screen.height - INPUT_STRIP_HEIGHT - ChatTabBar.TAB_HEIGHT,
					searchButton,
					clearButton,
					() -> filter.searchQuery() != null,
					() -> zea$toggleSearch(screen, filter, minecraft),
					() -> zea$clearHistory(minecraft));
			((ScreenWidgetAccess) (Object) this).zea$addRenderableWidget(buttonBar);
		}

		zea$searchBar = new ChatSearchBar(
				minecraft.font,
				2,
				screen.height - INPUT_STRIP_HEIGHT - ChatTabBar.TAB_HEIGHT - ChatSearchBar.SEARCH_HEIGHT,
				screen.width - 4);
		// Restore an open search across screen rebuilds (window resize); the
		// value is set before the responder so restoring applies no filter.
		String openQuery = filter.searchQuery();
		if (openQuery != null) {
			zea$searchBar.setValue(openQuery);
			zea$searchBar.visible = true;
			// The search keeps the caret across the rebuild too: without
			// this, typing would silently go to the chat input while the
			// search bar shows its old query — and the focus-loss close
			// below would treat that as a finished search.
			zea$moveFocus(screen, zea$searchBar);
		}
		zea$searchBar.setResponder(text -> {
			filter.setSearchQuery(text);
			filter.apply(minecraft.gui.hud.getChat());
		});
		((ScreenWidgetAccess) (Object) this).zea$addRenderableWidget(zea$searchBar);
	}

	/**
	 * Ctrl+F toggles the search field. Escape closes an open search before
	 * it closes the screen; so does Enter while the search field is focused —
	 * the player has finished asking, and with the focus-loss close below,
	 * anything that hands the caret back to the input ends the search anyway,
	 * so the key does it outright instead of a frame later. Tab is consumed
	 * outright: 26.3 screens treat it as focus navigation, and even when that
	 * navigation finds no target (all of ZEA's chat widgets refuse it, and
	 * vanilla's input is guarded by canLoseFocus=false) the search for one
	 * still clears the screen's focus — after which typed characters reach
	 * no widget until the player clicks the input again. A chat box has no
	 * Tab semantics worth that trap.
	 */
	@Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
	private void zea$handleSearchKeys(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
		ChatSubsystem chat = ZeaClient.chat();
		if (chat == null || !ZeaClient.lifecycle().onEarthMc()) {
			return;
		}
		FeedFilter filter = chat.feedFilter();
		ChatScreen screen = (ChatScreen) (Object) this;
		Minecraft minecraft = Minecraft.getInstance();

		if (event.isCycleFocus()) {
			cir.setReturnValue(true);
			return;
		}
		if (event.hasControlDownWithQuirk() && event.shortcutKey() == SHORTCUT_KEY_F
				|| ZeaKeys.SEARCH_CHAT.matches(event)) {
			zea$toggleSearch(screen, filter, minecraft);
			cir.setReturnValue(true);
			return;
		}
		if (filter.searchQuery() != null && event.isEscape()) {
			zea$closeSearch(screen, filter, minecraft);
			cir.setReturnValue(true);
			return;
		}
		if (filter.searchQuery() != null && event.isConfirmation()
				&& zea$searchBar != null && screen.getFocused() == zea$searchBar) {
			zea$closeSearch(screen, filter, minecraft);
			cir.setReturnValue(true);
		}
	}

	/**
	 * Closing the chat screen closes the search too: the visible-message
	 * filter also affects the heads-up chat, which must never stay narrowed
	 * by a query the player cannot see.
	 */
	@Inject(method = "removed", at = @At("TAIL"))
	private void zea$closeSearchOnScreenClose(CallbackInfo ci) {
		ChatSubsystem chat = ZeaClient.chat();
		if (chat == null || !ZeaClient.lifecycle().onEarthMc()) {
			return;
		}
		FeedFilter filter = chat.feedFilter();
		if (filter.searchQuery() != null) {
			zea$closeSearch((ChatScreen) (Object) this, filter, Minecraft.getInstance());
		}
	}

	/**
	 * A search closes when the player clicks into the chat input — the
	 * "found it, now I reply" moment Chatting also treats as done. The
	 * check is polled from the render path (after the screen's focus
	 * machinery, never reentrantly inside a focus transfer) and closes
	 * only on the input specifically: focus landing on a tab, say, must
	 * keep the search open, because a search composes with the selected
	 * feed and checking another feed mid-search is a normal move.
	 */
	@Inject(method = "extractRenderState", at = @At("TAIL"))
	private void zea$closeSearchWhenTheInputTakesFocus(GuiGraphicsExtractor extractor, int mouseX, int mouseY,
			float partialTick, CallbackInfo ci) {
		ChatSubsystem chat = ZeaClient.chat();
		if (chat == null || !ZeaClient.lifecycle().onEarthMc()) {
			return;
		}
		FeedFilter filter = chat.feedFilter();
		if (filter.searchQuery() == null || zea$searchBar == null || zea$searchBar.isFocused()) {
			return;
		}
		ChatScreen screen = (ChatScreen) (Object) this;
		if (screen.getFocused() == input) {
			zea$closeSearch(screen, filter, Minecraft.getInstance());
		}
	}

	/**
	 * Right-click copies the message under the cursor; alt + right-click
	 * copies its formatted (legacy-coded) variant. Clicks that miss every
	 * message fall through to vanilla untouched.
	 */
	@Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
	private void zea$copyMessageUnderCursor(MouseButtonEvent event, boolean doubled,
			CallbackInfoReturnable<Boolean> cir) {
		if (event.button() != InputConstants.MOUSE_BUTTON_RIGHT) {
			return;
		}
		ChatSubsystem chat = ZeaClient.chat();
		if (chat == null || !ZeaClient.lifecycle().onEarthMc()) {
			return;
		}
		ChatScreen screen = (ChatScreen) (Object) this;
		Minecraft minecraft = Minecraft.getInstance();
		boolean formatted = (event.modifiers() & ALT_MODIFIER) != 0;
		ChatCopier.Result result = ChatCopier.copy(
				minecraft.gui.hud.getChat(),
				event.x(),
				event.y(),
				screen.height,
				formatted);
		if (result == ChatCopier.Result.NO_MESSAGE) {
			return;
		}
		CopyToast.show(minecraft, result);
		cir.setReturnValue(true);
	}

	/**
	 * A send with shift-click targets selected goes to those channels
	 * instead of the server's current one: one message per target,
	 * staggered by the configured interval. Slash commands stay vanilla
	 * — a player typing a command means the command. The interception
	 * mirrors what vanilla does next: the normalized text enters the
	 * up-arrow recall, and cancelling lets the caller's close-or-clear
	 * behavior run unchanged.
	 */
	@Inject(method = "handleChatInput", at = @At("HEAD"), cancellable = true)
	private void zea$sendToTargets(String text, boolean addRecent, CallbackInfo ci) {
		ChatSubsystem chat = ZeaClient.chat();
		if (chat == null || !ZeaClient.lifecycle().onEarthMc()) {
			return;
		}
		String message = ((ChatScreen) (Object) this).normalizeChatMessage(text);
		SendTargets targets = chat.sendTargets();
		if (!MultiSendInteraction.shouldIntercept(
			targets, ZeaClient.config().multiSendEnabled(), message)) {
		return;
		}
		Minecraft minecraft = Minecraft.getInstance();
		if (addRecent) {
			minecraft.gui.hud.getChat().addRecentChat(message);
		}
		if (chat.sendToTargets(targets, message) == MultiSendResult.DISPATCHING) {
			String destinations = targets.targets().stream()
				.map(WriteChannel::label)
				.collect(Collectors.joining(", "));
			MultiSendToast.showAccepted(minecraft, targets.targets().size(), destinations);
		}
		ci.cancel();
	}

	/**
	 * The widest the tab strip may be: the chat panel's on-screen width.
	 * In 26.3 that is {@code ChatComponent.getWidth(option)} — floor(option
	 * * 280 + 40) screen pixels, independent of chat scale (the renderer
	 * scales internally by 1/scale; verified against the bytecode). The
	 * screen bound stays for tiny windows.
	 */
	private static int zea$stripMaxWidth(Minecraft minecraft, ChatScreen screen) {
		int panelWidth = ChatComponent.getWidth(minecraft.options.chatWidth().get());
		return Math.min(screen.width - 4, panelWidth);
	}

	private void zea$openSearch(ChatScreen screen, FeedFilter filter, Minecraft minecraft) {
		filter.setSearchQuery("");
		if (zea$searchBar == null) {
			return;
		}
		zea$searchBar.visible = true;
		zea$searchBar.setValue("");
		zea$moveFocus(screen, zea$searchBar);
		filter.apply(minecraft.gui.hud.getChat());
	}

	/** Opens a closed search, closes an open one — the shared body of Ctrl+F and the search button. */
	private void zea$toggleSearch(ChatScreen screen, FeedFilter filter, Minecraft minecraft) {
		if (filter.searchQuery() == null) {
			zea$openSearch(screen, filter, minecraft);
		} else {
			zea$closeSearch(screen, filter, minecraft);
		}
	}

	/** Empties the visible chat and this server's saved history, then confirms what went. */
	private void zea$clearHistory(Minecraft minecraft) {
		ChatSubsystem chat = ZeaClient.chat();
		if (chat == null) {
			return;
		}
		chat.clearChatHistory();
		ClearHistoryToast.show(minecraft);
	}

	/**
	 * Moves screen focus to another widget. The vanilla chat input is
	 * built to never give focus up ({@code setCanLoseFocus(false)} in
	 * {@code ChatScreen.init}), so a plain {@code setFocused} would leave
	 * both edit boxes holding the focused flag — two carets and confused
	 * IME state. Letting the input unfocus for the duration of the move
	 * transfers focus for real; it goes back to refusing the moment it
	 * regains focus.
	 */
	private void zea$moveFocus(ChatScreen screen, EditBox target) {
		input.setCanLoseFocus(true);
		screen.setFocused(target);
		input.setCanLoseFocus(false);
	}

	private void zea$closeSearch(ChatScreen screen, FeedFilter filter, Minecraft minecraft) {
		if (zea$searchBar != null) {
			// Clears the value before the query: the responder would re-open it.
			zea$searchBar.setValue("");
			zea$searchBar.visible = false;
		}
		filter.setSearchQuery(null);
		if (zea$searchBar != null && screen.getFocused() == zea$searchBar) {
			screen.setFocused(input);
		}
		filter.apply(minecraft.gui.hud.getChat());
	}
}
