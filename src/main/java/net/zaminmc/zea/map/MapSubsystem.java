package net.zaminmc.zea.map;

import com.mojang.blaze3d.platform.NativeImage;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.zaminmc.zea.ZeaClient;
import net.zaminmc.zea.config.ZeaConfig;
import net.zaminmc.zea.mixin.GuiGraphicsExtractorAccessor;
import net.zaminmc.zea.map.cache.ClaimStore;
import net.zaminmc.zea.map.cache.PlayerStore;
import net.zaminmc.zea.map.cache.TileCache;
import net.zaminmc.zea.map.config.MapConfig;
import net.zaminmc.zea.map.model.Freshness;
import net.zaminmc.zea.map.model.MapCamera;
import net.zaminmc.zea.map.model.MapWorld;
import net.zaminmc.zea.map.model.PlayerMarker;
import net.zaminmc.zea.map.model.TileId;
import net.zaminmc.zea.map.model.TownClaim;
import net.zaminmc.zea.map.projection.MapProjection;
import net.zaminmc.zea.map.render.overlay.ClaimOverlay;
import net.zaminmc.zea.map.render.overlay.PlayerOverlay;
import net.zaminmc.zea.map.render.tile.OverlayDrawer;
import net.zaminmc.zea.map.render.tile.TileFrameDrawer;
import net.zaminmc.zea.map.xaero.XaeroGateway;
import net.zaminmc.zea.map.render.tile.TileFrameDriver;
import net.zaminmc.zea.map.render.tile.TileRenderer;
import net.zaminmc.zea.map.render.tile.TileTextures;
import net.zaminmc.zea.map.render.tile.TileUploads;
import net.zaminmc.zea.map.source.MapHttp;
import net.zaminmc.zea.map.source.squaremap.MarkersDocument;
import net.zaminmc.zea.map.source.squaremap.SquaremapClient;
import net.zaminmc.zea.map.source.squaremap.TileFetcher;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Set;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

/**
 * The map subsystem\u2019s facade and wiring: it owns the caches, clients and
 * drivers, runs the scheduled data refresh on the client tick, draws one
 * surface\u2019s shared layers, and serves the two surfaces \u2014 the HUD minimap
 * and the full-screen world map ({@code /zeamap}).
 *
 * <p>Everything the pure packages already decided stays decided here:
 * network is scheduled (never frame-driven), all mutable state lives on
 * the client thread (futures marshal back through
 * {@code Minecraft.execute} before touching the stores), every frame of
 * the tile pipeline runs through a {@link TileFrameDriver} in its one
 * correct order, and the two surfaces keep separate {@link TileRenderer}s
 * so their sticky coverage gates never fight.
 *
 * <p>Gating: the master switch, then EarthMC recognition. The map\u2019s own
 * \u201cOnly on EarthMC\u201d keeps it off unrecognized servers, and the mod-wide
 * \u201cEarthMC Only\u201d forces the same even when the map\u2019s own switch would
 * allow testing elsewhere.
 */
public final class MapSubsystem {

        /** How long shutdown waits for the last scheduled refresh to land. */
        private static final long SHUTDOWN_TIMEOUT_MILLIS = 3_000L;

        /** Stale drawable tiles revalidated per tick. */
        private static final int REVALIDATIONS_PER_TICK = 4;

        /** Above this many visible markers, player labels hide (crowd rule). */
        public static final int PLAYER_LABEL_CROWD_LIMIT = 48;

        /**
         * The water beyond the map's bounds — sampled from the live map's
         * own zoom-0 ocean tiles (deep Terra Nostra blue).
         */
        public static final int WATER = 0xFF2E4EAF;

        /**
         * The undiscovered treatment inside the bounds: a muted moss-slate
         * plate that reads as "land, no detail yet" against the water and
         * against real imagery — never black, never a missing texture.
         */
        public static final int UNDISCOVERED = 0xFF35403A;

