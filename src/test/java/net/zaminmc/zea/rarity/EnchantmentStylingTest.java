package net.zaminmc.zea.rarity;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.zaminmc.zea.VanillaBootstrap;
import net.zaminmc.zea.itempresentation.recognition.ItemCategory;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static net.zaminmc.zea.rarity.RarityTooltipEditor.EnchantmentStyle;
import static net.zaminmc.zea.rarity.RarityTooltipEditor.footerLabel;
import static net.zaminmc.zea.rarity.RarityTooltipEditor.isAttributeSectionHeader;
import static net.zaminmc.zea.rarity.RarityTooltipEditor.recolorTree;
import static net.zaminmc.zea.rarity.RarityTooltipEditor.removeAdvancedDurabilityLines;
import static net.zaminmc.zea.rarity.RarityTooltipEditor.removeEnchantmentExplanationLore;
import static net.zaminmc.zea.rarity.RarityTooltipEditor.removeVanillaAttributeSections;
import static net.zaminmc.zea.rarity.RarityTooltipEditor.styleForLevels;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The enchantment styling tiers, pinned through the pure
 * (level, maxLevel) seam. The line-matching path needs a registry
 * holder; the tier decision is pure math, so every boundary is
 * testable without one.
 */
class EnchantmentStylingTest {
        static {
                VanillaBootstrap.ensure();
        }

        /** A level below the maximum is the basic grey tier. */
        @Test
        void belowMaximumIsBasic() {
                assertEquals(EnchantmentStyle.BASIC, styleForLevels(1, 5));
                assertEquals(EnchantmentStyle.BASIC, styleForLevels(4, 5));
        }

        /** A level at the maximum is the chroma tier, regardless of the cap. */
        @Test
        void atMaximumIsMaxed() {
                assertEquals(EnchantmentStyle.MAXED, styleForLevels(5, 5));
                assertEquals(EnchantmentStyle.MAXED, styleForLevels(3, 3));
                assertEquals(EnchantmentStyle.MAXED, styleForLevels(1, 1));
        }

        /** A level above the maximum is the enhanced blue tier — preserved, not clamped. */
        @Test
        void aboveMaximumIsEnhanced() {
                assertEquals(EnchantmentStyle.ENHANCED, styleForLevels(6, 5));
                assertEquals(EnchantmentStyle.ENHANCED, styleForLevels(10, 5));
        }

        /** The fixed colors match the spec: grey for basic, blue for enhanced. */
        @Test
        void fixedColorsMatchSpec() {
                assertEquals(0xAAAAAA, EnchantmentStyle.BASIC.rgb, "basic is grey (&7 family)");
                assertEquals(0x5555FF, EnchantmentStyle.ENHANCED.rgb, "enhanced is blue (&9 family)");
        }

        /**
         * The static fallback for the maxed tier when the chroma
         * animation is off — the gold (&6 family), so the tier stays
         * visible without motion. The animated color itself is the
         * chroma rotation, pure in the clock and pinned in
         * RarityFieldTest's chroma pins.
         */
        @Test
        void staticMaxedFallbackIsGold() {
                assertEquals(0xFFAA00, RarityEngine.STATIC_MAXED_COLOR,
                                "the animation-off maxed color is the gold (&6) tier");
        }

        /**
         * Every 26.3 attribute header is a translatable
         * {@code item.modifiers.*} component, whatever the display text
         * resolves to — including the renamed chest header and the
         * group headers (worn, equipped, held, saddled) the old
         * hardcoded string list missed.
         */
        @Test
        void attributeHeadersMatchByTranslationKey() {
                for (String key : new String[] {
                                "item.modifiers.mainhand", "item.modifiers.offhand",
                                "item.modifiers.head", "item.modifiers.chest",
                                "item.modifiers.legs", "item.modifiers.feet",
                                "item.modifiers.armor", "item.modifiers.body",
                                "item.modifiers.any", "item.modifiers.hand",
                                "item.modifiers.saddle"}) {
                        assertTrue(isAttributeSectionHeader(Component.translatable(key)),
                                        key + " is an attribute header");
                }
        }

        /**
         * A plain-text line that merely spells a header's words is not
         * a header — server lore survives the attribute-section removal
         * even when it mimics the vanilla wording.
         */
        @Test
        void plainTextLookalikeIsNotAHeader() {
                assertFalse(isAttributeSectionHeader(
                                Component.literal("When in Main Hand:")));
                assertFalse(isAttributeSectionHeader(
                                Component.literal("When on Chest:")));
                assertFalse(isAttributeSectionHeader(Component.literal("When on Body:")));
                assertFalse(isAttributeSectionHeader(Component.empty()));
        }

