package net.zaminmc.zea.itempresentation.calculation;

import net.zaminmc.zea.VanillaBootstrap;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The pure-math seams of the calculation engine. The component-reading
 * calculators depend on item default components (bound only during the
 * data-load flow a bare test JVM never runs), so they get live runtime
 * verification; the deterministic formulas they expose are pinned here.
 */
class CalculationMathTest {
        static {
                VanillaBootstrap.ensure();
        }

        /**
         * Unbreaking's expected-durability multiplier. Level 0 is 1.0
         * (no change); each level adds a 1/(L+1) refund chance, so the
         * effective durability is maximum divided by the kept fraction.
         */
        @Test
        void unbreakingFactorMatchesVanillaFormula() {
                assertEquals(1.0, DurabilityCalculator.unbreakingFactor(0), 1e-9);
                // Level 1: 50% refund → effective ×2.
                assertEquals(2.0, DurabilityCalculator.unbreakingFactor(1), 1e-9);
                // Level 2: 33% refund → effective ×1.5.
                assertEquals(1.5, DurabilityCalculator.unbreakingFactor(2), 1e-9);
                // Level 3: 25% refund → effective ×1.333…
                assertEquals(4.0 / 3.0, DurabilityCalculator.unbreakingFactor(3), 1e-9);
        }

        /**
         * A tool's effective maximum durability is its raw maximum
         * scaled by the unbreaking factor — the expected lifetime a
         * player plans repairs against.
         */
        @Test
        void durabilityEffectiveMaximumScalesByUnbreaking() {
                ItemStatistics.Durability noUnbreaking = new ItemStatistics.Durability(1561, 1561, 1.0);
                assertEquals(1561, noUnbreaking.effectiveMaximum());
                ItemStatistics.Durability unbreaking3 = new ItemStatistics.Durability(1561, 1561, 4.0 / 3.0);
                assertTrue(unbreaking3.effectiveMaximum() > 1561,
                                "Unbreaking III must raise the effective maximum above the raw maximum");
        }

        /**
         * The melee total is base plus enchantment bonus — the
         * enchantment contribution is additive, never a multiplier on
         * the item's base damage.
         */
        @Test
        void meleeDamageTotalIsBasePlusEnchantment() {
                ItemStatistics.MeleeDamage damage = new ItemStatistics.MeleeDamage(
                                8.0, 2.5, 1.6, 0.0, java.util.List.of());
                assertEquals(10.5, damage.total(), 1e-9);
        }

        /**
         * The bow's fully-charged damage is base (arrow × 3 velocity)
         * plus the Power bonus. A bow without Power shows just the base.
         */
        @Test
        void projectileFullyChargedIsBasePlusPower() {
                ItemStatistics.ProjectileStats noPower = new ItemStatistics.ProjectileStats(
                                6.0, 0.0, false, 0, 20);
                assertEquals(6.0, noPower.fullyCharged(), 1e-9);
                ItemStatistics.ProjectileStats powerV = new ItemStatistics.ProjectileStats(
                                6.0, 3.0, false, 0, 20);
                assertEquals(9.0, powerV.fullyCharged(), 1e-9);
        }

        /**
         * Mining speed when correct for drops is base plus Efficiency;
         * the appliesOnlyWhenCorrect flag carries the vanilla rule that
         * Efficiency does not apply to blocks the tool is wrong for.
         */
        @Test
        void miningSpeedCorrectForDropsAddsEfficiency() {
                ItemStatistics.MiningSpeed noEfficiency = new ItemStatistics.MiningSpeed(8.0, 0.0, true);
                assertEquals(8.0, noEfficiency.correctForDrops(), 1e-9);
                ItemStatistics.MiningSpeed efficiencyV = new ItemStatistics.MiningSpeed(8.0, 5.0, true);
                assertEquals(13.0, efficiencyV.correctForDrops(), 1e-9);
                assertTrue(efficiencyV.appliesOnlyWhenCorrect(),
                                "Efficiency must be marked as applying only when correct for drops");
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
                assertTrue(empty.food().isEmpty());
        }
}
