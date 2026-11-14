package net.zaminmc.zea.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.zaminmc.zea.duck.RecipeBookAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * Exposes the recipe book component of recipe-book container screens so
 * the inventory-button layout can tell whether the book panel is open —
 * the strip left of the GUI belongs to the book while it is, and the
 * left button region yields instead of fighting it (NEU's recipe-era
 * overlap lesson, applied to the modern recipe book that did not exist
 * in 1.8.9).
 */
@Mixin(AbstractRecipeBookScreen.class)
public abstract class RecipeBookScreenMixin implements RecipeBookAccess {
        @Shadow
        private RecipeBookComponent<?> recipeBookComponent;

        @Override
        public boolean zea$recipeBookOpen() {
                return recipeBookComponent != null && recipeBookComponent.isVisible();
        }
}
