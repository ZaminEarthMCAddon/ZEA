package net.zaminmc.zea.itempresentation.presentation;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.zaminmc.zea.VanillaBootstrap;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The special-enchantment indicators: the three-state classification
 * against enchantment fixtures constructed exactly as the vanilla data
 * writes them — real supported-item holder sets, real exclusive-set
 * holder sets — so {@code isSupportedItem} and {@code areCompatible},
 * the game's own compatibility mechanisms, are what decide the states.
 * The registry and profile seams pin the symbols and the item-class
 * profiles, including the intentional riptide/multishot symbol sharing.
 */
class SpecialEnchantmentsTest {
	static {
		VanillaBootstrap.ensure();
	}

	private final Fixtures fixtures = new Fixtures();

	/**
	 * A bow with Infinity shows Infinity applied, Mending not
	 * applicable (the exclusive set blocks it — never a gray missing
	 * upgrade), and Unbreaking missing: supported, unconflicted,
	 * absent.
	 */
	@Test
	void bowWithInfinityClassifiesTheThreeStates() {
		Map<Holder<Enchantment>, Integer> present =
				Map.of(fixtures.infinity, 1);
		assertEquals(SpecialEnchantments.State.APPLIED,
				SpecialEnchantments.state(true, present, fixtures.infinity));
		assertEquals(SpecialEnchantments.State.NOT_APPLICABLE,
				SpecialEnchantments.state(true, present, fixtures.mending),
				"mending conflicts with infinity — never shown as missing");
		assertEquals(SpecialEnchantments.State.MISSING,
				SpecialEnchantments.state(true, present, fixtures.unbreaking),
				"unbreaking is supported and unconflicted — a potential upgrade");
	}

	/** The conflict is symmetric: Mending present hides Infinity too. */
	@Test
	void theInfinityMendingConflictIsSymmetric() {
		Map<Holder<Enchantment>, Integer> present =
				Map.of(fixtures.mending, 1);
		assertEquals(SpecialEnchantments.State.APPLIED,
				SpecialEnchantments.state(true, present, fixtures.mending));
		assertEquals(SpecialEnchantments.State.NOT_APPLICABLE,
				SpecialEnchantments.state(true, present, fixtures.infinity),
				"infinity conflicts with mending — never shown as missing");
	}

	/**
	 * Silk Touch on a pickaxe shows applied, with the rest of the
	 * mining profile missing; on a sword — an item the enchantment
	 * does not support — Silk Touch is not applicable, never missing.
	 */
	@Test
	void silkTouchIsAppliedOnAPickaxeAndNotApplicableOnASword() {
		Map<Holder<Enchantment>, Integer> present =
				Map.of(fixtures.silkTouch, 1);
		assertEquals(SpecialEnchantments.State.APPLIED,
				SpecialEnchantments.state(true, present, fixtures.silkTouch));
		assertEquals(SpecialEnchantments.State.MISSING,
				SpecialEnchantments.state(true, present, fixtures.unbreaking));
		assertEquals(SpecialEnchantments.State.NOT_APPLICABLE,
				SpecialEnchantments.state(false, Map.of(), fixtures.silkTouch),
				"an unsupported candidate is hidden, never shown as a missing upgrade");
	}

	/**
	 * The Silk Touch/Fortune conflict reads through the same exclusive
	 * sets: with Silk Touch present, Fortune is not applicable on the
	 * pickaxe — the indicators hide it and the fortune statistic is
	 * separately suppressed by the calculator.
	 */
	@Test
	void fortuneIsNotApplicableWhenSilkTouchIsPresent() {
		Map<Holder<Enchantment>, Integer> present =
				Map.of(fixtures.silkTouch, 1);
		assertEquals(SpecialEnchantments.State.NOT_APPLICABLE,
				SpecialEnchantments.state(true, present, fixtures.fortune),
				"the exclusive mining set blocks fortune");
	}

	/** An absent, supported, unconflicted candidate is missing. */
	@Test
	void unenchantedBowShowsTheProfileAsMissing() {
		Map<Holder<Enchantment>, Integer> empty = Map.of();
		assertEquals(SpecialEnchantments.State.MISSING,
				SpecialEnchantments.state(true, empty, fixtures.infinity));
		assertEquals(SpecialEnchantments.State.MISSING,
				SpecialEnchantments.state(true, empty, fixtures.mending));
	}

