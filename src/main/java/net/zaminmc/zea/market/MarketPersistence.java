package net.zaminmc.zea.market;

import net.zaminmc.zea.market.model.PhysicalShop;
import net.zaminmc.zea.market.model.ShopObservation;
import net.zaminmc.zea.market.store.MarketStore;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

/**
 * Schedules the market's disk writes. The client thread marks the data
 * dirty; writes coalesce onto the market executor (one daemon thread, a
 * bounded queue — a rejected write is logged and dropped, never blocks
 * the game), and a flush lands at most every
 * {@link #FLUSH_INTERVAL_MILLIS}, so a burst of inspections costs one
 * write. {@link #flushNow()} forces one through for shutdown.
 *
 * <p>The write itself is a full-file snapshot through the safe-write
 * protocol: serialized in memory, verified to round-trip, atomically
 * moved. The market is small enough that a snapshot per flush is cheaper
 * and simpler than journaling.
 */
public final class MarketPersistence {
	/** Minimum time between disk writes. */
	public static final long FLUSH_INTERVAL_MILLIS = 30_000;

	private final MarketStore store;
	private final ExecutorService executor;
	private volatile boolean dirty;
	private volatile boolean writing;
	private volatile long lastWriteAt;
	// The snapshot awaiting the next write; null when nothing is pending.
	private volatile MarketStore.MarketFile pendingSnapshot;

	public MarketPersistence(MarketStore store, ExecutorService executor) {
		this.store = store;
		this.executor = executor;
	}

	/**
	 * Notes that the in-memory market changed. The caller passes the
	 * snapshot to persist — a plain copy of the shops and observations
	 * taken on the client thread, so the executor never reads the live
	 * index.
	 */
	public void markDirty(MarketStore.MarketFile snapshot) {
		pendingSnapshot = snapshot;
		dirty = true;
	}

	/**
	 * Writes pending changes when due. Called from the client tick; a
	 * write already in flight skips the tick and stays dirty.
	 */
	public void tick(long now) {
		if (!dirty || writing || now - lastWriteAt < FLUSH_INTERVAL_MILLIS) {
			return;
		}
		startWrite();
	}

	/** Writes pending changes now; used for shutdown. */
	public void flushNow() {
		if (dirty && !writing) {
			startWrite();
		}
	}

	/** Blocks until the executor finishes its last write (shutdown drain). */
	public void shutdown() {
		flushNow();
	}

	private void startWrite() {
		MarketStore.MarketFile snapshot = pendingSnapshot;
		if (snapshot == null) {
			return;
		}
		dirty = false;
		writing = true;
		try {
			executor.execute(() -> {
				try {
					store.save(snapshot);
					lastWriteAt = System.currentTimeMillis();
				} catch (Exception e) {
					// A failed write keeps the data dirty: the next flush
					// retries with a fresh snapshot rather than losing it.
					dirty = true;
					net.zaminmc.zea.ZeaClient.LOGGER.warn("Market save failed; will retry: {}", e.toString());
				} finally {
					writing = false;
				}
			});
		} catch (Exception rejected) {
			// Bounded queue full: keep the data dirty, drop this request.
			dirty = true;
			writing = false;
			net.zaminmc.zea.ZeaClient.LOGGER.warn("Market save queue full; will retry");
		}
	}

	/** The wire snapshot of the current market data. */
	public static MarketStore.MarketFile snapshot(MarketData data) {
		List<MarketStore.ShopWire> shops = new ArrayList<>();
		for (PhysicalShop shop : data.shops()) {
			shops.add(new MarketStore.ShopWire(
					shop.shopId(), shop.serverId(), shop.world(),
					shop.signPos().getX(), shop.signPos().getY(), shop.signPos().getZ(),
					shop.chestPos() == null ? null : shop.chestPos().getX(),
					shop.chestPos() == null ? null : shop.chestPos().getY(),
					shop.chestPos() == null ? null : shop.chestPos().getZ(),
					shop.owner(), shop.nation(),
					shop.createdAt().toEpochMilli(), shop.lastObservedAt().toEpochMilli()));
		}
		List<MarketStore.ObservationWire> observations = new ArrayList<>();
		for (ShopObservation observation : data.allObservations()) {
			observations.add(new MarketStore.ObservationWire(
					observation.shopId(),
					observation.direction().name(),
					observation.fingerprint().token(),
					observation.fingerprint().fidelity().name(),
					observation.itemDisplay(),
					observation.price() == null ? null : observation.price().toPlainString(),
					observation.priceScope() == null ? null : observation.priceScope().name(),
					observation.bulkSize(),
					observation.stockRaw(),
					observation.stockUnit().name(),
					observation.remainingPercent(),
					observation.maxBuyBulks(),
					observation.maxBuyItems(),
					observation.maxSellBulks(),
					observation.maxSellItems(),
					observation.enchantments(),
					observation.supportsAll(),
					observation.sourceType().name(),
					observation.observedAt().toEpochMilli()));
		}
		return new MarketStore.MarketFile(MarketStore.CURRENT_VERSION, shops, observations);
	}
}
