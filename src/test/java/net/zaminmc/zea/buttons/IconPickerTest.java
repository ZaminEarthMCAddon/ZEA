package net.zaminmc.zea.buttons;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IconPickerTest {
	private IconPicker.Icon icon(String display, String id, boolean block, boolean custom) {
		return new IconPicker.Icon(display, id, block, custom);
	}

	private List<IconPicker.Icon> pool() {
		return List.of(
				icon("Compass", "minecraft:compass", false, false),
				icon("Diamond Sword", "minecraft:diamond_sword", false, false),
				icon("Stone", "minecraft:stone", true, false),
				icon("Oak Planks", "minecraft:oak_planks", true, false),
				icon("Settings", "custom:settings", false, true),
				icon("Search", "custom:search", false, true));
	}

	@Test
	void blankQueriesShowEverything() {
		List<IconPicker.Icon> all = IconPicker.filter(pool(), List.of(), "", IconPicker.Filter.ALL);

		assertEquals(6, all.size());
	}

	@Test
	void favoritesKeepTheirOwnOrderFirst() {
		List<String> favorites = List.of("minecraft:stone", "minecraft:compass");

		List<IconPicker.Icon> ranked = IconPicker.filter(pool(), favorites, "s", IconPicker.Filter.ALL);

		// Stone (a favorite) leads despite "Search" and "Settings" also
		// matching; the remaining hits are alphabetical.
		assertEquals("minecraft:stone", ranked.get(0).configId());
		assertTrue(ranked.stream().anyMatch(i -> i.configId().equals("custom:search")));
	}

	@Test
	void prefixMatchesRankAheadOfSubstringMatches() {
		List<IconPicker.Icon> ranked = IconPicker.filter(pool(), List.of(), "s", IconPicker.Filter.ALL);

		// "Search" and "Settings" and "Stone" start with s; "Diamond
		// Sword" only contains it.
		int sword = index(ranked, "minecraft:diamond_sword");
		int search = index(ranked, "custom:search");
		assertTrue(sword > search, "prefix hits outrank substring hits");
	}

	@Test
	void filterTabsNarrowThePool() {
		assertEquals(2, IconPicker.filter(pool(), List.of(), "", IconPicker.Filter.BLOCKS).size());
		assertEquals(2, IconPicker.filter(pool(), List.of(), "", IconPicker.Filter.ITEMS).size());
		assertEquals(2, IconPicker.filter(pool(), List.of(), "", IconPicker.Filter.CUSTOM).size());
		assertEquals(0, IconPicker.filter(pool(), List.of(), "", IconPicker.Filter.CUSTOM)
				.stream().filter(i -> !i.custom()).count());
	}

	@Test
	void matchingCoversDisplayNamesAndConfigIds() {
		assertTrue(index(IconPicker.filter(pool(), List.of(), "compass", IconPicker.Filter.ALL),
				"minecraft:compass") >= 0);
		assertTrue(index(IconPicker.filter(pool(), List.of(), "diamond sword", IconPicker.Filter.ALL),
				"minecraft:diamond_sword") >= 0);
		assertEquals(0, IconPicker.filter(pool(), List.of(), "nothing matches this", IconPicker.Filter.ALL).size());
	}

	@Test
	void theQueryIsCaseAndWhitespaceInsensitive() {
		assertEquals(1, IconPicker.filter(pool(), List.of(), "  COMPASS ", IconPicker.Filter.ALL).size());
	}

	private int index(List<IconPicker.Icon> icons, String configId) {
		for (int i = 0; i < icons.size(); i++) {
			if (icons.get(i).configId().equals(configId)) {
				return i;
			}
		}
		return -1;
	}

	@Test
	void localeRootIsUsedForCaseFolding() {
		// Guard against a Turkish-I regression: display names fold with
		// Locale.ROOT so "compass".toUpperCase() in tr would not matter.
		IconPicker.Icon turkish = icon("İstanbul", "custom:istanbul", false, true);
		List<IconPicker.Icon> one = IconPicker.filter(List.of(turkish), List.of(), "istanbul",
				IconPicker.Filter.ALL);
		// The dotted capital I does not fold to a plain i under ROOT, so
		// the id path is the one that matches.
		assertEquals(1, one.size());
	}
}