	/**
	 * The profiles name the candidate sets — the bow profile carries
	 * the spec's five, the pickaxe profile the mining three, and the
	 * OTHER profile is empty so unsupported items grow no row.
	 */
	@Test
	void profilesMatchTheItemClasses() {
		assertEquals(List.of("infinity", "mending", "unbreaking", "flame", "punch"),
				SpecialEnchantments.profile(net.zaminmc.zea.itempresentation.recognition.ItemCategory.BOW));
		assertEquals(List.of("silk_touch", "mending", "unbreaking"),
				SpecialEnchantments.profile(net.zaminmc.zea.itempresentation.recognition.ItemCategory.PICKAXE));
		assertTrue(SpecialEnchantments.profile(
						net.zaminmc.zea.itempresentation.recognition.ItemCategory.OTHER).isEmpty(),
				"unsupported items carry no indicator candidates");
	}

	/**
	 * Riptide and Multishot intentionally share one symbol while
	 * staying distinct enchantments: same glyph, different registry
	 * paths — and every registered path resolves to a symbol, so the
	 * row can never silently drop one of them.
	 */
	@Test
	void sharedSymbolsKeepDistinctIdentities() {
		assertEquals("🔱", SpecialEnchantments.symbolOf("riptide"));
		assertEquals("🔱", SpecialEnchantments.symbolOf("multishot"));
		assertEquals("∞", SpecialEnchantments.symbolOf("infinity"));
		assertEquals("🛡️", SpecialEnchantments.symbolOf("unbreaking"));
		for (String path : List.of("infinity", "mending", "silk_touch", "unbreaking", "fire_aspect",
				"punch", "knockback", "flame", "channeling", "riptide", "multishot", "piercing",
				"wind_burst", "aqua_affinity", "respiration", "soul_speed", "swift_sneak", "frost_walker")) {
			assertTrue(SpecialEnchantments.symbolOf(path) != null
							&& !SpecialEnchantments.symbolOf(path).isEmpty(),
					"every registry path carries a symbol: " + path);
		}
		assertNull(SpecialEnchantments.symbolOf("efficiency"),
				"efficiency is a derived statistic, never an indicator");
		assertNull(SpecialEnchantments.symbolOf("fortune"),
				"fortune is a derived statistic, never an indicator");
	}

	/**
	 * The row renders one component per indicator list — the symbols
	 * in order, missing before applied gray — and omits entirely when
	 * nothing shows.
	 */
	@Test
	void rowRendersTheSymbolsInOrderAndOmitsTheEmptyOne() {
		List<SpecialEnchantments.Indicator> indicators = List.of(
				new SpecialEnchantments.Indicator("infinity", "∞", "Infinity", 1,
						SpecialEnchantments.State.APPLIED, "Arrows are not consumed"),
				new SpecialEnchantments.Indicator("mending", "♻", "Mending", 0,
						SpecialEnchantments.State.MISSING, "Repairs with gathered XP"));
		List<Component> row = SpecialEnchantments.row(indicators);
		assertEquals(1, row.size(), "one compact line");
		String text = row.get(0).getString();
		assertTrue(text.contains("∞"), "the applied symbol shows");
		assertTrue(text.contains("♻"), "the missing symbol shows");
		assertTrue(text.indexOf("∞") < text.indexOf("♻"), "profile order holds");
		assertTrue(SpecialEnchantments.row(List.of()).isEmpty(),
				"an empty indicator set renders nothing at all");
	}

	/**
	 * Fixture enchantments constructed with real supported-item holder
	 * sets and real exclusive-set holder sets — the same shapes the
	 * vanilla datapack writes.
	 */
	private static final class Fixtures {
		final Holder<Enchantment> infinity;
		final Holder<Enchantment> mending;
		final Holder<Enchantment> unbreaking;
		final Holder<Enchantment> silkTouch;
		final Holder<Enchantment> fortune;

		Fixtures() {
			infinity = enchantment("Infinity", Items.BOW, HolderSet.empty());
			mending = enchantment("Mending", Items.BOW,
					HolderSet.direct(List.of(infinity)));
			unbreaking = enchantment("Unbreaking", Items.BOW, HolderSet.empty());
			silkTouch = enchantment("Silk Touch", Items.DIAMOND_PICKAXE, HolderSet.empty());
			fortune = enchantment("Fortune", Items.DIAMOND_PICKAXE,
					HolderSet.direct(List.of(silkTouch)));
		}

		private Holder<Enchantment> enchantment(String display, net.minecraft.world.item.Item supported,
				HolderSet<Enchantment> exclusiveWith) {
			return Holder.direct(new Enchantment(
					Component.literal(display),
					Enchantment.definition(
							HolderSet.direct(List.of(BuiltInRegistries.ITEM.wrapAsHolder(supported))),
							10, 1, Enchantment.constantCost(1), Enchantment.constantCost(1), 0,
							EquipmentSlotGroup.ANY),
					exclusiveWith,
					DataComponentMap.EMPTY));
		}
	}
}
