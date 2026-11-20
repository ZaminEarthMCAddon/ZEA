package net.zaminmc.zea;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.zaminmc.zea.buttons.ButtonActivator;
import net.zaminmc.zea.buttons.ButtonEditorScreen;
import net.zaminmc.zea.buttons.InventoryButtons;
import net.zaminmc.zea.browser.ItemBrowser;
import net.zaminmc.zea.earthmc.ServerIdentity;
import net.zaminmc.zea.chat.ChatSubsystem;
import net.zaminmc.zea.chat.capture.CaptureCommands;
import net.zaminmc.zea.chat.capture.CaptureRecorder;
import net.zaminmc.zea.chat.capture.ComponentJson;
import net.zaminmc.zea.chat.classify.ClassificationPipeline;
import net.zaminmc.zea.config.ConfigScreen;
import net.zaminmc.zea.config.ZeaConfig;
import net.zaminmc.zea.config.ZeaConfigStore;
import net.zaminmc.zea.config.ZeaPaths;
import net.zaminmc.zea.keybind.ZeaKeys;
import net.zaminmc.zea.lifecycle.ClientLifecycle;
import net.zaminmc.zea.map.MapSubsystem;
import net.zaminmc.zea.map.cache.ClaimStore;
import net.zaminmc.zea.market.ClaimTownResolver;
import net.zaminmc.zea.market.MarketSubsystem;
import net.zaminmc.zea.market.editor.MarketEditorCommands;
import net.zaminmc.zea.market.sync.DatasetMerger;
import net.zaminmc.zea.market.sync.RepoSync;
import net.zaminmc.zea.market.sync.SharedShops;
import net.zaminmc.zea.hud.HudCommands;
import net.zaminmc.zea.hud.HudSubsystem;
import net.zaminmc.zea.map.hud.MapHudRenderer;
import net.zaminmc.zea.misc.TooltipScroller;
import net.zaminmc.zea.nav.ShopDestinationHud;
import net.zaminmc.zea.nav.ShopDestinationTracker;
import net.zaminmc.zea.nav.SpawnCommandObserver;
import net.zaminmc.zea.rarity.EnchantBudget;
import net.zaminmc.zea.rarity.RarityEngine;
import net.zaminmc.zea.search.InventorySearch;
import net.zaminmc.zea.slots.SlotLockStore;
import net.zaminmc.zea.slots.SlotLocks;
import net.zaminmc.zea.share.PasteKey;
import net.zaminmc.zea.share.PasteService;
import net.zaminmc.zea.share.ShareCommands;
import net.zaminmc.zea.share.ShareCoordinator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.nio.file.Path;
import java.time.Instant;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * ZEA entrypoint. Performs wiring only; subsystem behavior lives in the
 * packages described in ARCHITECTURE.md.
 */
public final class ZeaClient implements ClientModInitializer {
        public static final String MOD_ID = "zea";
        public static final Logger LOGGER = LoggerFactory.getLogger("ZEA");

        /** How long shutdown waits for the last history write to land. */
        private static final long HISTORY_SHUTDOWN_TIMEOUT_MILLIS = 5_000;

        private static ClientLifecycle lifecycle;
        private static ChatSubsystem chat;
        private static InventoryButtons inventoryButtons;
        private static InventorySearch search;
        private static SlotLocks slotLocks;
        private static ZeaConfig config;
        private static HudSubsystem hud;
        private static MapHudRenderer mapHudRenderer;
        private static RepoSync repoSync;
        private static ExecutorService repoExecutor;
        private static ExecutorService historyExecutor;
        private static ExecutorService shareExecutor;
        private static ExecutorService captureExecutor;
        private static PasteService pasteService;
        private static CaptureRecorder captureRecorder;
        private static ExecutorService marketExecutor;
        private static MarketSubsystem market;
        private static SpawnCommandObserver spawnCommands;
        private static ShopDestinationTracker destinationTracker;
        private static ItemBrowser browser;
        private static MapSubsystem map;

        public static ClientLifecycle lifecycle() {
                return lifecycle;
        }

        public static ChatSubsystem chat() {
                return chat;
        }

        public static InventoryButtons inventoryButtons() {
                return inventoryButtons;
        }

        /** The inventory search bar's session state. */
        public static InventorySearch search() {
                return search;
        }

        /** The slot lock and binding state. */
        public static SlotLocks slotLocks() {
                return slotLocks;
        }

        public static ZeaConfig config() {
                return config;
        }

