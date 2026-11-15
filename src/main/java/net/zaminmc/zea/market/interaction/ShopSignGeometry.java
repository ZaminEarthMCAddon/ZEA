package net.zaminmc.zea.market.interaction;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.SignBlock;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

/**
 * Where a sign's attached chest must be, from the sign's own placement.
 *
 * <p>A wall sign is attached to the block its facing points away from; a
 * standing sign is attached to the block below. The candidate position is
 * the identity's chest slot when the client can confirm a chest is
 * actually there — a shop sign over anything else keeps a null chest.
 */
public final class ShopSignGeometry {
	private ShopSignGeometry() {
	}

	/**
	 * The block position the sign hangs on, when the sign state is a
	 * wall or standing sign. {@code empty} for any other block.
	 */
	public static Optional<BlockPos> attachedBlock(BlockState signState, BlockPos signPos) {
		if (signState.getBlock() instanceof WallSignBlock) {
			return Optional.of(signPos.relative(signState.getValue(WallSignBlock.FACING).getOpposite()));
		}
		if (signState.getBlock() instanceof SignBlock) {
			return Optional.of(signPos.below());
		}
		return Optional.empty();
	}
}
