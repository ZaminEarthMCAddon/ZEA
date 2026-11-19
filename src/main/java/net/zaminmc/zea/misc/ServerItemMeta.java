package net.zaminmc.zea.misc;

import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * The server's forged-item meta block, recognized and hidden as a
 * whole. EarthMC stamps every forged piece of equipment with a four-line
 * provenance record the player cannot act on inside a tooltip:
 *
 * <pre>
 * Historical information
 * Forged by: Abasing
 * Origin: Micronesia|Palau
 * Date: 2026-11-5
 * </pre>
 *
 * <p>The block is server lore, so it rides through every tooltip pass
 * untouched and sits between the useful content and the market section.
 * The hide option removes every line of the record: the header line
 * matches by its exact text and the three field lines by their
 * {@code "Forged by: "}, {@code "Origin: "} and {@code "Date: "}
 * prefixes, all case-insensitive and whitespace-tolerant, so a renamed
 * header or a localized field can never strand the others. The name
 * line (index 0) is never touched, and lore that merely contains one of
 * the phrases — a hint with {@code "forged by:"} inside a sentence —
 * matches whole trimmed lines and prefixes exactly and stays. A piece
 * of lore that genuinely begins with one of the prefixes reads as the
 * record's field and goes with it: the prefixes are the server's own
 * provenance vocabulary, not words a crafted line otherwise opens with.
 *
 * <p>Removal leaves the surrounding blank structure tidy: a block whose
 * removal would leave two blank lines in a row collapses them to one,
 * and the collapse runs only when at least one meta line was actually
 * removed, so a tooltip without the record is untouched line for line.
 */
public final class ServerItemMeta {
        /** The block's header line, matched exactly (case-insensitive). */
        static final String HEADER = "historical information";

        /** The field-line prefixes, in the order the server writes them. */
        private static final String[] FIELD_PREFIXES = {"forged by:", "origin:", "date:"};

        private ServerItemMeta() {
        }

        /**
         * Removes the meta block from {@code lines} in place. The
         * vanilla-built list is the copy the tooltip mixin already owns;
         * the underlying stack and its lore components are never written
         * back to.
         */
        public static void filter(List<Component> lines) {
                boolean removed = false;
                for (int i = lines.size() - 1; i >= 1; i--) {
                        if (isMetaLine(lines.get(i).getString())) {
                                lines.remove(i);
                                removed = true;
                        }
                }
                if (removed) {
                        collapseDoubleBlanks(lines);
                }
        }

        /**
         * Whether {@code text} is one of the meta block's lines: the
         * header by its exact trimmed text, the fields by their prefix.
         * Package-private as the pure seam the recognizer tests pin.
         */
        static boolean isMetaLine(String text) {
                if (text == null) {
                        return false;
                }
                String trimmed = text.trim();
                if (trimmed.isEmpty()) {
                        return false;
                }
                if (HEADER.equalsIgnoreCase(trimmed)) {
                        return true;
                }
                for (String prefix : FIELD_PREFIXES) {
                        if (trimmed.regionMatches(true, 0, prefix, 0, prefix.length())) {
                                return true;
                        }
                }
                return false;
        }

        /** Collapses every run of two or more blank lines to one, below the name. */
        private static void collapseDoubleBlanks(List<Component> lines) {
                int i = 1;
                while (i < lines.size()) {
                        if (lines.get(i).getString().isEmpty()
                                        && lines.get(i - 1).getString().isEmpty()) {
                                lines.remove(i);
                        } else {
                                i++;
                        }
                }
        }
}