        /** The map subsystem (world map, minimap, claims and players). */
        public static MapSubsystem map() {
                return map;
        }

        /** The physical-shop market subsystem. */
        public static MarketSubsystem market() {
                return market;
        }

        /** The spawn navigation observer behind /n spawn and /t spawn tracking. */
        public static SpawnCommandObserver spawnCommands() {
                return spawnCommands;
        }

        /** The inventory shop-index browser session. */
        public static ItemBrowser browser() {
                return browser;
        }

        /** The shop destination (temporary waypoint) tracker. */
        public static ShopDestinationTracker destinationTracker() {
                return destinationTracker;
        }

        /**
         * One outgoing command reached vanilla's send path — the single
         * point typed commands, clicked links and macro dispatch share.
         * Spawn navigation commands are recorded with the position before
         * the send; everything else passes untouched, and nothing is ever
         * intercepted, canceled or rewritten.
         */
        public static void noteCommandIssued(String command) {
                SpawnCommandObserver observer = spawnCommands;
                if (observer == null || !lifecycle.onEarthMc()) {
                        return;
                }
                Minecraft client = Minecraft.getInstance();
                if (client.level == null || client.player == null) {
                        return;
                }
                observer.startFrom(command,
                                                client.level.dimension().identifier().toString(),
                                                lifecycle.earthMcServer().map(ServerIdentity::host).orElse("unknown"),
                                                client.player.getX(), client.player.getZ(),
                                                Instant.now())
                                .ifPresent(observer::observe);
        }

        /** The universal HUD element subsystem; see {@code /zeahud}. */
        public static HudSubsystem hud() {
                return hud;
        }

