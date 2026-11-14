package net.zaminmc.zea.buttons;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * The {@code /zeabuttons} editor: a live canvas of the player inventory
 * with every button drawn exactly where its region puts it, a control
 * panel for the selected button, and the preset row.
 *
 * <p>The canvas is the list: buttons render in region order, the selected
 * one carries a gold outline, and clicking one selects it. Editing a
 * button's text fields opens {@link ButtonEditScreen}; ordering, region
 * changes, enablement and deletion are one click on the panel. Presets
 * apply as a whole and the replaced set stays recoverable through
 * <em>Restore previous</em> until the next regular edit — a preset never
 * silently destroys the player's own buttons.
 *
 * <p>Drag-to-place stays in the real inventory (edit mode there); this
 * screen is the structured overview NEU's editor was, minus its pixel
 * offsets.
 */
public final class ButtonListScreen extends Screen {
        /** The previewed container's size — the standard inventory texture. */
        private static final int CANVAS_WIDTH = 176;
        private static final int CANVAS_HEIGHT = 166;

        /** The control panel's width, right of the canvas. */
        private static final int PANEL_WIDTH = 130;

        private static final int CANVAS_BACKGROUND = 0xC0101018;
        private static final int CANVAS_BORDER = 0xFF5A5A78;
        private static final int SEARCH_BAR_FILL = 0xFF000000;
        private static final int SELECTION_COLOR = 0xFFE8B14A;
        private static final int PRESET_APPLIED_COLOR = 0xFF55FF55;

        private final InventoryButtons owner;
        @Nullable
        private UUID selected;
        private int canvasLeft;
        private int canvasTop;
        private boolean deleteArmed;

        private Button addButton;
        private Button editButton;
        private Button upButton;
        private Button downButton;
        private Button regionButton;
        private Button toggleButton;
        private Button deleteButton;
        private Button restoreButton;

        public ButtonListScreen(InventoryButtons owner) {
                super(Component.translatable("zea.buttons.list.title"));
                this.owner = owner;
        }

        @Override
        protected void init() {
                addButton = Button.builder(Component.translatable("zea.buttons.list.add"), pressed -> add()).build();
                editButton = Button.builder(Component.translatable("zea.buttons.list.edit"), pressed -> edit()).build();
                upButton = Button.builder(Component.translatable("zea.buttons.list.up"), pressed -> move(-1)).build();
                downButton = Button.builder(Component.translatable("zea.buttons.list.down"), pressed -> move(1)).build();
                regionButton = Button.builder(regionLabel(), pressed -> cycleRegion()).build();
                toggleButton = Button.builder(toggleLabel(), pressed -> toggle()).build();
                deleteButton = Button.builder(deleteLabel(), pressed -> delete()).build();
                restoreButton = Button.builder(Component.translatable("zea.buttons.list.restore"), pressed -> restore())
                                .build();

                // Preset row along the top, panel along the right side.
                int i = 0;
                for (ButtonPresets.Preset preset : ButtonPresets.all()) {
                        int x = 8 + i * 86;
                        int index = i;
                        addRenderableWidget(Button.builder(
                                        Component.translatable(preset.nameKey()),
                                        pressed -> owner.applyPreset(ButtonPresets.all().get(index)))
                                        .bounds(x, 8, 82, 18)
                                        .build());
                        i++;
                }
                addRenderableWidget(addButton);
                addRenderableWidget(editButton);
                addRenderableWidget(upButton);
                addRenderableWidget(downButton);
                addRenderableWidget(regionButton);
                addRenderableWidget(toggleButton);
                addRenderableWidget(deleteButton);
                addRenderableWidget(restoreButton);
                repositionElements();
        }