        /** The world boundary's hairline: the browser row frame, halved. */
        public static final int WORLD_BORDER = 0x80B4B4B4;

        /** The GUI width assumed before a window exists (the common 1080p-at-2x unit). */
        private static final int FALLBACK_GUI_WIDTH = 960;

        private final ZeaConfig config;
        private final BooleanSupplier onEarthMc;

        private final ExecutorService executor;
        private final MapHttp http;
        private final SquaremapClient squaremap;
        private final TileCache<NativeImage> tiles;
        private final TileFetcher<NativeImage> fetcher;
        private final ClaimStore claims;
        private final PlayerStore players = new PlayerStore();
        private final TileTextures textures = new TileTextures();
        private final TileFrameDriver<NativeImage> minimapDriver;
        private final ClaimOverlay claimOverlay = new ClaimOverlay();
        private final PlayerOverlay playerOverlay = new PlayerOverlay();

        private final Map<MapWorld, MarkersDocument> markerDocuments = new EnumMap<>(MapWorld.class);
        private final AtomicReference<List<PlayerMarker>> lastFeed = new AtomicReference<>(List.of());

        private final Map<MapWorld, MapCamera> freeMinimapCameras = new EnumMap<>(MapWorld.class);
        private final Map<TileFrameDriver<NativeImage>, Double> lastCenterX = new IdentityHashMap<>();
        private final Map<TileFrameDriver<NativeImage>, Double> lastCenterZ = new IdentityHashMap<>();
        private final Map<TileFrameDriver<NativeImage>, List<TileRenderer.Quad>> lastQuads = new IdentityHashMap<>();

        private MapWorld playerWorld = MapWorld.EARTH;
        private boolean dimensionSupported = true;
        private long lastSurfaceDrawMs;

        public MapSubsystem(ZeaConfig config, net.zaminmc.zea.lifecycle.ClientLifecycle lifecycle) {
                this(config, lifecycle::onEarthMc);
        }

        public MapSubsystem(ZeaConfig config, BooleanSupplier onEarthMc) {
                this.config = config;
                this.onEarthMc = onEarthMc;
                this.executor = Executors.newFixedThreadPool(2, task -> {
                        Thread thread = new Thread(task, "ZEA-Map");
                        thread.setDaemon(true);
                        return thread;
                });
                this.http = new MapHttp(executor);
                this.squaremap = new SquaremapClient(http, executor);
                this.tiles = new TileCache<>();
                this.fetcher = new TileFetcher<>(http, executor, tiles, NativeImage::read);
                this.claims = new ClaimStore(System::currentTimeMillis);
                this.minimapDriver = new TileFrameDriver<>(tiles, new TileRenderer(tiles),
                                        new TileUploads<>(), textures, textures, fetcher::request);
        }

        // ------------------------------------------------------------------
        // Gating
        // ------------------------------------------------------------------

        /** Whether any map functionality may run right now. */
        public boolean active() {
                return featureActive(config, onEarthMc.getAsBoolean());
        }

        /** The pure gating rule; pinned by tests. */
        public static boolean featureActive(ZeaConfig config, boolean onEarthMc) {
                MapConfig map = config.map;
                if (map == null || !map.enabled) {
                        return false;
                }
                if (onEarthMc) {
                        return true;
                }
                // Off EarthMC: the map\u2019s own gate allows testing elsewhere, but the
                // mod-wide EarthMC Only wins when it is on.
                return !map.earthMcOnly && !config.earthMcOnly();
        }

        /** The world the local player stands in. */
        public MapWorld playerWorld() {
                return playerWorld;
        }

        /** Whether the current dimension has a map at all (the Nether does not). */
        public boolean dimensionSupported() {
                return dimensionSupported;
        }

        // ------------------------------------------------------------------
        // Tick: world tracking and scheduled refresh
        // ------------------------------------------------------------------

