package net.zaminmc.zea.itempresentation.presentation;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The enchantment-explanation recognizer, pinned purely: the observed
 * phrase bank matches case-insensitively with whitespace tolerated, a
 * held enchantment's live description text matches, and nothing else
 * does — flavor lore, usage hints and lines that merely contain a
 * phrase are not explanations.
 */
class EnchantmentExplanationsTest {
        /** Every observed explanation phrase is recognized. */
        @Test
        void knownPhrasesMatch() {
                for (String phrase : new String[] {
                                "Increases attack damage.",
                                "Increases the damage of sweeping attacks.",
                                "Burns mobs when you attack them.",
                                "Pushes mobs away when you attack them.",
                                "Mobs drop more loot when killed.",
                                "The tool loses durability slower.",
                                "Increases arrow damage.",
                                "Arrows will burn mobs.",
                                "Increases arrow knockback strength.",
                                "Bows shoot basic arrows without consuming them."}) {
                        assertTrue(EnchantmentExplanations.isExplanation(phrase, List.of()),
                                        phrase + " is a known explanation");
                }
        }

        /** The bank matches case-insensitively and tolerates padding. */
        @Test
        void matchingToleratesCaseAndPadding() {
                assertTrue(EnchantmentExplanations.isExplanation("  increases ATTACK DAMAGE. ", List.of()));
                assertTrue(EnchantmentExplanations.isExplanation("\tThe tool loses durability slower.\n", List.of()));
        }

        /** A held enchantment's live description text is an explanation. */
        @Test
        void heldEnchantmentDescriptionMatches() {
                assertTrue(EnchantmentExplanations.isExplanation(
                                "Custom server description of Sharpness.",
                                List.of("Custom server description of Sharpness.")),
                                "the synced registry description is the explanation");
                assertTrue(EnchantmentExplanations.isExplanation(
                                "  sharpness text  ", List.of("Sharpness Text")),
                                "the description match is trimmed and case-insensitive");
        }

        /** Flavor lore, usage hints and empty lines are not explanations. */
        @Test
        void otherLoreIsNotAnExplanation() {
                assertFalse(EnchantmentExplanations.isExplanation("Right-click to activate", List.of()),
                                "a usage hint is server flavor, not enchantment prose");
                assertFalse(EnchantmentExplanations.isExplanation("A gift from the mayor", List.of()),
                                "flavor lore stays");
                assertFalse(EnchantmentExplanations.isExplanation("", List.of()),
                                "blank lines never match");
                assertFalse(EnchantmentExplanations.isExplanation(null, List.of()),
                                "null never matches");
        }

        /** A line that merely contains a phrase is not a whole-line explanation. */
        @Test
        void containingThePhraseIsNotMatching() {
                assertFalse(EnchantmentExplanations.isExplanation(
                                "Mobs drop more loot when killed, and heal you", List.of()),
                                "only whole trimmed lines match");
                assertFalse(EnchantmentExplanations.isExplanation(
                                "This sword increases attack damage. Also everything else.", List.of()),
                                "a phrase inside other prose is not an explanation line");
        }

        /**
         * The two mining-tool prose lines the server attaches beside
         * Efficiency and Silk Touch are explanations too: whole-line
         * matches against the confirmed bank, never substring filters,
         * so unrelated lore survives and the structured stat block and
         * enchantment section replace them.
         */
        @Test
        void theMiningToolProseLinesAreExplanations() {
                assertTrue(EnchantmentExplanations.isExplanation(
                                "Allows fragile blocks such as glass to be collected.", List.of()),
                                "the silk-touch prose is a known explanation");
                assertTrue(EnchantmentExplanations.isExplanation(
                                "Increases mining speed.", List.of()),
                                "the efficiency prose is a known explanation");
                assertTrue(EnchantmentExplanations.isExplanation(
                                "  increases mining speed.  ", List.of()),
                                "the match tolerates surrounding whitespace");
                assertFalse(EnchantmentExplanations.isExplanation(
                                "Increases mining speed on soul sand", List.of()),
                                "a line that merely extends the phrase is not an explanation");
        }
}

