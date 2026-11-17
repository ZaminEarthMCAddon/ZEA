package net.zaminmc.zea.itempresentation.calculation;

import net.minecraft.world.item.Items;
import net.zaminmc.zea.VanillaBootstrap;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Breaking Power's no-fabrication contract: no authoritative source
 * exists today — EarthMC's items are vanilla, vanilla pickaxes carry
 * no breaking power — so every item resolves to empty and the tooltip
 * never shows a line. The mapping is the single place a future source
 * lands; nothing else may guess a value from material tiers or mining
 * speed.
 */
class BreakingPowerTest {
	static {
		VanillaBootstrap.ensure();
	}

	@Test
	void pickaxesResolveToEmptyWithoutAnAuthoritativeSource() {
		assertTrue(BreakingPower.of(Items.DIAMOND_PICKAXE).isEmpty(),
				"a diamond pickaxe has no authoritative breaking power — none is invented");
		assertTrue(BreakingPower.of(Items.NETHERITE_PICKAXE).isEmpty(),
				"a netherite pickaxe has no authoritative breaking power — none is inferred from the tier");
		assertTrue(BreakingPower.of(Items.WOODEN_PICKAXE).isEmpty(),
				"a wooden pickaxe has no authoritative breaking power — none is guessed");
	}

	@Test
	void nonPickaxesNeverResolveAValue() {
		assertTrue(BreakingPower.of(Items.DIAMOND_SWORD).isEmpty());
		assertTrue(BreakingPower.of(Items.DIAMOND_AXE).isEmpty());
		assertTrue(BreakingPower.of(Items.BOW).isEmpty());
		assertTrue(BreakingPower.of(Items.STICK).isEmpty());
	}

	@Test
	void theAuthoritativeMappingStartsEmpty() {
		assertTrue(BreakingPower.covered().isEmpty(),
				"no breaking-power source exists yet — the mapping must not carry guessed entries");
	}
}
