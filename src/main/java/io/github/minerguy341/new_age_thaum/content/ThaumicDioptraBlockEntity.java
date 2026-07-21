package io.github.minerguy341.new_age_thaum.content;

import io.github.minerguy341.new_age_thaum.core.ModBlockEntities;
import io.github.minerguy341.new_age_thaum.core.aura.AuraField;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/**
 * The Thaumic Dioptra's state: which chunk window this block displays (assigned by
 * {@link DioptraGroup}) and a 13x13 vis snapshot of that window the server refreshes to
 * nearby clients — 169 {@link AuraField} map lookups, so reading vis never touches or
 * loads a chunk. Also caches its own chunk's vis as a 0–15 comparator signal.
 * Sync is the vanilla block-entity update-tag path (the house idiom): the dioptra sits
 * in a player-loaded chunk whenever anyone can see it, so no custom payload is needed.
 */
public class ThaumicDioptraBlockEntity extends BlockEntity {
    /** Snapshot grid edge, one cell per chunk (13x13 centered on the window center). */
    public static final int GRID = DioptraGroup.WINDOW;
    /**
     * Every dioptra block entity currently loaded on the CLIENT. The hologram is drawn
     * from a per-frame hook (see {@code ThaumicDioptraRenderer}) instead of block-entity
     * renderer dispatch, so it must not depend on any anchor block's chunk section being
     * in the camera frustum — the whole point is that a group's map keeps drawing from
     * any angle. This set is that hook's source of truth. The {@code isClientSide} guard
     * below means the server never adds to it, so it stays empty and touches no client
     * code; keeping it here (common) rather than in the client class keeps this block
     * entity free of any client-only import.
     */
    private static final Set<ThaumicDioptraBlockEntity> CLIENT_LOADED =
            Collections.synchronizedSet(Collections.newSetFromMap(new IdentityHashMap<>()));
    private static final int HALF = DioptraGroup.HALF;
    private static final int COMPARATOR_INTERVAL_TICKS = 20;
    private static final int SYNC_INTERVAL_TICKS = 60;
    private static final double SYNC_PLAYER_RANGE = 48.0;
    /** Sanity bound on a loaded window center's offset from the block's own chunk. */
    private static final int MAX_CENTER_OFFSET = 512;

    private int windowCenterX;
    private int windowCenterZ;
    private final float[] visWindow = new float[GRID * GRID];
    private final float[] fluxWindow = new float[GRID * GRID];
    private int comparatorSignal;
    // Fresh placement (no NBT load) triggers one group recompute on the first tick —
    // onPlace fires before the block entity exists, so placement can't hook it directly.
    private boolean pendingGroupRefresh = true;

