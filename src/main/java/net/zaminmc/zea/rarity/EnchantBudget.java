package net.zaminmc.zea.rarity;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What enchantment progression is <em>possible</em> for one item class —
 * the compatible, non-neutral enchantments and the fill value of the best
 * legitimate maxed setup. The maximum is the baseline every item of the
 * same class is measured against, which is what makes an item
 * "hypermaxed" independent of how many enchantments some other item class
 * happens to have.
 *
 * <p>Derived entirely from the 26.3 enchantment registry — which is
 * data-driven in this version, so the enchantments come from the level's
 * own registry access, never a static list:
 * {@link Enchantment#isSupportedItem} decides compatibility and
 * {@link Enchantment#areCompatible} decides conflicts — including the
 * mutual exclusions vanilla ships as {@code exclusive_set} tags. Because
 * compatibility is tag-based, a wooden sword and a netherite sword share
 * the same budget, so equal enchantment setups stay comparable across
 * materials.
 *
 * <p>Budgets are cached per item class and computed once per registry;
 * {@link #invalidateCaches()} clears them when a level (and with it the
 * loaded enchantment set) goes away. The greedy selection below is exact
 * for vanilla's conflict shape (mutually exclusive groups are cliques)
 * and deterministic for anything else.
 */
public final class EnchantBudget {
        /**
         * One compatible, non-neutral enchantment with its maximum
         * legitimate level.
         */
        public record Compatible(Holder<Enchantment> enchantment, int maxLevel) {
                /** The fill this enchantment contributes at {@code level}, capped at 1. */
                public double fillAt(int level) {
                        if (level <= 0) {
                                return 0.0;
                        }
                        return Math.min(1.0, (double) Math.min(level, maxLevel) / maxLevel);
                }

                /** The vanilla display text for {@code level}, e.g. "Sharpness V". */
                public String displayAt(int level) {
                        return Enchantment.getFullname(enchantment, level).getString();
                }
        }

        /** The compatible enchantments, sorted by display name, then the maxed-fill sum. */
        private final List<Compatible> compatible;
        private final double maxFill;

        private static final Map<Item, EnchantBudget> CACHE = new ConcurrentHashMap<>();

        /** The budget used when no enchantment registry is available. */
        private static final EnchantBudget EMPTY = new EnchantBudget(List.of(), 0.0);

        private EnchantBudget(List<Compatible> compatible, double maxFill) {
                this.compatible = compatible;
                this.maxFill = maxFill;
        }

        /**
         * The budget for {@code item}'s class, computed on first use
         * against {@code lookup}'s enchantment registry. The provider must
         * be a live level's; a missing registry ({@code null}) or an
         * unusable one yields an empty budget, which scores every item
         * Common and suggests nothing.
         */
        public static EnchantBudget of(Item item, HolderLookup.Provider lookup) {
                if (lookup == null) {
                        return EMPTY;
                }
                return CACHE.computeIfAbsent(item, key -> compute(key, lookup));
        }

        /** Drops every budget; called when the level that backed them closes. */
        public static void invalidateCaches() {
                CACHE.clear();
        }

        /**
         * The compatible, non-neutral enchantments for this item class,
         * sorted by display name for stable output.
         */
        public List<Compatible> compatible() {
                return compatible;
        }

        /**
         * The fill of the best legitimate fully maxed setup — the number
         * every real enchantment setup is divided by to get its progress.
         */
        public double maxFill() {
                return maxFill;
        }

        private static EnchantBudget compute(Item item, HolderLookup.Provider lookup) {
                ItemStack representative = new ItemStack(item);
                List<Holder<Enchantment>> candidates = new ArrayList<>();
                var enchantments = lookup.lookup(Registries.ENCHANTMENT);
                if (enchantments.isEmpty()) {
                        return EMPTY;
                }
                enchantments.get().listElements().forEach(enchantment -> {
                        Enchantment value = enchantment.value();
                        if (value.getMaxLevel() <= 0
                                        || !value.isSupportedItem(representative)
                                        || EnchantPolicy.isNeutral(enchantment)) {
                                return;
                        }
                        candidates.add(enchantment);
                });
                // Deterministic order: registry key, then display name.
                candidates.sort(Comparator
                                .comparing((Holder<Enchantment> enchantment) -> enchantment
                                                .unwrapKey()
                                                .map(key -> key.identifier().toString())
                                                .orElse(""))
                                .thenComparing(enchantment -> enchantment.value().description().getString()));

                // Greedy optimal selection: take every enchantment that is
                // compatible with everything already selected. Vanilla
                // conflicts are exclusive groups (cliques), where greedy
                // picks exactly one per group — the maximum.
                List<Holder<Enchantment>> selected = new ArrayList<>();
                for (Holder<Enchantment> candidate : candidates) {
                        boolean blocked = selected.stream().anyMatch(
                                        chosen -> !Enchantment.areCompatible(candidate, chosen));
                        if (!blocked) {
                                selected.add(candidate);
                        }
                }
                double maxFill = selected.stream()
                                .mapToDouble(enchantment -> new Compatible(enchantment,
                                                                enchantment.value().getMaxLevel())
                                                .fillAt(enchantment.value().getMaxLevel()))
                                .sum();
                List<Compatible> compatible = candidates.stream()
                                .map(enchantment -> new Compatible(enchantment, enchantment.value().getMaxLevel()))
                                .toList();
                return new EnchantBudget(compatible, maxFill);
        }
}
