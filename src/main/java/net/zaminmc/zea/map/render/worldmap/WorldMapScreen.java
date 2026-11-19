package net.zaminmc.zea.map.render.worldmap;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.zaminmc.zea.map.MapSubsystem;
import net.zaminmc.zea.map.config.MapConfig;
import net.zaminmc.zea.map.model.MapCamera;
import net.zaminmc.zea.map.model.MapWorld;
import net.zaminmc.zea.map.model.TownClaim;
import net.zaminmc.zea.map.projection.GlobeTransition;
import net.zaminmc.zea.map.projection.MapCameraInput;
import net.zaminmc.zea.map.projection.MapProjection;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Locale;

/**
 * The full-screen world map ({@code /zeamap} or its keybind): the EarthMC
 * map in the Item Browser's frame language — the gray plate, the light
 * reflective edge, the gold active state, no new palette — with the
 * subsystem's shared surface routine drawing everything behind the
 * chrome.
 *
 * <p>The camera rides the wheel: drag pans, the cursor anchors the zoom,
 * and zooming out past the whole-map fit point curls the flat map into
 * the globe ({@link GlobeTransition} — no mode switch, the same wheel
 * that zooms). F re-follows the player, W switches worlds, and the
 * search pill flies to a town by name through a real focused
 * {@link EditBox}, so the platform's text input (and its IME machinery)
 * works exactly like vanilla's fields.
 *
 * <p>Input is isolated by being a plain {@link Screen}: while the map is
 * open, nothing underneath sees a key or a click. The camera of every
 * world persists in the subsystem, so closing and reopening lands where
 * the player left it.
 */
public final class WorldMapScreen extends Screen {

        // The Item Browser's frame language.
        private static final int PLATE_DARK = 0xFF1E1E1E;
        private static final int PLATE_GRAY = 0xFF646464;
        private static final int PLATE_EDGE = 0xFFB4B4B4;
        private static final int PLATE_GOLD = 0xFFAB870F;
        private static final int GOLD_TEXT = 0xFFE8C14A;
        private static final int TEXT = 0xFFE0E0E8;
        private static final int MUTED = 0xFF909098;
        private static final int PILL_BACKING = 0x901E1E1E;

        /** GLFW-style scancodes the 26.3 key events carry. */
        private static final int KEY_F = 70;
        private static final int KEY_W = 87;
        private static final int KEY_PLUS = 334;
        private static final int KEY_MINUS = 333;
        private static final int KEY_EQUAL = 61;
        private static final int KEY_ENTER = 257;
        private static final int KEY_KP_ENTER = 335;

        /** Keyboard zoom step: three keys to one wheel notch. */
        private static final double KEYBOARD_ZOOM_FACTOR = Math.pow(MapCameraInput.ZOOM_PER_NOTCH, 1.0 / 3.0);

        /** The search pill's geometry (GUI pixels, right-anchored in the header). */
        private static final int PILL_HEIGHT = 14;
        private static final int PILL_WIDTH = 130;
        private static final int PILL_RIGHT_MARGIN = 6;
        private static final int PILL_TOP = 4;

        private final MapSubsystem map;
        private MapWorld viewedWorld;
        private boolean followPlayer;

        @Nullable
        private MapCamera camera;
        @Nullable
        private EditBox searchField;
        private boolean searchOpen;
        private boolean dragging;
        private double dragX;
        private double dragY;
        @Nullable
        private String searchMiss;
        private long searchMissAtMs;

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
        protected void init() {
                Font font = getFont();
                searchField = new EditBox(font, 0, 0, PILL_WIDTH - 8, PILL_HEIGHT - 4,
                                                Component.translatable("zea.map.search"));
                searchField.setMaxLength(64);
                searchField.setResponder(text -> searchMiss = null);
                searchField.setVisible(false);
        }

        @Override
        public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
                Minecraft client = minecraft == null ? null : minecraft;
                var player = client == null ? null : client.player;
                double playerX = player == null ? 0 : player.getX();
                double playerZ = player == null ? 0 : player.getZ();
                MapCamera current = camera != null ? camera : map.worldCamera(viewedWorld, playerX, playerZ);
                if (followPlayer) {
                        current = current.centeredOn(playerX, playerZ);
                }
                camera = current;
                MapConfig.WorldMap worldMap = map.worldMapConfig();
                MapConfig.SharedDisplay shared = map.sharedDisplayConfig();

