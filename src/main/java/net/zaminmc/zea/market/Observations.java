package net.zaminmc.zea.market;

import net.zaminmc.zea.market.interaction.ShopInspectionTracker;
import net.zaminmc.zea.market.model.ItemFingerprint;
import net.zaminmc.zea.market.model.PhysicalShop;
import net.zaminmc.zea.market.model.ShopObservation;
import net.zaminmc.zea.market.model.ShopResponse;
import net.zaminmc.zea.market.model.SourceType;
import net.zaminmc.zea.market.model.StockUnit;
import net.zaminmc.zea.market.parse.ShopSignParser;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Optional;

/**
 * Builds the market records one inspection produces: the physical shop
 * it happened at and the observation of what was advertised.
 *
 * <p>A server-verified inspection records every field the response
 * carried, exactly as carried — nothing is inferred, and the stock unit
 * stays honest ({@code UNKNOWN} for stock the protocol does not let us
 * interpret, {@code SPACE} for capacity). A sign-only inspection records
 * only the sign's own advert: direction, display text, verbatim quantity
 * and price text, and a display-only fingerprint that can never join an
 * exact match.
 */
public final class Observations {
	private Observations() {
	}

	/**
	 * The records one inspection contributes. {@code empty} when neither
	 * a response nor a usable sign advert is present — an inspection
	 * with no evidence records nothing.
	 */
	public static Optional<Recorded> of(ShopInspectionTracker.Inspection inspection) {
		Instant at = inspection.inspectedAt();
		if (inspection.response() != null) {
			return Optional.of(fromResponse(inspection.response(), inspection, at));
		}
		if (inspection.signLines() != null) {
			return ShopSignParser.parse(inspection.signLines())
					.map(advert -> fromSign(advert, inspection, at));
		}
		return Optional.empty();
	}

	private static Recorded fromResponse(ShopResponse response,
			ShopInspectionTracker.Inspection inspection, Instant at) {
		String shopId = PhysicalShop.idOf(inspection.serverId(), inspection.world(), inspection.signPos());
		PhysicalShop shop = new PhysicalShop(shopId, inspection.serverId(), inspection.world(),
				inspection.signPos(), inspection.chestPos(), response.owner(), null, at, at);
		ItemFingerprint fingerprint = response.preview() != null
				? ItemFingerprints.ofTemplate(response.preview())
				: ItemFingerprints.fromDisplay(response.itemDisplay());
		ShopObservation observation = new ShopObservation(shopId, response.direction(), fingerprint,
				response.itemDisplay(), response.price(), response.priceScope(), response.bulkSize(),
				response.stockRaw(), response.stockUnit(), response.maxBuyBulks(), response.maxSellItems(),
				response.supportsAll(), SourceType.SERVER_RESPONSE, response.receivedAt());
		return new Recorded(shop, observation);
	}

	private static Recorded fromSign(ShopSignParser.SignAdvert advert,
			ShopInspectionTracker.Inspection inspection, Instant at) {
		String shopId = PhysicalShop.idOf(inspection.serverId(), inspection.world(), inspection.signPos());
		PhysicalShop shop = new PhysicalShop(shopId, inspection.serverId(), inspection.world(),
				inspection.signPos(), inspection.chestPos(), advert.owner(), null, at, at);
		ShopObservation observation = new ShopObservation(shopId, advert.direction(),
				ItemFingerprints.fromDisplay(advert.itemDisplay()), advert.itemDisplay(),
				null, null, null, advert.quantity(), StockUnit.UNKNOWN,
				null, null, false, SourceType.SIGN_ONLY, at);
		return new Recorded(shop, observation);
	}

	/** The shop and observation one inspection produced. */
	public record Recorded(PhysicalShop shop, ShopObservation observation) {
		public Recorded {
			if (shop == null || observation == null) {
				throw new NullPointerException("recorded fields must not be null");
			}
		}
	}
}
