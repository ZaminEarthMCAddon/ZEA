package net.zaminmc.zea.itempresentation.calculation;

import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.zaminmc.zea.itempresentation.recognition.ItemCategory;
import net.zaminmc.zea.itempresentation.recognition.ItemClassifier;

import java.util.Optional;

/**
 * The single facade the presentation layer asks for an item's
 * statistics. Builds an {@link ItemSnapshot} once and threads it
 * through the calculators, combining their results into one
 * {@link ItemStatistics}.
 *
 * <p>Each calculator decides whether it applies to the item — {@link
 * DamageCalculator} returns empty for a bow, {@link
 * MiningSpeedCalculator} returns empty for a sword — so the engine
 * does not need to know which categories carry which stats. The
 * category is resolved first only to short-circuit {@link
 * ItemCategory#OTHER} (blocks, materials) before any component is
 * read, keeping the hot path — hovering ordinary items — cheap, and to
 * pass the two facts only the category knows: whether Unbreaking's
 * armor data branch applies, and whether the spear-specific
 * calculator should run.
 *
 * <p>The engine is stateless; caching lives at the presentation layer,
 * keyed by the (item, enchantments) pair the snapshot captures. The
 * snapshot is built per hover and reused across calculators, so the
 * same stack is never read twice in one frame.
 */
public final class StatisticsEngine {
        private StatisticsEngine() {
        }

        /**
         * The complete statistics for {@code stack}, or
         * {@link ItemStatistics#empty()} when the item carries no
         * RPG-stat-worthy properties.
         *
         * @param stack the hovered item
         * @param registries the level's registry access, for enchantment
         *        effect resolution; may be {@code null} outside a level,
         *        in which case enchantment contributions are zero
         */
        public static ItemStatistics statisticsOf(ItemStack stack, HolderLookup.Provider registries) {
                if (stack == null || stack.isEmpty()) {
                        return ItemStatistics.empty();
                }
                ItemCategory category = ItemClassifier.classify(stack);
                if (category == ItemCategory.OTHER) {
                        return ItemStatistics.empty();
                }
                ItemSnapshot snapshot = ItemSnapshot.of(stack, registries);
                Optional<ItemStatistics.MeleeDamage> melee = category.isMeleeWeapon()
                                ? DamageCalculator.calculate(stack, snapshot) : Optional.empty();
                Optional<ItemStatistics.MiningSpeed> mining = category.isMiningTool()
                                ? MiningSpeedCalculator.calculate(stack, snapshot) : Optional.empty();
                Optional<ItemStatistics.ArmorStats> armor = category.isArmor()
                                ? ArmorCalculator.calculate(stack, snapshot) : Optional.empty();
                Optional<ItemStatistics.Durability> durability = DurabilityCalculator.calculate(
                                stack, snapshot, category.isArmor());
                Optional<ItemStatistics.ProjectileStats> projectile = category.isRangedWeapon()
                                ? ProjectileCalculator.calculate(stack.getItem(), snapshot) : Optional.empty();
                Optional<ItemStatistics.SmashDamage> smash = category == ItemCategory.MACE
                                ? MaceCalculator.calculate(stack.getItem(), snapshot) : Optional.empty();
                Optional<ItemStatistics.SpearStats> spear = category == ItemCategory.SPEAR
                                ? SpearCalculator.calculate(stack, snapshot) : Optional.empty();
                Optional<ItemStatistics.FoodStats> food = category == ItemCategory.FOOD
                                ? FoodCalculator.calculate(stack, snapshot) : Optional.empty();
                return new ItemStatistics(melee, mining, armor, durability, projectile, smash, spear, food);
        }
}
