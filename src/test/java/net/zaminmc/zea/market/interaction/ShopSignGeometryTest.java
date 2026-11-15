package net.zaminmc.zea.market.interaction;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SignBlock;
import net.minecraft.world.level.block.WallSignBlock;
import net.zaminmc.zea.VanillaBootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShopSignGeometryTest {
	@BeforeAll
	static void boot() {
		VanillaBootstrap.ensure();
	}

	private static final BlockPos SIGN = new BlockPos(10, 64, 20);

	@Test
	void aWallSignHangsOnTheBlockItsFacingPointsAwayFrom() {
		var wall = Blocks.OAK_WALL_SIGN.defaultBlockState()
				.setValue(WallSignBlock.FACING, Direction.NORTH);

		assertEquals(Optional.of(SIGN.relative(Direction.SOUTH)),
				ShopSignGeometry.attachedBlock(wall, SIGN));
	}

	@Test
	void aStandingSignHangsOnTheBlockBelow() {
		var standing = Blocks.OAK_SIGN.defaultBlockState();

		assertEquals(Optional.of(SIGN.below()),
				ShopSignGeometry.attachedBlock(standing, SIGN));
	}

	@Test
	void nonSignBlocksHaveNoAttachment() {
		assertEquals(Optional.empty(),
				ShopSignGeometry.attachedBlock(Blocks.STONE.defaultBlockState(), SIGN));
	}

	@Test
	void everyWallOrientationResolvesOppositeOfItsFacing() {
		for (Direction facing : Direction.Plane.HORIZONTAL) {
			var wall = Blocks.OAK_WALL_SIGN.defaultBlockState()
					.setValue(WallSignBlock.FACING, facing);
			assertEquals(Optional.of(SIGN.relative(facing.getOpposite())),
					ShopSignGeometry.attachedBlock(wall, SIGN), "facing " + facing);
		}
		assertTrue(true);
	}
}
