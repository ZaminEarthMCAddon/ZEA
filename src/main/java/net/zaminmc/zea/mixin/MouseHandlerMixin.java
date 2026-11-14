package net.zaminmc.zea.mixin;

import net.minecraft.client.MouseHandler;
import net.zaminmc.zea.misc.TooltipScroller;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Scrollable tooltips input: while an overflowing tooltip is on screen,
 * the wheel scrolls it instead of reaching the screen. The hook sits on
 * the dispatch vanilla makes into {@code Screen.mouseScrolled} — vanilla
 * has already turned the raw wheel deltas into scroll amounts and the
 * screen has not yet seen them, so canceling here consumes the event
 * completely (including the follow-up {@code afterMouseAction}).
 */
@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
        @Inject(
                        method = "onScroll",
                        at = @At(
                                        value = "INVOKE",
                                        target = "Lnet/minecraft/client/gui/screens/Screen;mouseScrolled(DDDD)Z"),
                        cancellable = true)
        private void zea$scrollableTooltips(long window, double xScroll, double yScroll, CallbackInfo ci) {
                if (TooltipScroller.scroll(yScroll, hasShift())) {
                        ci.cancel();
                }
        }

        /** Shift state at wheel time; the scroller uses it for horizontal scrolling. */
        private static boolean hasShift() {
                return net.minecraft.client.Minecraft.getInstance().hasShiftDown();
        }
}
