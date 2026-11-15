package net.zaminmc.zea.itempresentation.calculation;

import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;
import net.minecraft.world.item.consume_effects.ConsumeEffect;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Food and consumable properties: nutrition, saturation, and the status
 * effects the item applies on consumption.
 *
 * <p>Nutrition and saturation come from the {@link FoodProperties}
 * component. Status effects are read from the {@code Consumable}
 * component's {@code onConsumeEffects} — the 26.3 data-driven effect
 * list, which covers both classic food effects (golden apple's
 * Regeneration) and modern consume effects. Each {@code
 * apply_status_effects} entry is formatted the way the game itself
 * presents effects — name, amplifier as a roman numeral, duration as
 * {@code m:ss} — with the entry's probability appended when it is not
 * certain. Non-effect consume entries (sounds) are not statistics and
 * are skipped.
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
                List<String> effects = consumeEffects(snapshot);
                return Optional.of(new ItemStatistics.FoodStats(
                                food.nutrition(), food.saturation(), food.canAlwaysEat(), effects));
        }

        /**
         * The formatted status-effect labels for the snapshot's
         * consumable component, or an empty list when there is nothing
         * to show.
         */
        private static List<String> consumeEffects(ItemSnapshot snapshot) {
                var consumable = snapshot.consumable();
                if (consumable == null) {
                        return List.of();
                }
                List<String> effects = new ArrayList<>();
                for (ConsumeEffect effect : consumable.onConsumeEffects()) {
                        if (effect instanceof ApplyStatusEffectsConsumeEffect applyStatusEffects) {
                                for (MobEffectInstance instance : applyStatusEffects.effects()) {
                                        effects.add(effectLabel(instance, applyStatusEffects.probability()));
                                }
                        }
                }
                return List.copyOf(effects);
        }

        /**
         * One effect's human label: the effect's translated name, the
         * amplifier as a roman numeral (level II means amplifier 1),
         * the duration as {@code m:ss}, and the chance when it is not
         * certain. The name is a translatable component resolved by
         * the client's language at render time, exactly like the
         * enchantment names.
         */
        static String effectLabel(MobEffectInstance instance, float probability) {
                StringBuilder label = new StringBuilder();
                label.append(Component.translatable(
                                instance.getEffect().value().getDescriptionId()).getString());
                if (instance.getAmplifier() > 0) {
                        label.append(' ').append(roman(instance.getAmplifier() + 1));
                }
                label.append(" (").append(duration(instance.getDuration())).append(')');
                if (probability < 1.0f) {
                        label.append(" — ").append(Math.round(probability * 100.0f)).append('%');
                }
                return label.toString();
        }

        /**
         * A duration in ticks as {@code m:ss}; the infinite duration
         * (-1) reads as its usual "∞" — the one non-ASCII glyph the
         * vanilla font carries and the game itself uses for it.
         */
        static String duration(int ticks) {
                if (ticks < 0) {
                        return "∞";
                }
                long seconds = Math.round(ticks / 20.0);
                return String.format("%d:%02d", seconds / 60, seconds % 60);
        }

        /** The roman numeral for 1..12; higher amplifiers fall back to the number. */
        static String roman(int level) {
                String[] numerals = {
                                "I", "II", "III", "IV", "V", "VI",
                                "VII", "VIII", "IX", "X", "XI", "XII"};
                if (level >= 1 && level <= numerals.length) {
                        return numerals[level - 1];
                }
                return String.valueOf(level);
        }
}
