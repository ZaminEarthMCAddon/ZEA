package net.zaminmc.zea.market.model;

/**
 * Where an observation came from, which decides how much the market may
 * trust it.
 *
 * <ul>
 *   <li>{@code SERVER_RESPONSE} — the full shop response captured after a
 *       real sign inspection: server-verified item, price and quantities.
 *       The only source the strongest market recommendations may rank.</li>
 *   <li>{@code SIGN_ONLY} — the player looked at a sign but no (complete)
 *       server response was captured. Sign text is preliminary
 *       information — the displayed item name can be truncated and the
 *       advertised price can be stale — so sign-only observations may aid
 *       discovery but are never treated as equally reliable.</li>
 * </ul>
 */
public enum SourceType {
	SERVER_RESPONSE,
	SIGN_ONLY
}
