package io.github.minerguy341.new_age_thaum.content;

import io.github.minerguy341.new_age_thaum.core.ModMenus;
import io.github.minerguy341.new_age_thaum.core.ModRecipes;
import io.github.minerguy341.new_age_thaum.core.ModRegistries;
import io.github.minerguy341.new_age_thaum.core.casting.WandStats;
import io.github.minerguy341.new_age_thaum.core.casting.WandVis;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleContainerData;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

import java.util.Optional;

/**
 * Arcane Worktable menu — a vanilla-shaped 3×3 grid + result, AUGMENTED with a wand slot
 * and a live vis-cost readout (the "augment don't replace" grammar from the workbench-UI
 * study). It resolves {@link ArcaneCraftingRecipe}s first (vis-gated on the wand), then falls
 * back to plain vanilla crafting recipes so the block still doubles as a crafting table.
 *
 * <p>Like the vanilla crafting table the grid is a {@link TransientCraftingContainer} — no
 * block entity, contents drop back to the player on close — so grid, wand slot, and result all
 * ride vanilla slot sync. Only the cost readout needs extra sync, carried by a 3-int
 * {@link SimpleContainerData}. All recipe resolution and consumption is server-authoritative.
 */
public class ArcaneWorktableMenu extends AbstractContainerMenu {
    public static final int RESULT_SLOT = 0;
    public static final int GRID_START = 1;      // 1..9
    public static final int GRID_END = 10;       // exclusive
    public static final int WAND_SLOT = 10;
    public static final int INV_START = 11;      // 11..37
    public static final int HOTBAR_START = 38;   // 38..46
    public static final int HOTBAR_END = 47;     // exclusive

    // Status codes shared with the screen via data slot 2.
    public static final int STATUS_EMPTY = 0;
    public static final int STATUS_ARCANE_READY = 1;
    public static final int STATUS_NEED_WAND = 2;
    public static final int STATUS_INSUFFICIENT = 3;
    public static final int STATUS_VANILLA_READY = 4;

    // Layout (relative to leftPos/topPos); the screen mirrors these.
    public static final int GRID_X = 30, GRID_Y = 18;
    public static final int RESULT_X = 124, RESULT_Y = 36;
    public static final int WAND_X = 124, WAND_Y = 64;
    public static final int INV_X = 8, INV_Y = 108, HOTBAR_Y = 166;

    private final CraftingContainer craftSlots = new TransientCraftingContainer(this, 3, 3);
    private final SimpleContainer wandContainer = new SimpleContainer(1);
    private final ResultContainer resultSlots = new ResultContainer();
    private final ContainerLevelAccess access;
    private final Player player;
    private final BlockPos pos;
    private final SimpleContainerData data = new SimpleContainerData(3);

    // Set by the server on each recompute; read by the result slot's take path.
    private int activeVisCost;
    private boolean activeIsArcane;

    /** Client factory: NULL access + a dummy container; vanilla slot sync + data slots fill it. */
    public ArcaneWorktableMenu(int syncId, Inventory playerInventory, FriendlyByteBuf buf) {
        this(syncId, playerInventory, ContainerLevelAccess.NULL, buf.readBlockPos());
    }

