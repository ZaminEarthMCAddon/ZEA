package net.zaminmc.zea.market.store;

import net.minecraft.core.BlockPos;
import net.zaminmc.zea.market.model.ItemFingerprint;
import net.zaminmc.zea.market.model.PhysicalShop;
import net.zaminmc.zea.market.model.PriceScope;
import net.zaminmc.zea.market.model.ShopDirection;
import net.zaminmc.zea.market.model.ShopObservation;
import net.zaminmc.zea.market.model.SourceType;
import net.zaminmc.zea.market.model.StockUnit;
import net.zaminmc.zea.persistence.JsonStore;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Loads and saves one server's market data through the safe-write
 * protocol. Corrupt files are preserved as {@code *.corrupted} and
 * replaced by an empty market; a hand-edited file may contain damaged
 * entries, and one bad entry never quarantines the file — entries
 * missing a shop id, a direction or a timestamp are dropped with a
 * warning, unknown enum names degrade like the chat store's categories.
 *
 * <p>Prices are stored as their exact decimal text ({@code "1.5"}), not
 * as floats: the market's comparisons are exact decimal arithmetic, and
 * a float round-trip through JSON would silently move every price.
 * Timestamps are epoch milliseconds because Gson has no
 * {@link java.time.Instant} adapter.
 *
 * <p>Schema: {@code {"version": 1, "shops": [...], "observations": [...]}}.
 * Shops and observations are separate lists joined by {@code shopId}: a
 * shop is a physical place, an observation a measurement taken at it,
 * and the two never overwrite each other.
 */
public final class MarketStore {
	/** Bump when the file shape changes; add the matching migration above. */
	public static final int CURRENT_VERSION = 1;

	private final Path file;
	private final JsonStore json = new JsonStore();

	public MarketStore(Path file) {
		this.file = file;
	}

	/** The file shape on disk. */
	public record MarketFile(int version, List<ShopWire> shops, List<ObservationWire> observations) {
		public MarketFile {
			shops = shops == null ? List.of() : shops;
			observations = observations == null ? List.of() : observations;
		}
	}

	/** One physical shop as it appears in the file; every field is optional. */
	public record ShopWire(@Nullable String id, @Nullable String server, @Nullable String world,
			@Nullable Integer sx, @Nullable Integer sy, @Nullable Integer sz,
			@Nullable Integer cx, @Nullable Integer cy, @Nullable Integer cz,
			@Nullable String owner, @Nullable String nation,
			@Nullable Long createdAt, @Nullable Long lastObservedAt) {
	}

	/** One observation as it appears in the file; every field is optional. */
	public record ObservationWire(@Nullable String shopId, @Nullable String direction,
			@Nullable String token, @Nullable String fidelity,
			@Nullable String itemDisplay, @Nullable String price, @Nullable String priceScope,
			@Nullable Integer bulkSize, @Nullable String stockRaw, @Nullable String stockUnit,
			@Nullable Integer maxBuyBulks, @Nullable Integer maxSellItems,
			@Nullable Boolean supportsAll, @Nullable String sourceType, @Nullable Long observedAt) {
	}

	/** Loads the file, falling back to an empty market when missing or unreadable. */
	public MarketFile load() {
		MarketFile loaded = json.load(file, MarketFile.class, MarketStore::emptyFile);
		if (loaded.version() > CURRENT_VERSION) {
			// A file from a newer build: never guess at fields this build
			// does not know; keep it untouched on disk and start fresh.
			return emptyFile();
		}
		return loaded;
	}

	/** Saves the market atomically after a round-trip check. */
	public void save(MarketFile market) throws java.io.IOException {
		json.save(file, new MarketFile(CURRENT_VERSION, market.shops(), market.observations()));
	}

	private static MarketFile emptyFile() {
		return new MarketFile(CURRENT_VERSION, List.of(), List.of());
	}

	/** Drops entries a damaged file may carry instead of failing wholesale. */
	public static List<ShopWire> usableShops(MarketFile file) {
		List<ShopWire> usable = new ArrayList<>();
		for (ShopWire shop : file.shops()) {
			if (shop != null && shop.id() != null && shop.sx() != null && shop.sy() != null
					&& shop.sz() != null && shop.createdAt() != null && shop.lastObservedAt() != null) {
				usable.add(shop);
			}
		}
		return usable;
	}

