package net.zaminmc.zea.map.source.squaremap;

import net.zaminmc.zea.map.model.TownClaim;

import java.util.List;

/**
 * The parsed content of one world's {@code markers.json}: the town
 * territory layer plus the world-border polyline.
 *
 * @param towns       every town polygon on this world, in document order
 * @param worldBorder the "chunky" World Border polyline, or empty
 */
public record MarkersDocument(List<TownClaim> towns, List<int[]> worldBorder) {

	public MarkersDocument {
		towns = towns == null ? List.of() : List.copyOf(towns);
		worldBorder = worldBorder == null ? List.of() : List.copyOf(worldBorder);
	}

	public static final MarkersDocument EMPTY = new MarkersDocument(List.of(), List.of());
}
