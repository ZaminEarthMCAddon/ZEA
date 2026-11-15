package net.zaminmc.zea.chat.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;

/**
 * The trade pacing feedback toasts. Every pacing outcome is a toast —
 * never a chat message — because the chat HUD is feed-filtered and the
 * player is often on the Trade tab when this fires; a client system line
 * would be the one thing they cannot see. Uses the vanilla system toast
 * with ZEA's own token, like {@link MultiSendToast}.
 */
public final class TradeToast {
	/** ZEA's dedicated toast token; distinct from every vanilla id. */
	private static final SystemToast.SystemToastId TOKEN = new SystemToast.SystemToastId();

	private TradeToast() {
	}

	/** A trade message was held for the auto-send drain. */
	public static void showQueued(Minecraft minecraft, String remaining, int queued) {
		SystemToast.addOrUpdate(
				minecraft.gui.toastManager(),
				TOKEN,
				title(),
				Component.translatable("zea.chat.trade.queued", remaining, queued));
	}

	/** A trade message was refused: the cooldown runs and auto-send is off. */
	public static void showBlocked(Minecraft minecraft, String remaining) {
		SystemToast.addOrUpdate(
				minecraft.gui.toastManager(),
				TOKEN,
				title(),
				Component.translatable("zea.chat.trade.blocked", remaining));
	}

	/** A trade message was refused: the queue is full. */
	public static void showFull(Minecraft minecraft, int max) {
		SystemToast.addOrUpdate(
				minecraft.gui.toastManager(),
				TOKEN,
				title(),
				Component.translatable("zea.chat.trade.full", max));
	}

	/** The auto-send drain flushed one queued message. */
	public static void showAutoSent(Minecraft minecraft, int queued) {
		SystemToast.addOrUpdate(
				minecraft.gui.toastManager(),
				TOKEN,
				title(),
				Component.translatable("zea.chat.trade.auto_sent", queued));
	}

	/** The player toggled auto-send from the status line. */
	public static void showAutoSendToggled(Minecraft minecraft, boolean enabled) {
		SystemToast.addOrUpdate(
				minecraft.gui.toastManager(),
				TOKEN,
				title(),
				Component.translatable(enabled
						? "zea.chat.trade.auto_on" : "zea.chat.trade.auto_off"));
	}

	private static Component title() {
		return Component.translatable("zea.chat.trade.title");
	}
}
