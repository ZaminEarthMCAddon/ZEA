package net.zaminmc.zea.itempresentation.calculation;

import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The wielder half of a stat calculation — the player whose hands the
 * hovered item is (or is not) in, read live from the attribute system.
 *
 * <p>Two questions a stat cannot answer from the item alone live here.
 * First, <em>is this stack actually held?</em> {@link #holds} is
 * reference identity against the player's live main-hand stack — the
 * very instance the inventory slot hands the tooltip — so an identical
 * item hovered elsewhere in the inventory is not "held", and switching
 * the selected hotbar slot moves the identity away from the old stack.
 * Second, <em>what does the wielder contribute?</em> The player's base
 * attack damage and attack speed are read from the attribute instances
 * ({@code getBaseValue}) rather than assumed, and the active
 * attack-damage effects — Strength, Weakness, anything a datapack
 * registers on {@link Attributes#ATTACK_DAMAGE} — are enumerated with
 * their per-level amounts, so the tooltip can annotate what the player
 * adds on top of the item.
 *
 * <p>The context is built per tooltip, never cached: every read is a
 * live attribute query, so an effect expiring or a modifier changing is
 * reflected the next time a tooltip is built. {@link #NONE} is the
 * no-wielder form — item-only statistics, no player contribution.
 */
public record WielderContext(
                ItemStack heldStack,
                double attackDamageBase,
                double attackDamageLive,
                double attackSpeedLive,
                List<EffectAnnotation> attackDamageEffects) {

        /** The no-wielder form: item-only statistics, no player contribution. */
        public static final WielderContext NONE =
                        new WielderContext(ItemStack.EMPTY, 1.0, 1.0, 4.0, List.of());

        /**
         * Reads the wielder context from {@code player} — the live
         * main-hand stack, the attack-damage and attack-speed attribute
         * instances, and the active effects that modify attack damage.
         * {@code null} (outside a world) yields {@link #NONE}.
         */
        public static WielderContext of(@Nullable Player player) {
                if (player == null) {
                        return NONE;
                }
                AttributeInstance damage = player.getAttribute(Attributes.ATTACK_DAMAGE);
                AttributeInstance speed = player.getAttribute(Attributes.ATTACK_SPEED);
                return new WielderContext(
                                player.getMainHandItem(),
                                damage != null ? damage.getBaseValue() : NONE.attackDamageBase,
                                damage != null ? damage.getValue() : NONE.attackDamageLive,
                                speed != null ? speed.getValue() : NONE.attackSpeedLive,
                                attackDamageEffects(player));
        }

        /**
         * Whether {@code stack} is the stack this wielder is actually
         * holding — reference identity against the live main-hand
         * stack, not item-type equality. An identical item elsewhere in
         * the inventory is a different instance and is not held.
         */
        public boolean holds(@Nullable ItemStack stack) {
                return stack != null && !stack.isEmpty() && stack == heldStack;
        }

        /**
         * The active attack-damage effects, one entry per effect whose
         * data registers an {@link Attributes#ATTACK_DAMAGE} modifier:
         * the effect's display name and its total amount at the active
         * amplifier (Strength I → {@code +3.0}, Weakness I → {@code
         * -4.0}). The amounts come from the live effect data via
         * {@link MobEffect#createModifiers} — the same templates the
         * game applies when the effect lands — so a datapack that
         * retunes Strength is reported at its new rate.
         */
        private static List<EffectAnnotation> attackDamageEffects(Player player) {
                List<EffectAnnotation> effects = new ArrayList<>();
                for (MobEffectInstance instance : player.getActiveEffects()) {
                        MobEffect effect = instance.getEffect().value();
                        double[] total = {0.0};
                        boolean[] touches = {false};
                        effect.createModifiers(instance.getAmplifier(), (attribute, modifier) -> {
                                if (attribute == Attributes.ATTACK_DAMAGE) {
                                        touches[0] = true;
                                        total[0] += modifier.amount();
                                }
                        });
                        if (touches[0] && total[0] != 0.0) {
                                effects.add(new EffectAnnotation(
                                                effect.getDisplayName().getString(), total[0]));
                        }
                }
                return List.copyOf(effects);
        }

        /**
         * One active effect's attack-damage contribution: the effect's
         * display name and the amount it adds at its active amplifier.
         * The amount is the annotation's number ({@code +3 Strength});
         * the total it rides with comes from the live attribute value,
         * so the two agreeing is a testable property rather than an
         * assumption.
         */
        public record EffectAnnotation(String label, double amount) {
        }
}
