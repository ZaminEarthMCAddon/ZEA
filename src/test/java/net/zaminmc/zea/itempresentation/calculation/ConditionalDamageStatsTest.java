package net.zaminmc.zea.itempresentation.calculation;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.item.enchantment.ConditionalEffect;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.enchantment.LevelBasedValue;
import net.minecraft.world.item.enchantment.effects.AddValue;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.zaminmc.zea.VanillaBootstrap;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The RPG stat names for the target-conditional damage enchantments:
 * Smite reads as Undead Damage, Bane of Arthropods as Arthropod
 * Damage, Impaling as Aquatic Damage — the Hypixel-style vocabulary —
 * with the values still read live from each enchantment's own effect
 * data. A conditional enchantment outside the mapping keeps the
 * honest enchantment name with an "(conditional)" suffix instead of
 * inventing a stat name.
 *
 * <p>The fixtures use registry-keyed holders (the path mapping is the
 * seam under test) whose DAMAGE effects carry a non-empty requirement
 * holder — the shape that routes a contribution into the conditional
 * list rather than the unconditional bonus.
 */
class ConditionalDamageStatsTest {
	static {
		VanillaBootstrap.ensure();
	}

	private static final Holder<LootItemCondition> SOME_CONDITION =
			Holder.direct(new LootItemCondition() {
				@Override
				public boolean test(net.minecraft.world.level.storage.loot.LootContext context) {
					return true;
				}

				@Override
				public com.mojang.serialization.MapCodec<? extends LootItemCondition> codec() {
					throw new UnsupportedOperationException("never serialized in tests");
				}
			});

	@Test
	void smiteReadsAsUndeadDamage() {
		ItemStatistics.MeleeDamage melee = damageWithConditional("smite", "Smite", 0.5);
		assertEquals(1, melee.conditional().size());
		assertEquals("Undead Damage", melee.conditional().get(0).label(),
				"smite's stat name is Undead Damage");
		assertEquals(2.0, melee.conditional().get(0).value(), 1e-9,
				"the value is the enchantment's own per-level effect at level 5");
	}

	@Test
	void baneOfArthropodsReadsAsArthropodDamage() {
		ItemStatistics.MeleeDamage melee = damageWithConditional("bane_of_arthropods", "Bane of Arthropods", 0.5);
		assertEquals("Arthropod Damage", melee.conditional().get(0).label(),
				"bane of arthropods' stat name is Arthropod Damage");
	}

	@Test
	void impalingReadsAsAquaticDamage() {
		ItemStatistics.MeleeDamage melee = damageWithConditional("impaling", "Impaling", 0.5);
		assertEquals("Aquatic Damage", melee.conditional().get(0).label(),
				"impaling's stat name is Aquatic Damage — the enchantment's own loot condition stays the mob classifier");
	}

	@Test
	void unknownConditionalEnchantmentsKeepTheirName() {
		ItemStatistics.MeleeDamage melee = damageWithConditional("cursed_edge", "Cursed Edge", 1.0);
		assertEquals("Cursed Edge V (conditional)", melee.conditional().get(0).label(),
				"an unmapped conditional stays honest instead of inventing a stat");
	}

	@Test
	void unconditionalDamageStillFoldsIntoTheBonus() {
		KeyedEnchantments keyed = new KeyedEnchantments();
		Holder<Enchantment> sharpness = keyed.holder("sharpness", "Sharpness", effects -> effects.set(
				EnchantmentEffectComponents.DAMAGE,
				List.of(new ConditionalEffect<>(
						new AddValue(LevelBasedValue.perLevel(1.0f, 0.5f)), Optional.empty()))));
		// perLevel(base, per) reads base + per * (level - 1): level 5 gives 3.0.
		ItemSnapshot snapshot = snapshot(enchantments(sharpness, 5));
		Optional<ItemStatistics.MeleeDamage> melee = DamageCalculator.calculate(false, snapshot,
				WielderContext.NONE);
		assertTrue(melee.isPresent());
		assertEquals(3.0, melee.orElseThrow().enchantmentBonus(), 1e-9,
				"an unconditional effect sums into the bonus");
		assertTrue(melee.orElseThrow().conditional().isEmpty(),
				"an unconditional effect never appears as a conditional row");
	}

	// ------------------------------------------------------------------
	// Fixtures
	// ------------------------------------------------------------------

	/** One keyed conditional-damage enchantment at level 5, routed through the conditional list. */
	private static ItemStatistics.MeleeDamage damageWithConditional(String path, String display, double perLevel) {
		KeyedEnchantments keyed = new KeyedEnchantments();
		Holder<Enchantment> enchantment = keyed.holder(path, display, effects -> effects.set(
				EnchantmentEffectComponents.DAMAGE,
				List.of(new ConditionalEffect<>(
						new AddValue(LevelBasedValue.perLevel(0.0f, (float) perLevel)),
						Optional.of(SOME_CONDITION)))));
		ItemSnapshot snapshot = snapshot(enchantments(enchantment, 5));
		return DamageCalculator.calculate(false, snapshot, WielderContext.NONE).orElseThrow();
	}

	private static ItemEnchantments enchantments(Holder<Enchantment> holder, int level) {
		ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
		mutable.set(holder, level);
		return mutable.toImmutable();
	}

	private static ItemSnapshot snapshot(ItemEnchantments enchantments) {
		return new ItemSnapshot(meleeModifiers(), enchantments,
				null, null, null, null, 1561, 0);
	}

	/** A sword's own attack-damage modifier, so the calculator sees a weapon. */
	private static net.minecraft.world.item.component.ItemAttributeModifiers meleeModifiers() {
		return net.minecraft.world.item.component.ItemAttributeModifiers.builder()
				.add(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE,
						new net.minecraft.world.entity.ai.attributes.AttributeModifier(
								net.minecraft.world.item.Item.BASE_ATTACK_DAMAGE_ID, 6.0,
								net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE),
						net.minecraft.world.entity.EquipmentSlotGroup.MAINHAND)
				.build();
	}
}
