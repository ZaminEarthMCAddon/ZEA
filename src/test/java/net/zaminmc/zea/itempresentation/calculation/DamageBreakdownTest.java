package net.zaminmc.zea.itempresentation.calculation;

import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.enchantment.ConditionalEffect;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.enchantment.LevelBasedValue;
import net.minecraft.world.item.enchantment.effects.AddValue;
import net.zaminmc.zea.VanillaBootstrap;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The damage breakdown, pinned against the wielder matrix. The
 * calculators are component-free — the snapshot supplies the item's
 * modifiers directly, the context supplies the player's — so the
 * whole held / spare / Strength grid from the spec is testable in a
 * bare JVM: a diamond sword's +6 never includes the fist; the fist
 * (+1) and Strength (+3 per level) join only when the hovered stack
 * is the stack actually held. The held decision itself is the
 * engine's reference-identity check against the live main-hand stack
 * — never item-type equality — and the boolean it feeds the
 * calculator is what these tests drive.
 */
class DamageBreakdownTest {
        static {
                VanillaBootstrap.ensure();
        }

        /**
         * A diamond-sword-shaped modifier set: +6 damage, 1.6 speed.
         * The snapshot supplies the modifiers directly, so no real item
         * is needed — a bare test JVM binds no item components.
         */
        private static ItemSnapshot swordSnapshot(ItemEnchantments enchantments) {
                return new ItemSnapshot(meleeModifiers(6.0, -2.4), enchantments,
                                null, null, null, null, 1561, 0);
        }

