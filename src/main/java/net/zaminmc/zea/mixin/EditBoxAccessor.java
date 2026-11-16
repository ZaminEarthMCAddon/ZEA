package net.zaminmc.zea.mixin;

import net.minecraft.client.gui.components.EditBox;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Read access to {@link EditBox}'s scroll state: {@code displayPos}
 * (where the visible substring starts in the value) and {@code textX}
 * (where the visible text starts on screen). The search bar's
 * calculator overlay places its result right after the visible text,
 * which moves while the field scrolls — vanilla exposes neither
 * number publicly.
 */
@Mixin(EditBox.class)
public interface EditBoxAccessor {
	@Accessor("displayPos")
	int zea$displayPos();

	@Accessor("textX")
	int zea$textX();
}
