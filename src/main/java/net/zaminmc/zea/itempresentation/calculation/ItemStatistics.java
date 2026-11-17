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
 * <p>Wherever an enchantment contributes a numeric bonus, the
 * contributing enchantment's <em>level</em> rides along in its own
 * field ({@code bonusLevel}, {@code efficiencyLevel}, …) so the
 * presentation can render the compact bracket indicator — "Damage:
 * +12 [5]" — without re-deriving which enchantment was responsible.
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
                Optional<Integer> magicFind) {

        /** An empty statistics set — the item has no RPG-stat-worthy properties. */
        public static ItemStatistics empty() {
                return new ItemStatistics(
                                Optional.empty(), Optional.empty(), Optional.empty(),
                                Optional.empty(), Optional.empty(), Optional.empty(),
                                Optional.empty(), Optional.empty());
        }

        /**
         * The melee damage a weapon deals on a normal hit, split by
         * source so the tooltip can annotate what each contributor
         * adds. {@code itemDamage} is the item's intrinsic attack-damage
         * contribution (the diamond sword's +6) — never the player's
         * base, which is the wielder's, not the item's. {@code
         * enchantmentBonus} is the unconditional damage enchantments
         * (Sharpness — Smite and Bane of Arthropods are
         * conditional-on-target and listed separately);
         * {@code bonusLabel} names the enchantment and {@code
         * bonusLevel} carries its level for the compact bracket
         * indicator. {@code playerDamage} is the wielder's base attack
         * damage, populated only when the hovered stack is the stack
         * actually held, and {@code effectBonus} is the active
         * attack-damage effects (Strength) under the same condition,
         * with {@code effectLabel} naming the effect behind it. Both
         * wielder fields are zero for an item nobody is holding — a
         * chest slot's spare sword does not swing.
         */
        public record MeleeDamage(
                        double itemDamage,
                        double enchantmentBonus,
                        String bonusLabel,
                        int bonusLevel,
                        double playerDamage,
                        double effectBonus,
                        String effectLabel,
                        double attackSpeed,
                        double sweepingRatio,
                        List<ConditionalBonus> conditional) {
                /**
                 * The total damage on a normal hit: the item, its
                 * enchantments, and — when held — the wielder's base and
                 * active effects.
                 */
                public double total() {
                        return itemDamage + enchantmentBonus + playerDamage + effectBonus;
                }
        }

        /**
         * The mining speed against a representative block the tool is
         * correct for. {@code baseSpeed} is the tool's own speed; {@code
         * efficiencyBonus} is the Efficiency enchantment's live {@code
         * mining_efficiency} attribute contribution ({@code level
         * squared + 1} in the vanilla data) with its level in {@code
         * efficiencyLevel}. Both numbers describe blocks the tool is
         * correct for — Efficiency does not apply to blocks the tool is
         * wrong for, which {@code appliesOnlyWhenCorrect} records.
         */
        public record MiningSpeed(
                        double baseSpeed,
                        double efficiencyBonus,
                        int efficiencyLevel,
                        boolean appliesOnlyWhenCorrect) {
                /** Mining speed when the tool is correct for the block. */
                public double correctForDrops() {
                        return baseSpeed + efficiencyBonus;
                }
        }

        /**
         * Armor stats for a worn piece, on the RPG defense scale.
         * {@code defense} is the piece's armor points times ten plus
         * ten per level of generic Protection — the Hypixel-style
         * 10× interpretation, a presentation convention that leaves the
         * vanilla armor points themselves untouched. {@code
         * protectionLevel} is the actual Protection level behind that
         * bonus (the bracket indicator; zero when the piece carries
         * none, never clamped). The specialized protection enchantments
         * are <em>not</em> folded into defense: each states the damage
         * type it guards and is listed as a {@link ConditionalBonus}
         * whose value is the piece's enchantment-protection-factor
         * contribution, read live from the effect data; the reduction
         * this piece alone grants against matching damage is {@code
         * min(20, epf) / 25}, the vanilla combat rule.
         */
        public record ArmorStats(
                        double defense,
                        double toughness,
                        double knockbackResistance,
                        int protectionLevel,
                        List<ConditionalBonus> specializedProtections) {
        }

        /**
         * Durability: the current damage value against the maximum, and
         * nothing else. Unbreaking is deliberately not folded in — the
         * tooltip states the real maximum, and Unbreaking presents as
         * the enchantment it is.
         */
        public record Durability(
                        int current,
                        int maximum) {
        }

        /**
         * Projectile stats for bows, crossbows and tridents. Bows and
         * crossbows present the fully-charged arrow damage; a trident
         * presents its thrown damage. An arrow's damage is {@code
         * ceil(speed x (base + powerBonus))} — the Power enchantment
         * multiplies through the draw velocity, exactly as the arrow's
         * hit code computes it, so a Power V bow at full draw reads 15
         * and not a flat "6 + 3"; {@code powerLevel} carries Power's
         * level for the bracket indicator. A trident's thrown damage is
         * <em>not</em> velocity-scaled — it is {@code base + powerBonus}
         * flat — so {@code velocityScaled} records which formula
         * applies. A firework rocket damages through its explosion
         * instead ({@code fireworkDamage}), and no arrow-style number is
         * presented for it.
         */
        public record ProjectileStats(
                        double speed,
                        double baseDamage,
                        double powerBonus,
                        int powerLevel,
                        int chargeTicks,
                        int projectileCount,
                        int piercing,
                        boolean firework,
                        int fireworkStars,
                        boolean velocityScaled) {
                /** Fully-charged arrow damage, base plus Power, scaled by speed. */
                public double fullyCharged() {
                        return Math.ceil(speed * (baseDamage + powerBonus));
                }

                /** Flat projectile damage for a non-velocity-scaled throw (the trident). */
                public double flat() {
                        return baseDamage + powerBonus;
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
         * threshold; the bands carry those rates so the damage at any
         * real fall distance is computable. {@code densityPerFallenBlock}
         * is the Density enchantment's live per-block rate (level in
         * {@code densityLevel}); {@code breach} is Breach's live
         * armor-effectiveness reduction per hit (level in {@code
         * breachLevel}); {@code windBurst} flags the Wind Burst
         * knockback, which is not a damage number.
         */
        public record SmashDamage(
                        double fallThreshold,
                        List<SmashBand> bands,
                        double densityPerFallenBlock,
                        int densityLevel,
                        double breach,
                        int breachLevel,
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

        /**
         * A bonus that applies only under a condition the tooltip must
         * state — e.g. "Sharpness V vs undead", "Density III per fallen
         * block", or a specialized protection's damage type. The label
         * carries the condition (or the mapped stat name) in human
         * terms; the value is the numeric contribution when the
         * condition holds.
         */
        public record ConditionalBonus(String label, double value) {
        }
}