	/** Same tolerance for observations: no id, no direction, no time — no entry. */
	public static List<ObservationWire> usableObservations(MarketFile file) {
		List<ObservationWire> usable = new ArrayList<>();
		for (ObservationWire observation : file.observations()) {
			if (observation != null && observation.shopId() != null && observation.direction() != null
					&& observation.observedAt() != null) {
				usable.add(observation);
			}
		}
		return usable;
	}

	/** Rebuilds a shop record from its wire form; empty when it cannot be one. */
	public static Optional<PhysicalShop> toShop(ShopWire wire) {
		if (wire.id() == null || wire.sx() == null || wire.sy() == null || wire.sz() == null
				|| wire.createdAt() == null || wire.lastObservedAt() == null) {
			return Optional.empty();
		}
		return Optional.of(new net.zaminmc.zea.market.model.PhysicalShop(
				wire.id(),
				wire.server() == null ? "unknown" : wire.server(),
				wire.world() == null ? "unknown" : wire.world(),
				new BlockPos(wire.sx(), wire.sy(), wire.sz()),
				wire.cx() == null || wire.cy() == null || wire.cz() == null
						? null : new BlockPos(wire.cx(), wire.cy(), wire.cz()),
				wire.owner(), wire.nation(),
				Instant.ofEpochMilli(wire.createdAt()),
				Instant.ofEpochMilli(wire.lastObservedAt())));
	}

	/**
	 * Rebuilds an observation from its wire form; empty when a required
	 * field is missing or an enum name is unknown. Enum degradation is
	 * deliberate: unknown stock units read as unknown, an unknown
	 * fingerprint fidelity as display-only — the safe end of each scale.
	 */
	public static Optional<ShopObservation> toObservation(ObservationWire wire) {
		if (wire.shopId() == null || wire.direction() == null || wire.observedAt() == null) {
			return Optional.empty();
		}
		ShopDirection direction =
				enumOrNull(ShopDirection.class, wire.direction());
		if (direction == null) {
			return Optional.empty();
		}
		BigDecimal price = null;
		if (wire.price() != null) {
			try {
				price = new BigDecimal(wire.price());
			} catch (NumberFormatException ignored) {
				// A damaged price degrades to none, never to zero.
			}
		}
		ItemFingerprint.Fidelity fidelity = wire.fidelity() == null
				? ItemFingerprint.Fidelity.DISPLAY_ONLY
				: enumOrDefault(ItemFingerprint.Fidelity.class, wire.fidelity(),
						ItemFingerprint.Fidelity.DISPLAY_ONLY);
		String token = wire.token() == null ? "" : wire.token();
		SourceType sourceType = wire.sourceType() == null
				? SourceType.SIGN_ONLY
				: enumOrDefault(SourceType.class, wire.sourceType(),
						SourceType.SIGN_ONLY);
		return Optional.of(new ShopObservation(
				wire.shopId(), direction,
				new ItemFingerprint(token, fidelity),
				wire.itemDisplay(), price,
				enumOrNull(PriceScope.class, wire.priceScope()),
				wire.bulkSize(), wire.stockRaw(),
				enumOrDefault(StockUnit.class, wire.stockUnit(),
						StockUnit.UNKNOWN),
				wire.maxBuyBulks(), wire.maxSellItems(),
				Boolean.TRUE.equals(wire.supportsAll()), sourceType,
				Instant.ofEpochMilli(wire.observedAt())));
	}

	private static <E extends Enum<E>> @Nullable E enumOrNull(Class<E> type, @Nullable String name) {
		return enumOrDefault(type, name, null);
	}

	private static <E extends Enum<E>> @Nullable E enumOrDefault(Class<E> type, @Nullable String name, @Nullable E fallback) {
		if (name == null) {
			return fallback;
		}
		try {
			return Enum.valueOf(type, name);
		} catch (IllegalArgumentException ignored) {
			return fallback;
		}
	}
}
