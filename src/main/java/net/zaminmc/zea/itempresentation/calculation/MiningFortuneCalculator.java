package net.zaminmc.zea.itempresentation.calculation;

import java.util.Optional;

/**
 * Mining Fortune: the expected-yield percentage a Fortune level grants
 * over normal drops, from the vanilla formula — the multiplier for
 * level {@code L} is {@code 1 / (L + 2) + (L + 1) / 2}, so levels one
 * to three expect 1.33x, 1.75x and 2.20x, displayed as +33 %, +75 %
 * and +120 %.
 *
 * <p>The percentage is an expected yield, never a guaranteed item
 * count, and it describes the blocks the standard calculation covers.
 * It is computed for a mining tool that carries Fortune; Silk Touch
 * suppresses it — the two are mutually exclusive in normal vanilla
 * enchanting ({@code #minecraft:exclusive_set/mining}), and the
 * blocked case belongs to the special-enchantment indicators (Fortune
 * shown as not applicable), never to a number here. Fortune is never
 * derived from Efficiency and its raw level is never displayed as the
 * stat.
 */
public final class MiningFortuneCalculator {

	private MiningFortuneCalculator() {
	}

	/**
	 * The Fortune percentage for the snapshot's enchantments, or empty
	 * when the item carries no Fortune, or when Silk Touch blocks it.
	 */
	public static Optional<ItemStatistics.MiningFortune> calculate(ItemSnapshot snapshot) {
		int fortuneLevel = EnchantmentLevel.byPath(snapshot, "fortune");
		if (fortuneLevel <= 0) {
			return Optional.empty();
		}
		int silkTouchLevel = EnchantmentLevel.byPath(snapshot, "silk_touch");
		if (silkTouchLevel > 0) {
			return Optional.empty();
		}
		return Optional.of(new ItemStatistics.MiningFortune(percentBonus(fortuneLevel), fortuneLevel));
	}

	/**
	 * The expected-yield percentage for {@code level}: the vanilla
	 * multiplier {@code 1 / (L + 2) + (L + 1) / 2} minus one, as a
	 * percentage — 33, 75 and 120 for the three standard levels.
	 */
	public static double percentBonus(int level) {
		if (level <= 0) {
			return 0.0;
		}
		double multiplier = 1.0 / (level + 2) + (level + 1) / 2.0;
		return (multiplier - 1.0) * 100.0;
	}
}
