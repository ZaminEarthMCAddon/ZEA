package net.zaminmc.zea.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.zaminmc.zea.ZeaClient;
import net.zaminmc.zea.chat.ChatSubsystem;
import net.zaminmc.zea.chat.feeds.FeedFilter;
import net.zaminmc.zea.chat.links.SpawnCommandLinks;
import net.zaminmc.zea.chat.model.MessageCategory;
import net.zaminmc.zea.chat.render.ChannelTint;
import net.zaminmc.zea.chat.render.ChatGeometry;
import net.zaminmc.zea.chat.render.ChatScrolling;
import net.zaminmc.zea.chat.render.SmoothChat;
import net.zaminmc.zea.chat.render.SplitLineCache;
import org.joml.Matrix3x2fStack;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

import java.util.List;
import java.util.function.Predicate;

/**
 * The chat's motion, matching Polyfrost's Chatting:
 *
 * <p><b>Smooth chat (arrival)</b> — each arriving message opens one
 * animation window: the whole chat starts one line lower and glides up
 * (quartic ease-out) while the message's wrapped lines fade in. The
 * offset stays at zero while the player is scrolled up — vanilla pins
 * the view there — but the fade still applies, so new lines appear
 * gently in place. Back-to-back messages restart the window, Chatting's
 * exact model; the fade set is keyed on the wrapped content instances,
 * so a rebuild re-adding the whole history counts as none of them new.
 *
 * <p><b>Smooth scrolling</b> — the scroll position the renderer uses is
 * an animated float chasing the vanilla anchor (quadratic ease-out,
 * armed only by real scroll events, single-notch scrolls snapping and
 * multi-line jumps gliding), so wheel and key scrolling glide the way
 * Chatting's does. The animation moves content inside the chat's own
 * clip — nothing outside the chat element moves (the dev.26 drift that
 * killed the previous pose-translate experiment cannot recur). The
 * anchor itself stays the source of truth: message pinning, the
 * "new messages" flag and the anchor writes all keep working on the
 * integer; only the three render reads ({@code forEachLine}'s two, the
 * scrollbar's one) see the eased value.
 *
 * <p>All hooks are EarthMC-gated and configurable, so everywhere else
 * vanilla renders untouched. Click and hover hit regions are computed
 * from the eased scroll position too (the click-capture path flows
 * through the same reads), so they track what the player sees.
 */
@Mixin(ChatComponent.class)
public abstract class ChatComponentMixin {
        @Shadow
        private int chatScrollbarPos;

        @Shadow
        private boolean newMessageSinceScroll;

        @Shadow
        private double getScale() {
                throw new AssertionError();
        }

        @Shadow
        private void addMessageToQueue(GuiMessage message) {
                throw new AssertionError();
        }

        @Shadow
        public void scrollChat(int direction) {
                throw new AssertionError();
        }

        private final SmoothChat zea$smoothChat = new SmoothChat();
        private final ChatScrolling zea$scrolling = new ChatScrolling();
        /** Memoized line wraps for the display rebuilds; see {@link SplitLineCache}. */
        private final SplitLineCache zea$splitLines = new SplitLineCache(2048);
        /** Set while addMessageToDisplayQueue runs, to skip its internal pin scroll. */
        private boolean zea$inAddMessage;
        /** Set while refreshTrimmedMessages rebuilds the display list. */
        private boolean zea$inRefresh;
        /** The anchor before the add, and the wrapped lines of the message being added. */
        private int zea$barBeforeAdd;
        private List<FormattedCharSequence> zea$pendingContents;
        /** The scroll anchor refreshTrimmedMessages started from. */
        private int zea$scrollBeforeRefresh;

