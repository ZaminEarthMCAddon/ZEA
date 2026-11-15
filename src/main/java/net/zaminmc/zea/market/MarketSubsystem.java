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
import net.zaminmc.zea.market.interaction.ShopInspectionTracker;
import net.zaminmc.zea.market.interaction.ShopSignGeometry;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;

/**
 * The market subsystem: turns physical shop inspections into market
 * data. Sign right-clicks arrive from the game-mode mixin, chat arrives
 * through its own capture hook, and the inspection tracker correlates
 * the two — the result is a shop inspection carrying either a
 * server-verified response or the sign text that stands in when the
 * server never responded.
 *
 * <p>The subsystem owns no persistence and no UI; it produces
 * inspections and hands them on. Everything runs on the client thread:
 * capture events and ticks are client-thread, and the mixin fires
 * mid-interaction, where no other thread can race it.
 */
public final class MarketSubsystem {
	private final ClientLifecycle lifecycle;
	private final ShopInspectionTracker tracker;
	// Null until the first inspection lands; read on the client thread only.
	private volatile ShopInspectionTracker.Inspection latestInspection;

	public MarketSubsystem(ClientLifecycle lifecycle) {
		this.lifecycle = lifecycle;
		this.tracker = new ShopInspectionTracker(this::onInspection);
		// Pending inspections belong to one connection: a disconnect or a
		// world change leaves nothing for a response to correlate with.
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset());
		ClientTickEvents.END_CLIENT_TICK.register(client -> tracker.tick(Instant.now()));
		// A second, independent capture sink — the chat pipeline keeps
		// its own; the market reads the same events and never touches
		// the chat feed.
		new MessageCapture(lifecycle::onEarthMc, this::onMessage);
	}

	/** Forgets all pending inspection state (disconnect, world change). */
	public void reset() {
		tracker.reset();
		latestInspection = null;
	}

	private void onInspection(ShopInspectionTracker.Inspection inspection) {
		latestInspection = inspection;
	}

	/**
	 * The most recent inspection, for the shop screen that presents it.
	 * Cleared on disconnect along with the rest of the pending state.
	 */
	/** The most recent inspection, or {@code null} before the first one. */
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
}
