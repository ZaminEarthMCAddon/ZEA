package net.zaminmc.zea.rarity;

import net.zaminmc.zea.VanillaBootstrap;
import org.junit.jupiter.api.Test;

import static net.zaminmc.zea.rarity.RarityTooltipEditor.EnchantmentStyle;
import static net.zaminmc.zea.rarity.RarityTooltipEditor.styleForLevels;
import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
