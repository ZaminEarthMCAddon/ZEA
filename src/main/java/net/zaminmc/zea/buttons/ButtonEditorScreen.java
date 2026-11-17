package net.zaminmc.zea.buttons;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.zaminmc.zea.ZeaClient;
import net.zaminmc.zea.config.ZeaConfig;
import net.zaminmc.zea.search.SearchBarLayout;
import net.zaminmc.zea.search.SearchBarWidget;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.jspecify.annotations.Nullable;

/**
 * The NEU-style button editor: the standard inventory drawn as a canvas,
 * every button on it at its stored position, and a hover panel beside
 * the selected one for the details — command (with server-backed tab
 * completion), name, background style, and a searchable icon picker.
 *
 * <p>Interaction follows the reference port's grammar, adapted to the
 * dev.36 zone model: click a button to select it, click it again to
 * open its panel, drag to move (the drop magnetically docks to the
 * nearest free zone slot — the nine zones with their capacity limits
 * are the only places a button can live), right-click empty canvas to
 * create a button in the nearest free slot, Backspace on a selected
 * button to delete it, Escape to leave.
 *
 * <p>The panel edits a working copy; leaving the editor applies it if
 * it validates (a blank command refuses, and the stored button stands).
 * Every accepted change persists immediately through the coordinator's
 * safe-write store, so a crash mid-edit loses at most the working copy.
 *
 * <p>The canvas is the real inventory: the standard texture with every
 * live slot of the player's menu drawn on it, so a button is placed over
 * what the player is actually carrying. The search bar renders below in
 * its real footprint through the shared {@link SearchBarLayout} — the
 * same rectangle the container screens and the above-search zone are
 * laid out against — as a preview only: it is never a child widget, so
 * it cannot take focus, carets or clicks.
 */
public final class ButtonEditorScreen extends Screen {
        /** The standard survival inventory texture the canvas draws. */
        private static final Identifier INVENTORY_TEXTURE =
                        Identifier.fromNamespaceAndPath("minecraft", "textures/gui/container/inventory.png");

        /** The GLFW keycodes this screen reacts to when no field is focused. */
        private static final int GLFW_KEY_BACKSPACE = 259;
        private static final int GLFW_KEY_DELETE = 261;

        private static final int CANVAS_X = ButtonGeometry.CANVAS_WIDTH;
        private static final int CANVAS_Y = ButtonGeometry.CANVAS_HEIGHT;

        private static final int PANEL_WIDTH = 150;
        private static final int PANEL_HEIGHT = 262;
        private static final int PANEL_TEXT_X = 7;
        private static final int FIELD_WIDTH = PANEL_WIDTH - 14;
        private static final int FIELD_HEIGHT = 16;

        // Panel rows, top to bottom.
        private static final int ROW_COMMAND_LABEL = 7;
        private static final int ROW_COMMAND_FIELD = 19;
        private static final int ROW_NAME_LABEL = 40;
        private static final int ROW_NAME_FIELD = 52;
        private static final int ROW_STYLE_LABEL = 73;
        private static final int ROW_STYLE_SWATCHES = 85;
        private static final int ROW_FILTER_TABS = 107;
        private static final int ROW_SEARCH_LABEL = 132;
        private static final int ROW_SEARCH_FIELD = 144;
        private static final int ROW_ICON_GRID = 167;
        private static final int ICON_GRID_HEIGHT = 72;
        private static final int ROW_TOGGLE_ROW = 244;

        private static final int GRID_COLUMNS = 6;
        private static final int GRID_PITCH = 20;

        /** The GLFW keycode of Tab; see {@link CommandSuggestor#KEY_TAB}. */
        private static final int KEY_TAB = 258;

        /** The preset row, top-left like the reference's IO buttons. */
        private static final int PRESET_ROW_X = 8;
        private static final int PRESET_ROW_Y = 8;
        private static final int PRESET_BUTTON_WIDTH = 82;
        private static final int PRESET_BUTTON_HEIGHT = 18;
        private static final int PRESET_GAP = 4;

        private final Screen parent;
        private final InventoryButtons owner;
        private final List<IconPicker.Icon> allIcons = new ArrayList<>();

        private int guiLeft;
        private int guiTop;
        private int panelLeft;
        private int panelTop;

        private EditBox commandField;
        private EditBox nameField;
        private EditBox iconSearchField;
        private CommandSuggestor suggestor;

