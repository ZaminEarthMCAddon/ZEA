package net.zaminmc.zea.map.render.minimap;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.zaminmc.zea.ZeaClient;
import net.zaminmc.zea.map.MapSubsystem;
import net.zaminmc.zea.map.MinimapLayout;
import net.zaminmc.zea.map.config.MapConfig;
import net.zaminmc.zea.map.model.MapCamera;
import net.zaminmc.zea.map.projection.MapProjection;
import net.zaminmc.zea.map.render.tile.TileTextures;
import org.jspecify.annotations.Nullable;

/**
 * The HUD minimap: the config's shape and size in the screen's
 * top-right berth, tile imagery, claims and players drawn through the
 * subsystem's shared surface routine, then the chrome - frame or
 * circle mask, the coordinates line, the scale bar.
 *
 * <p>The circle shape is a mask blitted over the square draw: outside
 * the circle opaque in the panel color, inside fully transparent, with
 * a ring stroke - a circular viewport on a square panel, the honest
 * way to get a round minimap on a renderer without stencil scissor.
 *
 * <p>Unsupported dimensions (the Nether, the End) show a disabled
 * panel - or nothing at all, per the config - and never fabricate map
 * data for a world EarthMC does not publish.
 */
public final class MinimapHud implements HudElement {

	private static final int BACKDROP = 0x90000000;
	private static final int FRAME = 0xFF000000;
	private static final int DISABLED_TEXT = 0xFFA0A0A0;
	private static final int LABEL_TEXT = 0xFFFFFFFF;
	private static final int LABEL_SHADOW = 0xFF202020;

	private final MapSubsystem map;

	@Nullable
	private GpuTexture maskTexture;
	@Nullable
	private GpuTextureView maskView;
	private int maskSize = -1;
	private int maskAlpha = -1;

	public MinimapHud(MapSubsystem map) {
		this.map = map;
	}

	/** Draws the minimap; every gate is re-read per frame. */
	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		Minecraft client = Minecraft.getInstance();
		if (!map.active() || !mapConfig(client).minimapEnabled) {
			return;
		}
		if (client.gui.hud.isHidden()) {
			return;
		}
		LocalPlayer player = client.player;
		if (player == null) {
			return;
		}
		MapConfig.Minimap minimap = mapConfig(client).minimap;
		int screenWidth = client.getWindow().getGuiScaledWidth();
		int screenHeight = client.getWindow().getGuiScaledHeight();
		MinimapLayout.Rect panel = MinimapLayout.panel(minimap.size, screenWidth, screenHeight);
		Font font = client.gui.hud.getFont();

		if (!map.dimensionSupported()) {
			if (!mapConfig(client).hideInUnsupportedDimensions) {
				drawDisabled(graphics, font, panel);
			}
			return;
		}

		MapCamera camera = map.minimapCamera(player.getX(), player.getZ());
		double rotation = minimap.rotateWithPlayer ? MapProjection.rotationForYaw(player.getYRot()) : 0.0;
		MapProjection projection = new MapProjection(camera, panel.width(), panel.height(), rotation);
		MapConfig.SharedDisplay shared = mapConfig(client).sharedDisplay;

		graphics.fill(panel.x() - 1, panel.y() - 1, panel.x() + panel.width() + 1,
					panel.y() + panel.height() + 1, BACKDROP);
		graphics.enableScissor(panel.x(), panel.y(), panel.x() + panel.width(), panel.y() + panel.height());
		try {
			map.drawSurface(graphics, font, projection, panel.x(), panel.y(), MapSubsystem.Surface.MINIMAP,
						cameraFollowsMotion(minimap), new MapSubsystem.Layers(
									minimap.showClaims && shared.claimFillOpacityPercent > 0,
								minimap.showClaims && shared.claimOutlineOpacityPercent > 0,
								minimap.showPlayers,
								minimap.showPlayerNames,
								minimap.showTownNames,
								alpha(shared.claimFillOpacityPercent),
								alpha(shared.claimOutlineOpacityPercent)));
		} finally {
			graphics.disableScissor();
		}

