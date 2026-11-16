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
 * Enforces the trade channel's pacing on every outgoing trade send.
 * EarthMC allows one trade message per player every five minutes; a
 * second message inside the window is silently rejected by the server.
 * This is the one choke point every send passes through — the player's
 * own {@code /tradechat} (typed or recalled), ZEA's channel routing and
 * the auto-send drain all reach vanilla's
 * {@code ClientPacketListener.sendCommand} — so the gate is decided
 * here and the window restarts on exactly the sends that really went
 * out.
 *
 * <p>A held message is either queued for the drain (auto-send on, up to
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
                        gate.onSent(now);
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

        /**
         * Notes every executed command for the discovery subsystem's
         * navigation history — the single point all command origins flow
         * through, so a spawn command issued by a ZEA chat link, typed by
         * hand or sent by any other feature is recorded exactly once. The
         * subsystem filters to the relevant commands and does its own
         * gating (discovery on, EarthMC).
         */
        @Inject(method = "sendCommand", at = @At("HEAD"))
        private void zea$noteNavigationCommand(String command, CallbackInfo ci) {
                net.zaminmc.zea.discovery.DiscoverySubsystem discovery = ZeaClient.discovery();
                if (discovery == null) {
                        return;
                }
                discovery.onCommandIssued(command);
        }
}
