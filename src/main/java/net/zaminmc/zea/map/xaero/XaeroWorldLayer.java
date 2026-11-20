package net.zaminmc.zea.map.xaero;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.zaminmc.zea.ZeaClient;
import net.zaminmc.zea.map.MapSubsystem;
import net.zaminmc.zea.map.config.MapConfig;
import net.zaminmc.zea.map.model.MapCamera;
import net.zaminmc.zea.map.model.MapWorld;
import net.zaminmc.zea.map.model.TownClaim;
import net.zaminmc.zea.map.projection.MapProjection;
import net.zaminmc.zea.map.render.overlay.ClaimOverlay;
import net.zaminmc.zea.map.render.overlay.PlayerOverlay;
import net.zaminmc.zea.map.render.tile.OverlayDrawer;

import java.util.List;

/**
 * The EarthMC layer over Xaero's world map — the whole of ZEA's remaining
 * map-rendering surface. Xaero is the engine: its terrain, its camera, its
 * zoom, its chrome. This class renders only what Xaero cannot have — the
 * live claim fills and outlines and the player markers ZEA maintains from
 * the squaremap feed — over that terrain, at Xaero's camera.
 *
 * <p>There is no projection code here and no imagery of ZEA's own. Xaero's
 * camera pair ({@code scale / screenScale}) is GUI pixels per world block —
 * the same single zoom scalar {@link MapProjection} takes — so a north-up
 * projection built from the shadowed camera values and the full-window
 * surface agrees with the terrain beneath it by construction, at every
 * zoom. Claims plan into screen-space rects through the same
 * {@link ClaimOverlay} and draw through the same {@link OverlayDrawer} the
 * minimap uses; players plan through {@link PlayerOverlay}. One EarthMC
 * surface, one drawing vocabulary, no second renderer.
 *
 * <p>The world shown is the player's resolved world — the same rule the
 * minimap follows ({@code playerWorld()}), so Earth claims draw on Terra
 * Nostra and Moon claims on the Moon. Xaero's per-dimension terrain
 * switching is the world toggle; ZEA adds no UI of its own.
 *
 * <p>Everything here is defensive about absence: the layer runs from a
 * soft mixin that only applies when Xaero's GuiMap exists, and it exits
 * quietly whenever ZEA's map subsystem is off, the dimension is
 * unsupported, or the stores are cold — Xaero's map then reads exactly as
 * if ZEA were not installed.
 */
public final class XaeroWorldLayer {

        private XaeroWorldLayer() {
        }

        /**
         * Draws the layer for one frame. Called from
         * {@code XaeroGuiMapMixin} inside Xaero's
         * {@code extractRenderState}, with the shadowed camera values and
         * the render-state recorder Xaero's own pass runs on.
         */
        public static void draw(GuiGraphicsExtractor ctx, double cameraX, double cameraZ,
                                double scale, double screenScale) {
                MapSubsystem map = ZeaClient.map();
                if (map == null || !map.active() || !map.dimensionSupported()) {
                        return;
                }
                Minecraft client = Minecraft.getInstance();
                if (client == null || client.player == null) {
                        return;
                }
                int surfaceWidth = client.getWindow().getGuiScaledWidth();
                int surfaceHeight = client.getWindow().getGuiScaledHeight();
                if (surfaceWidth <= 0 || surfaceHeight <= 0) {
                        return;
                }
                // Xaero's zoom in GUI pixels per world block — the one
                // scalar ZEA's flat projection is built around.
                double pixelsPerBlock = screenScale > 0.0 ? scale / screenScale : scale;
                MapWorld world = map.playerWorld();
                MapProjection projection = MapProjection.northUp(
                                new MapCamera(world, cameraX, cameraZ, pixelsPerBlock),
                                surfaceWidth, surfaceHeight);
                float centerX = surfaceWidth / 2.0f;
                float centerY = surfaceHeight / 2.0f;
                MapConfig.WorldMap worldMap = map.worldMapConfig();
                MapConfig.SharedDisplay shared = map.sharedDisplayConfig();
                MapSubsystem.Layers layers = new MapSubsystem.Layers(
                                                worldMap.showTownFills && shared.claimFillOpacityPercent > 0,
                                                worldMap.showTownOutlines && shared.claimOutlineOpacityPercent > 0,
                                                worldMap.showPlayers,
                                                worldMap.showPlayerLabels,
                                                false,
                                                fillAlpha(shared.claimFillOpacityPercent),
                                                fillAlpha(shared.claimOutlineOpacityPercent));
                if (layers.claimFills() || layers.claimOutlines()) {
                        List<TownClaim> claims = map.claimsOf(world);
                        if (!claims.isEmpty()) {
                                List<ClaimOverlay.Rect> rects = new ClaimOverlay().plan(
                                                                claims, projection,
                                                                layers.claimFills(), layers.claimOutlines(),
                                                                layers.fillAlpha(), layers.outlineAlpha(), 1.0);
                                OverlayDrawer.drawClaims(ctx, rects, centerX, centerY, 0.0);
                        }
                }
                if (layers.players()) {
                        List<PlayerOverlay.Marker> markers = new PlayerOverlay().plan(
                                                        map.playerStore().markers(world),
                                                        map.playerStore().previous(world),
                                                        System.currentTimeMillis(),
                                                        map.playerStore().publishedAtMs(),
                                                        map.playerStore().previousAtMs(),
                                                        map.selfName(),
                                                        map.selfPosition(world),
                                                        projection);
                        if (!markers.isEmpty()) {
                                OverlayDrawer.drawMarkers(ctx, markers, centerX, centerY, 0.0);
                        }
                }
        }

        /** The configured opacity percent as the 0..255 alpha the drawers take. */
        private static int fillAlpha(int percent) {
                return (int) Math.round(Math.clamp(percent, 0, 100) * 255 / 100.0);
        }
}
