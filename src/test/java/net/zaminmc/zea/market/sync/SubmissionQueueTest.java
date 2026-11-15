package net.zaminmc.zea.market.sync;

import net.minecraft.core.BlockPos;
import net.zaminmc.zea.VanillaBootstrap;
import net.zaminmc.zea.market.model.ItemFingerprint;
import net.zaminmc.zea.market.model.PhysicalShop;
import net.zaminmc.zea.market.model.PriceScope;
import net.zaminmc.zea.market.model.ShopDirection;
import net.zaminmc.zea.market.model.ShopObservation;
import net.zaminmc.zea.market.model.SourceType;
import net.zaminmc.zea.market.model.StockUnit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SubmissionQueueTest {
	private static final Instant T = Instant.ofEpochMilli(1_000_000L);
	private static final Instant SUBMITTED = Instant.ofEpochMilli(2_000_000L);
	private static final String TOKEN = "minecraft:diamond_pickaxe|{}";

	@BeforeAll
	static void boot() {
		VanillaBootstrap.ensure();
	}

	private static PhysicalShop shop() {
		return new PhysicalShop("earthmc.net|world|1,64,2", "earthmc.net", "world",
				new BlockPos(1, 64, 2), new BlockPos(1, 64, 1), "Abasing", null, T, T);
	}

	private static ShopObservation verified() {
		return new ShopObservation("earthmc.net|world|1,64,2", ShopDirection.SELLING,
				new ItemFingerprint(TOKEN, ItemFingerprint.Fidelity.EXACT),
				"Diamond Pickaxe", new BigDecimal("1"), PriceScope.PER_BULK, 7,
				"78", StockUnit.UNKNOWN, 64, null, true, SourceType.SERVER_RESPONSE, T);
	}

	private static ShopObservation signOnly() {
		return new ShopObservation("earthmc.net|world|1,64,2", ShopDirection.SELLING,
				new ItemFingerprint("diamond_pickaxe", ItemFingerprint.Fidelity.DISPLAY_ONLY),
				"Diamond Pickaxe", null, null, null, "36", StockUnit.UNKNOWN,
				null, null, false, SourceType.SIGN_ONLY, T);
	}

	private static ExecutorService executor() {
		return new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
				new ArrayBlockingQueue<>(4), task -> {
					Thread thread = new Thread(task, "ZEA-Market-Test");
					thread.setDaemon(true);
					return thread;
				});
	}

	private static void drain(ExecutorService executor) throws InterruptedException {
		executor.shutdown();
		assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
	}

	@Test
	void serverVerifiedObservationsValidateIntoRecords() {
		SubmissionValidator.SubmissionRecord record =
				SubmissionValidator.validate(shop(), verified(), SUBMITTED).orElseThrow();

		assertEquals("earthmc.net|world|1,64,2", record.shopId());
		assertEquals("SELLING", record.direction());
		assertEquals("1", record.price());
		assertEquals(T, Instant.ofEpochMilli(record.observedAt()));
		assertEquals(SUBMITTED, Instant.ofEpochMilli(record.submittedAt()));
		assertTrue(record.observedAt() != record.submittedAt());
	}

	@Test
	void signOnlyObservationsNeverSubmit() {
		assertTrue(SubmissionValidator.validate(shop(), signOnly(), SUBMITTED).isEmpty());
	}

	@Test
	void aResponseWithoutAPriceIsRejected() {
		ShopObservation unpriced = new ShopObservation("id", ShopDirection.SELLING,
				new ItemFingerprint(TOKEN, ItemFingerprint.Fidelity.EXACT),
				"Diamond Pickaxe", null, null, null, null, StockUnit.UNKNOWN,
				null, null, false, SourceType.SERVER_RESPONSE, T);
		assertTrue(SubmissionValidator.validate(shop(), unpriced, SUBMITTED).isEmpty());
	}

	@Test
	void theQueuePersistsAndSurvivesARestart(@TempDir Path dir) throws InterruptedException {
		Path file = dir.resolve("submissions.json");
		ExecutorService executor = executor();
		SubmissionQueue queue = new SubmissionQueue(file, executor);
		assertTrue(queue.enqueue(shop(), verified()));
		queue.flushNow();
		drain(executor);

		ExecutorService executor2 = executor();
		SubmissionQueue reloaded = new SubmissionQueue(file, executor2);
		assertEquals(1, reloaded.size());
		assertEquals("1", reloaded.queued().get(0).price());
		assertEquals(T, Instant.ofEpochMilli(reloaded.queued().get(0).observedAt()));
		drain(executor2);
	}

	@Test
	void anIdenticalObservationIsNeverQueuedTwice(@TempDir Path dir) {
		SubmissionQueue queue = new SubmissionQueue(dir.resolve("q.json"), executor());
		assertTrue(queue.enqueue(shop(), verified()));
		assertFalse(queue.enqueue(shop(), verified()));

		assertEquals(1, queue.size());
	}

	@Test
	void aChangedPriceQueuesAgain(@TempDir Path dir) {
		SubmissionQueue queue = new SubmissionQueue(dir.resolve("q.json"), executor());
		assertTrue(queue.enqueue(shop(), verified()));
		assertTrue(queue.enqueue(shop(), new ShopObservation("earthmc.net|world|1,64,2",
				ShopDirection.SELLING,
				new ItemFingerprint(TOKEN, ItemFingerprint.Fidelity.EXACT),
				"Diamond Pickaxe", new BigDecimal("2"), PriceScope.PER_BULK, 7,
				"78", StockUnit.UNKNOWN, 64, null, true, SourceType.SERVER_RESPONSE,
				T.plusSeconds(60))));

		assertEquals(2, queue.size());
	}

	@Test
	void aDrainedBatchLeavesTheQueue(@TempDir Path dir) {
		SubmissionQueue queue = new SubmissionQueue(dir.resolve("q.json"), executor());
		queue.enqueue(shop(), verified());

		queue.removeAll(queue.queued());

		assertEquals(0, queue.size());
	}
}
