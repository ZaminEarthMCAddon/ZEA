package net.zaminmc.zea.chat.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;

/**
 * The "history cleared" confirmation toast. Clearing is one click over
 * data that outlives the session, so the feedback says exactly what went
 * away — the visible chat and this server's saved history — instead of
 * letting an emptied screen speak for an invisible file.
 */
public final class ClearHistoryToast {
	/** ZEA's dedicated toast token; distinct from every vanilla id. */
	private static final SystemToast.SystemToastId TOKEN = new SystemToast.SystemToastId();

	private ClearHistoryToast() {
	}

	/** Shows the confirmation after the clear button empties the chat. */
	public static void show(Minecraft minecraft) {
		SystemToast.addOrUpdate(
				minecraft.gui.toastManager(),
				TOKEN,
				Component.translatable("zea.chat.clear.title"),
				Component.translatable("zea.chat.clear.body"));
	}
}
