package net.zaminmc.zea.chat;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.zaminmc.zea.ZeaClient;
import net.zaminmc.zea.chat.capture.CaptureRecorder;
import net.zaminmc.zea.chat.capture.MessageCapture;
import net.zaminmc.zea.chat.classify.ClassificationCache;
import net.zaminmc.zea.chat.classify.ClassificationPipeline;
import net.zaminmc.zea.chat.feeds.ChannelState;
import net.zaminmc.zea.chat.feeds.Feed;
import net.zaminmc.zea.chat.feeds.FeedFilter;
import net.zaminmc.zea.chat.feeds.FeedSelection;
import net.zaminmc.zea.chat.feeds.Feeds;
import net.zaminmc.zea.chat.feeds.GlobalVisibility;
import net.zaminmc.zea.chat.feeds.MultiChannelDispatcher;
import net.zaminmc.zea.chat.feeds.MultiSendInteraction;
import net.zaminmc.zea.chat.feeds.MultiSendResult;
import net.zaminmc.zea.chat.feeds.OutgoingMessage;
import net.zaminmc.zea.chat.feeds.OutgoingRouter;
import net.zaminmc.zea.chat.feeds.PrivateMessaging;
import net.zaminmc.zea.chat.feeds.SendTargets;
import net.zaminmc.zea.chat.feeds.ShopTransactionInput;
import net.zaminmc.zea.chat.feeds.TradeGate;
import net.zaminmc.zea.chat.feeds.UnreadTracker;
import net.zaminmc.zea.chat.feeds.WriteChannel;
import net.zaminmc.zea.chat.history.ChatHistory;
import net.zaminmc.zea.chat.history.HistoryEntry;
import net.zaminmc.zea.chat.history.HistoryRecorder;
import net.zaminmc.zea.chat.model.CapturedMessage;
import net.zaminmc.zea.chat.model.ClassifiedMessage;
import net.zaminmc.zea.chat.model.MessageCategory;
import net.zaminmc.zea.chat.model.PrivateMessage;
import net.zaminmc.zea.chat.model.TaggedChannelChat;
import net.zaminmc.zea.chat.model.TradeWait;
import net.zaminmc.zea.chat.model.WritingChannelReport;
import net.zaminmc.zea.chat.render.MultiSendToast;
import net.zaminmc.zea.chat.render.TradeToast;
import net.zaminmc.zea.config.ZeaConfig;
import net.zaminmc.zea.lifecycle.ClientLifecycle;
import net.zaminmc.zea.mention.MentionAutoResponder;
import net.zaminmc.zea.mention.Mentions;
import net.zaminmc.zea.notify.HudNotificationSurface;
import net.zaminmc.zea.notify.Notifications;
import net.zaminmc.zea.notify.UiNotificationSound;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * Coordinates the chat subsystem: capture, classification, channel state,
 * conservative outgoing dispatch and notifications. Wiring only — behavior
 * lives in the classify/feeds/notify packages; presentation (tabs,
 * rendering) subscribes to the classified stream.
 */
public final class ChatSubsystem {
        private final ClassificationPipeline classification;
        private final ClassificationCache classifications;
        private final ZeaConfig config;
        private final OutgoingRouter router = new OutgoingRouter();
        private final ChannelState channelState = new ChannelState();
        private final FeedSelection feedSelection = new FeedSelection(Feeds.GLOBAL);
        private final SendTargets sendTargets = new SendTargets();
        private final FeedFilter feedFilter;
        private final MultiChannelDispatcher dispatcher;
        private final Notifications notifications;
        private final Mentions mentions;
        private final MentionAutoResponder autoResponder;
        private final ChatHistory history;
        private final HistoryRecorder historyRecorder;
        private final CaptureRecorder capture;
        private final UnreadTracker unread;
        private final TradeGate tradeGate;
        private final ShopTransactionInput shopInput = new ShopTransactionInput();
        private final PrivateMessaging privateMessaging = new PrivateMessaging();

