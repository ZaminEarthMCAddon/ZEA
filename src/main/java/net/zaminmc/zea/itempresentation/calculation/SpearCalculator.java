package net.zaminmc.zea.itempresentation.calculation;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

/**
 * Spear-specific statistics: the jab attack and the Lunge enchantment.
 *
 * <p>Spears in 26.3 are data-driven items in the {@code
 * #minecraft:spears} tag, with melee attributes resolved the same way
 * as swords — so the normal-hit damage lives in {@link
 * DamageCalculator}. The spear's own mechanic is the jab: a longer
 * reach attack that trades attack speed for range. The reach is
 * represented by the {@link Attributes#ENTITY_INTERACTION_RANGE}
 * attribute, which the calculator surfaces so the presentation can
 * state "reach: +X" rather than burying it in the vanilla attribute
 * list.
 *
 * <p>The Lunge enchantment is a spear-specific mechanic (dash attack);
 * its level is exposed so the presentation can show it without
 * duplicating the enchantment line. Lunge's actual damage depends on
 * the dash distance, so it is a flag, not a flat number.
 */
public final class SpearCalculator {
        private SpearCalculator() {
        }

        /**
         * The spear-specific reach and Lunge flag for {@code stack}, or
         * empty when the item is not a spear (the category classifier
         * decides this; the calculator takes the stack so it can read
         * the reach attribute).
         */
        public static Optional<SpearStats> calculate(ItemStack stack, ItemSnapshot snapshot) {
                var modifiers = snapshot.attributeModifiers();
                double reach = modifiers.compute(
                                Attributes.ENTITY_INTERACTION_RANGE,
                                Attributes.ENTITY_INTERACTION_RANGE.value().getDefaultValue(),
                                ItemSnapshot.WEAPON_SLOT);
                int lunge = EnchantmentLevel.lunge(snapshot);
                // An item with no interaction-range modifier returns the
                // default; only surface reach when the item actually
                // extends it beyond the baseline.
                if (!hasReachModifier(modifiers) && lunge == 0) {
                        return Optional.empty();
                }
                return Optional.of(new SpearStats(reach, lunge > 0));
        }

        /** Whether the modifier set contributes an interaction-range entry. */
        private static boolean hasReachModifier(
                        net.minecraft.world.item.component.ItemAttributeModifiers modifiers) {
                boolean[] found = {false};
                modifiers.forEach(ItemSnapshot.WEAPON_SLOT, (attribute, modifier) -> {
                        if (attribute == Attributes.ENTITY_INTERACTION_RANGE) {
                                found[0] = true;
                        }
                });
                return found[0];
        }

        /** The spear's reach and whether it carries the Lunge enchantment. */
        public record SpearStats(double reach, boolean lunge) {
        }
}
