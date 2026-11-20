package net.zaminmc.zea.market;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.ChatFormatting;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignTextSlot;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.zaminmc.zea.ZeaClient;
import net.zaminmc.zea.chat.capture.MessageCapture;
import net.zaminmc.zea.chat.model.CapturedMessage;
import net.zaminmc.zea.earthmc.ServerIdentity;
import net.zaminmc.zea.lifecycle.ClientLifecycle;
import net.zaminmc.zea.market.config.MarketConfig;
import net.zaminmc.zea.market.interaction.ShopInspectionTracker;
import net.zaminmc.zea.market.interaction.ShopSignGeometry;
import net.zaminmc.zea.market.model.ItemFingerprint;
import net.zaminmc.zea.market.model.PhysicalShop;
import net.zaminmc.zea.market.model.PriceScope;
import net.zaminmc.zea.market.model.ShopDirection;
import net.zaminmc.zea.market.model.ShopObservation;
import net.zaminmc.zea.market.model.ShopResponse;
import net.zaminmc.zea.market.model.SourceType;
import net.zaminmc.zea.market.model.StockUnit;
import net.zaminmc.zea.market.store.MarketStore;
import org.jspecify.annotations.Nullable;
import net.zaminmc.zea.market.sync.SubmissionQueue;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutorService;

/**
 * The market subsystem: turns physical shop inspections into persisted
 * market data. Sign right-clicks arrive from the game-mode mixin, chat
 * arrives through its own capture hook, and the inspection tracker
 * correlates the two — the result is a shop inspection carrying either a
 * server-verified response or the sign text that stands in when the
 * server never responded. Every accepted inspection is recorded into the
 * market index (a shop revisit updates the shop and appends an
 * observation) and scheduled for a disk write.
 *
 * <p>All state mutations run on the client thread: capture events, ticks
 * and the mixin are client-thread, so the index needs no locks; disk IO
 * happens on the market executor via snapshots taken on the client
 * thread. The market works fully offline — persistence never touches a
 * network.
 */
public final class MarketSubsystem {
        private final ClientLifecycle lifecycle;
        private final ShopInspectionTracker tracker;
        private final MarketData data;
        private final MarketPersistence persistence;
        private final MarketConfig config;
        private final SubmissionQueue submissions;
        // The town/nation lookup over claim geometry; null when no claim
        // source is wired, which leaves town and nation simply unavailable.
        private final MarketListings.TownLookup towns;
        // Null until the first inspection lands; read on the client thread only.
        private volatile ShopInspectionTracker.Inspection latestInspection;

