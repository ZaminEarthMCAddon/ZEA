package net.zaminmc.zea.market.tx;

import net.zaminmc.zea.market.model.PriceScope;
import net.zaminmc.zea.market.pricing.PriceMath;
import net.zaminmc.zea.market.model.ShopDirection;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * The exact arithmetic behind the shop screen's quantity controls —
 * the two transaction shapes, which are never to be confused:
 *
 * <ul>
 *   <li>At a SELLING shop the player buys and the selector counts
 *       <b>bulks</b>: {@code items = bulks × bulkSize},
 *       {@code cost = bulks × pricePerBulk}. The maximum comes only
 *       from the server's reported limit — never from inventory size or
 *       an assumed stack of 64.</li>
 *   <li>At a BUYING shop the player sells and the selector counts
 *       <b>individual items</b>: {@code payout = items × pricePerItem},
 *       capped by the server's reported sellable quantity — which can
 *       legitimately be 0, meaning ordinary submissions are refused.</li>
 * </ul>
 *
 * <p>Every result carries the exact input that would be sent to EarthMC,
 * so the review screen shows the server's own protocol, not ZEA's
 * paraphrase of it.
 */
public final class TransactionCalculator {
	private TransactionCalculator() {
	}

	/**
	 * The plan for entering {@code bulkCount} bulks at a SELLING shop.
	 * {@code empty} when the shop carries no bulk size (the calculation
	 * is impossible without the server's number) or the quantity is
	 * outside 1..max — the screen clamps, the calculator refuses.
	 */
	public static Optional<BulkPlan> buy(ShopDirection direction, @Nullable BigDecimal pricePerBulk,
			@Nullable Integer bulkSize, @Nullable Integer maxBulks, int bulkCount) {
		if (direction != ShopDirection.SELLING || pricePerBulk == null || bulkSize == null
				|| bulkSize <= 0 || bulkCount < 1) {
			return Optional.empty();
		}
		if (maxBulks != null && bulkCount > maxBulks) {
			return Optional.empty();
		}
		int items = bulkCount * bulkSize;
		return Optional.of(new BulkPlan(bulkCount, bulkSize, items,
				PriceMath.total(pricePerBulk, bulkCount), String.valueOf(bulkCount)));
	}

	/**
	 * The plan for selling {@code itemCount} individual items to a BUYING
	 * shop. {@code empty} when the shop has no per-item price, the
	 * quantity is outside 1..limit, or the limit is 0 — a shop that can
	 * buy nothing refuses every ordinary submission.
	 */
	public static Optional<ItemPlan> sell(ShopDirection direction, @Nullable BigDecimal pricePerItem,
			@Nullable Integer maxItems, int itemCount) {
		if (direction != ShopDirection.BUYING || pricePerItem == null || itemCount < 1) {
			return Optional.empty();
		}
		if (maxItems != null && itemCount > maxItems) {
			return Optional.empty();
		}
		return Optional.of(new ItemPlan(itemCount, PriceMath.total(pricePerItem, itemCount),
				String.valueOf(itemCount)));
	}

	/**
	 * The plan for the server's {@code all} input. Only ever produced
	 * when the server's response explicitly confirmed it accepts the
	 * word; the server decides the quantities, so the plan carries no
	 * totals — the review says exactly that instead of inventing them.
	 */
	public static Optional<AllPlan> all(boolean supportsAll, ShopDirection direction) {
		if (!supportsAll) {
			return Optional.empty();
		}
		return Optional.of(new AllPlan(direction));
	}

	/** A whole-bulk purchase at a SELLING shop. */
	public record BulkPlan(int bulkCount, int bulkSize, int itemCount, BigDecimal totalCost,
			String inputToSend) {
		public BulkPlan {
			if (bulkCount < 1 || bulkSize < 1 || totalCost == null || inputToSend == null) {
				throw new NullPointerException("bulk plan fields must not be null");
			}
		}
	}

	/** An individual-item sale to a BUYING shop. */
	public record ItemPlan(int itemCount, BigDecimal totalPayout, String inputToSend) {
		public ItemPlan {
			if (itemCount < 1 || totalPayout == null || inputToSend == null) {
				throw new NullPointerException("item plan fields must not be null");
			}
		}
	}

	/** The server's {@code all} input: quantities are the server's to decide. */
	public record AllPlan(ShopDirection direction) {
		public AllPlan {
			if (direction == null) {
				throw new NullPointerException("direction must not be null");
			}
		}

		/** The exact text sent to EarthMC. */
		public String inputToSend() {
			return "all";
		}
	}
}
