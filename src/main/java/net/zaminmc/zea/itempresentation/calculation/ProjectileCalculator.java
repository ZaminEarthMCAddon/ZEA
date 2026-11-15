package net.zaminmc.zea.itempresentation.calculation;

import net.minecraft.core.component.DataComponents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.effects.EnchantmentValueEffect;

import java.util.Optional;

/**
 * Projectile damage for bows and crossbows.
 *
 * <p><b>Bows.</b> An arrow's damage is {@code ceil(speed x base)} on
 * hit, where {@code base} is the arrow's own damage (2.0) plus the
 * Power enchantment's live bonus, and {@code speed} is the arrow's
 * velocity at impact — {@link BowItem#getPowerForTime} caps the draw
 * power at 1.0, which the bow multiplies by its 3.0 launch speed. The
 * Power bonus therefore scales through the draw velocity, exactly as
 * the arrow's hit code computes it: a Power V bow at full draw deals
 * {@code ceil(3 x 5) = 15}, not "6 + 3". Partial draws deal less —
 * the presentation states the full-draw condition rather than letting
 * the number read as universal.
 *
 * <p><b>Crossbows.</b> The damage is the loaded projectile's own: an
 * arrow flies at the crossbow's 3.15 speed ({@code ceil(6.3) = 7}), a
 * firework rocket at 1.6 and damages through its explosion ({@code 5
 * + 2 per firework star}, read from the rocket's own {@code fireworks}
 * component when the crossbow carries {@link ChargedProjectiles}).
 * The reload time is the 1.25 s base with the Quick Charge
 * enchantment's live {@code crossbow_charge_time} effect folded in;
 * Multishot's projectile count and Piercing's depth are read live
 * from their effect components.
 *
 * <p>Every enchantment number here is read from the live effect data
 * — the datapack's rates, not ZEA's restatement of them. Only the
 * bow's 3.0 and the crossbow's 3.15/1.6 launch speeds and the arrow's
 * 2.0 base remain constants: they are vanilla <em>code</em> constants
 * (bytecode-verified for 26.3), not datapack data, so there is no
 * live component to read them from.
 */
public final class ProjectileCalculator {
        /** The vanilla arrow base damage, before the launch speed scales it. */
        static final double ARROW_BASE_DAMAGE = 2.0;
        /** The bow's fully-drawn launch speed (BowItem's power x 3.0). */
        static final double BOW_FULL_CHARGE_SPEED = 3.0;
        /** The crossbow's arrow launch speed. */
        static final double CROSSBOW_ARROW_SPEED = 3.15;
        /** The crossbow's firework rocket launch speed. */
        static final double CROSSBOW_FIREWORK_SPEED = 1.6;
        /** The crossbow's base charge duration in seconds, before Quick Charge. */
        static final double CROSSBOW_BASE_CHARGE_SECONDS = 1.25;
        /** Ticks per second — the unit the charge durations report in. */
        static final double TICKS_PER_SECOND = 20.0;

        private ProjectileCalculator() {
        }

        /**
         * The projectile stats for {@code stack} from {@code snapshot},
         * or empty when the item is neither a bow nor a crossbow.
         */
        public static Optional<ItemStatistics.ProjectileStats> calculate(Item item, ItemSnapshot snapshot) {
                if (item instanceof BowItem) {
                        return Optional.of(bowStats(snapshot));
                }
                if (item instanceof CrossbowItem) {
                        return Optional.of(crossbowStats(snapshot));
                }
                return Optional.empty();
        }

        /**
         * Bow projectile stats: fully-drawn damage with the live Power
         * bonus. Package-private seam — the bow item itself cannot be
         * constructed in a bare test JVM (item construction needs the
         * registry's intrusive holders, bound only during data load),
         * so the stats are tested directly.
         */
        static ItemStatistics.ProjectileStats bowStats(ItemSnapshot snapshot) {
                double powerBonus = powerBonus(snapshot);
                return new ItemStatistics.ProjectileStats(
                                BOW_FULL_CHARGE_SPEED,
                                ARROW_BASE_DAMAGE,
                                powerBonus,
                                BowItem.MAX_DRAW_DURATION,
                                1,
                                0,
                                false,
                                0);
        }

