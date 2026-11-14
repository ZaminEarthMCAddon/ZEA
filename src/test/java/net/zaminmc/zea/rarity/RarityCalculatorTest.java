package net.zaminmc.zea.rarity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The rarity model's threshold matrix, pinned to the worked examples in
 * {@link RarityCalculator}'s javadoc. The model itself is pure math, so
 * these cover the spec's verification list without a registry: the
 * registry-dependent inputs (tier, fill, maxFill) have their own tests.
 */
class RarityCalculatorTest {
        private static final double MAX_FILL_SWORD = 6.0;
        private static final double MAX_FILL_PICKAXE = 4.0;
        private static final double MAX_FILL_CHESTPLATE = 3.0;

        private void assertRarity(ItemRarity expected, int tier, double fill, double maxFill) {
                assertEquals(expected, RarityCalculator.calculate(tier, fill, maxFill));
        }

        @Test
        void unenchantedToolsStayCommonThroughIron() {
                assertRarity(ItemRarity.COMMON, 0, 0, MAX_FILL_SWORD);
                assertRarity(ItemRarity.COMMON, 1, 0, MAX_FILL_SWORD);
                assertRarity(ItemRarity.COMMON, 2, 0, MAX_FILL_SWORD);
                assertRarity(ItemRarity.COMMON, 3, 0, MAX_FILL_SWORD);
                assertRarity(ItemRarity.COMMON, 4, 0, MAX_FILL_SWORD);
        }

        @Test
        void unenchantedDiamondIsUncommonAndNetheriteIsEpic() {
                assertRarity(ItemRarity.UNCOMMON, 5, 0, MAX_FILL_SWORD);
                assertRarity(ItemRarity.EPIC, 6, 0, MAX_FILL_SWORD);
        }

        @Test
        void unenchantedNetheriteNeverReachesLegendary() {
                assertRarity(ItemRarity.EPIC, 6, 0, MAX_FILL_SWORD);
        }

        @Test
        void identicalEnchantmentsOnDifferentMaterialsDiffer() {
                // Sharpness V + Unbreaking III (fill 2.0 of 6.0): the
                // enchantment factor alone lifts every material to
                // Uncommon, but higher materials then climb further.
                double fill = 1.0 + 1.0;
                assertRarity(ItemRarity.UNCOMMON, 0, fill, MAX_FILL_SWORD);
                assertRarity(ItemRarity.UNCOMMON, 4, fill, MAX_FILL_SWORD);
                assertRarity(ItemRarity.EPIC, 6, fill, MAX_FILL_SWORD);
        }

        @Test
        void highlyEnchantedLowTierStaysBounded() {
                // A fully maxed wooden sword: complete progress, capped by
                // its material ceiling at Uncommon.
                assertRarity(ItemRarity.UNCOMMON, 0, MAX_FILL_SWORD, MAX_FILL_SWORD);
                assertRarity(ItemRarity.UNCOMMON, 1, MAX_FILL_PICKAXE, MAX_FILL_PICKAXE);
        }

        @Test
        void maxedMidTierReachesEpicButNotLegendary() {
                assertRarity(ItemRarity.EPIC, 2, MAX_FILL_PICKAXE, MAX_FILL_PICKAXE);
                assertRarity(ItemRarity.EPIC, 3, MAX_FILL_SWORD, MAX_FILL_SWORD);
                assertRarity(ItemRarity.EPIC, 4, MAX_FILL_SWORD, MAX_FILL_SWORD);
        }

        @Test
        void maxedHighTierIsLegendary() {
                assertRarity(ItemRarity.LEGENDARY, 5, MAX_FILL_SWORD, MAX_FILL_SWORD);
                assertRarity(ItemRarity.LEGENDARY, 6, MAX_FILL_SWORD, MAX_FILL_SWORD);
                assertRarity(ItemRarity.LEGENDARY, 5, MAX_FILL_PICKAXE, MAX_FILL_PICKAXE);
                assertRarity(ItemRarity.LEGENDARY, 6, MAX_FILL_PICKAXE, MAX_FILL_PICKAXE);
                assertRarity(ItemRarity.LEGENDARY, 5, MAX_FILL_CHESTPLATE, MAX_FILL_CHESTPLATE);
        }

        @Test
        void partialHighTierBeatsMaxedLowTier() {
                // Netherite with a single maxed enchant (fill 1 of 6) stays
                // above the fully maxed wooden sword's ceiling.
                assertEquals(ItemRarity.EPIC, RarityCalculator.calculate(6, 1.0, MAX_FILL_SWORD));
                assertEquals(ItemRarity.UNCOMMON, RarityCalculator.calculate(0, MAX_FILL_SWORD, MAX_FILL_SWORD));
        }

        @Test
        void legendaryRequiresAbsoluteFillNotJustProgress() {
                // A class whose entire budget is worth 2.0 fill can be at
                // 100% progress yet still not Legendary.
                assertRarity(ItemRarity.EPIC, 6, 2.0, 2.0);
        }

        @Test
        void legendaryProgressGateIsStrict() {
                // 5 of 6 enchantments maxed: progress 0.833, below the
                // 0.85 hypermaxed gate — Epic, not Legendary.
                assertRarity(ItemRarity.EPIC, 6, 5.0, MAX_FILL_SWORD);
                // One more maxed enchant crosses the gate.
                assertRarity(ItemRarity.LEGENDARY, 6, 6.0, MAX_FILL_SWORD);
        }

        @Test
        void moderatelyEnchantedDiamondSwordIsUncommon() {
                // Sharpness V + Looting III (fill 2 of 6, progress 0.33).
                assertRarity(ItemRarity.UNCOMMON, 5, 2.0, MAX_FILL_SWORD);
        }

        @Test
        void substantialProgressOnDiamondIsEpic() {
                // Four of six enchantments maxed: progress 0.67.
                assertRarity(ItemRarity.EPIC, 5, 4.0, MAX_FILL_SWORD);
        }

        @Test
        void zeroMaxFillMeansEnchantmentsNeverCount() {
                // A class with no scorable enchantments: only the material
                // factor decides.
                assertRarity(ItemRarity.COMMON, 0, 0, 0);
                assertRarity(ItemRarity.EPIC, 6, 0, 0);
        }

        @Test
        void calculationIsDeterministic() {
                for (int tier = 0; tier <= 6; tier++) {
                        for (double fill = 0; fill <= 6; fill += 0.5) {
                                assertEquals(
                                                                RarityCalculator.calculate(tier, fill, MAX_FILL_SWORD),
                                                                RarityCalculator.calculate(tier, fill, MAX_FILL_SWORD));
                        }
                }
        }

        @Test
        void ceilingFollowsMaterialLadder() {
                assertEquals(1, RarityCalculator.ceilingFor(0));
                assertEquals(1, RarityCalculator.ceilingFor(1));
                assertEquals(2, RarityCalculator.ceilingFor(2));
                assertEquals(2, RarityCalculator.ceilingFor(3));
                assertEquals(2, RarityCalculator.ceilingFor(4));
                assertEquals(3, RarityCalculator.ceilingFor(5));
                assertEquals(3, RarityCalculator.ceilingFor(6));
        }
}
