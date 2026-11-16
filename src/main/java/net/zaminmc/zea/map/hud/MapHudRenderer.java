package net.zaminmc.zea.map.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.zaminmc.zea.ZeaClient;
import net.zaminmc.zea.config.ZeaConfig;
import net.zaminmc.zea.hud.HudRenderer;
import net.zaminmc.zea.map.MapEngine;
import net.zaminmc.zea.map.config.MapConfig;
import net.zaminmc.zea.map.model.MapWorld;

import java.util.function.Supplier;

/**
 * The map's HUD elements — the minimap and the larger map overlay. Both
 * render through the same engine surface call with their own rectangle
 * and zoom; the element's size and position are the HUD manager's
 * business (the on-screen pixel state MapConfig deliberately does not
 * carry), while the content options stay map config: zoom in blocks
 * across, rotation, claims, players, labels.
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

        private final Kind kind;
        private final Supplier<MapEngine> engine;

        public MapHudRenderer(Kind kind, Supplier<MapEngine> engine) {
                this.kind = kind;
                this.engine = engine;
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
                MapEngine mapEngine = engine.get();
                if (mapEngine == null) {
                        return;
                }
                boolean onEarthMc = ZeaClient.lifecycle() != null && ZeaClient.lifecycle().onEarthMc();
                if (config.map.earthMcOnly && !onEarthMc) {
                        if (editorPreview) {
                                drawPlaceholder(graphics, x, y, width, height);
                        }
                        return;
                }
                MapWorld world = MapEngine.currentWorld();
                Minecraft minecraft = Minecraft.getInstance();
                if (world == null || minecraft.player == null || minecraft.level == null) {
                        if (editorPreview) {
                                drawPlaceholder(graphics, x, y, width, height);
                        }
                        return;
                }

                double pixelsPerBlock;
                boolean rotateWithPlayer;
                if (kind == Kind.MINIMAP) {
                        pixelsPerBlock = (double) width / Math.max(64, config.map.minimap.blocksAcross);
                        rotateWithPlayer = config.map.minimap.rotateWithPlayer;
                } else {
                        pixelsPerBlock = (double) width / Math.max(64, config.map.worldMap.defaultBlocksAcross);
                        rotateWithPlayer = false;
                }
                float yaw = minecraft.player.getYRot();
                double centerX = minecraft.player.getX();
                double centerZ = minecraft.player.getZ();

                mapEngine.renderSurface(graphics, world, centerX, centerZ, yaw,
                                x, y, width, height, pixelsPerBlock, rotateWithPlayer, config.map);
                drawOverlays(graphics, x, y, width, height, config.map);
        }

        /** The readouts under the minimap edge — inside the element's own rectangle. */
        private void drawOverlays(GuiGraphicsExtractor graphics, int x, int y, int width, int height,
                        MapConfig config) {
                if (kind != Kind.MINIMAP) {
                        return;
                }
                Minecraft minecraft = Minecraft.getInstance();
                int baseline = y + height - 9;
                if (config.minimap.showCoordinates && minecraft.player != null) {
                        String coords = Math.round(minecraft.player.getX()) + ", "
                                        + Math.round(minecraft.player.getZ());
                        int textWidth = minecraft.font.width(coords);
                        graphics.fill(x + width - textWidth - 5, baseline - 1, x + width,
                                        baseline + 8, 0x90000000);
                        graphics.text(minecraft.font, coords, x + width - textWidth - 3,
                                        baseline, 0xFFE0E0E0, true);
                }
                if (config.minimap.showScaleBar) {
                        // A short scale bar: the pixel width of 100 blocks at
                        // the element's zoom, capped to a third of the edge.
                        double pixelsPerBlock = (double) width / Math.max(64, config.minimap.blocksAcross);
                        int barWidth = Math.max(12, Math.min(width / 3,
                                        (int) Math.round(100 * pixelsPerBlock)));
                        graphics.fill(x + 3, baseline + 4, x + 3 + barWidth, baseline + 6, 0xFFE0E0E0);
                        graphics.fill(x + 3, baseline + 2, x + 4, baseline + 8, 0xFFE0E0E0);
                        graphics.fill(x + 2 + barWidth, baseline + 2, x + 3 + barWidth, baseline + 8, 0xFFE0E0E0);
                }
        }

        /** The manager's preview frame: where the element sits, even with no map data. */
        private void drawPlaceholder(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
                graphics.fill(x, y, x + width, y + height, 0x66000000);
                graphics.fill(x, y, x + width, y + 1, 0xFF404040);
                graphics.fill(x, y + height - 1, x + width, y + height, 0xFF404040);
                graphics.fill(x, y + 1, x + 1, y + height - 1, 0xFF404040);
                graphics.fill(x + width - 1, y + 1, x + width, y + height - 1, 0xFF404040);
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
}