        /**
         * The rarity footer is RARITY plus equipment type — uppercase,
         * from the enums, never a bare rarity word and never lowercase.
         */
        @Test
        void footerLabelJoinsRarityAndEquipmentType() {
                assertEquals("UNCOMMON SWORD", footerLabel(ItemRarity.UNCOMMON, ItemCategory.SWORD));
                assertEquals("EPIC PICKAXE", footerLabel(ItemRarity.EPIC, ItemCategory.PICKAXE));
                assertEquals("LEGENDARY BOW", footerLabel(ItemRarity.LEGENDARY, ItemCategory.BOW));
                assertEquals("COMMON CHESTPLATE", footerLabel(ItemRarity.COMMON, ItemCategory.CHESTPLATE));
                assertEquals("UNCOMMON TRIDENT", footerLabel(ItemRarity.UNCOMMON, ItemCategory.TRIDENT));
        }

        // ------------------------------------------------------------------
        // Attribute-section removal (the pure seam, with a marker predicate)
        // ------------------------------------------------------------------

        /** A predicate marking the advanced-trailer marker line used by the tests. */
        private static boolean isMarker(Component line) {
                return line.getString().equals("minecraft:test_item");
        }

        /**
         * The plain removal: the emitter's blank, the header and the
         * entries go; everything before and after survives.
         */
        @Test
        void attributeRemovalDropsBlankHeaderAndEntries() {
                List<Component> lines = new ArrayList<>(List.of(
                                Component.literal("Test Sword"),
                                Component.literal("Sharpness V"),
                                Component.empty(),
                                Component.translatable("item.modifiers.mainhand"),
                                Component.literal("7 Attack Damage"),
                                Component.literal("1.6 Attack Speed")));
                removeVanillaAttributeSections(lines, EnchantmentStylingTest::isMarker);
                assertEquals(List.of("Test Sword", "Sharpness V"),
                                lines.stream().map(Component::getString).toList());
        }

        /**
         * The regression the live F3+H pass caught: with advanced
         * tooltips on, the identifier and the component count follow
         * the attribute entries with no blank between, and the removal
         * must stop at the advanced trailer instead of swallowing it.
         */
        @Test
        void attributeRemovalStopsAtTheAdvancedTrailer() {
                List<Component> lines = new ArrayList<>(List.of(
                                Component.literal("Test Sword"),
                                Component.literal("Sharpness V"),
                                Component.empty(),
                                Component.translatable("item.modifiers.mainhand"),
                                Component.literal("7 Attack Damage"),
                                Component.literal("minecraft:test_item"),
                                Component.literal("14 component(s)")));
                removeVanillaAttributeSections(lines, EnchantmentStylingTest::isMarker);
                assertEquals(List.of("Test Sword", "Sharpness V",
                                "minecraft:test_item", "14 component(s)"),
                                lines.stream().map(Component::getString).toList());
        }

        /**
         * Repeated slot-group sections — 26.3's "When worn:" family —
         * are all removed, each with its own leading blank, and the
         * blank between two sections belongs to the one that follows.
         */
        @Test
        void attributeRemovalHandlesRepeatedGroupSections() {
                List<Component> lines = new ArrayList<>(List.of(
                                Component.literal("Test Chestplate"),
                                Component.literal("Protection IV"),
                                Component.empty(),
                                Component.translatable("item.modifiers.armor"),
                                Component.literal("+8 Armor"),
                                Component.empty(),
                                Component.translatable("item.modifiers.chest"),
                                Component.literal("+2 Armor Toughness"),
                                Component.literal("minecraft:test_item")));
                removeVanillaAttributeSections(lines, EnchantmentStylingTest::isMarker);
                assertEquals(List.of("Test Chestplate", "Protection IV", "minecraft:test_item"),
                                lines.stream().map(Component::getString).toList());
        }

        /**
         * No header means nothing is removed — a server tooltip that
         * omits the attribute listing passes through untouched.
         */
        @Test
        void attributeRemovalWithoutHeaderChangesNothing() {
                List<Component> lines = new ArrayList<>(List.of(
                                Component.literal("Test Sword"),
                                Component.literal("server lore"),
                                Component.literal("minecraft:test_item")));
                removeVanillaAttributeSections(lines, EnchantmentStylingTest::isMarker);
                assertEquals(List.of("Test Sword", "server lore", "minecraft:test_item"),
                                lines.stream().map(Component::getString).toList());
        }

        // ------------------------------------------------------------------
        // Name recoloring (the pure tree seam)
        // ------------------------------------------------------------------

        /**
         * The live-server regression this seam exists for: a custom
         * name whose color lives in child runs — the root is
         * colorless, an aqua child carries the display — kept its
         * server color while the footer below showed the rarity,
         * because the old root-only recolor never reached the
         * children. The tree recolor replaces every explicit color,
         * root and children alike, and preserves the text and the
         * non-color styling.
         */
        @Test
        void serverNameWithChildColorsRecolorsThroughTheTree() {
                MutableComponent name = Component.empty()
                                .append(Component.literal("Diamond ").withStyle(ChatFormatting.AQUA))
                                .append(Component.literal("Sword")
                                                .withStyle(ChatFormatting.AQUA)
                                                .withStyle(ChatFormatting.BOLD));
                TextColor epic = RarityEngine.textColor(ItemRarity.EPIC);
                MutableComponent recolored = recolorTree(name, epic);
                assertEquals("Diamond Sword", recolored.getString(),
                                "the exact displayed text survives");
                assertEquals(epic, recolored.getStyle().getColor(),
                                "the root takes the rarity color");
                assertEquals(2, recolored.getSiblings().size(),
                                "the child runs survive as children");
                for (Component run : recolored.getSiblings()) {
                        assertEquals(epic, run.getStyle().getColor(),
                                        "a child run's color follows the rarity");
                }
                assertTrue(recolored.getSiblings().get(1).getStyle().isBold(),
                                "non-color styling survives");
                assertNotEquals(name, recolored, "the original tree is never modified");
        }

