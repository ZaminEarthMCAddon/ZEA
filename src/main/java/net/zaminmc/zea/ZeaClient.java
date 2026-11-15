package net.zaminmc.zea;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;
import net.zaminmc.zea.buttons.ButtonActivator;
import net.zaminmc.zea.buttons.ButtonEditorScreen;
import net.zaminmc.zea.buttons.InventoryButtons;
import net.zaminmc.zea.earthmc.ServerIdentity;
import net.zaminmc.zea.chat.ChatSubsystem;
import net.zaminmc.zea.chat.capture.CaptureCommands;
import net.zaminmc.zea.chat.capture.CaptureRecorder;
import net.zaminmc.zea.chat.capture.ComponentJson;
import net.zaminmc.zea.chat.classify.ClassificationPipeline;
import net.zaminmc.zea.config.ConfigScreen;
import net.zaminmc.zea.config.ZeaConfig;
import net.zaminmc.zea.config.ZeaConfigStore;
import net.zaminmc.zea.keybind.ZeaKeys;
import net.zaminmc.zea.lifecycle.ClientLifecycle;
import net.zaminmc.zea.market.MarketSubsystem;
import net.zaminmc.zea.misc.TooltipScroller;
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
        private static ExecutorService historyExecutor;
        private static ExecutorService shareExecutor;
        private static ExecutorService captureExecutor;
        private static PasteService pasteService;
        private static CaptureRecorder captureRecorder;
        private static ExecutorService marketExecutor;
        private static MarketSubsystem market;

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

        /** The physical-shop market subsystem. */
        public static MarketSubsystem market() {
                return market;
        }

        @Override
        public void onInitializeClient() {
                LOGGER.info("ZEA loading");
                Path configDir = FabricLoader.getInstance().getConfigDir();
                config = new ZeaConfigStore(configDir.resolve("zea.json")).load();
                historyExecutor = newHistoryExecutor();
                lifecycle = new ClientLifecycle();
                captureExecutor = newIoExecutor("ZEA-Capture");
                captureRecorder = new CaptureRecorder(
                                        configDir.resolve("zea/capture"),
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
                                                configDir.resolve("zea/history"),
                                                historyExecutor);
                inventoryButtons = new InventoryButtons(
                                                configDir.resolve("zea/buttons.json"),
                                                new ButtonActivator(zea$commandSender(), ButtonActivator.DEFAULT_COOLDOWN_MILLIS));
                search = new InventorySearch();
                slotLocks = new SlotLocks(new SlotLockStore(configDir.resolve("zea/slotlocks.json")));
                marketExecutor = newIoExecutor("ZEA-Market");
                market = new MarketSubsystem(lifecycle, configDir.resolve("zea/market.json"), marketExecutor);
                ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> inventoryButtons.reset());
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
                ClientCommandRegistrationCallback.EVENT.register((dispatcher, buildContext) -> dispatcher.register(zea));
                // /zeabuttons — the button editor. Deferred through the
                // client queue like the settings screen: a client command
                // runs mid-keystroke in the chat screen, whose follow-up
                // close would otherwise wipe the editor before a frame.
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
                } finally {
                        market.flushNow();
                        pasteService.close();
                        captureRecorder.shutdown();
                        drain(historyExecutor);
                        drain(captureExecutor);
                        drain(marketExecutor);
                        shareExecutor.shutdown();
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