        /**
         * Counts the wrapped lines of each added message; needed because the
         * trimmed list is capped at 100 entries, so growth alone under-reports
         * on busy channels. On EarthMC the wrap itself is memoized — a
         * rebuild re-adds the whole history, and re-wrapping every message
         * per tab switch is the live-reported lag — while everywhere else
         * vanilla's compute-per-call behavior is kept byte-for-byte.
         *
         * <p>This is also where received channel messages pick up the
         * slight-yellow tint: the wrap is computed from a tinted copy of
         * the content (see {@link ChannelTint}), memoized under the
         * original message, so every later rebuild reuses the tinted wrap.
         * The stored message itself is never modified — copying, mention
         * identity and history see the original component. The wrapped
         * sequence instances are stashed for the arrival fade — they are
         * the exact instances the display lines will carry.
         */
        @WrapOperation(method = "addMessageToDisplayQueue", at = @At(value = "INVOKE",
                        target = "Lnet/minecraft/client/multiplayer/chat/GuiMessage;splitLines(Lnet/minecraft/client/gui/Font;I)Ljava/util/List;"))
        private List<FormattedCharSequence> zea$countAddedLines(GuiMessage message, Font font, int maxWidth,
                        Operation<List<FormattedCharSequence>> original) {
                List<FormattedCharSequence> lines;
                if (zea$onEarthMc()) {
                        lines = zea$splitLines.cached(message, font, maxWidth);
                        if (lines == null) {
                                lines = original.call(zea$linkSpawnCommands(zea$tintIfChannel(message)), font, maxWidth);
                                zea$splitLines.remember(message, font, maxWidth, lines);
                        }
                } else {
                        lines = original.call(message, font, maxWidth);
                }
                zea$pendingContents = zea$inRefresh ? null : lines;
                return lines;
        }

        /**
         * The message to wrap for display: a derived record whose content is
         * the slight-yellow tinted copy when the message arrived in a
         * writable channel and the tint is enabled; the original otherwise.
         * Classification goes through the cache, so this is a lookup in
         * steady state.
         */
        private GuiMessage zea$tintIfChannel(GuiMessage message) {
                if (!ZeaClient.config().channelTintEnabled()) {
                        return message;
                }
                ChatSubsystem chat = ZeaClient.chat();
                if (chat == null) {
                        return message;
                }
                MessageCategory category = chat.classifications()
                                .classificationOf(message.content()).category();
                if (!ChannelTint.TINTED_CATEGORIES.contains(category)) {
                        return message;
                }
                return new GuiMessage(message.addedTime(), ChannelTint.tint(message.content()),
                                message.signature(), message.source(), message.tag());
        }

        /**
         * The message to wrap for display when click-to-visit is on: a derived
         * record whose content carries the click and hover on its spawn
         * commands ({@link SpawnCommandLinks} — same visible text, same
         * formatting, no interaction overwritten). Click resolution flows
         * through the same display lines, so the derived content is what both
         * renders and clicks see; the stored message keeps the original.
         */
        private GuiMessage zea$linkSpawnCommands(GuiMessage message) {
                if (!ZeaClient.config().clickToVisitSpawnCommandsEnabled()) {
                        return message;
                }
                Component linked = SpawnCommandLinks.link(message.content());
                if (linked == message.content()) {
                        return message;
                }
                return new GuiMessage(message.addedTime(), linked,
                                message.signature(), message.source(), message.tag());
        }

        @Inject(method = "addMessageToDisplayQueue", at = @At("HEAD"))
        private void zea$captureBeforeAdd(GuiMessage message, CallbackInfo ci) {
                if (zea$inRefresh || !zea$onEarthMc()) {
                        return;
                }
                zea$inAddMessage = true;
                zea$barBeforeAdd = chatScrollbarPos;
                // One animation window per arriving message, Chatting's exact
                // model: a zero duration (the effect disabled) still closes any
                // window left open, so nothing fades without a gliding offset.
                zea$smoothChat.start(System.nanoTime(), zea$smoothChatActive() ? zea$durationMillis() : 0);
        }

        /**
         * After the add: the anchor may have moved — vanilla's internal
         * per-line pin scroll while the view is scrolled up — and the
         * smooth-scroll animation rides along by the same delta, so the
         * glide lands where the content now lives (Chatting's shift). The
         * message's wrapped lines join the fade set, which is exactly the
         * current message's lines: the window opened at the head cleared
         * whatever an earlier message left.
         */
        @Inject(method = "addMessageToDisplayQueue", at = @At("TAIL"))
        private void zea$finishAdd(GuiMessage message, CallbackInfo ci) {
                zea$inAddMessage = false;
                List<FormattedCharSequence> added = zea$pendingContents;
                zea$pendingContents = null;
                if (zea$inRefresh) {
                        return;
                }
                if (zea$scrollSmoothingActive()) {
                        int delta = chatScrollbarPos - zea$barBeforeAdd;
                        if (delta != 0) {
                                zea$scrolling.shift(delta);
                        }
                }
                if (added != null && zea$smoothChatActive()) {
                        for (FormattedCharSequence content : added) {
                                zea$smoothChat.addLine(content);
                        }
                }
        }

        @Inject(method = "resetChatScroll", at = @At("HEAD"))
        private void zea$clearRefreshFlagOnReset(CallbackInfo ci) {
                // resetChatScroll never runs inside a refresh, so this is a
                // safe point to clear a refresh flag an exception left set
                // (a third-party filter that throws inside the rebuild).
                zea$inRefresh = false;
        }

