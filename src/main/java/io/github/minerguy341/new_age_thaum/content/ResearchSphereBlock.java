package io.github.minerguy341.new_age_thaum.content;

import dev.architectury.registry.menu.ExtendedMenuProvider;
import dev.architectury.registry.menu.MenuRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * Right-clicking opens the orrery menu (paper slot + player inventory); the client-side
 * screen renders the research sphere from this block's {@link ArcaneOrreryBlockEntity}.
 * The BlockPos travels to the client as the menu's extended data.
 */
public class ResearchSphereBlock extends Block implements EntityBlock {
    public ResearchSphereBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ArcaneOrreryBlockEntity(pos, state);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        // The research paper lives only in the block entity — spill it on break so a
        // relocation (or a creeper) never deletes the player's painted research.
        if (!state.is(newState.getBlock())
                && level.getBlockEntity(pos) instanceof ArcaneOrreryBlockEntity orrery) {
            Containers.dropContents(level, pos, orrery);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(pos) instanceof ArcaneOrreryBlockEntity orrery) {
            MenuRegistry.openExtendedMenu(serverPlayer, new ExtendedMenuProvider() {
                @Override
                public void saveExtraData(FriendlyByteBuf buf) {
                    buf.writeBlockPos(pos);
                }

                @Override
                public Component getDisplayName() {
                    return Component.translatable("screen.new_age_thaum.research_sphere");
                }

                @Override
                public AbstractContainerMenu createMenu(int syncId, Inventory inventory, Player p) {
                    return new ArcaneOrreryMenu(syncId, inventory, orrery, pos);
                }
            });
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
