package io.github.minerguy341.new_age_thaum.content;

import io.github.minerguy341.new_age_thaum.core.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * The Thaumic Dioptra: a pedestal that projects a holographic terrain map of the
 * surrounding chunks' ambient vis (the hologram is the block entity renderer's; the
 * pedestal is the block model). Side-by-side dioptras tile one contiguous map via
 * {@link DioptraGroup}, and a comparator reads the block's own chunk vis as 0–15.
 */
public class ThaumicDioptraBlock extends Block implements EntityBlock {
    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(0, 0, 0, 16, 3, 16),
            Block.box(4, 3, 4, 12, 13, 12),
            Block.box(1, 13, 1, 15, 16, 15));

    public ThaumicDioptraBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ThaumicDioptraBlockEntity(pos, state);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        super.onRemove(state, level, pos, newState, movedByPiston);
        // A removed dioptra may split its group: recompute from each surviving neighbor
        // (each fragment gets its own pass; refreshing one fragment twice is harmless).
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel serverLevel) {
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                DioptraGroup.refresh(serverLevel, pos.relative(direction));
            }
        }
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof ThaumicDioptraBlockEntity dioptra
                ? dioptra.comparatorSignal() : 0;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || type != ModBlockEntities.THAUMIC_DIOPTRA.get()) {
            return null;
        }
        return (tickLevel, pos, tickState, be) -> ((ThaumicDioptraBlockEntity) be).serverTick((ServerLevel) tickLevel);
    }
}
