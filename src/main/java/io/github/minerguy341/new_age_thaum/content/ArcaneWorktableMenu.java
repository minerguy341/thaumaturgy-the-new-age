package io.github.minerguy341.new_age_thaum.content;

import io.github.minerguy341.new_age_thaum.core.ModMenus;
import io.github.minerguy341.new_age_thaum.core.ModRecipes;
import io.github.minerguy341.new_age_thaum.core.ModRegistries;
import io.github.minerguy341.new_age_thaum.core.aspect.AspectBag;
import io.github.minerguy341.new_age_thaum.core.aspect.Primals;
import io.github.minerguy341.new_age_thaum.core.casting.WandStats;
import io.github.minerguy341.new_age_thaum.core.casting.WandVis;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
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
 * ride vanilla slot sync. Only the per-primal cost readout needs extra sync, carried by a
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

    // Layout — a custom 194x208 arcane panel following TC4's Arcane Worktable: the 3×3 grid is
    // CENTRED with the six primal aspect symbols ringing it (a hexagon), the wand slot TOP-RIGHT
    // (TC4 auto-inserts the wand there), the result below it. Slots are stamped from a real
    // vanilla slot cell so they still read as Minecraft.
    public static final int GRID_X = 70, GRID_Y = 34;   // centred: 70..124, panel centre x=97
    public static final int WAND_X = 168, WAND_Y = 20;  // top-right (TC4)
    public static final int RESULT_X = 168, RESULT_Y = 52;
    public static final int INV_X = 16, INV_Y = 126, HOTBAR_Y = 184;
    // Grid centre — the hexagon of six primal aspect glyphs is drawn around this.
    public static final int GRID_CX = GRID_X + 27, GRID_CY = GRID_Y + 27; // 3x3 @18 = 54px

    private final CraftingContainer craftSlots = new TransientCraftingContainer(this, 3, 3);
    private final SimpleContainer wandContainer = new SimpleContainer(1);
    private final ResultContainer resultSlots = new ResultContainer();
    private final ContainerLevelAccess access;
    private final Player player;
    private final BlockPos pos;
    // Data slots: [0..5] effective per-primal cost, [6..11] wand's per-primal vis, [12] status
    // — all indexed in Primals.LIST order.
    private final SimpleContainerData data = new SimpleContainerData(2 * Primals.COUNT + 1);

    // The effective per-primal cost of the currently-ready arcane recipe; spent on take.
    private AspectBag activeCost = AspectBag.EMPTY;
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

    /** Effective vis cost of the primal at ring-index {@code i} for the current recipe (0=none). */
    public int costOf(int i) {
        return data.get(i);
    }

    /** Wand's available vis of the primal at ring-index {@code i}. */
    public int availOf(int i) {
        return data.get(Primals.COUNT + i);
    }

    public int status() {
        return data.get(2 * Primals.COUNT);
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
        AspectBag cost = AspectBag.EMPTY;
        ItemStack wand = wandContainer.getItem(0);
        activeCost = AspectBag.EMPTY;
        activeIsArcane = false;

        Optional<RecipeHolder<ArcaneCraftingRecipe>> arcane =
                level.getRecipeManager().getRecipeFor(ModRecipes.ARCANE_CRAFTING_TYPE.get(), input, level);
        if (arcane.isPresent()) {
            ArcaneCraftingRecipe recipe = arcane.get().value();
            cost = effectiveCost(recipe.visCost(), wand);
            if (!WandVis.isReservoir(wand)) {
                status = STATUS_NEED_WAND;
            } else if (affordable(cost, wand)) {
                result = recipe.assemble(input, level.registryAccess());
                status = STATUS_ARCANE_READY;
                activeCost = cost;
                activeIsArcane = true;
            } else {
                status = STATUS_INSUFFICIENT;
            }
        } else {
            Optional<RecipeHolder<net.minecraft.world.item.crafting.CraftingRecipe>> vanilla =
                    level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level);
            if (vanilla.isPresent()) {
                result = vanilla.get().value().assemble(input, level.registryAccess());
                status = STATUS_VANILLA_READY;
            }
        }

        boolean reservoir = WandVis.isReservoir(wand);
        for (int i = 0; i < Primals.COUNT; i++) {
            ResourceLocation primal = Primals.LIST.get(i);
            data.set(i, cost.amountOf(primal));
            data.set(Primals.COUNT + i, reservoir ? WandVis.amountOf(wand, primal) : 0);
        }
        data.set(2 * Primals.COUNT, status);
        resultSlots.setItem(0, result);
        setRemoteSlot(RESULT_SLOT, result);
        serverPlayer.connection.send(
                new ClientboundContainerSetSlotPacket(containerId, incrementStateId(), RESULT_SLOT, result));
    }

    /** Per-primal recipe cost after the wand's cap discount (ceil, at least 1 where a cost exists). */
    private static AspectBag effectiveCost(AspectBag base, ItemStack wand) {
        if (base.isEmpty()) {
            return AspectBag.EMPTY;
        }
        double discount = 0.0;
        if (WandVis.isReservoir(wand)) {
            var component = CastingImplementItem.componentOf(wand);
            if (component != null && wand.getItem() instanceof CastingImplementItem impl) {
                discount = WandStats.compute(component, impl.form()).discount();
            }
        }
        AspectBag result = AspectBag.EMPTY;
        for (var entry : base.amounts().entrySet()) {
            int effective = (int) Math.ceil(entry.getValue() * (1.0 - discount));
            result = result.with(entry.getKey(), Math.max(1, effective));
        }
        return result;
    }

    /** True when the wand holds at least the required vis of every primal in {@code cost}. */
    private static boolean affordable(AspectBag cost, ItemStack wand) {
        for (var entry : cost.amounts().entrySet()) {
            if (WandVis.amountOf(wand, entry.getKey()) < entry.getValue()) {
                return false;
            }
        }
        return true;
    }

    /** Called from the result slot on a successful take: spend vis, consume one of each input. */
    private void onCrafted() {
        if (activeIsArcane && !activeCost.isEmpty()) {
            ItemStack wand = wandContainer.getItem(0);
            for (var entry : activeCost.amounts().entrySet()) {
                WandVis.add(wand, entry.getKey(), -entry.getValue());
            }
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
