package net.zaminmc.zea.itempresentation.calculation;

import net.minecraft.core.Holder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.component.AttackRange;
import net.minecraft.world.item.component.KineticWeapon;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.effects.AllOf;
import net.minecraft.world.item.enchantment.effects.ApplyEntityImpulse;

import java.util.Optional;

/**
 * The spear's own mechanics, on the components 26.3 actually ships.
 *
 * <p>Spears are fully data-driven: a plain item carrying a {@link
 * AttackRange} component (a window from {@code minReach} to {@code
 * maxReach} — 2.0 to 4.5 blocks on every vanilla spear, with a wider
 * window in creative), a {@link KineticWeapon} component (the charged
 * attack: hold use to lunge, dealing normal weapon damage times
 * {@code damageMultiplier} — 0.7 on a wooden spear up to 1.2 on
 * netherite), and a {@code PiercingWeapon} component (the jab: the
 * STAB left-click, which pierces through the entities in its path).
 * The jab's damage and cadence are the ordinary attack attributes —
 * {@link DamageCalculator} carries them — so this calculator resolves
 * only what is spear-specific: the reach window, the charge
 * multiplier, and the Lunge enchantment.
 *
 * <p>A spear's {@code minimum_attack_charge} is 1.0 in the vanilla
 * data — the jab is only released at a fully recovered attack
 * strength — which the attack-speed line already communicates as
 * cadence, so it is not repeated here.
 *
 * <p>Lunge replaces the jab with a forward dash while held: its level
 * is read by path, and its dash strength is read live from the
 * enchantment's {@code post_piercing_attack} impulse effect (the
 * vanilla data scales {@code 0.458 x level}). The dash's own costs —
 * exhaustion and a point of durability per lunge — belong to the
 * enchantment's effect list, not to a weapon statistic.
 */
public final class SpearCalculator {

        private SpearCalculator() {
        }

        /**
         * The spear-specific statistics for {@code stack} from {@code
         * snapshot}, or empty when the item carries none of the spear
         * components and no Lunge — the classifier's SPEAR category
         * decides whether to ask.
         */
        public static Optional<ItemStatistics.SpearStats> calculate(ItemStack stack, ItemSnapshot snapshot) {
                Float minReach = null;
                Float maxReach = null;
                AttackRange attackRange = snapshot.attackRange();
                if (attackRange != null) {
                        minReach = attackRange.minReach();
                        maxReach = attackRange.maxReach();
                }
                Double chargeMultiplier = null;
                KineticWeapon kinetic = snapshot.kineticWeapon();
                if (kinetic != null) {
                        chargeMultiplier = (double) kinetic.damageMultiplier();
                }
                int lunge = EnchantmentLevel.lunge(snapshot);
                ItemSnapshot.EnchantmentEntry lungeEntry = EnchantmentEffects.byPath(snapshot, "lunge");
                double lungeImpulse = lunge > 0 && lungeEntry != null
                                ? lungeImpulse(lungeEntry.enchantment(), lunge) : 0.0;
                if (minReach == null && chargeMultiplier == null && lunge == 0) {
                        return Optional.empty();
                }
                return Optional.of(new ItemStatistics.SpearStats(
                                minReach != null ? minReach : 0.0f,
                                maxReach != null ? maxReach : 0.0f,
                                chargeMultiplier != null ? chargeMultiplier : 1.0,
                                lunge,
                                lungeImpulse));
        }

        /**
         * The Lunge enchantment's live dash strength at {@code level}:
         * the magnitude of the impulse effect inside its {@code
         * post_piercing_attack} entry. The vanilla data wraps the
         * effect in an {@code all_of} (dash, exhaustion, durability,
         * sound); the impulse is the only numeric weapon statistic in
         * it. Zero when the effect tree carries no impulse.
         */
        static double lungeImpulse(Holder<Enchantment> lunge, int level) {
                for (var conditionalEffect : lunge.value()
                                .getEffects(EnchantmentEffectComponents.POST_PIERCING_ATTACK)) {
                        double magnitude = impulseWithin(conditionalEffect.effect(), level);
                        if (magnitude > 0.0) {
                                return magnitude;
                        }
                }
                return 0.0;
        }

        /** The impulse magnitude inside one effect, unwrapping {@code all_of}. */
        private static double impulseWithin(Object effect, int level) {
                if (effect instanceof ApplyEntityImpulse impulse) {
                        return impulse.magnitude().calculate(level);
                }
                if (effect instanceof AllOf.EntityEffects allOf) {
                        for (var nested : allOf.effects()) {
                                double magnitude = impulseWithin(nested, level);
                                if (magnitude > 0.0) {
                                        return magnitude;
                                }
                        }
                }
                return 0.0;
        }
}
