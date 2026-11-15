package net.zaminmc.zea.itempresentation.calculation;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.effects.RemoveBinomial;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Durability: the current damage value against the maximum, with the
 * Unbreaking enchantment's effective-durability multiplier factored in.
 *
 * <p>Unbreaking in 26.3 is data-driven: the enchantment carries
 * {@code minecraft:item_damage} {@code remove_binomial} effects, and
 * the vanilla data ships <em>two</em> of them — one that applies to
 * armor ({@code #minecraft:enchantable/armor} tools) and one to
 * everything else. At level 3 the armor branch refunds {@code 6/20}
 * of each durability point (an expected lifetime of {@code x1.43})
 * while the tool branch refunds {@code 3/4} ({@code x4}). One number
 * for both was the old behaviour and it was wrong for both kinds.
 *
 * <p>The refund chances are read live from the enchantment's effect
 * data ({@link RemoveBinomial#chance()} evaluated at the held level);
 * only the branch <em>selection</em> is ZEA's, because the branch
 * condition is a loot predicate a tooltip cannot evaluate. The two
 * vanilla branches are told apart by their refund chance — the armor
 * branch refunds strictly less at every level in the 26.3 data
 * ({@code 2L/(5L+5) < L/(L+1)} for all L >= 1) — so the calculator
 * picks the smaller chance for armor and the larger for anything
 * else, and a single-branch (modded) Unbreaking is used directly. A
 * datapack that inverts the vanilla relationship would show the other
 * kind's number; recorded as a limitation, not papered over.
 *
 * <p>The calculator returns the multiplier rather than a pre-multiplied
 * number, so the presentation can show both the raw maximum and the
 * expected lifetime without losing the raw value. An item with no
 * {@code max_damage} component (blocks, materials) yields an empty
 * result.
 */
public final class DurabilityCalculator {

        private DurabilityCalculator() {
        }

        /**
         * The durability stats for {@code stack} from {@code snapshot},
         * or empty when the item is not damageable. {@code armor}
         * selects Unbreaking's data branch — the item's category knows
         * which kind of thing it is even though the loot predicate
         * cannot be evaluated here.
         */
        public static Optional<ItemStatistics.Durability> calculate(
                        ItemStack stack, ItemSnapshot snapshot, boolean armor) {
                if (snapshot.maxDamage() <= 0) {
                        return Optional.empty();
                }
                int maximum = snapshot.maxDamage();
                int current = Math.max(0, maximum - snapshot.damage());
                double factor = unbreakingFactor(snapshot, armor);
                return Optional.of(new ItemStatistics.Durability(current, maximum, factor));
        }

        /**
         * The expected-durability multiplier the snapshot's enchantments
         * grant, resolved from the live refund chances of every {@code
         * item_damage} {@code remove_binomial} effect they carry — in
         * the vanilla data, Unbreaking's two branches alone. Absent
         * effects are 1.0.
         */
        static double unbreakingFactor(ItemSnapshot snapshot, boolean armor) {
                return factorFromChance(pickChance(refundChances(snapshot), armor));
        }

        /**
         * The branch-selection seam: which refund chance applies to this
         * kind of item. No chances means no Unbreaking data (1.0); one
         * chance is used directly (a modded single-branch Unbreaking);
         * with the vanilla pair the armor branch is the one that
         * refunds less at every level, so armor takes the smaller
         * chance and anything else the larger. See the class javadoc.
         */
        static double pickChance(List<Double> chances, boolean armor) {
                if (chances.isEmpty()) {
                        return 0.0;
                }
                if (chances.size() == 1) {
                        return chances.get(0);
                }
                double picked = chances.get(0);
                for (double candidate : chances) {
                        if (armor ? candidate < picked : candidate > picked) {
                                picked = candidate;
                        }
                }
                return picked;
        }

        /**
         * The pure expected-durability multiplier of a refund chance:
         * each incoming durability point is kept with probability
         * {@code 1 - chance}, so the expected lifetime scales by the
         * inverse of that.
         */
        static double factorFromChance(double refundChance) {
                if (refundChance <= 0.0) {
                        return 1.0;
                }
                if (refundChance >= 1.0) {
                        return Double.POSITIVE_INFINITY;
                }
                return 1.0 / (1.0 - refundChance);
        }

        /**
         * The live refund chances of every {@code remove_binomial}
         * {@code item_damage} effect across the snapshot's enchantments,
         * each evaluated at its held level.
         */
        private static List<Double> refundChances(ItemSnapshot snapshot) {
                if (!snapshot.hasEnchantments()) {
                        return List.of();
                }
                List<Double> chances = new ArrayList<>();
                for (ItemSnapshot.EnchantmentEntry entry : snapshot.enchantmentEntries()) {
                        for (var conditionalEffect : entry.enchantment().value()
                                        .getEffects(EnchantmentEffectComponents.ITEM_DAMAGE)) {
                                if (conditionalEffect.effect() instanceof RemoveBinomial removeBinomial) {
                                        chances.add((double) removeBinomial.chance().calculate(entry.level()));
                                }
                        }
                }
                return chances;
        }
}