    public ArcaneWorktableMenu(int syncId, Inventory playerInventory, ContainerLevelAccess access, BlockPos pos) {
        super(ModMenus.ARCANE_WORKTABLE.get(), syncId);
        this.access = access;
        this.player = playerInventory.player;
        this.pos = pos;

        addSlot(new ArcaneResultSlot(resultSlots, 0, RESULT_X, RESULT_Y));
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                addSlot(new Slot(craftSlots, col + row * 3, GRID_X + col * 18, GRID_Y + row * 18));
            }
        }
        addSlot(new Slot(wandContainer, 0, WAND_X, WAND_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return WandVis.isReservoir(stack);
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }

            @Override
            public void setChanged() {
                super.setChanged();
                // SimpleContainer (unlike the grid's TransientCraftingContainer) does not notify
                // the menu, so inserting/removing the wand wouldn't recompute the vis readout.
                slotsChanged(wandContainer);
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
        addDataSlots(data);
    }

    public BlockPos pos() {
        return pos;
    }

    public int visCost() {
        return data.get(0);
    }

    public int wandVis() {
        return data.get(1);
    }

    public int status() {
        return data.get(2);
    }

    /** True when the wand slot holds a vis reservoir — the screen uses this for the ghost hint. */
    public boolean hasWand() {
        return getSlot(WAND_SLOT).hasItem();
    }

    @Override
    public void slotsChanged(net.minecraft.world.Container container) {
        access.execute((level, blockPos) -> updateResult(level, player));
    }

    /** Server-authoritative: resolve arcane (then vanilla) recipe, set result + cost readout. */
    private void updateResult(Level level, Player who) {
        if (level.isClientSide || !(who instanceof ServerPlayer serverPlayer)) {
            return;
        }
        CraftingInput input = craftSlots.asCraftInput();
        ItemStack result = ItemStack.EMPTY;
        int status = STATUS_EMPTY;
        int cost = 0;
        int available = 0;
        activeVisCost = 0;
        activeIsArcane = false;

        Optional<RecipeHolder<ArcaneCraftingRecipe>> arcane =
                level.getRecipeManager().getRecipeFor(ModRecipes.ARCANE_CRAFTING_TYPE.get(), input, level);
        if (arcane.isPresent()) {
            ArcaneCraftingRecipe recipe = arcane.get().value();
            ItemStack wand = wandContainer.getItem(0);
            cost = effectiveCost(recipe.visCost(), wand);
            if (!WandVis.isReservoir(wand)) {
                status = STATUS_NEED_WAND;
            } else {
                available = Math.round(WandVis.get(wand));
                if (available >= cost) {
                    result = recipe.assemble(input, level.registryAccess());
                    status = STATUS_ARCANE_READY;
                    activeVisCost = cost;
                    activeIsArcane = true;
                } else {
                    status = STATUS_INSUFFICIENT;
                }
            }
        } else {
            Optional<RecipeHolder<net.minecraft.world.item.crafting.CraftingRecipe>> vanilla =
                    level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level);
            if (vanilla.isPresent()) {
                result = vanilla.get().value().assemble(input, level.registryAccess());
                status = STATUS_VANILLA_READY;
            }
        }

        data.set(0, cost);
        data.set(1, available);
        data.set(2, status);
        resultSlots.setItem(0, result);
        setRemoteSlot(RESULT_SLOT, result);
        serverPlayer.connection.send(
                new ClientboundContainerSetSlotPacket(containerId, incrementStateId(), RESULT_SLOT, result));
    }

    /** Recipe cost minus the wand's cap discount, rounded up, never below 0. */
    private static int effectiveCost(int base, ItemStack wand) {
        if (base <= 0) {
            return 0;
        }
        double discount = 0.0;
        if (WandVis.isReservoir(wand)) {
            var component = CastingImplementItem.componentOf(wand);
            if (component != null && wand.getItem() instanceof CastingImplementItem impl) {
                discount = WandStats.compute(component, impl.form()).discount();
            }
        }
        return Math.max(0, (int) Math.ceil(base * (1.0 - discount)));
    }

    /** Called from the result slot on a successful take: spend vis, consume one of each input. */
    private void onCrafted() {
        if (activeIsArcane && activeVisCost > 0) {
            WandVis.add(wandContainer.getItem(0), -activeVisCost);
        }
        for (int i = 0; i < craftSlots.getContainerSize(); i++) {
            ItemStack stack = craftSlots.getItem(i);
            if (!stack.isEmpty()) {
                craftSlots.removeItem(i, 1);
            }
        }
        access.execute((level, blockPos) -> updateResult(level, player));
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, ModRegistries.ARCANE_WORKTABLE.get());
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        // Transient grid + wand slot: hand contents back to the player on close (vanilla table).
        access.execute((level, blockPos) -> {
            clearContainer(player, craftSlots);
            clearContainer(player, wandContainer);
        });
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack moved = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack stack = slot.getItem();
            moved = stack.copy();
            if (index == RESULT_SLOT) {
                // Shift-craft: doClick loops quickMoveStack while the result slot keeps
                // repopulating (onTake recomputes), so this crafts as many as the grid allows.
                if (!moveItemStackTo(stack, INV_START, HOTBAR_END, true)) {
                    return ItemStack.EMPTY;
                }
                slot.onQuickCraft(stack, moved);
            } else if (index >= INV_START) {
                if (WandVis.isReservoir(stack) && !getSlot(WAND_SLOT).hasItem()) {
                    if (!moveItemStackTo(stack, WAND_SLOT, WAND_SLOT + 1, false)) {
                        return ItemStack.EMPTY;
                    }
                } else if (index < HOTBAR_START) {
                    if (!moveItemStackTo(stack, HOTBAR_START, HOTBAR_END, false)) {
                        return ItemStack.EMPTY;
                    }
                } else if (!moveItemStackTo(stack, INV_START, HOTBAR_START, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (!moveItemStackTo(stack, INV_START, HOTBAR_END, false)) {
                return ItemStack.EMPTY;
            }

            if (stack.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
            if (stack.getCount() == moved.getCount()) {
                return ItemStack.EMPTY;
            }
            slot.onTake(player, stack);
            if (index == RESULT_SLOT) {
                player.drop(stack, false);
            }
        }
        return moved;
    }

    /** Result slot: no insert, and taking it spends vis + consumes the grid via {@link #onCrafted()}. */
    private class ArcaneResultSlot extends Slot {
        ArcaneResultSlot(net.minecraft.world.Container container, int slot, int x, int y) {
            super(container, slot, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        @Override
        public void onTake(Player taker, ItemStack stack) {
            access.execute((level, blockPos) -> onCrafted());
            super.onTake(taker, stack);
        }
    }
}