        /** Runs on the client tick; drives every scheduled refresh. */
        public void tick(Minecraft client) {
                if (!active()) {
                        return;
                }
                LocalPlayer player = client.player;
                if (player == null || client.level == null) {
                        return;
                }
                trackDimension(player);
                long now = System.currentTimeMillis();
                refreshClaims(now, client);
                refreshPlayers(now, client);
                revalidateStale();
        }

        private void trackDimension(LocalPlayer player) {
                var location = player.level().dimension().identifier();
                String namespace = location.getNamespace();
                String path = location.getPath();
                MapWorld resolved = MapWorld.ofDimension(namespace, path);
                boolean supported = minimapSupported(namespace, path);
                if (supported != dimensionSupported || resolved != playerWorld) {
                        dimensionSupported = supported;
                        playerWorld = resolved;
                        tiles.setReservedWorld(resolved);
                        ZeaClient.LOGGER.debug("Map world resolved to {} (supported: {})", resolved, supported);
                }
        }

        /**
         * Whether a dimension carries map data: EarthMC publishes the Earth
         * overworld and the Moon, and policy forbids mapping the Nether and
         * the End even though their coordinates would project.
         */
        public static boolean minimapSupported(String namespace, String path) {
                return !("minecraft".equals(namespace) && ("the_nether".equals(path) || "the_end".equals(path)));
        }

        /** Worlds whose last claims failure has already been warned about. */
        private final java.util.Set<MapWorld> claimsFetchWarned = java.util.EnumSet.noneOf(MapWorld.class);

        private void refreshClaims(long now, Minecraft client) {
                for (MapWorld world : worldsNeedingClaims()) {
                        Freshness freshness = squaremap.claimsFreshness(world);
                        if (!MapRefreshPolicy.due(now, freshness.lastAttemptMs(), MapRefreshPolicy.MARKERS_MS)) {
                                continue;
                        }
                        MarkersDocument previous = markerDocuments.get(world);
                        squaremap.fetchMarkers(world, previous).whenComplete((document, failure) -> client.execute(() -> {
                                if (failure != null) {
                                        // Freshness already records the failure and the status
                                        // line shows the stale age; one warn per world until a
                                        // fetch succeeds, debug afterwards.
                                        if (claimsFetchWarned.add(world)) {
                                                ZeaClient.LOGGER.warn("Claims refresh for {} failed: {}", world, failure.toString());
                                        } else {
                                                ZeaClient.LOGGER.debug("Claims refresh for {} failed: {}", world, failure.toString());
                                        }
                                        return;
                                }
                                claimsFetchWarned.remove(world);
                                markerDocuments.put(world, document);
                                claims.publish(world, document.towns());
                        }));
                }
        }

        private List<MapWorld> worldsNeedingClaims() {
                // The Xaero overlay follows the player's world, and the
                // minimap draws it too — one world needs claims at a time.
                return List.of(playerWorld);
        }

        private void refreshPlayers(long now, Minecraft client) {
                Freshness freshness = squaremap.playersFreshness();
                boolean watched = MapRefreshPolicy.active(now, lastSurfaceDrawMs);
                if (!MapRefreshPolicy.due(now, freshness.lastAttemptMs(), MapRefreshPolicy.playersPeriod(watched))) {
                        return;
                }
                String key = config.map.apiKey;
                String bearer = key == null || key.isBlank() ? null : key;
                squaremap.fetchPlayers(lastFeed.get(), bearer).whenComplete((feed, failure) -> client.execute(() -> {
                        if (failure != null) {
                                ZeaClient.LOGGER.debug("Player feed refresh failed: {}", failure.toString());
                                return;
                        }
                        lastFeed.set(feed);
                        players.publish(feed, System.currentTimeMillis());
                }));
        }

        private void revalidateStale() {
                revalidate(minimapDriver, REVALIDATIONS_PER_TICK);
        }