        /** A root-colored name recolors exactly as before — the root path never regressed. */
        @Test
        void rootColoredNameStillRecolors() {
                MutableComponent name = Component.literal("Bow")
                                .withStyle(ChatFormatting.AQUA);
                TextColor legendary = RarityEngine.textColor(ItemRarity.LEGENDARY);
                MutableComponent recolored = recolorTree(name, legendary);
                assertEquals("Bow", recolored.getString());
                assertEquals(legendary, recolored.getStyle().getColor());
                assertTrue(recolored.getSiblings().isEmpty(), "no children to walk");
        }

        /** The recolor reaches grandchildren — servers nest deeper than one level. */
        @Test
        void recolorReachesGrandchildren() {
                MutableComponent name = Component.empty()
                                .append(Component.empty()
                                                .append(Component.literal("Deep")
                                                                .withStyle(ChatFormatting.AQUA)));
                TextColor color = RarityEngine.textColor(ItemRarity.COMMON);
                MutableComponent recolored = recolorTree(name, color);
                assertEquals("Deep", recolored.getString());
                assertEquals(color, recolored.getSiblings().get(0).getSiblings().get(0)
                                .getStyle().getColor(),
                                "the grandchild's color follows the rarity");
        }

        // ------------------------------------------------------------------
        // Advanced durability dedup (the pure seam)
        // ------------------------------------------------------------------

        /**
         * The duplicate-durability fix: the native advanced
         * {@code item.durability} line goes when the stat block above
         * already shows the same pair, while the identifier, the
         * component count and everything else keep their places.
         */
        @Test
        void advancedDurabilityLineIsRemovedAndTheTrailerStays() {
                List<Component> lines = new ArrayList<>(List.of(
                                Component.literal("Test Sword"),
                                Component.literal("Durability: 1354 / 1561"),
                                Component.translatable("item.durability", 1354, 1561),
                                Component.literal("minecraft:test_item"),
                                Component.translatable("item.components", 19)));
                removeAdvancedDurabilityLines(lines);
                assertEquals(List.of("Test Sword", "Durability: 1354 / 1561",
                                "minecraft:test_item", "19 component(s)"),
                                lines.stream().map(Component::getString).toList());
        }

        /**
         * A plain-text line that merely spells the durability words is
         * not the translatable advanced entry — server lore phrased the
         * same way survives.
         */
        @Test
        void plainTextDurabilityLookalikeStays() {
                List<Component> lines = new ArrayList<>(List.of(
                                Component.literal("Test Sword"),
                                Component.literal("Durability: 100 / 200")));
                removeAdvancedDurabilityLines(lines);
                assertEquals(List.of("Test Sword", "Durability: 100 / 200"),
                                lines.stream().map(Component::getString).toList());
        }

        // ------------------------------------------------------------------
        // Enchantment-explanation lore removal (the pure seam)
        // ------------------------------------------------------------------

        /**
         * The explanation lore a server attaches beside its
         * enchantments — the observed phrase bank and the held
         * enchantments' live descriptions — is removed, while flavor
         * and usage lore in the same block keeps its place and the
         * name line is never touched.
         */
        @Test
        void explanationLoreIsRemovedAndOtherLoreStays() {
                EnchantmentFixtures fixtures = new EnchantmentFixtures();
                ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
                mutable.set(fixtures.get("sharpness"), 5);
                ItemEnchantments held = mutable.toImmutable();
                List<Component> lines = new ArrayList<>(List.of(
                                Component.literal("Diamond Sword"),
                                Component.literal("Increases attack damage."),
                                Component.literal("Right-click to activate"),
                                Component.literal("Sharpness"),
                                Component.literal("A gift from the mayor")));
                removeEnchantmentExplanationLore(lines, held);
                assertEquals(List.of("Diamond Sword", "Right-click to activate",
                                "A gift from the mayor"),
                                lines.stream().map(Component::getString).toList());
        }

        /** With no enchantments held there is nothing to explain — every lore line stays. */
        @Test
        void explanationRemovalWithoutEnchantmentsChangesNothing() {
                List<Component> lines = new ArrayList<>(List.of(
                                Component.literal("Test Sword"),
                                Component.literal("Increases attack damage.")));
                removeEnchantmentExplanationLore(lines, ItemEnchantments.EMPTY);
                assertEquals(List.of("Test Sword", "Increases attack damage."),
                                lines.stream().map(Component::getString).toList());
        }
}
