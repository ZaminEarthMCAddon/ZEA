package net.zaminmc.zea.itempresentation.calculation;

import java.util.List;
import java.util.Optional;

/**
 * The complete, calculator-produced statistics for one item — the
 * bridge between the calculation engine and the presentation layer.
 *
 * <p>Every field is the <em>resolved</em> value, already combining the
 * item's base attributes with the contributions of its enchantments
 * where the enchantment effect is unconditional and computable without
 * a target. Target-dependent values (damage against a specific mob,
 * protection against a specific damage type) are represented as
 * conditional entries rather than fixed numbers, so the tooltip never
 * lies by presenting a single conditional number as universal.
 *
 * <p>Constructed by the calculators and consumed read-only by the
 * presentation model. Empty optionals mean the statistic does not apply
 * to this item (a bow has no melee attack speed worth showing), never
 * that the value is zero.
 */
public record ItemStatistics(
                Optional<MeleeDamage> meleeDamage,
                Optional<MiningSpeed> miningSpeed,
                Optional<ArmorStats> armor,
                Optional<Durability> durability,
                Optional<ProjectileStats> projectile,
                Optional<SmashDamage> smashDamage,
                Optional<FoodStats> food) {

        /** An empty statistics set — the item has no RPG-stat-worthy properties. */
        public static ItemStatistics empty() {
                return new ItemStatistics(
                                Optional.empty(), Optional.empty(), Optional.empty(),
                                Optional.empty(), Optional.empty(), Optional.empty(),
                                Optional.empty());
        }

        /**
         * The melee damage a weapon deals on a normal hit. Base attack
         * damage plus the unconditional damage enchantments (Sharpness,
         * Smite, Bane of Arthropods — whichever is present, they are
         * mutually exclusive). Conditional-on-target effects are listed
         * separately.
         */
        public record MeleeDamage(
                        double baseDamage,
                        double enchantmentBonus,
                        double attackSpeed,
                        double sweepingRatio,
                        List<ConditionalBonus> conditional) {
                /** The total damage on a normal hit, base plus enchantment. */
                public double total() {
                        return baseDamage + enchantmentBonus;
                }
        }

        /**
         * The mining speed against a representative block the tool is
         * correct for. {@code efficiencyBonus} is the Efficiency
         * enchantment's contribution when present and the tool is
         * correct for drops — Efficiency does not apply to blocks the
         * tool is wrong for, and that is reflected here by the
         * {@code appliesOnlyWhenCorrect} flag.
         */
        public record MiningSpeed(
                        double baseSpeed,
                        double efficiencyBonus,
                        boolean appliesOnlyWhenCorrect) {
                /** Mining speed when the tool is correct for the block. */
                public double correctForDrops() {
                        return baseSpeed + efficiencyBonus;
                }
        }

        /**
         * Armor stats for a worn piece. {@code protection} is the
         * piece's own armor points; the protection enchantments
         * contribute a <em>conditional</em> reduction that depends on
         * the incoming damage type, so they are listed rather than
         * folded into one misleading percentage.
         */
        public record ArmorStats(
                        double armor,
                        double toughness,
                        double knockbackResistance,
                        List<ConditionalBonus> protectionEnchantments) {
        }

        /**
         * Durability: the current damage value against the maximum.
         * {@code unbreakingFactor} is the effective durability multiplier
         * the Unbreaking enchantment yields — the expected number of
         * uses per point of durability consumed, averaged over the
         * enchantment's chance to negate consumption.
         */
        public record Durability(
                        int current,
                        int maximum,
                        double unbreakingFactor) {
                /** The effective maximum durability with Unbreaking factored in. */
                public int effectiveMaximum() {
                        return maximum + (int) Math.round(maximum * (unbreakingFactor - 1.0));
                }
        }

        /**
         * Projectile stats for bows and crossbows. Bows show the
         * fully-charged damage; crossbows show their projectile damage.
         * {@code powerBonus} is the Power enchantment's contribution at
         * full charge; {@code multishot}/{@code piercing} are flags
         * because their effect is multiplicative/projectile-count, not a
         * flat damage number.
         */
        public record ProjectileStats(
                        double baseDamage,
                        double powerBonus,
                        boolean multishot,
                        int piercing,
                        int drawTicks) {
                /** Fully-charged projectile damage, base plus Power. */
                public double fullyCharged() {
                        return baseDamage + powerBonus;
                }
        }

        /**
         * The mace's smash attack damage, which depends on fall
         * distance. {@code perFallenBlock} is the Density enchantment's
         * per-block contribution; {@code breach} and {@code windBurst}
         * are flags for the armor-pierce and knockback effects. The
         * normal-hit melee damage lives in {@link MeleeDamage}.
         */
        public record SmashDamage(
                        double baseSmashDamage,
                        double perFallenBlock,
                        boolean breach,
                        boolean windBurst) {
        }

        /** Food properties: nutrition, saturation, and any consume effects. */
        public record FoodStats(
                        int nutrition,
                        float saturation,
                        boolean canAlwaysEat,
                        List<ConditionalBonus> effects) {
        }

        /**
         * A bonus that applies only under a condition the tooltip must
         * state — e.g. "Sharpness V vs undead", "Density III per fallen
         * block", "Protection IV vs fire". The label carries the
         * condition in human terms; the value is the numeric
         * contribution when the condition holds.
         */
        public record ConditionalBonus(String label, double value) {
        }
}