        private int revalidate(TileFrameDriver<NativeImage> driver, int budget) {
                List<TileRenderer.Quad> quads = lastQuads.getOrDefault(driver, List.of());
                for (TileRenderer.Quad quad : quads) {
                        if (budget <= 0) {
                                return 0;
                        }
                        // The cache refuses anything not stale, not ready or already
                        // revalidating, so this is a cheap no-op most ticks.
                        fetcher.revalidate(quad.texture());
                        budget--;
                }
                return budget;
        }

        // ------------------------------------------------------------------
        // Cameras
        // ------------------------------------------------------------------

        /** The minimap camera: centered on the player, at the configured zoom. */
        public MapCamera minimapCamera(double x, double z) {
                MapConfig.Minimap minimap = config.map.minimap;
                double pixelsPerBlock = minimap.size / (double) Math.max(1, minimap.blocksAcross);
                if (minimap.centerPlayer) {
                        return new MapCamera(playerWorld, x, z, pixelsPerBlock);
                }
                // A fixed-offset camera (the drag editor\u2019s future domain) keeps
                // its stored center and only inherits the zoom.
                MapCamera stored = freeMinimapCameras.get(playerWorld);
                if (stored == null) {
                        stored = new MapCamera(playerWorld, x, z, pixelsPerBlock);
                        freeMinimapCameras.put(playerWorld, stored);
                }
                return new MapCamera(playerWorld, stored.centerX(), stored.centerZ(), pixelsPerBlock);
        }

        // ------------------------------------------------------------------
        // Surfaces
        // ------------------------------------------------------------------

        /**
         * Opens the world map — Xaero's GuiMap, the engine ZEA's EarthMC
         * layer renders inside. Xaero is the renderer: terrain, camera,
         * zoom and chrome are its; ZEA's soft mixin draws the claim and
         * player layer over its terrain at its camera. Without Xaero
         * installed there is no map of ZEA's own anymore — the player
         * gets told what to install instead of a second-rate screen.
         */
        public void openWorldMap(Minecraft client) {
                if (!active() || !config.map.worldMapEnabled) {
                        return;
                }
                if (FabricLoader.getInstance().isModLoaded("xaeroworldmap")) {
                        XaeroGateway.openWorldMap(client);
                        return;
                }
                LocalPlayer player = client.player;
                if (player != null) {
                        client.gui.hud.getChat().addClientSystemMessage(Component.literal(
                                        "Install Xaero's World Map for the EarthMC map — ZEA draws its "
                                                        + "claims and players inside it.")
                                        .withStyle(ChatFormatting.YELLOW));
                }
        }

        /** Marks a surface as drawn this frame \u2014 the refresh policy\u2019s input. */
        public void surfaceDrawn() {
                lastSurfaceDrawMs = System.currentTimeMillis();
        }

        // ------------------------------------------------------------------
        // Data access for the surfaces
        // ------------------------------------------------------------------

        /** The world map\u2019s config section. */
        public MapConfig.WorldMap worldMapConfig() {
                return config.map.worldMap;
        }

        /** The shared display config section. */
        public MapConfig.SharedDisplay sharedDisplayConfig() {
                return config.map.sharedDisplay;
        }

        /** Whether the status line is enabled. */
        public boolean showFreshnessConfig() {
                return config.map.showFreshness;
        }

        /** The effective claims of a world (the renderer\u2019s draw list). */
        public List<TownClaim> claimsOf(MapWorld world) {
                return claims.claims(world);
        }

        /** The player markers with their interpolation anchors. */
        public PlayerStore playerStore() {
                return players;
        }

        /** Freshness text for the status line of one world. */
        public String freshnessText(MapWorld world, long nowMs) {
                Freshness claimsFresh = squaremap.claimsFreshness(world);
                Freshness playersFresh = squaremap.playersFreshness();
                return claimsFresh.statusText(nowMs, "Claims") + " \u00b7 "
                                        + playersFresh.statusText(nowMs, "Players");
        }

        /** The self-override for the world the local player stands in, if any. */
        public PlayerOverlay.@Nullable SelfPosition selfPosition(MapWorld viewedWorld) {
                LocalPlayer player = Minecraft.getInstance().player;
                if (player == null || viewedWorld != playerWorld) {
                        return null;
                }
                return new PlayerOverlay.SelfPosition(player.getX(), player.getZ(), player.getYRot());
        }

