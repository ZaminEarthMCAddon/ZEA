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
 *
 * <p>The market observes {@code useItemOn} because right-clicking a
 * block is what triggers EarthMC's shop response: recording the used
 * block is what lets the market correlate that response with the
 * physical sign it came from, including its position and its attached
 * chest. Purely observational — the interaction is never modified,
 * cancelled or reordered.
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

        // cancellable=true is load-bearing for the injection hygiene test
        // (a Returnable handler must declare it); the handler never
        // cancels — the market only observes.
        @Inject(method = "useItemOn", at = @At("HEAD"), cancellable = true)
        private void zea$observeSignUse(LocalPlayer player, InteractionHand hand, BlockHitResult hit,
                        CallbackInfoReturnable<InteractionResult> cir) {
                Minecraft minecraft = Minecraft.getInstance();
                if (player == null || minecraft.player != player || minecraft.level == null
                                || ZeaClient.market() == null) {
                        return;
                }
                ZeaClient.market().onBlockUse(minecraft.level, hit);
        }
}