        @Override
        protected void repositionElements() {
                int centerX = width / 2;
                int centerY = height / 2;
                // Room for everything the canvas shows: top row above, the
                // search stack below, columns beside.
                canvasLeft = centerX - CANVAS_WIDTH / 2 - 40;
                canvasTop = centerY - CANVAS_HEIGHT / 2 - 20;
                int panelX = canvasLeft + CANVAS_WIDTH + 40 + 8;
                int panelY = canvasTop + 6;
                int step = 21;
                for (Button button : new Button[]{addButton, editButton, upButton, downButton,
                                regionButton, toggleButton, deleteButton, restoreButton}) {
                        button.setWidth(PANEL_WIDTH - 10);
                        button.setHeight(18);
                }
                addButton.setX(panelX);
                addButton.setY(panelY);
                editButton.setX(panelX);
                editButton.setY(panelY + step);
                upButton.setX(panelX);
                upButton.setY(panelY + step * 2);
                downButton.setX(panelX);
                downButton.setY(panelY + step * 3);
                regionButton.setX(panelX);
                regionButton.setY(panelY + step * 4);
                toggleButton.setX(panelX);
                toggleButton.setY(panelY + step * 5);
                deleteButton.setX(panelX);
                deleteButton.setY(panelY + step * 6);
                restoreButton.setX(panelX);
                restoreButton.setY(panelY + step * 7 + 6);
                refreshPanel();
        }

        /** The layout inputs the canvas previews: the inventory rect with the search bar shown. */
        private ButtonGeometry.Inputs canvasInputs() {
                return new ButtonGeometry.Inputs(
                                canvasLeft,
                                canvasTop,
                                CANVAS_WIDTH,
                                CANVAS_HEIGHT,
                                width,
                                height,
                                true,
                                false);
        }

        @Override
        public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
                super.extractRenderState(graphics, mouseX, mouseY, partialTick);

                // The previewed inventory.
                graphics.fill(canvasLeft - 1, canvasTop - 1,
                                canvasLeft + CANVAS_WIDTH + 1, canvasTop + CANVAS_HEIGHT + 1, CANVAS_BORDER);
                graphics.fill(canvasLeft, canvasTop,
                                canvasLeft + CANVAS_WIDTH, canvasTop + CANVAS_HEIGHT, CANVAS_BACKGROUND);

                // The search bar the under-search row anchors to.
                int barY = canvasTop + CANVAS_HEIGHT + ButtonGeometry.GAP;
                graphics.fill(canvasLeft, barY, canvasLeft + CANVAS_WIDTH, barY + ButtonGeometry.SEARCH_BAR_HEIGHT,
                                SEARCH_BAR_FILL);
                graphics.text(font, Component.translatable("zea.search.hint"),
                                canvasLeft + 4, barY + 2, 0xFF808080);

                // Every button at its region position.
                List<ButtonGeometry.Placement> placements = ButtonGeometry.placements(owner.buttons(), canvasInputs(), region -> true);
                for (ButtonGeometry.Placement placement : placements) {
                        InventoryButton button = placement.button();
                        boolean isSelected = selected != null && selected.equals(button.id());
                        int x = placement.x();
                        int y = placement.y();
                        int border = isSelected ? SELECTION_COLOR
                                        : button.isUsable() ? CANVAS_BORDER : 0xFF8A6A2A;
                        graphics.fill(x, y, x + ButtonGeometry.SIZE, y + ButtonGeometry.SIZE, border);
                        graphics.fill(x + 1, y + 1, x + ButtonGeometry.SIZE - 1, y + ButtonGeometry.SIZE - 1,
                                        button.enabled() ? 0xB0101014 : 0x70101014);
                        if (!button.text().isBlank()) {
                                String text = button.text();
                                float scale = Math.min(1f,
                                                (float) (ButtonGeometry.SIZE - 4) / Math.max(1, font.width(text)));
                                var pose = graphics.pose();
                                pose.pushMatrix();
                                pose.translate(x + ButtonGeometry.SIZE / 2f, y + ButtonGeometry.SIZE / 2f);
                                pose.scale(scale, scale);
                                graphics.text(font, text, -font.width(text) / 2, -font.lineHeight / 2,
                                                button.enabled() ? 0xFFFFFFFF : 0xFF9090A0, true);
                                pose.popMatrix();
                        } else {
                                graphics.item(ButtonIcons.iconFor(button.icon()), x + 1, y + 1);
                        }
                        if (!button.command().isBlank() && isSelected) {
                                graphics.text(font, "/" + button.command(),
                                                canvasLeft, canvasTop + CANVAS_HEIGHT + ButtonGeometry.GAP
                                                                + ButtonGeometry.SEARCH_BAR_HEIGHT + ButtonGeometry.GAP
                                                                + ButtonGeometry.SIZE + 4,
                                                0xFFC8C8E0, true);
                        }
                }

