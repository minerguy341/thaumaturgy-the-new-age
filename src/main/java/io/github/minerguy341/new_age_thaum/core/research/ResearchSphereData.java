package io.github.minerguy341.new_age_thaum.core.research;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

/**
 * The aspects painted onto one research paper's sphere (cell index → aspect id), stored
 * as a data component on the paper ItemStack — the research travels with the paper, not
 * the orrery it was worked on. Immutable; edits produce a new value so component change
 * detection (and menu slot sync) sees every mutation.
 */
public record ResearchSphereData(Map<Integer, ResourceLocation> cells) {
    public static final ResearchSphereData EMPTY = new ResearchSphereData(Map.of());

    /**
     * NBT/JSON map keys must be strings, so cell indices round-trip through String.
     * comapFlatMap, not xmap: a thrown NumberFormatException would escape the codec and
     * crash item/block-entity deserialization (a chunk-load crash loop) on corrupt data.
     */
    public static final Codec<Integer> CELL_INDEX = Codec.STRING.comapFlatMap(s -> {
        try {
            return DataResult.success(Integer.parseInt(s));
        } catch (NumberFormatException e) {
            return DataResult.error(() -> "Cell index is not an integer: '" + s + "'");
        }
    }, String::valueOf);

    public static final Codec<ResearchSphereData> CODEC = Codec
            .unboundedMap(CELL_INDEX, ResourceLocation.CODEC)
            .xmap(ResearchSphereData::new, ResearchSphereData::cells);

    public static final StreamCodec<RegistryFriendlyByteBuf, ResearchSphereData> STREAM_CODEC =
            StreamCodec.of(ResearchSphereData::write, ResearchSphereData::read);

    public ResearchSphereData {
        cells = Map.copyOf(cells);
    }

    public ResearchSphereData with(int cell, ResourceLocation aspect) {
        Map<Integer, ResourceLocation> next = new HashMap<>(cells);
        next.put(cell, aspect);
        return new ResearchSphereData(next);
    }

    public ResearchSphereData without(int cell) {
        if (!cells.containsKey(cell)) {
            return this;
        }
        Map<Integer, ResourceLocation> next = new HashMap<>(cells);
        next.remove(cell);
        return new ResearchSphereData(next);
    }

    private static void write(RegistryFriendlyByteBuf buf, ResearchSphereData data) {
        buf.writeVarInt(data.cells.size());
        data.cells.forEach((cell, aspect) -> {
            buf.writeVarInt(cell);
            buf.writeResourceLocation(aspect);
        });
    }

    /** Largest sphere: 10 * 8² + 2 cells at the puzzle codec's maximum frequency of 8. */
    private static final int MAX_CELLS = 10 * 8 * 8 + 2;

    private static ResearchSphereData read(RegistryFriendlyByteBuf buf) {
        // Capped allocation + bounded cell indices: see ResearchPuzzle.read.
        int count = buf.readVarInt();
        Map<Integer, ResourceLocation> cells = new HashMap<>(
                io.github.minerguy341.new_age_thaum.network.NetworkLimits.safeCapacity(count));
        for (int i = 0; i < count; i++) {
            int cell = buf.readVarInt();
            ResourceLocation aspect = buf.readResourceLocation();
            if (cell >= 0 && cell < MAX_CELLS) {
                cells.put(cell, aspect);
            }
        }
        return new ResearchSphereData(cells);
    }
}
