package net.zaminmc.zea.map.render.worldmap;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.zaminmc.zea.map.MapSubsystem;
import net.zaminmc.zea.map.config.MapConfig;
import net.zaminmc.zea.map.model.MapCamera;
import net.zaminmc.zea.map.model.MapWorld;
import net.zaminmc.zea.map.projection.MapCameraInput;
import net.zaminmc.zea.map.projection.MapProjection;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import org.jspecify.annotations.Nullable;

/**
 * The full-screen world map ({@code /zeamap} or its keybind): tile
 * imagery, claims and players through the subsystem's shared surface
 * routine, at a camera the player owns - drag to pan, scroll to zoom
 * at the cursor, + and - to zoom the center, F to re-follow, W to
 * switch worlds.
 *
 * <p>Input is isolated by being a plain {@link Screen}: while the map
 * is open, nothing underneath sees a key or a click. The camera of
 * every world persists in the subsystem, so closing and reopening
 * lands where the player left it; follow-player re-centers it on the
 * local player until the next pan.
 */
public final class WorldMapScreen extends Screen {

	private static final int BACKDROP = 0xFF101018;
	private static final int HUD_TEXT = 0xFFE0E0E8;
	private static final int HUD_MUTED = 0xFF909098;

	/** GLFW keycodes used while no field is focused (there are none). */
	private static final int KEY_F = 70;
	private static final int KEY_W = 87;
	private static final int KEY_PLUS = 334;
	private static final int KEY_MINUS = 333;
	private static final int KEY_EQUAL = 61;

	/** Keyboard zoom step: three keys to one wheel notch. */
	private static final double KEYBOARD_ZOOM_FACTOR = Math.pow(MapCameraInput.ZOOM_PER_NOTCH, 1.0 / 3.0);

	private final MapSubsystem map;
	private MapWorld viewedWorld;
	private boolean followPlayer;

	@Nullable
	private MapCamera camera;
	private boolean dragging;
	private double dragX;
	private double dragY;

	public WorldMapScreen(MapSubsystem map, MapWorld viewedWorld, boolean followPlayer) {
		super(Component.translatable("zea.map.screen.title"));
		this.map = map;
		this.viewedWorld = viewedWorld;
		this.followPlayer = followPlayer;
	}

	/** The world this screen currently views. */
	public MapWorld viewedWorld() {
		return viewedWorld;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		LocalPlayer player = minecraft == null ? null : minecraft.player;
		double playerX = player == null ? 0 : player.getX();
		double playerZ = player == null ? 0 : player.getZ();
		MapCamera current = camera != null ? camera : map.worldCamera(viewedWorld, playerX, playerZ);
		if (followPlayer) {
			current = current.centeredOn(playerX, playerZ);
		}
		camera = current;
		MapConfig.WorldMap worldMap = map.worldMapConfig();
		MapConfig.SharedDisplay shared = map.sharedDisplayConfig();

		graphics.fill(0, 0, width, height, BACKDROP);
		MapProjection projection = MapProjection.northUp(camera, width, height);
		map.drawSurface(graphics, getFont(), projection, 0, 0, MapSubsystem.Surface.WORLD_MAP, dragging, new MapSubsystem.Layers(
					worldMap.showTownFills && shared.claimFillOpacityPercent > 0,
					worldMap.showTownOutlines && shared.claimOutlineOpacityPercent > 0,
					worldMap.showPlayers,
					worldMap.showPlayerLabels,
					worldMap.showTownLabels,
					alpha(shared.claimFillOpacityPercent),
					alpha(shared.claimOutlineOpacityPercent)));

		drawHud(graphics, mouseX, mouseY, projection);
		map.surfaceDrawn();
	}

	private void drawHud(GuiGraphicsExtractor graphics, int mouseX, int mouseY, MapProjection projection) {
		Font font = getFont();
		if (map.showFreshnessConfig()) {
			graphics.text(font, map.freshnessText(viewedWorld, System.currentTimeMillis()), 6, 6, HUD_MUTED);
		}
		String world = viewedWorld.displayName;
		if (viewedWorld == map.playerWorld()) {
			world += " (you are here)";
		}
		graphics.text(font, world, 6, 18, HUD_TEXT);
		String controls = "Drag: pan \u00b7 Scroll: zoom \u00b7 +\u2212: zoom \u00b7 F: "
					+ (followPlayer ? "unfollow" : "follow") + " \u00b7 W: switch world \u00b7 Esc: close";
		graphics.text(font, controls, 6, height - font.lineHeight - 4, HUD_MUTED);
		double worldX = projection.worldX(mouseX, mouseY);
		double worldZ = projection.worldZ(mouseX, mouseY);
		String cursor = String.format(java.util.Locale.ROOT, "%,d %,d",
					(int) Math.floor(worldX), (int) Math.floor(worldZ));
		int cursorWidth = font.width(cursor);
		graphics.text(font, cursor, width - cursorWidth - 6, height - font.lineHeight - 4, HUD_TEXT);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		dragging = true;
		dragX = event.x();
		dragY = event.y();
		return true;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		dragging = false;
		return true;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
		if (dragging && camera != null) {
			double dx = event.x() - dragX;
			double dy = event.y() - dragY;
			dragX = event.x();
			dragY = event.y();
			if (dx != 0 || dy != 0) {
				followPlayer = false;
				camera = MapCameraInput.pan(camera, 0.0, dx, dy);
				map.storeWorldCamera(camera);
			}
		}
		return true;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		if (camera != null && verticalAmount != 0) {
			double factor = verticalAmount > 0 ? MapCameraInput.ZOOM_PER_NOTCH : 1.0 / MapCameraInput.ZOOM_PER_NOTCH;
			camera = MapCameraInput.zoomAtCursor(camera, width, height, 0.0, mouseX, mouseY, factor);
			map.storeWorldCamera(camera);
		}
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.isEscape()) {
			if (minecraft != null) {
				minecraft.gui.setScreen(null);
			}
			return true;
		}
		int key = event.key();
		if (key == KEY_F) {
			followPlayer = !followPlayer;
			return true;
		}
		if (key == KEY_W) {
			viewedWorld = viewedWorld.other();
			camera = null;
			return true;
		}
		if (key == KEY_PLUS || key == KEY_EQUAL) {
			zoomCenter(KEYBOARD_ZOOM_FACTOR);
			return true;
		}
		if (key == KEY_MINUS) {
			zoomCenter(1.0 / KEYBOARD_ZOOM_FACTOR);
			return true;
		}
		return super.keyPressed(event);
	}

	private void zoomCenter(double factor) {
		if (camera == null) {
			return;
		}
		camera = MapCameraInput.zoomAtCursor(camera, width, height, 0.0, width / 2.0, height / 2.0, factor);
		map.storeWorldCamera(camera);
	}

	@Override
	public void removed() {
		map.worldMapClosed(this);
		super.removed();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private static int alpha(int percent) {
		return (int) Math.round(Math.clamp(percent, 0, 100) * 255 / 100.0);
	}
}