        /** The search bar preview below the canvas; renders only, never takes input. */
        @Nullable
        private SearchBarWidget barPreview;

        private final ButtonDragSession drag = new ButtonDragSession();
        private ButtonEditForm form;
        private boolean panelOpen;
        private IconPicker.Filter filter = IconPicker.Filter.ALL;
        private List<IconPicker.Icon> shownIcons = List.of();
        private int iconScroll;

        private static final int PANEL_FILL = 0xFF202020;
        private static final int PANEL_BORDER = 0xFF505050;
        private static final int PANEL_LABEL = 0xFFA0A0A0;
        private static final int HOVER_FILL = 0xFF606060;
        private static final int IDLE_FILL = 0xFF303030;
        private static final int SELECTED_OVERLAY = 0x8000FF00;

        public ButtonEditorScreen(Screen parent, InventoryButtons owner) {
                super(Component.translatable("zea.buttons.editor.title"));
                this.parent = parent;
                this.owner = owner;
        }

        /** The button the panel edits, or {@code null} when nothing is selected. */
        private InventoryButton selected() {
                return form == null ? null : form.button();
        }

        @Override
        protected void init() {
                guiLeft = (width - CANVAS_X) / 2;
                guiTop = (height - CANVAS_Y) / 2;

                commandField = new EditBox(font, 0, 0, FIELD_WIDTH, FIELD_HEIGHT, Component.literal("Command"));
                commandField.setMaxLength(InventoryButton.MAX_COMMAND_LENGTH);
                commandField.setResponder(text -> {
                        if (form == null) {
                                return;
                        }
                        String normalized = text.isEmpty() ? "/"
                                        : text.startsWith("/") ? text
                                        : "/" + text.replace("/", "");
                        if (!normalized.equals(text)) {
                                commandField.setValue(normalized);
                                commandField.moveCursorTo(normalized.length(), false);
                        }
                        if (suggestor != null) {
                                suggestor.refresh();
                        }
                });

                nameField = new EditBox(font, 0, 0, FIELD_WIDTH, FIELD_HEIGHT, Component.literal("Name"));
                nameField.setMaxLength(InventoryButton.MAX_NAME_LENGTH);

                iconSearchField = new EditBox(font, 0, 0, FIELD_WIDTH, FIELD_HEIGHT, Component.literal("Icon"));
                iconSearchField.setMaxLength(64);
                iconSearchField.setResponder(text -> refreshIcons());

                suggestor = new CommandSuggestor(minecraft, commandField);

                ZeaConfig config = ZeaClient.config();
                if (config != null && config.inventorySearchEnabled()) {
                        barPreview = new SearchBarWidget(font, 0, 0, CANVAS_X, ZeaClient.search());
                        barPreview.calculatorEnabled(config.inventorySearchCalculatorEnabled());
                        // The real bar restores the per-screen query; the preview mirrors it.
                        barPreview.setValue(ZeaClient.search().query());
                } else {
                        barPreview = null;
                }

                buildIconList();
                refreshIcons();
        }

        /** The choosable icons: every registry item plus ZEA's custom glyphs. */
        private void buildIconList() {
                allIcons.clear();
                for (var item : BuiltInRegistries.ITEM) {
                        Identifier id = BuiltInRegistries.ITEM.getKey(item);
                        String display;
                        try {
                                display = new ItemStack(item).getHoverName().getString();
                        } catch (Exception e) {
                                // A broken display name must not take the picker down.
                                display = id.getPath();
                        }
                        allIcons.add(new IconPicker.Icon(display, id.toString(), item instanceof BlockItem, false));
                }
                for (String configId : ButtonIcons.CUSTOM_TEXTURES.keySet()) {
                        String name = configId.substring("custom:".length()).replace('_', ' ');
                        allIcons.add(new IconPicker.Icon(name.substring(0, 1).toUpperCase(Locale.ROOT) + name.substring(1),
                                        configId, false, true));
                }
        }

        private void refreshIcons() {
                shownIcons = IconPicker.filter(allIcons, List.of(ButtonIcons.QUICK_PICK),
                                iconSearchField == null ? "" : iconSearchField.getValue(), filter);
                iconScroll = 0;
        }

        @Override
        public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
                guiLeft = (width - CANVAS_X) / 2;
                guiTop = (height - CANVAS_Y) / 2;

