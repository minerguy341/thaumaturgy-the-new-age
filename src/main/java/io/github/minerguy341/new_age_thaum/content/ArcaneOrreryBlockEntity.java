package io.github.minerguy341.new_age_thaum.content;

import io.github.minerguy341.new_age_thaum.core.ModBlockEntities;
import io.github.minerguy341.new_age_thaum.core.ModComponents;
import io.github.minerguy341.new_age_thaum.core.ModRegistries;
import io.github.minerguy341.new_age_thaum.core.research.ResearchSphereData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Optional;

/**
 * The orrery is a workstation: it holds one research paper (a one-slot {@link Container}
 * for the menu) and edits the sphere data component ON the paper. The painted sphere
 * itself persists with the paper item, not this block — take the paper and the research
 * goes with it.
 */
public class ArcaneOrreryBlockEntity extends BlockEntity implements Container {
    /** The sphere's rest pose, shared with the screen. JOML is mutable — never mutate this. */
    public static final org.joml.Quaternionf DEFAULT_ORIENTATION =
            new org.joml.Quaternionf().rotateY(0.6f).rotateX(-0.35f);
    /** Default flick-coast friction time constant (configurable: coastFriction). */
    public static final float COAST_TAU_MS = 700f;

    private ItemStack paper = ItemStack.EMPTY;
    // The puzzle's world orientation, mirrored by the hologram. Driven by the screen's
    // drag rotation via OrreryRotatePayload; persisted so it survives reload. Always the
    // REST pose — a flick coast is display-only state layered on top (below).
    private final org.joml.Quaternionf orientation = new org.joml.Quaternionf(DEFAULT_ORIENTATION);
    // Ephemeral flick coast: the display pose still has `coastAngle * e^(-t/tau)` radians
    // to travel about coastAxis before settling on `orientation`. Not persisted — a chunk
    // reload mid-coast simply shows the rest pose.
    private final org.joml.Vector3f coastAxis = new org.joml.Vector3f();
    private float coastAngle;
    private float coastTau = COAST_TAU_MS; // per-flick: rides in the packet (coastFriction config)
    private long coastStart;

