package net.zaminmc.zea.search;

import java.util.List;
import java.util.Locale;

/**
 * Pure text matching for the inventory search: normalization and
 * containment, no Minecraft types, so every rule is unit-testable.
 *
 * <p>Normalization lowercases and collapses whitespace; matching is
 * case-insensitive substring containment per {@code |}-separated term
 * (NEU's OR syntax), so {@code diamond|emerald} highlights either. An
 * empty or blank query matches everything — clearing the search restores
 * the inventory's normal appearance.
 */
public final class SearchText {
	private SearchText() {
	}

	/** The searchable haystack for a stack: its text lines, normalized. */
	public static String normalize(List<String> lines) {
		if (lines == null || lines.isEmpty()) {
			return "";
		}
		StringBuilder out = new StringBuilder(64);
		for (String line : lines) {
			if (line == null || line.isBlank()) {
				continue;
			}
			if (!out.isEmpty()) {
				out.append(' ');
			}
			out.append(line.strip());
		}
		return out.toString().toLowerCase(Locale.ROOT);
	}

	/**
	 * Whether the (already normalized) haystack matches the query.
	 * Terms are split on {@code |}; the haystack matches when any term
	 * is contained. A blank query matches everything.
	 */
	public static boolean matches(String query, String haystack) {
		if (query == null || query.isBlank()) {
			return true;
		}
		String normalizedQuery = query.toLowerCase(Locale.ROOT).strip();
		if (normalizedQuery.isEmpty()) {
			return true;
		}
		if (haystack == null || haystack.isEmpty()) {
			return false;
		}
		for (String term : normalizedQuery.split("\\|")) {
			String stripped = term.strip();
			if (!stripped.isEmpty() && haystack.contains(stripped)) {
				return true;
			}
		}
		return false;
	}
}
