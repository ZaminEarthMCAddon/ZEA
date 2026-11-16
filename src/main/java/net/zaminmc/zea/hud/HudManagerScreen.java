package net.zaminmc.zea.hud;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.zaminmc.zea.ZeaClient;

import java.util.List;

/**
 * The universal HUD manager — {@code /zeahud}. One screen for every ZEA
 * HUD element: a live preview over the dimmed game, a list of the
 * registered elements, visibility toggles, drag-to-reposition, scale and
 * reset. Elements discover themselves through {@link HudSubsystem};
 * adding a future feature never rebuilds this screen.
 *
 * <p>Interactions follow the button editor's grammar: click a list row or
 * an element to select it, drag a selected element to move it (the drop
 * converts back to anchor + edge offsets, so the position survives
 * resolution changes), the buttons act on the selection. Every change
 * saves through the subsystem's atomic write on close.
 */
public final class HudManagerScreen extends Screen {
        private static final int PANEL_FILL = 0xC0101010;
        private static final int PANEL_BORDER = 0xFF505050;
        private static final int ROW_HOVER = 0x40FFFFFF;
        private static final int ROW_SELECTED = 0x3060FF60;
        private static final int LABEL_COLOR = 0xFFE0E0E0;
        private static final int HIDDEN_LABEL = 0xFF808080;
        private static final int SELECT_BORDER = 0xFFFFFFFF;
        private static final int ROW_HEIGHT = 18;
        private static final int PANEL_WIDTH = 150;
        private static final int PANEL_MARGIN = 6;

        private final HudSubsystem hud;
        private int selected = -1;
        private boolean dragging;
        private int dragGrabX;
        private int dragGrabY;

        private Button visibilityButton;
        private Button resetButton;
        private ScaleSlider scaleSlider;

        public HudManagerScreen(HudSubsystem hud) {
                super(Component.translatable("zea.hud.manager.title"));
                this.hud = hud;
        }

        private List<HudElementHandle> handles() {
                return hud.elements();
        }

        private HudElementHandle selectedHandle() {
                return selected >= 0 && selected < handles().size() ? handles().get(selected) : null;
        }

        @Override
        protected void init() {
                int bottom = height - 30;
                visibilityButton = addRenderableWidget(Button.builder(
                                Component.translatable("zea.hud.manager.toggle_visibility"), button -> toggleVisibility())
                                .bounds(width / 2 - 190, bottom, 90, 20)
                                .build());
                resetButton = addRenderableWidget(Button.builder(
                                Component.translatable("zea.hud.manager.reset"), button -> resetSelected())
                                .bounds(width / 2 - 95, bottom, 80, 20)
                                .build());
                scaleSlider = addRenderableWidget(new ScaleSlider(width / 2 - 8, bottom, 110, 20));
                addRenderableWidget(Button.builder(
                                Component.translatable("zea.hud.manager.done"), button -> onClose())
                                .bounds(width / 2 + 107, bottom, 83, 20)
                                .build());
                refreshControls();
        }

        /** Enables or disables the selection-only controls. */
        private void refreshControls() {
                HudElementHandle handle = selectedHandle();
                boolean enabled = handle != null;
                if (visibilityButton != null) {
                        visibilityButton.active = enabled;
                }
                if (resetButton != null) {
                        resetButton.active = enabled;
                }
                if (scaleSlider != null) {
                        scaleSlider.visible = enabled;
                        if (enabled) {
                                scaleSlider.refresh(handle);
                        }
                }
        }

        private void toggleVisibility() {
                HudElementHandle handle = selectedHandle();
                if (handle == null) {
                        return;
                }
                handle.state().visible(!handle.state().visible());
                hud.save();
                refreshControls();
        }

        private void resetSelected() {
                HudElementHandle handle = selectedHandle();
                if (handle == null) {
                        return;
                }
                hud.resetToDefaults(handle);
                refreshControls();
        }

        @Override
        public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
                // Dim backdrop: the manager previews over the world, not on it.
                context.fill(0, 0, width, height, 0x88000000);

