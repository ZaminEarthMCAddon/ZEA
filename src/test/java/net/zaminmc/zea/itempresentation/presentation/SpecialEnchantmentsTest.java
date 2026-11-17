package net.zaminmc.zea.itempresentation.presentation;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.zaminmc.zea.VanillaBootstrap;
import net.zaminmc.zea.rarity.ItemRarity;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The special-enchantment indicators: the three-state classification
 * against enchantment fixtures constructed exactly as the vanilla data
 * writes them — real supported-item holder sets, real exclusive-set
 * holder sets — so {@code isSupportedItem} and {@code areCompatible},
 * the game's own compatibility mechanisms, are what decide the states.
 * The registry and profile seams pin the symbols and the item-class
 * profiles, including the intentional riptide/multishot symbol sharing;
 * the tier rule, the bracketed rendering, the rarity-tier colors and
 * the font routing pin the bug report's presentation contract.
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
		assertEquals("🛡", SpecialEnchantments.symbolOf("unbreaking"));
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
	 * Every symbol is exactly one codepoint: no variation selectors,
	 * no combining marks — a bare U+FE0F has no glyph of its own and
	 * renders as a broken box.
	 */
	@Test
	void everySymbolIsExactlyOneCodepoint() {
		for (String path : List.of("infinity", "mending", "silk_touch", "unbreaking", "fire_aspect",
				"punch", "knockback", "flame", "channeling", "riptide", "multishot", "piercing",
				"wind_burst", "aqua_affinity", "respiration", "soul_speed", "swift_sneak", "frost_walker")) {
			String symbol = SpecialEnchantments.symbolOf(path);
			assertEquals(1, symbol.codePointCount(0, symbol.length()),
					path + " carries a variation selector or combining mark: " + symbol);
		}
	}

	/**
	 * The row renders one component per indicator list — the bracketed
	 * symbols in order, single-spaced — and omits entirely when
	 * nothing shows.
	 */
	@Test
	void rowRendersTheSymbolsInOrderAndOmitsTheEmptyOne() {
		List<SpecialEnchantments.Indicator> indicators = List.of(
				new SpecialEnchantments.Indicator("infinity", "∞", "Infinity", 1, 1,
						SpecialEnchantments.State.APPLIED, "Arrows are not consumed"),
				new SpecialEnchantments.Indicator("mending", "♻", "Mending", 0, 1,
						SpecialEnchantments.State.MISSING, "Repairs with gathered XP"));
		List<Component> row = SpecialEnchantments.row(indicators);
		assertEquals(1, row.size(), "one compact line");
		assertEquals("[∞] [♻]", row.get(0).getString(),
				"bracketed symbols, single space, profile order");
		assertTrue(SpecialEnchantments.row(List.of()).isEmpty(),
				"an empty indicator set renders nothing at all");
	}

	/**
	 * The spec's example, verbatim: Unbreaking caps at III, so I is
	 * the lowest tier, II the tier immediately below Legendary, and
	 * III Legendary gold.
	 */
	@Test
	void unbreakingTheSpecsExampleMapsCommonEpicLegendary() {
		assertEquals(ItemRarity.COMMON, SpecialEnchantments.tierFor(1, 3));
		assertEquals(ItemRarity.EPIC, SpecialEnchantments.tierFor(2, 3));
		assertEquals(ItemRarity.LEGENDARY, SpecialEnchantments.tierFor(3, 3));
	}

	/** A single-level enchantment's only level is its maximum: gold. */
	@Test
	void singleLevelEnchantmentsAreLegendaryGold() {
		assertEquals(ItemRarity.LEGENDARY, SpecialEnchantments.tierFor(1, 1));
	}

	/**
	 * A two-level enchantment spans the full range: the lowest level
	 * Common, the maximum Legendary.
	 */
	@Test
	void twoLevelEnchantmentsSpanCommonToLegendary() {
		assertEquals(ItemRarity.COMMON, SpecialEnchantments.tierFor(1, 2));
		assertEquals(ItemRarity.LEGENDARY, SpecialEnchantments.tierFor(2, 2));
	}

	/** Four levels land one tier each, no skips. */
	@Test
	void fourLevelsFillEveryTierExactlyOnce() {
		assertEquals(ItemRarity.COMMON, SpecialEnchantments.tierFor(1, 4));
		assertEquals(ItemRarity.UNCOMMON, SpecialEnchantments.tierFor(2, 4));
		assertEquals(ItemRarity.EPIC, SpecialEnchantments.tierFor(3, 4));
		assertEquals(ItemRarity.LEGENDARY, SpecialEnchantments.tierFor(4, 4));
	}

	/**
	 * Five levels distribute proportionally: only the true maximum is
	 * Legendary, and the mapping never runs backwards.
	 */
	@Test
	void fiveLevelsDistributeProportionallyAndMonotonically() {
		assertEquals(List.of(ItemRarity.COMMON, ItemRarity.UNCOMMON, ItemRarity.EPIC,
						ItemRarity.EPIC, ItemRarity.LEGENDARY),
				List.of(
						SpecialEnchantments.tierFor(1, 5),
						SpecialEnchantments.tierFor(2, 5),
						SpecialEnchantments.tierFor(3, 5),
						SpecialEnchantments.tierFor(4, 5),
						SpecialEnchantments.tierFor(5, 5)));
	}

	/** Out-of-range levels clamp: below the ladder Common, beyond it Legendary. */
	@Test
	void levelsClampIntoTheLadder() {
		assertEquals(ItemRarity.COMMON, SpecialEnchantments.tierFor(0, 3));
		assertEquals(ItemRarity.COMMON, SpecialEnchantments.tierFor(-4, 3));
		assertEquals(ItemRarity.LEGENDARY, SpecialEnchantments.tierFor(99, 3));
		assertEquals(ItemRarity.LEGENDARY, SpecialEnchantments.tierFor(7, 1));
	}

	/** The tiers are the rarity system's own — no palette exists besides it. */
	@Test
	void theTierColorsAreTheRaritySystemColors() {
		assertEquals(ItemRarity.LEGENDARY.rgb(), SpecialEnchantments.tierFor(3, 3).rgb());
		assertEquals(0xFFAA00, SpecialEnchantments.tierFor(3, 3).rgb());
		assertEquals(ItemRarity.EPIC.rgb(), SpecialEnchantments.tierFor(2, 3).rgb());
	}

	/**
	 * An applied Unbreaking III is gold on every part — brackets,
	 * symbol, brackets — and the gold is the rarity system's own.
	 */
	@Test
	void appliedMaximumLevelIsLegendaryGoldOnAllParts() {
		Component row = SpecialEnchantments.row(List.of(
				new SpecialEnchantments.Indicator("unbreaking", "🛡", "Unbreaking III", 3, 3,
						SpecialEnchantments.State.APPLIED, "Durability loss often avoided"))).get(0);
		Component part = row.getSiblings().get(0);
		assertEquals("[", part.getSiblings().get(0).getString());
		assertEquals("🛡", part.getSiblings().get(1).getString());
		assertEquals("]", part.getSiblings().get(2).getString());
		for (Component piece : part.getSiblings()) {
			assertEquals(TextColor.fromRgb(ItemRarity.LEGENDARY.rgb()),
					piece.getStyle().getColor());
		}
	}

	/** Lower applied levels take progressively lower tiers — Unbreaking II is Epic purple. */
	@Test
	void appliedIntermediateLevelTakesItsTierColor() {
		Component row = SpecialEnchantments.row(List.of(
				new SpecialEnchantments.Indicator("unbreaking", "🛡", "Unbreaking II", 2, 3,
						SpecialEnchantments.State.APPLIED, "Durability loss often avoided"))).get(0);
		for (Component piece : row.getSiblings().get(0).getSiblings()) {
			assertEquals(TextColor.fromRgb(ItemRarity.EPIC.rgb()),
					piece.getStyle().getColor());
		}
	}

	/** Missing-but-applicable is the legacy neutral gray. */
	@Test
	void missingIndicatorsAreNeutralGray() {
		Component row = SpecialEnchantments.row(List.of(
				new SpecialEnchantments.Indicator("mending", "♻", "Mending", 0, 1,
						SpecialEnchantments.State.MISSING, "Repairs with gathered XP"))).get(0);
		for (Component piece : row.getSiblings().get(0).getSiblings()) {
			assertEquals(TextColor.fromLegacyFormat(ChatFormatting.GRAY),
					piece.getStyle().getColor());
		}
	}

	/** No state labels anywhere: brackets and symbols only. */
	@Test
	void theRowNeverCarriesStateLabels() {
		String text = SpecialEnchantments.row(List.of(
				new SpecialEnchantments.Indicator("infinity", "∞", "Infinity", 1, 1,
						SpecialEnchantments.State.APPLIED, "Arrows are not consumed"),
				new SpecialEnchantments.Indicator("silk_touch", "🔮", "Silk Touch", 0, 1,
						SpecialEnchantments.State.MISSING, "Blocks drop themselves"))).get(0)
				.getString();
		assertTrue(text.indexOf("APPLIED") < 0);
		assertTrue(text.indexOf("MISSING") < 0);
		assertTrue(text.indexOf("NOT APPLICABLE") < 0);
	}

	/**
	 * Symbols vanilla's font defines render in the default font; the
	 * others route to ZEA's bitmap font — a codepoint is never sent to
	 * a font that lacks it, so no missing-glyph box can appear.
	 */
	@Test
	void symbolsRouteToTheFontThatHasTheirGlyph() {
		assertEquals(SpecialEnchantments.FontSource.VANILLA,
				SpecialEnchantments.fontOf("unbreaking"));
		assertEquals(SpecialEnchantments.FontSource.VANILLA,
				SpecialEnchantments.fontOf("infinity"));
		assertEquals(SpecialEnchantments.FontSource.ZEA,
				SpecialEnchantments.fontOf("mending"));
		assertEquals(SpecialEnchantments.FontSource.ZEA,
				SpecialEnchantments.fontOf("silk_touch"));

		Component vanillaRow = SpecialEnchantments.row(List.of(
				new SpecialEnchantments.Indicator("unbreaking", "🛡", "Unbreaking III", 3, 3,
						SpecialEnchantments.State.APPLIED, "Durability loss often avoided"))).get(0);
		assertEquals(FontDescription.DEFAULT,
				vanillaRow.getSiblings().get(0).getSiblings().get(1).getStyle().getFont());

		Component zeaRow = SpecialEnchantments.row(List.of(
				new SpecialEnchantments.Indicator("mending", "♻", "Mending", 1, 1,
						SpecialEnchantments.State.APPLIED, "Repairs with gathered XP"))).get(0);
		assertEquals(SpecialEnchantments.FONT,
				zeaRow.getSiblings().get(0).getSiblings().get(1).getStyle().getFont());
	}

	/** The brackets always stay in the vanilla font, whatever the symbol needs. */
	@Test
	void bracketsAlwaysStayInTheVanillaFont() {
		Component row = SpecialEnchantments.row(List.of(
				new SpecialEnchantments.Indicator("mending", "♻", "Mending", 1, 1,
						SpecialEnchantments.State.APPLIED, "Repairs with gathered XP"))).get(0);
		assertEquals(FontDescription.DEFAULT,
				row.getSiblings().get(0).getSiblings().get(0).getStyle().getFont());
		assertEquals(FontDescription.DEFAULT,
				row.getSiblings().get(0).getSiblings().get(2).getStyle().getFont());
	}

	/**
	 * The shipped bitmap font defines exactly the codepoints the
	 * registry routes to it — every one present (no missing-glyph
	 * boxes), none redundant with the vanilla-covered set — and its
	 * texture ships beside it.
	 */
	@Test
	void theShippedBitmapFontDefinesExactlyTheRegistrySymbolsItMust() throws Exception {
		Set<Integer> shipped = new HashSet<>();
		try (InputStream in = SpecialEnchantments.class
				.getResourceAsStream("/assets/zea/font/enchant_indicators.json")) {
			assertNotNull(in, "assets/zea/font/enchant_indicators.json is on the classpath");
			JsonObject root = JsonParser.parseReader(
					new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
			for (var provider : root.getAsJsonArray("providers")) {
				for (var row : provider.getAsJsonObject().getAsJsonArray("chars")) {
					row.getAsString().codePoints().forEach(shipped::add);
				}
			}
		}
		Set<Integer> zeaFont = new HashSet<>();
		for (String path : List.of("mending", "silk_touch", "flame", "piercing", "wind_burst",
				"respiration", "soul_speed", "swift_sneak")) {
			String symbol = SpecialEnchantments.symbolOf(path);
			assertEquals(SpecialEnchantments.FontSource.ZEA, SpecialEnchantments.fontOf(path),
					path + " is the one the bitmap font must cover");
			zeaFont.add(symbol.codePointAt(0));
		}
		assertEquals(zeaFont, shipped, "the bitmap font and the registry agree");
		try (InputStream texture = SpecialEnchantments.class
				.getResourceAsStream("/assets/zea/textures/font/enchant_symbols.png")) {
			assertNotNull(texture, "the bitmap font's texture is shipped");
		}
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
