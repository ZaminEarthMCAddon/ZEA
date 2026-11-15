package net.zaminmc.zea.market.pricing;

import net.zaminmc.zea.market.model.PhysicalShop;
import net.zaminmc.zea.market.model.ShopObservation;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;

/**
 * One comparable offer: the physical shop, the observation it is judged
 * by, and the normalized per-item price when the observation carries
 * enough information to have one. Derived view only — never persisted.
 *
 * <p>{@code unitPrice} is {@code null} exactly when the observation is
 * incomparable (no price, no scope, per-bulk without a bulk size); an
 * incomparable offer is listed but never ranked.
 */
public record MarketOffer(PhysicalShop shop, ShopObservation observation,
		@Nullable BigDecimal unitPrice) {
	public MarketOffer {
		if (shop == null || observation == null) {
			throw new NullPointerException("offer fields must not be null");
		}
	}
}
