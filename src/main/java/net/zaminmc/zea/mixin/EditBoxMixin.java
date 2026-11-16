package net.zaminmc.zea.mixin;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.EditBox;
import net.zaminmc.zea.search.SearchBarWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Centers the inventory search bar's text: a borderless {@link EditBox}
 * places its first line at the widget's top edge ({@code textY = getY()}
 * in {@code updateTextPosition}) — invisible at the vanilla 12-pixel
 * height, but visibly top-heavy in ZEA's 22-pixel bar. After the vanilla
 * placement runs, the bar's own text is re-centered vertically.
 *
 * <p>Scoped to {@link SearchBarWidget} instances only: every other
 * EditBox — the chat search, the editor's fields — keeps vanilla
 * placement exactly.
 */
@Mixin(EditBox.class)
public abstract class EditBoxMixin {

	@Shadow
	private int textY;

	@Shadow
	private Font font;

	@Inject(method = "updateTextPosition", at = @At("TAIL"))
	private void zea$centerSearchBarText(CallbackInfo ci) {
		if (!((Object) this instanceof SearchBarWidget) || font == null) {
			return;
		}
		EditBox self = (EditBox) (Object) this;
		textY = self.getY() + (self.getHeight() - font.lineHeight + 1) / 2;
	}
}
