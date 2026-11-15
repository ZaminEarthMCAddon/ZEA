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
import java.util.Map;
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
 * damage-reduction percentage. In the 26.3 data every protection
 * enchantment's {@code damage_protection} effect carries a
 * damage-source requirement — plain Protection applies to everything
 * that does not bypass invulnerability, Fire Protection only to fire,
 * Feather Falling only to falls — so a tooltip with no incoming damage
 * to evaluate must state the condition, not average over it. Each
 * enchantment is listed as a {@link ItemStatistics.ConditionalBonus}
 * whose value is the piece's enchantment-protection-factor (EPF)
 * contribution, read live from the effect; the reduction this piece
 * alone grants against matching damage is {@code min(20, epf) / 25} of
 * it, the vanilla combat rule (each EPF point is 4%, the whole set
 * caps at 80%).
 */
public final class ArmorCalculator {

        /**
         * The condition text for the protection enchantments, keyed by
         * registry path. The EPF <em>values</em> are read live from the
         * effect data; only the human phrasing of the damage type each
         * loot condition selects is curated here, because a loot
         * condition is opaque to a tooltip. Unknown protection
         * enchantments fall back to the honest "(conditional)".
         */
        private static final Map<String, String> CONDITION_LABELS = Map.of(
                        "protection", "most damage",
                        "fire_protection", "fire damage",
                        "blast_protection", "explosions",
                        "projectile_protection", "projectiles",
                        "feather_falling", "falls");

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
         * bonus: the label is the enchantment's fullname at its held
         * level plus the damage type it applies to, the value is its
         * live EPF contribution. Enchantment effects whose requirements
         * are empty still count — the datapack decides which protection
         * is conditional, not ZEA.
         */
        private static List<ItemStatistics.ConditionalBonus> protectionEnchantments(ItemSnapshot snapshot) {
                if (!snapshot.hasEnchantments()) {
                        return List.of();
                }
                RandomSource random = RandomSource.create(EnchantmentEffects.EFFECT_SEED);
                List<ItemStatistics.ConditionalBonus> bonuses = new ArrayList<>();
                for (ItemSnapshot.EnchantmentEntry entry : snapshot.enchantmentEntries()) {
                        Holder<Enchantment> holder = entry.enchantment();
                        int level = entry.level();
                        var effects = holder.value().getEffects(EnchantmentEffectComponents.DAMAGE_PROTECTION);
                        if (effects.isEmpty()) {
                                continue;
                        }
                        double epf = 0.0;
                        for (var conditionalEffect : effects) {
                                epf += conditionalEffect.effect().process(level, random, 0.0f);
                        }
                        if (epf > 0.0) {
                                String label = Enchantment.getFullname(holder, level).getString();
                                String condition = holder.unwrapKey()
                                                .map(key -> CONDITION_LABELS.get(key.identifier().getPath()))
                                                .orElse(null);
                                bonuses.add(new ItemStatistics.ConditionalBonus(
                                                label + " (" + (condition != null
                                                                ? condition : "conditional") + ")",
                                                epf));
                        }
                }
                return List.copyOf(bonuses);
        }
}
