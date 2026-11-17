package net.zaminmc.zea.market.model;

/**
 * Where an observation came from, which decides how much the market may
 * trust it.
 *
 * <ul>
 *   <li>{@code SERVER_RESPONSE} — the full shop response captured after a
 *       real sign inspection by this player's own client: server-verified
 *       item, price and quantities. The source the strongest market
 *       recommendations rank, and the only source community submission
 *       accepts.</li>
 *   <li>{@code REMOTE_SHARED} — the same kind of server-verified shop
 *       response, captured by another player's client and shared through
 *       the ZEA-REPO dataset. It ranks with the verified responses in the
 *       browser (the response itself was the server's), while the
 *       interface presents its provenance: it is someone else's
 *       observation, and the timestamp conflict rules keep it from ever
 *       overwriting a newer local observation.</li>
 *   <li>{@code SIGN_ONLY} — the player looked at a sign but no (complete)
 *       server response was captured. Sign text is preliminary
 *       information — the displayed item name can be truncated and the
 *       advertised price can be stale — so sign-only observations may aid
 *       discovery but are never treated as equally reliable.</li>
 * </ul>
 */
public enum SourceType {
	SERVER_RESPONSE,
	REMOTE_SHARED,
	SIGN_ONLY
}