                renderElementPreview(context, mouseX, mouseY);
                renderElementList(context, mouseX, mouseY);
        }

        /** Every element at its real position — the manager's live preview. */
        private void renderElementPreview(GuiGraphicsExtractor context, int mouseX, int mouseY) {
                for (int i = 0; i < handles().size(); i++) {
                        HudElementHandle handle = handles().get(i);
                        HudLayoutMath.Rect rect = hud.rectOf(handle, width, height);
                        try {
                                handle.renderer().render(context, rect.x(), rect.y(),
                                                rect.width(), rect.height(), true);
                        } catch (Exception e) {
                                ZeaClient.LOGGER.warn("HUD preview of {} failed: {}", handle.spec().id(), e.toString());
                        }
                        if (handle.state().visible()) {
                                drawFrame(context, rect, 0xFF606060);
                        } else {
                                drawFrame(context, rect, 0xFF303030);
                        }
                        if (i == selected) {
                                drawFrame(context, HudLayoutMath.clamped(
                                                new HudLayoutMath.Rect(rect.x() - 1, rect.y() - 1,
                                                                rect.width() + 2, rect.height() + 2),
                                                width, height), SELECT_BORDER);
                                context.text(font, handle.spec().displayName(),
                                                Math.clamp(rect.x(), 0, width - font.width(handle.spec().displayName())),
                                                Math.max(0, rect.y() - 11), SELECT_BORDER, true);
                        }
                }
        }

        private static void drawFrame(GuiGraphicsExtractor context, HudLayoutMath.Rect rect, int color) {
                context.fill(rect.x(), rect.y(), rect.x() + rect.width(), rect.y() + 1, color);
                context.fill(rect.x(), rect.y() + rect.height() - 1, rect.x() + rect.width(),
                                rect.y() + rect.height(), color);
                context.fill(rect.x(), rect.y() + 1, rect.x() + 1, rect.y() + rect.height() - 1, color);
                context.fill(rect.x() + rect.width() - 1, rect.y() + 1, rect.x() + rect.width(),
                                rect.y() + rect.height() - 1, color);
        }

        /** The left panel: one row per registered element, in registration order. */
        private void renderElementList(GuiGraphicsExtractor context, int mouseX, int mouseY) {
                int rows = handles().size();
                int panelHeight = rows * ROW_HEIGHT + 8;
                int panelTop = PANEL_MARGIN;
                context.fill(PANEL_MARGIN, panelTop, PANEL_MARGIN + PANEL_WIDTH, panelTop + panelHeight, PANEL_FILL);
                net.zaminmc.zea.buttons.ButtonRenderer.drawBorder(context, PANEL_MARGIN, panelTop,
                                PANEL_WIDTH, panelHeight, PANEL_BORDER);
                for (int i = 0; i < rows; i++) {
                        HudElementHandle handle = handles().get(i);
                        int rowY = panelTop + 4 + i * ROW_HEIGHT;
                        boolean hover = mouseX >= PANEL_MARGIN && mouseX < PANEL_MARGIN + PANEL_WIDTH
                                        && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT;
                        if (i == selected) {
                                context.fill(PANEL_MARGIN + 1, rowY, PANEL_MARGIN + PANEL_WIDTH - 1,
                                                rowY + ROW_HEIGHT, ROW_SELECTED);
                        } else if (hover) {
                                context.fill(PANEL_MARGIN + 1, rowY, PANEL_MARGIN + PANEL_WIDTH - 1,
                                                rowY + ROW_HEIGHT, ROW_HOVER);
                        }
                        int color = handle.state().visible() ? LABEL_COLOR : HIDDEN_LABEL;
                        String label = (handle.state().visible() ? "● " : "○ ") + handle.spec().displayName();
                        context.text(font, Component.literal(label), PANEL_MARGIN + 8,
                                        rowY + 5, color, false);
                }
        }

        /** The element list panel's bounds — the click router's and layout's shared truth. */
        private boolean inPanel(double mouseX, double mouseY) {
                int rows = handles().size();
                return mouseX >= PANEL_MARGIN && mouseX < PANEL_MARGIN + PANEL_WIDTH
                                && mouseY >= PANEL_MARGIN
                                && mouseY < PANEL_MARGIN + 4 + rows * ROW_HEIGHT + 4;
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
                double mouseX = click.x();
                double mouseY = click.y();
                if (inPanel(mouseX, mouseY)) {
                        int rows = handles().size();
                        int index = (int) ((mouseY - PANEL_MARGIN - 4) / ROW_HEIGHT);
                        if (index >= 0 && index < rows) {
                                select(index);
                        }
                        return true;
                }
                for (int i = handles().size() - 1; i >= 0; i--) {
                        HudElementHandle handle = handles().get(i);
                        HudLayoutMath.Rect rect = hud.rectOf(handle, width, height);
                        if (mouseX >= rect.x() && mouseX < rect.x() + rect.width()
                                        && mouseY >= rect.y() && mouseY < rect.y() + rect.height()) {
                                select(i);
                                dragging = true;
                                dragGrabX = (int) mouseX - rect.x();
                                dragGrabY = (int) mouseY - rect.y();
                                return true;
                        }
                }
                select(-1);
                return super.mouseClicked(click, doubled);
        }

        private void select(int index) {
                selected = index;
                refreshControls();
        }

        @Override
        public boolean mouseDragged(MouseButtonEvent click, double dragX, double dragY) {
                if (dragging) {
                        HudElementHandle handle = selectedHandle();
                        if (handle != null) {
                                moveHandleTo(handle, (int) click.x() - dragGrabX, (int) click.y() - dragGrabY);
                        }
                        return true;
                }
                return super.mouseDragged(click, dragX, dragY);
        }

        /** Moves one element's top-left to the point, clamped, and stores the placement. */
        private void moveHandleTo(HudElementHandle handle, int x, int y) {
                HudElementState state = handle.state();
                HudLayoutMath.Rect moved = HudLayoutMath.clamped(
                                new HudLayoutMath.Rect(x, y, state.scaledWidth(handle.spec()),
                                                state.scaledHeight(handle.spec())),
                                width, height);
                HudLayoutMath.Placement placement = HudLayoutMath.placementFor(moved, width, height);
                state.anchor(placement.anchor());
                state.offsetX(placement.offsetX());
                state.offsetY(placement.offsetY());
        }

        @Override
        public boolean mouseReleased(MouseButtonEvent click) {
                if (dragging) {
                        dragging = false;
                        hud.save();
                        return true;
                }
                return super.mouseReleased(click);
        }

        @Override
        public boolean keyPressed(KeyEvent input) {
                if (input.isEscape()) {
                        onClose();
                        return true;
                }
                return super.keyPressed(input);
        }

        @Override
        public void onClose() {
                hud.save();
                super.onClose();
        }

        @Override
        public void removed() {
                hud.save();
                super.removed();
        }

        /**
         * The element scale slider: 25–400% of the spec's natural size.
         * The value updates the live state per drag step, so the preview
         * responds immediately; the write lands on release and close.
         */
        private final class ScaleSlider extends AbstractSliderButton {
                private HudElementHandle handle;

                private ScaleSlider(int x, int y, int w, int h) {
                        super(x, y, w, h, Component.empty(), 0.5);
                        refresh(selectedHandle());
                }

                private void refresh(HudElementHandle handle) {
                        this.handle = handle;
                        if (handle == null) {
                                setMessage(Component.translatable("zea.hud.manager.scale", "—"));
                                return;
                        }
                        value = (handle.state().scalePercent() - 25) / 375.0;
                        setMessage(Component.translatable("zea.hud.manager.scale",
                                        handle.state().scalePercent() + "%"));
                }

                @Override
                protected void updateMessage() {
                        if (handle != null) {
                                setMessage(Component.translatable("zea.hud.manager.scale",
                                                handle.state().scalePercent() + "%"));
                        }
                }

                @Override
                protected void applyValue() {
                        if (handle == null) {
                                return;
                        }
                        int percent = 25 + (int) Math.round(value * 375.0);
                        handle.state().scalePercent(Math.clamp(percent, 25, 400));
                }
        }
}
