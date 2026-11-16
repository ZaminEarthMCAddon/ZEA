package net.zaminmc.zea.itempresentation.presentation;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.PlainTextContents;
import net.zaminmc.zea.VanillaBootstrap;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The compact grouped enchantment section: alphabetical ordering,
 * comma-boundary wrapping by measured width, and the color structure —
 * each name keeps its entry color and the separators are grey. The
 * width oracle is the plain string length, so the wrap decisions are
 * deterministic without a font.
 */
class EnchantmentSectionTest {
        static {
                VanillaBootstrap.ensure();
        }

        /** Entries arrive unsorted; the section renders alphabetically. */
        @Test
        void entriesAreSortedAlphabeticallyByDisplayName() {
                List<Component> lines = EnchantmentSection.render(List.of(
                                new EnchantmentSection.Entry("Unbreaking III", 0x5555FF),
                                new EnchantmentSection.Entry("Fire Aspect II", 0x5555FF),
                                new EnchantmentSection.Entry("Sharpness V", 0x5555FF)),
                                1000, String::length);
                assertEquals(1, lines.size(), "everything fits on one line");
                assertEquals("Fire Aspect II, Sharpness V, Unbreaking III", lines.get(0).getString(),
                                "alphabetical by rendered name, regardless of input order");
        }

        /** Long lists wrap at comma boundaries, never mid-name. */
        @Test
        void wrappingBreaksAtCommaBoundaries() {
                // Width oracle is string length: "Fire Aspect II, Looting III"
                // is 27 units; adding ", Sharpness V" (13 more) would pass 30.
                List<Component> lines = EnchantmentSection.render(List.of(
                                new EnchantmentSection.Entry("Sharpness V", 0x5555FF),
                                new EnchantmentSection.Entry("Fire Aspect II", 0x5555FF),
                                new EnchantmentSection.Entry("Looting III", 0x5555FF)),
                                30, String::length);
                assertEquals(2, lines.size());
                assertEquals("Fire Aspect II, Looting III", lines.get(0).getString());
                assertEquals("Sharpness V", lines.get(1).getString());
        }

        /** An entry wider than the whole budget gets its own line, never truncation. */
        @Test
        void overWideEntryGetsItsOwnLine() {
                List<Component> lines = EnchantmentSection.render(List.of(
                                new EnchantmentSection.Entry("A very long enchantment name", 0x5555FF),
                                new EnchantmentSection.Entry("Bane of Arthropods V", 0x5555FF)),
                                10, String::length);
                assertEquals(2, lines.size());
                assertEquals("A very long enchantment name", lines.get(0).getString(),
                                "nothing is ever cut off to fit");
                assertEquals("Bane of Arthropods V", lines.get(1).getString());
        }

        /**
         * The color structure: a line is a sequence of name components
         * in their entry colors joined by grey separators — the exact
         * component tree the renderer emits, verified by walking the
         * siblings.
         */
        @Test
        void namesKeepTheirColorsAndSeparatorsAreGrey() {
                List<Component> lines = EnchantmentSection.render(List.of(
                                new EnchantmentSection.Entry("Sharpness V", 0x5555FF),
                                new EnchantmentSection.Entry("Curse of Vanishing", 0xFF5555)),
                                1000, String::length);
                assertEquals(1, lines.size());
                List<Component> parts = lines.get(0).getSiblings();
                assertEquals(3, parts.size(), "name, separator, name");
                assertEquals("Curse of Vanishing", parts.get(0).getString(),
                                "alphabetically first");
                assertEquals(0xFF5555, parts.get(0).getStyle().getColor().getValue(),
                                "a curse keeps its red");
                assertEquals(", ", parts.get(1).getString());
                assertEquals(0xAAAAAA, parts.get(1).getStyle().getColor().getValue(),
                                "the separator is grey");
                assertEquals("Sharpness V", parts.get(2).getString());
                assertEquals(0x5555FF, parts.get(2).getStyle().getColor().getValue());
        }

        /** Empty input renders nothing. */
        @Test
        void emptyInputRendersNothing() {
                assertTrue(EnchantmentSection.render(List.of(), 100, String::length).isEmpty());
        }
}