		if (minimap.shape == MapConfig.MinimapShape.CIRCLE) {
			blitCircleMask(graphics, panel, alpha(minimap.opacity));
		} else {
			drawFrame(graphics, panel);
		}
		if (minimap.showCoordinates) {
			int y = MinimapLayout.coordinatesY(panel, screenHeight);
			if (y >= 0) {
				String coords = String.format(java.util.Locale.ROOT, "%,d %,d",
							(int) Math.floor(player.getX()), (int) Math.floor(player.getZ()));
				drawLabel(graphics, font, coords, screenWidth - MinimapLayout.MARGIN, y, true);
			}
		}
		if (minimap.showScaleBar) {
			int y = MinimapLayout.scaleBarY(panel, screenHeight);
			if (y >= 0) {
				MinimapLayout.ScaleBar bar = MinimapLayout.scaleBar(panel.width(), minimap.blocksAcross);
				int right = screenWidth - MinimapLayout.MARGIN;
				graphics.fill(right - bar.pixels(), y + 4, right, y + 6, LABEL_TEXT);
				graphics.fill(right - bar.pixels(), y + 6, right - bar.pixels() + 1, y + 7, LABEL_TEXT);
				graphics.fill(right - 1, y + 6, right, y + 7, LABEL_TEXT);
				drawLabel(graphics, font, bar.blocks() + " blocks", right, y + 8, true);
			}
		}
		map.surfaceDrawn();
	}

	/** Whether the minimap camera moves with the player this frame. */
	private boolean cameraFollowsMotion(MapConfig.Minimap minimap) {
		LocalPlayer player = Minecraft.getInstance().player;
		return minimap.centerPlayer && player != null
					&& (player.xOld != player.getX() || player.zOld != player.getZ());
	}

	private MapConfig mapConfig(Minecraft client) {
		return ZeaClient.config().map;
	}

	private void drawDisabled(GuiGraphicsExtractor graphics, Font font, MinimapLayout.Rect panel) {
		graphics.fill(panel.x() - 1, panel.y() - 1, panel.x() + panel.width() + 1,
					panel.y() + panel.height() + 1, BACKDROP);
		drawFrame(graphics, panel);
		graphics.centeredText(font, "No map", panel.x() + panel.width() / 2,
					panel.y() + panel.height() / 2 - font.lineHeight / 2, DISABLED_TEXT);
	}

	private void drawFrame(GuiGraphicsExtractor graphics, MinimapLayout.Rect panel) {
		graphics.fill(panel.x() - 1, panel.y() - 1, panel.x() + panel.width(), panel.y(), FRAME);
		graphics.fill(panel.x() - 1, panel.y() + panel.height(), panel.x() + panel.width(),
					panel.y() + panel.height() + 1, FRAME);
		graphics.fill(panel.x() - 1, panel.y(), panel.x(), panel.y() + panel.height(), FRAME);
		graphics.fill(panel.x() + panel.width(), panel.y(), panel.x() + panel.width() + 1,
					panel.y() + panel.height(), FRAME);
	}

	private void drawLabel(GuiGraphicsExtractor graphics, Font font, String text, int rightX, int y,
				boolean rightAligned) {
		int x = rightAligned ? rightX - font.width(text) : rightX;
		graphics.text(font, text, x + 1, y + 1, LABEL_SHADOW);
		graphics.text(font, text, x, y, LABEL_TEXT);
	}

	/** Blits (and lazily builds) the circle mask for the current size and opacity. */
	private void blitCircleMask(GuiGraphicsExtractor graphics, MinimapLayout.Rect panel, int alpha) {
		if (maskTexture == null || maskSize != panel.width() || maskAlpha != alpha) {
			rebuildMask(panel.width(), alpha);
		}
		if (maskView != null) {
			GpuSampler sampler = TileTextures.tileSampler();
			graphics.blit(maskView, sampler, panel.x() - 1, panel.y() - 1, panel.width() + 2, panel.height() + 2,
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
		for (int y = 0; y < dim; y++) {
			for (int x = 0; x < dim; x++) {
				double distance = Math.hypot(x - center, y - center);
				int color;
				if (distance <= ringInner) {
					color = 0x00000000;
				} else if (distance <= ringOuter) {
					color = 0xFF3C3C46;
				} else {
					color = (alpha << 24) | 0x101018;
				}
				image.setPixelABGR(x, y, abgr(color));
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
