package io.github.minerguy341.new_age_thaum.content;

import io.github.minerguy341.new_age_thaum.core.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * The Thaumic Dioptra: a pedestal that projects a holographic terrain map of the
 * surrounding chunks' ambient vis (the hologram is the block entity renderer's; the
 * pedestal is the block model). Side-by-side dioptras tile one contiguous map via
 * {@link DioptraGroup}, and a comparator reads the block's own chunk vis as 0–15.
 *
 * <p>The basin rim is drawn per-side only where there is NO adjacent dioptra
 * ({@code RIM_*} state), so a slab of dioptras forms one continuous basin (one outer
 * rim) rather than a grid of separate divots — matching the merged hologram.
 */
public class ThaumicDioptraBlock extends Block implements EntityBlock {
    /** True on a side means "draw the rim there" — i.e. no dioptra is adjacent that way. */
    public static final BooleanProperty RIM_NORTH = BooleanProperty.create("rim_north");
    public static final BooleanProperty RIM_EAST = BooleanProperty.create("rim_east");
    public static final BooleanProperty RIM_SOUTH = BooleanProperty.create("rim_south");
    public static final BooleanProperty RIM_WEST = BooleanProperty.create("rim_west");

    // Mostly a full block (TC6-style): solid body with a shallow basin rim on top.
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 15, 16);

    public ThaumicDioptraBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState()
                .setValue(RIM_NORTH, true).setValue(RIM_EAST, true)
                .setValue(RIM_SOUTH, true).setValue(RIM_WEST, true));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(RIM_NORTH, RIM_EAST, RIM_SOUTH, RIM_WEST);
    }

    private static BooleanProperty rimFor(Direction direction) {
        return switch (direction) {
            case NORTH -> RIM_NORTH;
            case EAST -> RIM_EAST;
            case SOUTH -> RIM_SOUTH;
            default -> RIM_WEST;
        };
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = defaultBlockState();
        LevelReader level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            state = state.setValue(rimFor(direction), !level.getBlockState(pos.relative(direction)).is(this));
        }
        return state;
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
            LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        // A dioptra neighbor appears/disappears on a horizontal side: drop or restore
        // that rim so interior seams merge into one basin.
        if (direction.getAxis().isHorizontal()) {
            return state.setValue(rimFor(direction), !neighborState.is(this));
        }
        return state;
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
