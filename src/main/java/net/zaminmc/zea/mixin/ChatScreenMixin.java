package net.zaminmc.zea.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.components.CommandSuggestions;
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
import net.zaminmc.zea.chat.feeds.ShopTransactionInput;
import net.zaminmc.zea.chat.feeds.WriteChannel;
import net.zaminmc.zea.chat.render.ChatButtonBar;
import net.zaminmc.zea.chat.render.ChatCopier;
import net.zaminmc.zea.chat.render.ChatSearchBar;
import net.zaminmc.zea.chat.render.ChatTabBar;
import net.zaminmc.zea.chat.render.ClearHistoryToast;
import net.zaminmc.zea.chat.render.CopyToast;
import net.zaminmc.zea.chat.render.MultiSendToast;
import net.zaminmc.zea.chat.render.PmSendIndicator;
import net.zaminmc.zea.chat.render.PrivateMessageToast;
import net.zaminmc.zea.chat.render.TradeQueuePanel;
import net.zaminmc.zea.chat.render.TradeStatusIndicator;
import net.zaminmc.zea.chat.render.TradeToast;
import net.zaminmc.zea.config.ZeaConfig;
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
 * multi-send targeting), the action buttons and search field laid out
 * like Polyfrost's Chatting — the search box a quarter of the screen
 * wide on the tab row, the 9×9 buttons right-aligned to the screen with
 * the rightmost starting 12 pixels in — right-click message copying,
 * and send interception: multi-target batches to shift-click targets,
 * single-channel routing to the selected tab's channel. Active only
 * while connected to EarthMC — elsewhere the vanilla chat is untouched.
 *
 * <p>The search behaves like Chatting's: it opens as a field on the tab
 * row (the search button, Ctrl+F or the hotkey) with the chat input
 * handing over the caret and vanilla's command suggestions silenced —
 * not only hidden, but unplugged, because the async completion callback
 * would reshow a hidden popup; it closes on the button again, on losing
 * focus (polled per frame, after the screen's focus machinery) and on
 * the screen going away, each close handing the caret back to the input
 * and re-arming the suggestions. One documented deviation from
 * Chatting: the field's space is not reserved while it is hidden.
 * Chatting can afford the reserve because its tabs sit under a wide
 * panel; ZEA's strip is clamped to the chat panel's width (320 px by
 * default) and the dev.34 reserve — a quarter of the screen for the box —
 * starved the strip to ~45 px, every tab truncated to "G…" (the dev.35
 * live report). The strip takes the full row while the search is closed
 * and reflows live when it opens, so the two never overlap and the tabs
 * stay readable. Focus that lands on ZEA's own row widgets (the tab
 * strip, the action buttons) does not close the search, because a search
 * composes with the selected feed and checking another feed mid-search
 * is a normal move — Chatting's tabs are not widgets, so its focus-loss
 * close never sees this case.
 *
 * <p>Focus discipline (the dev.36 live report: "chat gets de-focused
 * often"): every ZEA row widget overrides
 * {@code shouldTakeFocusAfterInteraction} to false, because 26.3's
 * container click handler moves screen focus onto whatever widget
 * consumed a click — clearing the chat input's focused flag and sending
 * every following keystroke nowhere until the input was clicked again.
 * ZEA's widgets are mouse-driven; the caret belongs to the input through
 * every tab click and button press. Tab itself is handled at the point
 * vanilla would start focus navigation, after command completion had
 * its chance (see {@code zea$consumeTabAfterCompletion}), so completion
 * works and the caret survives.
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
         * The chat action row: the tab strip on the left (never past the
         * chat panel's width), the search box and action buttons on the
         * right — the search box's right edge {@code n * 11 + 12} pixels
         * in from the screen edge and the buttons right-aligned with the
         * rightmost starting at {@code width - 12}, Chatting's exact
         * geometry (its 9-pixel buttons on an 11-pixel pitch, the box one
         * quarter of the screen wide). While a search is open the strip's
         * maximum is reduced by everything the box and the buttons
         * occupy, so the three can never run under each other, even on
         * narrow windows; while it is closed the strip keeps the row to
         * itself (minus the buttons) and the box is simply not there —
         * see the class javadoc for why the space is not reserved.
         */
        private static final int BUTTON_ROW_RIGHT_EDGE_MARGIN = 3;
        private static final int BUTTON_ROW_RIGHTMOST_LEFT = 12;
        /** Breathing room between the tab strip and the search box. */
        private static final int BUTTON_ROW_GAP = 2;
        /** The tab strip's left edge, flush with the input strip's background. */
        private static final int STRIP_X = 2;

        @Shadow
        protected EditBox input;

        @Shadow
        private CommandSuggestions commandSuggestions;

        /** The search field of the currently built screen; rebuilt on resize. */
        private ChatSearchBar zea$searchBar;
        /** The row widgets a search stays open across; see the class javadoc. */
        private ChatTabBar zea$tabBar;
        private ChatButtonBar zea$buttonBar;
        /** The trade pacing status line above the tab row; always built, shown when active. */
        private TradeStatusIndicator zea$tradeIndicator;
        /** The trade queue panel the status line opens; rebuilt with the screen. */
        private TradeQueuePanel zea$queuePanel;
        /** Whether the queue panel is open; resets when the screen rebuilds. */
        private boolean zea$queuePanelOpen;
        /** The PMs tab's Send-to tag on the input row; rebuilt with the screen. */
        private PmSendIndicator zea$pmIndicator;

        @Inject(method = "init", at = @At("TAIL"))
        private void zea$addChatTabs(CallbackInfo ci) {
                ChatSubsystem chat = ZeaClient.chat();
                if (chat == null || !ZeaClient.lifecycle().onEarthMc()) {
                        return;
                }
                ChatScreen screen = (ChatScreen) (Object) this;
                Minecraft minecraft = Minecraft.getInstance();
                FeedFilter filter = chat.feedFilter();
                int tabRowY = screen.height - INPUT_STRIP_HEIGHT - ChatTabBar.TAB_HEIGHT;

                boolean searchButton = ZeaClient.config().searchButtonEnabled();
                boolean clearButton = ZeaClient.config().clearHistoryButtonEnabled();
                int buttonCount = (searchButton ? 1 : 0) + (clearButton ? 1 : 0);
                int buttonRowWidth = ChatButtonBar.rowWidth(searchButton, clearButton);
                int searchBoxLeft = zea$searchBoxLeft(screen, buttonCount);
                int buttonRowLeft = buttonCount == 0 ? searchBoxLeft
                                : screen.width - BUTTON_ROW_RIGHTMOST_LEFT
                                                - (buttonCount - 1) * (ChatButtonBar.BUTTON_SIZE + ChatButtonBar.BUTTON_GAP);
                boolean searchOpen = filter.searchQuery() != null;

                ChatTabBar tabBar = new ChatTabBar(
                                minecraft.font,
                                STRIP_X,
                                tabRowY,
                                zea$stripMaxWidth(minecraft, screen, searchOpen, searchBoxLeft, buttonRowWidth),
                                Feeds.visibleTabs(ZeaClient.config().enabledExtraFeedIds()),
                                chat.feedSelection(),
                                feed -> MultiSendInteraction.isTargeted(
                                        chat.sendTargets(), ZeaClient.config().multiSendEnabled(), feed),
                                feed -> {
                                        chat.feedSelection().select(feed);
                                        chat.unreadTracker().markSelected(feed);
                                        filter.apply(minecraft.gui.hud.getChat());
                                },
                                feed -> {
                                        MultiSendInteraction.ToggleOutcome outcome = MultiSendInteraction.toggleTarget(
                                                chat.sendTargets(), feed, ZeaClient.config().multiSendEnabled());
                                        MultiSendToast.showToggle(minecraft, outcome, feed.label());
                                },
                                feed -> chat.unreadTracker().isUnread(feed));
                zea$tabBar = tabBar;
                // addRenderableWidget is protected on Screen and inherited, so it
                // cannot be @Shadowed here — ScreenMixin exposes it instead.
                ((ScreenWidgetAccess) (Object) this).zea$addRenderableWidget(tabBar);

                if (buttonRowWidth > 0) {
                        ChatButtonBar buttonBar = new ChatButtonBar(
                                        buttonRowLeft,
                                        tabRowY,
                                        searchButton,
                                        clearButton,
                                        () -> filter.searchQuery() != null,
                                        () -> zea$toggleSearch(screen, filter, minecraft),
                                        () -> zea$clearHistory(minecraft));
                        zea$buttonBar = buttonBar;
                        ((ScreenWidgetAccess) (Object) this).zea$addRenderableWidget(buttonBar);
                }

                zea$searchBar = new ChatSearchBar(
                                minecraft.font,
                                searchBoxLeft,
                                tabRowY,
                                screen.width / 4);
                // The box starts hidden — a fresh chat screen has no search —
                // and an open one is restored across the rebuild (window
                // resize); the value is set before the responder so restoring
                // applies no filter.
                String openQuery = filter.searchQuery();
                zea$searchBar.visible = openQuery != null;
                if (openQuery != null) {
                        zea$searchBar.setValue(openQuery);
                        // The search keeps the caret across the rebuild too: without
                        // this, typing would silently go to the chat input while the
                        // search bar shows its old query — and the focus-loss close
                        // below would treat that as a finished search. The suggestions
                        // stay unplugged for the same reason Chatting silences them
                        // on open: the async completion callback would reshow a
                        // hidden popup over the search.
                        commandSuggestions.setAllowSuggestions(false);
                        zea$moveFocus(screen, zea$searchBar);
                }
                zea$searchBar.setResponder(text -> {
                        filter.setSearchQuery(text);
                        filter.apply(minecraft.gui.hud.getChat());
                });
                ((ScreenWidgetAccess) (Object) this).zea$addRenderableWidget(zea$searchBar);

                // The trade pacing status line: one small yellow tag on its own
                // row above the strip, right-aligned. Built unconditionally and
                // hidden by default; the per-frame poll below flips it on the
                // moment the cooldown or queue says something. Clicking it opens
                // the queue panel below.
                zea$tradeIndicator = new TradeStatusIndicator(
                                screen.width - 2,
                                tabRowY - TradeStatusIndicator.HEIGHT,
                                minecraft.font,
                                ZeaClient.config()::tradePacingEnabled,
                                () -> chat.tradeGate().snapshot(System.currentTimeMillis()),
                                () -> zea$toggleQueuePanel());
                zea$tradeIndicator.visible = false;
                ((ScreenWidgetAccess) (Object) this).zea$addRenderableWidget(zea$tradeIndicator);

                // The queue panel: what the pacing holds, with delete buttons and
                // the auto-send toggle. Hidden until the status line opens it; the
                // open state resets on every rebuild (resize, reopen).
                zea$queuePanelOpen = false;
                zea$queuePanel = new TradeQueuePanel(
                                minecraft.font,
                                screen.width - 2,
                                tabRowY - TradeStatusIndicator.HEIGHT - 2,
                                () -> chat.tradeGate().queuedSnapshot(),
                                ZeaClient.config()::tradeAutoSend,
                                () -> zea$toggleTradeAutoSend(minecraft),
                                index -> chat.tradeGate().removeQueued(index),
                                () -> zea$closeQueuePanel());
                zea$queuePanel.visible = false;
                ((ScreenWidgetAccess) (Object) this).zea$addRenderableWidget(zea$queuePanel);

                // The PMs tab's Send-to tag: one plain text tag on the input's
                // row, at its right end, naming whom the tab's next send
                // would reach — the live reply target — or, before any PM
                // exchange, the session's recent frequent partners. Built
                // unconditionally and hidden by default; the per-frame poll
                // below flips it only while the PMs tab is selected and the
                // PM state has something to say. The input exists by now:
                // vanilla builds it in its own init, before this TAIL inject.
                zea$pmIndicator = new PmSendIndicator(
                                input.getX() + input.getWidth() + PmSendIndicator.GAP,
                                input.getY(),
                                input.getHeight(),
                                minecraft.font,
                                () -> ZeaClient.chat() == null ? null : ZeaClient.chat().privateMessaging(),
                                () -> {
                                        ChatSubsystem live = ZeaClient.chat();
                                        return live != null && Feeds.PRIVATE_MESSAGES.id()
                                                                        .equals(live.feedSelection().selected().id());
                                },
                                screen.width);
                zea$pmIndicator.visible = false;
                ((ScreenWidgetAccess) (Object) this).zea$addRenderableWidget(zea$pmIndicator);
        }

        /**
         * Ctrl+F toggles the search field. Escape closes an open search — or
         * the trade queue panel, whichever is on top — before it closes the
         * screen; Enter while the search field is focused closes the search —
         * the player has finished asking, and with the focus-loss close below,
         * anything that hands the caret back to the input ends the search anyway,
         * so the key does it outright instead of a frame later.
         *
         * <p>Tab is deliberately NOT consumed here. Vanilla's first act in
         * {@code keyPressed} is command-suggestion completion, and eating Tab
         * at the head killed it (the dev.36 live report: "tab-completion
         * doesn't really quite work"); the focus-cycle trap Tab used to spring
         * is defused one call later, at the injection point below.
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

                if (zea$queuePanelOpen && event.isEscape()) {
                        zea$closeQueuePanel();
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
         * Tab is consumed only after vanilla's command-suggestion completion
         * had its chance — this runs at the call to {@code Screen.keyPressed},
         * which sits after {@code commandSuggestions.keyPressed} in vanilla's
         * own order but before the screen's focus navigation. Reaching this
         * point means completion did not want the key (nothing to complete),
         * so Tab's only remaining effect would be 26.3's focus cycle: the
         * search for a focus target clears the screen's focus even when no
         * widget takes it, after which typed characters reach no widget
         * until the input is clicked again. Consumed here, completion keeps
         * working and the caret never dies.
         */
        @Inject(method = "keyPressed", at = @At(value = "INVOKE",
                        target = "Lnet/minecraft/client/gui/screens/Screen;keyPressed"
                                        + "(Lnet/minecraft/client/input/KeyEvent;)Z"),
                        cancellable = true)
        private void zea$consumeTabAfterCompletion(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
                if (ZeaClient.chat() == null || !ZeaClient.lifecycle().onEarthMc()) {
                        return;
                }
                if (event.isCycleFocus()) {
                        cir.setReturnValue(true);
                }
        }

        /**
         * Closing the chat screen closes the search too: the visible-message
         * filter also affects the heads-up chat, which must never stay narrowed
         * by a query the player cannot see. The screen is going away, so the
         * close stays light — no focus moves, no suggestion re-arming.
         */
        @Inject(method = "removed", at = @At("TAIL"))
        private void zea$closeSearchOnScreenClose(CallbackInfo ci) {
                ChatSubsystem chat = ZeaClient.chat();
                if (chat == null || !ZeaClient.lifecycle().onEarthMc()) {
                        return;
                }
                FeedFilter filter = chat.feedFilter();
                if (filter.searchQuery() != null && zea$searchBar != null) {
                        zea$searchBar.setValue("");
                        zea$searchBar.visible = false;
                        filter.setSearchQuery(null);
                        filter.apply(Minecraft.getInstance().gui.hud.getChat());
                }
        }

        /**
         * A search closes when its field loses focus — Chatting's rule,
         * polled per frame so it runs after the screen's focus machinery,
         * never reentrantly inside a focus transfer. The exception is
         * ZEA's own row widgets: focus landing on the tab strip or the
         * action buttons must keep the search open, because a search
         * composes with the selected feed and checking another feed mid-
         * search is a normal move (Chatting's tabs are drawn directly on
         * the screen and never take focus, so its close never sees this
         * case). Everything else — the chat input, a clicked message, no
         * focused widget at all — ends the search and hands the caret
         * back to the input, the "found it, now I reply" moment.
         */
        @Inject(method = "extractRenderState", at = @At("TAIL"))
        private void zea$closeSearchOnFocusLoss(GuiGraphicsExtractor extractor, int mouseX, int mouseY,
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
                Object focused = screen.getFocused();
                if (focused == zea$tabBar || focused == zea$buttonBar) {
                        return;
                }
                zea$closeSearch(screen, filter, Minecraft.getInstance());
        }

        /**
         * Flips the trade pacing status line's visibility per frame: it shows
         * while pacing is on and there is a cooldown or a queue to report.
         * The widget cannot poll itself back to life once invisible — its
         * render path only runs while visible — so the screen does it here,
         * the same per-frame hook that watches the search field's focus.
         * The queue panel rides along: open while the panel flag says so and
         * pacing stays on, so turning pacing off mid-inspection closes it.
         */
        @Inject(method = "extractRenderState", at = @At("TAIL"))
        private void zea$updateTradeIndicator(GuiGraphicsExtractor extractor, int mouseX, int mouseY,
                        float partialTick, CallbackInfo ci) {
                if (zea$tradeIndicator != null) {
                        zea$tradeIndicator.visible = zea$tradeIndicator.shouldShow();
                }
                if (zea$queuePanel != null) {
                        zea$queuePanel.visible = zea$queuePanelOpen
                                        && ZeaClient.config() != null
                                        && ZeaClient.config().tradePacingEnabled();
                }
        }

        /**
         * Flips the PMs tab's Send-to tag per frame: it shows while the
         * PMs tab is selected, the config keeps the indicator on, and the
         * PM state has something to name — the live reply target, or the
         * session's recent frequent partners before one exists. The widget
         * cannot poll itself back to life once invisible, so the screen
         * does it here, the same per-frame hook the other chat widgets ride.
         */
        @Inject(method = "extractRenderState", at = @At("TAIL"))
        private void zea$updatePmIndicator(GuiGraphicsExtractor extractor, int mouseX, int mouseY,
                        float partialTick, CallbackInfo ci) {
                if (zea$pmIndicator == null) {
                        return;
                }
                zea$pmIndicator.visible = ZeaClient.config() != null
                                && ZeaClient.config().pmSendIndicatorEnabled()
                                && zea$pmIndicator.shouldShow();
        }

        /**
         * Left-clicks land on the trade queue panel before vanilla turns
         * them on the chat underneath. Vanilla's chat screen handles
         * clickable message components ahead of its widgets, so without
         * this a click on the panel could open a link hidden under it;
         * the panel is drawn above the chat, so its clicks belong to it.
         * Right-clicks keep falling through — message copying stays.
         */
        @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
        private void zea$queuePanelClicksFirst(MouseButtonEvent event, boolean doubled,
                        CallbackInfoReturnable<Boolean> cir) {
                if (event.button() != InputConstants.MOUSE_BUTTON_LEFT
                                || zea$queuePanel == null || !zea$queuePanel.visible
                                || !zea$queuePanelOpen
                                || !zea$queuePanel.isMouseOver(event.x(), event.y())) {
                        return;
                }
                zea$queuePanel.mouseClicked(event, doubled);
                cir.setReturnValue(true);
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
         * Routes a send to the selected channels: with shift-click targets
         * selected the message goes to all of them (one per target,
         * staggered by the configured interval); otherwise a plain message
         * typed on a channel tab goes to that tab's channel —
         * {@code /global <msg>} on Global, {@code /nc <msg>} on Nation, and
         * so on — instead of to whatever channel the server currently has
         * the player writing to. The PMs tab sends {@code /r <msg>} once a
         * PM exchange exists; used before that, the send is refused with a
         * toast and never falls through to plain chat (the live crash
         * report's path — plain chat on a PMs tab went to the writing
         * channel and took the client down with it). On feeds without a
         * destination (events) nothing is intercepted and vanilla plain
         * chat stands.
         *
         * <p>Except when a shop transaction is pending: EarthMC's physical
         * shops collect the quantity as the next plain chat message ("Enter
         * in chat, how many you wish to BUY."), and channel routing would
         * turn that "16" into "/global 16" — a garbled channel message the
         * shop never reads. While {@link ShopTransactionInput} is armed, the
         * next eligible plain message is deliberately <b>not</b> intercepted
         * at all: falling through lets vanilla send it through
         * {@code connection.sendChat} unmodified, with no prefix, no
         * duplication and no recall of the shop state — the one-shot state
         * is consumed by the send itself. Slash commands are never shop
         * input and keep their normal pathway.
         *
         * <p>Slash commands stay vanilla in all other branches too — a
         * player typing a command means the command (and a manual
         * {@code /msg} still works: its echo lands on the PMs tab and
         * becomes the reply target). The interception mirrors what vanilla
         * does next: the normalized text enters the up-arrow recall, and
         * cancelling lets the caller's close-or-clear behavior run unchanged.
         */
        @Inject(method = "handleChatInput", at = @At("HEAD"), cancellable = true)
        private void zea$sendToTargets(String text, boolean addRecent, CallbackInfo ci) {
                ChatSubsystem chat = ZeaClient.chat();
                if (chat == null || !ZeaClient.lifecycle().onEarthMc()) {
                        return;
                }
                String message = ((ChatScreen) (Object) this).normalizeChatMessage(text);
                // The pending shop input outranks every routing: EarthMC's
                // physical shops collect the quantity as the next plain chat
                // message, and channel routing would turn that "16" into
                // "/global 16" — a garbled channel message the shop never
                // reads. Not cancelling hands the message to vanilla's own
                // send, exactly as typed; the one-shot state is consumed by
                // the send itself (ShopTransactionInput).
                if (chat.shopInput().shouldBypass(message, System.currentTimeMillis())) {
                        return;
                }
                SendTargets targets = chat.sendTargets();
                if (MultiSendInteraction.shouldIntercept(
                        targets, ZeaClient.config().multiSendEnabled(), message)) {
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
                        return;
                }
                if (message == null || message.isBlank() || message.startsWith("/")) {
                        return;
                }
                ChatSubsystem.ChannelSend outcome =
                                chat.sendToSelectedChannel(chat.feedSelection().selected(), message);
                if (outcome == ChatSubsystem.ChannelSend.ROUTED) {
                        if (addRecent) {
                                Minecraft.getInstance().gui.hud.getChat().addRecentChat(message);
                        }
                        ci.cancel();
                } else if (outcome == ChatSubsystem.ChannelSend.NO_PM_PARTNER) {
                        PrivateMessageToast.showNoPartner(Minecraft.getInstance());
                        ci.cancel();
                }
        }

        /**
         * The widest the tab strip may be. While the search is closed that
         * is the chat panel's on-screen width — in 26.3
         * {@code ChatComponent.getWidth(option)}, floor(option * 280 + 40)
         * screen pixels, independent of chat scale because the renderer
         * scales internally by 1/scale (verified against the bytecode) — so
         * the tabs align with the chat log, less what the action buttons
         * occupy at the screen's right. While a search is open the strip
         * spans the row up to the search box instead: the search row is a
         * screen-wide row — the vanilla input already spans it, and
         * Chatting's box sits beside its tabs the same way — and holding
         * the strip to the panel would starve it of everything the box
         * covers (the dev.35 lesson again).
         */
        private static int zea$stripMaxWidth(Minecraft minecraft, ChatScreen screen, boolean searchOpen,
                        int searchBoxLeft, int buttonRowWidth) {
                if (searchOpen) {
                        return Math.max(STRIP_X + 20, searchBoxLeft - 2 * BUTTON_ROW_GAP - STRIP_X);
                }
                int reserve = buttonRowWidth == 0 ? 0
                                : BUTTON_ROW_RIGHT_EDGE_MARGIN + buttonRowWidth + BUTTON_ROW_GAP;
                int panelWidth = ChatComponent.getWidth(minecraft.options.chatWidth().get());
                return Math.min(screen.width - BUTTON_ROW_RIGHT_EDGE_MARGIN, panelWidth) - reserve;
        }

        /**
         * The search box's left edge — Chatting's geometry: the box is a
         * quarter of the screen wide, its right edge sitting
         * {@code n * 11 + 12} pixels in from the screen's right.
         */
        private static int zea$searchBoxLeft(ChatScreen screen, int buttonCount) {
                int searchBoxWidth = screen.width / 4;
                return screen.width - searchBoxWidth
                                - (buttonCount * (ChatButtonBar.BUTTON_SIZE + ChatButtonBar.BUTTON_GAP) + 12);
        }

        /**
         * Re-budgets the tab strip for the search's new state — the box
         * just appeared or vanished from the row's right end. The strip
         * reflows its padding and scroll position itself, keeping the
         * selected tab in view.
         */
        private void zea$relayoutStrip(ChatScreen screen, Minecraft minecraft) {
                if (zea$tabBar == null) {
                        return;
                }
                ChatSubsystem chat = ZeaClient.chat();
                if (chat == null) {
                        return;
                }
                boolean searchButton = ZeaClient.config().searchButtonEnabled();
                boolean clearButton = ZeaClient.config().clearHistoryButtonEnabled();
                int buttonCount = (searchButton ? 1 : 0) + (clearButton ? 1 : 0);
                int buttonRowWidth = ChatButtonBar.rowWidth(searchButton, clearButton);
                boolean searchOpen = chat.feedFilter().searchQuery() != null;
                zea$tabBar.setMaxWidth(zea$stripMaxWidth(minecraft, screen, searchOpen,
                                zea$searchBoxLeft(screen, buttonCount), buttonRowWidth));
        }

        private void zea$openSearch(ChatScreen screen, FeedFilter filter, Minecraft minecraft) {
                filter.setSearchQuery("");
                if (zea$searchBar == null) {
                        return;
                }
                zea$searchBar.visible = true;
                zea$searchBar.setValue("");
                // Chatting's open sequence: the suggestion popup is not only
                // hidden but unplugged — the async completion callback would
                // otherwise reshow it over the search — and the input hands
                // the caret over through the move below.
                commandSuggestions.setAllowSuggestions(false);
                zea$moveFocus(screen, zea$searchBar);
                filter.apply(minecraft.gui.hud.getChat());
                zea$relayoutStrip(screen, minecraft);
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
         * The queue panel's auto-send row: flip auto-send, persist it, and
         * confirm with a toast — the one mid-conversation control the pacing
         * feature needs, without opening the settings.
         */
        private static void zea$toggleTradeAutoSend(Minecraft minecraft) {
                ZeaConfig config = ZeaClient.config();
                if (config == null) {
                        return;
                }
                config.chat.tradeChat.autoSend = !config.chat.tradeChat.autoSend;
                config.saveNow();
                if (minecraft.gui != null) {
                        TradeToast.showAutoSendToggled(minecraft, config.tradeAutoSend());
                }
        }

        /** The status line's click: show the held messages, or hide them again. */
        private void zea$toggleQueuePanel() {
                zea$queuePanelOpen = !zea$queuePanelOpen;
        }

        /** Closes the queue panel — its close button, or Escape. */
        private void zea$closeQueuePanel() {
                zea$queuePanelOpen = false;
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

        /**
         * Ends the search: clears the field and the query, hands the caret
         * back to the input — Chatting's close always refocuses the input,
         * the "found it, now I reply" moment — and re-arms vanilla's
         * command suggestions, refreshing them for the input's current
         * text so the popup can return on the next keystroke.
         */
        private void zea$closeSearch(ChatScreen screen, FeedFilter filter, Minecraft minecraft) {
                if (zea$searchBar != null) {
                        // Clears the value before the query: the responder would re-open it.
                        zea$searchBar.setValue("");
                        zea$searchBar.visible = false;
                }
                filter.setSearchQuery(null);
                input.setFocused(true);
                screen.setFocused(input);
                commandSuggestions.setAllowSuggestions(true);
                commandSuggestions.updateCommandInfo();
                filter.apply(minecraft.gui.hud.getChat());
                zea$relayoutStrip(screen, minecraft);
        }

        /**
         * The wheel over an overflowing tab strip scrolls the strip by one
         * tab before vanilla turns the same gesture on the chat log — the
         * strip is a small target and the arrows are not the only way in.
         * Vanilla's chat handler never forwards the wheel to screen
         * widgets, so this runs ahead of it: wheel up (away) scrolls left
         * toward the earlier tabs, wheel down scrolls right.
         */
        @Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true)
        private void zea$scrollTheTabStrip(double x, double y, double scrollX, double scrollY,
                        CallbackInfoReturnable<Boolean> cir) {
                if (zea$tabBar == null || ZeaClient.chat() == null || !ZeaClient.lifecycle().onEarthMc()) {
                        return;
                }
                if (zea$tabBar.wantsScroll(x, y)) {
                        zea$tabBar.scrollBy(scrollY > 0 ? -1 : 1);
                        cir.setReturnValue(true);
                }
        }
}
