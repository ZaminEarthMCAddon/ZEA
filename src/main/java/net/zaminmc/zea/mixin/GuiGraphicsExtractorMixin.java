package net.zaminmc.zea.mixin;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.zaminmc.zea.misc.TooltipScroller;
import org.joml.Vector2ic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Scrollable tooltips: vanilla resolves a tooltip's draw position through
 * one {@code ClientTooltipPositioner.positionTooltip} call inside
 * {@code GuiGraphicsExtractor.tooltip}, then draws background and lines
 * at that resolved position. Redirecting that single call hands the
 * scroller the position, the tooltip's size and the screen size in one
 * place, and the shifted position flows into the background, the text and
 * the image loop alike — so the whole tooltip moves as one and stays
 * clamped inside the screen margins.
 */
@Mixin(GuiGraphicsExtractor.class)
public abstract class GuiGraphicsExtractorMixin {
        @Redirect(
                        method = "tooltip",
                        at = @At(
                                        value = "INVOKE",
                                        target = "Lnet/minecraft/client/gui/screens/inventory/tooltip/ClientTooltipPositioner;positionTooltip(IIIIII)Lorg/joml/Vector2ic;"))
        private Vector2ic zea$scrollableTooltipPosition(ClientTooltipPositioner positioner, int guiWidth,
                        int guiHeight, int mouseX, int mouseY, int tooltipWidth, int tooltipHeight) {
                Vector2ic vanilla = positioner.positionTooltip(
                                                guiWidth, guiHeight, mouseX, mouseY, tooltipWidth, tooltipHeight);
                return TooltipScroller.adjustPosition(
                                                vanilla, guiWidth, guiHeight, tooltipWidth, tooltipHeight);
        }
}
