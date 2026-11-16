package net.zaminmc.zea.rarity;

import net.minecraft.network.chat.Component;
import net.zaminmc.zea.VanillaBootstrap;
import net.zaminmc.zea.itempresentation.recognition.ItemCategory;
import org.junit.jupiter.api.Test;

import static net.zaminmc.zea.rarity.RarityTooltipEditor.EnchantmentStyle;
import static net.zaminmc.zea.rarity.RarityTooltipEditor.footerLabel;
import static net.zaminmc.zea.rarity.RarityTooltipEditor.isAttributeSectionHeader;
import static net.zaminmc.zea.rarity.RarityTooltipEditor.styleForLevels;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
}