        /** The local player\u2019s name, for the self marker. */
        @Nullable
        public String selfName() {
                LocalPlayer player = Minecraft.getInstance().player;
                return player == null ? null : player.getGameProfile().name();
        }

        // ------------------------------------------------------------------
        // Drawing one surface\u2019s shared layers
        // ------------------------------------------------------------------

        /** The layer switches both surfaces derive from their config sections. */
        public record Layers(
                                        boolean claimFills,
                                        boolean claimOutlines,
                                        boolean players,
                                        boolean playerLabels,
                                        boolean townLabels,
                                        int fillAlpha,
                                        int outlineAlpha) {
        }

        /** The driver-backed surface the caller wants drawn: the HUD minimap. */
        public enum Surface {
                        MINIMAP
        }

        /**
         * Draws the minimap's world content: the flat path — backdrop
         * treatment, one driver frame, tile imagery, claims and player
         * markers, labels. The world map is Xaero's now; the only surface
         * ZEA draws is the HUD minimap. The caller owns the chrome:
         * scissor, frame, coordinates.
         */
        public void drawSurface(GuiGraphicsExtractor graphics, Font font, MapProjection projection,
                                int originX, int originY, Surface surface, boolean moving, Layers layers) {
                MapCamera camera = projection.camera();

                drawFlatBackdrop(graphics, projection, originX, originY);

                TileFrameDriver<NativeImage> driver = minimapDriver;
                Double previousX = lastCenterX.get(driver);
                Double previousZ = lastCenterZ.get(driver);
                lastCenterX.put(driver, camera.centerX());
                lastCenterZ.put(driver, camera.centerZ());
                TileRenderer.Motion motion = previousX == null || previousZ == null || !moving
                                        ? TileRenderer.Motion.STILL
                                        : new TileRenderer.Motion(camera.centerX() - previousX, camera.centerZ() - previousZ);
                TileRenderer.FramePlan plan = driver.frame(projection, motion, System::nanoTime);
                lastQuads.put(driver, plan.detailed().isEmpty() ? plan.overview() : plan.detailed());

                float centerX = originX + projection.surfaceWidth() / 2.0f;
                float centerY = originY + projection.surfaceHeight() / 2.0f;
                TileFrameDrawer.draw(graphics, textures, plan, centerX, centerY, projection.rotationRadians());
                // The overlay stratum: fills sort before textured quads
                // within one stratum, so claims and players must travel in
                // their own to draw over the imagery.
                graphics.nextStratum();
                if (layers.claimFills() || layers.claimOutlines()) {
                        List<ClaimOverlay.Rect> rects = claimOverlay.plan(claimsOf(projection.world()), projection,
                                                layers.claimFills(), layers.claimOutlines(), layers.fillAlpha(), layers.outlineAlpha(), 1.0);
                        OverlayDrawer.drawClaims(graphics, rects, centerX, centerY, projection.rotationRadians());
                }
                List<PlayerOverlay.Marker> markers = List.of();
                if (layers.players()) {
                        markers = playerOverlay.plan(
                                                playerStore().markers(projection.world()),
                                                playerStore().previous(projection.world()),
                                                System.currentTimeMillis(),
                                                playerStore().publishedAtMs(),
                                                playerStore().previousAtMs(),
                                                selfName(),
                                                selfPosition(projection.world()),
                                                projection);
                        OverlayDrawer.drawMarkers(graphics, markers, centerX, centerY, projection.rotationRadians());
                }
                if (layers.townLabels()) {
                        drawTownLabels(graphics, font, projection);
                }
                if (layers.players() && layers.playerLabels() && markers.size() <= PLAYER_LABEL_CROWD_LIMIT) {
                        drawPlayerLabels(graphics, font, projection, markers);
                }
                surfaceDrawn();
        }