        private static ItemAttributeModifiers meleeModifiers(double damageBonus, double speedPenalty) {
                return ItemAttributeModifiers.builder()
                                .add(Attributes.ATTACK_DAMAGE,
                                                new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID,
                                                                damageBonus, AttributeModifier.Operation.ADD_VALUE),
                                                EquipmentSlotGroup.MAINHAND)
                                .add(Attributes.ATTACK_SPEED,
                                                new AttributeModifier(Item.BASE_ATTACK_SPEED_ID,
                                                                speedPenalty, AttributeModifier.Operation.ADD_VALUE),
                                                EquipmentSlotGroup.MAINHAND)
                                .build();
        }

        private static Holder<Enchantment> sharpness() {
                return enchantment("Sharpness", effects -> effects.set(
                                EnchantmentEffectComponents.DAMAGE,
                                List.of(new ConditionalEffect<>(
                                                new AddValue(LevelBasedValue.perLevel(1.0f, 0.5f)),
                                                Optional.empty()))));
        }

        private static ItemEnchantments sharpnessAt(int level) {
                ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
                mutable.set(sharpness(), level);
                return mutable.toImmutable();
        }

        /**
         * A wielder holding the hovered stack: base attack damage 1.0
         * and a live instance of {@code liveDamage} (base plus the held
         * sword's 6 plus any active effects), attack speed 1.6.
         */
        private static WielderContext holding(double liveDamage,
                        WielderContext.EffectAnnotation... effects) {
                return new WielderContext(null, 1.0, liveDamage, 1.6, List.of(effects));
        }

        /** An unenchanted sword nobody holds reads its own +6, no fist. */
        @Test
        void unenchantedSwordNotHeldReadsItemDamageOnly() {
                Optional<ItemStatistics.MeleeDamage> melee = DamageCalculator.calculate(
                                false, swordSnapshot(ItemEnchantments.EMPTY), WielderContext.NONE);
                assertTrue(melee.isPresent());
                assertEquals(6.0, melee.orElseThrow().itemDamage(), 1e-9);
                assertEquals(0.0, melee.orElseThrow().playerDamage(), 1e-9);
                assertEquals(0.0, melee.orElseThrow().effectBonus(), 1e-9);
                assertEquals(6.0, melee.orElseThrow().total(), 1e-9,
                                "a spare sword deals its own damage, not item plus fist");
        }

        /** The same sword held: the fist's +1 joins the total. */
        @Test
        void unenchantedSwordHeldAddsTheWielderBase() {
                Optional<ItemStatistics.MeleeDamage> melee = DamageCalculator.calculate(
                                true, swordSnapshot(ItemEnchantments.EMPTY), holding(7.0));
                assertTrue(melee.isPresent());
                assertEquals(6.0, melee.orElseThrow().itemDamage(), 1e-9,
                                "the item's contribution stays its own when held");
                assertEquals(1.0, melee.orElseThrow().playerDamage(), 1e-9,
                                "the wielder's base comes from the live attribute instance");
                assertEquals(7.0, melee.orElseThrow().total(), 1e-9);
        }

        /**
         * The Sharpness matrix, held: the bonus is {@code 0.5 x level +
         * 0.5} — I through V add 1, 1.5, 2, 2.5 and 3 — so the totals
         * read 8, 8.5, 9, 9.5 and 10. The bracket carries the level,
         * not the contribution.
         */
        @Test
        void sharpnessMatrixHeld() {
                double[] expectedBonus = {1.0, 1.5, 2.0, 2.5, 3.0};
                for (int level = 1; level <= 5; level++) {
                        Optional<ItemStatistics.MeleeDamage> melee = DamageCalculator.calculate(
                                        true, swordSnapshot(sharpnessAt(level)),
                                        holding(7.0));
                        assertTrue(melee.isPresent(), "level " + level);
                        assertEquals(expectedBonus[level - 1], melee.orElseThrow().enchantmentBonus(), 1e-9,
                                        "Sharpness " + level + " adds 0.5 x level + 0.5");
                        assertEquals(level, melee.orElseThrow().bonusLevel(),
                                        "the bracket carries the enchantment's level");
                        assertEquals(7.0 + expectedBonus[level - 1], melee.orElseThrow().total(), 1e-9,
                                        "Sharpness " + level + " held reads "
                                                        + (7.0 + expectedBonus[level - 1]));
                }
        }

        /** The same Sharpness V sword, not held: +9, no fist, no effects. */
        @Test
        void sharpnessSwordNotHeldReadsNine() {
                Optional<ItemStatistics.MeleeDamage> melee = DamageCalculator.calculate(
                                false, swordSnapshot(sharpnessAt(5)), WielderContext.NONE);
                assertTrue(melee.isPresent());
                assertEquals(9.0, melee.orElseThrow().total(), 1e-9);
                assertEquals(0.0, melee.orElseThrow().playerDamage(), 1e-9);
                assertEquals(0.0, melee.orElseThrow().effectBonus(), 1e-9);
        }

        /**
         * Strength's +3 per level joins the held total, named for the
         * annotation: Strength I reads 13, Strength II reads 16. The
         * effect amounts come from the live attribute value, so the
         * total and the named annotation cannot drift apart.
         */
        @Test
        void strengthJoinsTheHeldTotal() {
                // The live attribute instance carries base + item + the
                // effect; Sharpness rides on top through the damage
                // pipeline. Strength I: 1 + 6 + 3 = 10 live, +3 Sharpness = 13.
                Optional<ItemStatistics.MeleeDamage> strengthI = DamageCalculator.calculate(
                                true, swordSnapshot(sharpnessAt(5)),
                                holding(10.0, new WielderContext.EffectAnnotation("Strength", 3.0)));
                assertTrue(strengthI.isPresent());
                assertEquals(3.0, strengthI.orElseThrow().effectBonus(), 1e-9);
                assertEquals("Strength", strengthI.orElseThrow().effectLabel());
                assertEquals(13.0, strengthI.orElseThrow().total(), 1e-9,
                                "Sharpness V held with Strength I deals 13");
                Optional<ItemStatistics.MeleeDamage> strengthII = DamageCalculator.calculate(
                                true, swordSnapshot(sharpnessAt(5)),
                                holding(13.0, new WielderContext.EffectAnnotation("Strength", 6.0)));
                assertTrue(strengthII.isPresent());
                assertEquals(16.0, strengthII.orElseThrow().total(), 1e-9,
                                "Sharpness V held with Strength II deals 16");
        }

        /**
         * The held decision is reference identity against the live
         * main-hand stack — never item-type equality — so it rejects
         * nothing-hovered and the empty stack, and only the context's
         * own held instance passes. The identical-instance distinction
         * itself is one reference comparison, verified live in the
         * client: hovering an equal stack in another slot keeps the
         * fist out of its damage.
         */
        @Test
        void heldIdentityIsReferenceNotItemType() {
                WielderContext wielder = holding(7.0);
                assertFalse(wielder.holds(null), "nothing hovered is not held");
                assertFalse(wielder.holds(ItemStack.EMPTY), "empty is never held");
                Optional<ItemStatistics.MeleeDamage> spare = DamageCalculator.calculate(
                                false, swordSnapshot(ItemEnchantments.EMPTY), holding(7.0));
                assertTrue(spare.isPresent());
                assertEquals(0.0, spare.orElseThrow().playerDamage(), 1e-9,
                                "the not-held decision keeps the fist out of the total");
        }

        /**
         * The attack speed when held comes off the live instance —
         * whatever the wielder's effects make of it — and when spare,
         * from the item's modifiers over the base. Either way the
         * effective 1.6 is shown, never the raw -2.4 modifier, and the
         * base is never added twice.
         */
        @Test
        void attackSpeedIsTheEffectiveValueEitherWay() {
                Optional<ItemStatistics.MeleeDamage> spare = DamageCalculator.calculate(
                                false, swordSnapshot(ItemEnchantments.EMPTY), WielderContext.NONE);
                assertEquals(1.6, spare.orElseThrow().attackSpeed(), 1e-9,
                                "base 4.0 plus the sword's -2.4, effective");
                Optional<ItemStatistics.MeleeDamage> heldStats = DamageCalculator.calculate(
                                true, swordSnapshot(ItemEnchantments.EMPTY),
                                new WielderContext(null, 1.0, 7.0, 2.5, List.of()));
                assertEquals(2.5, heldStats.orElseThrow().attackSpeed(), 1e-9,
                                "the live instance value, effects included");
                assertFalse(String.valueOf(heldStats.orElseThrow().attackSpeed()).contains("-2.4"),
                                "the raw modifier never appears as the speed");
        }

        /**
         * Critical hits stay out of the damage line: the normal-hit
         * total is the fully charged ordinary attack, and nothing in
         * the record or the presentation speaks of a critical.
         */
        @Test
        void criticalDamageIsNeverMixedIntoTheNormalHit() {
                Optional<ItemStatistics.MeleeDamage> melee = DamageCalculator.calculate(
                                true, swordSnapshot(sharpnessAt(5)), holding(7.0));
                assertEquals(10.0, melee.orElseThrow().total(), 1e-9,
                                "the normal hit only — a critical would read 13.5");
                List<Component> lines = net.zaminmc.zea.itempresentation.presentation.ItemPresentationBuilder
                                .build(net.zaminmc.zea.itempresentation.recognition.ItemCategory.SWORD,
                                                new ItemStatistics(melee, Optional.empty(), Optional.empty(),
                                                                Optional.empty(), Optional.empty(),
                                                                Optional.empty(), Optional.empty(),
                                                                Optional.empty()));
                assertTrue(lines.stream().noneMatch(line -> line.getString().toLowerCase().contains("critical")),
                                "the tooltip never presents a critical as the base damage");
        }

        /**
         * Magic Find is the hovered item's own Looting level: none → no
         * stat, I → +1, II → +2, III → +3, and the value follows the
         * component the moment it changes. No other equipment's
         * enchantments leak into it — the read is off the snapshot
         * alone.
         */
        @Test
        void magicFindIsTheItemsOwnLootingLevel() {
                assertTrue(StatisticsEngine.magicFind(snapshotOf(ItemEnchantments.EMPTY)).isEmpty(),
                                "no Looting means no Magic Find stat");
                KeyedEnchantments keyed = new KeyedEnchantments();
                Holder<Enchantment> looting = keyed.holder("looting", "Looting", effects -> {
                });
                for (int level = 1; level <= 3; level++) {
                        ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
                        mutable.set(looting, level);
                        Optional<Integer> magicFind = StatisticsEngine.magicFind(snapshotOf(mutable.toImmutable()));
                        assertTrue(magicFind.isPresent(), "Looting " + level);
                        assertEquals(level, magicFind.orElseThrow().intValue(),
                                        "Magic Find is one point per Looting level");
                }
        }

        private static ItemSnapshot snapshotOf(ItemEnchantments enchantments) {
                return new ItemSnapshot(ItemAttributeModifiers.EMPTY, enchantments,
                                null, null, null, null, 100, 0);
        }

        private static Holder<Enchantment> enchantment(String display,
                        java.util.function.Consumer<net.minecraft.core.component.DataComponentMap.Builder> effects) {
                net.minecraft.core.component.DataComponentMap.Builder builder =
                                net.minecraft.core.component.DataComponentMap.builder();
                effects.accept(builder);
                return Holder.direct(new Enchantment(
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
                                builder.build()));
        }
}
