package net.zaminmc.zea.itempresentation.calculation;

import net.zaminmc.zea.VanillaBootstrap;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The pure-math seams of the calculation engine. The component-reading
 * calculators depend on item default components (bound only during the
 * data-load flow a bare test JVM never runs), so the live registry
 * reads get verified in {@link EnchantmentEffectReadsTest} against
 * constructed effect data and at runtime; the deterministic formulas
 * they expose are pinned here against values verified from the 26.3
 * bytecode and datapack.
 */
class CalculationMathTest {
        static {
                VanillaBootstrap.ensure();
        }

        /**
         * The melee total is item plus enchantment plus — when held —
         * the wielder's base and active effects. The enchantment
         * contribution is additive, never a multiplier on the item's
         * base damage. The split label and level ride along for the
         * presentation's bracket indicator.
         */
        @Test
        void meleeDamageTotalIsItemPlusEnchantmentPlusWielder() {
                ItemStatistics.MeleeDamage spare = new ItemStatistics.MeleeDamage(
                                6.0, 3.0, "Sharpness V", 5, 0.0, 0.0, "", 1.6, 0.0, List.of());
                assertEquals(9.0, spare.total(), 1e-9,
                                "a spare sword's total is the item plus the enchantment");
                ItemStatistics.MeleeDamage held = new ItemStatistics.MeleeDamage(
                                6.0, 3.0, "Sharpness V", 5, 1.0, 3.0, "Strength", 1.6, 0.0, List.of());
                assertEquals(13.0, held.total(), 1e-9,
                                "a held sword adds the wielder's base and active effects");
                assertEquals("Sharpness V", held.bonusLabel());
                assertEquals(5, held.bonusLevel());
        }

        /**
         * The bow's fully-drawn damage is the draw speed times the
         * arrow's base damage plus the Power bonus — Power multiplies
         * through the velocity, exactly as the arrow's hit code
         * computes it. A Power V bow at full draw deals 15, not a flat
         * "6 + 3"; an unenchanted crossbow arrow deals ceil(3.15 x 2)
         * = 7. A trident's flat throw damage is base plus bonus, not
         * velocity-scaled.
         */
        @Test
        void projectileDamageScalesPowerThroughVelocity() {
                ItemStatistics.ProjectileStats noPower = new ItemStatistics.ProjectileStats(
                                3.0, 2.0, 0.0, 0, 20, 1, 0, false, 0, true);
                assertEquals(6.0, noPower.fullyCharged(), 1e-9);
                ItemStatistics.ProjectileStats powerV = new ItemStatistics.ProjectileStats(
                                3.0, 2.0, 3.0, 5, 20, 1, 0, false, 0, true);
                assertEquals(15.0, powerV.fullyCharged(), 1e-9);
                ItemStatistics.ProjectileStats crossbowArrow = new ItemStatistics.ProjectileStats(
                                3.15, 2.0, 0.0, 0, 25, 1, 0, false, 0, true);
                assertEquals(7.0, crossbowArrow.fullyCharged(), 1e-9);
                ItemStatistics.ProjectileStats trident = new ItemStatistics.ProjectileStats(
                                2.5, 8.0, 3.0, 5, 10, 1, 0, false, 0, false);
                assertEquals(11.0, trident.flat(), 1e-9);
        }

        /**
         * A firework rocket's direct-hit damage is 5 plus 2 per star —
         * the rocket's own explosion math, not the arrow's.
         */
        @Test
        void fireworkDamageIsFivePlusTwoPerStar() {
                assertEquals(5.0, new ItemStatistics.ProjectileStats(
                                1.6, 2.0, 0.0, 0, 25, 1, 0, true, 0, true).fireworkDamage(), 1e-9);
                assertEquals(11.0, new ItemStatistics.ProjectileStats(
                                1.6, 2.0, 0.0, 0, 25, 1, 0, true, 3, true).fireworkDamage(), 1e-9);
        }

        /**
         * The mace's smash curve, verified against the 26.3 bytecode:
         * 4 per fallen block up to 3, 2 per block from 3 to 8, 1 per
         * block beyond. The minimum smash (at the 1.5-block threshold)
         * already adds 6; the 20-block dive adds 34.
         */
        @Test
        void maceSmashCurveMatchesVanilla() {
                ItemStatistics.SmashDamage smash = new ItemStatistics.SmashDamage(
                                1.5,
                                List.of(new ItemStatistics.SmashDamage.SmashBand(4.0, 3.0),
                                                new ItemStatistics.SmashDamage.SmashBand(2.0, 8.0),
                                                new ItemStatistics.SmashDamage.SmashBand(1.0,
                                                                Double.POSITIVE_INFINITY)),
                                0.0, 0, 0.0, 0, false);
                assertEquals(0.0, smash.fallBonus(0.0), 1e-9);
                assertEquals(6.0, smash.fallBonus(1.5), 1e-9);
                assertEquals(12.0, smash.fallBonus(3.0), 1e-9);
                assertEquals(22.0, smash.fallBonus(8.0), 1e-9);
                assertEquals(34.0, smash.fallBonus(20.0), 1e-9);
        }

        /**
         * Mining speed when correct for drops is base plus Efficiency;
         * Efficiency V's live contribution is 26 (level squared plus
         * one), so a diamond pickaxe reads 34.
         */
        @Test
        void miningSpeedCorrectForDropsAddsEfficiency() {
                ItemStatistics.MiningSpeed noEfficiency = new ItemStatistics.MiningSpeed(8.0, 0.0, 0, true);
                assertEquals(8.0, noEfficiency.correctForDrops(), 1e-9);
                ItemStatistics.MiningSpeed efficiencyV = new ItemStatistics.MiningSpeed(8.0, 26.0, 5, true);
                assertEquals(34.0, efficiencyV.correctForDrops(), 1e-9);
                assertTrue(efficiencyV.appliesOnlyWhenCorrect(),
                                "Efficiency must be marked as applying only when correct for drops");
                assertEquals(5, efficiencyV.efficiencyLevel(),
                                "the Efficiency level rides along for the bracket indicator");
        }

        /**
         * Mining Fortune's expected-yield formula: the multiplier
         * {@code 1 / (L + 2) + (L + 1) / 2} gives 1.33x, 1.75x and
         * 2.20x for the three standard levels, displayed as +33 %,
         * +75 % and +120 % — the calculated yield, never the raw
         * enchantment level.
         */
        @Test
        void miningFortuneReadsTheVanillaExpectedYieldFormula() {
                assertEquals(33.3333333, MiningFortuneCalculator.percentBonus(1), 1e-4);
                assertEquals(75.0, MiningFortuneCalculator.percentBonus(2), 1e-9);
                assertEquals(120.0, MiningFortuneCalculator.percentBonus(3), 1e-9);
                assertEquals(0.0, MiningFortuneCalculator.percentBonus(0), 1e-9,
                                "no fortune, no percentage");
        }

        /**
         * An empty statistics set carries no populated optional — the
         * short-circuit for items ZEA has no presentation for.
         */
        @Test
        void emptyStatisticsHasNoPopulatedOptionals() {
                ItemStatistics empty = ItemStatistics.empty();
                assertTrue(empty.meleeDamage().isEmpty());
                assertTrue(empty.miningSpeed().isEmpty());
                assertTrue(empty.miningFortune().isEmpty());
                assertTrue(empty.armor().isEmpty());
                assertTrue(empty.durability().isEmpty());
                assertTrue(empty.projectile().isEmpty());
                assertTrue(empty.smashDamage().isEmpty());
                assertTrue(empty.spear().isEmpty());
                assertTrue(empty.magicFind().isEmpty());
        }
}
