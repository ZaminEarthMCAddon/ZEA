package net.zaminmc.zea.buttons;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.layouts.FrameLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * The button edit form: name, command, icon, display text, region, order,
 * close-on-execute and enabled state for one button. Done validates
 * before saving — a button without a command, or without an icon and
 * without display text, is refused with an inline error — and Delete
 * asks for a second click before removing anything. Unknown but
 * well-formed icon ids are accepted; the game shows a barrier icon and a
 * tooltip warning until they are fixed.
 *
 * <p>Nothing here executes commands; the screen only edits data and
 * hands it back to {@link InventoryButtons#upsert}.
 */
public final class ButtonEditScreen extends Screen {
        private static final int FIELD_WIDTH = 220;

        private final Screen parent;
        private final InventoryButtons owner;
        private final LinearLayout layout = LinearLayout.vertical().spacing(6);

        private final ButtonEditForm form;
        private EditBox nameField;
        private EditBox commandField;
        private EditBox iconField;
        private EditBox textField;
        private EditBox tooltipField;
        @Nullable
        private Component error;
        @Nullable
        private Button deleteButton;
        @Nullable
        private Button orderField;
        @Nullable
        private Button closeField;
        @Nullable
        private Button enabledField;

        public ButtonEditScreen(Screen parent, InventoryButton button, InventoryButtons owner) {
                super(Component.translatable("zea.buttons.edit.title"));
                this.parent = parent;
                this.owner = owner;
                this.form = new ButtonEditForm(button);
        }

        @Override
        protected void init() {
                layout.defaultCellSetting().alignHorizontallyLeft();

                nameField = field("zea.buttons.edit.name", form.button().name(), InventoryButton.MAX_NAME_LENGTH);
                commandField = field("zea.buttons.edit.command", form.button().command(), InventoryButton.MAX_COMMAND_LENGTH);
                iconField = field("zea.buttons.edit.icon", form.button().icon(), InventoryButton.MAX_ICON_LENGTH);
                textField = field("zea.buttons.edit.text", form.button().text(), InventoryButton.MAX_TEXT_LENGTH);
                tooltipField = field("zea.buttons.edit.tooltip", form.button().tooltip(), InventoryButton.MAX_TOOLTIP_LENGTH);

                layout.addChild(label("zea.buttons.edit.name"));
                layout.addChild(nameField);
                layout.addChild(label("zea.buttons.edit.command"));
                layout.addChild(commandField);
                layout.addChild(label("zea.buttons.edit.icon"));
                layout.addChild(iconField);
                layout.addChild(iconPicker());
                layout.addChild(label("zea.buttons.edit.text"));
                layout.addChild(textField);
                layout.addChild(label("zea.buttons.edit.tooltip"));
                layout.addChild(tooltipField);

                CycleButton<ButtonRegion> regionField = CycleButton
                                .<ButtonRegion>builder(ButtonEditScreen::regionLabel, form.button().region())
                                .withValues(ButtonRegion.values())
                                .create(Component.translatable("zea.buttons.edit.region"),
                                                (cycle, value) -> form.region(value));
                layout.addChild(regionField);

                orderField = Button.builder(orderLabel(), pressed -> cycleOrder()).build();
                layout.addChild(orderField);

                closeField = Button.builder(closeLabel(), pressed -> toggleClose()).build();
                layout.addChild(closeField);

                enabledField = Button.builder(CommonComponents.OPTION_ON, pressed -> toggleEnabled()).build();
                enabledField.setMessage(CommonComponents.optionStatus(form.button().enabled()));
                layout.addChild(enabledField);

                LinearLayout actions = LinearLayout.horizontal().spacing(4);
                actions.addChild(Button.builder(Component.translatable("zea.buttons.edit.done"), pressed -> save()).build());
                deleteButton = Button.builder(deleteLabel(), pressed -> delete()).build();
                actions.addChild(deleteButton);
                layout.addChild(actions);

                layout.visitWidgets(this::addRenderableWidget);
                repositionElements();
        }

        @Override
        protected void repositionElements() {
                layout.arrangeElements();
                FrameLayout.centerInRectangle(layout, getRectangle());
        }

        @Override
        public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
                super.extractRenderState(graphics, mouseX, mouseY, partialTick);
                if (error != null) {
                        graphics.centeredText(font, error, width / 2, height - 30, 0xFFFF5555);
                }
        }

        @Override
        public void onClose() {
                zea$returnToParent();
        }

        @Override
        public boolean isPauseScreen() {
                return false;
        }

        @Override
        public boolean isInGameUi() {
                return true;
        }

        /** Applies the form, or shows why it cannot be applied yet. */
        private void save() {
                var result = form.save(nameField.getValue(), commandField.getValue(), iconField.getValue(),
                                textField.getValue(), tooltipField.getValue());
                if (result instanceof ButtonEditForm.Result.Refused refused) {
                        error = Component.translatable("zea.buttons.edit.error." + refused.reason().key());
                        return;
                }
                if (result instanceof ButtonEditForm.Result.Saved saved) {
                        owner.upsert(saved.button());
                        zea$returnToParent();
                }
        }

        private void delete() {
                if (!form.requestDelete()) {
                        if (deleteButton != null) {
                                deleteButton.setMessage(deleteLabel());
                        }
                        return;
                }
                owner.delete(form.button().id());
                zea$returnToParent();
        }

        private void cycleOrder() {
                if (!form.orderLater()) {
                        form.orderFirst();
                }
                refreshLabels();
        }

        private void toggleClose() {
                form.toggleCloseOnExecute();
                refreshLabels();
        }

        private void toggleEnabled() {
                form.toggleEnabled();
                refreshLabels();
        }

        private void refreshLabels() {
                if (orderField != null) {
                        orderField.setMessage(orderLabel());
                }
                if (closeField != null) {
                        closeField.setMessage(closeLabel());
                }
                if (enabledField != null) {
                        enabledField.setMessage(CommonComponents.optionStatus(form.button().enabled()));
                }
        }

        private void zea$returnToParent() {
                if (minecraft == null) {
                        return;
                }
                if (parent != null) {
                        minecraft.gui.setScreen(parent);
                } else {
                        minecraft.gui.setScreen(null);
                }
        }

        private EditBox field(String hintKey, String value, int maxLength) {
                EditBox field = new EditBox(font, 0, 0, FIELD_WIDTH, 16, Component.translatable(hintKey));
                field.setMaxLength(maxLength);
                field.setValue(value);
                field.setHint(Component.translatable(hintKey + ".hint"));
                return field;
        }

        /**
         * The icon quick-picker: one button per common icon, drawn with the
         * item itself as the label. Clicking one fills the icon field — no
         * resource-file editing, no item-id memorization; anything else
         * still goes through the field.
         */
        private LinearLayout iconPicker() {
                LinearLayout picker = LinearLayout.horizontal().spacing(2);
                for (String icon : ButtonIcons.QUICK_PICK) {
                        picker.addChild(Button.builder(
                                        Component.literal(icon.substring(icon.indexOf(':') + 1)),
                                        pressed -> iconField.setValue(icon))
                                        .size(62, 16)
                                        .build());
                }
                return picker;
        }

        private StringWidget label(String key) {
                return new StringWidget(Component.translatable(key), font);
        }

        private Component deleteLabel() {
                return Component.translatable(form.deleteArmed()
                                ? "zea.buttons.edit.delete_confirm"
                                : "zea.buttons.edit.delete");
        }

        private Component orderLabel() {
                return Component.translatable("zea.buttons.edit.order", form.button().order() + 1);
        }

        private Component closeLabel() {
                return Component.translatable("zea.buttons.edit.close_on_execute",
                                Component.literal(form.button().closeOnExecute()
                                                ? CommonComponents.OPTION_ON.getString()
                                                : CommonComponents.OPTION_OFF.getString()));
        }

        private static Component regionLabel(ButtonRegion region) {
                return Component.translatable("zea.buttons.region." + region.name().toLowerCase());
        }
}
