package net.zaminmc.zea.chat.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;

/**
 * The PMs tab's feedback toast: a plain message typed there before any
 * private conversation exists is refused — there is nobody to reply to —
 * and the refusal explains itself here instead of sending the words to
 * the writing channel, which is never what typing on a PMs tab means.
 * A toast, not a chat line, because the chat HUD is feed-filtered and
 * the line would be the one thing the player cannot see while standing
 * on the PMs tab.
 */
public final class PrivateMessageToast {
	/** ZEA's dedicated toast token; distinct from every vanilla id. */
	private static final SystemToast.SystemToastId TOKEN = new SystemToast.SystemToastId();

	private PrivateMessageToast() {
	}

	/** A send on the PMs tab was refused: no one to reply to yet. */
	public static void showNoPartner(Minecraft minecraft) {
		SystemToast.addOrUpdate(
						minecraft.gui.toastManager(),
						TOKEN,
						Component.translatable("zea.chat.pm.no_partner.title"),
						Component.translatable("zea.chat.pm.no_partner.body"));
	}
}
