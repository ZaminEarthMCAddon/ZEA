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
                Optional<SpearStats> spear,
                Optional<FoodStats> food) {

        /** An empty statistics set — the item has no RPG-stat-worthy properties. */
        public static ItemStatistics empty() {
                return new ItemStatistics(
                                Optional.empty(), Optional.empty(), Optional.empty(),
                                Optional.empty(), Optional.empty(), Optional.empty(),
                                Optional.empty(), Optional.empty());
        }

        /**
         * The melee damage a weapon deals on a normal hit. Base attack
         * damage plus the unconditional damage enchantments (Sharpness,
         * Smite, Bane of Arthropods — whichever is present, they are
         * mutually exclusive). Conditional-on-target effects are listed
         * separately. {@code bonusLabel} carries the display names of the
         * enchantments that make up {@code enchantmentBonus}, so the
         * presentation can state the split ("8 base + 3 Sharpness V")
         * rather than one opaque total.
         */
        public record MeleeDamage(
                        double baseDamage,
                        double enchantmentBonus,
                        String bonusLabel,
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
         * enchantment's live {@code mining_efficiency} attribute
         * contribution ({@code level squared + 1} in the vanilla data) —
         * Efficiency does not apply to blocks the tool is wrong for, and
         * that is reflected here by the {@code appliesOnlyWhenCorrect}
         * flag.
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
         * Armor stats for a worn piece. {@code armor} is the piece's own
         * armor points; the protection enchantments contribute a
         * <em>conditional</em> reduction that depends on the incoming
         * damage type, so they are listed rather than folded into one
         * misleading percentage. Each entry's value is the piece's
         * enchantment-protection-factor contribution; the reduction this
         * piece alone grants against matching damage is {@code min(20,
         * epf) / 25}, the vanilla combat rule.
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
         * the Unbreaking enchantment yields — the expected number of uses
         * per point of durability consumed, resolved from the
         * enchantment's live data branch for this item's kind (armor and
         * other items refund at different rates).
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
         * Projectile stats for bows and crossbows. Bows present the
         * fully-charged damage; crossbows present their loaded
         * projectile's damage. The damage of a velocity-scaled projectile
         * (an arrow) is {@code ceil(speed x (base + powerBonus))} — the
         * Power enchantment multiplies through the draw velocity, exactly
         * as the arrow's hit code computes it, so a Power V bow at full
         * draw reads 15 and not a flat "6 + 3". A firework rocket damages
         * through its explosion instead ({@code fireworkDamage}), and no
         * arrow-style number is presented for it.
         */
        public record ProjectileStats(
                        double speed,
                        double baseDamage,
                        double powerBonus,
                        int chargeTicks,
                        int projectileCount,
                        int piercing,
                        boolean firework,
                        int fireworkStars) {
                /** Fully-charged arrow damage, base plus Power, scaled by speed. */
                public double fullyCharged() {
                        return Math.ceil(speed * (baseDamage + powerBonus));
                }

                /** A firework rocket's direct-hit damage: 5 plus 2 per star. */
                public double fireworkDamage() {
                        return 5.0 + 2.0 * fireworkStars;
                }
        }

        /**
         * The mace's smash attack, whose bonus damage depends on how far
         * the wielder fell before striking. The vanilla curve is piecewise
         * linear — 4 per fallen block up to 3 blocks, 2 per block from 3
         * to 8, 1 per block beyond — and only applies past the fall
         * threshold; the bands carry those rates so the presentation can
         * state real numbers at real fall distances instead of one
         * misleading fixed damage. {@code densityPerFallenBlock} is the
         * Density enchantment's live per-block rate, added on top of the
         * curve; {@code breach} is Breach's live armor-effectiveness
         * reduction per hit; {@code windBurst} flags the Wind Burst
         * knockback, which is not a damage number.
         */
        public record SmashDamage(
                        double fallThreshold,
                        List<SmashBand> bands,
                        double densityPerFallenBlock,
                        double breach,
                        boolean windBurst) {

                /** One piecewise segment: {@code perBlock} per fallen block, up to {@code upTo}. */
                public record SmashBand(double perBlock, double upTo) {
                }

                /**
                 * The smash bonus damage at {@code fallBlocks} of fall
                 * distance. The curve accumulates over the whole fall — the
                 * fall threshold only decides whether the attack is a smash
                 * at all, so the minimum smash (at the threshold) already
                 * carries the first band's rate down to zero.
                 */
                public double fallBonus(double fallBlocks) {
                        if (fallBlocks <= 0.0) {
                                return 0.0;
                        }
                        double bonus = 0.0;
                        double covered = 0.0;
                        for (SmashBand band : bands) {
                                if (fallBlocks <= band.upTo()) {
                                        bonus += (fallBlocks - covered) * band.perBlock();
                                        return bonus;
                                }
                                bonus += (band.upTo() - covered) * band.perBlock();
                                covered = band.upTo();
                        }
                        // An unbounded final band already returned; a
                        // bounded band list extrapolates at its last rate.
                        bonus += (fallBlocks - covered) * bands.get(bands.size() - 1).perBlock();
                        return bonus;
                }
        }

        /**
         * The spear's own mechanics, on top of the melee damage every
         * weapon carries: the reach window from its {@code AttackRange}
         * component, the charged attack's damage multiplier from its
         * {@code KineticWeapon} component, and the Lunge enchantment's
         * dash strength read live from its impulse effect. The jab's
         * damage and cadence are the ordinary {@link MeleeDamage} numbers
         * — a spear swings like any weapon; what makes it a spear is reach
         * and the charge.
         */
        public record SpearStats(
                        float minReach,
                        float maxReach,
                        double chargeAttackMultiplier,
                        int lungeLevel,
                        double lungeImpulse) {
        }

        /** Food properties: nutrition, saturation, and formatted consume effects. */
        public record FoodStats(
                        int nutrition,
                        float saturation,
                        boolean canAlwaysEat,
                        List<String> effects) {
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
