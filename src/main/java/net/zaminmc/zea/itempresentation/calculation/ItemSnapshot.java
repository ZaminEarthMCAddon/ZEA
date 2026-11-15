package net.zaminmc.zea.itempresentation.calculation;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.AttackRange;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.KineticWeapon;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import java.util.List;
import java.util.Optional;

/**
 * A one-shot capture of the components the calculators read off an
 * {@link ItemStack}. Built once when a tooltip is hovered and handed to
 * every calculator, so the same stack never has its components read
 * twice in one frame.
 *
 * <p>The snapshot intentionally holds only what the calculations are
 * <em>stateless</em> over — the item's own attribute modifiers, its
 * enchantments, its tool rules, its food, its weapon-type components.
 * Target-dependent values (the actual damage dealt to a specific mob,
 * the mining speed against a specific block, the protection against a
 * specific damage type) are not captured here: they depend on a target
 * the tooltip does not have, so they live in the calculators behind
 * explicit "against X" parameters and are never cached as universal
 * item properties.
 *
 * <p>Immutable after construction. The component instances themselves
 * are stable for the life of the stack, so a snapshot taken at hover
 * time stays valid for the duration of the hover.
 */
public record ItemSnapshot(
                ItemAttributeModifiers attributeModifiers,
                ItemEnchantments enchantments,
                Tool tool,
                FoodProperties food,
                Consumable consumable,
                AttackRange attackRange,
                KineticWeapon kineticWeapon,
                ChargedProjectiles chargedProjectiles,
                int maxDamage,
                int damage) {

        /** The main-hand slot — where weapons and tools present their stats. */
        public static final EquipmentSlot WEAPON_SLOT = EquipmentSlot.MAINHAND;

        /**
         * Captures the snapshot from {@code stack} against the level's
         * registries. Components that are absent resolve to {@code null}
         * for the optional ones; the calculators treat those as "no
         * contribution".
         */
        public static ItemSnapshot of(ItemStack stack, HolderLookup.Provider registries) {
                ItemAttributeModifiers modifiers = stack.getOrDefault(
                                DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
                ItemEnchantments enchantments = stack.getOrDefault(
                                DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
                Tool tool = stack.get(DataComponents.TOOL);
                FoodProperties food = stack.get(DataComponents.FOOD);
                Consumable consumable = stack.get(DataComponents.CONSUMABLE);
                AttackRange attackRange = stack.get(DataComponents.ATTACK_RANGE);
                KineticWeapon kineticWeapon = stack.get(DataComponents.KINETIC_WEAPON);
                ChargedProjectiles chargedProjectiles = stack.get(DataComponents.CHARGED_PROJECTILES);
                int maxDamage = stack.getOrDefault(DataComponents.MAX_DAMAGE, 0);
                int damage = stack.getOrDefault(DataComponents.DAMAGE, 0);
                return new ItemSnapshot(modifiers, enchantments, tool, food, consumable,
                                attackRange, kineticWeapon, chargedProjectiles, maxDamage, damage);
        }

        /** Whether the item carries any enchantments at all. */
        public boolean hasEnchantments() {
                return !enchantments.isEmpty();
        }

        /**
         * The enchantment entries in the snapshot, for calculators that
         * walk them directly. Returns an unmodifiable view of the
         * component's own entry set.
         */
        public List<EnchantmentEntry> enchantmentEntries() {
                return enchantments.entrySet().stream()
                                .map(e -> new EnchantmentEntry(e.getKey(), e.getIntValue()))
                                .toList();
        }

        /** One held enchantment: the holder and its level on this item. */
        public record EnchantmentEntry(Holder<Enchantment> enchantment, int level) {
        }

        /** Placeholder for {@code Optional<Tool>} ergonomics — unused so far. */
        public Optional<Tool> toolOptional() {
                return Optional.ofNullable(tool);
        }
}
