package net.zaminmc.zea.itempresentation.calculation;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderOwner;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.item.enchantment.Enchantment;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Registry-keyed enchantment holders for the path-based lookups — the
 * same keyed-reference fixture {@code RarityFieldTest} builds for the
 * rarity resolvers. {@link EnchantmentLevel#byPath} and the specialized
 * protection labels unwrap the registry key, so a key-less {@code
 * Holder.direct} would never match; these references carry a real
 * {@code minecraft:} key and a bound value.
 */
final class KeyedEnchantments {

        /** A throwaway owner for the standalone references; never queried. */
        private static final HolderOwner<Enchantment> OWNER = new HolderOwner<>() {
        };

        private final Map<String, Keyed> holders = new HashMap<>();

        /**
         * The keyed holder for {@code path}, bound to an enchantment
         * whose effect map {@code effects} populates. Repeated calls
         * for the same path return the same bound holder.
         */
        Holder<Enchantment> holder(String path, String display,
                        Consumer<DataComponentMap.Builder> effects) {
                Keyed keyed = holders.computeIfAbsent(path, key -> {
                        Keyed reference = new Keyed(ResourceKey.create(
                                        Registries.ENCHANTMENT,
                                        Identifier.fromNamespaceAndPath("minecraft", key)));
                        reference.bind(enchantment(display, effects));
                        return reference;
                });
                return keyed;
        }

        private static Enchantment enchantment(String display,
                        Consumer<DataComponentMap.Builder> effects) {
                DataComponentMap.Builder builder = DataComponentMap.builder();
                effects.accept(builder);
                return new Enchantment(
                                Component.literal(display),
                                Enchantment.definition(
                                                net.minecraft.core.HolderSet.empty(),
                                                10,
                                                5,
                                                Enchantment.constantCost(1),
                                                Enchantment.constantCost(1),
                                                0,
                                                EquipmentSlotGroup.ANY),
                                net.minecraft.core.HolderSet.empty(),
                                builder.build());
        }

        /**
         * A registry-keyed reference, like the holders the live game
         * uses — created unbound, then bound to its value. The tag set
         * is primed empty (the registry binding that would do this in
         * game never runs in a test JVM) because {@code getFullname}
         * reads the curse tag for every display string.
         */
        private static final class Keyed extends Holder.Reference<Enchantment> {
                private Keyed(ResourceKey<Enchantment> key) {
                        super(Holder.Reference.Type.STAND_ALONE, OWNER, key, null);
                        try {
                                var field = Holder.Reference.class.getDeclaredField("tags");
                                field.setAccessible(true);
                                if (field.get(this) == null) {
                                        field.set(this, java.util.Set.of());
                                }
                        } catch (ReflectiveOperationException e) {
                                throw new IllegalStateException(e);
                        }
                }

                private void bind(Enchantment value) {
                        bindValue(value);
                }
        }
}