        /**
         * Vanilla applies the visible-message filter as an ingest gate:
         * {@code addMessage} returns before its store calls when the filter
         * rejects, so a message hidden by the selected feed would vanish
         * from the chat entirely — {@code allMessages} never receives it,
         * and switching back to General could not restore it (verified against
         * the 26.3 bytecode). When the installed filter is ZEA's, store the
         * rejected message into {@code allMessages} before vanilla drops
         * it: hidden from the display list now, retained for every other
         * feed and the General aggregate. Any other filter (vanilla's own
         * chat-trust filter, other mods') keeps vanilla's drop semantics —
         * the wrap is then a pure passthrough.
         *
         * <p>Messages passing a ZEA filter are also classified right here,
         * once, as they arrive: the feed predicate classifies on cache miss,
         * so a message that skips this step (history restored by another
         * mod, or accepted by the always-true General filter) would otherwise
         * run the whole pipeline inside the next tab switch's rebuild —
         * repeatedly, once per switch, until the cache sees it. With the
         * wrap classifying at ingest, every later rebuild is a lookup.
         */
        @WrapOperation(method = "addMessage", at = @At(value = "INVOKE",
                        target = "Ljava/util/function/Predicate;test(Ljava/lang/Object;)Z"))
        private boolean zea$retainMessagesHiddenByZeaFilters(Predicate<GuiMessage> filter, Object message,
                        Operation<Boolean> original) {
                boolean shown = original.call(filter, message);
                if (filter instanceof FeedFilter.Installed) {
                        ChatSubsystem chat = ZeaClient.chat();
                        if (chat != null) {
                                chat.classifications().classificationOf(((GuiMessage) message).content());
                        }
                        if (!shown) {
                                addMessageToQueue((GuiMessage) message);
                        }
                }
                return shown;
        }

        /**
         * A bulk rebuild is not an append: the per-line scroll compensation
         * {@code addMessageToDisplayQueue} applies (one notch per line while
         * the chat is open and scrolled up) exists to keep single new
         * messages steady, and across a whole rebuild it inflates the anchor
         * until it clamps against the new list — the view jumps toward the
         * oldest messages on every tab switch. Remember the anchor before
         * the rebuild and restore it after, letting vanilla's own
         * {@code scrollChat(0)} re-clamp it to the new list. The smooth-chat
         * hooks check {@code zea$inRefresh} so a rebuilt message never
         * counts as new or as scrolling. Off EarthMC this is inert: vanilla's
         * own filter toggles keep their behavior byte-for-byte.
         */
        @Inject(method = "refreshTrimmedMessages", at = @At("HEAD"))
        private void zea$rememberScrollBeforeRefresh(CallbackInfo ci) {
                zea$inRefresh = true;
                zea$scrollBeforeRefresh = chatScrollbarPos;
        }

        @Inject(method = "refreshTrimmedMessages", at = @At("TAIL"))
        private void zea$restoreScrollAfterRefresh(CallbackInfo ci) {
                zea$inRefresh = false;
                if (!zea$onEarthMc()) {
                        return;
                }
                chatScrollbarPos = zea$scrollBeforeRefresh;
                newMessageSinceScroll = false;
                scrollChat(0);
        }

        /**
         * The chat forgetting its messages forgets their wraps with them:
         * keeping the memo alive past {@code clearMessages} would pin the
         * components of a dead conversation for no rebuild that can ever
         * ask for them again.
         */
        @Inject(method = "clearMessages", at = @At("TAIL"))
        private void zea$forgetSplitLines(CallbackInfo ci) {
                zea$splitLines.clear();
        }

        /**
         * The scroll arm: vanilla's {@code scrollChat} is every real scroll
         * event's entry point — wheel, page keys, the internal pin scroll.
         * Only the last must not arm an animation (a message add would
         * glide the pinned view on its own), hence the add guard; Chatting
         * arms the same way.
         */
        @Inject(method = "scrollChat", at = @At("HEAD"))
        private void zea$armSmoothScroll(int direction, CallbackInfo ci) {
                if (!zea$inAddMessage && zea$scrollSmoothingActive()) {
                        zea$scrolling.arm();
                }
        }