                if (owner.hasRestorePoint()) {
                        graphics.text(font, Component.translatable("zea.buttons.list.applied"),
                                        8, height - 16, PRESET_APPLIED_COLOR, true);
                }
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
                ButtonGeometry.Placement hit = ButtonGeometry.editableAt(
                                ButtonGeometry.placements(owner.buttons(), canvasInputs(), region -> true), event.x(), event.y());
                if (hit != null) {
                        selected = hit.button().id();
                        deleteArmed = false;
                        refreshPanel();
                        return true;
                }
                return super.mouseClicked(event, doubleClick);
        }

        @Override
        public void onClose() {
                if (minecraft == null) {
                        return;
                }
                minecraft.gui.setScreen(null);
        }

        @Override
        public boolean isPauseScreen() {
                return false;
        }

        @Override
        public boolean isInGameUi() {
                return true;
        }

        private void add() {
                if (minecraft == null) {
                        return;
                }
                InventoryButton fresh = InventoryButton.newButton(owner.buttons().size() + 1);
                selected = fresh.id();
                deleteArmed = false;
                minecraft.gui.setScreen(new ButtonEditScreen(this, fresh, owner));
        }

        private void edit() {
                InventoryButton current = selectedButton();
                if (current == null || minecraft == null) {
                        return;
                }
                deleteArmed = false;
                minecraft.gui.setScreen(new ButtonEditScreen(this, current, owner));
        }

        private void move(int delta) {
                InventoryButton current = selectedButton();
                if (current == null) {
                        return;
                }
                if (delta < 0) {
                        owner.moveEarlier(current.id());
                } else {
                        owner.moveLater(current.id());
                }
                refreshPanel();
        }

        private void cycleRegion() {
                InventoryButton current = selectedButton();
                if (current == null) {
                        return;
                }
                ButtonRegion[] regions = ButtonRegion.values();
                owner.upsert(current.withRegion(regions[(current.region().ordinal() + 1) % regions.length]));
                refreshPanel();
        }

        private void toggle() {
                InventoryButton current = selectedButton();
                if (current == null) {
                        return;
                }
                owner.upsert(current.withEnabled(!current.enabled()));
                refreshPanel();
        }

        private void delete() {
                InventoryButton current = selectedButton();
                if (current == null) {
                        return;
                }
                if (!deleteArmed) {
                        deleteArmed = true;
                        refreshPanel();
                        return;
                }
                owner.delete(current.id());
                selected = null;
                deleteArmed = false;
                refreshPanel();
        }

        private void restore() {
                owner.restorePrevious();
                refreshPanel();
        }

        @Nullable
        private InventoryButton selectedButton() {
                if (selected == null) {
                        return null;
                }
                for (InventoryButton button : owner.buttons()) {
                        if (button.id().equals(selected)) {
                                return button;
                        }
                }
                return null;
        }

        private void refreshPanel() {
                InventoryButton current = selectedButton();
                boolean has = current != null;
                editButton.active = has;
                upButton.active = has;
                downButton.active = has;
                regionButton.active = has;
                toggleButton.active = has;
                deleteButton.active = has;
                if (has) {
                        regionButton.setMessage(regionLabel());
                        toggleButton.setMessage(toggleLabel());
                } else {
                        regionButton.setMessage(Component.translatable("zea.buttons.list.region_none"));
                        toggleButton.setMessage(Component.translatable("zea.buttons.list.toggle_none"));
                }
                deleteButton.setMessage(deleteLabel());
                restoreButton.active = owner.hasRestorePoint();
        }

        private Component regionLabel() {
                InventoryButton current = selectedButton();
                if (current == null) {
                        return Component.translatable("zea.buttons.list.region_none");
                }
                return Component.translatable("zea.buttons.list.region",
                                Component.translatable("zea.buttons.region." + current.region().name().toLowerCase()));
        }

        private Component toggleLabel() {
                InventoryButton current = selectedButton();
                if (current == null) {
                        return Component.translatable("zea.buttons.list.toggle_none");
                }
                return Component.translatable("zea.buttons.list.toggle",
                                current.enabled()
                                                ? net.minecraft.network.chat.CommonComponents.OPTION_ON
                                                : net.minecraft.network.chat.CommonComponents.OPTION_OFF);
        }

        private Component deleteLabel() {
                return Component.translatable(deleteArmed
                                ? "zea.buttons.edit.delete_confirm"
                                : "zea.buttons.list.delete");
        }
}