    public ThaumicDioptraBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.THAUMIC_DIOPTRA.get(), pos, state);
        ChunkPos own = new ChunkPos(pos);
        windowCenterX = own.x;
        windowCenterZ = own.z;
    }

    /** A snapshot of every client-loaded dioptra, for the per-frame hologram hook. */
    public static List<ThaumicDioptraBlockEntity> loadedClientSide() {
        synchronized (CLIENT_LOADED) {
            return new ArrayList<>(CLIENT_LOADED);
        }
    }

    // Client load/unload = the render hook's registration. setLevel fires when a chunk
    // brings the block entity into a level (both sides); the guard keeps the server out.
    @Override
    public void setLevel(Level level) {
        super.setLevel(level);
        if (level.isClientSide) {
            CLIENT_LOADED.add(this);
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        CLIENT_LOADED.remove(this); // no-op server-side; the block was never tracked there
    }

    /** Center chunk of the displayed window. */
    public ChunkPos windowCenter() {
        return new ChunkPos(windowCenterX, windowCenterZ);
    }

    /** Row-major 13x13 vis snapshot of the window; client display data. */
    public float[] visWindow() {
        return visWindow;
    }

    /** Row-major 13x13 flux snapshot of the window; stains the map where flux is high. */
    public float[] fluxWindow() {
        return fluxWindow;
    }

    /** Cached comparator signal, 0–15 proportional to the block's own chunk vis. */
    public int comparatorSignal() {
        return comparatorSignal;
    }

    public void serverTick(ServerLevel level) {
        if (pendingGroupRefresh) {
            pendingGroupRefresh = false;
            DioptraGroup.refresh(level, worldPosition);
            // Fill and push the snapshot immediately on the first tick after placement, so
            // a freshly placed dioptra shows its real terrain at once instead of a flat
            // patch until the periodic sync. A group join already fills via applyWindowCenter
            // (the center changes), but a lone block keeps its own-chunk default, so that
            // path never fires — this covers it. refreshSnapshot only syncs on a real change,
            // so it's a no-op when the group path already pushed the data.
            refreshSnapshot(level);
        }
        long time = level.getGameTime();
        if (time % COMPARATOR_INTERVAL_TICKS == 0) {
            refreshComparator(level);
        }
        // Refresh the map for nearby clients; skip when nobody is close enough to see it.
        if (time % SYNC_INTERVAL_TICKS == 0 && level.getNearestPlayer(
                worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), SYNC_PLAYER_RANGE, false) != null) {
            refreshSnapshot(level);
        }
    }

    /**
     * Adopts a window center computed by {@link DioptraGroup}. On change the snapshot
     * refills and syncs unconditionally, so the map is correct the moment a group grows
     * or splits. Public so gametests drive the exact production path.
     */
    public void applyWindowCenter(ServerLevel level, int centerX, int centerZ) {
        if (windowCenterX == centerX && windowCenterZ == centerZ) {
            return;
        }
        windowCenterX = centerX;
        windowCenterZ = centerZ;
        fillSnapshot(level);
        setChanged();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    /** Refills the snapshot from the aura field; syncs only if a cell changed. */
    public void refreshSnapshot(ServerLevel level) {
        if (fillSnapshot(level)) {
            setChanged();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    private boolean fillSnapshot(ServerLevel level) {
        AuraField aura = AuraField.get(level);
        boolean changed = false;
        for (int dz = -HALF; dz <= HALF; dz++) {
            for (int dx = -HALF; dx <= HALF; dx++) {
                long chunk = new ChunkPos(windowCenterX + dx, windowCenterZ + dz).toLong();
                int index = (dz + HALF) * GRID + (dx + HALF);
                float value = aura.vis(chunk);
                if (visWindow[index] != value) {
                    visWindow[index] = value;
                    changed = true;
                }
                float fluxValue = aura.flux(chunk);
                if (fluxWindow[index] != fluxValue) {
                    fluxWindow[index] = fluxValue;
                    changed = true;
                }
            }
        }
        return changed;
    }

    /** Recomputes the comparator cache from own-chunk vis. Public for gametests. */
    public void refreshComparator(ServerLevel level) {
        float vis = AuraField.get(level).vis(new ChunkPos(worldPosition).toLong());
        int signal = vis <= 0f ? 0 : Mth.clamp(1 + Mth.floor(vis / AuraField.CHUNK_CAP * 14f), 1, 15);
        if (signal != comparatorSignal) {
            comparatorSignal = signal;
            setChanged();
            level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("WindowX", windowCenterX);
        tag.putInt("WindowZ", windowCenterZ);
        // The snapshot rides along so getUpdateTag carries it; stale values on world
        // load are refreshed by the first sync interval.
        int[] snapshot = new int[visWindow.length];
        int[] fluxSnapshot = new int[fluxWindow.length];
        for (int i = 0; i < visWindow.length; i++) {
            snapshot[i] = Float.floatToIntBits(visWindow[i]);
            fluxSnapshot[i] = Float.floatToIntBits(fluxWindow[i]);
        }
        tag.putIntArray("Vis", snapshot);
        tag.putIntArray("Flux", fluxSnapshot);
        tag.putInt("Signal", comparatorSignal);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        pendingGroupRefresh = false; // a saved dioptra's group is already computed
        ChunkPos own = new ChunkPos(worldPosition);
        int loadedX = tag.contains("WindowX") ? tag.getInt("WindowX") : own.x;
        int loadedZ = tag.contains("WindowZ") ? tag.getInt("WindowZ") : own.z;
        // Defensive: a hand-edited/corrupt center far from the block falls back home.
        boolean sane = Math.abs(loadedX - own.x) <= MAX_CENTER_OFFSET
                && Math.abs(loadedZ - own.z) <= MAX_CENTER_OFFSET;
        windowCenterX = sane ? loadedX : own.x;
        windowCenterZ = sane ? loadedZ : own.z;
        int[] snapshot = tag.getIntArray("Vis");
        int[] fluxSnapshot = tag.getIntArray("Flux");
        for (int i = 0; i < visWindow.length; i++) {
            float value = i < snapshot.length ? Float.intBitsToFloat(snapshot[i]) : 0f;
            visWindow[i] = Float.isFinite(value) ? Mth.clamp(value, 0f, AuraField.CHUNK_CAP) : 0f;
            float fluxValue = i < fluxSnapshot.length ? Float.intBitsToFloat(fluxSnapshot[i]) : 0f;
            fluxWindow[i] = Float.isFinite(fluxValue) ? Mth.clamp(fluxValue, 0f, AuraField.FLUX_CAP) : 0f;
        }
        comparatorSignal = Mth.clamp(tag.getInt("Signal"), 0, 15);
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
