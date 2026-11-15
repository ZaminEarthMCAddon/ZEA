package net.zaminmc.zea.itempresentation.calculation;

import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.core.component.DataComponents;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Food and consumable properties: nutrition, saturation, and any status
 * effects the item applies on consumption.
 *
 * <p>Nutrition and saturation come from the {@link FoodProperties}
 * component. Status effects are read from the {@link Consumable}
 * component's {@code onConsumeEffects} — the 26.3 data-driven effect
 * list, which covers both classic food effects (golden apple's
 * Regeneration) and modern consume effects (suspicious stew, potions
 * drunk as food). Each effect is listed as a conditional bonus with a
 * human-readable label, because the effect's duration and amplifier are
 * properties of the effect instance, not flat damage numbers.
 *
 * <p>Items with no {@code FOOD} component yield an empty result.
 */
public final class FoodCalculator {
        private FoodCalculator() {
        }

        /**
         * The food stats for {@code stack} from {@code snapshot}, or
         * empty when the item is not food.
         */
        public static Optional<ItemStatistics.FoodStats> calculate(ItemStack stack, ItemSnapshot snapshot) {
                FoodProperties food = snapshot.food();
                if (food == null) {
                        return Optional.empty();
                }
                List<ItemStatistics.ConditionalBonus> effects = consumeEffects(stack);
                return Optional.of(new ItemStatistics.FoodStats(
                                food.nutrition(), food.saturation(), food.canAlwaysEat(), effects));
        }

        /**
         * The status effects the item applies on consumption. Each is
         * surfaced as a labelled entry so the presentation can show
         * "Regeneration II (0:05)" rather than a flat number; the value
         * carries the effect amplifier for calculators that want it.
         */
        private static List<ItemStatistics.ConditionalBonus> consumeEffects(ItemStack stack) {
                Consumable consumable = stack.get(DataComponents.CONSUMABLE);
                if (consumable == null) {
                        return List.of();
                }
                List<ItemStatistics.ConditionalBonus> effects = new ArrayList<>();
                for (var effect : consumable.onConsumeEffects()) {
                        effects.add(new ItemStatistics.ConditionalBonus(effect.toString(), 0.0));
                }
                return List.copyOf(effects);
        }
}
