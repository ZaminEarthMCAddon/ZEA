package net.zaminmc.zea.itempresentation.calculation;

import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;

import java.util.List;
import java.util.Optional;

/**
 * The mace's smash attack damage, which depends on how far the wielder
 * has fallen before striking.
 *
 * <p>A mace has two damage modes: a normal melee hit (handled by
 * {@link DamageCalculator} — the base attack damage plus Sharpness) and
 * a <em>smash attack</em>, triggered when the player lands a hit after
 * falling more than {@link MaceItem#SMASH_ATTACK_FALL_THRESHOLD} blocks
 * (1.5 in the 26.3 data). The smash bonus is a piecewise-linear curve
 * over fall distance — 4 per fallen block up to 3 blocks, 2 per block
 * from 3 to 8, 1 per block beyond — read straight from the 26.3
 * bytecode of {@code MaceItem.getAttackDamageBonus}, so the tooltip
 * states real numbers at real fall distances instead of one misleading
 * fixed damage. A minimum smash at 1.5 blocks adds 6; falling 8 blocks
 * adds 22; the 20-block dive adds 34.
 *
 * <p>Density adds per-fallen-block damage on top of the curve, read
 * live from the {@link
 * EnchantmentEffectComponents#SMASH_DAMAGE_PER_FALLEN_BLOCK} effect
 * (the vanilla data grants {@code 0.5 x level} per block). Breach's
 * armor-piercing is its live {@code armor_effectiveness} effect (the
 * dedicated enchantment component, {@code -0.15 x level} in the
 * vanilla data); Wind Burst is a knockback, not a damage number, so it
 * stays a flag. The normal-hit melee damage is not duplicated here —
 * it lives in {@link ItemStatistics.MeleeDamage}.
 */
public final class MaceCalculator {
        /** The smash curve's first band: 4 damage per block, up to 3 blocks. */
        static final double FIRST_BAND_PER_BLOCK = 4.0;
        /** The smash curve's second band: 2 damage per block, from 3 to 8 blocks. */
        static final double SECOND_BAND_PER_BLOCK = 2.0;
        /** The smash curve's final band: 1 damage per block beyond 8. */
        static final double FINAL_BAND_PER_BLOCK = 1.0;
        /** Where the second band ends and the final rate takes over. */
        static final double FINAL_BAND_START = 8.0;
        /** Where the first band ends. */
        static final double FIRST_BAND_END = 3.0;

        private MaceCalculator() {
        }

        /**
         * The smash-attack statistics for {@code stack} from {@code
         * snapshot}, or empty when the item is not a mace.
         */
        public static Optional<ItemStatistics.SmashDamage> calculate(Item item, ItemSnapshot snapshot) {
                if (!(item instanceof MaceItem)) {
                        return Optional.empty();
                }
                double perFallenBlock = densityPerFallenBlock(snapshot);
                double breach = breachArmorEffectiveness(snapshot);
                boolean windBurst = EnchantmentLevel.byPath(snapshot, "wind_burst") > 0;
                return Optional.of(new ItemStatistics.SmashDamage(
                                MaceItem.SMASH_ATTACK_FALL_THRESHOLD,
                                List.of(
                                new ItemStatistics.SmashDamage.SmashBand(FIRST_BAND_PER_BLOCK, FIRST_BAND_END),
                                new ItemStatistics.SmashDamage.SmashBand(SECOND_BAND_PER_BLOCK, FINAL_BAND_START),
                                new ItemStatistics.SmashDamage.SmashBand(FINAL_BAND_PER_BLOCK,
                                                Double.POSITIVE_INFINITY)),
                                perFallenBlock,
                                breach,
                                windBurst));
        }

        /**
         * Breach's live armor-effectiveness reduction — the dedicated
         * {@code armor_effectiveness} enchantment effect, not the
         * generic attribute path. The vanilla data writes
         * {@code -0.15 x level}. Zero when Breach is absent.
         */
        static double breachArmorEffectiveness(ItemSnapshot snapshot) {
                if (!snapshot.hasEnchantments()) {
                        return 0.0;
                }
                RandomSource random = RandomSource.create(EnchantmentEffects.EFFECT_SEED);
                double total = 0.0;
                for (ItemSnapshot.EnchantmentEntry entry : snapshot.enchantmentEntries()) {
                        for (var conditionalEffect : entry.enchantment().value()
                                        .getEffects(EnchantmentEffectComponents.ARMOR_EFFECTIVENESS)) {
                                if (conditionalEffect.requirements().isEmpty()) {
                                        total += conditionalEffect.effect()
                                                        .process(entry.level(), random, 0.0f);
                                }
                        }
                }
                return total;
        }

        /**
         * The Density enchantment's per-fallen-block damage bonus,
         * evaluated from the live {@link
         * EnchantmentEffectComponents#SMASH_DAMAGE_PER_FALLEN_BLOCK}
         * effect with a fixed seed. Zero when Density is absent.
         */
        static double densityPerFallenBlock(ItemSnapshot snapshot) {
                if (!snapshot.hasEnchantments()) {
                        return 0.0;
                }
                RandomSource random = RandomSource.create(EnchantmentEffects.EFFECT_SEED);
                double total = 0.0;
                for (ItemSnapshot.EnchantmentEntry entry : snapshot.enchantmentEntries()) {
                        var effects = entry.enchantment().value()
                                        .getEffects(EnchantmentEffectComponents.SMASH_DAMAGE_PER_FALLEN_BLOCK);
                        for (var conditionalEffect : effects) {
                                if (conditionalEffect.requirements().isEmpty()) {
                                        total += conditionalEffect.effect()
                                                        .process(entry.level(), random, 0.0f);
                                }
                        }
                }
                return total;
        }
}
