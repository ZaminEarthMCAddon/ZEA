package net.zaminmc.zea.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.zaminmc.zea.ZeaClient;
import net.zaminmc.zea.chat.ChatSubsystem;
import net.zaminmc.zea.chat.feeds.TradeGate;
import net.zaminmc.zea.chat.render.TradeToast;
import net.zaminmc.zea.config.ZeaConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Tracks every outgoing trade send through the one choke point they all
 * share — the player's own {@code /tradechat} (typed or recalled), ZEA's
 * channel routing and the auto-send drain all reach vanilla's
 * {@code ClientPacketListener.sendCommand}. While no cooldown is known
 * the send goes straight out and is remembered as unresolved, because
 * the server — never a client-side guess — decides whether it counted
 * (the dev.36 live request: no forced five-minute window). Its answer
 * arrives as the classified wait line, which syncs the gate's window
 * and moves the rejected message into the queue; an accepted echo
 * resolves the send. While a server-reported window runs, a send is
 * held for the drain (auto-send on, up to
 * {@link TradeGate#MAX_QUEUED}) or refused with a toast explaining the
 * wait; a bare {@code /tradechat} with no message is a channel switch,
 * not a send, and passes untouched. Active only while connected to
 * EarthMC — elsewhere the vanilla send path is not modified.
 */
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
        /**
         * Deliberately no enum switch anywhere in this class: javac compiles a
         * switch over an enum into a synthetic nested class holding the switch
         * map, that class lives in the mixin package, and Mixin refuses to
         * load unregistered classes from it (the hygiene test pins this).
         */
        @Inject(method = "sendCommand", at = @At("HEAD"), cancellable = true)
        private void zea$paceTradeChat(String command, CallbackInfo ci) {
                if (!TradeGate.isTradeMessage(command)) {
                        return;
                }
                ChatSubsystem chat = ZeaClient.chat();
                ZeaConfig config = ZeaClient.config();
                if (chat == null || config == null || !ZeaClient.lifecycle().onEarthMc()
                                || !config.tradePacingEnabled()) {
                        return;
                }
                TradeGate gate = chat.tradeGate();
                long now = System.currentTimeMillis();
                TradeGate.Outcome outcome = gate.offer(command, now, config.tradeAutoSend());
                if (outcome == TradeGate.Outcome.SEND_NOW) {
                        // The gate remembers the send; the server's answer —
                        // echo or wait line — resolves it.
                        return;
                }
                ci.cancel();
                Minecraft minecraft = Minecraft.getInstance();
                if (minecraft.gui == null) {
                        return;
                }
                if (outcome == TradeGate.Outcome.QUEUED) {
                        TradeToast.showQueued(minecraft,
                                        TradeGate.formatRemaining(gate.remainingMillis(now)), gate.queuedCount());
                } else if (outcome == TradeGate.Outcome.BLOCKED) {
                        TradeToast.showBlocked(minecraft,
                                        TradeGate.formatRemaining(gate.remainingMillis(now)));
                } else if (outcome == TradeGate.Outcome.FULL) {
                        TradeToast.showFull(minecraft, TradeGate.MAX_QUEUED);
                }
        }
}
