package net.zaminmc.zea.market.sync;

import com.google.gson.Gson;
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
import java.util.Comparator;
import java.util.List;

/**
 * The ZEA-REPO shared shop dataset: the wire format the mod reads, the
 * per-entry validation that guards it, and the local cache that keeps
 * the last accepted snapshot available offline.
 *
 * <p>The repository's {@code manifest.json} indexes versioned dataset
 * files by SHA-256; the shop dataset itself is a list of shop records,
 * each one physical shop's latest server-verified observation — field
 * for field the local observation plus its physical shop context. Item
 * definitions are never duplicated per record: the exact fingerprint
 * token is the stable reference, and the tokens group into the same
 * item variants the local index builds.
 *
 * <p>Records are validated individually and in isolation: one malformed
 * or timestamp-less record is rejected with a warning and the rest of
 * the dataset loads — a corrupt entry can never quarantine the file or
 * crash the client. Records without a trustworthy observation time are
 * rejected outright: the repository's publication time is not an
 * observation time, and a timestamp-less record can never participate
 * in the freshness conflict rules.
 */
public final class SharedShops {
	/** The dataset schema version this build reads. */
	public static final int CURRENT_VERSION = 1;

	/** The most records one dataset may carry; the newest per shop wins. */
	public static final int MAX_RECORDS = 25_000;

	private static final JsonStore json = new JsonStore();

	/** One shared shop record, exactly as the dataset stores it. */
	public record SharedRecord(
			@Nullable String shopId, @Nullable String serverId, @Nullable String world,
			@Nullable Integer sx, @Nullable Integer sy, @Nullable Integer sz,
			@Nullable Integer cx, @Nullable Integer cy, @Nullable Integer cz,
			@Nullable String owner, @Nullable String nation,
			@Nullable String direction, @Nullable String token,
			@Nullable String itemDisplay, @Nullable String price, @Nullable String priceScope,
			@Nullable Integer bulkSize, @Nullable String stockRaw, @Nullable String stockUnit,
			@Nullable Integer remainingPercent,
			@Nullable Integer maxBuyBulks, @Nullable Integer maxBuyItems,
			@Nullable Integer maxSellBulks, @Nullable Integer maxSellItems,
			@Nullable List<String> enchantments, @Nullable Boolean supportsAll,
			@Nullable Long observedAt) {
	}

	/** The dataset file shape: versioned records. */
	public record SharedFile(int version, List<SharedRecord> records) {
		public SharedFile {
			records = records == null ? List.of() : records;
		}
	}

	private SharedShops() {
	}

	/**
	 * Parses a dataset document. Every record validates alone; invalid
	 * ones are logged and skipped. When the document carries more than
	 * {@link #MAX_RECORDS} valid records, the newest per shop wins — the
	 * active state of a shop is the one thing the dataset must carry.
	 */
	public static List<SharedRecord> parse(String datasetJson, String source) {
		List<SharedRecord> valid = new ArrayList<>();
		if (datasetJson == null || datasetJson.isBlank()) {
			return valid;
		}
		SharedFile file;
		try {
			file = new Gson().fromJson(datasetJson, SharedFile.class);
		} catch (Exception e) {
			warn(source, "dataset could not be parsed", e);
			return valid;
		}
		if (file == null || file.version() > CURRENT_VERSION) {
			// A dataset from a newer build is never guessed at.
			warn(source, "dataset version is newer than this build; skipped", null);
			return valid;
		}
		for (SharedRecord record : file.records()) {
			if (isValid(record)) {
				valid.add(record);
			} else {
				warn(source, "rejected malformed shop record "
						+ (record == null ? "(null)" : record.shopId()), null);
			}
		}
		if (valid.size() > MAX_RECORDS) {
			valid.sort(Comparator.comparing(SharedShops::observedAtOf).reversed());
			// Keep one record per shop, newest first, up to the bound.
			List<SharedRecord> bounded = new ArrayList<>();
			java.util.Set<String> seen = new java.util.HashSet<>();
			for (SharedRecord record : valid) {
				if (seen.add(record.shopId())) {
					bounded.add(record);
					if (bounded.size() >= MAX_RECORDS) {
						break;
					}
				}
			}
			return bounded;
		}
		return valid;
	}

	private static Long observedAtOf(SharedRecord record) {
		return record.observedAt() == null ? 0L : record.observedAt();
	}

	private static void warn(String source, String message, @Nullable Exception cause) {
		String text = "Shared shop dataset ({}): {}";
		if (cause == null) {
			net.zaminmc.zea.ZeaClient.LOGGER.warn(text, source, message);
		} else {
			net.zaminmc.zea.ZeaClient.LOGGER.warn(text + " — {}", source, message, cause.toString());
		}
	}

