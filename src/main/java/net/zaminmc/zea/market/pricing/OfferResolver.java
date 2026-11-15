package net.zaminmc.zea.market.pricing;

import net.zaminmc.zea.market.MarketData;
import net.zaminmc.zea.market.model.PhysicalShop;
import net.zaminmc.zea.market.model.ShopDirection;
import net.zaminmc.zea.market.model.ShopObservation;
import net.zaminmc.zea.market.model.SourceType;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Resolves the best observed offers for one exact item identity.
 *
 * <p>The two directions are resolved separately and never mixed:
 *
 * <ul>
 *   <li><b>Best observed place to buy</b> — the cheapest shop SELLING
 *       the item to the player (lowest normalized unit price).</li>
 *   <li><b>Best observed place to sell</b> — the shop BUYING the item
 *       from the player at the highest normalized unit price.</li>
 * </ul>
 *
 * <p>Rules the resolver enforces:
 *
 * <ul>
 *   <li>Only exact fingerprints compare; display-only identities never
 *       rank against anything.</li>
 *   <li>One shop contributes one offer per side — its <b>latest</b>
 *       observation. An old price is never resurrected by an older
 *       better measurement, but the history behind the winner stays
 *       available for display.</li>
 *   <li>Server-verified observations outrank sign-only ones; a sign-only
 *       offer can never displace a server-verified one at the same
 *       price.</li>
 *   <li>Ordering is fully deterministic: price, then observation
 *       recency, then shop id — equal prices never shuffle between
 *       calls.</li>
 * </ul>
 *
 * <p>No freshness threshold lives here: the market has no universal
 * expiry, every offer carries its observation timestamp, and the UI
 * presents the age honestly.
 */
public final class OfferResolver {
	private static final Comparator<MarketOffer> BUY_ORDER = Comparator
			.comparing((MarketOffer offer) -> offer.unitPrice() == null
					? BigDecimal.valueOf(Long.MAX_VALUE) : offer.unitPrice())
			.thenComparing(offer -> offer.observation().observedAt(), Comparator.reverseOrder())
			.thenComparing(offer -> offer.shop().shopId());

	private static final Comparator<MarketOffer> SELL_ORDER = Comparator
			.comparing((MarketOffer offer) -> offer.unitPrice() == null
					? BigDecimal.valueOf(Long.MAX_VALUE) : offer.unitPrice(), Comparator.reverseOrder())
			.thenComparing(offer -> offer.observation().observedAt(), Comparator.reverseOrder())
			.thenComparing(offer -> offer.shop().shopId());

	/**
	 * Every offer for the exact identity, one per shop, best buy offers
	 * first followed by best sell offers. An inexact token matches
	 * nothing — that is the exact-matching default.
	 */
	public List<MarketOffer> offers(MarketData data, String token) {
		if (token == null || token.isEmpty()) {
			return List.of();
		}
		List<MarketOffer> buys = new ArrayList<>();
		List<MarketOffer> sells = new ArrayList<>();
		for (PhysicalShop shop : data.shops()) {
			ShopObservation latest = latestObservation(data, shop.shopId(), token);
			if (latest == null) {
				continue;
			}
			MarketOffer offer = new MarketOffer(shop, latest,
					PriceMath.normalize(latest.price(), latest.priceScope(), latest.bulkSize()).orElse(null));
			if (latest.direction() == ShopDirection.SELLING) {
				buys.add(offer);
			} else {
				sells.add(offer);
			}
		}
		buys.sort(BUY_ORDER);
		sells.sort(SELL_ORDER);
		List<MarketOffer> all = new ArrayList<>(buys.size() + sells.size());
		all.addAll(buys);
		all.addAll(sells);
		return all;
	}

	/** The best observed place to buy the item, when any comparable offer exists. */
	public Optional<MarketOffer> bestBuyOffer(MarketData data, String token) {
		return offers(data, token).stream()
				.filter(offer -> offer.observation().direction() == ShopDirection.SELLING)
				.filter(offer -> offer.unitPrice() != null)
				.findFirst();
	}

	/** The best observed place to sell the item, when any comparable offer exists. */
	public Optional<MarketOffer> bestSellOffer(MarketData data, String token) {
		return offers(data, token).stream()
				.filter(offer -> offer.observation().direction() == ShopDirection.BUYING)
				.filter(offer -> offer.unitPrice() != null)
				.findFirst();
	}

	/**
	 * The shop's latest observation of the exact identity. Server-verified
	 * observations are preferred when the latest entry is a stale sign-only
	 * advert left by an expired inspection: an advert never outranks a real
	 * response the same shop produced.
	 */
	private static @Nullable ShopObservation latestObservation(MarketData data, String shopId, String token) {
		List<ShopObservation> history = data.observations(shopId);
		ShopObservation latest = null;
		for (ShopObservation observation : history) {
			if (observation.fingerprint().isExact() && token.equals(observation.fingerprint().token())) {
				latest = observation;
			}
		}
		if (latest != null) {
			return latest;
		}
		// No exact match: a sign-only advert for the item can still be
		// listed for discovery, ranked under nothing.
		for (ShopObservation observation : history) {
			if (observation.sourceType() == SourceType.SIGN_ONLY && token.equals(observation.fingerprint().token())) {
				latest = observation;
			}
		}
		return latest;
	}
}
