package net.zaminmc.zea.map.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.zaminmc.zea.ZeaClient;
import net.zaminmc.zea.config.ZeaConfig;
import net.zaminmc.zea.hud.HudRenderer;
import net.zaminmc.zea.map.MapSubsystem;
import net.zaminmc.zea.map.MinimapLayout;
import net.zaminmc.zea.map.config.MapConfig;
import net.zaminmc.zea.map.model.MapCamera;
import net.zaminmc.zea.map.model.MapWorld;
import net.zaminmc.zea.map.projection.MapProjection;
import net.zaminmc.zea.map.render.tile.TileTextures;
import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;

/**
 * The HUD manager's map elements — the minimap and the map overlay.
 * Both draw through the one map engine ({@link MapSubsystem}'s shared
 * surface routine, the same data, caches and drivers the world map
 * uses) with their own rectangle and zoom; the element's size and
 * position are the HUD manager's business, while the content options
 * stay map config: zoom in blocks across, rotation, claims, players,
 * labels.
 *
 * <p>The frame is the Item Browser's design language — the dark
 * interior line, the gray plate and the light reflective edge the
 * sidebar buttons carry — and the circle shape masks the square draw
 * with the same ring treatment. No second engine, no second cache, no
 * duplicate HTTP stack: everything below delegates to the subsystem.
 *
 * <p>Gating follows the map config: the master switch, the per-element
 * switch, EarthMC-only (the map's imagery only exists for EarthMC's
 * worlds), and unsupported dimensions — where nothing draws rather than
 * a broken image. In the manager's preview the element draws its frame
 * even without a world, so it can always be found and moved.
 */
public final class MapHudRenderer implements HudRenderer {

        /** Which HUD element this renderer serves. */
        public enum Kind {
                MINIMAP,
                MAP_OVERLAY
        }

        /** The browser palette: dark interior, gray plate, light edge. */
        private static final int PLATE_DARK = 0xFF1E1E1E;
        private static final int PLATE_GRAY = 0xFF646464;
        private static final int PLATE_EDGE = 0xFFB4B4B4;
        private static final int DISABLED_TEXT = 0xFFA0A0A0;
        private static final int LABEL_TEXT = 0xFFFFFFFF;
        private static final int LABEL_SHADOW = 0xFF202020;

        private final Kind kind;

        @Nullable
        private GpuTexture maskTexture;
        @Nullable
        private GpuTextureView maskView;
        private int maskSize = -1;
        private int maskAlpha = -1;

        public MapHudRenderer(Kind kind) {
                this.kind = kind;
        }

        @Override
        public void render(GuiGraphicsExtractor graphics, int x, int y, int width, int height,
                        boolean editorPreview) {
                ZeaConfig config = ZeaClient.config();
                if (config == null || !config.map.enabled) {
                        return;
                }
                if (kind == Kind.MINIMAP && !config.map.minimapEnabled) {
                        return;
                }
                if (kind == Kind.MAP_OVERLAY && !config.map.worldMapEnabled) {
                        return;
                }
                MapSubsystem map = ZeaClient.map();
                if (map == null) {
                        return;
                }
                boolean onEarthMc = ZeaClient.lifecycle() != null && ZeaClient.lifecycle().onEarthMc();
                if (config.map.earthMcOnly && !onEarthMc) {
                        if (editorPreview) {
                                drawPlaceholder(graphics, x, y, width, height);
                        }
                        return;
                }
                MapWorld world = currentWorld();
                Minecraft minecraft = Minecraft.getInstance();
                if (world == null || minecraft.player == null || minecraft.level == null
                                                || !map.dimensionSupported()) {
                        if (editorPreview) {
                                drawPlaceholder(graphics, x, y, width, height);
                        } else if (world != null && !map.dimensionSupported()
                                                        && !config.map.hideInUnsupportedDimensions) {
                                drawDisabled(graphics, minecraft.gui.hud.getFont(), x, y, width, height);
                        }
                        return;
                }

                LocalPlayer player = minecraft.player;
                double pixelsPerBlock;
                boolean rotateWithPlayer;
                if (kind == Kind.MINIMAP) {
                        pixelsPerBlock = (double) width / Math.max(64, config.map.minimap.blocksAcross);
                        rotateWithPlayer = config.map.minimap.rotateWithPlayer;
                } else {
                        pixelsPerBlock = (double) width / Math.max(64, config.map.worldMap.defaultBlocksAcross);
                        rotateWithPlayer = false;
                }
                MapCamera camera;
                if (kind == Kind.MINIMAP) {
                        // The subsystem owns the minimap camera (player-centered
                        // or the stored free camera); only the zoom follows the
                        // manager's element size.
                        camera = map.minimapCamera(player.getX(), player.getZ());
                        if (camera.pixelsPerBlock() != pixelsPerBlock) {
                                camera = new MapCamera(world, camera.centerX(), camera.centerZ(), pixelsPerBlock);
                        }
                } else {
                        camera = new MapCamera(world, player.getX(), player.getZ(), pixelsPerBlock);
                }
                float yaw = player.getYRot();
                double rotation = rotateWithPlayer ? MapProjection.rotationForYaw(yaw) : 0.0;
                MapProjection projection = new MapProjection(camera, width, height, rotation);
                MapConfig.SharedDisplay shared = config.map.sharedDisplay;
                Font font = minecraft.gui.hud.getFont();

                graphics.fill(x, y, x + width, y + height, PLATE_DARK);
                graphics.enableScissor(x + 1, y + 1, x + width - 1, y + height - 1);
                try {
                        map.drawSurface(graphics, font, projection, x + 1, y + 1, MapSubsystem.Surface.MINIMAP,
                                                        cameraFollowsMotion(config.map, player), new MapSubsystem.Layers(
                                                                        config.map.minimap.showClaims && shared.claimFillOpacityPercent > 0,
                                                                        config.map.minimap.showClaims && shared.claimOutlineOpacityPercent > 0,
                                                                        config.map.minimap.showPlayers,
                                                                        config.map.minimap.showPlayerNames,
                                                                        config.map.minimap.showTownNames,
                                                                        alpha(shared.claimFillOpacityPercent),
                                                                        alpha(shared.claimOutlineOpacityPercent)));
                } finally {
                        graphics.disableScissor();
                }

                if (config.map.minimap.shape == MapConfig.MinimapShape.CIRCLE) {
                        blitCircleMask(graphics, x, y, width, height, alpha(config.map.minimap.opacity));
                } else {
                        drawNeuFrame(graphics, x, y, width, height);
                }
                drawOverlays(graphics, font, x, y, width, height, config.map, player);
        }

