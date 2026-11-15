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
         * Unbreaking's expected-durability multiplier from a refund
         * chance: each incoming durability point is kept with
         * probability 1 − chance, so the lifetime scales by the inverse.
         * The vanilla branch chances at level 3 — armor 6/20, tools 3/4
         * — give the wiki's x1.43 and x4.
         */
        @Test
        void unbreakingFactorFromRefundChance() {
                assertEquals(1.0, DurabilityCalculator.factorFromChance(0.0), 1e-9);
                // Tool branch at level 1: chance 1/2 → x2.
                assertEquals(2.0, DurabilityCalculator.factorFromChance(0.5), 1e-9);
                // Tool branch at level 3: chance 3/4 → x4.
                assertEquals(4.0, DurabilityCalculator.factorFromChance(0.75), 1e-9);
                // Armor branch at level 3: chance 6/20 → x1.4286.
                assertEquals(1.0 / 0.7, DurabilityCalculator.factorFromChance(0.3), 1e-9);
                // Armor branch at level 1: chance 2/10 → x1.25.
                assertEquals(1.25, DurabilityCalculator.factorFromChance(0.2), 1e-9);
        }

        /**
         * The branch selection: the vanilla data ships two Unbreaking
         * branches and the armor one refunds less at every level, so
         * armor takes the smaller chance and everything else the
         * larger. A single (modded) branch is used as-is; no branches
         * at all means no Unbreaking data.
         */
        @Test
        void unbreakingBranchSelection() {
                List<Double> vanillaBranches = List.of(0.3, 0.75);
                assertEquals(0.3, DurabilityCalculator.pickChance(vanillaBranches, true), 1e-9);
                assertEquals(0.75, DurabilityCalculator.pickChance(vanillaBranches, false), 1e-9);
                assertEquals(0.4, DurabilityCalculator.pickChance(List.of(0.4), true), 1e-9);
                assertEquals(0.4, DurabilityCalculator.pickChance(List.of(0.4), false), 1e-9);
                assertEquals(0.0, DurabilityCalculator.pickChance(List.of(), false), 1e-9);
        }

        /**
         * A tool's effective maximum durability is its raw maximum
         * scaled by the unbreaking factor — the expected lifetime a
         * player plans repairs against. Unbreaking III on a diamond
         * pickaxe quadruples it, the armor rate does not.
         */
        @Test
        void durabilityEffectiveMaximumScalesByUnbreaking() {
                ItemStatistics.Durability noUnbreaking = new ItemStatistics.Durability(1561, 1561, 1.0);
                assertEquals(1561, noUnbreaking.effectiveMaximum());
                ItemStatistics.Durability toolUnbreaking3 = new ItemStatistics.Durability(1561, 1561, 4.0);
                assertEquals(6244, toolUnbreaking3.effectiveMaximum());
                ItemStatistics.Durability armorUnbreaking3 = new ItemStatistics.Durability(480, 480, 1.0 / 0.7);
                assertEquals(686, armorUnbreaking3.effectiveMaximum());
        }

        /**
         * The melee total is base plus enchantment bonus — the
         * enchantment contribution is additive, never a multiplier on
         * the item's base damage. The split label rides along for the
         * presentation.
         */
        @Test
        void meleeDamageTotalIsBasePlusEnchantment() {
                ItemStatistics.MeleeDamage damage = new ItemStatistics.MeleeDamage(
                                8.0, 3.0, "Sharpness V", 1.6, 0.0, List.of());
                assertEquals(11.0, damage.total(), 1e-9);
                assertEquals("Sharpness V", damage.bonusLabel());
        }

        /**
         * The bow's fully-drawn damage is the draw speed times the
         * arrow's base damage plus the Power bonus — Power multiplies
         * through the velocity, exactly as the arrow's hit code
         * computes it. A Power V bow at full draw deals 15, not a flat
         * "6 + 3"; an unenchanted crossbow arrow deals ceil(3.15 x 2)
         * = 7.
         */
        @Test
        void projectileDamageScalesPowerThroughVelocity() {
                ItemStatistics.ProjectileStats noPower = new ItemStatistics.ProjectileStats(
                                3.0, 2.0, 0.0, 20, 1, 0, false, 0);
                assertEquals(6.0, noPower.fullyCharged(), 1e-9);
                ItemStatistics.ProjectileStats powerV = new ItemStatistics.ProjectileStats(
                                3.0, 2.0, 3.0, 20, 1, 0, false, 0);
                assertEquals(15.0, powerV.fullyCharged(), 1e-9);
                ItemStatistics.ProjectileStats crossbowArrow = new ItemStatistics.ProjectileStats(
                                3.15, 2.0, 0.0, 25, 1, 0, false, 0);
                assertEquals(7.0, crossbowArrow.fullyCharged(), 1e-9);
        }

        /**
         * A firework rocket's direct-hit damage is 5 plus 2 per star —
         * the rocket's own explosion math, not the arrow's.
         */
        @Test
        void fireworkDamageIsFivePlusTwoPerStar() {
                assertEquals(5.0, new ItemStatistics.ProjectileStats(
                                1.6, 2.0, 0.0, 25, 1, 0, true, 0).fireworkDamage(), 1e-9);
                assertEquals(11.0, new ItemStatistics.ProjectileStats(
                                1.6, 2.0, 0.0, 25, 1, 0, true, 3).fireworkDamage(), 1e-9);
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
                                0.0, 0.0, false);
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
                ItemStatistics.MiningSpeed noEfficiency = new ItemStatistics.MiningSpeed(8.0, 0.0, true);
                assertEquals(8.0, noEfficiency.correctForDrops(), 1e-9);
                ItemStatistics.MiningSpeed efficiencyV = new ItemStatistics.MiningSpeed(8.0, 26.0, true);
                assertEquals(34.0, efficiencyV.correctForDrops(), 1e-9);
                assertTrue(efficiencyV.appliesOnlyWhenCorrect(),
                                "Efficiency must be marked as applying only when correct for drops");
        }

        /**
         * Food effect labels: duration in ticks as m:ss, amplifiers as
         * roman numerals (level II means amplifier 1), the infinite
         * duration as the glyph the game itself uses.
         */
        @Test
        void foodEffectFormatting() {
                assertEquals("0:05", FoodCalculator.duration(100));
                assertEquals("1:30", FoodCalculator.duration(1800));
                assertEquals("0:00", FoodCalculator.duration(1));
                assertEquals("∞", FoodCalculator.duration(-1));
                assertEquals("II", FoodCalculator.roman(2));
                assertEquals("I", FoodCalculator.roman(1));
                assertEquals("13", FoodCalculator.roman(13));
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
                assertTrue(empty.armor().isEmpty());
                assertTrue(empty.durability().isEmpty());
                assertTrue(empty.projectile().isEmpty());
                assertTrue(empty.smashDamage().isEmpty());
                assertTrue(empty.spear().isEmpty());
                assertTrue(empty.food().isEmpty());
        }
}