                context.blit(RenderPipelines.GUI_TEXTURED, INVENTORY_TEXTURE, guiLeft, guiTop,
                                0.0f, 0.0f, CANVAS_X, CANVAS_Y, 256, 256);

                zea$renderLiveInventory(context);

                for (InventoryButton button : owner.buttons()) {
                        int x = canvasToScreenX(button);
                        int y = canvasToScreenY(button);
                        boolean isSelected = selected() != null && button.id().equals(selected().id());
                        boolean isDragged = drag.isActive() && drag.button().id().equals(button.id());
                        if (isDragged) {
                                x = drag.previewX();
                                y = drag.previewY();
                        }
                        if (isSelected) {
                                context.fill(x, y, x + ButtonGeometry.SIZE, y + ButtonGeometry.SIZE, SELECTED_OVERLAY);
                                ButtonRenderer.drawBorder(context, x, y, 0xFFFFFFFF);
                        } else {
                                ButtonRenderer.drawSwatch(context, x, y, button.style());
                        }
                        drawButtonFace(context, button, x, y);
                }

                if (barPreview != null) {
                        SearchBarLayout.apply(barPreview, guiLeft, CANVAS_X, height);
                        barPreview.extractWidgetRenderState(context, mouseX, mouseY, delta);
                }

                if (selected() == null) {
                        context.centeredText(font, Component.translatable("zea.buttons.editor.hint.select"),
                                        width / 2, 8, 0xFFFFFFFF);
                        context.centeredText(font, Component.translatable("zea.buttons.editor.hint.delete"),
                                        width / 2, 20, 0xFFAAAAAA);
                        context.centeredText(font, Component.translatable("zea.buttons.editor.hint.create"),
                                        width / 2, 32, 0xFFAAAAAA);
                }

                renderPresetRow(context, mouseX, mouseY);