        public MarketSubsystem(ClientLifecycle lifecycle, Path file, ExecutorService executor,
                        MarketConfig config, MarketListings.TownLookup towns) {
                this.lifecycle = lifecycle;
                this.config = config;
                this.towns = towns;
                this.tracker = new ShopInspectionTracker(this::onInspection);
                this.data = restore(file);
                this.persistence = new MarketPersistence(new MarketStore(file), executor);
                this.submissions = new SubmissionQueue(file.resolveSibling("market-submissions.json"), executor);
                // Pending inspections belong to one connection: a disconnect or a
                // world change leaves nothing for a response to correlate with.
                // The recorded market, in contrast, survives every disconnect —
                // that is the point of it.
                ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset());
                ClientTickEvents.END_CLIENT_TICK.register(client -> {
                        tracker.tick(Instant.now());
                        persistence.tick(System.currentTimeMillis());
                });
                // A second, independent capture sink — the chat pipeline keeps
                // its own; the market reads the same events and never touches
                // the chat feed.
                new MessageCapture(lifecycle::onEarthMc, this::onMessage);
        }

        /** The market index: known shops and their observation history. */
        public MarketData data() {
                return data;
        }

        /**
         * The town/nation lookup the browser consults, from the claim store's
         * published geometry; {@code null} when no claim source is wired.
         */
        public MarketListings.TownLookup townLookup() {
                return towns;
        }

        /** Forces pending market and queue writes to land; used for shutdown. */
        public void flushNow() {
                persistence.flushNow();
                submissions.flushNow();
        }

        /** Forgets pending inspection state (disconnect, world change), keeps the market. */
        public void reset() {
                tracker.reset();
                latestInspection = null;
        }

        private void onInspection(ShopInspectionTracker.Inspection inspection) {
                latestInspection = inspection;
                Observations.of(inspection).ifPresent(recorded -> {
                        if (data.record(recorded.shop(), recorded.observation())) {
                                persistence.markDirty(MarketPersistence.snapshot(data));
                                submissions.enqueue(recorded.shop(), recorded.observation());
                                promptReview(inspection, recorded);
                        }
                        // The player has reached and inspected the shop they were
                        // navigating to: the waypoint has done its job and is gone.
                        net.zaminmc.zea.nav.ShopDestinationTracker destinations = ZeaClient.destinationTracker();
                        if (destinations != null) {
                                destinations.clearFor(recorded.shop().shopId());
                        }
                });
        }

        /**
         * The capture prompt: one chat line announcing what was just captured
         * with a clickable Review action. The capture itself is already
         * recorded — the prompt is the invitation to verify it, fix what the
         * server got wrong or add what it never carried; Done records the
         * corrected listing as the shop's newest observation. Nothing here
         * interrupts the player mid-transaction: chat lines never steal the
         * screen.
         */
        private void promptReview(ShopInspectionTracker.Inspection inspection,
                        Observations.Recorded recorded) {
                if (config == null || !config.captureReviewPrompt) {
                        return;
                }
                Minecraft client = Minecraft.getInstance();
                if (client.player == null) {
                        return;
                }
                String what = recorded.observation().itemDisplay() != null
                                ? recorded.observation().itemDisplay()
                                : "shop";
                Component line = Component.literal("\u25aa ")
                                .withStyle(ChatFormatting.GOLD)
                                .append(Component.literal("Captured " + what + " ")
                                                .withStyle(ChatFormatting.WHITE))
                                .append(Component.literal("[Review]")
                                                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                                                .withStyle(style -> style.withClickEvent(new ClickEvent.RunCommand(
                                                                "/zea market-editor review"))));
                client.gui.hud.getChat().addClientSystemMessage(line);
        }

        /**
         * Records a listing the player entered or confirmed in the market
         * editor: the physical shop at the given sign, the player-vouched
         * observation of what it trades. The same pipeline as a captured
         * inspection follows — the index, the disk, the repository queue —
         * with the provenance the player's own word earns: {@link
         * SourceType#MANUAL}.
         *
         * @return whether the listing was recorded
         */
        public boolean submitManualListing(String serverId, String world, BlockPos signPos,
                        @Nullable String owner, @Nullable BlockPos chestPos,
                        ShopDirection direction, ItemFingerprint fingerprint, String itemDisplay,
                        @Nullable BigDecimal price, @Nullable PriceScope scope, @Nullable Integer bulkSize,
                        @Nullable String stockRaw, StockUnit stockUnit, List<String> enchantments,
                        Instant at) {
                if (direction == null || fingerprint == null || stockUnit == null) {
                        return false;
                }
                String shopId = PhysicalShop.idOf(serverId, world, signPos);
                PhysicalShop shop = new PhysicalShop(shopId, serverId, world, signPos, chestPos,
                                owner, null, at, at);
                ShopObservation observation;
                try {
                        observation = new ShopObservation(shopId, direction, fingerprint, itemDisplay,
                                        price, scope, bulkSize, stockRaw, stockUnit,
                                        null, null, null, null, null, false,
                                        enchantments == null ? List.of() : enchantments,
                                        SourceType.MANUAL, at);
                } catch (NullPointerException | IllegalArgumentException e) {
                        // A malformed listing stays out of the market — the
                        // editor's own validation usually catches it first.
                        return false;
                }
                boolean recorded = data.record(shop, observation);
                if (recorded) {
                        persistence.markDirty(MarketPersistence.snapshot(data));
                        submissions.enqueue(shop, observation);
                        confirmListing(observation);
                }
                return recorded;
        }

        /** The chat confirmation the market list answers with. */
        private void confirmListing(ShopObservation observation) {
                Minecraft client = Minecraft.getInstance();
                if (client.player == null) {
                        return;
                }
                String what = observation.itemDisplay() != null ? observation.itemDisplay() : "item";
                client.gui.hud.getChat().addClientSystemMessage(Component.literal("\u25aa ")
                                .withStyle(ChatFormatting.GOLD)
                                .append(Component.literal("Listing added to the market list: " + what))
                                .withStyle(ChatFormatting.WHITE));
        }

        /**
         * The most recent inspection, for the shop screen that presents it.
         * Cleared on disconnect along with the rest of the pending state.
         */
        public ShopInspectionTracker.Inspection latestInspection() {
                return latestInspection;
        }

        /** A sign was right-clicked; the market records what the player inspected. */
        public void onBlockUse(ClientLevel level, BlockHitResult hit) {
                BlockPos pos = hit.getBlockPos();
                BlockState state = level.getBlockState(pos);
                List<String> signLines = readSignText(level, pos);
                // The chest slot stays empty unless a chest is provably attached —
                // a sign over anything else is recorded with sign position only.
                BlockPos chestPos = ShopSignGeometry.attachedBlock(state, pos)
                                .filter(candidate -> level.getBlockState(candidate).getBlock() instanceof ChestBlock)
                                .orElse(null);
                String serverId = lifecycle.earthMcServer().map(ServerIdentity::host).orElse("unknown");
                tracker.noteSignUse(level.dimension().identifier().toString(), serverId,
                                pos, chestPos, signLines, Instant.now());
        }

        /** One captured chat message; only EarthMC sessions are inspected. */
        public void onMessage(CapturedMessage message) {
                if (!lifecycle.onEarthMc()) {
                        return;
                }
                tracker.onMessage(message, Instant.now());
        }

        private List<String> readSignText(ClientLevel level, BlockPos pos) {
                if (level.getBlockEntity(pos) instanceof SignBlockEntity sign) {
                        return sign.getText(SignTextSlot.FRONT).getMessages(false).stream()
                                        .map(Component::getString)
                                        .toList();
                }
                return List.of();
        }

        /**
         * Restores the index from disk with full entry tolerance: shops
         * without a position or observations without an id, direction or
         * timestamp are dropped, never crash the load.
         */
        private static MarketData restore(Path file) {
                MarketStore.MarketFile stored = new MarketStore(file).load();
                MarketData data = new MarketData();
                for (MarketStore.ShopWire wire : MarketStore.usableShops(stored)) {
                        MarketStore.toShop(wire).ifPresent(data::putShop);
                }
                for (MarketStore.ObservationWire wire : MarketStore.usableObservations(stored)) {
                        MarketStore.toObservation(wire).ifPresent(data::putObservation);
                }
                return data;
        }
}
