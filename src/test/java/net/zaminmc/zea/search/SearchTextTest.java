package net.zaminmc.zea.search;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SearchTextTest {
        private static final String HAYSTACK = SearchText.normalize(List.of(
                        "Diamond Sword",
                        "Sharpness V",
                        "A gift from Steve"));

        @Test
        void normalizationLowercasesAndJoinsLines() {
                assertEquals("diamond sword sharpness v a gift from steve", HAYSTACK);
                assertEquals("", SearchText.normalize(List.of()));
                assertEquals("", SearchText.normalize(null));
                assertEquals("one", SearchText.normalize(java.util.Arrays.asList("  one  ", null, "")));
        }

        @Test
        void matchingIsCaseInsensitiveSubstring() {
                assertTrue(SearchText.matches("diamond", HAYSTACK));
                assertTrue(SearchText.matches("DIAMOND", HAYSTACK));
                assertTrue(SearchText.matches("sharpness v", HAYSTACK));
                assertTrue(SearchText.matches("gift from", HAYSTACK));
                assertFalse(SearchText.matches("emerald", HAYSTACK));
        }

        @Test
        void blankQueriesMatchEverything() {
                assertTrue(SearchText.matches("", HAYSTACK));
                assertTrue(SearchText.matches("   ", HAYSTACK));
                assertTrue(SearchText.matches(null, HAYSTACK));
                assertTrue(SearchText.matches("", ""));
        }

        @Test
        void pipesActAsOr() {
                assertTrue(SearchText.matches("diamond|emerald", HAYSTACK));
                assertTrue(SearchText.matches("emerald|steve", HAYSTACK));
                assertFalse(SearchText.matches("emerald|gold", HAYSTACK));
                // Blank terms inside the alternation are skipped, not matched.
                assertFalse(SearchText.matches("|", HAYSTACK));
        }

        @Test
        void emptyHaystacksNeverMatchATerm() {
                assertFalse(SearchText.matches("diamond", ""));
                assertFalse(SearchText.matches("diamond", null));
        }

        @Test
        void theSpecSearchExamples() {
                String named = SearchText.normalize(List.of("Diamond Chestplate", "Protection IV"));
                assertTrue(SearchText.matches("diamond", named));
                String lores = SearchText.normalize(List.of("Sharp Cactus", "This blade drips sharpness"));
                assertTrue(SearchText.matches("sharpness", lores));
        }
}
