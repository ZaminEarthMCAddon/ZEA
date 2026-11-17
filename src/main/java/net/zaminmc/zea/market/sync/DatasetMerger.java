package net.zaminmc.zea.market.sync;

import net.zaminmc.zea.market.MarketData;
import net.zaminmc.zea.market.model.ItemFingerprint;
import net.zaminmc.zea.market.model.PhysicalShop;
import net.zaminmc.zea.market.model.ShopObservation;
import net.zaminmc.zea.market.model.SourceType;

import java.time.Instant;
import java.util.List;

/**
 * Merges the shared repository dataset into the local market — the
 * rule set that makes remote data safe against local observations, in
 * one sentence: <b>older remote data never overwrites newer local
 * observations, and local observations win timestamp ties</b>.
 *
 * <p>The rules, each pinned by tests:
 *
 * <ul>
 *   <li><b>A shop the local set does not know</b> joins as a shared
 *       shop, its observation marked {@link SourceType#REMOTE_SHARED}.</li>
 *   <li><b>Remote strictly newer</b> than the shop's newest local
 *       observation: the remote observation is appended — the local
 *       record stays in the history, and the appended one becomes the
 *       active advert because it is genuinely the newest measurement.</li>
 *   <li><b>Timestamp tie</b>: the local observation wins; the remote one
 *       is not appended.</li>
 *   <li><b>Remote older or equal to any local observation</b>: skipped
 *       as stale — never appended, so list order (the advert scan) can
 *       never resurrect an older measurement over a newer one.</li>
 *   <li><b>Re-merges are idempotent</b>: a record whose observation
 *       already exists for the shop (same token, same timestamp) is
 *       skipped — a dataset refresh appends nothing twice.</li>
 *   <li><b>Malformed records</b> were rejected by the parser before the
 *       merge sees them; the merge itself never fabricates coordinates,
 *       prices or identities.</li>
 * </ul>
 *
 * <p>The comparison uses actual observation timestamps — never the
 * repository's download or commit time. The merge runs on the client
 * thread, like every MarketData mutation.
 */
public final class DatasetMerger {
	private DatasetMerger() {
	}

	/** What one merge pass did. */
	public record Result(int shopsAdded, int observationsAppended,
			int staleSkipped, int tiesSkipped, int duplicatesSkipped) {
		public static final Result NOTHING = new Result(0, 0, 0, 0, 0);
	}

	/** Merges one dataset snapshot into the market, in place. */
	public static Result merge(MarketData data, List<SharedShops.SharedRecord> records) {
		int shopsAdded = 0;
		int appended = 0;
		int stale = 0;
		int ties = 0;
		int duplicates = 0;
		for (SharedShops.SharedRecord record : records) {
			ShopObservation remote = SharedShops.toObservation(record);
			PhysicalShop shop = SharedShops.toShop(record);
			Instant remoteAt = remote.observedAt();
			List<ShopObservation> history = data.observations(record.shopId());
			if (alreadyRecorded(history, remote)) {
				duplicates++;
				continue;
			}
			ShopObservation newestLocal = newest(history);
			if (shop(record.shopId(), data) == null) {
				data.putShop(shop);
				data.putObservation(remote);
				shopsAdded++;
				appended++;
				continue;
			}
			if (newestLocal == null) {
				data.putObservation(remote);
				appended++;
				continue;
			}
			int compared = remoteAt.compareTo(newestLocal.observedAt());
			if (compared > 0) {
				data.putObservation(remote);
				appended++;
			} else if (compared == 0) {
				// The local observation wins the tie: same measurement
				// time, local provenance outranks shared.
				ties++;
			} else {
				stale++;
			}
		}
		return new Result(shopsAdded, appended, stale, ties, duplicates);
	}

	private static PhysicalShop shop(String shopId, MarketData data) {
		return data.shop(shopId);
	}

	/** The shop's newest observation of any source, or null. */
	private static ShopObservation newest(List<ShopObservation> history) {
		ShopObservation newest = null;
		for (ShopObservation observation : history) {
			if (newest == null || observation.observedAt().isAfter(newest.observedAt())) {
				newest = observation;
			}
		}
		return newest;
	}

	/**
	 * Whether the shop's history already carries this measurement: same
	 * fingerprint token and same observation time. The dataset refresh
	 * may repeat records; the history must not grow because of that.
	 */
	private static boolean alreadyRecorded(List<ShopObservation> history, ShopObservation remote) {
		for (ShopObservation observation : history) {
			if (observation.observedAt().equals(remote.observedAt())
					&& sameToken(observation.fingerprint(), remote.fingerprint())) {
				return true;
			}
		}
		return false;
	}

	private static boolean sameToken(ItemFingerprint a, ItemFingerprint b) {
		return a.isExact() && b.isExact() && a.token().equals(b.token());
	}
}
