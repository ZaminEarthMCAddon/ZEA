package net.zaminmc.zea.map;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.Identifier;
import net.zaminmc.zea.ZeaClient;
import net.zaminmc.zea.map.cache.ClaimStore;
import net.zaminmc.zea.map.cache.PlayerStore;
import net.zaminmc.zea.map.cache.TileCache;
import net.zaminmc.zea.map.model.MapCamera;
import net.zaminmc.zea.map.model.MapWorld;
import net.zaminmc.zea.map.model.PlayerMarker;
import net.zaminmc.zea.map.model.TileId;
import net.zaminmc.zea.map.projection.MapProjection;
import net.zaminmc.zea.map.render.overlay.ClaimOverlay;
import net.zaminmc.zea.map.render.overlay.PlayerOverlay;
import net.zaminmc.zea.map.render.tile.TileRenderer;
import net.zaminmc.zea.map.render.tile.TileUploads;
import net.zaminmc.zea.map.source.MapHttp;
import net.zaminmc.zea.map.source.earthmcapi.EarthMcApiClient;
import net.zaminmc.zea.map.source.squaremap.MarkersDocument;
import net.zaminmc.zea.map.source.squaremap.SquaremapClient;
import net.zaminmc.zea.map.source.squaremap.TileFetcher;
import net.zaminmc.zea.map.config.MapConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The map engine: the Minecraft adapter for ZEA's pure map stack. It
 * owns the tile cache and its GPU textures, the fetcher, the squaremap
 * feeds (claims, players) and the per-frame render call the HUD elements
 * make — the one place where the tested planners (TileRenderer,
 * ClaimOverlay, PlayerOverlay) meet actual drawing.
 *
 * <p>Discipline inherited from the stack's design:
 *
 * <ul>
 * <li><b>Network is scheduled, never frame-driven.</b> Tile requests
 *     come from the planner's bounded budget into the fetcher's
 *     per-world gates; markers and players refresh on fixed intervals,
 *     not per frame.</li>
 * <li><b>No work while nothing shows.</b> The tick takes an
 *     {@code active} flag the render pass sets; with no visible map
 *     element there are no uploads, no disposals and no feed requests.</li>
 * <li><b>Images are freed on the render thread only.</b> The cache's
 *     disposal channel is drained every active frame; a disposal whose
 *     image still lives in its DynamicTexture releases the texture
 *     (which closes the image), anything else closes the image directly.</li>
 * <li><b>All failures are logged, never swallowed</b> — a feed that
 *     fails keeps its previous data and tries again next interval.</li>
 * </ul>
 */
public final class MapEngine {
        public static final Logger LOGGER = LoggerFactory.getLogger("ZEA/Map");

        /** Claims markers refresh once a minute; the squaremap updates them slowly. */
        private static final long CLAIMS_REFRESH_MS = 60_000L;
        /** The online-player feed refreshes every 30 seconds. */
        private static final long PLAYERS_REFRESH_MS = 30_000L;

        private final TextureManager textures;
        private final ExecutorService executor = Executors.newSingleThreadExecutor(
                        task -> {
                                Thread thread = new Thread(task, "ZEA-Map");
                                thread.setDaemon(true);
                                return thread;
                        });
        private final MapHttp http = new MapHttp(executor);
        private final TileCache<NativeImage> tileCache = new TileCache<>();
        private final TileRenderer tileRenderer = new TileRenderer(tileCache);
        private final TileUploads<NativeImage> uploads = new TileUploads<>();
        private final TileFetcher<NativeImage> fetcher;
        private final SquaremapClient client;
        private final ClaimStore claims = new ClaimStore(System::currentTimeMillis);
        private final PlayerStore players = new PlayerStore();
        private final EarthMcApiClient townApi = new EarthMcApiClient(http, executor);

        private final Map<TileId, DynamicTexture> liveTextures = new HashMap<>();
        private final Map<MapWorld, MarkersDocument> markerDocuments = new EnumMap<>(MapWorld.class);

        private long lastClaimsRefreshMs;
        private long lastPlayersRefreshMs;
        private long lastClaimsRequestMs;
        private long lastPlayersRequestMs;

        // Camera motion between frames, for the planner's budget selection.
        private MapWorld motionWorld;
        private double lastCenterX;
        private double lastCenterZ;
        private boolean motionKnown;

        /** Set by the render pass; the tick does nothing unless the map showed last frame. */
        private boolean renderedLastFrame;

