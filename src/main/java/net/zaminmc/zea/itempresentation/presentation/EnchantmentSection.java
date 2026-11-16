package net.zaminmc.zea.itempresentation.presentation;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.ToIntFunction;

/**
 * Renders an item's enchantments as the compact grouped section the
 * RPG tooltip uses: blue names joined by grey separators, several per
 * line, wrapped at comma boundaries —
 *
 * <pre>
 * Sharpness V, Sweeping Edge III, Fire Aspect II
 * Looting III, Unbreaking III
 * </pre>
 *
 * <p>Entries arrive with their display text and resolved color (blue
 * for an ordinary enchantment, the maxed color for one at its
 * registered cap, red for a curse); this class owns only the grouping,
 * the sorting and the wrapping. Sorting is alphabetical by rendered
 * name so the output is stable frame to frame regardless of the
 * component's iteration order.
 *
 * <p>Wrapping is greedy over measured widths: entries accumulate on
 * the current line while the joined text — including the separator —
 * fits {@code maxWidth}; the line breaks before the entry that would
 * overflow, always at a comma boundary and never mid-name. A single
 * entry wider than the whole budget gets its own line rather than
 * being truncated: nothing is ever cut off, the section simply grows
 * to the width of its longest unavoidable line.
 */
public final class EnchantmentSection {
        /** The grey separator color between names on a line. */
        private static final int SEPARATOR_GRAY = 0xAAAAAA;

        /** One enchantment ready to display: its text and its color. */
        public record Entry(String display, int color) {
        }

        private EnchantmentSection() {
        }

        /**
         * The grouped enchantment lines for {@code entries}, wrapped to
         * {@code maxWidth} glyph units using {@code width} as the width
         * oracle. Returns an empty list when there is nothing to show.
         */
        public static List<Component> render(List<Entry> entries, int maxWidth, ToIntFunction<String> width) {
                List<Entry> sorted = new ArrayList<>(entries);
                sorted.sort(Comparator.comparing(Entry::display, String.CASE_INSENSITIVE_ORDER));
                List<List<Entry>> lines = wrap(sorted, maxWidth, width);
                List<Component> rendered = new ArrayList<>();
                for (List<Entry> line : lines) {
                        MutableComponent component = Component.empty();
                        for (int i = 0; i < line.size(); i++) {
                                if (i > 0) {
                                        component.append(Component.literal(", ").withColor(SEPARATOR_GRAY));
                                }
                                component.append(Component.literal(line.get(i).display())
                                                .withColor(line.get(i).color()));
                        }
                        rendered.add(component);
                }
                return rendered;
        }

        /**
         * Greedy comma-aware wrapping over the sorted entries: entries
         * accumulate while the joined text fits; the line breaks before
         * the entry that would overflow. An entry that cannot fit on
         * any line is placed alone.
         */
        private static List<List<Entry>> wrap(List<Entry> entries, int maxWidth, ToIntFunction<String> width) {
                List<List<Entry>> lines = new ArrayList<>();
                List<Entry> current = new ArrayList<>();
                for (Entry entry : entries) {
                        if (current.isEmpty()) {
                                current.add(entry);
                                continue;
                        }
                        String joined = joinedText(current, entry);
                        if (width.applyAsInt(joined) <= maxWidth) {
                                current.add(entry);
                                continue;
                        }
                        lines.add(current);
                        current = new ArrayList<>();
                        current.add(entry);
                }
                if (!current.isEmpty()) {
                        lines.add(current);
                }
                return lines;
        }

        /** The plain text of the current line with {@code next} appended, separators included. */
        private static String joinedText(List<Entry> line, Entry next) {
                StringBuilder text = new StringBuilder();
                for (Entry entry : line) {
                        if (text.length() > 0) {
                                text.append(", ");
                        }
                        text.append(entry.display());
                }
                text.append(", ").append(next.display());
                return text.toString();
        }
}
