package io.github.minerguy341.new_age_thaum.core.research;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A generated puzzle definition, stamped onto a research paper as a data component the
 * first time it enters an orrery: the sphere size, the fixed endpoint aspects the player
 * must join, and the gap cells (voids that hold nothing). Player placements live in the
 * separate {@link ResearchSphereData} component. {@code solved} flips once — when the
 * server sees every endpoint joined in one web — and permanently seals the paper.
 * Immutable.
 */
public record ResearchPuzzle(int frequency, Map<Integer, ResourceLocation> endpoints, Set<Integer> gaps,
        boolean solved) {

    private static final Codec<Map<Integer, ResourceLocation>> ENDPOINT_CODEC = Codec
            .unboundedMap(Codec.STRING.xmap(Integer::parseInt, String::valueOf), ResourceLocation.CODEC);

    public static final Codec<ResearchPuzzle> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.intRange(1, 8).fieldOf("frequency").forGetter(ResearchPuzzle::frequency),
            ENDPOINT_CODEC.fieldOf("endpoints").forGetter(ResearchPuzzle::endpoints),
            Codec.INT.listOf().<Set<Integer>>xmap(HashSet::new, List::copyOf).fieldOf("gaps").forGetter(ResearchPuzzle::gaps),
            // optional: papers stamped before the solve mechanic existed load as unsolved
            Codec.BOOL.optionalFieldOf("solved", false).forGetter(ResearchPuzzle::solved)
    ).apply(instance, ResearchPuzzle::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, ResearchPuzzle> STREAM_CODEC =
            StreamCodec.of(ResearchPuzzle::write, ResearchPuzzle::read);

    public ResearchPuzzle {
        endpoints = Map.copyOf(endpoints);
        gaps = Set.copyOf(gaps);
    }

    public ResearchPuzzle(int frequency, Map<Integer, ResourceLocation> endpoints, Set<Integer> gaps) {
        this(frequency, endpoints, gaps, false);
    }

    public ResearchPuzzle asSolved() {
        return solved ? this : new ResearchPuzzle(frequency, endpoints, gaps, true);
    }

    public boolean isEndpoint(int cell) {
        return endpoints.containsKey(cell);
    }

    public boolean isGap(int cell) {
        return gaps.contains(cell);
    }

    private static void write(RegistryFriendlyByteBuf buf, ResearchPuzzle puzzle) {
        buf.writeVarInt(puzzle.frequency);
        buf.writeVarInt(puzzle.endpoints.size());
        puzzle.endpoints.forEach((cell, aspect) -> {
            buf.writeVarInt(cell);
            buf.writeResourceLocation(aspect);
        });
        buf.writeCollection(puzzle.gaps, FriendlyByteBuf::writeVarInt);
        buf.writeBoolean(puzzle.solved);
    }

    private static ResearchPuzzle read(RegistryFriendlyByteBuf buf) {
        int frequency = buf.readVarInt();
        int endpointCount = buf.readVarInt();
        Map<Integer, ResourceLocation> endpoints = new HashMap<>(endpointCount);
        for (int i = 0; i < endpointCount; i++) {
            int cell = buf.readVarInt();
            endpoints.put(cell, buf.readResourceLocation());
        }
        Set<Integer> gaps = buf.readCollection(HashSet::new, FriendlyByteBuf::readVarInt);
        boolean solved = buf.readBoolean();
        return new ResearchPuzzle(frequency, endpoints, gaps, solved);
    }
}
