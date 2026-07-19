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
import java.util.List;
import java.util.Map;
import java.util.function.LongPredicate;

/**
 * The ambient aura field (PLAN.md §4.3, TC6 half of the hybrid): per-chunk vis in a
 * dimension-scoped SavedData map keyed by ChunkPos — the simplest fully cross-loader
 * store, no attachment APIs involved. Aura nodes (the TC4 half) are the regeneration
 * sources: they pump vis into their own and neighboring chunks each server tick cycle,
 * and {@link #diffuse} bleeds it outward on a budget so the field smooths toward the
 * nodes' surroundings. Chunks the map has never touched read as vis 0.
 */
public final class AuraField extends SavedData {
    /** Uniform per-chunk cap for the prototype; biome-scaled caps are an M3 follow-up. */
    public static final float CHUNK_CAP = 100f;
    /** Fraction of a vis difference that flows per diffusion pass. */
    private static final float DIFFUSION_RATE = 0.05f;
    /** Differences below this don't flow — stops endless micro-oscillation. */
    private static final float DIFFUSION_EPSILON = 0.05f;
    /** Budget: chunks processed per diffusion pass (PLAN §5 budget-ticker discipline). */
    private static final int DIFFUSION_BUDGET = 256;

    private static final SavedData.Factory<AuraField> FACTORY =
            new SavedData.Factory<>(AuraField::new, AuraField::load, null);

    private final Map<Long, Float> vis = new HashMap<>();
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

    /** Adds (or drains, negative) vis, clamped to [0, cap]. Returns the new value. */
    public float add(long chunk, float amount) {
        float next = Mth.clamp(vis(chunk) + amount, 0f, CHUNK_CAP);
        if (next == 0f) {
            vis.remove(chunk); // untouched chunks stay off the map (and off the disk)
        } else {
            vis.put(chunk, next);
        }
        setDirty();
        return next;
    }

    /**
     * One budgeted diffusion pass: each processed chunk bleeds a fraction of its
     * positive difference into its four neighbors. Vis-conserving up to the cap (what
     * leaves one chunk arrives in the next; overflow at a capped chunk dissipates), so
     * nodes remain the only sources.
     */
    public void diffuse() {
        diffuse(chunk -> true);
    }

    /**
     * As {@link #diffuse()}, but only chunks passing {@code scope} act as SOURCES.
     * Excluded chunks still receive inflow from included neighbors (a map write, not a
     * chunk access), so a loaded-only scope doesn't build vis walls at the frontier.
     * Skipped chunks still consume budget slots — the pass stays bounded either way.
     */
    public void diffuse(LongPredicate scope) {
        if (vis.isEmpty()) {
            return;
        }
        List<Long> chunks = new ArrayList<>(vis.keySet());
        int count = Math.min(chunks.size(), DIFFUSION_BUDGET);
        for (int i = 0; i < count; i++) {
            long chunk = chunks.get((diffusionCursor + i) % chunks.size());
            if (!scope.test(chunk)) {
                continue;
            }
            float here = vis(chunk);
            ChunkPos pos = new ChunkPos(chunk);
            for (ChunkPos neighbor : new ChunkPos[]{
                    new ChunkPos(pos.x + 1, pos.z), new ChunkPos(pos.x - 1, pos.z),
                    new ChunkPos(pos.x, pos.z + 1), new ChunkPos(pos.x, pos.z - 1)}) {
                float there = vis(neighbor.toLong());
                float flow = (here - there) * DIFFUSION_RATE;
                if (flow > DIFFUSION_EPSILON) {
                    add(chunk, -flow);
                    add(neighbor.toLong(), flow);
                    here -= flow;
                }
            }
        }
        diffusionCursor = chunks.isEmpty() ? 0 : (diffusionCursor + count) % chunks.size();
        setDirty();
    }

    /** Public so gametests can round-trip the save format. */
    public static AuraField load(CompoundTag tag, HolderLookup.Provider registries) {
        AuraField field = new AuraField();
        for (Tag entry : tag.getList("Chunks", Tag.TAG_COMPOUND)) {
            CompoundTag chunk = (CompoundTag) entry;
            float value = chunk.getFloat("Vis");
            if (Float.isFinite(value) && value > 0f) {
                field.vis.put(chunk.getLong("Pos"), Mth.clamp(value, 0f, CHUNK_CAP));
            }
        }
        return field;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag chunks = new ListTag();
        for (Map.Entry<Long, Float> entry : vis.entrySet()) {
            CompoundTag chunk = new CompoundTag();
            chunk.putLong("Pos", entry.getKey());
            chunk.putFloat("Vis", entry.getValue());
            chunks.add(chunk);
        }
        tag.put("Chunks", chunks);
        return tag;
    }
}
