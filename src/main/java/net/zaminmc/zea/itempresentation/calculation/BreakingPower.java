package net.zaminmc.zea.itempresentation.calculation;

import net.minecraft.world.item.Item;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Breaking Power — the pickaxe-only rating the tooltip carries at the
 * very top.
 *
 * <p>The value is only ever the authoritative one: a value from ZEA's
 * own project data, from the server's item metadata, or from an
 * established mapping this class owns. No such source exists today —
 * EarthMC's items are vanilla, and vanilla pickaxes carry no breaking
 * power — so the mapping is deliberately empty and every pickaxe
 * resolves to empty. The tooltip then shows no line at all: a missing
 * authoritative value is never fabricated, never guessed from Mining
 * Speed, and never inferred from a material tier.
 *
 * <p>When a source appears, its mapping lands in one place —
 * {@link #AUTHORITATIVE}, keyed by item registry path — and every
 * supported pickaxe starts reading it, exactly one line, above the
 * statistics. Swords, axes, shovels, bows and every other class never
 * read the mapping at all.
 */
public final class BreakingPower {
	/**
	 * The authoritative mapping, item registry path to breaking power.
	 * Empty by design until a real source exists; a wrong value here is
	 * a wrong tooltip, so the bar for an entry is that it is documented
	 * in this repository.
	 */
	private static final Map<String, Integer> AUTHORITATIVE = Map.of();

	private BreakingPower() {
	}

	/**
	 * The authoritative breaking power for {@code item}, or empty when
	 * no source carries one.
	 */
	public static Optional<Integer> of(Item item) {
		if (item == null) {
			return Optional.empty();
		}
		var key = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item);
		if (key == null) {
			return Optional.empty();
		}
		return Optional.ofNullable(AUTHORITATIVE.get(key.toString()));
	}

	/** The item registry paths the mapping currently covers, for diagnostics. */
	public static List<String> covered() {
		return List.copyOf(AUTHORITATIVE.keySet());
	}
}
