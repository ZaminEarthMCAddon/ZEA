package net.zaminmc.zea.market.search;

import net.zaminmc.zea.market.MarketData;
import net.zaminmc.zea.market.model.PhysicalShop;
import net.zaminmc.zea.market.model.ShopObservation;
import net.zaminmc.zea.market.pricing.MarketOffer;
import net.zaminmc.zea.market.pricing.OfferResolver;
import net.zaminmc.zea.market.model.ShopDirection;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Search over the local persistent market. The index is fully local, so
 * search works offline by construction.
 *
 * <p>Matching is plain text over the honest evidence: item display
 * names, fingerprint tokens (which carry the registry id), shop owners
 * and nations when known. A query matches an observation when any of
 * those contain it case-insensitively; nothing is invented for missing
 * fields, and an empty market simply returns no results.
 *
 * <p>Results group by identity token — the same product seen at several
 * shops is one row whose offers cover every shop that advertised it.
 * Each group carries its offers best-first (buy offers ahead of sell
 * offers, cheapest/highest first), so a UI can show the best observed
 * offer prominently and keep the alternatives one step away.
 */
public final class MarketSearch {
	/** Which sides of the market the results cover. */
	public enum Filter {
		/** SELLING shops the player can buy from, and BUYING shops to sell to. */
		ALL,
		/** Only shops the player can buy from (EarthMC SELLING shops). */
		BUY_FROM,
		/** Only shops the player can sell to (EarthMC BUYING shops). */
		SELL_TO
	}

	private final OfferResolver resolver;

	public MarketSearch(OfferResolver resolver) {
		this.resolver = resolver;
	}

	public MarketSearch() {
		this(new OfferResolver());
	}

	/**
	 * Searches the market. {@code query} is matched as plain text; an
	 * empty query returns every known identity. The filter selects which
	 * sides survive: {@code BUY_FROM} keeps groups that have at least one
	 * offer from a SELLING shop, {@code SELL_TO} at least one from a
	 * BUYING shop.
	 */
	public List<Grouping> search(MarketData data, String query, Filter filter) {
		String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
		Map<String, List<MarketOffer>> byToken = new LinkedHashMap<>();
		for (PhysicalShop shop : data.shops()) {
			for (ShopObservation observation : data.observations(shop.shopId())) {
				if (needle.isEmpty() || matches(observation, shop, needle)) {
					byToken.computeIfAbsent(observation.fingerprint().token(),
							key -> new ArrayList<>());
				}
			}
		}
		List<Grouping> groups = new ArrayList<>();
		for (Map.Entry<String, List<MarketOffer>> entry : byToken.entrySet()) {
			List<MarketOffer> offers = resolver.offers(data, entry.getKey());
			if (!filterAllows(offers, filter)) {
				continue;
			}
			String display = offers.isEmpty() ? "" : offers.get(0).observation().itemDisplay() != null
					? offers.get(0).observation().itemDisplay()
					: entry.getKey();
			groups.add(new Grouping(entry.getKey(), display, offers));
		}
		return groups;
	}

	private boolean filterAllows(List<MarketOffer> offers, Filter filter) {
		return switch (filter) {
			case ALL -> !offers.isEmpty();
			case BUY_FROM -> offers.stream().anyMatch(offer ->
					offer.observation().direction() == ShopDirection.SELLING);
			case SELL_TO -> offers.stream().anyMatch(offer ->
					offer.observation().direction() == ShopDirection.BUYING);
		};
	}

	private static boolean matches(ShopObservation observation, PhysicalShop shop, String needle) {
		return contains(observation.itemDisplay(), needle)
				|| contains(observation.fingerprint().token(), needle)
				|| contains(shop.owner(), needle)
				|| contains(shop.nation(), needle);
	}

	private static boolean contains(String text, String needle) {
		return text != null && text.toLowerCase(Locale.ROOT).contains(needle);
	}

	/**
	 * One identity as the search sees it: the token that keys it, a
	 * display name for lists, and every shop's best offer for it, sorted
	 * best-first. Display names come from observations; a group without
	 * one falls back to its token rather than an invented label.
	 */
	public record Grouping(String token, String display, List<MarketOffer> offers) {
		public Grouping {
			if (token == null || display == null || offers == null) {
				throw new NullPointerException("grouping fields must not be null");
			}
			offers = List.copyOf(offers);
		}
	}
}