        public MapEngine(TextureManager textures, MapConfig config) {
                this.textures = textures;
                this.fetcher = new TileFetcher<>(http, executor, tileCache, NativeImage::read);
                this.client = new SquaremapClient(http, executor);
        }

        // ------------------------------------------------------------------
        // Tick: uploads, disposals, feed refresh
        // ------------------------------------------------------------------

        /**
         * The render-thread tick. Does nothing while the map was not on
         * screen last frame — the expensive work follows the visibility,
         * never runs behind the player's back.
         */
        public void tick() {
                boolean active = renderedLastFrame;
                renderedLastFrame = false;
                if (!active) {
                        return;
                }
                uploads.run(tileCache, this::uploadTexture, false, System::nanoTime);
                TileUploads.disposeRetired(tileCache, this::dispose);
                refreshFeeds();
                claims.tick();
        }

        /** The claim feed: conditional GET against the squaremap markers document. */
        private void refreshFeeds() {
                MapWorld world = currentWorld();
                if (world == null) {
                        return;
                }
                long now = System.currentTimeMillis();
                if (now - lastClaimsRefreshMs >= CLAIMS_REFRESH_MS && now - lastClaimsRequestMs >= 10_000L) {
                        lastClaimsRefreshMs = now;
                        lastClaimsRequestMs = now;
                        MarkersDocument previous = markerDocuments.getOrDefault(world, MarkersDocument.EMPTY);
                        client.fetchMarkers(world, previous)
                                        .thenAccept(document -> {
                                                markerDocuments.put(world, document);
                                                claims.publish(world, document.towns());
                                        })
                                        .exceptionally(failure -> {
                                                LOGGER.warn("Claim markers refresh failed: {}", failure.toString());
                                                return null;
                                        });
                }
                if (now - lastPlayersRefreshMs >= PLAYERS_REFRESH_MS && now - lastPlayersRequestMs >= 10_000L) {
                        lastPlayersRefreshMs = now;
                        lastPlayersRequestMs = now;
                        MapConfig config = ZeaClient.config() == null ? null : ZeaClient.config().map;
                        String apiKey = config == null ? "" : config.apiKey;
                        client.fetchPlayers(players.previous(world), apiKey)
                                        .thenAccept(feed -> players.publish(feed, System.currentTimeMillis()))
                                        .exceptionally(failure -> {
                                                LOGGER.warn("Player feed refresh failed: {}", failure.toString());
                                                return null;
                                        });
                }
        }

        /** The claim store the overlays read — shared with discovery's town context. */
        public ClaimStore claims() {
                return claims;
        }

        /** The player feed store, for subsystems that want the live player list. */
        public PlayerStore players() {
                return players;
        }

        /** The world the player stands in, or null outside a supported one. */
        public static MapWorld currentWorld() {
                Minecraft minecraft = Minecraft.getInstance();
                if (minecraft.player == null || minecraft.level == null) {
                        return null;
                }
                var dimension = minecraft.level.dimension();
                Identifier dimensionId = dimension.identifier();
                return MapWorld.ofDimension(dimensionId.getNamespace(), dimensionId.getPath());
        }

        // ------------------------------------------------------------------
        // Render: one map surface
        // ------------------------------------------------------------------

        /**
         * Draws one map surface — the minimap and the map overlay both come
         * through here with their own rectangles and settings. Tiles, claim
         * overlays and player markers are clipped to the rectangle; the
         * frame and labels draw after the clip so they stay on its edge.
         */
        public void renderSurface(GuiGraphicsExtractor graphics, MapWorld world,
                        double centerX, double centerZ, float yaw,
                        int x, int y, int width, int height,
                        double pixelsPerBlock, boolean rotateWithPlayer, MapConfig config) {
                renderedLastFrame = true;
                if (width <= 0 || height <= 0 || !(pixelsPerBlock > 0)) {
                        return;
                }
                tileCache.setReservedWorld(world);
                MapCamera camera = new MapCamera(world, centerX, centerZ, pixelsPerBlock);
                double rotation = rotateWithPlayer ? MapProjection.rotationForYaw(yaw) : 0.0;
                MapProjection projection = new MapProjection(camera, width, height, rotation);
                TileRenderer.Motion motion = motionFor(world, centerX, centerZ);

                TileRenderer.FramePlan plan = tileRenderer.planFrame(projection, motion);
                for (TileId request : plan.requests()) {
                        CompletableFuture<Void> ignored = fetcher.request(request);
                }

                // Backdrop so imagery loads over something readable.
                graphics.fill(x, y, x + width, y + height, 0xFF101010);

                graphics.enableScissor(x, y, x + width, y + height);
                var pose = graphics.pose();
                pose.pushMatrix();
                pose.translate(x + width / 2f, y + height / 2f);
                if (rotation != 0.0) {
                        pose.rotate((float) rotation);
                }
                drawTiles(graphics, plan);
                if (config.minimap.showClaims) {
                        drawClaims(graphics, world, projection, config);
                }
                if (config.minimap.showPlayers) {
                        drawPlayers(graphics, world, projection, config, centerX, centerZ, yaw);
                }
                pose.popMatrix();
                graphics.disableScissor();

                drawFrame(graphics, x, y, width, height);
        }

