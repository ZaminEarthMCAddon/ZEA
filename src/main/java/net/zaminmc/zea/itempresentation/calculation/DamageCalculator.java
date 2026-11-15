package net.zaminmc.zea.itempresentation.calculation;

import net.minecraft.core.Holder;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.ConditionalEffect;
import net.minecraft.world.item.enchantment.effects.EnchantmentValueEffect;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Melee damage for swords, axes, spears and maces (normal hits — the
 * mace's smash attack is {@link MaceCalculator}'s concern).
 *
 * <p>The base attack damage is resolved the way Minecraft resolves it
 * when the item is held: the {@link ItemAttributeModifiers} component is
 * asked to {@code compute} the {@link Attributes#ATTACK_DAMAGE} value
 * for the main-hand slot, starting from the attribute's default. The
 * item's {@code ADD_VALUE} modifier (the "+7" on a diamond sword) lands
 * on top of the player's base, exactly as equipping does.
 *
 * <p>Enchantment damage is read from the enchantment's {@link
 * EnchantmentEffectComponents#DAMAGE} effects through the live
 * {@link Enchantment#getEffects} API — the data-driven effect list, not
 * a hardcoded Sharpness formula. Each effect's {@link
 * EnchantmentValueEffect#process} is evaluated with a fixed-seed random
 * source so the tooltip is deterministic: the same item always shows the
 * same damage. Unconditional effects contribute to {@code
 * enchantmentBonus}; effects whose condition cannot be evaluated
 * without a target (Smite vs undead, Bane of Arthropods vs arthropods)
 * are listed as conditional bonuses instead of folded in.
 *
 * <p>The attack speed and sweeping ratio come from the same
 * {@code compute} path on their respective attributes. An item with no
 * attack-damage modifier yields an empty result — a bow never shows
 * melee damage.
 */
public final class DamageCalculator {
        /** The fixed seed for enchantment-effect evaluation, so tooltips are stable. */
        static final long EFFECT_SEED = 0L;

        private DamageCalculator() {
        }

        /**
         * The melee damage for {@code stack} from {@code snapshot}, or
         * empty when the item carries no attack-damage modifier.
         */
        public static Optional<ItemStatistics.MeleeDamage> calculate(ItemStack stack, ItemSnapshot snapshot) {
                var modifiers = snapshot.attributeModifiers();
                double attackDamage = modifiers.compute(
                                Attributes.ATTACK_DAMAGE,
                                Attributes.ATTACK_DAMAGE.value().getDefaultValue(),
                                ItemSnapshot.WEAPON_SLOT);
                // An item with no attack-damage modifier computes back to
                // the attribute default (1.0 for ATTACK_DAMAGE). That is
                // the player's fist, not a weapon statistic — bows and
                // tools that don't swing for damage are not melee weapons.
                if (!hasAttackDamageModifier(modifiers)) {
                        return Optional.empty();
                }
                double attackSpeed = modifiers.compute(
                                Attributes.ATTACK_SPEED,
                                Attributes.ATTACK_SPEED.value().getDefaultValue(),
                                ItemSnapshot.WEAPON_SLOT);
                double sweeping = modifiers.compute(
                                Attributes.SWEEPING_DAMAGE_RATIO,
                                Attributes.SWEEPING_DAMAGE_RATIO.value().getDefaultValue(),
                                ItemSnapshot.WEAPON_SLOT);
                EnchantmentDamage enchantDamage = enchantmentDamage(snapshot);
                return Optional.of(new ItemStatistics.MeleeDamage(
                                attackDamage,
                                enchantDamage.bonus,
                                attackSpeed,
                                sweeping,
                                enchantDamage.conditional));
        }

        /**
         * Whether the modifier set actually contributes an
         * {@link Attributes#ATTACK_DAMAGE} entry — the signal that the
         * item is a melee weapon rather than a tool that happens to be
         * holdable. {@code compute} returns the default for items
         * without the modifier, so the modifier presence is the real
         * classifier.
         */
        private static boolean hasAttackDamageModifier(
                        net.minecraft.world.item.component.ItemAttributeModifiers modifiers) {
                boolean[] found = {false};
                modifiers.forEach(ItemSnapshot.WEAPON_SLOT, (attribute, modifier) -> {
                        if (attribute == Attributes.ATTACK_DAMAGE) {
                                found[0] = true;
                        }
                });
                return found[0];
        }

        /**
         * The unconditional and conditional damage enchantments on the
         * item. Unconditional effects (Sharpness) sum into {@code bonus};
         * conditional effects (Smite, Bane of Arthropods — gated on a
         * target mob family the tooltip does not have) become
         * {@link ItemStatistics.ConditionalBonus} entries.
         */
        private static EnchantmentDamage enchantmentDamage(ItemSnapshot snapshot) {
                if (!snapshot.hasEnchantments()) {
                        return new EnchantmentDamage(0.0, List.of());
                }
                RandomSource random = RandomSource.create(EFFECT_SEED);
                double bonus = 0.0;
                List<ItemStatistics.ConditionalBonus> conditional = new ArrayList<>();
                for (ItemSnapshot.EnchantmentEntry entry : snapshot.enchantmentEntries()) {
                        Holder<Enchantment> holder = entry.enchantment();
                        int level = entry.level();
                        List<ConditionalEffect<EnchantmentValueEffect>> effects =
                                        holder.value().getEffects(EnchantmentEffectComponents.DAMAGE);
                        if (effects.isEmpty()) {
                                continue;
                        }
                        String label = displayLabel(holder);
                        boolean unconditional = effects.stream().allMatch(e -> e.requirements().isEmpty());
                        for (ConditionalEffect<EnchantmentValueEffect> conditionalEffect : effects) {
                                // process(level, random, base) — base 0 because
                                // enchantment damage is additive, not a multiplier
                                // on the item's damage.
                                double value = conditionalEffect.effect().process(level, random, 0.0f);
                                if (unconditional) {
                                        bonus += value;
                                } else {
                                        conditional.add(new ItemStatistics.ConditionalBonus(
                                                        label + " (conditional)", value));
                                }
                        }
                }
                return new EnchantmentDamage(bonus, List.copyOf(conditional));
        }

        /** The plain display label of an enchantment, for conditional entries. */
        private static String displayLabel(Holder<Enchantment> holder) {
                return Enchantment.getFullname(holder, holder.value().getMaxLevel()).getString();
        }

        private record EnchantmentDamage(double bonus, List<ItemStatistics.ConditionalBonus> conditional) {
        }
}