    public ArcaneOrreryBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ARCANE_ORRERY.get(), pos, state);
    }

    /** Read-only by convention — callers must not mutate the returned quaternion. */
    public org.joml.Quaternionf orientation() {
        return orientation;
    }

    public void setOrientation(org.joml.Quaternionf value) {
        orientation.set(value).normalize();
        coastAngle = 0; // a directly set pose overrides any in-flight coast
        setChanged();
    }

    /** Begins the display-only coast toward the (already stored) rest pose. */
    public void startCoast(float axisX, float axisY, float axisZ, float remainingAngle, float tauMs) {
        coastAxis.set(axisX, axisY, axisZ);
        coastAngle = remainingAngle;
        coastTau = tauMs;
        coastStart = net.minecraft.Util.getMillis();
    }

    /**
     * The pose to draw right now: the rest pose wound back by however much of the flick
     * coast has not yet played out. Converges on {@link #orientation()} within ~3s.
     */
    public org.joml.Quaternionf displayOrientation() {
        if (coastAngle != 0) {
            float remaining = (float) (coastAngle
                    * Math.exp((coastStart - net.minecraft.Util.getMillis()) / (double) coastTau));
            if (Math.abs(remaining) < 0.005f) {
                coastAngle = 0;
            } else {
                return new org.joml.Quaternionf()
                        .rotationAxis(-remaining, coastAxis).mul(orientation);
            }
        }
        return new org.joml.Quaternionf(orientation);
    }

    public ItemStack paper() {
        return paper;
    }

    public void setPaper(ItemStack stack) {
        paper = stack;
        stampPuzzleIfNeeded();
        sync();
    }

    /** True when the slot holds a research paper whose sphere can be edited. */
    public boolean canEditSphere() {
        return paper.getItem() instanceof ResearchPaperItem;
    }

    /** The paper's generated puzzle, if it has one yet. */
    public java.util.Optional<io.github.minerguy341.new_age_thaum.core.research.ResearchPuzzle> puzzle() {
        return java.util.Optional.ofNullable(
                paper.get(ModComponents.RESEARCH_PUZZLE.get()));
    }

    /** All painted cells on the held paper. */
    public java.util.Map<Integer, ResourceLocation> sphereCells() {
        return paper.getOrDefault(ModComponents.RESEARCH_SPHERE.get(), ResearchSphereData.EMPTY).cells();
    }

    /** Seals the paper's puzzle as solved. Server authority; flips exactly once. */
    public void markSolved() {
        io.github.minerguy341.new_age_thaum.core.research.ResearchPuzzle puzzle =
                paper.get(ModComponents.RESEARCH_PUZZLE.get());
        if (puzzle != null && !puzzle.solved()) {
            paper.set(ModComponents.RESEARCH_PUZZLE.get(), puzzle.asSolved());
            sync();
        }
    }

    /**
     * Lazy generation: the first time a tiered paper sits in a (server-side) orrery, its
     * puzzle is rolled from the tier parameters and stamped on as a component.
     */
    private boolean stampPuzzleIfNeeded() {
        if (level == null || level.isClientSide
                || !(paper.getItem() instanceof ResearchPaperItem item)
                || paper.has(ModComponents.RESEARCH_PUZZLE.get())) {
            return false;
        }
        paper.set(ModComponents.RESEARCH_PUZZLE.get(),
                io.github.minerguy341.new_age_thaum.core.research.PuzzleGenerator.generate(item.tier(), level.random));
        return true;
    }

    @Override
    public void setLevel(net.minecraft.world.level.Level level) {
        super.setLevel(level);
        // loadAdditional restores the paper before a level exists, so papers saved
        // unstamped (pre-puzzle worlds, structure NBT) get their puzzle here — edits on
        // unstamped papers are rejected outright by the network handler.
        if (stampPuzzleIfNeeded()) {
            setChanged();
        }
    }

    /** The aspect currently painted at {@code cell}, or null. */
    public ResourceLocation aspectAt(int cell) {
        return paper.getOrDefault(ModComponents.RESEARCH_SPHERE.get(), ResearchSphereData.EMPTY)
                .cells().get(cell);
    }

    /** Paints (aspect present) or clears (empty) a cell on the held paper's sphere. */
    public void editSphere(int cell, Optional<ResourceLocation> aspect) {
        if (!canEditSphere()) {
            return;
        }
        ResearchSphereData data = paper.getOrDefault(ModComponents.RESEARCH_SPHERE.get(), ResearchSphereData.EMPTY);
        ResearchSphereData next = aspect.map(a -> data.with(cell, a)).orElseGet(() -> data.without(cell));
        paper.set(ModComponents.RESEARCH_SPHERE.get(), next);
        // Full BE broadcast per edit: the in-world hologram renders from this block
        // entity on every nearby client, not from menu slot sync.
        sync();
    }

    private void sync() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    // --- Container (one paper slot) -------------------------------------------

    @Override
    public int getContainerSize() {
        return 1;
    }

    @Override
    public boolean isEmpty() {
        return paper.isEmpty();
    }

    @Override
    public ItemStack getItem(int slot) {
        return slot == 0 ? paper : ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        if (slot != 0 || paper.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack split = paper.split(amount);
        sync();
        return split;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        ItemStack removed = paper;
        paper = ItemStack.EMPTY;
        return removed;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (slot == 0) {
            setPaper(stack);
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this, player);
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        // Hopper-facing rule (the menu slot enforces its own): one research paper only.
        return slot == 0 && paper.isEmpty() && stack.getItem() instanceof ResearchPaperItem;
    }

    @Override
    public boolean canTakeItem(Container target, int slot, ItemStack stack) {
        // Research papers carry the player's work; automation may not silently
        // extract them — retrieval stays a deliberate act at the menu.
        return false;
    }

    @Override
    public void clearContent() {
        setPaper(ItemStack.EMPTY);
    }

    // --- persistence -----------------------------------------------------------

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!paper.isEmpty()) {
            tag.put("Paper", paper.save(registries));
        }
        tag.putFloat("RotX", orientation.x);
        tag.putFloat("RotY", orientation.y);
        tag.putFloat("RotZ", orientation.z);
        tag.putFloat("RotW", orientation.w);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        paper = tag.contains("Paper")
                ? ItemStack.parse(registries, tag.getCompound("Paper")).orElse(ItemStack.EMPTY)
                : ItemStack.EMPTY;
        if (tag.contains("RotW")) {
            float x = tag.getFloat("RotX");
            float y = tag.getFloat("RotY");
            float z = tag.getFloat("RotZ");
            float w = tag.getFloat("RotW");
            // Hand-edited/corrupt NBT must not produce a degenerate rotation.
            if (Float.isFinite(x) && Float.isFinite(y) && Float.isFinite(z) && Float.isFinite(w)
                    && x * x + y * y + z * z + w * w > 1.0e-6f) {
                orientation.set(x, y, z, w).normalize();
            } else {
                orientation.set(DEFAULT_ORIENTATION);
            }
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, registries);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