        @Override
        public void onInitializeClient() {
                LOGGER.info("ZEA loading");
                Path configDir = FabricLoader.getInstance().getConfigDir();
                // The organized config/zea/ layout; the historical flat
                // config/zea.json migrates into it once, with a backup kept.
                ZeaPaths paths = new ZeaPaths(configDir);
                config = new ZeaConfigStore(paths.migrateMainConfig()).load();
                historyExecutor = newHistoryExecutor();
                lifecycle = new ClientLifecycle();
                captureExecutor = newIoExecutor("ZEA-Capture");
                captureRecorder = new CaptureRecorder(
                                        paths.captureDir(),
                                        ComponentJson::serialize,
                                        captureExecutor,
                                        Minecraft.getInstance()::execute,
                                        stopped -> Minecraft.getInstance().gui.hud.getChat().addClientSystemMessage(
                                                                Component.literal("Capture finished on its own — " + stopped.recorded()
                                                                                                + " messages saved to " + stopped.file() + ".")));
                chat = new ChatSubsystem(
                                                lifecycle,
                                                new ClassificationPipeline(),
                                                config,
                                                captureRecorder,
                                                paths.historyDir(),
                                                historyExecutor);
                inventoryButtons = new InventoryButtons(
                                                paths.buttons(),
                                                new ButtonActivator(zea$commandSender(), ButtonActivator.DEFAULT_COOLDOWN_MILLIS));
                search = new InventorySearch();
                spawnCommands = new SpawnCommandObserver();
                destinationTracker = new ShopDestinationTracker();
                browser = new ItemBrowser();
                marketExecutor = newIoExecutor("ZEA-Market");
                // The claim cache is constructed here because the market's town
                // resolution reads it; the map subsystem reuses this instance
                // when its claim fetch pipeline lands.
                ClaimStore claimStore = new ClaimStore(System::currentTimeMillis);
                market = new MarketSubsystem(lifecycle, configDir.resolve("zea/market.json"), marketExecutor,
                                config.market, new ClaimTownResolver(claimStore));
                slotLocks = new SlotLocks(new SlotLockStore(paths.slotLocks()));
                // The universal HUD manager and the map engine behind its map
                // elements: the element specs seed their factory size from the
                // map config, position and scale belong to the manager (the
                // on-screen pixel state MapConfig deliberately does not carry).
                        hud = new HudSubsystem(paths.hudSettings());
                hud.register(
                                new net.zaminmc.zea.hud.HudElementSpec(
                                                Identifier.fromNamespaceAndPath("zea", "hud/minimap"),
                                                "Minimap",
                                                config.map.minimap.size, config.map.minimap.size,
                                                net.zaminmc.zea.hud.HudAnchor.TOP_RIGHT, 8, 8, true),
                                mapHudRenderer = new MapHudRenderer(MapHudRenderer.Kind.MINIMAP));
                hud.register(
                                new net.zaminmc.zea.hud.HudElementSpec(
                                                Identifier.fromNamespaceAndPath("zea", "hud/map"),
                                                "Map overlay",
                                                config.map.minimap.size * 2, config.map.minimap.size * 2,
                                                net.zaminmc.zea.hud.HudAnchor.BOTTOM_RIGHT, 8, 8, false),
                                new MapHudRenderer(MapHudRenderer.Kind.MAP_OVERLAY));
                hud.register(
                                new net.zaminmc.zea.hud.HudElementSpec(
                                                Identifier.fromNamespaceAndPath("zea", "hud/destination"),
                                                "Shop destination",
                                                180, 14,
                                                net.zaminmc.zea.hud.HudAnchor.TOP_RIGHT, 8, 28, false),
                                new ShopDestinationHud());
                        repoExecutor = newIoExecutor("ZEA-Repo");
                // The shared dataset sync: fetch and parse on the repo
                // executor, merge on the client thread into the market
                // index. The last accepted snapshot loads right now, on
                // the client thread — a restart serves the dataset from
                // disk before the network answers anything.
                repoSync = new RepoSync(new net.zaminmc.zea.map.source.MapHttp(repoExecutor), repoExecutor,
                                paths.sharedDataset(), records -> Minecraft.getInstance().execute(() -> {
                                        var result = DatasetMerger.merge(market.data(), records);
                                        if (result.shopsAdded() + result.observationsAppended() > 0) {
                                                ZeaClient.LOGGER.info(
                                                                "Shared dataset merged: {} shops added, {} observations appended ({} stale skipped, {} ties kept local)",
                                                                result.shopsAdded(), result.observationsAppended(),
                                                                result.staleSkipped(), result.tiesSkipped());
                                        }
                                }));
                DatasetMerger.merge(market.data(), SharedShops.loadCache(paths.sharedDataset()));
                ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
                                if (lifecycle.onEarthMc()) {
                                        repoSync.refresh();
                                }
                });
                ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> inventoryButtons.reset());
                // Pending spawn navigation belongs to one connection, like
                // the market's pending inspections.
                ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> spawnCommands.reset());
                // The waypoint belongs to one world session, like the
                // pending state above it.
                ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> destinationTracker.reset());
                // Slot locks belong to the server they were made on: the
                // connect hostname (the same key history isolates) decides
                // the context; menus and singleplayer share "generic".
                ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
                                ServerData server = client.getCurrentServer();
                                String host = server == null ? null
                                                : ServerIdentity.of(server.ip).map(ServerIdentity::host).orElse(null);
                                slotLocks.context(host);
                });
                ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> slotLocks.context(null));
                // The enchantment registry is data-driven per level: a
                // closed level invalidates every rarity budget and cache,
                // and an open tooltip scroller has nothing left to scroll.
                ClientPlayConnectionEvents.DISCONNECT.register(
                                                                (handler, client) -> {
                                                                                EnchantBudget.invalidateCaches();
                                                                                RarityEngine.invalidateCaches();
                                                                                TooltipScroller.reset();
                                                                });
                map = new MapSubsystem(config, lifecycle);
                ClientTickEvents.END_CLIENT_TICK.register(client -> {
                        map.tick(client);
                        spawnCommands.tick(
                                                        client.player == null ? null
                                                                        : new double[] {client.player.getX(), client.player.getZ()},
                                                        Instant.now());
                        // The destination's lifecycle reads the player's world
                        // and nation; the nation resolution (claim geometry)
                        // runs once a second, not per tick.
                        if (destinationTracker.destination() != null) {
                                destinationTick++;
                                if (destinationTick >= 20) {
                                        destinationTick = 0;
                                        destinationTracker.tick(currentWorld(), playerNation());
                                }
                        }
                        while (ZeaKeys.OPEN_MAP.consumeClick()) {
                                if (client.gui.screen() == null) {
                                        map.openWorldMap(client);
                                }
                        }
                });
                shareExecutor = newShareExecutor();
                pasteService = new PasteService(URI.create(PasteKey.BASE_URL), shareExecutor);
                ShareCoordinator coordinator = new ShareCoordinator(
                                        pasteService, inventoryButtons, config, Minecraft.getInstance()::execute);
                LiteralArgumentBuilder<FabricClientCommandSource> zea = ClientCommands.literal("zea");
                // Bare /zea opens the settings menu.
                zea.executes(context -> {
                        ConfigScreen.open(context.getSource().getClient(), config);
                        return 1;
                });
                ShareCommands.register(zea, coordinator);
                CaptureCommands.register(zea, captureRecorder, lifecycle::onEarthMc);
                HudCommands.register(zea, () -> hud);
                MarketEditorCommands.register(zea);
                ClientCommandRegistrationCallback.EVENT.register((dispatcher, buildContext) -> dispatcher.register(zea));
                // /zeabuttons — the button editor. Deferred through the
                // client queue like the settings screen: a client command
                // runs mid-keystroke in the chat screen, whose follow-up
                // close would otherwise wipe the editor before a frame.
                ClientCommandRegistrationCallback.EVENT.register((dispatcher, buildContext) -> dispatcher.register(
                                ClientCommands.literal("zeamap").executes(context -> {
                                        Minecraft client = context.getSource().getClient();
                                        client.execute(() -> map.openWorldMap(client));
                                        return 1;
                                })));
                ClientCommandRegistrationCallback.EVENT.register((dispatcher, buildContext) -> dispatcher.register(
                                ClientCommands.literal("zeabuttons").executes(context -> {
                                        Minecraft client = context.getSource().getClient();
                                        client.execute(() -> client.gui.setScreen(
                                                        new ButtonEditorScreen(null, inventoryButtons)));
                                        return 1;
                                })));
                ClientLifecycleEvents.CLIENT_STOPPING.register(client -> shutdownBackgroundWork());
                ZeaKeys.register();
        }

        private static int destinationTick;

        /** The player's current world, or null outside one. */
        private static String currentWorld() {
                Minecraft client = Minecraft.getInstance();
                return client.level == null ? null : client.level.dimension().identifier().toString();
        }

        /** The nation the player stands in, from claim geometry; null when unknown. */
        private static String playerNation() {
                Minecraft client = Minecraft.getInstance();
                if (market == null || market.townLookup() == null
                                || client.player == null || client.level == null) {
                        return null;
                }
                var resolved = market.townLookup().at(client.level.dimension().identifier().toString(),
                                (int) Math.floor(client.player.getX()), (int) Math.floor(client.player.getZ()));
                return resolved == null ? null : resolved.nation();
        }

        /**
         * The bounded, single-threaded executor history writes run on — one
         * daemon thread, a small bounded queue (writes coalesce upstream, so
         * more than a couple of pending tasks never happens), rejection
         * handled by the recorder rather than by blocking the client thread.
         */
        private static ExecutorService newHistoryExecutor() {
                return newIoExecutor("ZEA-History");
        }

        /** Same shape, for the share subsystem's uploads and downloads. */
        private static ExecutorService newShareExecutor() {
                return newIoExecutor("ZEA-Share");
        }

        private static ExecutorService newIoExecutor(String threadName) {
                return new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
                                        new ArrayBlockingQueue<>(4),
                                        task -> {
                                                Thread thread = new Thread(task, threadName);
                                                thread.setDaemon(true);
                                                return thread;
                                        });
        }

        /**
         * Flush pending history, close the paste client, finish any capture,
         * then drain the executors: submitted work finishes before shutdown,
         * and the bounded wait never hangs the game on a stuck disk or a
         * stuck request.
         */
        private static void shutdownBackgroundWork() {
                try {
                        chat.shutdownHistory();
                        if (mapHudRenderer != null) {
                                mapHudRenderer.close();
                        }
                        map.shutdown();
                } finally {
                        market.flushNow();
                        pasteService.close();
                        captureRecorder.shutdown();
                        drain(historyExecutor);
                        drain(captureExecutor);
                        drain(marketExecutor);
                        shareExecutor.shutdown();
                        if (hud != null) {
                                hud.shutdown();
                        }
                        if (repoExecutor != null) {
                                repoExecutor.shutdown();
                        }
                }
        }

        /** Shuts down an IO executor and waits for its last write to land. */
        private static void drain(ExecutorService executor) {
                executor.shutdown();
                try {
                        if (!executor.awaitTermination(HISTORY_SHUTDOWN_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
                                LOGGER.warn("Background writes did not finish within {} ms; continuing shutdown",
                                                        HISTORY_SHUTDOWN_TIMEOUT_MILLIS);
                        }
                } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                }
        }

        /** Sends a button's command through the play connection. */
        private static ButtonActivator.CommandSender zea$commandSender() {
                return command -> {
                        ClientPacketListener connection = Minecraft.getInstance().getConnection();
                        if (connection == null) {
                                throw new IllegalStateException("not connected to a server");
                        }
                        connection.sendCommand(command);
                };
        }
}
