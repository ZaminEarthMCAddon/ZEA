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
 * Armor statistics for a worn piece: RPG defense, toughness, knockback
 * resistance, and the specialized protection enchantments the piece
 * carries.
 *
 * <p>The defense number is the RPG interpretation: the piece's vanilla
 * armor points times ten, plus ten per level of generic Protection —
 * the diamond chestplate's 8 points and Protection V's 50 bonus read
 * as 130. The 10× scale is a presentation convention; the vanilla
 * armor points and the game's damage math are untouched. The bracket
 * level is the actual Protection level read from the enchantment
 * component, never clamped — a server item with Protection VI shows
 * VI. Toughness and knockback resistance keep their vanilla values.
 *
 * <p>The specialized protection enchantments are deliberately not part
 * of defense. In the 26.3 data every protection enchantment's {@code
 * damage_protection} effect carries a damage-source requirement —
 * generic Protection applies to everything that does not bypass
 * invulnerability, Fire Protection only to fire, Feather Falling only
 * to falls — so each specialized enchantment is listed on its own
 * stat, named for the damage type it guards, with the piece's
 * enchantment-protection-factor (EPF) contribution read live from the
 * effect data. The reduction the piece alone grants against matching
 * damage is {@code min(20, epf) / 25} of it, the vanilla combat rule
 * (each EPF point is 4%, the whole set caps at 80%); a single piece's
 * value is its contribution, not the reduction the whole armor set
 * buys.
 */
public final class ArmorCalculator {

        /**
         * The RPG defense scale: vanilla armor points multiply by ten,
         * and each level of generic Protection adds ten. A presentation
         * convention only — the attribute values the game uses stay
         * vanilla.
         */
        static final double DEFENSE_SCALE = 10.0;

        /**
         * The stat names for the specialized protection enchantments,
         * keyed by registry path. The EPF <em>values</em> are read live
         * from the effect data; only the human name of the damage type
         * each enchantment guards is curated here. Generic Protection
         * is not in this map — it contributes to defense, not to a
         * specialized stat. Unknown protection enchantments fall back
         * to the honest "(conditional)".
         */
        private static final Map<String, String> SPECIALIZED_LABELS = Map.of(
                        "fire_protection", "Fire Resistance",
                        "blast_protection", "Explosion Resistance",
                        "projectile_protection", "Ranged Defense",
                        "feather_falling", "Fall Resistance");

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
                double armorPoints = modifiers.compute(
                                Attributes.ARMOR, Attributes.ARMOR.value().getDefaultValue(), slot);
                double toughness = modifiers.compute(
                                Attributes.ARMOR_TOUGHNESS,
                                Attributes.ARMOR_TOUGHNESS.value().getDefaultValue(), slot);
                double knockbackResistance = modifiers.compute(
                                Attributes.KNOCKBACK_RESISTANCE,
                                Attributes.KNOCKBACK_RESISTANCE.value().getDefaultValue(), slot);
                int protectionLevel = EnchantmentLevel.byPath(snapshot, "protection");
                double defense = armorPoints * DEFENSE_SCALE
                                + protectionLevel * DEFENSE_SCALE;
                return Optional.of(new ItemStatistics.ArmorStats(
                                defense, toughness, knockbackResistance,
                                protectionLevel, specializedProtections(snapshot)));
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
         * The specialized protection enchantments on the item, each as
         * a conditional bonus: the label is the stat name for the
         * damage type the enchantment guards (Fire Protection → "Fire
         * Resistance"), the value is its live EPF contribution. Generic
         * Protection is excluded — its level rides the defense number —
         * and an unknown protection enchantment (a modded one) keeps
         * its fullname with the honest "(conditional)" so nothing is
         * silently merged into the wrong stat. Enchantment effects
         * whose requirements are empty still count — the datapack
         * decides which protection is conditional, not ZEA.
         */
        private static List<ItemStatistics.ConditionalBonus> specializedProtections(ItemSnapshot snapshot) {
                if (!snapshot.hasEnchantments()) {
                        return List.of();
                }
                RandomSource random = RandomSource.create(EnchantmentEffects.EFFECT_SEED);
                List<ItemStatistics.ConditionalBonus> bonuses = new ArrayList<>();
                for (ItemSnapshot.EnchantmentEntry entry : snapshot.enchantmentEntries()) {
                        Holder<Enchantment> holder = entry.enchantment();
                        String path = holder.unwrapKey()
                                        .map(key -> key.identifier().getPath())
                                        .orElse("");
                        if (path.equals("protection")) {
                                continue;
                        }
                        var effects = holder.value().getEffects(EnchantmentEffectComponents.DAMAGE_PROTECTION);
                        if (effects.isEmpty()) {
                                continue;
                        }
                        double epf = 0.0;
                        for (var conditionalEffect : effects) {
                                epf += conditionalEffect.effect().process(entry.level(), random, 0.0f);
                        }
                        if (epf > 0.0) {
                                String statName = SPECIALIZED_LABELS.get(path);
                                bonuses.add(new ItemStatistics.ConditionalBonus(
                                                statName != null ? statName
                                                                : Enchantment.getFullname(holder, entry.level()).getString()
                                                                                + " (conditional)",
                                                epf));
                        }
                }
                return List.copyOf(bonuses);
        }
}
