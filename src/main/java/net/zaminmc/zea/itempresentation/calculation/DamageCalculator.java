package net.zaminmc.zea.itempresentation.calculation;

import net.minecraft.core.Holder;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ai.attributes.Attributes;
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
 * <p>The damage is split by source. The item's own contribution is the
 * {@link ItemAttributeModifiers} component's {@code compute} of
 * {@link Attributes#ATTACK_DAMAGE} for the main-hand slot minus the
 * baseline it started from — the diamond sword's +6 lands on top of
 * whatever base the item is being evaluated against, and the delta is
 * the item's, never the player's. The player's base (1.0, verified in
 * {@code Player.createAttributes}'s 26.3 bytecode) is read from the
 * wielder's live attribute instance — not assumed — and enters the
 * total only when the hovered stack is the stack actually held: a
 * spare sword in a chest slot deals its own damage, not "item plus
 * fist". Active attack-damage effects (Strength's +3 per level, read
 * live from the attribute value) join the total under the same held
 * condition, with the effect's name riding along for the annotation.
 *
 * <p>Enchantment damage is read from the enchantment's {@link
 * EnchantmentEffectComponents#DAMAGE} effects through the live
 * {@link Enchantment#getEffects} API — the data-driven effect list, not
 * a hardcoded Sharpness formula (the 26.3 data writes {@code 1 + 0.5
 * per level above first}, which is the {@code 0.5 x level + 0.5} rule
 * at every level). Each effect's {@link EnchantmentValueEffect#process}
 * is evaluated with a fixed-seed random source so the tooltip is
 * deterministic. Unconditional effects contribute to {@code
 * enchantmentBonus} (labelled, so the presentation can bracket the
 * enchantment's level); effects whose condition cannot be evaluated
 * without a target (Smite vs undead, Bane of Arthropods vs arthropods)
 * are listed as conditional bonuses instead of folded in.
 *
 * <p>The attack speed is the effective value — base plus the item's
 * main-hand modifier, the number the cooldown actually ticks down
 * from — never the raw {@code -2.4}. When held it comes straight off
 * the live {@code ATTACK_SPEED} instance (the wielder's effects
 * included); when not, from the item's modifiers over the player
 * base. The sweeping ratio is the item's own modifier plus the
 * Sweeping Edge enchantment's live {@code sweeping_damage_ratio}
 * contribution ({@code level / (level + 1)} in the 26.3 data) — the
 * fraction of the hit a sweep deals to nearby entities. An item with
 * no attack-damage modifier yields an empty result — a bow never
 * shows melee damage.
 */
public final class DamageCalculator {
        /**
         * The player's base attack damage — the attribute value an
         * empty hand swings with, used when no wielder is in play (a
         * hovered stack nobody is holding is still an item a player
         * could pick up). The <em>generic</em> attribute default is
         * 2.0, but the player supplier overrides it to 1.0 (verified
         * in {@code Player.createAttributes}'s 26.3 bytecode); a held
         * stack reads the actual base from the wielder's instance.
         */
        static final double PLAYER_BASE_ATTACK_DAMAGE = 1.0;
        /** The player's base attack speed (4.0, the same as the generic default). */
        static final double PLAYER_BASE_ATTACK_SPEED = 4.0;
        /** The player's base sweeping ratio (0.0 — no sweep without Sweeping Edge). */
        static final double PLAYER_BASE_SWEEPING = 0.0;

        /**
         * The RPG stat name for the target-conditional damage
         * enchantments, keyed by registry path — the stat each
         * enchantment modifies, in the Hypixel-style vocabulary:
         * Smite is Undead Damage, Bane of Arthropods is Arthropod
         * Damage, Impaling is Aquatic Damage. The <em>values</em> are
         * always read live from the effect data — the enchantment's own
         * loot condition, and with it the mob classification, stays the
         * game's, never a manually written mob list. A conditional
         * enchantment outside the mapping keeps the honest enchantment
         * name with an "(conditional)" suffix instead of inventing a
         * stat name for it.
         */
        private static final Map<String, String> CONDITION_STATS = Map.of(
                        "smite", "Undead Damage",
                        "bane_of_arthropods", "Arthropod Damage",
                        "impaling", "Aquatic Damage");

        private DamageCalculator() {
        }

        /**
         * The melee damage for an item from {@code snapshot}. {@code
         * held} is the engine's decision, from the wielder context's
         * reference-identity check, that the hovered stack is the stack
         * actually held — it decides whether the player's base and
         * active effects join the total.
         */
        public static Optional<ItemStatistics.MeleeDamage> calculate(
                        boolean held, ItemSnapshot snapshot, WielderContext wielder) {
                var modifiers = snapshot.attributeModifiers();
                // An item with no attack-damage modifier computes back to
                // the attribute default (1.0 for ATTACK_DAMAGE). That is
                // the player's fist, not a weapon statistic — bows and
                // tools that don't swing for damage are not melee weapons.
                if (!hasAttackDamageModifier(modifiers)) {
                        return Optional.empty();
                }
                double base = held ? wielder.attackDamageBase() : PLAYER_BASE_ATTACK_DAMAGE;
                // The item's own contribution: the full main-hand value
                // minus the base it was computed from. add_value lands on
                // top of the base; a multiply-style modifier scales the
                // same way in both terms, so the delta is the item's own.
                double itemDamage = modifiers.compute(
                                Attributes.ATTACK_DAMAGE,
                                base,
                                ItemSnapshot.WEAPON_SLOT) - base;
                double attackSpeed = held
                                ? wielder.attackSpeedLive()
                                : modifiers.compute(
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
                double playerDamage = held ? base : 0.0;
                // Active attack-damage effects join the held total at their
                // enumerated amounts — Strength's +3 per level, a Weakness's
                // -4, whatever the live effect data registers on the
                // attribute. The amounts come from the effect templates the
                // game itself applies, read live per tooltip; the attack-
                // damage instance's value is deliberately not differenced
                // against them, because the client's instance does not carry
                // the held item's equipment modifier to subtract cleanly.
                double effectBonus = 0.0;
                String effectLabel = "";
                if (held) {
                        for (WielderContext.EffectAnnotation effect : wielder.attackDamageEffects()) {
                                effectBonus += effect.amount();
                        }
                        if (Math.abs(effectBonus) < 1e-9) {
                                effectBonus = 0.0;
                                effectLabel = "";
                        } else {
                                effectLabel = effectLabel(wielder, effectBonus);
                        }
                }
                return Optional.of(new ItemStatistics.MeleeDamage(
                                itemDamage,
                                enchantDamage.bonus,
                                enchantDamage.bonusLabel,
                                enchantDamage.bonusLevel,
                                playerDamage,
                                effectBonus,
                                effectLabel,
                                attackSpeed,
                                sweeping,
                                enchantDamage.conditional));
        }

        /**
         * The names behind {@code effectBonus} — the active attack-
         * damage effects' display names, joined. {@code effectBonus} is
         * exactly the sum of the context's enumerated amounts, so the
         * label always accounts for the annotation's number.
         */
        private static String effectLabel(WielderContext wielder, double effectBonus) {
                if (effectBonus == 0.0 || wielder.attackDamageEffects().isEmpty()) {
                        return "";
                }
                List<String> names = new ArrayList<>();
                for (WielderContext.EffectAnnotation effect : wielder.attackDamageEffects()) {
                        names.add(effect.label());
                }
                return String.join(" + ", names);
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
                        return new EnchantmentDamage(0.0, "", 0, List.of());
                }
                RandomSource random = RandomSource.create(EnchantmentEffects.EFFECT_SEED);
                double bonus = 0.0;
                int bonusLevel = 0;
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
                        for (ConditionalEffect<EnchantmentValueEffect> conditionalEffect : effects) {
                                // process(level, random, base) — base 0 because
                                // enchantment damage is additive, not a multiplier
                                // on the item's damage.
                                double value = conditionalEffect.effect().process(level, random, 0.0f);
                                if (conditionalEffect.requirements().isEmpty()) {
                                        bonus += value;
                                        bonusLevel = Math.max(bonusLevel, level);
                                        bonusLabels.add(fullname);
                                } else {
                                        String statName = holder.unwrapKey()
                                                        .map(key -> CONDITION_STATS.get(key.identifier().getPath()))
                                                        .orElse(null);
                                        conditional.add(new ItemStatistics.ConditionalBonus(
                                                        statName != null ? statName
                                                                        : fullname + " (conditional)",
                                                        value));
                                }
                        }
                }
                return new EnchantmentDamage(bonus, String.join(" + ", bonusLabels), bonusLevel, List.copyOf(conditional));
        }

        private record EnchantmentDamage(
                        double bonus,
                        String bonusLabel,
                        int bonusLevel,
                        List<ItemStatistics.ConditionalBonus> conditional) {
        }
}
