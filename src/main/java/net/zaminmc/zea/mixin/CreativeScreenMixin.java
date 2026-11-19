package net.zaminmc.zea.mixin;

import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.zaminmc.zea.market.tooltip.MarketTooltipEditor;
import net.zaminmc.zea.rarity.RarityTooltipEditor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * The creative screen overrides the container tooltip source and builds
 * its own list (creative tooltips carry extra flags) without calling
 * super — so the container hook in {@link ContainerScreenMixin} never
 * fires there. The same tooltip edit runs on the creative list: the
 * creative inventory is a player inventory and shows rarity like any
 * other container screen.
 */
@Mixin(CreativeModeInventoryScreen.class)
public abstract class CreativeScreenMixin {
        // cancellable is load-bearing: the handler writes the return value
        // back, and a non-cancellable injection makes that first hover crash
        // the game (pinned by InjectionCancellableTest).
        @Inject(method = "getTooltipFromContainerItem", at = @At("TAIL"), cancellable = true)
        private void zea$rarityTooltip(ItemStack stack,
                        CallbackInfoReturnable<List<Component>> cir) {
                net.zaminmc.zea.config.ZeaConfig config = net.zaminmc.zea.ZeaClient.config();
                if (config != null && config.hideServerItemMetaEnabled()) {
                        net.zaminmc.zea.misc.ServerItemMeta.filter(cir.getReturnValue());
                }
                List<Component> lines = RarityTooltipEditor.edit(cir.getReturnValue(), stack);
                MarketTooltipEditor.edit(lines, stack);
                cir.setReturnValue(lines);
        }
}