        /**
         * The flat path\u2019s backdrop: water beyond the world\u2019s bounds, the
         * undiscovered plate inside them, and the boundary\u2019s hairline \u2014
         * the full fixed-extent treatment, drawn before any imagery so a
         * missing tile never reads as void.
         */
        private void drawFlatBackdrop(GuiGraphicsExtractor graphics, MapProjection projection,
                        int originX, int originY) {
                MapWorld world = projection.world();
                int surfaceLeft = originX;
                int surfaceTop = originY;
                int surfaceRight = originX + projection.surfaceWidth();
                int surfaceBottom = originY + projection.surfaceHeight();
                graphics.fill(surfaceLeft, surfaceTop, surfaceRight, surfaceBottom, WATER);
                double left = projection.screenX(-world.worldWidth / 2.0, 0.0);
                double right = projection.screenX(world.worldWidth / 2.0, 0.0);
                double top = projection.screenY(0.0, -world.worldHeight / 2.0);
                double bottom = projection.screenY(0.0, world.worldHeight / 2.0);
                int plateLeft = Math.max(surfaceLeft, (int) Math.floor(left));
                int plateTop = Math.max(surfaceTop, (int) Math.floor(top));
                int plateRight = Math.min(surfaceRight, (int) Math.ceil(right));
                int plateBottom = Math.min(surfaceBottom, (int) Math.ceil(bottom));
                if (plateRight > plateLeft && plateBottom > plateTop) {
                        graphics.fill(plateLeft, plateTop, plateRight, plateBottom, UNDISCOVERED);
                        graphics.fill(plateLeft, plateTop, plateRight, plateTop + 1, WORLD_BORDER);
                        graphics.fill(plateLeft, plateBottom - 1, plateRight, plateBottom, WORLD_BORDER);
                        graphics.fill(plateLeft, plateTop + 1, plateLeft + 1, plateBottom - 1, WORLD_BORDER);
                        graphics.fill(plateRight - 1, plateTop + 1, plateRight, plateBottom - 1, WORLD_BORDER);
                }
        }

        private void drawTownLabels(GuiGraphicsExtractor graphics, Font font, MapProjection projection) {
                double ppb = projection.camera().pixelsPerBlock();
                for (TownClaim town : claimsOf(projection.world())) {
                        double extentBlocks = Math.max(town.maxX() - town.minX(), town.maxZ() - town.minZ());
                        if (extentBlocks * ppb < font.width(town.name()) + 10) {
                                continue;
                        }
                        int x = (int) Math.round(projection.screenX(town.centerX(), town.centerZ()));
                        int y = (int) Math.round(projection.screenY(town.centerX(), town.centerZ()));
                        graphics.centeredText(font, town.name(), x, y - font.lineHeight / 2, 0xFFFFFFFF);
                }
        }

        private void drawPlayerLabels(GuiGraphicsExtractor graphics, Font font, MapProjection projection,
                                List<PlayerOverlay.Marker> markers) {
                for (PlayerOverlay.Marker marker : markers) {
                        if (marker.self()) {
                                continue;
                        }
                        int x = (int) Math.round(projection.screenX(marker.worldX(), marker.worldZ())) + 4;
                        int y = (int) Math.round(projection.screenY(marker.worldX(), marker.worldZ())) - font.lineHeight / 2;
                        graphics.text(font, marker.name(), x, y, 0xFFFFFFFF);
                }
        }

        // ------------------------------------------------------------------
        // Shutdown
        // ------------------------------------------------------------------

        /** Stops scheduling, drains the executor, releases every GPU texture. */
        public void shutdown() {
                executor.shutdown();
                try {
                        if (!executor.awaitTermination(SHUTDOWN_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
                                ZeaClient.LOGGER.warn("Map refreshes did not finish within {} ms; continuing shutdown",
                                                                SHUTDOWN_TIMEOUT_MILLIS);
                        }
                } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                }
                textures.freeAll();
        }
}
