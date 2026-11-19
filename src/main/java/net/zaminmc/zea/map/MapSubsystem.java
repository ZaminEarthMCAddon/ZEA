package net.zaminmc.zea.map;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.player.LocalPlayer;
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
import net.zaminmc.zea.map.geo.CountryMosaic;
import net.zaminmc.zea.map.geo.CountryRegistry;
import net.zaminmc.zea.map.projection.GlobeTransition;
import net.zaminmc.zea.map.projection.MapProjection;
import net.zaminmc.zea.map.render.globe.GlobeMeshElement;
import net.zaminmc.zea.map.render.globe.GlobeMeshPlanner;
import net.zaminmc.zea.map.render.overlay.ClaimOverlay;
import net.zaminmc.zea.map.render.overlay.PlayerOverlay;
import net.zaminmc.zea.map.render.tile.OverlayDrawer;
import net.zaminmc.zea.map.render.tile.TileFrameDrawer;
import net.zaminmc.zea.map.render.tile.TileFrameDriver;
import net.zaminmc.zea.map.render.tile.TileRenderer;
import net.zaminmc.zea.map.render.tile.TileTextures;
import net.zaminmc.zea.map.render.tile.TileUploads;
import net.zaminmc.zea.map.render.worldmap.WorldMapScreen;
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
        private final TileFrameDriver<NativeImage> worldMapDriver;
        /** Serves the curved phases: the zoom-0 layer the globe mesh samples. */
        private final TileFrameDriver<NativeImage> globeDriver;
        /** The country-association registry (deterministic, bundled dataset). */
        private final CountryRegistry countryRegistry;
        /** The published country aggregates per world; built off the render thread. */
        private volatile Map<MapWorld, List<CountryMosaic.Aggregate>> aggregates = Map.of();
        /** One aggregation rebuild at a time; changes during one coalesce into another. */
        private final java.util.concurrent.atomic.AtomicBoolean aggregating =
                                        new java.util.concurrent.atomic.AtomicBoolean(false);
        private volatile boolean aggregationRescheduled;
        private final ClaimOverlay claimOverlay = new ClaimOverlay();
        private final PlayerOverlay playerOverlay = new PlayerOverlay();

        private final Map<MapWorld, MarkersDocument> markerDocuments = new EnumMap<>(MapWorld.class);
        private final AtomicReference<List<PlayerMarker>> lastFeed = new AtomicReference<>(List.of());

        private final Map<MapWorld, MapCamera> worldCameras = new EnumMap<>(MapWorld.class);
        private final Map<MapWorld, MapCamera> freeMinimapCameras = new EnumMap<>(MapWorld.class);
        private final Map<TileFrameDriver<NativeImage>, Double> lastCenterX = new IdentityHashMap<>();
        private final Map<TileFrameDriver<NativeImage>, Double> lastCenterZ = new IdentityHashMap<>();
        private final Map<TileFrameDriver<NativeImage>, List<TileRenderer.Quad>> lastQuads = new IdentityHashMap<>();

        private MapWorld playerWorld = MapWorld.EARTH;
        private boolean dimensionSupported = true;
        @Nullable
        private WorldMapScreen worldMapScreen;
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
                this.worldMapDriver = new TileFrameDriver<>(tiles, new TileRenderer(tiles),
                                        new TileUploads<>(), textures, textures, fetcher::request);
                this.globeDriver = new TileFrameDriver<>(tiles, new TileRenderer(tiles),
                                        new TileUploads<>(), textures, textures, fetcher::request);
                this.countryRegistry = loadCountryRegistry();
        }

        /** The bundled country dataset; a broken or missing file leaves an empty registry. */
        private static CountryRegistry loadCountryRegistry() {
                try (var stream = MapSubsystem.class.getResourceAsStream("/assets/zea/map/countries.json")) {
                        if (stream == null) {
                                ZeaClient.LOGGER.warn("The country dataset is missing; the global view aggregates nothing");
                                return CountryRegistry.parse("{\"version\":1,\"countries\":[]}");
                        }
                        return CountryRegistry.parse(new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
                } catch (Exception failure) {
                        ZeaClient.LOGGER.warn("The country dataset failed to parse: {}", failure.toString());
                        return CountryRegistry.parse("{\"version\":1,\"countries\":[]}");
                }
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
                                        // line shows the stale age; keep the log quiet.
                                        ZeaClient.LOGGER.debug("Claims refresh for {} failed: {}", world, failure.toString());
                                        return;
                                }
                                markerDocuments.put(world, document);
                                claims.publish(world, document.towns());
                                scheduleAggregation();
                        }));
                }
        }

        private List<MapWorld> worldsNeedingClaims() {
                List<MapWorld> needed = new ArrayList<>(2);
                needed.add(playerWorld);
                WorldMapScreen screen = worldMapScreen;
                if (screen != null && screen.viewedWorld() != playerWorld) {
                        needed.add(screen.viewedWorld());
                }
                return needed;
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
                int budget = REVALIDATIONS_PER_TICK;
                budget = revalidate(minimapDriver, budget);
                revalidate(worldMapDriver, budget);
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
        // Country aggregation
        // ------------------------------------------------------------------

        /**
         * Schedules one mosaic rebuild off the render thread. A change
         * that lands while a rebuild runs coalesces into one follow-up —
         * the snapshot never blocks a refresh, and the draw never sees a
         * half-built map.
         */
        private void scheduleAggregation() {
                if (!aggregating.compareAndSet(false, true)) {
                        aggregationRescheduled = true;
                        return;
                }
                executor.submit(() -> {
                        try {
                                Map<MapWorld, List<CountryMosaic.Aggregate>> built = new EnumMap<>(MapWorld.class);
                                for (MapWorld world : MapWorld.values()) {
                                        built.put(world, CountryMosaic.build(claims.claims(world), countryRegistry));
                                }
                                Minecraft.getInstance().execute(() -> aggregates = Map.copyOf(built));
                        } catch (Exception failure) {
                                ZeaClient.LOGGER.warn("Country aggregation failed: {}", failure.toString());
                        } finally {
                                aggregating.set(false);
                                if (aggregationRescheduled) {
                                        aggregationRescheduled = false;
                                        scheduleAggregation();
                                }
                        }
                });
        }

        /** The country aggregates of a world, or an empty list before the first rebuild. */
        public List<CountryMosaic.Aggregate> aggregatesOf(MapWorld world) {
                List<CountryMosaic.Aggregate> list = aggregates.get(world);
                return list == null ? List.of() : list;
        }

        /** The association registry, for the report and the corrections UI. */
        public CountryRegistry countryRegistry() {
                return countryRegistry;
        }

        /**
         * The global-LOD gate: the mosaic and flags draw when the map
         * shows more than a few viewports of the world — past that the
         * towns themselves resolve and the country layer fades out. A
         * pure function of the zoom, so it cannot flicker.
         */
        public static boolean globalZoom(MapWorld world, double pixelsPerBlock, int surfaceWidth) {
                double fit = GlobeTransition.fitPixelsPerBlock(world, surfaceWidth);
                return pixelsPerBlock < fit * 4.0;
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

        /** The world map camera of a world, created at the player\u2019s position on first use. */
        public MapCamera worldCamera(MapWorld world, double playerX, double playerZ) {
                MapCamera stored = worldCameras.get(world);
                if (stored == null) {
                        stored = new MapCamera(world, playerX, playerZ, worldZoom());
                        worldCameras.put(world, stored);
                }
                return stored;
        }

        /** Stores the world map camera of its world (pan/zoom from the screen). */
        public void storeWorldCamera(MapCamera camera) {
                worldCameras.put(camera.world(), camera);
        }

        private double worldZoom() {
                Minecraft client = Minecraft.getInstance();
                int width = client == null ? FALLBACK_GUI_WIDTH : client.getWindow().getGuiScaledWidth();
                int blocksAcross = Math.max(1, config.map.worldMap.defaultBlocksAcross);
                return Math.clamp(width / (double) blocksAcross,
                                        MapCamera.MIN_PIXELS_PER_BLOCK, MapCamera.MAX_PIXELS_PER_BLOCK);
        }

        // ------------------------------------------------------------------
        // Surfaces
        // ------------------------------------------------------------------

        /** The open world map screen, or null while closed. */
        @Nullable
        public WorldMapScreen worldMapScreen() {
                return worldMapScreen;
        }

        /** Opens the full-screen world map. */
        public void openWorldMap(Minecraft client) {
                if (!active() || !config.map.worldMapEnabled) {
                        return;
                }
                LocalPlayer player = client.player;
                double x = player == null ? 0 : player.getX();
                double z = player == null ? 0 : player.getZ();
                MapWorld viewed = switch (config.map.defaultWorld) {
                        case EARTH -> MapWorld.EARTH;
                        case MOON -> MapWorld.MOON;
                        case AUTO -> playerWorld;
                };
                worldCamera(viewed, x, z);
                WorldMapScreen screen = new WorldMapScreen(this, viewed, config.map.worldMap.followPlayer);
                worldMapScreen = screen;
                client.gui.setScreen(screen);
        }

        /** The screen reports its own lifetime. */
        public void worldMapClosed(WorldMapScreen screen) {
                if (worldMapScreen == screen) {
                        worldMapScreen = null;
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

        /** The driver-backed surface the caller wants drawn. */
        public enum Surface {
                        MINIMAP, WORLD_MAP
        }

        /**
         * Draws one map surface\u2019s world content. The camera\u2019s zoom picks
         * the path: at and above the whole-map fit scale the flat path
         * (backdrop treatment, one driver frame, tile imagery, claims and
         * player markers, labels); below it the curved path \u2014 the same
         * zoom-0 imagery wrapped onto the flat-to-globe surface family,
         * with the water disc and the undiscovered plate behind it. The
         * caller owns the chrome: scissor, frame, coordinates.
         */
        public void drawSurface(GuiGraphicsExtractor graphics, Font font, MapProjection projection,
                                int originX, int originY, Surface surface, boolean moving, Layers layers) {
                MapCamera camera = projection.camera();
                double fit = GlobeTransition.fitPixelsPerBlock(camera.world(), projection.surfaceWidth());
                double curvature = GlobeTransition.curvatureOf(camera.pixelsPerBlock(), fit);
                if (surface == Surface.WORLD_MAP && curvature > 0.0) {
                        drawCurvedSurface(graphics, font, projection, originX, originY, curvature, fit);
                        surfaceDrawn();
                        return;
                }

                drawFlatBackdrop(graphics, projection, originX, originY);

                TileFrameDriver<NativeImage> driver = surface == Surface.MINIMAP ? minimapDriver : worldMapDriver;
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
                boolean global = globalZoom(camera.world(), camera.pixelsPerBlock(), projection.surfaceWidth());
                if (global) {
                        // The global LOD: country territory and flags replace
                        // the per-town layers — no six thousand claim rects at
                        // the fit scale.
                        drawCountryLayerFlat(graphics, projection, centerX, centerY);
                        drawCountryFlags(graphics, font, projection, centerX, centerY, camera.world(), null, 0.0, 0.0);
                        surfaceDrawn();
                        return;
                }
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

        /**
         * The curved path: the same zoom-0 imagery the flat path\u2019s
         * overview layer serves, wrapped onto the flat-to-globe family.
         * The globe driver\u2019s synthetic fit-scale frame requests exactly
         * the tiles the mesh samples; the planner\u2019s layers submit through
         * the render state \u2014 water disc, world plate, imagery cells \u2014 and
         * every quad clips to the active scissor.
         */
        private void drawCurvedSurface(GuiGraphicsExtractor graphics, Font font, MapProjection projection,
                        int originX, int originY, double curvature, double fit) {
                MapCamera camera = projection.camera();
                MapWorld world = camera.world();
                double recede = GlobeTransition.globeScaleOf(camera.pixelsPerBlock(), fit);
                double radius = GlobeTransition.globeRadius(projection.surfaceWidth(), projection.surfaceHeight())
                                        * recede;
                int halfW = projection.surfaceWidth() / 2;
                int halfH = projection.surfaceHeight() / 2;

                // The mesh\u2019s tiles, through the driver\u2019s budgets: a
                // synthetic flat frame at the fit scale centered on the
                // camera enumerates exactly the zoom-0 set.
                MapCamera fitCamera = new MapCamera(world, camera.centerX(), camera.centerZ(), fit);
                MapProjection fitProjection = MapProjection.northUp(fitCamera,
                                                        projection.surfaceWidth(), projection.surfaceHeight());
                TileRenderer.Motion motion = TileRenderer.Motion.STILL;
                TileRenderer.FramePlan plan = globeDriver.frame(fitProjection, motion, System::nanoTime);
                lastQuads.put(globeDriver, plan.detailed().isEmpty() ? plan.overview() : plan.detailed());

                float centerX = originX + projection.surfaceWidth() / 2.0f;
                float centerY = originY + projection.surfaceHeight() / 2.0f;
                GlobeMeshPlanner.Plan mesh = GlobeMeshPlanner.plan(world, camera.centerX(), camera.centerZ(),
                                                        radius, curvature, Math.hypot(halfW, halfH) + 64.0,
                                                        halfW, halfH);

                ScreenRectangle scissor = currentScissor(graphics);
                List<GlobeMeshElement.Quad> colored = new ArrayList<>(mesh.colored().size());
                int discQuads = GlobeMeshPlanner.DISC_SEGMENTS;
                for (int i = 0; i < mesh.colored().size(); i++) {
                        GlobeMeshPlanner.ColoredCell cell = mesh.colored().get(i);
                        int color = i < discQuads ? WATER : UNDISCOVERED;
                        colored.add(absolute(cell.xs(), cell.ys(), color, centerX, centerY));
                }
                if (!colored.isEmpty()) {
                        ((GuiGraphicsExtractorAccessor) (Object) graphics).zea$guiRenderState()
                                                        .addGuiElement(GlobeMeshElement.colored(colored, scissor));
                }

                // Imagery cells grouped per tile texture: one element per
                // tile that has a GPU copy, so the draws batch per texture.
                java.util.Map<TileId, List<GlobeMeshElement.Quad>> groups = new java.util.LinkedHashMap<>();
                for (GlobeMeshPlanner.Cell cell : mesh.cells()) {
                        if (textures.viewOf(cell.tile()) == null) {
                                continue;
                        }
                        groups.computeIfAbsent(cell.tile(), ignored -> new ArrayList<>())
                                                .add(new GlobeMeshElement.Quad(cell.xs(), cell.ys(),
                                                                        cell.u0(), cell.v0(), cell.u1(), cell.v1(), 0xFFFFFFFF));
                }
                for (var entry : groups.entrySet()) {
                        TextureSetup setup = TextureSetup.singleTexture(
                                                        textures.viewOf(entry.getKey()), TileTextures.tileSampler());
                        ((GuiGraphicsExtractorAccessor) (Object) graphics).zea$guiRenderState()
                                                        .addGuiElement(GlobeMeshElement.textured(
                                                                        entry.getValue(), setup, scissor));
                }

                // The country layer rides its own stratum above the imagery
                // (colored fills sort before textured quads in one stratum).
                graphics.nextStratum();
                drawCountryLayerCurved(graphics, projection, centerX, centerY, curvature, fit);
                drawCountryFlags(graphics, font, projection, centerX, centerY, world,
                                                new double[] {camera.centerX(), camera.centerZ()},
                                                radius, curvature);
        }

        /**
         * The country mosaic on the flat path: each visible cell of the
         * camera's mip fills as a world-aligned rect in the projection's
         * rotated frame — the same geometry route the claim fills take.
         */
        private void drawCountryLayerFlat(GuiGraphicsExtractor graphics, MapProjection projection,
                        float centerX, float centerY) {
                List<CountryMosaic.Aggregate> list = aggregatesOf(projection.world());
                if (list.isEmpty()) {
                        return;
                }
                double ppb = projection.camera().pixelsPerBlock();
                int mip = CountryMosaic.mipFor(ppb, 3.0);
                int cellBlocks = CountryMosaic.CELL << mip;
                int fillAlpha = 0xB0;
                for (CountryMosaic.Aggregate aggregate : list) {
                        int argb = (fillAlpha << 24) | (aggregate.argb() & 0xFFFFFF);
                        Set<Long> cells = aggregate.cells(mip);
                        for (long packed : cells) {
                                int cx = CountryMosaic.unpackX(packed);
                                int cz = CountryMosaic.unpackZ(packed);
                                double wx0 = (double) cx * cellBlocks;
                                double wz0 = (double) cz * cellBlocks;
                                double wx1 = wx0 + cellBlocks;
                                double wz1 = wz0 + cellBlocks;
                                double sx0 = projection.screenX(wx0, wz0);
                                double sx1 = projection.screenX(wx1, wz1);
                                double sy0 = projection.screenY(wx0, wz0);
                                double sy1 = projection.screenY(wx0, wz1);
                                double x = Math.min(sx0, sx1) + centerX - projection.surfaceWidth() / 2.0;
                                double y = Math.min(sy0, sy1) + centerY - projection.surfaceHeight() / 2.0;
                                double w = Math.abs(sx1 - sx0);
                                double h = Math.abs(sy1 - sy0);
                                if (x + w < 0 || y + h < 0
                                                                || x > projection.surfaceWidth() || y > projection.surfaceHeight()) {
                                        continue;
                                }
                                graphics.fill((int) Math.floor(x), (int) Math.floor(y),
                                                                (int) Math.ceil(x + w), (int) Math.ceil(y + h), argb);
                        }
                }
        }

        /**
         * The country mosaic on the curved path: each visible cell's four
         * world corners project through the family and submit as colored
         * quads, culled by the same limb rule the imagery cells use.
         */
        private void drawCountryLayerCurved(GuiGraphicsExtractor graphics, MapProjection projection,
                        float centerX, float centerY, double curvature, double fit) {
                MapCamera camera = projection.camera();
                MapWorld world = camera.world();
                List<CountryMosaic.Aggregate> list = aggregatesOf(world);
                if (list.isEmpty()) {
                        return;
                }
                double recede = GlobeTransition.globeScaleOf(camera.pixelsPerBlock(), fit);
                double radius = GlobeTransition.globeRadius(projection.surfaceWidth(), projection.surfaceHeight())
                                        * recede;
                double halfW = world.worldWidth / 2.0;
                double halfH = world.worldHeight / 2.0;
                int mip = CountryMosaic.mipFor(fit * recede, 3.0);
                int cellBlocks = CountryMosaic.CELL << mip;
                int fillAlpha = 0xB0;
                double cullRadius = Math.hypot(projection.surfaceWidth() / 2.0, projection.surfaceHeight() / 2.0) + 64.0;
                List<GlobeMeshElement.Quad> quads = new ArrayList<>();
                for (CountryMosaic.Aggregate aggregate : list) {
                        int argb = (fillAlpha << 24) | (aggregate.argb() & 0xFFFFFF);
                        for (long packed : aggregate.cells(mip)) {
                                int cx = CountryMosaic.unpackX(packed);
                                int cz = CountryMosaic.unpackZ(packed);
                                double wx0 = (double) cx * cellBlocks;
                                double wz0 = (double) cz * cellBlocks;
                                double wx1 = wx0 + cellBlocks;
                                double wz1 = wz0 + cellBlocks;
                                // Center projection first: one trig family per
                                // cell rejects the far side cheaply.
                                GlobeTransition.SpherePoint c = GlobeTransition.project(
                                                                wx0 + cellBlocks / 2.0, wz0 + cellBlocks / 2.0,
                                                                camera.centerX(), camera.centerZ(),
                                                                curvature, radius, halfW, halfH);
                                if (c.z() < 0.0 || Math.hypot(c.x(), c.y()) > cullRadius) {
                                        continue;
                                }
                                GlobeTransition.SpherePoint tl = GlobeTransition.project(
                                                                wx0, wz0, camera.centerX(), camera.centerZ(),
                                                                curvature, radius, halfW, halfH);
                                GlobeTransition.SpherePoint bl = GlobeTransition.project(
                                                                wx0, wz1, camera.centerX(), camera.centerZ(),
                                                                curvature, radius, halfW, halfH);
                                GlobeTransition.SpherePoint br = GlobeTransition.project(
                                                                wx1, wz1, camera.centerX(), camera.centerZ(),
                                                                curvature, radius, halfW, halfH);
                                GlobeTransition.SpherePoint tr = GlobeTransition.project(
                                                                wx1, wz0, camera.centerX(), camera.centerZ(),
                                                                curvature, radius, halfW, halfH);
                                if (tl.z() < 0.0 && bl.z() < 0.0 && br.z() < 0.0 && tr.z() < 0.0) {
                                        continue;
                                }
                                quads.add(new GlobeMeshElement.Quad(
                                                                new double[] {centerX + tl.x(), centerX + bl.x(), centerX + br.x(), centerX + tr.x()},
                                                                new double[] {centerY + tl.y(), centerY + bl.y(), centerY + br.y(), centerY + tr.y()},
                                                                0.0, 0.0, 1.0, 1.0, argb));
                        }
                }
                if (!quads.isEmpty()) {
                        ScreenRectangle scissor = currentScissor(graphics);
                        ((GuiGraphicsExtractorAccessor) (Object) graphics).zea$guiRenderState()
                                                        .addGuiElement(GlobeMeshElement.colored(quads, scissor));
                }
        }

        /**
         * One flag per country aggregate, at the territory's anchor cell:
         * a fixed-size procedural stripe flag with the ISO code under it —
         * never stretched across the territory, never repeated per town.
         * Small territories draw the compact dot marker instead.
         */
        private void drawCountryFlags(GuiGraphicsExtractor graphics, Font font, MapProjection projection,
                        float centerX, float centerY, MapWorld world,
                        @Nullable double[] curvedCamera, double curvedRadius, double curvature) {
                List<CountryMosaic.Aggregate> list = aggregatesOf(world);
                if (list.isEmpty()) {
                        return;
                }
                double halfW = world.worldWidth / 2.0;
                double halfH = world.worldHeight / 2.0;
                double surfaceHalfW = projection.surfaceWidth() / 2.0;
                double surfaceHalfH = projection.surfaceHeight() / 2.0;
                for (CountryMosaic.Aggregate aggregate : list) {
                        double[] flagPos = CountryMosaic.flagWorldPos(aggregate);
                        double sx;
                        double sy;
                        if (curvedCamera != null) {
                                GlobeTransition.SpherePoint p = GlobeTransition.project(
                                                                flagPos[0], flagPos[1],
                                                                curvedCamera[0], curvedCamera[1],
                                                                curvature, curvedRadius, halfW, halfH);
                                if (p.z() < 0.0) {
                                        continue;
                                }
                                sx = centerX + p.x();
                                sy = centerY + p.y();
                        } else {
                                sx = centerX + (projection.screenX(flagPos[0], flagPos[1]) - surfaceHalfW);
                                sy = centerY + (projection.screenY(flagPos[0], flagPos[1]) - surfaceHalfH);
                        }
                        if (sx < -20 || sy < -20
                                                        || sx > projection.surfaceWidth() + 20 || sy > projection.surfaceHeight() + 20) {
                                continue;
                        }
                        CountryRegistry.Country country = countryRegistry.country(aggregate.countryId());
                        String iso = country == null ? aggregate.countryId() : country.iso();
                        if (aggregate.cellCount() < 3) {
                                // The compact marker: a dot with the ISO code.
                                graphics.fill((int) sx - 2, (int) sy - 2, (int) sx + 2, (int) sy + 2,
                                                                aggregate.argb());
                                graphics.text(font, iso, (int) sx + 4, (int) sy - font.lineHeight / 2, 0xFFE0E0E0);
                                continue;
                        }
                        int flagW = 15;
                        int flagH = 9;
                        int x0 = (int) Math.round(sx - flagW / 2.0);
                        int y0 = (int) Math.round(sy - flagH / 2.0);
                        List<Integer> colors = country == null ? List.of(aggregate.argb()) : country.flagColors();
                        int bands = colors.size();
                        for (int band = 0; band < bands; band++) {
                                int bandY0 = y0 + band * flagH / bands;
                                int bandY1 = y0 + (band + 1) * flagH / bands;
                                graphics.fill(x0, bandY0, x0 + flagW, bandY1, colors.get(band));
                        }
                        graphics.fill(x0, y0, x0 + flagW, y0 + 1, 0xFF1E1E1E);
                        graphics.fill(x0, y0 + flagH - 1, x0 + flagW, y0 + flagH, 0xFF1E1E1E);
                        graphics.fill(x0, y0 + 1, x0 + 1, y0 + flagH - 1, 0xFF1E1E1E);
                        graphics.fill(x0 + flagW - 1, y0 + 1, x0 + flagW, y0 + flagH - 1, 0xFF1E1E1E);
                        graphics.text(font, iso, x0, y0 + flagH + 1, 0xFFE0E0E0);
                }
        }

        /** Shifts a planner cell from globe-center offsets to screen pixels. */
        private static GlobeMeshElement.Quad absolute(double[] xs, double[] ys, int argb,
                        float centerX, float centerY) {
                double[] ax = new double[4];
                double[] ay = new double[4];
                for (int i = 0; i < 4; i++) {
                        ax[i] = centerX + xs[i];
                        ay[i] = centerY + ys[i];
                }
                return new GlobeMeshElement.Quad(ax, ay, 0.0, 0.0, 1.0, 1.0, argb);
        }

        /** The scissor active at the call site, for the mesh\u2019s clip. */
        private static ScreenRectangle currentScissor(GuiGraphicsExtractor graphics) {
                return ((GuiGraphicsExtractorAccessor) (Object) graphics).zea$scissorStack().peek();
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