	/** The minimum identity a mergeable record must carry. */
	static boolean isValid(@Nullable SharedRecord record) {
		if (record == null) {
			return false;
		}
		if (blank(record.shopId()) || blank(record.serverId()) || blank(record.world())
				|| record.sx() == null || record.sy() == null || record.sz() == null
				|| record.observedAt() == null || record.observedAt() <= 0) {
			return false;
		}
		if (directionOf(record.direction()) == null
				|| blank(record.token()) || record.price() == null) {
			return false;
		}
		try {
			if (new BigDecimal(record.price()).signum() < 0) {
				return false;
			}
		} catch (NumberFormatException e) {
			return false;
		}
		return priceScopeOf(record.priceScope()) != null;
	}

	private static boolean blank(@Nullable String text) {
		return text == null || text.isBlank();
	}

	/** The record's direction, or null when unnameable. */
	static ShopDirection directionOf(@Nullable String name) {
		return name == null ? null : enumOrNull(ShopDirection.class, name);
	}

	/** The record's price scope, or null when unnameable. */
	static PriceScope priceScopeOf(@Nullable String name) {
		return name == null ? null : enumOrNull(PriceScope.class, name);
	}

	/** The record's stock unit, or the safe unknown when unnameable. */
	static StockUnit stockUnitOf(@Nullable String name) {
		return name == null ? StockUnit.UNKNOWN
				: enumOrNull(StockUnit.class, name) == null ? StockUnit.UNKNOWN
				: StockUnit.valueOf(name);
	}

	private static <E extends Enum<E>> @Nullable E enumOrNull(Class<E> type, String name) {
		try {
			return Enum.valueOf(type, name);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	/** Rebuilds the physical shop the record describes. */
	public static PhysicalShop toShop(SharedRecord record) {
		return new PhysicalShop(record.shopId(), record.serverId(), record.world(),
				new net.minecraft.core.BlockPos(record.sx(), record.sy(), record.sz()),
				record.cx() == null || record.cy() == null || record.cz() == null
						? null : new net.minecraft.core.BlockPos(record.cx(), record.cy(), record.cz()),
				record.owner(), record.nation(),
				Instant.ofEpochMilli(record.observedAt()),
				Instant.ofEpochMilli(record.observedAt()));
	}

	/**
	 * Rebuilds the observation the record carries, marked as
	 * repository-shared provenance.
	 */
	public static ShopObservation toObservation(SharedRecord record) {
		return new ShopObservation(record.shopId(),
				directionOf(record.direction()),
				new ItemFingerprint(record.token(), ItemFingerprint.Fidelity.EXACT),
				record.itemDisplay(),
				new BigDecimal(record.price()),
				priceScopeOf(record.priceScope()),
				record.bulkSize(), record.stockRaw(),
				stockUnitOf(record.stockUnit()),
				record.remainingPercent(),
				record.maxBuyBulks(), record.maxBuyItems(),
				record.maxSellBulks(), record.maxSellItems(),
				Boolean.TRUE.equals(record.supportsAll()),
				record.enchantments() == null ? List.of() : record.enchantments(),
				SourceType.REMOTE_SHARED,
				Instant.ofEpochMilli(record.observedAt()));
	}

	/** Saves the accepted snapshot as the offline cache. */
	public static void saveCache(Path file, List<SharedRecord> records) {
		try {
			json.save(file, new SharedFile(CURRENT_VERSION, records));
		} catch (Exception e) {
			net.zaminmc.zea.ZeaClient.LOGGER.warn("Shared dataset cache could not be written: {}", e.toString());
		}
	}

	/** Loads the offline cache; empty when missing, corrupt or from a newer build. */
	public static List<SharedRecord> loadCache(Path file) {
		if (!java.nio.file.Files.exists(file)) {
			return List.of();
		}
		try {
			SharedFile stored = json.load(file, SharedFile.class, () -> new SharedFile(CURRENT_VERSION, List.of()));
			if (stored.version() > CURRENT_VERSION) {
				return List.of();
			}
			List<SharedRecord> valid = new ArrayList<>();
			for (SharedRecord record : stored.records()) {
				if (isValid(record)) {
					valid.add(record);
				}
			}
			return valid;
		} catch (Exception e) {
			net.zaminmc.zea.ZeaClient.LOGGER.warn("Shared dataset cache could not be read: {}", e.toString());
			return List.of();
		}
	}
}
