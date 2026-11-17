package net.zaminmc.zea.itempresentation.calculation;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.zaminmc.zea.VanillaBootstrap;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mining Fortune's suppression rules: the calculator reads Fortune's
 * level by registry path, and Silk Touch's presence on the same tool
 * suppresses the statistic — the two are mutually exclusive in normal
 * vanilla enchanting, and the blocked case belongs to the indicators,
 * never to a number here. A tool without Fortune shows no percentage
 * at all, and Fortune is never derived from Efficiency.
 */
class MiningFortuneSuppressionTest {
        static {
                VanillaBootstrap.ensure();
        }

        private final KeyedEnchantments keyed = new KeyedEnchantments();

        @Test
        void fortuneAloneYieldsThePercentage() {
                ItemSnapshot snapshot = snapshot(enchantments(
                                fortune(), 3));
                assertTrue(MiningFortuneCalculator.calculate(snapshot).isPresent());
                assertEquals(120.0, MiningFortuneCalculator.calculate(snapshot).orElseThrow().percentBonus(), 1e-9);
                assertEquals(3, MiningFortuneCalculator.calculate(snapshot).orElseThrow().fortuneLevel());
        }

        @Test
        void silkTouchBlocksFortune() {
                ItemSnapshot snapshot = snapshot(enchantments(
                                fortune(), 3, silkTouch(), 1));
                assertTrue(MiningFortuneCalculator.calculate(snapshot).isEmpty(),
                                "silks touch conflicts with fortune — the blocked case is the indicators'");
        }

        @Test
        void noFortuneYieldsNothing() {
                ItemSnapshot snapshot = snapshot(enchantments(
                                silkTouch(), 1));
                assertTrue(MiningFortuneCalculator.calculate(snapshot).isEmpty());
        }

        private Holder<Enchantment> fortune() {
                return keyed.holder("fortune", "Fortune", effects -> { });
        }

        private Holder<Enchantment> silkTouch() {
                return keyed.holder("silk_touch", "Silk Touch", effects -> { });
        }

        private static ItemEnchantments enchantments(Holder<Enchantment> first, int firstLevel,
                        Holder<Enchantment> second, int secondLevel) {
                ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
                mutable.set(first, firstLevel);
                mutable.set(second, secondLevel);
                return mutable.toImmutable();
        }

        private static ItemEnchantments enchantments(Holder<Enchantment> first, int firstLevel) {
                ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
                mutable.set(first, firstLevel);
                return mutable.toImmutable();
        }

        private static ItemSnapshot snapshot(ItemEnchantments enchantments) {
                return new ItemSnapshot(
                                net.minecraft.world.item.component.ItemAttributeModifiers.EMPTY, enchantments,
                                null, null, null, null, 1561, 0);
        }
}