        /**
         * Crossbow projectile stats, resolved from the loaded projectile
         * when the stack carries one and defaulting to the arrow — an
         * unloaded crossbow's stats are the arrow's, because that is what
         * loading it with the usual ammunition produces.
         */
        static ItemStatistics.ProjectileStats crossbowStats(ItemSnapshot snapshot) {
                boolean firework = false;
                int fireworkStars = 0;
                ChargedProjectiles charged = snapshot.chargedProjectiles();
                if (charged != null && !charged.isEmpty()) {
                        // One crossbow holds exactly one projectile entry,
                        // even under Multishot; the copies carry the
                        // projectile's own components (a rocket's stars).
                        Optional<ItemStack> rocket = charged.itemCopies()
                                        .filter(projectile -> projectile.is(net.minecraft.world.item.Items.FIREWORK_ROCKET))
                                        .findFirst();
                        if (rocket.isPresent()) {
                                firework = true;
                                var fireworks = rocket.get().get(DataComponents.FIREWORKS);
                                if (fireworks != null) {
                                        fireworkStars = fireworks.explosions().size();
                                }
                        }
                }
                double speed = firework ? CROSSBOW_FIREWORK_SPEED : CROSSBOW_ARROW_SPEED;
                int projectileCount = 1 + (int) Math.round(
                                EnchantmentEffects.unconditionalValue(snapshot, EnchantmentEffectComponents.PROJECTILE_COUNT));
                int piercing = (int) Math.round(
                                EnchantmentEffects.unconditionalValue(snapshot, EnchantmentEffectComponents.PROJECTILE_PIERCING));
                int chargeTicks = crossbowChargeTicks(snapshot);
                return new ItemStatistics.ProjectileStats(
                                speed, ARROW_BASE_DAMAGE, 0.0, chargeTicks, projectileCount, piercing, firework, fireworkStars);
        }

        /**
         * The live damage bonus the held enchantments add to the arrow —
         * the same effects {@code EnchantmentHelper.modifyDamage} runs
         * against the bow when its arrow lands. In the vanilla data that
         * is Power alone (its condition, the direct attacker being an
         * arrow, holds for every arrow the bow shoots); a command-placed
         * Sharpness would add here too, because the game would apply it
         * there too. Zero when nothing contributes.
         */
        private static double powerBonus(ItemSnapshot snapshot) {
                if (!snapshot.hasEnchantments()) {
                        return 0.0;
                }
                RandomSource random = RandomSource.create(EnchantmentEffects.EFFECT_SEED);
                double bonus = 0.0;
                for (ItemSnapshot.EnchantmentEntry entry : snapshot.enchantmentEntries()) {
                        for (var conditionalEffect : entry.enchantment().value()
                                        .getEffects(EnchantmentEffectComponents.DAMAGE)) {
                                bonus += conditionalEffect.effect()
                                                .process(entry.level(), random, 0.0f);
                        }
                }
                return bonus;
        }

        /**
         * The crossbow's charge duration in ticks: the 1.25 s base with
         * every held Quick Charge enchantment's live {@code
         * crossbow_charge_time} effect applied — the same fold
         * {@code EnchantmentHelper.modifyCrossbowChargingTime} performs,
         * floored to ticks the way {@code CrossbowItem.getChargeDuration}
         * does.
         */
        private static int crossbowChargeTicks(ItemSnapshot snapshot) {
                double seconds = CROSSBOW_BASE_CHARGE_SECONDS;
                if (snapshot.hasEnchantments()) {
                        RandomSource random = RandomSource.create(EnchantmentEffects.EFFECT_SEED);
                        for (ItemSnapshot.EnchantmentEntry entry : snapshot.enchantmentEntries()) {
                                EnchantmentValueEffect chargeTime = entry.enchantment().value()
                                                .effects().get(EnchantmentEffectComponents.CROSSBOW_CHARGE_TIME);
                                if (chargeTime != null) {
                                        seconds = chargeTime.process(entry.level(), random, (float) seconds);
                                }
                        }
                }
                return (int) Math.floor(seconds * TICKS_PER_SECOND);
        }
}
