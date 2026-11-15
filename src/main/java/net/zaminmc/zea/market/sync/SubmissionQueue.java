package net.zaminmc.zea.market.sync;

import net.zaminmc.zea.ZeaClient;
import net.zaminmc.zea.persistence.JsonStore;
import net.zaminmc.zea.market.model.PhysicalShop;
import net.zaminmc.zea.market.model.ShopObservation;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

/**
 * The local submission queue: validated observations waiting to reach
 * the ZEA-REPO shop dataset.
 *
 * <p>The queue is deliberately the whole integration surface. The
 * submission side of ZEA-REPO has no shop dataset yet — no schema, no
 * manifest entry, no validation workflow — and inventing a remote
 * endpoint for it is forbidden by this feature's own contract. So the
 * client records validated, deduplicated, timestamp-preserving batches
 * locally (offline-safe, replay-safe), and a future repository client
 * drains the file exactly as stored. The missing contract is documented
 * in {@code docs/MARKET.md} under "ZEA-REPO integration".
 *
 * <p>Queue rules: an observation identical to one already queued (same
 * shop, same identity, same measurement time) is never queued twice;
 * observations keep their original timestamps with the submission time
 * recorded separately; the file survives a disconnect, a crash and a
 * restart, because sharing must never depend on the game staying open.
 */
public final class SubmissionQueue {
	/** The file shape on disk. */
	public static final int CURRENT_VERSION = 1;

	private final Path file;
	private final JsonStore json = new JsonStore();
	private final ExecutorService executor;
	private final List<SubmissionValidator.SubmissionRecord> queued = new ArrayList<>();
	private boolean dirty;
	private boolean writing;

	public SubmissionQueue(Path file, ExecutorService executor) {
		this.file = file;
		this.executor = executor;
		restore();
	}

	/** The queued file shape; unknown versions are not applied. */
	public record QueueFile(int version, List<SubmissionValidator.SubmissionRecord> submissions) {
		public QueueFile {
			submissions = submissions == null ? List.of() : submissions;
		}
	}

	private void restore() {
		QueueFile stored = json.load(file, QueueFile.class, () -> new QueueFile(CURRENT_VERSION, List.of()));
		if (stored.version() == CURRENT_VERSION) {
			for (SubmissionValidator.SubmissionRecord record : stored.submissions()) {
				if (record != null) {
					queued.add(record);
				}
			}
		}
	}

	/**
	 * Validates and queues one observation. Returns {@code true} when
	 * the queue changed — the caller writes the market snapshot anyway;
	 * this queue flushes on its own schedule.
	 */
	public boolean enqueue(PhysicalShop shop, ShopObservation observation) {
		SubmissionValidator.SubmissionRecord record =
				SubmissionValidator.validate(shop, observation, Instant.now()).orElse(null);
		if (record == null || alreadyQueued(record)) {
			return false;
		}
		queued.add(record);
		dirty = true;
		return true;
	}

	private boolean alreadyQueued(SubmissionValidator.SubmissionRecord record) {
		for (SubmissionValidator.SubmissionRecord existing : queued) {
			if (existing.shopId().equals(record.shopId())
					&& existing.token().equals(record.token())
					&& existing.observedAt() == record.observedAt()
					&& existing.price().equals(record.price())) {
				return true;
			}
		}
		return false;
	}

	/** The records waiting, in submission order. */
	public List<SubmissionValidator.SubmissionRecord> queued() {
		return List.copyOf(queued);
	}

	/** How many records wait; the future repository client drains by this. */
	public int size() {
		return queued.size();
	}

	/**
	 * Removes the records a repository upload consumed. Called only by
	 * the future adapter, after the repository has truly accepted them —
	 * the queue never forgets an observation because a send failed.
	 */
	public void removeAll(List<SubmissionValidator.SubmissionRecord> drained) {
		queued.removeAll(drained);
		dirty = true;
	}

	/** Writes pending changes when due; same cadence as the market store. */
	public void tick(long now, long lastMarketWrite) {
		if (dirty && !writing) {
			flush();
		}
	}

	private void flush() {
		QueueFile snapshot = new QueueFile(CURRENT_VERSION, List.copyOf(queued));
		dirty = false;
		writing = true;
		try {
			executor.execute(() -> {
				try {
					json.save(file, snapshot);
				} catch (Exception e) {
					dirty = true;
					ZeaClient.LOGGER.warn("Submission queue save failed; will retry: {}", e.toString());
				} finally {
					writing = false;
				}
			});
		} catch (Exception rejected) {
			dirty = true;
			writing = false;
			ZeaClient.LOGGER.warn("Submission queue save queue full; will retry");
		}
	}

	/** Writes pending changes now; used for shutdown. */
	public void flushNow() {
		if (dirty && !writing) {
			flush();
		}
	}
}
