package net.zaminmc.zea.itempresentation.calculation;

import net.minecraft.world.item.ItemStack;

import java.util.Optional;

/**
 * Durability: the current damage value against the maximum, and
 * nothing else.
 *
 * <p>The numbers are read from the stack's own {@code max_damage} and
 * {@code damage} components — the tooltip states the real maximum the
 * item ships with. Unbreaking is deliberately not folded into any
 * "effective maximum": it changes how often durability is consumed,
 * not how much there is, and presenting an inflated theoretical
 * lifetime as the maximum misrepresents both the item and the
 * enchantment. Unbreaking presents as the enchantment it is, in the
 * enchantment section, and keeps its real vanilla behaviour. An item
 * with no {@code max_damage} component (blocks, materials) yields an
 * empty result — no durability line where durability does not apply.
 */
public final class DurabilityCalculator {

        private DurabilityCalculator() {
        }

        /**
         * The durability stats for {@code stack} from {@code snapshot},
         * or empty when the item is not damageable.
         */
        public static Optional<ItemStatistics.Durability> calculate(
                        ItemStack stack, ItemSnapshot snapshot) {
                if (snapshot.maxDamage() <= 0) {
                        return Optional.empty();
                }
                int maximum = snapshot.maxDamage();
                int current = Math.max(0, maximum - snapshot.damage());
                return Optional.of(new ItemStatistics.Durability(current, maximum));
        }
}
