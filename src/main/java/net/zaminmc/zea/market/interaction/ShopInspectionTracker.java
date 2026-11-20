package net.zaminmc.zea.market.interaction;

import net.minecraft.core.BlockPos;
import net.zaminmc.zea.chat.model.CapturedMessage;
import net.zaminmc.zea.market.model.ShopResponse;
import net.zaminmc.zea.market.parse.ShopResponseParser;
import net.zaminmc.zea.market.parse.ShopSignParser;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Correlates server shop responses with the sign interactions that
 * caused them.
 *
 * <p>The flow: a sign right-click notes a pending use (position, world,
 * server identity, the sign's own text). Shop-shaped lines that arrive
 * while a use is pending start — or extend — a response run; the run is
 * closed by a quiet gap, the next sign use, or the use's expiry, and the
 * closed run is parsed into a {@link ShopResponse}. A use that expires
 * without a parseable response falls back to its sign text, which may
 * still identify the shop as a sign-only advert.
 *
 * <p>Robustness rules, all pinned by tests:
 *
 * <ul>
 *   <li>Shop-shaped lines with no pending use are ignored entirely —
 *       unrelated chat is never touched, and nothing is ever recorded
 *       for a shop the player has not actually inspected.</li>
 *   <li>An unrelated chat message never splits a response. The server's
 *       block is anchored by its shop-shaped lines; a non-shop line
 *       between two shop lines (another player talking, a broadcast, a
 *       wording the anchors do not know) used to close the run and
 *       record half a response — now the run stays open and the quiet
 *       window closes it whole.</li>
 *   <li>A response attaches to the newest pending use that actually is
 *       a shop sign. Everyday block uses (chests, doors) also arrive
 *       here as uses with no sign text; they must never own a shop's
 *       response. When no pending use carries shop sign text the
 *       newest use still takes it, so a shop whose sign shape varies
 *       keeps capturing.</li>
 *   <li>A new sign use flushes the open run first: the boundary between
 *       two inspections is the next sign the player clicks, not random
 *       chat.</li>
 *   <li>A run that fails to parse is discarded but the use stays
 *       pending, so a delayed continuation can still complete it.</li>
 *   <li>An identical response re-arriving inside the duplicate window is
 *       suppressed instead of being recorded twice.</li>
 *   <li>Disconnected or world-changed sessions are reset by the owner;
 *       the tracker itself never sees stale state.</li>
 * </ul>
 */
public final class ShopInspectionTracker {
        /** How long a sign use may wait for its server response. */
        public static final Duration RESPONSE_WINDOW = Duration.ofSeconds(10);

        /** How long a response run may stay quiet before it is closed. */
        public static final Duration RUN_QUIET_WINDOW = Duration.ofMillis(1_200);

        /** How long an exact repeat of a just-recorded response is suppressed. */
        public static final Duration DUPLICATE_WINDOW = Duration.ofMillis(2_500);

        private static final int MAX_PENDING_USES = 4;

        private final Consumer<Inspection> sink;
        private final List<PendingUse> pending = new ArrayList<>();
        private final List<String> lastRawResponse = new ArrayList<>();
        /** The open response run, or {@code null} when idle. */
        private OpenRun run;
        private Instant lastResponseAt = Instant.EPOCH;

        /** @param sink receives exactly one inspection per accepted response or sign-only advert */
        public ShopInspectionTracker(Consumer<Inspection> sink) {
                this.sink = sink;
        }

        /**
         * Notes a sign right-click. The sign's text is read by the caller
         * before anything can overwrite it; it is the sign-only fallback if
         * the server never responds.
         */
        public void noteSignUse(String world, String serverId, BlockPos signPos,
                        @Nullable BlockPos chestPos, List<String> signLines, Instant usedAt) {
                // The next sign use is the boundary of the previous response:
                // flush what is open before this click starts hoping for its
                // own reply.
                closeRun(true);
                pending.add(new PendingUse(world, serverId, signPos, chestPos, List.copyOf(signLines), usedAt));
                while (pending.size() > MAX_PENDING_USES) {
                        PendingUse oldest = pending.remove(0);
                        expireWithoutResponse(oldest, usedAt);
                }
        }

        /**
         * Feeds one captured message. Messages that carry shop-shaped lines
         * extend the open run (or start one when a use is pending); any
         * other message is left alone — an unrelated line between two shop
         * lines used to split the response in half and record the fragment
         * the anchors happened to recognize. The quiet window, not random
         * chat, closes the run.
         */
        public void onMessage(CapturedMessage message, Instant now) {
                List<String> lines = ShopResponseParser.shopLines(message);
                if (lines == null) {
                        return;
                }
                if (pending.isEmpty()) {
                        // Nothing this could belong to: unrelated chat, or a shop
                        // response the player never inspected. Ignored either way.
                        return;
                }
                if (run == null) {
                        run = new OpenRun(runUse(), now);
                }
                run.messages().add(message);
                run.lastLineAt = now;
        }

        /**
         * Drives the time-based transitions: quiet runs close and parse,
         * expired uses fall back to their sign text.
         */
        public void tick(Instant now) {
                if (run != null && Duration.between(run.lastLineAt, now).compareTo(RUN_QUIET_WINDOW) >= 0) {
                        closeRun(true);
                }
                if (!pending.isEmpty()) {
                        PendingUse oldest = pending.get(0);
                        if (Duration.between(oldest.usedAt(), now).compareTo(RESPONSE_WINDOW) >= 0) {
                                pending.remove(oldest);
                                expireWithoutResponse(oldest, now);
                        }
                }
        }

        /** Forgets all pending state (disconnect, world change). */
        public void reset() {
                pending.clear();
                run = null;
                lastRawResponse.clear();
        }

        private void expireWithoutResponse(PendingUse use, Instant at) {
                if (ShopSignParser.isShopSign(use.signLines())) {
                        sink.accept(Inspection.signOnly(use.world(), use.serverId(), use.signPos(),
                                        use.chestPos(), use.signLines(), at));
                }
        }

        private PendingUse newestUse() {
                return pending.get(pending.size() - 1);
        }

        /**
         * The use a response run should attach to: the newest pending use
         * whose sign text actually is a shop sign, falling back to the
         * newest use of any kind. Chests, doors and every other block use
         * also land here as pending uses with no sign text — a shop's
         * response must attach to the shop's sign, not to whatever block
         * the player last touched.
         */
        private PendingUse runUse() {
                for (int i = pending.size() - 1; i >= 0; i--) {
                        PendingUse use = pending.get(i);
                        if (ShopSignParser.isShopSign(use.signLines())) {
                                return use;
                        }
                }
                return newestUse();
        }

        /**
         * Closes the open run, parses it, and emits the inspection when the
         * parse succeeds. A declined parse (malformed or partial to the
         * point of unusability) keeps the use pending so a delayed rest of
         * the response can still arrive.
         */
        private void closeRun(boolean parseIfReady) {
                if (run == null) {
                        return;
                }
                OpenRun closing = run;
                run = null;
                if (!parseIfReady) {
                        return;
                }
                var parsed = new ShopResponseParser().parse(closing.messages(), closing.lastLineAt);
                if (parsed.isEmpty()) {
                        return;
                }
                ShopResponse response = parsed.get();
                List<String> raw = response.rawLines();
                if (!lastRawResponse.isEmpty() && lastRawResponse.equals(raw)
                                && Duration.between(lastResponseAt, closing.lastLineAt).compareTo(DUPLICATE_WINDOW) < 0) {
                        return;
                }
                lastRawResponse.clear();
                lastRawResponse.addAll(raw);
                lastResponseAt = closing.lastLineAt;

                PendingUse use = closing.use();
                sink.accept(new Inspection(use.world(), use.serverId(), use.signPos(), use.chestPos(),
                                response, use.signLines(), closing.lastLineAt));
                pending.remove(use);
        }

        /** One sign use still hoping for its server response. */
        private record PendingUse(String world, String serverId, BlockPos signPos,
                        @Nullable BlockPos chestPos, List<String> signLines, Instant usedAt) {
        }

        /** A response run being assembled against one pending use. */
        private static final class OpenRun {
                private final PendingUse use;
                private final List<CapturedMessage> messages = new ArrayList<>();
                private Instant lastLineAt;

                OpenRun(PendingUse use, Instant startedAt) {
                        this.use = use;
                        this.lastLineAt = startedAt;
                }

                PendingUse use() {
                        return use;
                }

                List<CapturedMessage> messages() {
                        return messages;
                }
        }

        /**
         * One correlated inspection: the physical shop context plus either a
         * parsed server response or the sign text that stands in for it.
         */
        public record Inspection(
                        String world,
                        String serverId,
                        BlockPos signPos,
                        @Nullable BlockPos chestPos,
                        @Nullable ShopResponse response,
                        @Nullable List<String> signLines,
                        Instant inspectedAt) {

                public Inspection {
                        if (world == null || serverId == null || signPos == null || inspectedAt == null) {
                                throw new NullPointerException("inspection fields must not be null");
                        }
                        if (signLines != null) {
                                signLines = List.copyOf(signLines);
                        }
                }

                static Inspection signOnly(String world, String serverId, BlockPos signPos,
                                @Nullable BlockPos chestPos, List<String> signLines, Instant at) {
                        return new Inspection(world, serverId, signPos, chestPos, null, signLines, at);
                }

                /** Whether this inspection carries a server-verified response. */
                public boolean isServerVerified() {
                        return response != null;
                }
        }
}
