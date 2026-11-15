package net.zaminmc.zea.itempresentation.calculation;

import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

/**
 * Projectile damage for bows and crossbows.
 *
 * <p><b>Bows.</b> The arrow's damage is draw-dependent: a partially
 * charged shot does less than a fully charged one. The calculator
 * returns the fully-charged damage as the representative value (the
 * number a player planning a fight cares about) and exposes the draw
 * ticks so the presentation can state the condition. The vanilla arrow
 * base is {@code 2.0}; at full charge the velocity multiplier triples
 * it to {@code 6.0}, and the Power enchantment adds {@code level * 0.5
 * + 0.5} on top — read from the live enchantment level, never a
 * hardcoded formula.
 *
 * <p><b>Crossbows.</b> Damage is the projectile's own value, not
 * draw-dependent — a crossbow is either loaded or not. {@code
 * multishot} and {@code piercing} are flags because their effect is
 * projectile-count and pierce-depth, not a flat damage number, and
 * presenting them as damage would mislead.
 *
 * <p>The draw-dependent nature of bow damage is the reason this
 * calculator returns a single representative number with the condition
 * attached, not a "damage: X" line that reads as universal. A bow that
 * is not fully drawn does less.
 */
public final class ProjectileCalculator {
        /** The vanilla arrow base damage, before the draw multiplier. */
        static final double ARROW_BASE_DAMAGE = 2.0;
        /** The draw-power-to-velocity multiplier vanilla applies at full charge. */
        static final double FULL_CHARGE_VELOCITY = 3.0;
        /** The Power enchantment's per-level damage, plus its base bonus. */
        static final double POWER_PER_LEVEL = 0.5;
        static final double POWER_BASE = 0.5;
        /** Bow full-charge draw duration in ticks ({@link BowItem#MAX_DRAW_DURATION}). */
        static final int BOW_FULL_CHARGE_TICKS = 20;

        private ProjectileCalculator() {
        }

        /**
         * The projectile stats for {@code stack} from {@code snapshot},
         * or empty when the item is neither a bow nor a crossbow.
         */
        public static Optional<ItemStatistics.ProjectileStats> calculate(ItemStack stack, ItemSnapshot snapshot) {
                if (stack.getItem() instanceof BowItem) {
                        return Optional.of(bowStats(snapshot));
                }
                if (stack.getItem() instanceof CrossbowItem) {
                        return Optional.of(crossbowStats(snapshot));
                }
                return Optional.empty();
        }

        /** Bow projectile stats: fully-charged damage with Power bonus. */
        private static ItemStatistics.ProjectileStats bowStats(ItemSnapshot snapshot) {
                double base = ARROW_BASE_DAMAGE * FULL_CHARGE_VELOCITY;
                int power = EnchantmentLevel.power(snapshot);
                double powerBonus = power > 0 ? power * POWER_PER_LEVEL + POWER_BASE : 0.0;
                return new ItemStatistics.ProjectileStats(
                                base, powerBonus, false, 0, BOW_FULL_CHARGE_TICKS);
        }

        /** Crossbow projectile stats. The base damage is the arrow's
         * projectile value (6.0 for a vanilla arrow); multishot and
         * piercing are resolved from the snapshot's enchantments as
         * flags and pierce depth. Power does not apply to crossbows. */
        private static ItemStatistics.ProjectileStats crossbowStats(ItemSnapshot snapshot) {
                int piercing = EnchantmentLevel.byPath(snapshot, "piercing");
                boolean multishot = EnchantmentLevel.byPath(snapshot, "multishot") > 0;
                return new ItemStatistics.ProjectileStats(
                                6.0, 0.0, multishot, piercing, 25);
        }
}
