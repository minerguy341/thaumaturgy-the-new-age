package io.github.minerguy341.new_age_thaum.content;

import dev.architectury.registry.menu.ExtendedMenuProvider;
import dev.architectury.registry.menu.MenuRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The Arcane Worktable: right-click opens the 3×3 arcane crafting grid + wand slot. Like a
 * vanilla crafting table it has NO block entity — the menu's grid is transient and its
 * contents drop back to the player on close. The block's {@link BlockPos} rides to the client
 * as the menu's extended data (for {@code stillValid} and the screen title).
 */
public class ArcaneWorktableBlock extends Block {
    public ArcaneWorktableBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            MenuRegistry.openExtendedMenu(serverPlayer, new ExtendedMenuProvider() {
                @Override
                public void saveExtraData(FriendlyByteBuf buf) {
                    buf.writeBlockPos(pos);
                }

                @Override
                public Component getDisplayName() {
                    return Component.translatable("screen.new_age_thaum.arcane_worktable");
                }

                @Override
                public AbstractContainerMenu createMenu(int syncId, Inventory inventory, Player p) {
                    return new ArcaneWorktableMenu(syncId, inventory,
                            ContainerLevelAccess.create(level, pos), pos);
                }
            });
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
