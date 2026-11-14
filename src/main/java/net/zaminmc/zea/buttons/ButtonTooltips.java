package net.zaminmc.zea.buttons;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.ArrayList;
import java.util.List;

/**
 * The tooltip lines shown when hovering a button. Pure text assembly:
 * icon resolution is the caller's concern ({@link ButtonIcons}), passed
 * in as a flag so this class stays testable without registries.
 */
public final class ButtonTooltips {
        private ButtonTooltips() {
        }

        /**
         * Lines for a hovered button: its name, the command it would run,
         * its placement and close behavior, and warnings when the icon does
         * not resolve or the button is currently hidden.
         *
         * @param iconKnown     whether {@link ButtonIcons} resolved the icon id
         * @param visibleOnScreen whether the layout currently renders this button
         */
        public static List<Component> lines(InventoryButton button, boolean iconKnown, boolean visibleOnScreen) {
                List<Component> lines = new ArrayList<>(6);
                lines.add(Component.literal(button.name()));
                if (!button.tooltip().isBlank()) {
                        MutableComponent description = Component.literal(button.tooltip());
                        lines.add(description.withStyle(ChatFormatting.DARK_GRAY));
                }
                if (button.command().isBlank()) {
                        MutableComponent unconfigured = Component.translatable("zea.buttons.unconfigured");
                        lines.add(unconfigured.withStyle(ChatFormatting.GRAY));
                } else {
                    MutableComponent command = Component.literal("/" + button.command());
                    lines.add(command.withStyle(ChatFormatting.GRAY));
                }
                if (!iconKnown && button.text().isBlank()) {
                        MutableComponent unknown = Component.translatable("zea.buttons.unknown_icon", button.icon());
                        lines.add(unknown.withStyle(ChatFormatting.RED));
                }
                if (!visibleOnScreen) {
                        MutableComponent hidden = Component.translatable("zea.buttons.hidden");
                        lines.add(hidden.withStyle(ChatFormatting.RED));
                }
                if (button.closeOnExecute()) {
                        MutableComponent closes = Component.translatable("zea.buttons.close_on_execute");
                        lines.add(closes.withStyle(ChatFormatting.DARK_GRAY));
                }
                return lines;
        }
}
