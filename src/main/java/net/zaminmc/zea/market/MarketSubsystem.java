package net.zaminmc.zea.market;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.ChatFormatting;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
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
import net.zaminmc.zea.market.model.SpawnRef;
import net.zaminmc.zea.market.model.StockUnit;
import net.zaminmc.zea.market.pricing.PriceMath;
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
 * <p>Capture announces itself in chat: the listing found and added, with
 * everything the capture got — owner, item, direction, stock, price,
 * enchantments, the sign it sits at, the spawn that leads there. The
 * spawn resolves from claim data when that can; whatever the server
 * never carried (a spawn, a price) earns a clickable prompt that opens
 * the completion screen — the only time a screen is involved. The
 * player's answers join the capture as the player-vouched observation.
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
                                announceCapture(inspection, recorded);
                        }
                        // The player has reached and inspected the shop they were
                        // navigating to: the waypoint has done its job and is gone.
                        net.zaminmc.zea.nav.ShopDestinationTracker destinations = ZeaClient.destinationTracker();
                        if (destinations != null) {
                                destinations.clearFor(recorded.shop().shopId());
                        }
                });
        }

        // ------------------------------------------------------------------
        // The capture announcement
        // ------------------------------------------------------------------

        /**
         * The capture announcement: the listing found and added, with the
         * full information the capture got, followed by a clickable prompt
         * for whatever the server never carried. Nothing here interrupts the
         * player mid-transaction: chat lines never steal the screen.
         */
        private void announceCapture(ShopInspectionTracker.Inspection inspection,
                        Observations.Recorded recorded) {
                Minecraft client = Minecraft.getInstance();
                if (client.player == null) {
                        return;
                }
                String shopId = recorded.shop().shopId();
                // The spawn: the player's own annotation stands; claims resolve
                // what they can, and a resolution joins the shop's record.
                String spawn = data.shop(shopId) == null ? null : data.shop(shopId).spawn();
                if (spawn == null) {
                        spawn = resolvedSpawn(inspection);
                        if (spawn != null && data.annotate(shopId, spawn)) {
                                persistence.markDirty(MarketPersistence.snapshot(data));
                        }
                }
                ShopObservation observation = recorded.observation();
                announce(inspection, recorded.shop(), observation, spawn);
                List<String> missing = missingDetails(observation, spawn);
                if (!missing.isEmpty()) {
                        promptDetails(missing);
                }
        }

        /** The spawn the claim geometry resolves for the sign, or {@code null}. */
        private @Nullable String resolvedSpawn(ShopInspectionTracker.Inspection inspection) {
                if (towns == null) {
                        return null;
                }
                MarketListings.ResolvedTown resolved = towns.at(inspection.world(),
                                inspection.signPos().getX(), inspection.signPos().getZ());
                if (resolved == null) {
                        return null;
                }
                if (resolved.nation() != null) {
                        return SpawnRef.ofNation(resolved.nation());
                }
                if (resolved.town() != null) {
                        return SpawnRef.ofTown(resolved.town());
                }
                return null;
        }

        /** The details a capture is missing: the spawn, and absent response fields. */
        private static List<String> missingDetails(ShopObservation observation, @Nullable String spawn) {
                java.util.ArrayList<String> missing = new java.util.ArrayList<>();
                if (spawn == null) {
                        missing.add("spawn");
                }
                if (observation.price() == null) {
                        missing.add("price");
                }
                if (observation.stockRaw() == null) {
                        missing.add("stock");
                }
                return missing;
        }

        /** The success block: what was found, what it got, where it sits. */
        private void announce(ShopInspectionTracker.Inspection inspection, PhysicalShop shop,
                        ShopObservation observation, @Nullable String spawn) {
                boolean verified = observation.sourceType() == SourceType.SERVER_RESPONSE;
                Minecraft client = Minecraft.getInstance();
                chat(client, headline(verified ? "Listing found and added successfully"
                                : "Shop sign read — captured from the sign"));
                String item = observation.itemDisplay() == null ? "unknown item" : observation.itemDisplay();
                chat(client, detail("Owner: ", owner(shop))
                                .append(" · ").append(direction(observation.direction()))
                                .append(" ").append(item));
                String stock = stockText(observation);
                String price = priceText(observation);
                if (stock != null || price != null) {
                        MutableComponent line = Component.literal("");
                        if (stock != null) {
                                line.append(detail("Stock: ", stock));
                        }
                        if (price != null) {
                                if (!stock.isEmpty()) {
                                        line.append(" · ");
                                }
                                line.append(detail("Price: ", price));
                        }
                        chat(client, line);
                }
                if (!observation.enchantments().isEmpty()) {
                        chat(client, detail("Enchantments: ", String.join(", ", observation.enchantments())));
                }
                MutableComponent place = detail("Sign: ", coordinates(inspection.signPos()));
                if (spawn != null) {
                        place.append(" · ").append(detail("Spawn: ", spawn));
                }
                chat(client, place);
        }

        /** The clickable prompt for the details the server never carried. */
        private void promptDetails(List<String> missing) {
                if (config == null || !config.capturePrompts) {
                        return;
                }
                Minecraft client = Minecraft.getInstance();
                if (client.player == null) {
                        return;
                }
                chat(client, headline("Missing: " + String.join(", ", missing) + " ")
                                .append(clickable("[Add details]")));
        }

        private static String owner(@Nullable PhysicalShop shop) {
                String owner = shop == null ? null : shop.owner();
                return owner == null || owner.isBlank() ? "?" : owner;
        }

        private static String direction(ShopDirection direction) {
                return direction == ShopDirection.SELLING ? "Selling" : "Buying";
        }

        /** The stock as the server worded it, unit word only when it is known. */
        private static @Nullable String stockText(ShopObservation observation) {
                if (observation.stockRaw() == null) {
                        return null;
                }
                return switch (observation.stockUnit()) {
                        case ITEMS -> observation.stockRaw() + " items";
                        case SPACE -> observation.stockRaw() + " space";
                        default -> observation.stockRaw();
                };
        }

        /** The price as captured, with the per-item figure when it normalizes. */
        private static @Nullable String priceText(ShopObservation observation) {
                if (observation.price() == null) {
                        return null;
                }
                StringBuilder text = new StringBuilder(gold(observation.price()));
                if (observation.priceScope() == PriceScope.PER_BULK && observation.bulkSize() != null) {
                        text.append(" per ").append(observation.bulkSize()).append("x");
                } else if (observation.priceScope() == PriceScope.PER_ITEM) {
                        text.append(" each");
                }
                var unit = PriceMath.normalize(observation.price(), observation.priceScope(),
                                observation.bulkSize());
                if (unit.isPresent()) {
                        BigDecimal perItem = unit.get();
                        boolean isBulk = observation.priceScope() == PriceScope.PER_BULK;
                        if (isBulk || perItem.compareTo(observation.price()) != 0) {
                                text.append(" (").append(gold(perItem)).append(" each)");
                        }
                }
                return text.toString();
        }

        private static String gold(BigDecimal price) {
                return price.stripTrailingZeros().toPlainString() + "G";
        }

        private static String coordinates(BlockPos pos) {
                return pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
        }

        /** One gray detail line: a label, then a light value. */
        private static MutableComponent detail(String label, String value) {
                return Component.literal(label).withStyle(ChatFormatting.GRAY)
                                .append(Component.literal(value).withStyle(ChatFormatting.WHITE));
        }

        /** The gold bullet and a white headline. */
        private static MutableComponent headline(String text) {
                return Component.literal("\u25aa ").withStyle(ChatFormatting.GOLD)
                                .append(Component.literal(text).withStyle(ChatFormatting.WHITE));
        }

        /** The clickable command the prompt carries. */
        private static MutableComponent clickable(String text) {
                return Component.literal(text)
                                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                                .withStyle(style -> style.withClickEvent(new ClickEvent.RunCommand(
                                                "/zea market-dev review")));
        }

        /** One client system message into the chat HUD. */
        private static void chat(Minecraft client, Component line) {
                client.gui.hud.getChat().addClientSystemMessage(line);
        }

        // ------------------------------------------------------------------
        // The completion the capture screen records
        // ------------------------------------------------------------------

        /**
         * The completed capture: the spawn annotation joins the shop's
         * record, and a price or stock the player filled into a capture
         * that lacked them joins the shop's latest observation as the
         * player-vouched MANUAL record — the player's word outranks the
         * half-parsed capture. Nothing the player left empty is invented.
         *
         * @return whether the market changed
         */
        public boolean completeCapture(String shopId, @Nullable String spawn,
                        @Nullable BigDecimal price, @Nullable String stockRaw) {
                boolean changed = data.annotate(shopId, spawn);
                PhysicalShop shop = data.shop(shopId);
                if (shop == null) {
                        return changed;
                }
                List<ShopObservation> history = data.observations(shopId);
                ShopObservation latest = history.isEmpty() ? null : history.get(history.size() - 1);
                if (latest == null) {
                        if (changed) {
                                persistence.markDirty(MarketPersistence.snapshot(data));
                        }
                        return changed;
                }
                boolean priceFill = price != null && price.signum() >= 0 && latest.price() == null;
                boolean stockFill = stockRaw != null && !stockRaw.isBlank() && latest.stockRaw() == null;
                if (!priceFill && !stockFill) {
                        if (changed) {
                                persistence.markDirty(MarketPersistence.snapshot(data));
                        }
                        return changed;
                }
                ShopObservation completed = new ShopObservation(shopId, latest.direction(),
                                latest.fingerprint(), latest.itemDisplay(),
                                priceFill ? price : latest.price(),
                                priceFill ? (latest.priceScope() == null
                                                ? PriceScope.PER_ITEM : latest.priceScope()) : latest.priceScope(),
                                latest.bulkSize(),
                                stockFill ? stockRaw.trim() : latest.stockRaw(),
                                stockFill ? StockUnit.ITEMS : latest.stockUnit(),
                                latest.remainingPercent(),
                                latest.maxBuyBulks(), latest.maxBuyItems(),
                                latest.maxSellBulks(), latest.maxSellItems(),
                                latest.supportsAll(), latest.enchantments(),
                                SourceType.MANUAL, Instant.now());
                if (data.record(shop, completed)) {
                        persistence.markDirty(MarketPersistence.snapshot(data));
                        submissions.enqueue(shop, completed);
                        confirmCompletion(shop, completed, spawn);
                }
                return true;
        }

        /** The chat confirmation of a completion: the listing as it now stands. */
        private void confirmCompletion(PhysicalShop shop, ShopObservation completed,
                        @Nullable String spawn) {
                Minecraft client = Minecraft.getInstance();
                if (client.player == null) {
                        return;
                }
                String item = completed.itemDisplay() == null ? "item" : completed.itemDisplay();
                MutableComponent line = headline("Listing added to the market list")
                                .append(" · ").append(detail("Item: ", item))
                                .append(" · ").append(detail("Sign: ", coordinates(shop.signPos())));
                if (spawn != null) {
                        line.append(" · ").append(detail("Spawn: ", spawn));
                }
                chat(client, line);
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