        /** The readouts under the minimap edge — inside the element's own rectangle. */
        private void drawOverlays(GuiGraphicsExtractor graphics, Font font, int x, int y,
                        int width, int height, MapConfig config, LocalPlayer player) {
                if (kind != Kind.MINIMAP) {
                        return;
                }
                int baseline = y + height - 9;
                if (config.minimap.showCoordinates) {
                        String coords = Math.round(player.getX()) + ", "
                                                        + Math.round(player.getZ());
                        int textWidth = font.width(coords);
                        graphics.fill(x + width - textWidth - 5, baseline - 1, x + width,
                                                        baseline + 8, 0x90000000);
                        graphics.text(font, coords, x + width - textWidth - 3,
                                                        baseline, 0xFFE0E0E0, true);
                }
                if (config.minimap.showScaleBar) {
                        // A short scale bar: the pixel width of 100 blocks at
                        // the element's zoom, capped to a third of the edge.
                        double pixelsPerBlock = (double) width / Math.max(64, config.minimap.blocksAcross);
                        int barWidth = Math.max(12, Math.min(width / 3,
                                                        (int) Math.round(100 * pixelsPerBlock)));
                        graphics.fill(x + 3, baseline + 4, x + 3 + barWidth, baseline + 6, LABEL_TEXT);
                        graphics.fill(x + 3, baseline + 2, x + 4, baseline + 8, LABEL_TEXT);
                        graphics.fill(x + 2 + barWidth, baseline + 2, x + 3 + barWidth, baseline + 8, LABEL_TEXT);
                }
        }

        /**
         * The frame the Item Browser's sidebar buttons carry: the dark
         * interior line, the gray plate and the light reflective edge —
         * three one-pixel rings, no glow, no new palette.
         */
        private void drawNeuFrame(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
                // Interior line just inside the content edge.
                graphics.fill(x, y, x + width, y + 1, PLATE_DARK);
                graphics.fill(x, y + height - 1, x + width, y + height, PLATE_DARK);
                graphics.fill(x, y + 1, x + 1, y + height - 1, PLATE_DARK);
                graphics.fill(x + width - 1, y + 1, x + width, y + height - 1, PLATE_DARK);
                // The plate ring, one pixel out.
                graphics.fill(x - 1, y - 1, x + width + 1, y, PLATE_GRAY);
                graphics.fill(x - 1, y + height, x + width + 1, y + height + 1, PLATE_GRAY);
                graphics.fill(x - 1, y, x, y + height, PLATE_GRAY);
                graphics.fill(x + width, y, x + width + 1, y + height, PLATE_GRAY);
                // The reflective edge, one pixel further out.
                graphics.fill(x - 2, y - 2, x + width + 2, y - 1, PLATE_EDGE);
                graphics.fill(x - 2, y + height + 1, x + width + 2, y + height + 2, PLATE_EDGE);
                graphics.fill(x - 2, y - 1, x - 1, y + height + 1, PLATE_EDGE);
                graphics.fill(x + width + 1, y - 1, x + width + 2, y + height + 1, PLATE_EDGE);
        }

