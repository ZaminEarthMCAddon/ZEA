package net.zaminmc.zea.misc;

import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The forged-item meta recognizer, pinned purely: the whole four-line
 * record — header, Forged by, Origin, Date — is removed wherever it
 * sits, case-insensitive and whitespace-tolerant; other lore, usage
 * hints and the name line survive; and a tooltip without the block is
 * untouched line for line.
 */
class ServerItemMetaTest {
        /** The server's full record, as the user sees it on a forged sword. */
        private static List<Component> forgedBlock() {
                return List.of(
                                Component.literal("Diamond Sword"),
                                Component.literal(""),
                                Component.literal("Historical information"),
                                Component.literal("Forged by: Abasing"),
                                Component.literal("Origin: Micronesia|Palau"),
                                Component.literal("Date: 2026-11-5"));
        }

        /** The whole record goes, name and structure survive. */
        @Test
        void theWholeBlockIsRemoved() {
                List<Component> lines = new ArrayList<>(forgedBlock());
                ServerItemMeta.filter(lines);
                assertEquals(List.of("Diamond Sword", ""), texts(lines));
        }

        /** The block is recognized anywhere below the name line. */
        @Test
        void theBlockIsFoundWhereverItSits() {
                List<Component> lines = new ArrayList<>(List.of(
                                Component.literal("Netherite Spear"),
                                Component.literal("Historical information"),
                                Component.literal("Forged by: Abasing"),
                                Component.literal("Origin: Micronesia|Palau"),
                                Component.literal("Date: 2026-11-5"),
                                Component.literal(""),
                                Component.literal("A gift from the mayor")));
                ServerItemMeta.filter(lines);
                assertEquals(List.of("Netherite Spear", "", "A gift from the mayor"), texts(lines));
        }

        /** Matching is case-insensitive and padding-tolerant. */
        @Test
        void matchingToleratesCaseAndPadding() {
                assertTrue(ServerItemMeta.isMetaLine("  HISTORICAL INFORMATION "));
                assertTrue(ServerItemMeta.isMetaLine("\tforged by:  SomeoneElse\n"));
                assertTrue(ServerItemMeta.isMetaLine("ORIGIN: Aurora|Danish "),
                                "the prefix match ignores case");
                assertTrue(ServerItemMeta.isMetaLine("date: 2026-11-5"));
        }

        /** Each field is its own match: a partial record still goes whole-line. */
        @Test
        void eachFieldMatchesOnItsOwn() {
                assertTrue(ServerItemMeta.isMetaLine("Forged by: Abasing"));
                assertTrue(ServerItemMeta.isMetaLine("Origin: Micronesia|Palau"));
                assertTrue(ServerItemMeta.isMetaLine("Date: 2026-11-5"));
        }

        /** Other lore, hints and the name line are not meta. */
        @Test
        void otherLoreIsNotMeta() {
                assertFalse(ServerItemMeta.isMetaLine("Forged in the deepest mines"),
                                "a sentence that starts the same way is server flavor");
                assertFalse(ServerItemMeta.isMetaLine("Right-click to open"));
                assertFalse(ServerItemMeta.isMetaLine(""));
                assertFalse(ServerItemMeta.isMetaLine(null));
        }

        /** The name line is never touched, even if it spells the header. */
        @Test
        void theNameLineIsNeverRemoved() {
                List<Component> lines = new ArrayList<>(List.of(
                                Component.literal("Historical information"),
                                Component.literal("Forged by: Abasing")));
                ServerItemMeta.filter(lines);
                assertEquals(List.of("Historical information"), texts(lines));
        }

        /** A tooltip without the block is untouched, line for line. */
        @Test
        void aTooltipWithoutTheBlockIsUntouched() {
                List<Component> lines = new ArrayList<>(List.of(
                                Component.literal("Diamond Sword"),
                                Component.literal(""),
                                Component.literal("+7 Attack Damage"),
                                Component.literal(""),
                                Component.literal("A gift from the mayor")));
                List<String> expected = texts(lines);
                ServerItemMeta.filter(lines);
                assertEquals(expected, texts(lines));
        }

        /** A removal that would double a blank collapses the pair to one. */
        @Test
        void removalCollapsesDoubledBlanks() {
                // The block sits between two blanks; removing it must not
                // leave two empty lines in a row.
                List<Component> lines = new ArrayList<>(List.of(
                                Component.literal("Diamond Sword"),
                                Component.literal(""),
                                Component.literal("Historical information"),
                                Component.literal("Forged by: Abasing"),
                                Component.literal(""),
                                Component.literal("Legendary Sword")));
                ServerItemMeta.filter(lines);
                assertEquals(List.of("Diamond Sword", "", "Legendary Sword"), texts(lines));
        }

        private static List<String> texts(List<Component> lines) {
                return lines.stream().map(Component::getString).toList();
        }
}