                MapProjection projection = MapProjection.northUp(camera, width, height);
                map.drawSurface(graphics, getFont(), projection, 0, 0, MapSubsystem.Surface.WORLD_MAP, dragging,
                                                new MapSubsystem.Layers(
                                                                        worldMap.showTownFills && shared.claimFillOpacityPercent > 0,
                                                                        worldMap.showTownOutlines && shared.claimOutlineOpacityPercent > 0,
                                                                        worldMap.showPlayers,
                                                                        worldMap.showPlayerLabels,
                                                                        worldMap.showTownLabels,
                                                                        alpha(shared.claimFillOpacityPercent),
                                                                        alpha(shared.claimOutlineOpacityPercent)));

                drawChrome(graphics, mouseX, mouseY, projection, player != null);
        }

        // ------------------------------------------------------------------
        // Chrome
        // ------------------------------------------------------------------

        private void drawChrome(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                        MapProjection projection, boolean hasPlayer) {
                Font font = getFont();

                // The screen's edge trim: the browser pane's plate and light
                // edge, one ring around the whole map.
                graphics.fill(0, 0, width, 1, PLATE_GRAY);
                graphics.fill(0, height - 1, width, height, PLATE_GRAY);
                graphics.fill(0, 1, 1, height - 1, PLATE_GRAY);
                graphics.fill(width - 1, 1, width, height - 1, PLATE_GRAY);

                // Header, left: the world's name — gold while the screen
                // views a world the player is not standing in, plain white
                // otherwise — plus the follow state.
                String world = viewedWorld.displayName;
                boolean elsewhere = viewedWorld != map.playerWorld();
                if (hasPlayer && viewedWorld == map.playerWorld()) {
                        world += " (you are here)";
                }
                drawLabel(graphics, font, world, 8, 7, elsewhere ? GOLD_TEXT : TEXT);

                // Header, right: freshness, then the search pill.
                if (map.showFreshnessConfig()) {
                        String freshness = map.freshnessText(viewedWorld, System.currentTimeMillis());
                        int right = pillLeft() - 8;
                        graphics.text(font, freshness, right - font.width(freshness), 7, MUTED);
                }
                drawSearchPill(graphics, font, mouseX, mouseY);

                // Footer: the controls hint at the left, the follow state and
                // the cursor's coordinates at the right.
                String follow = "F: " + (followPlayer ? "following" : "follow");
                int followWidth = font.width(follow);
                graphics.text(font, follow, 8, height - font.lineHeight - 4, followPlayer ? GOLD_TEXT : MUTED);
                String worldSwitch = "W: " + viewedWorld.other().displayName;
                graphics.text(font, worldSwitch, 8 + followWidth + 14, height - font.lineHeight - 4, MUTED);
                double worldX = projection.worldX(mouseX, mouseY);
                double worldZ = projection.worldZ(mouseX, mouseY);
                String cursor = String.format(Locale.ROOT, "%,d %,d",
                                                (int) Math.floor(worldX), (int) Math.floor(worldZ));
                int cursorWidth = font.width(cursor);
                graphics.text(font, cursor, width - cursorWidth - 8, height - font.lineHeight - 4, TEXT);
                graphics.text(font, "Esc: close", width - cursorWidth - 8 - font.width("Esc: close") - 14,
                                                height - font.lineHeight - 4, MUTED);

                // A search miss fades for a few seconds under the pill.
                if (searchMiss != null && System.currentTimeMillis() - searchMissAtMs < 3_500L) {
                        graphics.text(font, searchMiss, pillLeft(), PILL_TOP + PILL_HEIGHT + 2, GOLD_TEXT);
                }
        }

        private void drawSearchPill(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
                int left = pillLeft();
                int top = PILL_TOP;
                int right = width - PILL_RIGHT_MARGIN;
                int bottom = top + PILL_HEIGHT;
                boolean hovered = mouseX >= left && mouseX < right && mouseY >= top && mouseY < bottom;
                // The plate: gray when live, the gold plate while the search
                // owns the keyboard.
                int plate = searchOpen ? PLATE_GOLD : PLATE_GRAY;
                graphics.fill(left - 1, top - 1, right + 1, bottom + 1, PLATE_EDGE);
                graphics.fill(left, top, right, bottom, plate);
                graphics.fill(left + 1, top + 1, right - 1, bottom - 1, searchOpen ? PLATE_GOLD : PLATE_DARK);

                Font f = getFont();
                if (searchOpen && searchField != null) {
                        searchField.setX(left + 4);
                        searchField.setY(top + (PILL_HEIGHT - searchField.getHeight()) / 2);
                        searchField.extractRenderState(graphics, mouseX, mouseY, 0.0f);
                } else {
                        String placeholder = hovered ? "Search towns" : "Search";
                        int color = hovered ? TEXT : MUTED;
                        graphics.text(font, placeholder, left + 5,
                                                        top + (PILL_HEIGHT - font.lineHeight) / 2 + 1, color);
                }
        }

        private void drawLabel(GuiGraphicsExtractor graphics, Font font, String text, int x, int y, int color) {
                graphics.text(font, text, x + 1, y + 1, 0x90202020);
                graphics.text(font, text, x, y, color);
        }

        private int pillLeft() {
                return width - PILL_RIGHT_MARGIN - PILL_WIDTH;
        }

        // ------------------------------------------------------------------
        // Search
        // ------------------------------------------------------------------

        private void openSearch() {
                if (searchOpen || searchField == null) {
                        return;
                }
                searchOpen = true;
                searchField.setVisible(true);
                searchField.setValue("");
                searchField.setFocused(true);
                searchField.setEditable(true);
        }

        private void closeSearch() {
                if (!searchOpen || searchField == null) {
                        return;
                }
                searchOpen = false;
                searchField.setFocused(false);
                searchField.setVisible(false);
        }

        /** Flies to the first town whose name contains the query. */
        private void flyToSearch() {
                if (searchField == null) {
                        return;
                }
                String query = searchField.getValue().trim();
                if (query.isEmpty()) {
                        closeSearch();
                        return;
                }
                TownClaim hit = findTown(query);
                if (hit == null) {
                        searchMiss = "No town named \"" + query + "\"";
                        searchMissAtMs = System.currentTimeMillis();
                        return;
                }
                // A comfortable regional zoom: the town plus its neighbors.
                double targetPpb = Math.clamp(width / 1200.0,
                                                MapCamera.MIN_PIXELS_PER_BLOCK, MapCamera.MAX_PIXELS_PER_BLOCK);
                camera = map.worldCamera(viewedWorld, hit.centerX(), hit.centerZ())
                                                .centeredOn(hit.centerX(), hit.centerZ());
                if (Math.abs(camera.pixelsPerBlock() - targetPpb) > targetPpb * 0.01) {
                        camera = camera.zoomedAround(hit.centerX(), hit.centerZ(),
                                                        targetPpb / camera.pixelsPerBlock());
                }
                map.storeWorldCamera(camera);
                followPlayer = false;
                searchMiss = null;
                closeSearch();
        }

        /** The first claim whose name contains the query, case-insensitively. */
        @Nullable
        private TownClaim findTown(String query) {
                String needle = query.toLowerCase(Locale.ROOT);
                List<TownClaim> claims = map.claimsOf(viewedWorld);
                TownClaim exact = null;
                for (TownClaim claim : claims) {
                        String name = claim.name().toLowerCase(Locale.ROOT);
                        if (name.equals(needle)) {
                                exact = claim;
                                break;
                        }
                        if (exact == null && name.contains(needle)) {
                                exact = claim;
                        }
                }
                return exact;
        }

        // ------------------------------------------------------------------
        // Input
        // ------------------------------------------------------------------

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
                int left = pillLeft();
                int top = PILL_TOP;
                boolean inPill = event.x() >= left && event.x() < width - PILL_RIGHT_MARGIN
                                                && event.y() >= top && event.y() < top + PILL_HEIGHT;
                if (inPill) {
                        openSearch();
                        return true;
                }
                if (searchOpen) {
                        closeSearch();
                        return true;
                }
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
                if (searchOpen) {
                        return true;
                }
                if (camera != null && verticalAmount != 0) {
                        double factor = verticalAmount > 0 ? MapCameraInput.ZOOM_PER_NOTCH : 1.0 / MapCameraInput.ZOOM_PER_NOTCH;
                        camera = MapCameraInput.zoomAtCursor(camera, width, height, 0.0, mouseX, mouseY, factor);
                        map.storeWorldCamera(camera);
                }
                return true;
        }

        @Override
        public boolean keyPressed(KeyEvent event) {
                if (searchOpen && searchField != null) {
                        if (event.isEscape()) {
                                closeSearch();
                                return true;
                        }
                        if (event.key() == KEY_ENTER || event.key() == KEY_KP_ENTER) {
                                flyToSearch();
                                return true;
                        }
                        if (searchField.keyPressed(event)) {
                                return true;
                        }
                        return true;
                }
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

        @Override
        public boolean charTyped(net.minecraft.client.input.CharacterEvent event) {
                if (searchOpen && searchField != null && searchField.charTyped(event)) {
                        return true;
                }
                return super.charTyped(event);
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
                closeSearch();
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
