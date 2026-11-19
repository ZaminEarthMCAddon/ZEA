package net.zaminmc.zea.itempresentation.presentation;

import java.util.Collection;
import java.util.Locale;
import java.util.Set;

/**
 * Recognizes the enchantment-explanation lore lines servers attach to
 * their renamed RPG equipment — the gray prose lines ("Increases
 * attack damage.", "The tool loses durability slower.") that repeat,
 * in words, what the compact enchantment section and the stat block
 * already say in numbers.
 *
 * <p>Two matchers, either of which recognizes a line:
 *
 * <ol>
 *   <li><b>The live registry</b> — the line's text equals the synced
 *       {@code description} component of one of the item's held
 *       enchantments. This catches every server that derives its lore
 *       from the same enchantment definitions the client sees.</li>
 *   <li><b>A known-phrase bank</b> — the observed explanation strings
 *       themselves, matched case-insensitively with surrounding
 *       whitespace tolerated. This catches servers whose plugin
 *       hardcodes the prose instead of syncing it.</li>
 * </ol>
 *
 * <p>Both matchers compare whole trimmed lines only: a lore line that
 * merely contains one of the phrases is not an explanation and is left
 * alone. The class never decides what to do with a recognized line —
 * that is the tooltip editor's business; it only answers whether a
 * line is enchantment explanation prose.
 */
public final class EnchantmentExplanations {
        /**
         * The observed explanation strings, lowercased for
         * case-insensitive comparison. The bank is deliberately the
         * confirmed set — phrases are never invented for enchantments
         * that were not seen carrying them. The armor, Fortune and
         * spear entries arrived together with the dev.101 batch: the
         * server's prose for the protection group, the water enchants,
         * Feather Falling, Fortune (its typo "then normal" is verbatim
         * server text — the whole-line matcher compares exactly) and
         * the spear's lunge enchantment, whose prose no synced
         * registry description ever carried.
         */
        private static final Set<String> KNOWN_EXPLANATIONS = Set.of(
                        "increases attack damage.",
                        "increases the damage of sweeping attacks.",
                        "burns mobs when you attack them.",
                        "pushes mobs away when you attack them.",
                        "mobs drop more loot when killed.",
                        "the tool loses durability slower.",
                        "increases arrow damage.",
                        "arrows will burn mobs.",
                        "increases arrow knockback strength.",
                        "bows shoot basic arrows without consuming them.",
                        "allows fragile blocks such as glass to be collected.",
                        "increases mining speed.",
                        "reduces damage from most sources.",
                        "increases underwater breathing time.",
                        "increases underwater mining speed.",
                        "reduces fall damage.",
                        "increases underwater movement speed.",
                        "the user lunges forward when using the spear.",
                        "some blocks drop more items then normal.");

        private EnchantmentExplanations() {
        }

        /**
         * Whether {@code text} is an enchantment-explanation lore line
         * for an item whose held enchantments carry the given live
         * description texts.
         *
         * @param text the candidate lore line's text
         * @param heldDescriptions the description texts of the item's
         *        held enchantments, as the client's synced registry
         *        resolves them
         */
        public static boolean isExplanation(String text, Collection<String> heldDescriptions) {
                if (text == null) {
                        return false;
                }
                String trimmed = text.trim();
                if (trimmed.isEmpty()) {
                        return false;
                }
                if (KNOWN_EXPLANATIONS.contains(trimmed.toLowerCase(Locale.ROOT))) {
                        return true;
                }
                for (String description : heldDescriptions) {
                        if (description != null && description.trim().equalsIgnoreCase(trimmed)) {
                                return true;
                        }
                }
                return false;
        }
}
