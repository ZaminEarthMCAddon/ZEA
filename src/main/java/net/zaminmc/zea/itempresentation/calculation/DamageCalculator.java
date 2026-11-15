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
import java.util.Map;
import java.util.Optional;

/**
 * Melee damage for swords, axes, spears and maces (normal hits — the
 * mace's smash attack is {@link MaceCalculator}'s concern, the spear's
 * charged attack is {@link SpearCalculator}'s).
 *
 * <p>The base attack damage is resolved the way Minecraft resolves it
 * when the item is held: the {@link ItemAttributeModifiers} component is
 * asked to {@code compute} the {@link Attributes#ATTACK_DAMAGE} value
 * for the main-hand slot, starting from the attribute's default. The
 * item's {@code ADD_VALUE} modifier (the "+7" on a diamond sword) lands
 * on top of the player's base, exactly as equipping does — so the
 * presented number is the damage a normal hit actually deals, not just
 * the item's own modifier.
 *
 * <p>Enchantment damage is read from the enchantment's {@link
 * EnchantmentEffectComponents#DAMAGE} effects through the live
 * {@link Enchantment#getEffects} API — the data-driven effect list, not
 * a hardcoded Sharpness formula. Each effect's {@link
 * EnchantmentValueEffect#process} is evaluated with a fixed-seed random
 * source so the tooltip is deterministic: the same item always shows the
 * same damage. Unconditional effects contribute to {@code
 * enchantmentBonus} (labelled, so the presentation can split base from
 * enchantment); effects whose condition cannot be evaluated without a
 * target (Smite vs undead, Bane of Arthropods vs arthropods) are listed
 * as conditional bonuses instead of folded in.
 *
 * <p>The attack speed and sweeping ratio come from the same {@code
 * compute} path on their respective attributes — plus, for sweeping,
 * the live {@code sweeping_damage_ratio} contribution of the Sweeping
 * Edge enchantment, which vanilla expresses as an attribute effect
 * ({@code level / (level + 1)}) rather than an item modifier. An item
 * with no attack-damage modifier yields an empty result — a bow never
 * shows melee damage.
 */
public final class DamageCalculator {
        /**
         * The player's base attack damage — the attribute value an
         * empty hand swings with. The <em>generic</em> attribute
         * default is 2.0, but the player supplier overrides it to 1.0
         * (verified in {@code Player.createAttributes}'s 26.3
         * bytecode), and the effective number a normal hit deals is
         * computed on the player's base.
         */
        static final double PLAYER_BASE_ATTACK_DAMAGE = 1.0;
        /** The player's base attack speed (4.0, the same as the generic default). */
        static final double PLAYER_BASE_ATTACK_SPEED = 4.0;
        /** The player's base sweeping ratio (0.0 — no sweep without Sweeping Edge). */
        static final double PLAYER_BASE_SWEEPING = 0.0;

        /**
         * The condition text for the target-conditional damage
         * enchantments, keyed by registry path. The <em>values</em> are
         * always read live from the effect data; only the human phrasing
         * of what the loot condition selects is curated here, because a
         * loot condition is opaque to the tooltip. Unknown conditional
         * enchantments fall back to the honest "(conditional)".
         */
        private static final Map<String, String> CONDITION_LABELS = Map.of(
                        "smite", "vs undead",
                        "bane_of_arthropods", "vs arthropods",
                        "impaling", "vs aquatic mobs");

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
                                PLAYER_BASE_ATTACK_DAMAGE,
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
                                PLAYER_BASE_ATTACK_SPEED,
                                ItemSnapshot.WEAPON_SLOT);
                // The sweeping ratio is the item's own modifier plus the
                // Sweeping Edge enchantment's live attribute contribution
                // (level / (level + 1)); vanilla swords carry no base
                // sweeping modifier, so an unenchanted sword reports the
                // attribute default and stays hidden.
                double sweeping = modifiers.compute(
                                Attributes.SWEEPING_DAMAGE_RATIO,
                                PLAYER_BASE_SWEEPING,
                                ItemSnapshot.WEAPON_SLOT)
                                + EnchantmentEffects.attributeContribution(
                                                snapshot, Attributes.SWEEPING_DAMAGE_RATIO);
                EnchantmentDamage enchantDamage = enchantmentDamage(snapshot);
                return Optional.of(new ItemStatistics.MeleeDamage(
                                attackDamage,
                                enchantDamage.bonus,
                                enchantDamage.bonusLabel,
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
         * item. Unconditional effects (Sharpness) sum into {@code bonus}
         * with their display names joined into {@code bonusLabel};
         * conditional effects (Smite, Bane of Arthropods — gated on a
         * target mob family the tooltip does not have) become
         * {@link ItemStatistics.ConditionalBonus} entries labelled with
         * the enchantment's fullname <em>at the level actually held</em>
         * and the curated condition text.
         */
        private static EnchantmentDamage enchantmentDamage(ItemSnapshot snapshot) {
                if (!snapshot.hasEnchantments()) {
                        return new EnchantmentDamage(0.0, "", List.of());
                }
                RandomSource random = RandomSource.create(EnchantmentEffects.EFFECT_SEED);
                double bonus = 0.0;
                List<String> bonusLabels = new ArrayList<>();
                List<ItemStatistics.ConditionalBonus> conditional = new ArrayList<>();
                for (ItemSnapshot.EnchantmentEntry entry : snapshot.enchantmentEntries()) {
                        Holder<Enchantment> holder = entry.enchantment();
                        int level = entry.level();
                        List<ConditionalEffect<EnchantmentValueEffect>> effects =
                                        holder.value().getEffects(EnchantmentEffectComponents.DAMAGE);
                        if (effects.isEmpty()) {
                                continue;
                        }
                        String fullname = Enchantment.getFullname(holder, level).getString();
                        String condition = holder.unwrapKey()
                                        .map(key -> CONDITION_LABELS.get(key.identifier().getPath()))
                                        .orElse(null);
                        for (ConditionalEffect<EnchantmentValueEffect> conditionalEffect : effects) {
                                // process(level, random, base) — base 0 because
                                // enchantment damage is additive, not a multiplier
                                // on the item's damage.
                                double value = conditionalEffect.effect().process(level, random, 0.0f);
                                if (conditionalEffect.requirements().isEmpty()) {
                                        bonus += value;
                                        bonusLabels.add(fullname);
                                } else {
                                        conditional.add(new ItemStatistics.ConditionalBonus(
                                                        fullname + " (" + (condition != null
                                                                        ? condition : "conditional") + ")",
                                                        value));
                                }
                        }
                }
                return new EnchantmentDamage(bonus, String.join(" + ", bonusLabels), List.copyOf(conditional));
        }

        private record EnchantmentDamage(
                        double bonus,
                        String bonusLabel,
                        List<ItemStatistics.ConditionalBonus> conditional) {
        }
}
