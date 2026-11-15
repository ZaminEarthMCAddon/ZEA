package net.zaminmc.zea.buttons;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The icon picker's search model, pure so the ranking rules run in unit
 * tests without a client. The screen feeds the full icon list (registry
 * items plus ZEA's custom textures) and the quick-pick favorites; this
 * class decides what a query shows and in what order:
 *
 * <ul>
 * <li>matching favorites first, in their own fixed order — the quick
 * picks are a curated row, not an alphabet slice;
 * <li>then everything else whose name or id contains the query,
 * prefix matches before substring matches, ties alphabetical;
 * <li>the filter tabs narrow the pool first: items, blocks or the
 * custom glyphs only.
 * </ul>
 */
public final class IconPicker {
	/** Which pool of icons the picker shows. */
	public enum Filter {
		ALL, ITEMS, BLOCKS, CUSTOM
	}

	/**
	 * One choosable icon.
	 *
	 * @param displayName what the tooltip shows
	 * @param configId    what the button stores
	 * @param block       whether the icon is a block item
	 * @param custom      whether the icon is one of ZEA's custom glyphs
	 */
	public record Icon(String displayName, String configId, boolean block, boolean custom) {
	}

	private IconPicker() {
	}

	/**
	 * Filters and ranks the icon list for a query.
	 *
	 * @param icons     every choosable icon
	 * @param favorites the quick-pick ids, in their display order
	 * @param query     the search text; blank shows everything (ranked)
	 * @param mode      the filter tab
	 */
	public static List<Icon> filter(List<Icon> icons, List<String> favorites, String query, Filter mode) {
		String needle = query == null ? "" : query.toLowerCase(Locale.ROOT).trim();
		List<Icon> pool = new ArrayList<>(icons == null ? List.of() : icons);
		pool.removeIf(icon -> !inPool(icon, mode));

		List<Icon> favoriteHits = new ArrayList<>();
		List<Icon> prefixHits = new ArrayList<>();
		List<Icon> substringHits = new ArrayList<>();
		for (Icon icon : pool) {
			if (!needle.isEmpty() && !matches(icon, needle)) {
				continue;
			}
			int favoriteRank = favorites == null ? -1 : favorites.indexOf(icon.configId());
			if (favoriteRank >= 0) {
				favoriteHits.add(icon);
			} else if (!needle.isEmpty() && startsWith(icon, needle)) {
				prefixHits.add(icon);
			} else {
				substringHits.add(icon);
			}
		}

		favoriteHits.sort((a, b) -> Integer.compare(
				favorites.indexOf(a.configId()), favorites.indexOf(b.configId())));
		prefixHits.sort(IconPicker::compareNames);
		substringHits.sort(IconPicker::compareNames);

		List<Icon> out = new ArrayList<>(favoriteHits.size() + prefixHits.size() + substringHits.size());
		out.addAll(favoriteHits);
		out.addAll(prefixHits);
		out.addAll(substringHits);
		return out;
	}

	private static boolean inPool(Icon icon, Filter mode) {
		return switch (mode) {
			case ALL -> true;
			case ITEMS -> !icon.custom() && !icon.block();
			case BLOCKS -> icon.block();
			case CUSTOM -> icon.custom();
		};
	}

	private static boolean matches(Icon icon, String needle) {
		return icon.displayName().toLowerCase(Locale.ROOT).contains(needle)
				|| icon.configId().toLowerCase(Locale.ROOT).contains(needle);
	}

	private static boolean startsWith(Icon icon, String needle) {
		return icon.displayName().toLowerCase(Locale.ROOT).startsWith(needle)
				|| icon.configId().toLowerCase(Locale.ROOT).startsWith(needle);
	}

	private static int compareNames(Icon a, Icon b) {
		int byName = a.displayName().toLowerCase(Locale.ROOT)
				.compareTo(b.displayName().toLowerCase(Locale.ROOT));
		return byName != 0 ? byName : a.configId().compareTo(b.configId());
	}
}
