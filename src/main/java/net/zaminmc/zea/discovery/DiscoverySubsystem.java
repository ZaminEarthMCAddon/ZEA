package net.zaminmc.zea.discovery;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.zaminmc.zea.ZeaClient;
import net.zaminmc.zea.chat.capture.MessageCapture;
import net.zaminmc.zea.chat.model.CapturedMessage;
import net.zaminmc.zea.earthmc.ServerIdentity;
import net.zaminmc.zea.lifecycle.ClientLifecycle;
import net.zaminmc.zea.map.MapEngine;
import net.zaminmc.zea.map.cache.ClaimStore;
import net.zaminmc.zea.map.model.MapWorld;
import net.zaminmc.zea.map.model.TownClaim;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * The native discovery subsystem: while enabled, it collects physical
 * shop observations from real shop-sign interactions and the server's
 * shop-information responses, and private navigation observations from
 * executed spawn commands. Everything is event-driven — the capture is
 * registered only when discovery is on, no scans run in the background,
 * and disk writes coalesce through the store's dirty flags.
 *
 * <p>Shop flow: a sign click arms the collector (with the sign's block
 * position); the server's shop block arrives over the following chat
 * components; the closing quantity prompt completes the block; the
 * parser fills a {@link ShopObservation} keyed by
 * {@link ShopIdentity#shopId} — server, dimension, sign position — and
 * the store updates the record for that physical shop. Separate shops
 * with the same owner and item stay separate records by construction.
 *
 * <p>Navigation flow: every executed {@code /n spawn}/{@code /t spawn}
 * command is recorded with the position before it; a confirmed arrival
 * (a large position jump within the confirmation window) fills the
 * after-coordinates. These entries are private and never share into the
 * public dataset.
 */
public final class DiscoverySubsystem {
        public static final Logger LOGGER = LoggerFactory.getLogger("ZEA/Discovery");

        /** How long after a spawn command an arrival teleport may confirm. */
        private static final long ARRIVAL_CONFIRM_WINDOW_MS = 20_000L;
        /** The position change that counts as a spawn arrival. */
        private static final double ARRIVAL_MIN_DISTANCE = 32.0;

        private final ClientLifecycle lifecycle;
        private final DiscoveryStore store;
        private final Path stateFile;
        private final net.zaminmc.zea.persistence.JsonStore json =
                        new net.zaminmc.zea.persistence.JsonStore();
        private DiscoveryState state;
        private final ShopTransactionCollector collector = new ShopTransactionCollector();
        private final net.zaminmc.zea.map.cache.ClaimStore claimStore;

        // The navigation entry awaiting a confirmed arrival.
        private NavigationObservation pendingArrival;
        private double pendingX;
        private double pendingZ;
        private long pendingAtMs;

        private MessageCapture capture;

        public DiscoverySubsystem(ClientLifecycle lifecycle, DiscoveryStore store,
                        Path stateFile, ClaimStore claimStore) {
                this.lifecycle = lifecycle;
                this.store = store;
                this.stateFile = stateFile;
                this.claimStore = claimStore;
                this.state = json.load(stateFile, DiscoveryState.class, DiscoveryState::new);
                this.state.schemaVersion = DiscoveryState.CURRENT_VERSION;
                ClientTickEvents.END_CLIENT_TICK.register(client -> tick());
        }

        /** Whether discovery is currently enabled. */
        public boolean enabled() {
                return state.enabled;
        }

        /** Enables or disables discovery; the state persists immediately. */
        public void setEnabled(boolean enabled) {
                state.enabled = enabled;
                persistState();
                // The capture listener follows the switch: off means no
                // message capture at all, not silent collection.
                if (enabled) {
                        registerCapture();
                }
                collector.reset();
                ZeaClient.LOGGER.info("Discovery {}", enabled ? "enabled" : "disabled");
        }

        /** The shop observations store. */
        public DiscoveryStore store() {
                return store;
        }

        /** A one-line status for the command. */
        public String statusLine() {
                return "Discovery is " + (state.enabled ? "on" : "off") + " — "
                                + store.localShops().size() + " local shop"
                                + (store.localShops().size() == 1 ? "" : "s") + " observed, "
                                + store.navigationEntries().size() + " navigation entries.";
        }

        private void persistState() {
                json.saveUnchecked(stateFile, state);
        }

        private void registerCapture() {
                if (capture != null) {
                        return;
                }
                // Enabled = onEarthMc AND discovery on: off-server capture is
                // pointless, and disabled capture must not run at all.
                capture = new MessageCapture(
                                () -> state.enabled && lifecycle.onEarthMc(),
                                this::onChatMessage);
        }

        // ------------------------------------------------------------------
        // Chat capture: shop blocks
        // ------------------------------------------------------------------

        private void onChatMessage(CapturedMessage captured) {
                if (!state.enabled || !lifecycle.onEarthMc()) {
                        return;
                }
                String text = captured.text();
                if (text == null || text.isBlank()) {
                        return;
                }
                long now = System.currentTimeMillis();
                boolean complete = collector.feed(text, now);
                if (!complete) {
                        return;
                }
                try {
                    recordShopObservation(collector.collected(), now);
                } finally {
                    collector.reset();
                }
        }

        /** Parses the collected block and upserts the physical shop's record. */
        private void recordShopObservation(List<String> lines, long now) {
                if (lines.isEmpty() || collector.signPos() == null) {
                        return;
                }
                String shopId = ShopIdentity.shopId(
                                lifecycle.earthMcServer().map(ServerIdentity::host).orElse(null),
                                currentDimension(),
                                parsePos(collector.signPos()));
                ShopObservation existing = store.localShops().get(shopId);
                ShopObservation observation;
                if (existing != null) {
                        // The same physical shop: update the record in place,
                        // keeping its discovery history.
                        observation = existing;
                } else {
                        observation = new ShopObservation();
                        observation.shopId = shopId;
                        observation.firstObservedAt = now;
                        observation.source = "local";
                }
                observation.server = lifecycle.earthMcServer()
                                .map(ServerIdentity::host).orElse(null);
                observation.dimension = currentDimension();
                observation.signPos = collector.signPos();
                observation.chestPos = lastChestPos;
                observation.lastObservedAt = now;
                ShopParser.apply(observation, lines);
                if (observation.owner == null && observation.itemName == null
                                && observation.direction == null) {
                        // Nothing recognized — not a shop response after all.
                        return;
                }
                applyTownContext(observation);
                store.putLocalShop(observation);
                LOGGER.info("Shop observation {} recorded ({} total)", shopId,
                                store.localShops().size());
        }

        /** The town and nation the sign's chunk sits in, from the map's claim data. */
        private void applyTownContext(ShopObservation observation) {
                if (collector.signPos() == null) {
                        return;
                }
                BlockPos pos = parsePos(collector.signPos());
                MapWorld world = MapEngine.currentWorld();
                if (pos == null || world == null || claimStore == null) {
                        return;
                }
                List<TownClaim> claims = claimStore.claims(world);
                for (TownClaim claim : claims) {
                        if (claim.coversBlock(pos.getX(), pos.getZ())) {
                                observation.town = claim.name();
                                observation.nation = claim.nation();
                                return;
                        }
                }
        }

        private static BlockPos parsePos(String serialized) {
                if (serialized == null) {
                        return null;
                }
                try {
                        String[] parts = serialized.split(",");
                        return new BlockPos(Integer.parseInt(parts[0]),
                                        Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
                } catch (Exception e) {
                        return null;
                }
        }

        private static String currentDimension() {
                Minecraft minecraft = Minecraft.getInstance();
                if (minecraft.level == null) {
                        return null;
                }
                return minecraft.level.dimension().identifier().toString();
        }

        // ------------------------------------------------------------------
        // Sign interaction
        // ------------------------------------------------------------------

        private String lastChestPos;

        /**
         * The player just used a block. When it is a shop-style sign and
         * discovery is on, the interaction arms the collector and records
         * the sign's position — and, when a container can be reliably
         * located from the sign's actual block relationships, the chest's.
         */
        public void onBlockUse(BlockPos pos) {
                if (!state.enabled || !lifecycle.onEarthMc()) {
                        return;
                }
                Minecraft minecraft = Minecraft.getInstance();
                if (minecraft.level == null) {
                        return;
                }
                BlockState state = minecraft.level.getBlockState(pos);
                if (!(state.getBlock() instanceof net.minecraft.world.level.block.SignBlock)
                                && !(state.getBlock() instanceof net.minecraft.world.level.block.WallSignBlock)
                                && !(state.getBlock() instanceof net.minecraft.world.level.block.StandingSignBlock)
                                && !(state.getBlock() instanceof net.minecraft.world.level.block.WallHangingSignBlock)
                                && !(state.getBlock() instanceof net.minecraft.world.level.block.CeilingHangingSignBlock)
                                && !(state.getBlock() instanceof net.minecraft.world.level.block.HangingSignBlock)) {
                        return;
                }
                lastChestPos = locateChest(pos, state);
                collector.begin(ShopIdentity.formatPos(pos), System.currentTimeMillis());
        }

        /**
         * The shop chest behind a sign, from actual block relationships:
         * a wall sign looks at the block it is attached to; a floor sign
         * sits on one. Only an immediate container counts — a guess across
         * several blocks would poison the record, so an unlocated chest is
         * recorded as unavailable instead.
         */
        private static String locateChest(BlockPos signPos, BlockState signState) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.level == null) {
                return null;
            }
            List<BlockPos> candidates = new java.util.ArrayList<>();
            if (signState.hasProperty(BlockStateProperties.FACING)
                            && signState.getBlock() instanceof net.minecraft.world.level.block.WallSignBlock) {
                    // A wall sign's back is the block it hangs on.
                    candidates.add(signPos.relative(signState.getValue(BlockStateProperties.FACING).getOpposite()));
            }
            candidates.add(signPos.below());
            for (net.minecraft.core.Direction direction : net.minecraft.core.Direction.Plane.HORIZONTAL) {
                    candidates.add(signPos.relative(direction));
            }
            for (BlockPos candidate : candidates) {
                    if (minecraft.level.getBlockEntity(candidate)
                                    instanceof RandomizableContainerBlockEntity) {
                            return ShopIdentity.formatPos(candidate);
                    }
                }
                return null;
        }

        // ------------------------------------------------------------------
        // Navigation commands
        // ------------------------------------------------------------------

        /**
         * One executed chat command. Relevant navigation commands are
         * recorded — whatever their origin: typed, clicked through ZEA's
         * links, or sent by another client feature. The command's own text
         * is the only thing inspected.
         */
        public void onCommandIssued(String command) {
                if (!state.enabled || !lifecycle.onEarthMc()) {
                        return;
                }
                if (command == null || command.isBlank()) {
                        return;
                }
                String normalized = command.trim().toLowerCase(Locale.ROOT);
                if (!normalized.startsWith("/n spawn ") && !normalized.startsWith("/t spawn ")) {
                        return;
                }
                Minecraft minecraft = Minecraft.getInstance();
                NavigationObservation entry = new NavigationObservation();
                entry.command = normalized.substring(1);
                entry.kind = normalized.startsWith("/n") ? "n" : "t";
                entry.target = normalized.substring("/* spawn ".length()).trim();
                entry.dimension = currentDimension();
                entry.observedAt = System.currentTimeMillis();
                entry.source = "other";
                if (minecraft.player != null) {
                        entry.beforeX = minecraft.player.getX();
                        entry.beforeZ = minecraft.player.getZ();
                        pendingArrival = entry;
                        pendingX = entry.beforeX;
                        pendingZ = entry.beforeZ;
                        pendingAtMs = entry.observedAt;
                }
                store.addNavigation(entry);
        }

        /** Confirms arrivals: a big position jump inside the window fills the after-coordinates. */
        private void tick() {
                if (pendingArrival != null) {
                        Minecraft minecraft = Minecraft.getInstance();
                        if (minecraft.player != null) {
                                double dx = minecraft.player.getX() - pendingX;
                                double dz = minecraft.player.getZ() - pendingZ;
                                if (Math.hypot(dx, dz) >= ARRIVAL_MIN_DISTANCE) {
                                        pendingArrival.afterX = minecraft.player.getX();
                                        pendingArrival.afterZ = minecraft.player.getZ();
                                        pendingArrival = null;
                                        store.saveIfDirty();
                                } else if (System.currentTimeMillis() - pendingAtMs
                                                > ARRIVAL_CONFIRM_WINDOW_MS) {
                                        pendingArrival = null;
                                }
                        } else {
                                pendingArrival = null;
                        }
                }
                store.saveIfDirty();
        }

        /** Flushes the store and the toggle state on shutdown. */
        public void shutdown() {
                store.flush();
                persistState();
        }

        /** Saves the navigation store immediately (the command paths call this). */
        public void save() {
                store.saveIfDirty();
                persistState();
        }
}
