package net.zaminmc.zea.map.render.overlay;

import net.zaminmc.zea.map.model.MapCamera;
import net.zaminmc.zea.map.model.PlayerMarker;
import net.zaminmc.zea.map.projection.MapProjection;

import java.util.ArrayList;
import java.util.List;

/**
 * Positions the player markers for one frame: interpolated between the
 * last two feeds, guarded against teleports, in map-frame pixels.
 *
 * <p>Interpolation anchors are the two feed snapshots the
 * {@code PlayerStore} keeps; the rendered position walks from the
 * previous snapshot towards the current one over exactly the interval
 * between their arrivals, so dots glide instead of stepping at the
 * feed's cadence. The guard is NEU's: a step larger than half the
 * view span is a teleport or a world edge, not motion — it snaps
 * instead of sweeping a streak across the map.
 */
public final class PlayerOverlay {

	/** One marker to draw: map-frame pixels for the dot, world coordinates for labels. */
	public record Marker(double x, double y, double worldX, double worldZ, String name, boolean self,
			float yawDegrees) {
	}

	/** A live local-player override, in the viewed world's coordinates. */
	public record SelfPosition(double x, double z, float yawDegrees) {
	}

	/**
	 * Plans the markers visible on one surface.
	 *
	 * @param current    the newest per-world feed
	 * @param previous   the feed before it (empty on the first publish)
	 * @param nowMs      the frame's clock
	 * @param currentAtMs when the newest feed arrived
	 * @param previousAtMs when the feed before it arrived
	 * @param selfName   the local player's name, to flag the self marker
	 * @param selfOverride the local player's live position, when the
	 *                    viewed world is the one they stand in
	 */
	public List<Marker> plan(List<PlayerMarker> current, List<PlayerMarker> previous, long nowMs,
					long currentAtMs, long previousAtMs, String selfName, SelfPosition selfOverride,
					MapProjection projection) {
		if (current.isEmpty() && selfOverride == null) {
			return List.of();
		}
		MapCamera camera = projection.camera();
		double ppb = camera.pixelsPerBlock();
		double halfSpanBlocks = Math.max(projection.surfaceWidth(), projection.surfaceHeight()) / 2.0 / ppb;
		double t = interpolationFactor(nowMs, currentAtMs, previousAtMs);
		java.util.Map<String, PlayerMarker> before = new java.util.HashMap<>(previous.size() * 2);
		for (PlayerMarker marker : previous) {
			before.put(marker.name(), marker);
		}
		List<Marker> out = new ArrayList<>(current.size() + 1);
		for (PlayerMarker marker : current) {
			double x = marker.x();
			double z = marker.z();
			PlayerMarker prior = before.get(marker.name());
			if (prior != null) {
				double dx = marker.x() - prior.x();
				double dz = marker.z() - prior.z();
				if (Math.hypot(dx, dz) <= halfSpanBlocks) {
					x = prior.x() + dx * t;
					z = prior.z() + dz * t;
				}
			}
			boolean self = selfName != null && marker.name().equals(selfName);
			if (self && selfOverride != null) {
				continue;
			}
			out.add(new Marker((x - camera.centerX()) * ppb, (z - camera.centerZ()) * ppb,
							x, z, marker.name(), self, marker.yaw()));
		}
		if (selfOverride != null) {
			out.add(new Marker((selfOverride.x() - camera.centerX()) * ppb,
							(selfOverride.z() - camera.centerZ()) * ppb,
							selfOverride.x(), selfOverride.z(), selfName, true,
							selfOverride.yawDegrees()));
		}
		return out;
	}

	/** How far between the previous and current feed the frame's clock is: 0..1. */
	private static double interpolationFactor(long nowMs, long currentAtMs, long previousAtMs) {
		long span = currentAtMs - previousAtMs;
		if (span <= 0) {
			return 1.0;
		}
		return Math.clamp((nowMs - currentAtMs) / (double) span, 0.0, 1.0);
	}
}
