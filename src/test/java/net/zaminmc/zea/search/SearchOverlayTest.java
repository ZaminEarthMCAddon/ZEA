package net.zaminmc.zea.search;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SearchOverlayTest {
        @Test
        void colorsCarryTheConfiguredAlpha() {
                int fill = SearchOverlay.fillArgb(SearchOverlay.HighlightColor.GREEN);
                assertEquals(0x33, fill >>> 24, "the match fill is subtle (20 %)");
                assertEquals(0x00C850, fill & 0xFFFFFF);

                int border = SearchOverlay.borderArgb(SearchOverlay.HighlightColor.GREEN);
                assertEquals(0xB3, border >>> 24, "the match border is clearly visible (70 %)");

                int dim = SearchOverlay.dimArgb(62);
                assertEquals(62 * 255 / 100, dim >>> 24);
                assertEquals(0, dim & 0xFFFFFF, "the dim is pure black");
        }

        @Test
        void dimIntensityIsClamped() {
                assertEquals(SearchOverlay.dimArgb(SearchOverlay.MIN_DIM_INTENSITY),
                                SearchOverlay.dimArgb(SearchOverlay.MIN_DIM_INTENSITY - 50));
                assertEquals(SearchOverlay.dimArgb(SearchOverlay.MAX_DIM_INTENSITY),
                                SearchOverlay.dimArgb(SearchOverlay.MAX_DIM_INTENSITY + 50));
        }

        @Test
        void highlightColorFallsBackToGreen() {
                assertEquals(SearchOverlay.HighlightColor.GREEN, SearchOverlay.HighlightColor.of(null));
                assertEquals(SearchOverlay.HighlightColor.GREEN, SearchOverlay.HighlightColor.of("nonsense"));
                assertEquals(SearchOverlay.HighlightColor.BLUE, SearchOverlay.HighlightColor.of("blue"));
                assertEquals(SearchOverlay.HighlightColor.BLUE, SearchOverlay.HighlightColor.of("BLUE"));
        }

        @Test
        void everyChoiceHasADistinctLabelAndtoString() {
                String[] labels = new String[SearchOverlay.HighlightColor.values().length];
                int i = 0;
                for (SearchOverlay.HighlightColor color : SearchOverlay.HighlightColor.values()) {
                        labels[i++] = color.label();
                        assertEquals(color.label(), color.toString());
                }
                long distinct = java.util.Arrays.stream(labels).distinct().count();
                assertEquals(labels.length, distinct);
                assertTrue(SearchOverlay.HighlightColor.valueOf("GREEN") == SearchOverlay.HighlightColor.GREEN);
        }
}
