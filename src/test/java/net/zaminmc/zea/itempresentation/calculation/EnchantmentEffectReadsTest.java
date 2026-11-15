package net.zaminmc.zea.itempresentation.calculation;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.item.enchantment.ConditionalEffect;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.enchantment.LevelBasedValue;
import net.minecraft.world.item.enchantment.effects.AddValue;
import net.minecraft.world.item.enchantment.effects.AllOf;
import net.minecraft.world.item.enchantment.effects.ApplyEntityImpulse;
import net.minecraft.world.item.enchantment.effects.EnchantmentAttributeEffect;
import net.minecraft.world.item.enchantment.effects.EnchantmentValueEffect;
import net.minecraft.world.item.enchantment.effects.RemoveBinomial;
import net.minecraft.world.phys.Vec3;
import net.zaminmc.zea.VanillaBootstrap;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The live effect reads, verified against effect data constructed
 * exactly as the 26.3 vanilla datapack writes it — the same component
 * shapes the calculators meet at runtime. Registry-keyed lookups
 * (Unbreaking's, Lunge's, Breach's) cannot be exercised with
 * key-less direct holders; their selection logic is pinned in
 * {@link CalculationMathTest} and their values verified at runtime.
 */
class EnchantmentEffectReadsTest {
        static {
                VanillaBootstrap.ensure();
        }

        /**
         * Efficiency's live read: the vanilla data writes a
         * {@code mining_efficiency} attribute effect with a
         * {@code levels_squared} amount of {@code level squared + 1},
         * so Efficiency V contributes 26 to a diamond pickaxe's 8.
         */
        @Test
        void efficiencyReadsTheLiveSquaredRate() {
                Holder<Enchantment> efficiency = enchantment("Efficiency", effects -> effects.set(
                                EnchantmentEffectComponents.ATTRIBUTES,
                                List.of(new EnchantmentAttributeEffect(
                                                Identifier.fromNamespaceAndPath("zea", "test.efficiency"),
                                                Attributes.MINING_EFFICIENCY,
                                                new LevelBasedValue.LevelsSquared(1.0f),
                                                AttributeModifier.Operation.ADD_VALUE))));
                ItemSnapshot snapshot = snapshot(
                                enchantments(efficiency, 5),
                                new Tool(List.of(), 8.0f, 1, true));
                Optional<ItemStatistics.MiningSpeed> mining = MiningSpeedCalculator.calculate(null, snapshot);
                assertTrue(mining.isPresent());
                assertEquals(8.0, mining.orElseThrow().baseSpeed(), 1e-9);
                assertEquals(26.0, mining.orElseThrow().efficiencyBonus(), 1e-9,
                                "Efficiency V must read level squared plus one: 26");
                assertEquals(34.0, mining.orElseThrow().correctForDrops(), 1e-9);
        }

        /**
         * Sweeping Edge's live read: the vanilla data writes the
         * {@code sweeping_damage_ratio} attribute as a fraction of
         * {@code level / (level + 1)}, on top of the item's own
         * (default-zero) modifier.
         */
        @Test
        void sweepingEdgeReadsTheLiveFraction() {
                Holder<Enchantment> sweeping = enchantment("Sweeping Edge", effects -> effects.set(
                                EnchantmentEffectComponents.ATTRIBUTES,
                                List.of(new EnchantmentAttributeEffect(
                                                Identifier.fromNamespaceAndPath("zea", "test.sweeping"),
                                                Attributes.SWEEPING_DAMAGE_RATIO,
                                                new LevelBasedValue.Fraction(
                                                                new LevelBasedValue.Linear(1.0f, 1.0f),
                                                                new LevelBasedValue.Linear(2.0f, 1.0f)),
                                                AttributeModifier.Operation.ADD_VALUE))));
                ItemSnapshot snapshot = snapshot(
                                enchantments(sweeping, 3),
                                null,
                                meleeModifiers(7.0, -2.4));
                Optional<ItemStatistics.MeleeDamage> melee = DamageCalculator.calculate(null, snapshot);
                assertTrue(melee.isPresent());
                assertEquals(0.75, melee.orElseThrow().sweepingRatio(), 1e-9,
                                "Sweeping Edge III must read level / (level + 1)");
        }

        /**
         * Sharpness's live read through the damage effects: the vanilla
         * data is an {@code add} of {@code 1 + 0.5 per level above
         * first}, so Sharpness V adds 3 and the presentation split
         * carries the enchantment's fullname.
         */
        @Test
        void sharpnessReadsTheLiveDamageEffect() {
                Holder<Enchantment> sharpness = enchantment("Sharpness", effects -> effects.set(
                                EnchantmentEffectComponents.DAMAGE,
                                List.of(new ConditionalEffect<>(
                                                new AddValue(LevelBasedValue.perLevel(1.0f, 0.5f)),
                                                Optional.empty()))));
                ItemSnapshot snapshot = snapshot(
                                enchantments(sharpness, 5),
                                null,
                                meleeModifiers(7.0, -2.4));
                Optional<ItemStatistics.MeleeDamage> melee = DamageCalculator.calculate(null, snapshot);
                assertTrue(melee.isPresent());
                assertEquals(3.0, melee.orElseThrow().enchantmentBonus(), 1e-9);
                assertEquals(8.0, melee.orElseThrow().baseDamage(), 1e-9,
                                "the player's base is 1.0, not the generic attribute default 2.0");
                assertEquals(11.0, melee.orElseThrow().total(), 1e-9);
                assertEquals("Sharpness V", melee.orElseThrow().bonusLabel());
                assertEquals(1.6, melee.orElseThrow().attackSpeed(), 1e-9);
        }

        /**
         * Unbreaking's live read: the vanilla data ships two
         * {@code remove_binomial} branches whose chances at the held
         * level resolve through the same fraction arithmetic the game
         * uses — 3/4 for tools, 6/20 for armor at level 3.
         */
        @Test
        void unbreakingReadsTheLiveBranchChances() {
                Holder<Enchantment> unbreaking = enchantment("Unbreaking", effects -> effects.set(
                                EnchantmentEffectComponents.ITEM_DAMAGE,
                                List.of(
                                                new ConditionalEffect<>(new RemoveBinomial(new LevelBasedValue.Fraction(
                                                                                new LevelBasedValue.Linear(2.0f, 2.0f),
                                                                                new LevelBasedValue.Linear(10.0f, 5.0f))),
                                                                Optional.empty()),
                                                new ConditionalEffect<>(new RemoveBinomial(new LevelBasedValue.Fraction(
                                                                                new LevelBasedValue.Linear(1.0f, 1.0f),
                                                                                new LevelBasedValue.Linear(2.0f, 1.0f))),
                                                                Optional.empty()))));
                ItemEnchantments enchantments = enchantments(unbreaking, 3);
                ItemSnapshot toolSnapshot = new ItemSnapshot(
                                ItemAttributeModifiers.EMPTY, enchantments, null, null, null, null, null, null, 100, 0);
                assertEquals(4.0, DurabilityCalculator.unbreakingFactor(toolSnapshot, false), 1e-9,
                                "the tool branch refunds 3/4 at level 3: x4 lifetime");
                ItemSnapshot armorSnapshot = new ItemSnapshot(
                                ItemAttributeModifiers.EMPTY, enchantments, null, null, null, null, null, null, 100, 0);
                assertEquals(1.0 / 0.7, DurabilityCalculator.unbreakingFactor(armorSnapshot, true), 1e-6,
                                "the armor branch refunds 6/20 at level 3: x1.43 lifetime");
        }

        /**
         * The bow's Power bonus through the live damage effects, and
         * the full-draw damage that scales it through the velocity:
         * Power V reads 3, so the fully-drawn arrow deals
         * ceil(3 x (2 + 3)) = 15.
         */
        @Test
        void bowPowerScalesThroughTheDrawVelocity() {
                Holder<Enchantment> power = enchantment("Power", effects -> effects.set(
                                EnchantmentEffectComponents.DAMAGE,
                                List.of(new ConditionalEffect<>(
                                                new AddValue(LevelBasedValue.perLevel(1.0f, 0.5f)),
                                                Optional.empty()))));
                ItemSnapshot snapshot = snapshot(enchantments(power, 5), null);
                ItemStatistics.ProjectileStats projectile = ProjectileCalculator.bowStats(snapshot);
                assertEquals(3.0, projectile.powerBonus(), 1e-9);
                assertEquals(15.0, projectile.fullyCharged(), 1e-9);
                assertEquals(20, projectile.chargeTicks(), "the bow's full draw is 20 ticks");
        }

        /**
         * The crossbow's live enchantment reads: Quick Charge's
         * {@code crossbow_charge_time} folds the 1.25 s base down to
         * 0.5 s (10 ticks) at level 3, Multishot's {@code
         * projectile_count} makes three arrows, and Piercing adds its
         * level.
         */
        @Test
        void crossbowReadsQuickChargeMultishotAndPiercing() {
                Holder<Enchantment> quickCharge = enchantment("Quick Charge", effects -> effects.set(
                                EnchantmentEffectComponents.CROSSBOW_CHARGE_TIME,
                                new AddValue(LevelBasedValue.perLevel(-0.25f))));
                Holder<Enchantment> multishot = enchantment("Multishot", effects -> effects.set(
                                EnchantmentEffectComponents.PROJECTILE_COUNT,
                                List.of(new ConditionalEffect<>(
                                                new AddValue(LevelBasedValue.perLevel(2.0f)), Optional.empty()))));
                Holder<Enchantment> piercing = enchantment("Piercing", effects -> effects.set(
                                EnchantmentEffectComponents.PROJECTILE_PIERCING,
                                List.of(new ConditionalEffect<>(
                                                new AddValue(LevelBasedValue.perLevel(1.0f)), Optional.empty()))));
                ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
                mutable.set(quickCharge, 3);
                mutable.set(multishot, 1);
                mutable.set(piercing, 4);
                ItemSnapshot snapshot = new ItemSnapshot(ItemAttributeModifiers.EMPTY, mutable.toImmutable(),
                                null, null, null, null, null, null, 100, 0);
                ItemStatistics.ProjectileStats projectile = ProjectileCalculator.crossbowStats(snapshot);
                assertEquals(10, projectile.chargeTicks(),
                                "Quick Charge III must halve the reload to 10 ticks");
                assertEquals(3, projectile.projectileCount(), "Multishot shoots three arrows");
                assertEquals(4, projectile.piercing());
                assertEquals(7.0, projectile.fullyCharged(), 1e-9,
                                "the crossbow arrow deals ceil(3.15 x 2)");
                assertTrue(!projectile.firework());
        }

        /**
         * The mace's Density reads its live per-fallen-block rate —
         * the vanilla data grants {@code 0.5 x level}, so Density V
         * adds 2.5 per block — and the smash bands carry the
         * bytecode-verified curve.
         */
        @Test
        void maceDensityReadsTheLivePerBlockRate() {
                Holder<Enchantment> density = enchantment("Density", effects -> effects.set(
                                EnchantmentEffectComponents.SMASH_DAMAGE_PER_FALLEN_BLOCK,
                                List.of(new ConditionalEffect<>(
                                                new AddValue(LevelBasedValue.perLevel(0.5f)), Optional.empty()))));
                ItemSnapshot snapshot = snapshot(enchantments(density, 5), null);
                assertEquals(2.5, MaceCalculator.densityPerFallenBlock(snapshot), 1e-9,
                                "Density V reads 0.5 x level per fallen block");
        }

        /**
         * Lunge's dash strength walks the live impulse effect inside
         * its {@code all_of}: the vanilla data scales
         * {@code 0.458 x level}, so Lunge III dashes at 1.374.
         */
        @Test
        void lungeImpulseWalksTheLiveEffectTree() {
                Holder<Enchantment> lunge = enchantment("Lunge", effects -> effects.set(
                                EnchantmentEffectComponents.POST_PIERCING_ATTACK,
                                List.of(new ConditionalEffect<>(
                                                new AllOf.EntityEffects(List.of(
                                                                new ApplyEntityImpulse(
                                                                                new Vec3(0.0, 0.0, 1.0),
                                                                                new Vec3(1.0, 0.0, 1.0),
                                                                                LevelBasedValue.perLevel(0.458f)))),
                                                Optional.empty()))));
                assertEquals(1.374, SpearCalculator.lungeImpulse(lunge, 3), 1e-3);
                assertEquals(0.458, SpearCalculator.lungeImpulse(lunge, 1), 1e-3);
        }

        /**
         * A protection enchantment's EPF read: plain Protection's
         * {@code damage_protection} effect is {@code level}, so
         * Protection IV on a chestplate lists 4 EPF against most
         * damage.
         */
        @Test
        void protectionReadsTheLiveEpf() {
                Holder<Enchantment> protection = enchantment("Protection", effects -> effects.set(
                                EnchantmentEffectComponents.DAMAGE_PROTECTION,
                                List.of(new ConditionalEffect<>(
                                                new AddValue(LevelBasedValue.perLevel(1.0f)), Optional.empty()))));
                ItemSnapshot snapshot = snapshot(enchantments(protection, 4), null, armorModifiers());
                Optional<ItemStatistics.ArmorStats> armor = ArmorCalculator.calculate(null, snapshot);
                assertTrue(armor.isPresent());
                assertEquals(1, armor.orElseThrow().protectionEnchantments().size());
                assertEquals(4.0, armor.orElseThrow().protectionEnchantments().get(0).value(), 1e-9);
                assertTrue(armor.orElseThrow().protectionEnchantments().get(0).label().startsWith("Protection IV"));
                assertEquals(8.0, armor.orElseThrow().armor(), 1e-9);
        }

        // ---------------------------------------------------------------------
        // Fixture helpers
        // ---------------------------------------------------------------------

        /** An enchantment whose effect map the given builder populates. */
        private static Holder<Enchantment> enchantment(String display,
                        java.util.function.Consumer<DataComponentMap.Builder> effects) {
                DataComponentMap.Builder builder = DataComponentMap.builder();
                effects.accept(builder);
                return Holder.direct(new Enchantment(
                                Component.literal(display),
                                Enchantment.definition(
                                                HolderSet.empty(),
                                                10,
                                                5,
                                                Enchantment.constantCost(1),
                                                Enchantment.constantCost(1),
                                                0,
                                                EquipmentSlotGroup.ANY),
                                HolderSet.empty(),
                                builder.build()));
        }

        private static ItemEnchantments enchantments(Holder<Enchantment> enchantment, int level) {
                ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
                mutable.set(enchantment, level);
                return mutable.toImmutable();
        }

        private static ItemSnapshot snapshot(ItemEnchantments enchantments, Tool tool) {
                return new ItemSnapshot(ItemAttributeModifiers.EMPTY, enchantments, tool,
                                null, null, null, null, null, 100, 0);
        }

        private static ItemSnapshot snapshot(ItemEnchantments enchantments, Tool tool,
                        ItemAttributeModifiers modifiers) {
                return new ItemSnapshot(modifiers, enchantments, tool,
                                null, null, null, null, null, 100, 0);
        }

        /** A sword-shaped modifier set: +7 damage, attack speed 1.6. */
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

        /** A chestplate-shaped modifier set: +8 armor in the chest slot. */
        private static ItemAttributeModifiers armorModifiers() {
                return ItemAttributeModifiers.builder()
                                .add(Attributes.ARMOR,
                                                new AttributeModifier(Identifier.fromNamespaceAndPath("zea", "test.armor"),
                                                                8.0, AttributeModifier.Operation.ADD_VALUE),
                                                EquipmentSlotGroup.CHEST)
                                .build();
        }
}
