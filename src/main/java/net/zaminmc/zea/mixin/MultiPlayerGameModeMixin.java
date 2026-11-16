package net.zaminmc.zea.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.BlockHitResult;
import net.zaminmc.zea.ZeaClient;
import net.zaminmc.zea.slots.SlotGuard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The drop key without a hovered slot: {@code dropItem} removes from the
 * currently selected hotbar slot. A locked selected slot must not be
 * dropped from, so the call is cancelled before the client-side removal
 * or the packet.
 *
 * <p>NEU needed a timestamp race guard here because 1.8.9 could drop the
 * slot the server still considered selected right after a scroll-away;
 * modern {@code dropItem} sends the carried-item sync before the drop
 * packet itself, so blocking on the locked selected slot is complete.
 * The drop key over a hovered slot is a THROW container input and is
 * handled by the container screen mixin instead.
 */
@Mixin(MultiPlayerGameMode.class)
public abstract class MultiPlayerGameModeMixin {
        @Inject(method = "dropItem", at = @At("HEAD"), cancellable = true)
        private void zea$blockLockedDrop(LocalPlayer player, boolean dropStack, CallbackInfo ci) {
                Minecraft minecraft = Minecraft.getInstance();
                if (player == null || minecraft.player != player || ZeaClient.slotLocks() == null) {
                        return;
                }
                if (SlotGuard.blocksDropKey(player.getInventory().getSelectedSlot(), ZeaClient.slotLocks())) {
                        ci.cancel();
                }
        }

        /**
         * Notes every block use for the discovery subsystem, which filters
         * it down to shop-sign interactions. Hooked at TAIL with the
         * interaction's result: a failed use (out of reach, refused) is not
         * a shop interaction, anything the server accepted is — the
         * subsystem's own gating (discovery on, EarthMC, the block being a
         * sign) applies there. The returnable is cancellable per the mixin
         * house rule (every returnable handler declares it); this handler
         * only reads the result and never cancels or edits it.
         */
        @Inject(method = "useItemOn", at = @At("TAIL"), cancellable = true)
        private void zea$noteBlockUse(LocalPlayer player, InteractionHand hand, BlockHitResult hit,
                        CallbackInfoReturnable<InteractionResult> cir) {
                if (!cir.getReturnValue().consumesAction()) {
                        return;
                }
                net.zaminmc.zea.discovery.DiscoverySubsystem discovery = ZeaClient.discovery();
                if (discovery == null) {
                        return;
                }
                discovery.onBlockUse(hit.getBlockPos());
        }
}
