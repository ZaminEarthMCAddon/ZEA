package net.zaminmc.zea.nav;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.zaminmc.zea.ZeaClient;
import net.zaminmc.zea.hud.HudRenderer;

/**
 * The shop-destination HUD element: the temporary waypoint, drawn as a
 * small diamond with the shop's label, its distance and its direction.
 * Subtle by design — one line at the element's anchor — and useful from
 * any distance, because the distance readout is the guidance a minimap
 * marker cannot give.
 *
 * <p>The element draws nothing without an active destination, without a
 * player, or off the destination's world — a waypoint that outlived its
 * session is not drawn, it is gone (the tracker clears it).
 */
public final class ShopDestinationHud implements HudRenderer {
	@Override
	public void render(GuiGraphicsExtractor graphics, int x, int y, int width, int height,
			boolean editorPreview) {
		ShopDestinationTracker tracker = ZeaClient.destinationTracker();
		if (tracker == null || tracker.destination() == null) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.level == null) {
			return;
		}
		ShopDestination destination = tracker.destination();
		String world = client.level.dimension().identifier().toString();
		if (!world.equals(destination.world())) {
			return;
		}
		double dx = destination.target().getX() + 0.5 - client.player.getX();
		double dz = destination.target().getZ() + 0.5 - client.player.getZ();
		int distance = (int) Math.round(Math.hypot(dx, dz));
		String bearing = bearingOf(dx, dz);
		String label = "◆ " + destination.label() + " · " + distance + "m " + bearing;
		if (editorPreview) {
			label = "◆ Shop destination";
		}
		graphics.fill(x + 2, y + height / 2 - 5, x + 4, y + height / 2 + 5, 0xFF55FFAA);
		graphics.text(client.font, label, x + 8, y + height / 2 - 4, 0xFFE0E0E0, true);
	}

	/** The eight-wind bearing of a target offset. */
	private static String bearingOf(double dx, double dz) {
		double angle = Math.toDegrees(Math.atan2(dx, -dz));
		if (angle < 0) {
			angle += 360;
		}
		String[] winds = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
		return winds[((int) ((angle + 22.5) / 45)) % 8];
	}
}