        /** Whether the minimap camera moves with the player this frame. */
        private boolean cameraFollowsMotion(MapConfig config, LocalPlayer player) {
                return config.minimap.centerPlayer && player != null
                                        && (player.xOld != player.getX() || player.zOld != player.getZ());
        }

        /** The world the player stands in, or null outside a supported one. */
        @Nullable
        public static MapWorld currentWorld() {
                Minecraft minecraft = Minecraft.getInstance();
                if (minecraft.player == null || minecraft.level == null) {
                        return null;
                }
                var dimension = minecraft.level.dimension();
                var id = dimension.identifier();
                return MapWorld.ofDimension(id.getNamespace(), id.getPath());
        }

        /** The disabled panel: an honest short label, never fake imagery. */
        private void drawDisabled(GuiGraphicsExtractor graphics, Font font, int x, int y, int width, int height) {
                graphics.fill(x, y, x + width, y + height, PLATE_DARK);
                drawNeuFrame(graphics, x, y, width, height);
                graphics.centeredText(font, "No map", x + width / 2,
                                                        y + height / 2 - font.lineHeight / 2, DISABLED_TEXT);
        }

        /** The manager's preview frame: where the element sits, even with no map data. */
        private void drawPlaceholder(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
                graphics.fill(x, y, x + width, y + height, 0x66000000);
                drawNeuFrame(graphics, x, y, width, height);
                Minecraft minecraft = Minecraft.getInstance();
                if (minecraft.font != null) {
                        graphics.centeredText(minecraft.font,
                                                        net.minecraft.network.chat.Component.translatable(
                                                                        kind == Kind.MINIMAP
                                                                                        ? "zea.hud.map.minimap_placeholder"
                                                                                        : "zea.hud.map.map_placeholder"),
                                                        x + width / 2, y + height / 2 - 4, 0xFF808080);
                }
        }

        /** Blits (and lazily builds) the circle mask for the current size and opacity. */
        private void blitCircleMask(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int alpha) {
                if (maskTexture == null || maskSize != width || maskAlpha != alpha) {
                        rebuildMask(width, alpha);
                }
                if (maskView != null) {
                        GpuSampler sampler = TileTextures.tileSampler();
                        graphics.blit(maskView, sampler, x - 1, y - 1, width + 2, height + 2,
                                                        0.0f, 0.0f, 1.0f, 1.0f);
                }
        }

        private void rebuildMask(int size, int alpha) {
                closeMask();
                int dim = size + 2;
                NativeImage image = new NativeImage(dim, dim, true);
                double center = (dim - 1) / 2.0;
                double radius = size / 2.0;
                double ringOuter = radius + 0.5;
                double ringInner = radius - 1.5;
                for (int py = 0; py < dim; py++) {
                        for (int px = 0; px < dim; px++) {
                                double distance = Math.hypot(px - center, py - center);
                                int color;
                                if (distance <= ringInner) {
                                        color = 0x00000000;
                                } else if (distance <= ringOuter) {
                                        // The ring reads as the plate edge.
                                        color = PLATE_EDGE;
                                } else {
                                        color = (alpha << 24) | 0x101018;
                                }
                                image.setPixelABGR(px, py, abgr(color));
                        }
                }
                try {
                        GpuTexture texture = RenderSystem.getDevice().createTexture("zea:map/minimap_circle",
                                                        GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING,
                                                        GpuFormat.RGBA8_UNORM, dim, dim, 1, 1);
                        RenderSystem.getDevice().createCommandEncoder().writeToTexture(texture, image);
                        maskTexture = texture;
                        maskView = RenderSystem.getDevice().createTextureView(texture);
                        maskSize = size;
                        maskAlpha = alpha;
                } catch (Throwable failure) {
                        ZeaClient.LOGGER.warn("Minimap circle mask could not be created: {}", failure.toString());
                } finally {
                        image.close();
                }
        }

        /** ARGB (alpha high) to the ABGR packing NativeImage stores. */
        private static int abgr(int argb) {
                int a = (argb >>> 24) & 0xFF;
                int r = (argb >>> 16) & 0xFF;
                int g = (argb >>> 8) & 0xFF;
                int b = argb & 0xFF;
                return (a << 24) | (b << 16) | (g << 8) | r;
        }

        private static int alpha(int percent) {
                return (int) Math.round(Math.clamp(percent, 0, 100) * 255 / 100.0);
        }

        private void closeMask() {
                if (maskTexture != null) {
                        maskTexture.close();
                        maskTexture = null;
                        maskView = null;
                        maskSize = -1;
                        maskAlpha = -1;
                }
        }

        /** Releases the mask texture; called on client shutdown. */
        public void close() {
                closeMask();
        }
}