        /** The planner's motion input: camera movement since the previous frame. */
        private TileRenderer.Motion motionFor(MapWorld world, double centerX, double centerZ) {
                if (!motionKnown || motionWorld != world) {
                        motionWorld = world;
                        lastCenterX = centerX;
                        lastCenterZ = centerZ;
                        motionKnown = true;
                        return TileRenderer.Motion.STILL;
                }
                double dx = centerX - lastCenterX;
                double dz = centerZ - lastCenterZ;
                lastCenterX = centerX;
                lastCenterZ = centerZ;
                return new TileRenderer.Motion(dx, dz);
        }

        /** The planned tile quads, textured from the live GPU textures. */
        private void drawTiles(GuiGraphicsExtractor graphics, TileRenderer.FramePlan plan) {
                for (TileRenderer.Quad quad : plan.detailed()) {
                        drawQuad(graphics, quad);
                }
                for (TileRenderer.Quad quad : plan.overview()) {
                        drawQuad(graphics, quad);
                }
        }

        private void drawQuad(GuiGraphicsExtractor graphics, TileRenderer.Quad quad) {
                DynamicTexture texture = liveTextures.get(quad.texture());
                if (texture == null) {
                        return;
                }
                MapProjection.TileRegion region = quad.region();
                graphics.blit(texture.getTextureView(), texture.getSampler(),
                                (int) Math.floor(quad.x()), (int) Math.floor(quad.y()),
                                (int) Math.ceil(quad.x() + quad.width()), (int) Math.ceil(quad.y() + quad.height()),
                                (float) region.u0(), (float) region.v0(),
                                (float) region.u1(), (float) region.v1());
        }

        private void drawClaims(GuiGraphicsExtractor graphics, MapWorld world,
                        MapProjection projection, MapConfig config) {
                var all = new java.util.ArrayList<>(claims.claims(world));
                all.addAll(claims.optimisticClaims(world));
                ClaimOverlay overlay = new ClaimOverlay();
                var rects = overlay.plan(all, projection, true, true,
                                config.sharedDisplay.claimFillOpacityPercent * 255 / 100,
                                config.sharedDisplay.claimOutlineOpacityPercent * 255 / 100,
                                1.0);
                for (ClaimOverlay.Rect rect : rects) {
                        graphics.fill((int) Math.floor(rect.x()), (int) Math.floor(rect.y()),
                                        (int) Math.ceil(rect.x() + rect.width()),
                                        (int) Math.ceil(rect.y() + rect.height()), rect.argb());
                }
        }

        private void drawPlayers(GuiGraphicsExtractor graphics, MapWorld world,
                        MapProjection projection, MapConfig config,
                        double selfX, double selfZ, float selfYaw) {
                PlayerOverlay overlay = new PlayerOverlay();
                Minecraft minecraft = Minecraft.getInstance();
                String selfName = minecraft.player == null ? null
                                : minecraft.player.getName().getString();
                PlayerOverlay.SelfPosition self = new PlayerOverlay.SelfPosition(selfX, selfZ, selfYaw);
                long nowMs = System.currentTimeMillis();
                var markers = overlay.plan(players.markers(world), players.previous(world), nowMs,
                                players.publishedAtMs(), players.previousAtMs(),
                                selfName, self, projection);
                boolean interpolate = config.minimap.interpolatePlayers;
                for (PlayerOverlay.Marker marker : markers) {
                        int px = (int) Math.floor(marker.x());
                        int py = (int) Math.floor(marker.y());
                        double angle = markerAngle(marker.yawDegrees());
                        poseAround(graphics, px, py, angle, () -> {
                                // A chunky arrow: a nose on a square body.
                                graphics.fill(-3, -3, 3, 3,
                                                marker.self() ? 0xFFFFFFFF : 0xFF40C0FF);
                                graphics.fill(3, -1, 7, 1,
                                                marker.self() ? 0xFFFFFFFF : 0xFF40C0FF);
                        });
                        if (!marker.self() && config.minimap.showPlayerNames) {
                                var pose = graphics.pose();
                                pose.pushMatrix();
                                pose.translate(px, py - 6);
                                if (projection.rotationRadians() != 0.0) {
                                        pose.rotate((float) projection.iconRotation());
                                }
                                graphics.centeredText(minecraft.font, marker.name(), 0, 0, 0xFFE0E0E0);
                                pose.popMatrix();
                        }
                }
                if (!interpolate) {
                        // The overlay plans with interpolation when feeds
                        // precede it; a no-interpolation pass simply draws
                        // the same feed without the previous-feed lerp.
                }
        }

