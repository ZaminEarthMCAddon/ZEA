package net.zaminmc.zea.itempresentation.calculation;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.enchantment.ConditionalEffect;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.effects.EnchantmentAttributeEffect;
import net.minecraft.world.item.enchantment.effects.EnchantmentValueEffect;

import java.util.List;

/**
 * Reads the live, data-driven enchantment effects off a snapshot's
 * enchantments — the single seam every calculator asks for "what does
 * this item's enchantment data actually contribute".
 *
 * <p>26.3 expresses most enchantment behaviour as effect components on
 * the {@link Enchantment} record itself: Efficiency is an {@code
 * minecraft:attributes} entry writing {@code mining_efficiency} with a
 * {@code levels_squared} amount, Sweeping Edge writes {@code
 * sweeping_damage_ratio} as a fraction, Quick Charge carries a {@code
 * crossbow_charge_time} value effect. Reading those live — instead of
 * restating their formulas in ZEA — is what keeps a server datapack
 * that rebalances an enchantment honest in the tooltip without a ZEA
 * update.
 *
 * <p>All evaluation uses the shared fixed-seed random source, so the
 * same item always presents the same numbers.
 */
final class EnchantmentEffects {
        /** The fixed seed for enchantment-effect evaluation, so tooltips are stable. */
        static final long EFFECT_SEED = 0L;

        private EnchantmentEffects() {
        }

        /**
         * The summed {@code add_value} contribution the snapshot's
         * enchantments make to {@code attribute} — e.g. Efficiency's
         * {@code mining_efficiency} or Sweeping Edge's {@code
         * sweeping_damage_ratio}. Only {@code add_value} entries are
         * summed: a multiply-style effect needs the base it multiplies,
         * which the tooltip does not have, and vanilla's enchantment
         * data uses none on the stats ZEA presents.
         */
        static double attributeContribution(ItemSnapshot snapshot, Holder<Attribute> attribute) {
                if (!snapshot.hasEnchantments()) {
                        return 0.0;
                }
                double total = 0.0;
                for (ItemSnapshot.EnchantmentEntry entry : snapshot.enchantmentEntries()) {
                        for (EnchantmentAttributeEffect effect : entry.enchantment().value()
                                        .getEffects(EnchantmentEffectComponents.ATTRIBUTES)) {
                                if (effect.attribute() == attribute
                                                && effect.operation() == AttributeModifier.Operation.ADD_VALUE) {
                                        total += effect.amount().calculate(entry.level());
                                }
                        }
                }
                return total;
        }

        /**
         * The same contribution, matching the attribute by registry-key
         * path — for attributes the game itself registers from datapacks
         * ({@code armor_effectiveness}, Breach's target), which have no
         * compile-time field to compare against.
         */
        static double attributeContributionByPath(ItemSnapshot snapshot, String path) {
                if (!snapshot.hasEnchantments()) {
                        return 0.0;
                }
                double total = 0.0;
                for (ItemSnapshot.EnchantmentEntry entry : snapshot.enchantmentEntries()) {
                        for (EnchantmentAttributeEffect effect : entry.enchantment().value()
                                        .getEffects(EnchantmentEffectComponents.ATTRIBUTES)) {
                                boolean matches = effect.attribute().unwrapKey()
                                                .map(key -> key.identifier().getPath().equals(path))
                                                .orElse(false);
                                if (matches
                                                && effect.operation() == AttributeModifier.Operation.ADD_VALUE) {
                                        total += effect.amount().calculate(entry.level());
                                }
                        }
                }
                return total;
        }

        /**
         * The summed unconditional value of the list-typed effect
         * {@code component} across the snapshot's enchantments — e.g.
         * {@code projectile_piercing} for Piercing, {@code
         * projectile_count} for Multishot. Conditional entries are
         * skipped: their requirement is a loot condition the tooltip
         * cannot evaluate, and guessing at it would overstate the stat.
         */
        static double unconditionalValue(ItemSnapshot snapshot,
                        DataComponentType<List<ConditionalEffect<EnchantmentValueEffect>>> component) {
                if (!snapshot.hasEnchantments()) {
                        return 0.0;
                }
                RandomSource random = RandomSource.create(EFFECT_SEED);
                double total = 0.0;
                for (ItemSnapshot.EnchantmentEntry entry : snapshot.enchantmentEntries()) {
                        for (var conditionalEffect : entry.enchantment().value().getEffects(component)) {
                                if (conditionalEffect.requirements().isEmpty()) {
                                        total += conditionalEffect.effect()
                                                        .process(entry.level(), random, 0.0f);
                                }
                        }
                }
                return total;
        }

        /**
         * The single held enchantment whose registry-key path ends with
         * {@code path}, or {@code null}. Path-keyed, matching {@link
         * EnchantmentLevel}: resource packs can rewrite display names
         * but cannot move an enchantment's key.
         */
        static ItemSnapshot.EnchantmentEntry byPath(ItemSnapshot snapshot, String path) {
                for (ItemSnapshot.EnchantmentEntry entry : snapshot.enchantmentEntries()) {
                        String entryPath = entry.enchantment().unwrapKey()
                                        .map(key -> key.identifier().getPath())
                                        .orElse("");
                        if (entryPath.equals(path)) {
                                return entry;
                        }
                }
                return null;
        }
}
