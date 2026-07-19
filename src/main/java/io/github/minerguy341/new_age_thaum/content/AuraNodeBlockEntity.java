package io.github.minerguy341.new_age_thaum.content;

import io.github.minerguy341.new_age_thaum.core.ModBlockEntities;
import io.github.minerguy341.new_age_thaum.core.aspect.Aspect;
import io.github.minerguy341.new_age_thaum.core.aspect.AspectRegistry;
import io.github.minerguy341.new_age_thaum.core.aura.AuraField;
import io.github.minerguy341.new_age_thaum.core.aura.NodePersonality;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * An aura node (PLAN.md §4.3, the TC4 half of the hybrid): a floating wellspring with
 * an aspect identity that regenerates the ambient aura around it — full rate into its
 * own chunk, quarter rate into the eight neighbors, all through {@link AuraField}.
 * The renderer draws the orb from {@code aspect}/{@code size}, and the aura-visualizer
 * hologram reads {@code auraSnapshot}: a 5x5 grid of surrounding chunk vis the server
 * refreshes to nearby clients every few seconds.
 */
public class AuraNodeBlockEntity extends BlockEntity {
    /** Snapshot grid edge (5x5 chunks centered on the node). */
    public static final int GRID = 5;
    private static final int PUMP_INTERVAL_TICKS = 20;
    private static final int SYNC_INTERVAL_TICKS = 60;
    private static final double SYNC_PLAYER_RANGE = 48.0;

    private ResourceLocation aspect;
    private float size = 1.0f; // recharge rate in vis per pump, also scales the orb
    private NodePersonality personality; // temperament; null until rolled (or migrated)
    private final float[] auraSnapshot = new float[GRID * GRID];

    public AuraNodeBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.AURA_NODE.get(), pos, state);
    }

    public ResourceLocation aspect() {
        return aspect;
    }

    public float size() {
        return size;
    }

    /** The node's temperament; never null after its first server tick. */
    public NodePersonality personality() {
        return personality;
    }

    /**
     * Pre-seeds this node's identity so its first tick won't roll a random one — used by
     * worldgen to plant a specific node (e.g. a pure, aether-leaning node in a silverwood
     * trunk). Sets aspect, personality, and size directly; the tick guards then skip.
     */
    public void seedIdentity(ResourceLocation aspect, NodePersonality personality, float size) {
        this.aspect = aspect;
        this.personality = personality;
        this.size = Mth.clamp(size, 0.1f, 4f);
        setChanged();
    }

    /** Row-major 5x5 vis snapshot centered on this node's chunk; client display data. */
    public float[] auraSnapshot() {
        return auraSnapshot;
    }

    public void serverTick(ServerLevel level) {
        long time = level.getGameTime();
        if (aspect == null) {
            // Worldgen and creative placement both land here: roll the node's identity
            // once, from the live registry's primal aspects.
            List<Aspect> primals = new ArrayList<>();
            for (Aspect candidate : AspectRegistry.all()) {
                if (candidate.isPrimal()) {
                    primals.add(candidate);
                }
            }
            if (primals.isEmpty()) {
                return; // registry not loaded yet; try again next tick
            }
            aspect = primals.get(level.random.nextInt(primals.size())).id();
            size = 0.6f + level.random.nextFloat() * 0.9f;
            setChanged();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
        if (personality == null) {
            // Fresh nodes roll here; nodes saved before personalities existed migrate the
            // first time they tick after the update (aspect is already set, so only this
            // runs). A worldgen feature that pre-seeds a personality skips both.
            personality = NodePersonality.roll(level.random);
            setChanged();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
        if (time % PUMP_INTERVAL_TICKS == 0) {
            pump(level);
        }
        // Refresh the visualizer snapshot for nearby clients; skip when nobody is close
        // enough to see the hologram.
        if (time % SYNC_INTERVAL_TICKS == 0 && level.getNearestPlayer(
                worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), SYNC_PLAYER_RANGE, false) != null) {
            AuraField aura = AuraField.get(level);
            ChunkPos center = new ChunkPos(worldPosition);
            int half = GRID / 2;
            for (int dz = -half; dz <= half; dz++) {
                for (int dx = -half; dx <= half; dx++) {
                    auraSnapshot[(dz + half) * GRID + (dx + half)] =
                            aura.vis(new ChunkPos(center.x + dx, center.z + dz).toLong());
                }
            }
            setChanged();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    /**
     * One regeneration pulse: full rate into the node's chunk, quarter rate into the
     * eight neighbors, both scaled by the personality's vis multiplier. Tainted nodes
     * also add flux to their chunk; pure nodes burn it down. Public so gametests drive
     * the exact production path.
     */
    public void pump(ServerLevel level) {
        AuraField aura = AuraField.get(level);
        ChunkPos center = new ChunkPos(worldPosition);
        NodePersonality nature = personality != null ? personality : NodePersonality.PALE;
        float output = size * nature.visMultiplier();
        aura.add(center.toLong(), output);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx != 0 || dz != 0) {
                    aura.add(new ChunkPos(center.x + dx, center.z + dz).toLong(), output * 0.25f);
                }
            }
        }
        if (nature.fluxPerPump() != 0f) {
            aura.addFlux(center.toLong(), size * nature.fluxPerPump());
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (aspect != null) {
            tag.putString("Aspect", aspect.toString());
        }
        tag.putFloat("Size", size);
        if (personality != null) {
            tag.putString("Personality", personality.id());
        }
        // The snapshot rides along so getUpdateTag carries it; stale values on world
        // load are refreshed by the first sync interval.
        int[] snapshot = new int[auraSnapshot.length];
        for (int i = 0; i < auraSnapshot.length; i++) {
            snapshot[i] = Float.floatToIntBits(auraSnapshot[i]);
        }
        tag.putIntArray("Aura", snapshot);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        aspect = tag.contains("Aspect") ? ResourceLocation.tryParse(tag.getString("Aspect")) : null;
        float loadedSize = tag.getFloat("Size");
        size = Float.isFinite(loadedSize) && loadedSize > 0f ? Mth.clamp(loadedSize, 0.1f, 4f) : 1.0f;
        // Absent on pre-personality saves — left null so the first tick migrates it.
        personality = tag.contains("Personality") ? NodePersonality.byId(tag.getString("Personality")) : null;
        int[] snapshot = tag.getIntArray("Aura");
        for (int i = 0; i < auraSnapshot.length; i++) {
            float value = i < snapshot.length ? Float.intBitsToFloat(snapshot[i]) : 0f;
            auraSnapshot[i] = Float.isFinite(value) ? Mth.clamp(value, 0f, AuraField.CHUNK_CAP) : 0f;
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