                if (panelOpen && selected() != null) {
                        updatePanelCoordinates();
                        renderPanel(context, mouseX, mouseY, delta);
                }
        }

        /** The button's face — the container view's own drawing, so the preview is exact. */
        private void drawButtonFace(GuiGraphicsExtractor context, InventoryButton button, int x, int y) {
                ButtonRenderer.drawFace(context, font, button, x, y, true);
        }

        /**
         * The real inventory, drawn into the canvas: every live slot of the
         * player's menu at its vanilla position — items, counts, durability
         * bars and all — so a button is placed over what the player is
         * actually carrying instead of a blank grid that hides it.
         */
        private void zea$renderLiveInventory(GuiGraphicsExtractor context) {
                Player player = minecraft == null ? null : minecraft.player;
                if (player == null) {
                        return;
                }
                for (Slot slot : player.inventoryMenu.slots) {
                        ItemStack stack = slot.getItem();
                        if (!stack.isEmpty()) {
                                int x = guiLeft + slot.x;
                                int y = guiTop + slot.y;
                                context.item(stack, x, y);
                                context.itemDecorations(font, stack, x, y);
                        }
                }
        }

        /**
         * The editor canvas's geometry: the standard inventory texture drawn
         * at its own size, the editor's screen as the world. Zones compute
         * their slots from it, so the editor shows every button exactly where
         * the container screens will draw it.
         */
        private ButtonGeometry.Inputs canvasInputs() {
                return new ButtonGeometry.Inputs(guiLeft, guiTop, CANVAS_X, CANVAS_Y,
                                                                                                        width, height, true, false);
        }

        private List<ButtonGeometry.Placement> canvasPlacements() {
                return ButtonGeometry.placements(owner.buttons(), canvasInputs());
        }

        private int canvasToScreenX(InventoryButton button) {
                return placementOf(button).x();
        }

        private int canvasToScreenY(InventoryButton button) {
                return placementOf(button).y();
        }

        /** The button's editor-canvas rectangle; unplaceable buttons park off-canvas. */
        private ButtonGeometry.Placement placementOf(InventoryButton button) {
                for (ButtonGeometry.Placement placement : canvasPlacements()) {
                        if (placement.button().id().equals(button.id())) {
                                return placement;
                        }
                }
                return new ButtonGeometry.Placement(button, guiLeft - 2 * ButtonGeometry.SIZE,
                                                                                                        guiTop - 2 * ButtonGeometry.SIZE, false);
        }

        /** The preset row: one small bordered button per preset, plus the restore hint. */
        private void renderPresetRow(GuiGraphicsExtractor context, int mouseX, int mouseY) {
                List<ButtonPresets.Preset> presets = ButtonPresets.all();
                for (int i = 0; i < presets.size(); i++) {
                        int x = PRESET_ROW_X + i * (PRESET_BUTTON_WIDTH + PRESET_GAP);
                        int y = PRESET_ROW_Y;
                        boolean hover = mouseX >= x && mouseX < x + PRESET_BUTTON_WIDTH
                                        && mouseY >= y && mouseY < y + PRESET_BUTTON_HEIGHT;
                        context.fill(x, y, x + PRESET_BUTTON_WIDTH, y + PRESET_BUTTON_HEIGHT,
                                        hover ? HOVER_FILL : IDLE_FILL);
                        ButtonRenderer.drawBorder(context, x, y, PRESET_BUTTON_WIDTH, PRESET_BUTTON_HEIGHT, PANEL_BORDER);
                        // Labels fit their own button — "Buttons Everywhere" is
                        // wider than the 82-pixel preset and must shrink, not spill.
                        ButtonRenderer.drawFittedCenteredText(context, font,
                                        Component.translatable(presets.get(i).nameKey()).getString(),
                                        x + PRESET_BUTTON_WIDTH / 2, y + 5, PRESET_BUTTON_WIDTH - 8, 0xFFFFFFFF);
                }
                if (owner.hasRestorePoint()) {
                        context.text(font, Component.translatable("zea.buttons.list.applied"),
                                        PRESET_ROW_X, PRESET_ROW_Y + PRESET_BUTTON_HEIGHT + 4, 0xFF55FF55, false);
                }
        }

        /** The preset button under the point, or {@code null}. */
        private ButtonPresets.Preset presetAt(double mouseX, double mouseY) {
                List<ButtonPresets.Preset> presets = ButtonPresets.all();
                for (int i = 0; i < presets.size(); i++) {
                        int x = PRESET_ROW_X + i * (PRESET_BUTTON_WIDTH + PRESET_GAP);
                        if (mouseX >= x && mouseX < x + PRESET_BUTTON_WIDTH
                                        && mouseY >= PRESET_ROW_Y && mouseY < PRESET_ROW_Y + PRESET_BUTTON_HEIGHT) {
                                return presets.get(i);
                        }
                }
                return null;
        }

        /** The panel sits right of the selected button, always fully on screen. */
        private void updatePanelCoordinates() {
                int x = canvasToScreenX(selected()) + ButtonGeometry.SIZE + 7;
                int y = canvasToScreenY(selected()) - 20;
                if (x + PANEL_WIDTH > width) {
                        x = canvasToScreenX(selected()) - PANEL_WIDTH - 5;
                }
                if (y + PANEL_HEIGHT > height) {
                        y = height - PANEL_HEIGHT - 5;
                }
                if (y < 5) {
                        y = 5;
                }
                panelLeft = x;
                panelTop = y;
        }

        private void renderPanel(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
                context.fill(panelLeft, panelTop, panelLeft + PANEL_WIDTH, panelTop + PANEL_HEIGHT, PANEL_FILL);
                ButtonRenderer.drawBorder(context, panelLeft, panelTop, PANEL_WIDTH, PANEL_HEIGHT, PANEL_BORDER);

                context.text(font, Component.translatable("zea.buttons.editor.command"),
                                panelLeft + PANEL_TEXT_X, panelTop + ROW_COMMAND_LABEL, PANEL_LABEL, false);
                positionField(commandField, panelTop + ROW_COMMAND_FIELD);
                commandField.extractRenderState(context, mouseX, mouseY, delta);

                context.text(font, Component.translatable("zea.buttons.editor.name"),
                                panelLeft + PANEL_TEXT_X, panelTop + ROW_NAME_LABEL, PANEL_LABEL, false);
                positionField(nameField, panelTop + ROW_NAME_FIELD);
                nameField.extractRenderState(context, mouseX, mouseY, delta);

                context.text(font, Component.translatable("zea.buttons.editor.background"),
                                panelLeft + PANEL_TEXT_X, panelTop + ROW_STYLE_LABEL, PANEL_LABEL, false);
                for (int i = 0; i < InventoryButton.STYLE_COUNT; i++) {
                        int sx = panelLeft + PANEL_TEXT_X + i * 20;
                        int sy = panelTop + ROW_STYLE_SWATCHES;
                        ButtonRenderer.drawSwatch(context, sx, sy, i);
                        if (selected().style() == i) {
                                ButtonRenderer.drawBorder(context, sx - 1, sy - 1, ButtonGeometry.SIZE + 2,
                                                ButtonGeometry.SIZE + 2, 0xFF00FF00);
                        }
                }

                renderFilterTabs(context, mouseX, mouseY);
                renderIconGrid(context, mouseX, mouseY, delta);

                renderMiniButton(context, mouseX, mouseY, 0, "zea.buttons.editor.close",
                                selected().closeOnExecute());
                renderMiniButton(context, mouseX, mouseY, 1, "zea.buttons.editor.enabled",
                                selected().enabled());
                renderMiniButton(context, mouseX, mouseY, 2, form.deleteArmed()
                                ? "zea.buttons.editor.delete.armed" : "zea.buttons.editor.delete",
                                false);

                // The completion popup sits on top of everything else in the panel.
                suggestor.render(context, mouseX, mouseY, width, height);
        }

        private void renderFilterTabs(GuiGraphicsExtractor context, int mouseX, int mouseY) {
                IconPicker.Filter[] modes = IconPicker.Filter.values();
                int tabWidth = FIELD_WIDTH / modes.length;
                for (int i = 0; i < modes.length; i++) {
                        int x = panelLeft + PANEL_TEXT_X + i * tabWidth;
                        int y = panelTop + ROW_FILTER_TABS;
                        boolean active = filter == modes[i];
                        context.fill(x, y, x + tabWidth, y + 18, active ? HOVER_FILL : IDLE_FILL);
                        ButtonRenderer.drawBorder(context, x, y, tabWidth, 18, active ? 0xFFFFFFFF : PANEL_BORDER);
                        context.centeredText(font, Component.translatable("zea.buttons.editor.filter."
                                        + modes[i].name().toLowerCase(Locale.ROOT)), x + tabWidth / 2, y + 5,
                                        active ? 0xFFFFFFFF : PANEL_LABEL);
                }
        }

        private void renderIconGrid(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
                context.text(font, Component.translatable("zea.buttons.editor.search"),
                                panelLeft + PANEL_TEXT_X, panelTop + ROW_SEARCH_LABEL, PANEL_LABEL, false);
                positionField(iconSearchField, panelTop + ROW_SEARCH_FIELD);
                iconSearchField.extractRenderState(context, mouseX, mouseY, delta);

                int listX = panelLeft + PANEL_TEXT_X;
                int listY = panelTop + ROW_ICON_GRID;
                int listW = FIELD_WIDTH;
                context.fill(listX, listY, listX + listW, listY + ICON_GRID_HEIGHT, 0xFF101010);
                context.enableScissor(listX, listY, listX + listW, listY + ICON_GRID_HEIGHT);

                int firstRow = iconScroll / GRID_PITCH;
                int visibleRows = ICON_GRID_HEIGHT / GRID_PITCH;
                IconPicker.Icon hovered = null;
                for (int row = 0; row <= visibleRows; row++) {
                        for (int col = 0; col < GRID_COLUMNS; col++) {
                                int index = (firstRow + row) * GRID_COLUMNS + col;
                                if (index >= shownIcons.size()) {
                                        break;
                                }
                                IconPicker.Icon icon = shownIcons.get(index);
                                int ix = listX + 2 + col * GRID_PITCH;
                                int iy = listY + 2 + row * GRID_PITCH - iconScroll % GRID_PITCH;
                                drawIcon(context, icon, ix, iy);
                                if (mouseX >= ix && mouseX < ix + 16 && mouseY >= iy && mouseY < iy + 16) {
                                        context.fill(ix, iy, ix + 16, iy + 16, 0x40FFFFFF);
                                        hovered = icon;
                                }
                        }
                }
                context.disableScissor();

                if (hovered != null) {
                        context.setTooltipForNextFrame(font, Component.literal(hovered.displayName()), mouseX, mouseY);
                }

                int totalRows = (shownIcons.size() + GRID_COLUMNS - 1) / GRID_COLUMNS;
                if (totalRows > visibleRows) {
                        float ratio = (float) iconScroll / ((totalRows - visibleRows) * GRID_PITCH);
                        ratio = Math.min(1f, ratio);
                        int barHeight = Math.max(10, ICON_GRID_HEIGHT * visibleRows / totalRows);
                        int barY = listY + (int) ((ICON_GRID_HEIGHT - barHeight) * ratio);
                        context.fill(listX + listW - 2, barY, listX + listW, barY + barHeight, 0xFF808080);
                }
        }

        private void drawIcon(GuiGraphicsExtractor context, IconPicker.Icon icon, int x, int y) {
                Identifier custom = ButtonIcons.customTextureFor(icon.configId());
                if (custom != null) {
                        context.blit(RenderPipelines.GUI_TEXTURED, custom, x, y, 0.0f, 0.0f, 16, 16, 16, 16);
                        return;
                }
                context.item(ButtonIcons.iconFor(icon.configId()), x, y);
        }

        private void renderMiniButton(GuiGraphicsExtractor context, int mouseX, int mouseY, int slot,
                        String key, boolean active) {
                int width = (FIELD_WIDTH - 8) / 3;
                int x = panelLeft + PANEL_TEXT_X + slot * (width + 4);
                int y = panelTop + ROW_TOGGLE_ROW;
                boolean hover = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + 16;
                int fill = active || hover ? HOVER_FILL : IDLE_FILL;
                if (slot == 2 && form.deleteArmed()) {
                        fill = 0xFF802020;
                }
                context.fill(x, y, x + width, y + 16, fill);
                ButtonRenderer.drawBorder(context, x, y, width, 16, PANEL_BORDER);
                context.centeredText(font, Component.translatable(key).withStyle(
                                active ? net.minecraft.ChatFormatting.WHITE : net.minecraft.ChatFormatting.GRAY),
                                x + width / 2, y + 4, 0xFFFFFFFF);
        }

        private void positionField(EditBox field, int y) {
                field.setX(panelLeft + PANEL_TEXT_X);
                field.setY(y);
        }

        @Override
        public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent click, boolean doubled) {
                double mouseX = click.x();
                double mouseY = click.y();
                int button = click.button();

                ButtonPresets.Preset preset = presetAt(mouseX, mouseY);
                if (preset != null) {
                        closePanel(true);
                        owner.applyPreset(preset);
                        return true;
                }

                if (panelOpen && selected() != null && panelContains(mouseX, mouseY)) {
                        return handlePanelClick(click, doubled, mouseX, mouseY);
                }
                if (panelOpen && selected() != null && suggestor.mouseClicked(mouseX, mouseY)) {
                        return true;
                }

                for (InventoryButton candidate : owner.buttons()) {
                        int x = canvasToScreenX(candidate);
                        int y = canvasToScreenY(candidate);
                        if (mouseX >= x && mouseX < x + ButtonGeometry.SIZE && mouseY >= y && mouseY < y + ButtonGeometry.SIZE) {
                                select(candidate);
                                drag.begin(candidate, x, y, mouseX, mouseY);
                                return true;
                        }
                }

                if (button == InputConstants.MOUSE_BUTTON_RIGHT) {
                        ButtonGeometry.SlotRect target = ButtonGeometry.dropTarget(owner.buttons(), null,
                                        (int) mouseX - guiLeft, (int) mouseY - guiTop, canvasInputs());
                        ButtonGeometry.Zone zone = target != null ? target.zone() : ButtonGeometry.Zone.RIGHT;
                        InventoryButton created = InventoryButton.newButton(owner.buttons().size() + 1,
                                        zone.name(), Math.max(0, target == null ? 0 : target.order()));
                        owner.add(created);
                        openPanel(created);
                        drag.begin(created, canvasToScreenX(created), canvasToScreenY(created), mouseX, mouseY);
                        return true;
                }

                closePanel(true);
                return super.mouseClicked(click, doubled);
        }

        private void select(InventoryButton button) {
                if (selected() == null || !selected().id().equals(button.id())) {
                        closePanel(true);
                        form = new ButtonEditForm(button);
                        return;
                }
                if (!panelOpen) {
                        openPanel(button);
                }
        }

        private void openPanel(InventoryButton button) {
                form = new ButtonEditForm(button);
                panelOpen = true;
                commandField.setValue(button.command().startsWith("/") ? button.command() : "/" + button.command());
                commandField.moveCursorTo(commandField.getValue().length(), false);
                commandField.setFocused(true);
                nameField.setValue(button.name());
                nameField.setFocused(false);
                iconSearchField.setValue("");
                iconSearchField.setFocused(false);
                filter = IconPicker.Filter.ALL;
                refreshIcons();
        }

        /** Applies the working copy if it validates; the stored button stands otherwise. */
        private void closePanel(boolean applyWorkingCopy) {
                if (form != null && applyWorkingCopy) {
                        applyForm();
                }
                form = null;
                panelOpen = false;
                defocusFields();
                suggestor.hide();
        }

        private void applyForm() {
                if (form == null) {
                        return;
                }
                ButtonEditForm.Result result = form.save(
                                nameField.getValue(),
                                commandField.getValue(),
                                currentIconId(form.button()),
                                form.button().text(),
                                form.button().tooltip());
                if (result instanceof ButtonEditForm.Result.Saved saved) {
                        owner.upsert(saved.button());
                }
        }

        /** The icon the picker last chose, or the button's current one. */
        private String currentIconId(InventoryButton button) {
                return pickedIcon != null ? pickedIcon : button.icon();
        }

        private String pickedIcon;

        private boolean panelContains(double mouseX, double mouseY) {
                return mouseX >= panelLeft && mouseX < panelLeft + PANEL_WIDTH
                                && mouseY >= panelTop && mouseY < panelTop + PANEL_HEIGHT;
        }

        private boolean handlePanelClick(net.minecraft.client.input.MouseButtonEvent click, boolean doubled,
                        double mouseX, double mouseY) {
                if (suggestor.mouseClicked(mouseX, mouseY)) {
                        return true;
                }
                boolean inCommand = fieldHit(commandField, mouseX, mouseY);
                boolean inName = fieldHit(nameField, mouseX, mouseY);
                boolean inSearch = fieldHit(iconSearchField, mouseX, mouseY);
                commandField.setFocused(inCommand);
                nameField.setFocused(inName);
                iconSearchField.setFocused(inSearch);

                int y = (int) mouseY - panelTop;
                if (y >= ROW_STYLE_SWATCHES && y < ROW_STYLE_SWATCHES + ButtonGeometry.SIZE) {
                        int swatch = ((int) mouseX - panelLeft - PANEL_TEXT_X) / 20;
                        if (swatch >= 0 && swatch < InventoryButton.STYLE_COUNT) {
                                form.style(swatch);
                                return true;
                        }
                }
                if (y >= ROW_FILTER_TABS && y < ROW_FILTER_TABS + 18) {
                        IconPicker.Filter[] modes = IconPicker.Filter.values();
                        int tabWidth = FIELD_WIDTH / modes.length;
                        int index = ((int) mouseX - panelLeft - PANEL_TEXT_X) / tabWidth;
                        if (index >= 0 && index < modes.length) {
                                filter = modes[index];
                                refreshIcons();
                                return true;
                        }
                }
                if (y >= ROW_ICON_GRID && y < ROW_ICON_GRID + ICON_GRID_HEIGHT) {
                        int col = ((int) mouseX - panelLeft - PANEL_TEXT_X - 2) / GRID_PITCH;
                        int row = (y - ROW_ICON_GRID + iconScroll % GRID_PITCH) / GRID_PITCH;
                        int index = (iconScroll / GRID_PITCH + row) * GRID_COLUMNS + col;
                        if (col >= 0 && col < GRID_COLUMNS && index >= 0 && index < shownIcons.size()) {
                                pickedIcon = shownIcons.get(index).configId();
                                return true;
                        }
                }
                if (y >= ROW_TOGGLE_ROW && y < ROW_TOGGLE_ROW + 16) {
                        int width = (FIELD_WIDTH - 8) / 3;
                        int slot = ((int) mouseX - panelLeft - PANEL_TEXT_X) / (width + 4);
                        if (slot == 0) {
                                form.toggleCloseOnExecute();
                        } else if (slot == 1) {
                                form.toggleEnabled();
                        } else if (slot == 2) {
                                if (form.requestDelete()) {
                                        owner.delete(form.button().id());
                                        closePanel(false);
                                }
                        }
                        return true;
                }
                return true;
        }

        private boolean fieldHit(EditBox field, double mouseX, double mouseY) {
                return mouseX >= field.getX() && mouseX < field.getX() + field.getWidth()
                                && mouseY >= field.getY() && mouseY < field.getY() + field.getHeight();
        }

        @Override
        public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent click) {
                if (drag.isActive()) {
                        InventoryButton dragged = drag.end();
                        if (dragged != null) {
                                dropAt(drag.previewX(), drag.previewY());
                        }
                        return true;
                }
                return super.mouseReleased(click);
        }

        /** Converts a drag preview (screen space) into a stored position and persists it. */
        private void dropAt(int screenX, int screenY) {
                if (form == null) {
                        return;
                }
                ButtonGeometry.SlotRect target = ButtonGeometry.dropTarget(owner.buttons(), form.button(),
                                screenX - guiLeft, screenY - guiTop, canvasInputs());
                if (target == null) {
                        return;
                }
                boolean moved = owner.move(form.button().id(), target.zone().name(), target.order());
                // The working copy keeps its unsaved edits and takes the new
                // slot when the move landed; a refused drop keeps the old one.
                // Either way the panel keeps editing the same button.
                if (moved) {
                        form = new ButtonEditForm(form.button().withZone(target.zone().name(), target.order()));
                }
        }

        @Override
        public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent click, double dragX, double dragY) {
                if (drag.isActive()) {
                        drag.moveTo(click.x(), click.y());
                        ButtonGeometry.SlotRect target = ButtonGeometry.dropTarget(owner.buttons(), drag.button(),
                                        drag.previewX() - guiLeft, drag.previewY() - guiTop, canvasInputs());
                        if (target != null) {
                                drag.snapPreviewTo(target.x(), target.y());
                        }
                        return true;
                }
                return super.mouseDragged(click, dragX, dragY);
        }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
                if (panelOpen && selected() != null && suggestor.mouseScrolled(mouseX, mouseY, verticalAmount)) {
                        return true;
                }
                if (panelOpen && selected() != null && panelContains(mouseX, mouseY)) {
                        int totalRows = (shownIcons.size() + GRID_COLUMNS - 1) / GRID_COLUMNS;
                        int visibleRows = ICON_GRID_HEIGHT / GRID_PITCH;
                        int max = Math.max(0, (totalRows - visibleRows) * GRID_PITCH);
                        iconScroll = Math.clamp(iconScroll - (int) (verticalAmount * GRID_PITCH), 0, max);
                        return true;
                }
                return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        }

        @Override
        public boolean keyPressed(net.minecraft.client.input.KeyEvent input) {
                boolean fieldFocused = panelOpen && (commandField.isFocused() || nameField.isFocused()
                                || iconSearchField.isFocused());
                if (panelOpen && commandField.isFocused()) {
                        if (suggestor.keyPressed(input)) {
                                return true;
                        }
                        if (input.key() == KEY_TAB) {
                                suggestor.refresh();
                                return true;
                        }
                }
                if (fieldFocused) {
                        if (commandField.isFocused() && commandField.keyPressed(input)) {
                                return true;
                        }
                        if (nameField.isFocused() && nameField.keyPressed(input)) {
                                return true;
                        }
                        if (iconSearchField.isFocused() && iconSearchField.keyPressed(input)) {
                                refreshIcons();
                                return true;
                        }
                }
                if (input.isEscape()) {
                        if (panelOpen) {
                                closePanel(true);
                                return true;
                        }
                        onClose();
                        return true;
                }
                if (!fieldFocused && selected() != null
                                && (input.key() == GLFW_KEY_BACKSPACE || input.key() == GLFW_KEY_DELETE)) {
                        owner.delete(selected().id());
                        closePanel(false);
                        return true;
                }
                return super.keyPressed(input);
        }

        @Override
        public boolean charTyped(net.minecraft.client.input.CharacterEvent input) {
                if (panelOpen) {
                        if (commandField.isFocused() && commandField.charTyped(input)) {
                                return true;
                        }
                        if (nameField.isFocused() && nameField.charTyped(input)) {
                                return true;
                        }
                        if (iconSearchField.isFocused() && iconSearchField.charTyped(input)) {
                                refreshIcons();
                                return true;
                        }
                }
                return super.charTyped(input);
        }

        private void defocusFields() {
                commandField.setFocused(false);
                nameField.setFocused(false);
                iconSearchField.setFocused(false);
        }

        @Override
        public void onClose() {
                closePanel(true);
                if (minecraft != null) {
                        minecraft.gui.setScreen(parent);
                } else {
                        super.onClose();
                }
        }

        @Override
        public void removed() {
                // The fields were never registered as screen children; nothing to remove.
                super.removed();
        }
}
