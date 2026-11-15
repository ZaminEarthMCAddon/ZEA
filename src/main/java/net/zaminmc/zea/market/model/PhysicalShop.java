package net.zaminmc.zea.market.model;

import net.minecraft.core.BlockPos;
import org.jspecify.annotations.Nullable;

import java.time.Instant;

/**
 * A physical EarthMC shop: a chest-attached sign at a concrete place on a
 * concrete server. A shop is a location, not an offer — what it trades,
 * at which price, and in which direction belongs to the
 * {@link ShopObservation}s taken at it.
 *
 * <p>Identity is {@code (server, world, sign position)}: a physical shop
 * keeps its identity when the owner, item, price or direction on the sign
 * change, so an item change never registers as a different shop.
 * The attached chest position, when it could be resolved, is part of the
 * location record (and disambiguates shops would that share a sign
 * position on different chests).
 *
 * <p>Nation is recorded only when a reliable source (authorized claim
 * data) resolves it; an unresolved nation stays {@code null} and is never
 * guessed from surrounding text or approximate positions.
 */
public record PhysicalShop(
		String shopId,
		String serverId,
		String world,
		BlockPos signPos,
		@Nullable BlockPos chestPos,
		@Nullable String owner,
		@Nullable String nation,
		Instant createdAt,
		Instant lastObservedAt) {
	public PhysicalShop {
		if (shopId == null || serverId == null || world == null || signPos == null
				|| createdAt == null || lastObservedAt == null) {
			throw new NullPointerException("physical shop fields must not be null");
		}
	}

	/**
	 * The stable identity of the shop anchored at a sign: server identity
	 * plus world plus sign block position, with the chest appended when
	 * known. The same format every layer uses — a shop found again after
	 * a restart must resolve to the same id.
	 */
	public static String idOf(String serverId, String world, BlockPos signPos, @Nullable BlockPos chestPos) {
		StringBuilder id = new StringBuilder(serverId)
				.append('|').append(world)
				.append('|').append(signPos.getX()).append(',').append(signPos.getY()).append(',').append(signPos.getZ());
		if (chestPos != null) {
			id.append('|').append(chestPos.getX()).append(',').append(chestPos.getY()).append(',').append(chestPos.getZ());
		}
		return id.toString();
	}

	/** A copy of this shop with the owner, nation and observation stamp updated. */
	public PhysicalShop revisited(@Nullable String owner, @Nullable String nation, Instant at) {
		return new PhysicalShop(shopId, serverId, world, signPos, chestPos,
				owner != null ? owner : this.owner,
				nation != null ? nation : this.nation,
				createdAt, at);
	}
}
