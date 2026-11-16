package net.zaminmc.zea.market;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignTextSlot;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.zaminmc.zea.chat.capture.MessageCapture;
import net.zaminmc.zea.chat.model.CapturedMessage;
import net.zaminmc.zea.earthmc.ServerIdentity;
import net.zaminmc.zea.lifecycle.ClientLifecycle;
import net.zaminmc.zea.market.config.MarketConfig;
import net.zaminmc.zea.market.interaction.ShopInspectionTracker;
import net.zaminmc.zea.market.interaction.ShopSignGeometry;
import net.zaminmc.zea.market.model.ShopResponse;
import net.zaminmc.zea.market.store.MarketStore;
import net.zaminmc.zea.market.sync.SubmissionQueue;
import net.zaminmc.zea.market.ui.ShopScreens;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutorService;

/**
 * The market subsystem: turns physical shop inspections into persisted
 * market data. Sign right-clicks arrive from the game-mode mixin, chat
 * arrives through its own capture hook, and the inspection tracker
 * correlates the two — the result is a shop inspection carrying either a
 * server-verified response or the sign text that stands in when the
 * server never responded. Every accepted inspection is recorded into the
 * market index (a shop revisit updates the shop and appends an
 * observation) and scheduled for a disk write.
 *
 * <p>All state mutations run on the client thread: capture events, ticks
 * and the mixin are client-thread, so the index needs no locks; disk IO
 * happens on the market executor via snapshots taken on the client
 * thread. The market works fully offline — persistence never touches a
 * network.
 */
public final class MarketSubsystem {
	private final ClientLifecycle lifecycle;
	private final ShopInspectionTracker tracker;
	private final MarketData data;
	private final MarketPersistence persistence;
	private final MarketConfig config;
	private final SubmissionQueue submissions;
	// The town/nation lookup over claim geometry; null when no claim
	// source is wired, which leaves town and nation simply unavailable.
	private final MarketListings.TownLookup towns;
	// Null until the first inspection lands; read on the client thread only.
	private volatile ShopInspectionTracker.Inspection latestInspection;

	public MarketSubsystem(ClientLifecycle lifecycle, Path file, ExecutorService executor,
			MarketConfig config, MarketListings.TownLookup towns) {
		this.lifecycle = lifecycle;
		this.config = config;
		this.towns = towns;
		this.tracker = new ShopInspectionTracker(this::onInspection);
		this.data = restore(file);
		this.persistence = new MarketPersistence(new MarketStore(file), executor);
		this.submissions = new SubmissionQueue(file.resolveSibling("market-submissions.json"), executor);
		// Pending inspections belong to one connection: a disconnect or a
		// world change leaves nothing for a response to correlate with.
		// The recorded market, in contrast, survives every disconnect —
		// that is the point of it.
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset());
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			tracker.tick(Instant.now());
			persistence.tick(System.currentTimeMillis());
		});
		// A second, independent capture sink — the chat pipeline keeps
		// its own; the market reads the same events and never touches
		// the chat feed.
		new MessageCapture(lifecycle::onEarthMc, this::onMessage);
	}

	/** The market index: known shops and their observation history. */
	public MarketData data() {
		return data;
	}

	/**
	 * The town/nation lookup the browser consults, from the claim store's
	 * published geometry; {@code null} when no claim source is wired.
	 */
	public MarketListings.TownLookup townLookup() {
		return towns;
	}

	/** Forces pending market and queue writes to land; used for shutdown. */
	public void flushNow() {
		persistence.flushNow();
		submissions.flushNow();
	}

	/** Forgets pending inspection state (disconnect, world change), keeps the market. */
	public void reset() {
		tracker.reset();
		latestInspection = null;
	}

	private void onInspection(ShopInspectionTracker.Inspection inspection) {
		latestInspection = inspection;
		Observations.of(inspection).ifPresent(recorded -> {
			if (data.record(recorded.shop(), recorded.observation())) {
				persistence.markDirty(MarketPersistence.snapshot(data));
				submissions.enqueue(recorded.shop(), recorded.observation());
			}
			// The structured shop interface replaces the raw chat wall for
			// server-verified inspections when the player wants it. A
			// partial response never opens a falsely complete screen; a
			// sign-only advert stays in the market data without
			// interrupting the player.
			if (config.openShopScreen && inspection.response() != null
					&& inspection.response().completeness() == ShopResponse.Completeness.COMPLETE
					&& inspection.response().price() != null) {
				ShopScreens.open(recorded.shop(), recorded.observation(), inspection.response());
			}
		});
	}

	/**
	 * The most recent inspection, for the shop screen that presents it.
	 * Cleared on disconnect along with the rest of the pending state.
	 */
	public ShopInspectionTracker.Inspection latestInspection() {
		return latestInspection;
	}

	/** A sign was right-clicked; the market records what the player inspected. */
	public void onBlockUse(ClientLevel level, BlockHitResult hit) {
		BlockPos pos = hit.getBlockPos();
		BlockState state = level.getBlockState(pos);
		List<String> signLines = readSignText(level, pos);
		// The chest slot stays empty unless a chest is provably attached —
		// a sign over anything else is recorded with sign position only.
		BlockPos chestPos = ShopSignGeometry.attachedBlock(state, pos)
				.filter(candidate -> level.getBlockState(candidate).getBlock() instanceof ChestBlock)
				.orElse(null);
		String serverId = lifecycle.earthMcServer().map(ServerIdentity::host).orElse("unknown");
		tracker.noteSignUse(level.dimension().identifier().toString(), serverId,
				pos, chestPos, signLines, Instant.now());
	}

	/** One captured chat message; only EarthMC sessions are inspected. */
	public void onMessage(CapturedMessage message) {
		if (!lifecycle.onEarthMc()) {
			return;
		}
		tracker.onMessage(message, Instant.now());
	}

	private List<String> readSignText(ClientLevel level, BlockPos pos) {
		if (level.getBlockEntity(pos) instanceof SignBlockEntity sign) {
			return sign.getText(SignTextSlot.FRONT).getMessages(false).stream()
					.map(Component::getString)
					.toList();
		}
		return List.of();
	}

	/**
	 * Restores the index from disk with full entry tolerance: shops
	 * without a position or observations without an id, direction or
	 * timestamp are dropped, never crash the load.
	 */
	private static MarketData restore(Path file) {
		MarketStore.MarketFile stored = new MarketStore(file).load();
		MarketData data = new MarketData();
		for (MarketStore.ShopWire wire : MarketStore.usableShops(stored)) {
			MarketStore.toShop(wire).ifPresent(data::putShop);
		}
		for (MarketStore.ObservationWire wire : MarketStore.usableObservations(stored)) {
			MarketStore.toObservation(wire).ifPresent(data::putObservation);
		}
		return data;
	}
}
