package net.zaminmc.zea.itempresentation.calculation;

import net.minecraft.core.Holder;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Armor statistics for a worn piece: armor points, toughness, knockback
 * resistance, and the protection enchantments the piece carries.
 *
 * <p>The armor points, toughness and knockback resistance come from the
 * {@link ItemAttributeModifiers} component's {@code compute} for the
 * piece's equipment slot — the same path the game uses when the piece is
 * equipped. An item with no armor modifier yields an empty result.
 *
 * <p>Protection enchantments are <em>not</em> folded into a single
 * damage-reduction percentage. The vanilla protection formula is
 * conditional on the incoming damage type (Fire Protection only helps
 * against fire, Blast Protection only against explosions, …), and the
 * tooltip has no incoming damage to evaluate against. Each protection
 * enchantment is therefore listed as a {@link
 * ItemStatistics.ConditionalBonus} carrying the enchantment's label and
 * the unconditional portion of its effect (the flat Protection bonus
 * that applies to every damage type), so the tooltip states the
 * protection it actually grants without claiming a universal reduction.
 */
public final class ArmorCalculator {
        private ArmorCalculator() {
        }

        /**
         * The armor stats for {@code stack} from {@code snapshot}, or
         * empty when the item carries no armor modifier.
         */
        public static Optional<ItemStatistics.ArmorStats> calculate(ItemStack stack, ItemSnapshot snapshot) {
                var modifiers = snapshot.attributeModifiers();
                EquipmentSlot slot = armorSlot(modifiers);
                if (slot == null) {
                        return Optional.empty();
                }
                double armor = modifiers.compute(
                                Attributes.ARMOR, Attributes.ARMOR.value().getDefaultValue(), slot);
                double toughness = modifiers.compute(
                                Attributes.ARMOR_TOUGHNESS,
                                Attributes.ARMOR_TOUGHNESS.value().getDefaultValue(), slot);
                double knockbackResistance = modifiers.compute(
                                Attributes.KNOCKBACK_RESISTANCE,
                                Attributes.KNOCKBACK_RESISTANCE.value().getDefaultValue(), slot);
                return Optional.of(new ItemStatistics.ArmorStats(
                                armor, toughness, knockbackResistance,
                                protectionEnchantments(snapshot)));
        }

        /**
         * The first armor-slot the modifier set contributes armor points
         * to — helmet, chestplate, leggings or boots. Shields and elytra
         * do not carry the armor attribute and return null.
         */
        private static EquipmentSlot armorSlot(
                        net.minecraft.world.item.component.ItemAttributeModifiers modifiers) {
                for (EquipmentSlot slot : new EquipmentSlot[] {
                                EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
                        boolean[] found = {false};
                        modifiers.forEach(slot, (attribute, modifier) -> {
                                if (attribute == Attributes.ARMOR) {
                                        found[0] = true;
                                }
                        });
                        if (found[0]) {
                                return slot;
                        }
                }
                return null;
        }

        /**
         * The protection enchantments on the item, each as a conditional
         * bonus labelled with the enchantment's display name. The value
         * is the unconditional portion of the {@link
         * EnchantmentEffectComponents#DAMAGE_PROTECTION} effect,
         * evaluated with a fixed seed so the tooltip is stable.
         */
        private static List<ItemStatistics.ConditionalBonus> protectionEnchantments(ItemSnapshot snapshot) {
                if (!snapshot.hasEnchantments()) {
                        return List.of();
                }
                RandomSource random = RandomSource.create(DamageCalculator.EFFECT_SEED);
                List<ItemStatistics.ConditionalBonus> bonuses = new ArrayList<>();
                for (ItemSnapshot.EnchantmentEntry entry : snapshot.enchantmentEntries()) {
                        Holder<Enchantment> holder = entry.enchantment();
                        int level = entry.level();
                        var effects = holder.value().getEffects(EnchantmentEffectComponents.DAMAGE_PROTECTION);
                        if (effects.isEmpty()) {
                                continue;
                        }
                        double value = 0.0;
                        for (var conditionalEffect : effects) {
                                if (conditionalEffect.requirements().isEmpty()) {
                                        value += conditionalEffect.effect().process(level, random, 0.0f);
                                }
                        }
                        if (value > 0.0) {
                                String label = Enchantment.getFullname(holder, level).getString();
                                bonuses.add(new ItemStatistics.ConditionalBonus(label, value));
                        }
                }
                return List.copyOf(bonuses);
        }
}