        /**
         * Advances the scroll animation at the render head — both the
         * visible path and the click-capture path flow through this
         * overload, so hit-testing reads the same position the player
         * sees. The duration is re-read per step, so a config change
         * applies to the next scroll.
         */
        @Inject(method = "extractRenderState(Lnet/minecraft/client/gui/components/ChatComponent$ChatGraphicsAccess;IILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;)V",
                        at = @At("HEAD"))
        private void zea$stepSmoothScroll(ChatComponent.ChatGraphicsAccess graphics, int screenHeight, int ticks,
                        ChatComponent.DisplayMode displayMode, CallbackInfo ci) {
                if (zea$scrollSmoothingActive()) {
                        zea$scrolling.step(chatScrollbarPos, zea$scrollDurationMillis(), System.nanoTime());
                }
        }

        /**
         * The three render reads of the scroll anchor — the line indexing
         * pair in {@code forEachLine} and the scrollbar's one — see the
         * eased position; everything else (writes, the pin scroll, the
         * new-message flag) keeps the raw anchor.
         */
        @ModifyExpressionValue(method = {"forEachLine",
                        "extractRenderState(Lnet/minecraft/client/gui/components/ChatComponent$ChatGraphicsAccess;IILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;)V"},
                        at = @At(value = "FIELD", target = "Lnet/minecraft/client/gui/components/ChatComponent;chatScrollbarPos:I",
                                opcode = Opcodes.GETFIELD))
        private int zea$renderedScrollPos(int original) {
                return zea$scrollSmoothingActive() ? zea$scrolling.pos() : original;
        }

        /**
         * The arrival fade, applied where vanilla hands each rendered line
         * its alpha — one injection covers both consumers of
         * {@code forEachLine}, the background pass and the text pass, so a
         * window's lines fade in together exactly as Chatting's do (its
         * fadeColor on the background and the text alpha amount to the
         * same multiplication). Lines outside the window pass through
         * untouched; the handler is inert when smooth chat is off. The
         * injection is an argument modifier because the consumer interface
         * is private to ChatComponent — a wrap's handler would have to name
         * it, and the argument shape ({@code Line, int, float}) needs no
         * private type at all.
         */
        @ModifyArgs(method = "forEachLine", at = @At(value = "INVOKE",
                        target = "Lnet/minecraft/client/gui/components/ChatComponent$LineConsumer;accept(Lnet/minecraft/client/multiplayer/chat/GuiMessage$Line;IF)V"))
        private void zea$fadeIncomingLines(Args args) {
                if (!zea$smoothChatActive()) {
                        return;
                }
                GuiMessage.Line line = args.get(0);
                float alpha = args.get(2);
                args.set(2, alpha * zea$smoothChat.fadeAlpha(line.content(), System.nanoTime()));
        }

        /**
         * Applies the eased arrival offset to the chat's pushed pose, in
         * screen pixels: the translate happens before vanilla applies the
         * chat scale, and chat units times the scale are gui-scaled pixels.
         * Chatting's gate — no glide while the player is scrolled up —
         * reads the raw anchor.
         */
        @WrapOperation(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;IIILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;Z)V",
                        at = @At(value = "INVOKE", target = "Lorg/joml/Matrix3x2fStack;pushMatrix()Lorg/joml/Matrix3x2fStack;"))
        private Matrix3x2fStack zea$applySmoothChatOffset(Matrix3x2fStack stack,
                        Operation<Matrix3x2fStack> original) {
                Matrix3x2fStack pushed = original.call(stack);
                if (zea$smoothChatActive()) {
                        float units = zea$smoothChat.translateUnits(zea$lineHeight(), chatScrollbarPos > 0,
                                        System.nanoTime());
                        if (units != 0f) {
                                pushed.translate(0.0f, units * (float) getScale());
                        }
                }
                return pushed;
        }

        private boolean zea$smoothChatActive() {
                return ZeaClient.chat() != null
                                && ZeaClient.lifecycle().onEarthMc()
                                && ZeaClient.config().smoothChatEnabled();
        }

        private boolean zea$scrollSmoothingActive() {
                return ZeaClient.chat() != null
                                && ZeaClient.lifecycle().onEarthMc()
                                && ZeaClient.config().smoothScrollingEnabled();
        }

        private boolean zea$onEarthMc() {
                return ZeaClient.chat() != null && ZeaClient.lifecycle().onEarthMc();
        }

        private int zea$durationMillis() {
                return ZeaClient.config().smoothChatDurationMillis();
        }

        private int zea$scrollDurationMillis() {
                return ZeaClient.config().smoothScrollingDurationMillis();
        }

        private double zea$lineHeight() {
                return ChatGeometry.entryHeight(Minecraft.getInstance().options.chatLineSpacing().get());
        }
}
