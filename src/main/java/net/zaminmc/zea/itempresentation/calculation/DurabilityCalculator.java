package net.zaminmc.zea.itempresentation.calculation;

import net.minecraft.world.item.ItemStack;

import java.util.Optional;

/**
 * Durability: the current damage value against the maximum, with the
 * Unbreaking enchantment's effective-durability multiplier factored in.
 *
 * <p>Unbreaking in 26.3 negates a fraction of durability consumption:
 * at level {@code L}, each point of durability consumed has a
 * {@code 100 / (L + 1)}% chance (rounded down to the nearest whole
 * percent) to be refunded. The expected durability per consumed point is
 * therefore {@code 1 / (1 - p)}, and the effective maximum is
 * {@code maximum * (expected per point)} — the average lifetime the
 * item reaches, which is the number a player planning tool repairs
 * against.
 *
 * <p>The calculator returns the unbreaking factor (the multiplier on
 * maximum) rather than a pre-multiplied number, so the presentation can
 * show both the raw maximum and the expected lifetime without losing the
 * raw value. An item with no {@code max_damage} component (blocks,
 * materials) yields an empty result.
 */
public final class DurabilityCalculator {
        private DurabilityCalculator() {
        }

        /**
         * The durability stats for {@code stack} from {@code snapshot},
         * or empty when the item is not damageable.
         */
        public static Optional<ItemStatistics.Durability> calculate(ItemStack stack, ItemSnapshot snapshot) {
                if (snapshot.maxDamage() <= 0) {
                        return Optional.empty();
                }
                int maximum = snapshot.maxDamage();
                int current = Math.max(0, maximum - snapshot.damage());
                int unbreaking = EnchantmentLevel.unbreaking(snapshot);
                double factor = unbreakingFactor(unbreaking);
                return Optional.of(new ItemStatistics.Durability(current, maximum, factor));
        }

        /**
         * The expected-durability multiplier Unbreaking level {@code L}
         * yields. Vanilla's refund chance is {@code 100 / (L + 1)}% for
         * most items; the multiplier is the inverse of the kept
         * fraction.
         */
        static double unbreakingFactor(int level) {
                if (level <= 0) {
                        return 1.0;
                }
                double refundChance = 1.0 / (level + 1);
                return 1.0 / (1.0 - refundChance);
        }
}
