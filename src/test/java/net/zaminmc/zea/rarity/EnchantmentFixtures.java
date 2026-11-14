package net.zaminmc.zea.rarity;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.item.enchantment.Enchantment;
import net.zaminmc.zea.VanillaBootstrap;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Registry-free enchantment fixtures for the rarity tests. Each fixture
 * wraps one constructed {@link Enchantment} in a stable direct holder, so
 * {@link Enchantment#areCompatible}'s equality checks, exclusive-set
 * containment and {@link Enchantment#getFullname}'s display text behave
 * exactly as they do against registry holders. Direct holders carry no
 * registry key — the key-path logic in {@link EnchantPolicy} is tested
 * through its path-based seams instead.
 */
final class EnchantmentFixtures {
        private final Map<String, Holder<Enchantment>> byName = new HashMap<>();

        static {
                // Constructing an Enchantment runs its static codec, whose
                // effect-component registration writes into BuiltInRegistries
                // — which throws "Not bootstrapped" and poisons the JVM's
                // registries if vanilla has not been bootstrapped first.
                VanillaBootstrap.ensure();
        }

        /** A standalone enchantment with the given max level and no exclusions. */
        Holder<Enchantment> make(String name, int maxLevel) {
                return byName.computeIfAbsent(name, key -> holder(key, maxLevel, HolderSet.empty()));
        }

        /**
         * An enchantment exclusive with the given others: its exclusive
         * set names them, mirroring how vanilla writes an exclusive group
         * into the registry (one-sided exclusion is enough — {@code
         * areCompatible} checks both directions).
         */
        Holder<Enchantment> makeExclusive(String name, int maxLevel, Holder<Enchantment>... others) {
                Holder<Enchantment> rebuilt = holder(name, maxLevel, HolderSet.direct(others));
                byName.put(name, rebuilt);
                return rebuilt;
        }

        /** The fixture holder for {@code name}, created at max level 5 if absent. */
        Holder<Enchantment> get(String name) {
                return byName.computeIfAbsent(name, key -> holder(key, 5, HolderSet.empty()));
        }

        /** A compatible-set entry for {@code name} at its fixture max level. */
        EnchantBudget.Compatible compatible(String name) {
                Holder<Enchantment> enchantment = get(name);
                return new EnchantBudget.Compatible(enchantment, enchantment.value().getMaxLevel());
        }

        /** Compatible-set entries for several fixtures, in order. */
        List<EnchantBudget.Compatible> compatibleAll(String... names) {
                return Arrays.stream(names).map(this::compatible).toList();
        }

        private static Holder<Enchantment> holder(String name, int maxLevel,
                        HolderSet<Enchantment> exclusiveWith) {
                // Display text as title case, matching how vanilla shows
                // enchantment names ("sharpness" fixture → "Sharpness V").
                String display = Character.toUpperCase(name.charAt(0)) + name.substring(1);
                Enchantment enchantment = new Enchantment(
                                                Component.literal(display),
                                                Enchantment.definition(
                                                                                HolderSet.empty(),
                                                                                10,
                                                                                maxLevel,
                                                                                Enchantment.constantCost(1),
                                                                                Enchantment.constantCost(1),
                                                                                0,
                                                                                EquipmentSlotGroup.ANY),
                                                exclusiveWith,
                                                DataComponentMap.EMPTY);
                return Holder.direct(enchantment);
        }
}
