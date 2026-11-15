package net.zaminmc.zea.market.model;

/**
 * Which side of a transaction an EarthMC shop is on — the single most
 * important property of a shop, and the one most easily confused.
 *
 * <p>The enum is named from the <b>shop's</b> point of view, exactly as
 * the server phrases it ({@code This shop is SELLING items.}). The player
 * does the opposite of the shop, so every label that addresses the player
 * goes through {@link #playerVerb()}:
 *
 * <ul>
 *   <li>{@code SELLING} — the shop sells items to the player; the player
 *       is buying. The player enters <b>bulks</b> and pays per bulk.</li>
 *   <li>{@code BUYING} — the shop purchases items from the player; the
 *       player is selling. The player enters <b>individual items</b> and
 *       is paid per item.</li>
 * </ul>
 *
 * <p>Never reverse or conflate these meanings anywhere in the market:
 * the best place to <i>buy</i> from is the cheapest {@code SELLING} shop,
 * the best place to <i>sell</i> to is the highest-paying {@code BUYING}
 * shop.
 */
public enum ShopDirection {
	SELLING("buy"),
	BUYING("sell");

	private final String playerVerb;

	ShopDirection(String playerVerb) {
		this.playerVerb = playerVerb;
	}

	/** What the player does at this kind of shop: "buy" or "sell". */
	public String playerVerb() {
		return playerVerb;
	}
}
