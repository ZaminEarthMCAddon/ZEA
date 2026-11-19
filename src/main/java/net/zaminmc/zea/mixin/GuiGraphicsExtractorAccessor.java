package net.zaminmc.zea.mixin;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.GuiGraphicsExtractor.ScissorStack;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes the extractor's render state and scissor stack to the map's
 * globe path: the curved surface is submitted as a custom
 * {@code GuiElementRenderState} through {@code GuiRenderState.addGuiElement}
 * (the same queue every vanilla fill and blit travels), clipped by the
 * current scissor so the sphere stays inside the map plate.
 */
@Mixin(GuiGraphicsExtractor.class)
public interface GuiGraphicsExtractorAccessor {

        @Accessor("guiRenderState")
        GuiRenderState zea$guiRenderState();

        @Accessor("scissorStack")
        ScissorStack zea$scissorStack();
}
