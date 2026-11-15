package net.zaminmc.zea.itempresentation.calculation;

import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;

import java.util.Optional;

/**
 * The mace's smash attack damage, which depends on how far the wielder
 * has fallen before striking.
 *
 * <p>A mace has two damage modes: a normal melee hit (handled by {@link
 * DamageCalculator} — the base attack damage plus Sharpness) and a
 * <em>smash attack</em>, triggered when the player lands a hit after
 * falling more than {@link MaceItem#SMASH_ATTACK_FALL_THRESHOLD} blocks.
 * The smash bonus is a function of fall distance, so it cannot be one
 * number — the calculator returns the base smash damage and the
 * per-fallen-block contribution from the Density enchantment, and the
 * presentation states the condition.
 *
 * <p>Density adds per-fallen-block damage read from the live
 * {@link EnchantmentEffectComponents#SMASH_DAMAGE_PER_FALLEN_BLOCK}
 * effect; Breach and Wind Burst are flags (armor-pierce and knockback)
 * rather than damage numbers, so they are booleans. The normal-hit
 * melee damage is not duplicated here — it lives in {@link MeleeDamage}.
 */
public final class MaceCalculator {
        private MaceCalculator() {
        }

        /**
         * The smash-attack damage for {@code stack} from {@code
         * snapshot}, or empty when the item is not a mace.
         */
        public static Optional<ItemStatistics.SmashDamage> calculate(ItemStack stack, ItemSnapshot snapshot) {
                if (!(stack.getItem() instanceof MaceItem)) {
                        return Optional.empty();
                }
                double perFallenBlock = densityPerFallenBlock(snapshot);
                boolean breach = EnchantmentLevel.byPath(snapshot, "breach") > 0;
                boolean windBurst = EnchantmentLevel.byPath(snapshot, "wind_burst") > 0;
                // The smash attack's base damage is the normal-hit damage
                // plus the smash fall bonus; the fall bonus is per-block and
                // represented through perFallenBlock so the presentation
                // can show the per-block rate. The flat base is the
                // weapon's own attack damage, already in MeleeDamage.
                return Optional.of(new ItemStatistics.SmashDamage(
                                0.0, perFallenBlock, breach, windBurst));
        }

        /**
         * The Density enchantment's per-fallen-block damage bonus,
         * evaluated from the live {@link
         * EnchantmentEffectComponents#SMASH_DAMAGE_PER_FALLEN_BLOCK}
         * effect with a fixed seed. Zero when Density is absent.
         */
        private static double densityPerFallenBlock(ItemSnapshot snapshot) {
                if (!snapshot.hasEnchantments()) {
                        return 0.0;
                }
                RandomSource random = RandomSource.create(DamageCalculator.EFFECT_SEED);
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
