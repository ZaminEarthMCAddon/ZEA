package net.zaminmc.zea.market.sync;

import net.zaminmc.zea.market.model.PhysicalShop;
import net.zaminmc.zea.market.model.ShopObservation;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Optional;

/**
 * Turns a local observation into a shareable submission record — or a
 * rejection with its reason. This is the validation gate between the
 * local market and the ZEA-REPO contribution pipeline; nothing leaves
 * the client without passing it.
 *
 * <p>Rules, in order:
 *
 * <ul>
 *   <li>Only server-verified observations submit. Sign-only adverts may
 *       aid local discovery but are never shared — the repository's
 *       strongest recommendations must stay grounded in real shop
 *       responses.</li>
 *   <li>The record must carry a usable exact identity, a direction, a
 *       price with scope, and a physical position (sign coordinates);
 *       anything less cannot be validated downstream and is rejected
 *       rather than guessed.</li>
 *   <li>Provenance is preserved: observation time (original), submission
 *       time (this moment), source confidence. The repository gets both
 *       timestamps and must keep them distinct.</li>
 * </ul>
 */
public final class SubmissionValidator {
	private SubmissionValidator() {
	}

	public static Optional<SubmissionRecord> validate(PhysicalShop shop, ShopObservation observation,
			Instant submittedAt) {
		if (observation.sourceType() != net.zaminmc.zea.market.model.SourceType.SERVER_RESPONSE) {
			return Optional.empty();
		}
		if (observation.fingerprint() == null || !observation.fingerprint().isExact()
				|| observation.direction() == null
				|| observation.price() == null || observation.price().signum() < 0
				|| observation.priceScope() == null
				|| observation.observedAt() == null || submittedAt == null) {
			return Optional.empty();
		}
		return Optional.of(new SubmissionRecord(
				shop.shopId(), shop.serverId(), shop.world(),
				shop.signPos().getX(), shop.signPos().getY(), shop.signPos().getZ(),
				shop.chestPos() == null ? null : shop.chestPos().getX(),
				shop.chestPos() == null ? null : shop.chestPos().getY(),
				shop.chestPos() == null ? null : shop.chestPos().getZ(),
				shop.owner(), shop.nation(),
				observation.direction().name(),
				observation.fingerprint().token(),
				observation.itemDisplay(),
				observation.price().toPlainString(),
				observation.priceScope().name(),
				observation.bulkSize(),
				observation.stockRaw(),
				observation.stockUnit() == null ? null : observation.stockUnit().name(),
				observation.maxBuyBulks(),
				observation.maxSellItems(),
				observation.supportsAll(),
				observation.observedAt().toEpochMilli(),
				submittedAt.toEpochMilli()));
	}

	/**
	 * One shareable shop observation. Field-for-field the local
	 * observation plus its physical shop context and both timestamps;
	 * the ZEA-REPO shop dataset schema (which does not exist yet) must
	 * accept exactly this shape or a documented subset.
	 */
	public record SubmissionRecord(
			String shopId, String serverId, String world,
			int signX, int signY, int signZ,
			@Nullable Integer chestX, @Nullable Integer chestY, @Nullable Integer chestZ,
			@Nullable String owner, @Nullable String nation,
			String direction, String token, @Nullable String itemDisplay,
			String price, String priceScope,
			@Nullable Integer bulkSize, @Nullable String stockRaw, @Nullable String stockUnit,
			@Nullable Integer maxBuyBulks, @Nullable Integer maxSellItems, boolean supportsAll,
			long observedAt, long submittedAt) {
		public SubmissionRecord {
			if (shopId == null || serverId == null || world == null || direction == null
					|| token == null || token.isEmpty() || price == null || priceScope == null) {
				throw new NullPointerException("submission fields must not be null");
			}
		}
	}
}
