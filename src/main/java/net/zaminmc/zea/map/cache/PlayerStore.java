package net.zaminmc.zea.map.cache;

import net.zaminmc.zea.map.model.MapWorld;
import net.zaminmc.zea.map.model.PlayerMarker;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The live player markers, split per world and memoised.
 *
 * <p>The feed publishes every online player in one document with a
 * {@code world} field per entry; both worlds are therefore held
 * simultaneously and a world switch needs no clearing — each world's
 * list is simply correct ({@code 7ea2bb8d}). A marker with a blank
 * world field (the legacy squaremap shape) belongs to whichever world
 * asks, matching {@link PlayerMarker#inWorld}.
 *
 * <p>Lists are rebuilt only when the feed's content actually changes:
 * an identical document (the common case at a 1 s cadence with a quiet
 * server) keeps every instance, so render-side identity comparisons and
 * interpolation anchors stay stable and nothing reallocates per poll.
 *
 * <p>Interpolation itself is a rendering concern (NEU's guards: only
 * under a jump bound, wrap-aware); the store merely keeps the previous
 * snapshot and its timestamp so the renderer can anchor to it.
 */
public final class PlayerStore {

	private final Map<MapWorld, List<PlayerMarker>> current = new ConcurrentHashMap<>();
	private final Map<MapWorld, List<PlayerMarker>> previous = new ConcurrentHashMap<>();
	private long publishedAtMs;
	private long previousAtMs;
	private int contentStamp;

	public synchronized void publish(List<PlayerMarker> feed, long nowMs) {
		Objects.requireNonNull(feed, "feed");
		int stamp = contentStamp(feed);
		if (stamp == contentStamp) {
			return;
		}
		Map<MapWorld, List<PlayerMarker>> next = splitByWorld(feed);
		previous.clear();
		previous.putAll(current);
		current.clear();
		current.putAll(next);
		previousAtMs = publishedAtMs;
		publishedAtMs = nowMs;
		contentStamp = stamp;
	}

	/** The markers of one world; empty before the first successful poll. */
	public List<PlayerMarker> markers(MapWorld world) {
		return current.getOrDefault(world, List.of());
	}

	/**
	 * The previous snapshot of one world, for interpolation anchors.
	 * Empty on the first poll or after a content change with no prior
	 * data.
	 */
	public List<PlayerMarker> previous(MapWorld world) {
		return previous.getOrDefault(world, List.of());
	}

	/** When the current snapshots landed; 0 before the first poll. */
	public synchronized long publishedAtMs() {
		return publishedAtMs;
	}

	/** When the previous snapshots landed; 0 when there was none. */
	public synchronized long previousAtMs() {
		return previousAtMs;
	}

	/** True once any poll has landed. */
	public synchronized boolean hasData() {
		return publishedAtMs > 0;
	}

	/** The marker of a named player on a world, for the self marker. */
	public PlayerMarker marker(MapWorld world, String name) {
		if (name == null || name.isBlank()) {
			return null;
		}
		for (PlayerMarker marker : markers(world)) {
			if (marker.name().equalsIgnoreCase(name)) {
				return marker;
			}
		}
		return null;
	}

	private static Map<MapWorld, List<PlayerMarker>> splitByWorld(List<PlayerMarker> feed) {
		Map<MapWorld, List<PlayerMarker>> out = new ConcurrentHashMap<>();
		for (MapWorld world : MapWorld.values()) {
			List<PlayerMarker> ofWorld = new ArrayList<>();
			for (PlayerMarker marker : feed) {
				if (marker.inWorld(world)) {
					ofWorld.add(marker);
				}
			}
			out.put(world, List.copyOf(ofWorld));
		}
		return out;
	}

	/**
	 * A content stamp: changes when any name, world or position changes,
	 * in any order. Deliberately not a general-purpose hash — identical
	 * inputs must produce identical stamps on every call and JVM, which
	 * an identity-hashed object mix would not guarantee.
	 */
	private static int contentStamp(List<PlayerMarker> feed) {
		int stamp = 7;
		for (PlayerMarker marker : feed) {
			stamp = 31 * stamp + marker.name().hashCode();
			stamp = 31 * stamp + Double.hashCode(marker.x());
			stamp = 31 * stamp + Double.hashCode(marker.z());
			stamp = 31 * stamp + Float.hashCode(marker.yaw());
			stamp = 31 * stamp + Objects.hashCode(marker.world());
		}
		return stamp;
	}
}
