package io.github.minerguy341.new_age_thaum.core.aura;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.LongPredicate;

/**
 * The ambient aura field (PLAN.md §4.3, TC6 half of the hybrid): per-chunk <b>vis</b> and
 * <b>flux</b> in a dimension-scoped SavedData map keyed by ChunkPos — the simplest fully
 * cross-loader store, no attachment APIs involved. Aura nodes (the TC4 half) are the
 * sources: they pump vis into their own and neighboring chunks each server tick cycle,
 * and — depending on personality — raise flux (tainted nodes pollute) or lower it (pure
 * nodes cleanse). {@link #diffuse} bleeds both outward on a shared budget so the field
 * smooths toward the nodes' surroundings. Chunks the map has never touched read 0 for
 * both. Flux is pollution: it spreads like vis but no node is a passive sink, so it
 * lingers until a pure node (or future countermeasure) burns it down.
 */
public final class AuraField extends SavedData {
    /** Uniform per-chunk vis cap for the prototype; biome-scaled caps are a follow-up. */
    public static final float CHUNK_CAP = 100f;
    /** Per-chunk flux cap. High sustained flux is where taint will later seed (TODO). */
    public static final float FLUX_CAP = 100f;
    /** Fraction of a vis/flux difference that flows per diffusion pass. */
    private static final float DIFFUSION_RATE = 0.05f;
    /** Differences below this don't flow — stops endless micro-oscillation. */
    private static final float DIFFUSION_EPSILON = 0.05f;
    /** Budget: chunks processed per diffusion pass (PLAN §5 budget-ticker discipline). */
    private static final int DIFFUSION_BUDGET = 256;

    private static final SavedData.Factory<AuraField> FACTORY =
            new SavedData.Factory<>(AuraField::new, AuraField::load, null);

    private final Map<Long, Float> vis = new HashMap<>();
    private final Map<Long, Float> flux = new HashMap<>();
    // Round-robin cursor so a budget-limited pass eventually reaches every chunk.
    private int diffusionCursor;

    public static AuraField get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, "new_age_thaum_aura");
    }

    public float vis(ChunkPos pos) {
        return vis(pos.toLong());
    }

    public float vis(long chunk) {
        return vis.getOrDefault(chunk, 0f);
    }

    public float flux(ChunkPos pos) {
        return flux(pos.toLong());
    }

    public float flux(long chunk) {
        return flux.getOrDefault(chunk, 0f);
    }

    /** Adds (or drains, negative) vis, clamped to [0, cap]. Returns the new value. */
    public float add(long chunk, float amount) {
        return change(vis, chunk, amount, CHUNK_CAP);
    }

    /** Adds (or removes, negative) flux, clamped to [0, cap]. Returns the new value. */
    public float addFlux(long chunk, float amount) {
        return change(flux, chunk, amount, FLUX_CAP);
    }

    private float change(Map<Long, Float> field, long chunk, float amount, float cap) {
        float next = Mth.clamp(field.getOrDefault(chunk, 0f) + amount, 0f, cap);
        if (next == 0f) {
            field.remove(chunk); // untouched chunks stay off the map (and off the disk)
        } else {
            field.put(chunk, next);
        }
        setDirty();
        return next;
    }

    /**
     * One budgeted diffusion pass over both vis and flux: each processed chunk bleeds a
     * fraction of its positive difference into its four neighbors. Conserving up to the
     * cap (what leaves one chunk arrives in the next; overflow at a capped chunk
     * dissipates), so nodes remain the only sources.
     */
    public void diffuse() {
        diffuse(chunk -> true);
    }

    /**
     * As {@link #diffuse()}, but only chunks passing {@code scope} act as SOURCES.
     * Excluded chunks still receive inflow from included neighbors (a map write, not a
     * chunk access), so a loaded-only scope doesn't build walls at the frontier. Skipped
     * chunks still consume budget slots — the pass stays bounded either way.
     */
    public void diffuse(LongPredicate scope) {
        if (vis.isEmpty() && flux.isEmpty()) {
            return;
        }
        // Union of both fields' keys, vis order first (keeps vis-only behavior identical
        // to before flux existed), flux-only chunks appended.
        Set<Long> union = new LinkedHashSet<>(vis.keySet());
        union.addAll(flux.keySet());
        List<Long> chunks = new ArrayList<>(union);
        int count = Math.min(chunks.size(), DIFFUSION_BUDGET);
        diffuseField(vis, CHUNK_CAP, chunks, count, scope);
        diffuseField(flux, FLUX_CAP, chunks, count, scope);
        diffusionCursor = chunks.isEmpty() ? 0 : (diffusionCursor + count) % chunks.size();
        setDirty();
    }

    private void diffuseField(Map<Long, Float> field, float cap, List<Long> chunks, int count,
            LongPredicate scope) {
        int size = chunks.size();
        for (int i = 0; i < count; i++) {
            long chunk = chunks.get((diffusionCursor + i) % size);
            if (!scope.test(chunk)) {
                continue;
            }
            float here = field.getOrDefault(chunk, 0f);
            ChunkPos pos = new ChunkPos(chunk);
            for (ChunkPos neighbor : new ChunkPos[]{
                    new ChunkPos(pos.x + 1, pos.z), new ChunkPos(pos.x - 1, pos.z),
                    new ChunkPos(pos.x, pos.z + 1), new ChunkPos(pos.x, pos.z - 1)}) {
                float there = field.getOrDefault(neighbor.toLong(), 0f);
                float flow = (here - there) * DIFFUSION_RATE;
                if (flow > DIFFUSION_EPSILON) {
                    change(field, chunk, -flow, cap);
                    change(field, neighbor.toLong(), flow, cap);
                    here -= flow;
                }
            }
        }
    }

    /** Public so gametests can round-trip the save format. */
    public static AuraField load(CompoundTag tag, HolderLookup.Provider registries) {
        AuraField field = new AuraField();
        for (Tag entry : tag.getList("Chunks", Tag.TAG_COMPOUND)) {
            CompoundTag chunk = (CompoundTag) entry;
            long pos = chunk.getLong("Pos");
            float visValue = chunk.getFloat("Vis");
            if (Float.isFinite(visValue) && visValue > 0f) {
                field.vis.put(pos, Mth.clamp(visValue, 0f, CHUNK_CAP));
            }
            // Flux is optional: pre-flux saves simply have no key and load as 0.
            float fluxValue = chunk.getFloat("Flux");
            if (Float.isFinite(fluxValue) && fluxValue > 0f) {
                field.flux.put(pos, Mth.clamp(fluxValue, 0f, FLUX_CAP));
            }
        }
        return field;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag chunks = new ListTag();
        Set<Long> keys = new LinkedHashSet<>(vis.keySet());
        keys.addAll(flux.keySet());
        for (long key : keys) {
            CompoundTag chunk = new CompoundTag();
            chunk.putLong("Pos", key);
            float visValue = vis.getOrDefault(key, 0f);
            float fluxValue = flux.getOrDefault(key, 0f);
            if (visValue > 0f) {
                chunk.putFloat("Vis", visValue);
            }
            if (fluxValue > 0f) {
                chunk.putFloat("Flux", fluxValue);
            }
            chunks.add(chunk);
        }
        tag.put("Chunks", chunks);
        return tag;
    }
}