        public ChatSubsystem(ClientLifecycle lifecycle, ClassificationPipeline classification, ZeaConfig config,
                        CaptureRecorder capture, Path historyDirectory, Executor historyExecutor) {
                this.classification = classification;
                // 2048 keeps the remembered classifications covering the chat
                // store even when a history mod widens it far past vanilla's
                // 100-message window (the live pack runs MoreChatHistory): a
                // cache smaller than the store re-runs the classification
                // pipeline inside every tab switch's rebuild — once per
                // overflowed message, per switch — which the dev.24 session
                // felt as lag and freezes while switching feeds.
                this.classifications = new ClassificationCache(classification, 2048);
                this.config = config;
                this.capture = capture;
                // Constructed here — after the cache exists — never in a
                // field initializer: initializers run before this body and
                // would read the still-null field through classifications()
                // (the dev.23 live crash; ChatSubsystemWiringTest pins the
                // compiled order).
                this.feedFilter = new FeedFilter(this.classifications, this.feedSelection,
                                config::globalVisibility, config::tradePacingEnabled);
                this.dispatcher = new MultiChannelDispatcher(
                                this::sendCommand,
                                new DispatchFeedback(),
                                config.dispatchIntervalMillis());
                this.notifications = new Notifications(new HudNotificationSurface(), config, new UiNotificationSound());
                this.autoResponder = new MentionAutoResponder(config,
                                message -> dispatcher.dispatch(List.of(message)));
                this.history = new ChatHistory(config.historyMaxEntries());
                this.historyRecorder = new HistoryRecorder(
                                history, config, historyExecutor, System::currentTimeMillis,
                                Minecraft.getInstance()::execute);
                this.mentions = new Mentions(
                                () -> Minecraft.getInstance().getUser().getName(),
                                this.notifications,
                                new UiNotificationSound(),
                                config,
                                autoResponder);
                // Unread, trade pacing and PM replies are display/flow state,
                // not wiring: built after the collaborators they read.
                this.unread = new UnreadTracker();
                this.tradeGate = new TradeGate();

                ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
                        // Registered after ClientLifecycle (constructed first in
                        // ZeaClient), so the identity is already resolved here.
                        lifecycle.earthMcServer()
                                        .ifPresent(server -> historyRecorder.start(historyDirectory, server));
                });
                ClientTickEvents.END_CLIENT_TICK.register(client -> {
                        dispatcher.tick(System.currentTimeMillis());
                        notifications.tick(System.currentTimeMillis());
                        historyRecorder.tick();
                        tickTradePacing();
                });
                ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
                        dispatcher.cancel();
                        notifications.clear();
                        mentions.highlight().clear();
                        autoResponder.reset();
                        channelState.clear();
                        // Flush the session's history, then end it; the next EarthMC
                        // join reloads the per-server file.
                        historyRecorder.stop();
                        // Display state is EarthMC context; leaving resets it so the
                        // vanilla chat is untouched on other servers.
                        feedSelection.select(Feeds.GLOBAL);
                        sendTargets.clear();
                        unread.clear();
                        tradeGate.clear();
                        shopInput.clear();
                        privateMessaging.clear();
                        feedFilter.setSearchQuery(null);
                        feedFilter.apply(client.gui.hud.getChat());
                });

                new MessageCapture(lifecycle::onEarthMc, this::onCaptured);
        }

        /** Classified stream entry point; feeds, presentation and notifications subscribe here. */
        private void onCaptured(CapturedMessage captured) {
                // The shop quantity prompt arms before anything else sees the
                // message: display filters may hide the line later, but the
                // next typed message must still go out as the raw shop input.
                if (ShopTransactionInput.isQuantityPrompt(captured.text())) {
                        shopInput.arm(System.currentTimeMillis());
                }
                ClassifiedMessage classified = classifications.classify(captured);
                if (classified.payload() instanceof WritingChannelReport report) {
                        channelState.applyReport(report);
                }
                // The server's trade-cooldown report syncs the pacing window; the
                // line itself never reaches the history — the client has already
                // answered it (the rejected message queued, the countdown shown).
                boolean tradeWaitLine = classified.payload() instanceof TradeWait;
                if (classified.payload() instanceof TradeWait waitLine) {
                        tradeGate.onServerWait(System.currentTimeMillis(), waitLine.remainingMillis());
                }
                // The player's own trade message echoing back resolves the oldest
                // unresolved send as accepted. Display names can be nicknames, so
                // the gate also settles old entries by time; this is the fast path.
                if (classified.category() == MessageCategory.TRADE
                                && classified.payload() instanceof TaggedChannelChat chat
                                && chat.displayName().equals(ownName())) {
                        tradeGate.onOwnTradeAccepted();
                }
                if (classified.payload() instanceof PrivateMessage pm) {
                        privateMessaging.onPrivateMessage(pm);
                }
                notifications.onClassified(classified, System.currentTimeMillis());
                mentions.onClassified(classified, System.currentTimeMillis());
                unread.onMessage(classified, feedSelection.selected(),
                                Feeds.visibleTabs(config.enabledExtraFeedIds()), config.globalVisibility());
                if (!captured.text().isBlank() && !tradeWaitLine) {
                        history.append(HistoryEntry.of(classified));
                        historyRecorder.markDirty();
                }
                ZeaClient.LOGGER.debug("Classified [{}] {}", classified.category(), captured.text());
        }

        /** The local player's account name; the trade echo check. */
        private static String ownName() {
                return Minecraft.getInstance().getUser().getName();
        }

        /**
         * Sends one message to the shift-click selected channels. Refused
         * unless the user enabled multi-channel sending; accepted batches
         * dispatch one message at a time, spaced by the configured interval.
         */
        public MultiSendResult sendToTargets(SendTargets targets, String message) {
                if (!config.multiSendEnabled()) {
                        return MultiSendResult.DISABLED;
                }
                if (targets == null || targets.isEmpty()) {
                        return MultiSendResult.NO_TARGETS;
                }
                dispatcher.dispatch(router.route(targets.targets(), message));
                return MultiSendResult.DISPATCHING;
        }

        /** What {@link #sendToSelectedChannel} decided for one plain send. */
        public enum ChannelSend {
                /** The message was routed to the tab's destination. */
                ROUTED,
                /** The tab has no destination — vanilla plain chat stands. */
                NO_CHANNEL,
                /** The PMs tab was used before any PM arrived — nothing was sent. */
                NO_PM_PARTNER
        }

        /**
         * Sends one message to the feed's own destination — the dev.26 live
         * request: a plain message typed while a channel tab is selected
         * must go to that channel ({@code /global <msg>} on the Global tab,
         * {@code /nc <msg>} on Nation, ...), not to whatever channel the
         * server currently has the player writing to. The PMs tab sends
         * {@code /r <msg>} — but only once a PM exchange exists
         * ({@link PrivateMessaging}); before that the send is refused, never
         * dropped into vanilla plain chat, which would write to the writing
         * channel instead of a player (the live crash report's path — the
         * PMs tab no longer falls through at all). Event feeds without a
         * destination return {@link ChannelSend#NO_CHANNEL} and vanilla's
         * plain chat stands. Slash commands never reach here — the chat
         * screen intercepts only non-command text. Single-destination routing
         * is core tab behavior, deliberately not gated behind the multi-send
         * setting: the dispatch still goes through the interval-spaced
         * queue, so a rapid follow-up can never race the previous send.
         */
        public ChannelSend sendToSelectedChannel(Feed feed, String message) {
                if (message == null || message.isBlank()) {
                        return ChannelSend.NO_CHANNEL;
                }
                if (Feeds.PRIVATE_MESSAGES.equals(feed)) {
                        if (privateMessaging.partner().isEmpty()) {
                                return ChannelSend.NO_PM_PARTNER;
                        }
                        dispatcher.dispatch(List.of(new OutgoingMessage("r", message)));
                        return ChannelSend.ROUTED;
                }
                WriteChannel channel = MultiSendInteraction.channelOf(feed);
                if (channel == null) {
                        return ChannelSend.NO_CHANNEL;
                }
                dispatcher.dispatch(List.of(router.route(channel, message)));
                return ChannelSend.ROUTED;
        }

        public ChannelState channelState() {
                return channelState;
        }

        public ClassificationCache classifications() {
                return classifications;
        }

        public FeedSelection feedSelection() {
                return feedSelection;
        }

        /** The shift-click selected multi-send destinations; cleared on disconnect. */
        public SendTargets sendTargets() {
                return sendTargets;
        }

        public FeedFilter feedFilter() {
                return feedFilter;
        }

        /** Which visible tabs hold unseen messages; drives the yellow labels. */
        public UnreadTracker unreadTracker() {
                return unread;
        }

        /** The trade channel's pacing state; see {@link TradeGate}. */
        /** The one-shot pending shop-quantity state; see {@link ShopTransactionInput}. */
        public ShopTransactionInput shopInput() {
                return shopInput;
        }

        public TradeGate tradeGate() {
                return tradeGate;
        }

        /** Whom the PMs tab would reply to; see {@link PrivateMessaging}. */
        public PrivateMessaging privateMessaging() {
                return privateMessaging;
        }

        public MultiChannelDispatcher dispatcher() {
                return dispatcher;
        }

        public Notifications notifications() {
                return notifications;
        }

        public Mentions mentions() {
                return mentions;
        }

        /** The session's bounded chat history. */
        public ChatHistory history() {
                return history;
        }

        /**
         * Clears the chat the player sees together with the history
         * remembered for this server — persistent store included, so the
         * cleared messages do not return on the next join. The up-arrow
         * sent-message recall deliberately survives: the 26.3
         * {@code clearMessages} boolean resets the recall back to raw
         * command history, and wiping what the player typed is not what
         * "clear the chat" asks for.
         */
        public void clearChatHistory() {
                Minecraft minecraft = Minecraft.getInstance();
                if (minecraft.gui != null) {
                        minecraft.gui.hud.getChat().clearMessages(false);
                }
                historyRecorder.clear();
        }

        /** Flushes pending history writes; called before the executor closes. */
        public void shutdownHistory() {
                historyRecorder.flush();
        }

        private void sendCommand(OutgoingMessage message) throws Exception {
                ClientPacketListener connection = Minecraft.getInstance().getConnection();
                if (connection == null) {
                        throw new IllegalStateException("not connected to a server");
                }
                connection.sendCommand(message.commandLine());
        }

        /**
         * The pacing tick: resolves sends the server stopped answering
         * about, then drains the queue — one tick polls the gate, and when
         * the server-reported window has expired with messages waiting,
         * sends the oldest through the same choke point every trade send
         * passes — the send command mixin — so its outcome is tracked like
         * any other send: accepted, or refused with a fresh wait line that
         * re-syncs the window and re-queues it. The toast says what left
         * and what still waits; a disconnect between polls keeps the
         * message queued (the whole queue clears on disconnect anyway).
         */
        private void tickTradePacing() {
                if (!config.tradePacingEnabled() || !config.tradeAutoSend()) {
                        return;
                }
                ClientPacketListener connection = Minecraft.getInstance().getConnection();
                if (connection == null) {
                        return;
                }
                long now = System.currentTimeMillis();
                tradeGate.settleUnresolved(now);
                String due = tradeGate.pollDue(now);
                if (due == null) {
                        return;
                }
                connection.sendCommand(due);
                Minecraft minecraft = Minecraft.getInstance();
                if (minecraft.gui != null) {
                        TradeToast.showAutoSent(minecraft, tradeGate.queuedCount());
                }
        }

        /**
         * Dispatch feedback: every outcome is logged; failures additionally
         * toast, because a stopped multi-send otherwise looks like the server
         * ignoring the player. Toasts — not chat lines — because the chat HUD
         * is feed-filtered and would hide them on a channel tab.
         */
        private static final class DispatchFeedback implements MultiChannelDispatcher.Listener {
                @Override
                public void onDispatched(OutgoingMessage message, int remaining) {
                        ZeaClient.LOGGER.debug("Dispatched to {} ({} remaining)", message.command(), remaining);
                }

                @Override
                public void onFailed(OutgoingMessage message, Throwable cause, int cancelled) {
                        ZeaClient.LOGGER.warn("Dispatch to {} failed, cancelled {} remaining: {}",
                                        message.command(), cancelled, cause.toString());
                        Minecraft minecraft = Minecraft.getInstance();
                        if (minecraft.gui != null) {
                                MultiSendToast.showDispatchFailed(minecraft, message.command(), cancelled);
                        }
                }

                @Override
                public void onCancelled(int remaining) {
                        // Disconnect and dispatch replacement both land here; the
                        // disconnect reset already explains itself, a replaced batch is
                        // immediately superseded by its acceptance toast.
                        ZeaClient.LOGGER.info("Cancelled multi-channel dispatch ({} remaining)", remaining);
                }
        }
}
