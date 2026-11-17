package net.zaminmc.zea.market;

import net.zaminmc.zea.market.model.PhysicalShop;
import net.zaminmc.zea.market.model.ShopDirection;
import net.zaminmc.zea.market.model.ShopObservation;
import net.zaminmc.zea.market.model.SourceType;
import net.zaminmc.zea.market.pricing.PriceMath;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * The flat listing model the market browser presents: one row per
 * physical shop, carrying that shop's latest verified advert.
 *
 * <p>An EarthMC shop trades one item in one direction, so one shop is
 * one listing — a shop whose sign changed item or direction is still
 * the same physical shop, now advertising something else. The row's
 * observation is the shop's latest server-verified response, falling
 * back to its latest sign-only advert when no response was ever
 * captured; the source type travels with the row so the UI can present
 * an advert as an advert, never as verified stock.
 *
 * <p>{@link #query} composes the whole presentation pipeline — text
 * search, direction filter, sort — over the live index. It reads only
 * the in-memory model: no parsing, no disk, no network, no world
 * access, so screens may call it on user actions and render from the
 * result.
 */
public final class MarketListings {
	private MarketListings() {
	}

	/** Which sides of the market a view covers. */
	public enum Filter {
		/** Every observed listing, either direction. */
		ALL,
		/** Shops the player can buy from (EarthMC SELLING shops). */
		BUY_FROM,
		/** Shops the player can sell to (EarthMC BUYING shops). */
		SELL_TO
	}

	/** One browser row: a physical shop and its current advert. */
	public record Listing(PhysicalShop shop, ShopObservation observation,
			@Nullable BigDecimal unitPrice, @Nullable String town, @Nullable String nation) {
		public Listing {
			if (shop == null || observation == null) {
				throw new NullPointerException("listing fields must not be null");
			}
		}

		/**
		 * Whether this row's advert was verified by a real shop response —
		 * captured by this client, or shared by another player's through
		 * the repository dataset. Only sign-text adverts rank below
		 * verified ones.
		 */
		public boolean isServerVerified() {
			return observation.sourceType() != SourceType.SIGN_ONLY;
		}

		/** The player-facing direction: buy from, or sell to. */
		public ShopDirection direction() {
			return observation.direction();
		}
	}

	/** Town and nation as resolved from claim data, when available. */
	public record ResolvedTown(String town, @Nullable String nation) {
		public ResolvedTown {
			if (town == null || town.isBlank()) {
				throw new IllegalArgumentException("town must not be blank");
			}
		}
	}

	/**
	 * The town/nation lookup the query consults. Deliberately a seam:
	 * the claim store lives in the map subsystem and may hold no data
	 * yet — a null lookup simply leaves town and nation unavailable,
	 * and nothing is fabricated for them.
	 */
	public interface TownLookup {
		/** The resolved town and nation, or {@code null} when nothing is known there. */
		ResolvedTown at(String world, int x, int z);
	}

	/**
	 * Builds the listing rows for one view: every shop's advert, text-
	 * and direction-filtered, sorted. The sort carries the direction
	 * rule itself (see {@link MarketSort}) so a price sort presents the
	 * best offer first from the player's side of the counter.
	 */
	public static List<Listing> query(MarketData data, String searchText, Filter filter,
			MarketSort sort, @Nullable TownLookup towns) {
		String needle = searchText == null ? "" : searchText.trim().toLowerCase(Locale.ROOT);
		List<Listing> listings = new ArrayList<>();
		for (PhysicalShop shop : data.shops()) {
			ShopObservation advert = latestAdvert(data, shop.shopId());
			if (advert == null) {
				continue;
			}
			if (filter != Filter.ALL && advert.direction() != directionFor(filter)) {
				continue;
			}
			ResolvedTown resolved = towns == null ? null : towns.at(shop.world(),
					shop.signPos().getX(), shop.signPos().getZ());
			Listing listing = new Listing(shop, advert,
					PriceMath.normalize(advert.price(), advert.priceScope(), advert.bulkSize()).orElse(null),
					resolved == null ? null : resolved.town(),
					resolved == null ? null : resolved.nation());
			if (!needle.isEmpty() && !matches(listing, needle)) {
				continue;
			}
			listings.add(listing);
		}
		listings.sort(sort.comparator(filter));
		return listings;
	}

	private static ShopDirection directionFor(Filter filter) {
		return filter == Filter.BUY_FROM ? ShopDirection.SELLING : ShopDirection.BUYING;
	}

	/**
	 * The shop's current advert: its latest server-verified observation —
	 * own or shared — or, when the shop was only ever seen from its sign,
	 * its latest sign-only advert. A stale advert never hides behind a
	 * newer advert.
	 */
	private static @Nullable ShopObservation latestAdvert(MarketData data, String shopId) {
		List<ShopObservation> history = data.observations(shopId);
		ShopObservation verified = null;
		ShopObservation signOnly = null;
		for (ShopObservation observation : history) {
			if (observation.sourceType() == SourceType.SIGN_ONLY) {
				signOnly = observation;
			} else {
				verified = observation;
			}
		}
		return verified != null ? verified : signOnly;
	}

	private static boolean matches(Listing listing, String needle) {
		return contains(listing.observation().itemDisplay(), needle)
				|| contains(listing.observation().fingerprint().token(), needle)
				|| contains(listing.shop().owner(), needle)
				|| contains(listing.shop().nation(), needle)
				|| contains(listing.town(), needle);
	}

	private static boolean contains(String text, String needle) {
		return text != null && text.toLowerCase(Locale.ROOT).contains(needle);
	}
}
