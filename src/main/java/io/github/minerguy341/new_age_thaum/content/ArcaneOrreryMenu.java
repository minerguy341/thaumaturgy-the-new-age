package io.github.minerguy341.new_age_thaum.content;

import io.github.minerguy341.new_age_thaum.core.ModMenus;
import io.github.minerguy341.new_age_thaum.core.ModRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Menu for the Arcane Orrery: one research-paper slot backed by the block entity's
 * container, plus the standard player inventory + hotbar. Slot coordinates are fixed
 * against the screen's 420x240 panel. The sphere itself is not menu state — the screen
 * renders it from the block entity, addressed by {@link #pos()}.
 */
public class ArcaneOrreryMenu extends AbstractContainerMenu {
    public static final int PAPER_SLOT_X = 12;
    public static final int PAPER_SLOT_Y = 136;
    public static final int INV_X = 12;
    public static final int INV_Y = 157;
    public static final int HOTBAR_Y = 217;

    private final Container container;
    private final BlockPos pos;

    /** Client factory: the container is a dummy — vanilla slot sync fills it. */
    public ArcaneOrreryMenu(int syncId, Inventory playerInventory, FriendlyByteBuf buf) {
        this(syncId, playerInventory, new SimpleContainer(1), buf.readBlockPos());
    }

    public ArcaneOrreryMenu(int syncId, Inventory playerInventory, Container container, BlockPos pos) {
        super(ModMenus.ARCANE_ORRERY.get(), syncId);
        this.container = container;
        this.pos = pos;
        checkContainerSize(container, 1);

        addSlot(new Slot(container, 0, PAPER_SLOT_X, PAPER_SLOT_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.getItem() instanceof ResearchPaperItem;
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9, INV_X + col * 18, INV_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, INV_X + col * 18, HOTBAR_Y));
        }
    }

    public BlockPos pos() {
        return pos;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot.hasItem()) {
            ItemStack stack = slot.getItem();
            result = stack.copy();
            if (index == 0) {
                if (!moveItemStackTo(stack, 1, 37, true)) {
                    return ItemStack.EMPTY;
                }
            } else if (!moveItemStackTo(stack, 0, 1, false)) {
                if (index < 28) {
                    if (!moveItemStackTo(stack, 28, 37, false)) {
                        return ItemStack.EMPTY;
                    }
                } else if (!moveItemStackTo(stack, 1, 28, false)) {
                    return ItemStack.EMPTY;
                }
            }
            if (stack.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
            if (stack.getCount() == result.getCount()) {
                return ItemStack.EMPTY;
            }
            slot.onTake(player, stack);
        }
        return result;
    }

    @Override
    public boolean stillValid(Player player) {
        return container.stillValid(player);
    }
}
