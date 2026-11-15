package net.zaminmc.zea.mixin;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.zaminmc.zea.rarity.RarityEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The rarity slot tint in the HUD hotbar — the dev.36 live report: the
 * wash showed inside containers but never under the hotbar items. The
 * hotbar draws each held item through one private slot pass,
 * {@code Hud.extractSlot}, for the nine hotbar slots and the off-hand
 * item alike; a head injection there draws the tint before the item, so
 * texture, count, durability bar and glint stay on top — the same
 * layering the container screens use. Gated by the rarity master switch
 * and the hotbar's own toggle, like every rarity visual.
 */
@Mixin(Hud.class)
public abstract class HudMixin {
        @Inject(method = "extractSlot", at = @At("HEAD"))
        private void zea$rarityTintHotbarSlot(GuiGraphicsExtractor graphics, int x, int y,
                                        DeltaTracker deltaTracker, Player player, ItemStack stack, int seed, CallbackInfo ci) {
                if (stack.isEmpty()) {
                        return;
                }
                int tint = RarityEngine.hotbarTintArgb(stack);
                if (tint != 0) {
                        graphics.fill(x, y, x + 16, y + 16, tint);
                }
        }
}