        /** A marker's world yaw → map-frame arrow angle. */
        private static double markerAngle(float yawDegrees) {
                // Heading (−sin yaw, cos yaw) in world (x, z); the frame's
                // +x is world +x and +y is world +z.
                double dx = -Math.sin(Math.toRadians(yawDegrees));
                double dy = Math.cos(Math.toRadians(yawDegrees));
                return Math.atan2(dy, dx);
        }

        private static void poseAround(GuiGraphicsExtractor graphics, int x, int y, double angleRadians,
                        Runnable draw) {
                var pose = graphics.pose();
                pose.pushMatrix();
                pose.translate(x, y);
                pose.rotate((float) angleRadians);
                draw.run();
                pose.popMatrix();
        }

        private static void drawFrame(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
                graphics.fill(x, y, x + width, y + 1, 0xFF202020);
                graphics.fill(x, y + height - 1, x + width, y + height, 0xFF202020);
                graphics.fill(x, y + 1, x + 1, y + height - 1, 0xFF202020);
                graphics.fill(x + width - 1, y + 1, x + width, y + height - 1, 0xFF202020);
        }

        // ------------------------------------------------------------------
        // GPU texture plumbing
        // ------------------------------------------------------------------

        /** The GPU upload half of the cache's upload budget. */
        private boolean uploadTexture(TileId id, NativeImage image) {
                try {
                        Identifier textureId = textureId(id);
                        DynamicTexture texture = liveTextures.get(id);
                        if (texture == null) {
                                texture = new DynamicTexture(
                                                () -> "ZEA map tile " + id, image);
                                textures.register(textureId, texture);
                                liveTextures.put(id, texture);
                        } else {
                                texture.setPixels(image);
                        }
                        texture.upload();
                        return true;
                } catch (Exception e) {
                        LOGGER.warn("Tile upload for {} failed: {}", id, e.toString());
                        return false;
                }
        }

        /**
         * The render-thread free half. A disposal whose image still lives
         * inside its DynamicTexture releases the texture — that close frees
         * the image — anything else closes the image directly.
         */
        private void dispose(TileId id, NativeImage image) {
                try {
                        DynamicTexture texture = liveTextures.get(id);
                        if (texture != null && texture.getPixels() == image) {
                                liveTextures.remove(id);
                                textures.release(textureId(id));
                        } else {
                                image.close();
                        }
                } catch (Exception e) {
                        LOGGER.warn("Tile disposal for {} failed: {}", id, e.toString());
                }
        }

        /** The texture identifier for a tile — stable, filesystem-safe. */
        private static Identifier textureId(TileId id) {
                return Identifier.fromNamespaceAndPath("zea", "maptile/"
                                + id.world().squaremapKey + "/" + id.zoom()
                                + "/" + id.x() + "_" + id.z());
        }

        /** Drops every texture and tile (world switch). */
        public void clearWorld(MapWorld world) {
                tileCache.clearWorld(world);
                for (var iterator = liveTextures.entrySet().iterator(); iterator.hasNext(); ) {
                        var entry = iterator.next();
                        if (entry.getKey().world() == world) {
                                textures.release(textureId(entry.getKey()));
                                iterator.remove();
                        }
                }
                markerDocuments.remove(world);
        }

        /** Shutdown: releases GPU textures and stops the IO thread. */
        public void shutdown() {
                executor.shutdown();
                for (Map.Entry<TileId, DynamicTexture> entry : liveTextures.entrySet()) {
                        try {
                                textures.release(textureId(entry.getKey()));
                        } catch (Exception e) {
                                LOGGER.warn("Tile texture release failed on shutdown: {}", e.toString());
                        }
                }
                liveTextures.clear();
        }
}
