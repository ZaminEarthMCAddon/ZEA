package net.zaminmc.zea.market.model;

import org.jspecify.annotations.Nullable;

/**
 * The exact identity of a marketable item, as a deterministic token.
 *
 * <p>Two stacks with the same registry id can be completely different
 * products — an Efficiency V / Fortune III pickaxe is not an
 * Efficiency V / Silk Touch pickaxe, and a damaged tool is not an
 * undamaged one. The token therefore covers the item id and every
 * component that changes identity or value (enchantments, damage, custom
 * name, custom data), while the stack count is normalized away: an
 * identical product in a larger pile is the same product.
 *
 * <p>{@link Fidelity} records how much evidence stands behind the token:
 *
 * <ul>
 *   <li>{@code EXACT} — derived from the server's item preview, with the
 *       full component data encoded; exact marketplace matching operates
 *       on these tokens only.</li>
 *   <li>{@code DISPLAY_ONLY} — derived from display text (a sign line)
 *       alone; the token is a best-effort material slug and can never
 *       participate in an exact match, because truncation or a custom
 *       name would silently compare different products.</li>
 * </ul>
 *
 * <p>When even the display text is unusable, a display-only fingerprint
 * with an empty token is produced and the item is treated as
 * unmatchable rather than guessed.
 */
public record ItemFingerprint(String token, Fidelity fidelity) {
	public ItemFingerprint {
		if (token == null || fidelity == null) {
			throw new NullPointerException("fingerprint fields must not be null");
		}
		if (fidelity == Fidelity.EXACT && token.isEmpty()) {
			throw new IllegalArgumentException("an exact fingerprint requires a token");
		}
	}

	/**
	 * A display-only fingerprint with no usable token: the item identity
	 * is incomplete and the market must not claim any match for it.
	 */
	public static ItemFingerprint unknown() {
		return new ItemFingerprint("", Fidelity.DISPLAY_ONLY);
	}

	/** Whether this fingerprint can be exact-matched against others. */
	public boolean isExact() {
		return fidelity == Fidelity.EXACT && !token.isEmpty();
	}

	/**
	 * How much evidence stands behind the identity token.
	 */
	public enum Fidelity {
		EXACT,
		DISPLAY_ONLY
	}
}
