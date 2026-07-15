package io.github.minerguy341.new_age_thaum.core.player;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.minerguy341.new_age_thaum.core.aspect.AspectBag;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Per-player scanning progress: which object kinds have been scanned, and the
 * observation points accrued per aspect. Immutable; mutations return a new copy
 * (the platform bridge stores the replacement). Serialized by the loader's
 * attachment system and synced to the owning client.
 */
public record PlayerProgress(Set<String> scanned, Map<ResourceLocation, Integer> points) {
    public static final PlayerProgress EMPTY = new PlayerProgress(Set.of(), Map.of());

    private static final Codec<Set<String>> SCANNED_CODEC =
            Codec.STRING.listOf().xmap(HashSet::new, ArrayList::new);

    public static final Codec<PlayerProgress> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            SCANNED_CODEC.fieldOf("scanned").forGetter(p -> new HashSet<>(p.scanned())),
            Codec.unboundedMap(ResourceLocation.CODEC, Codec.INT).fieldOf("points").forGetter(PlayerProgress::points)
    ).apply(instance, PlayerProgress::new));

    public PlayerProgress {
        scanned = Set.copyOf(scanned);
        points = Map.copyOf(points);
    }

    public boolean hasScanned(String key) {
        return scanned.contains(key);
    }

    public int points(ResourceLocation aspect) {
        return points.getOrDefault(aspect, 0);
    }

    /** Marks {@code key} scanned and adds {@code gained} observation points. */
    public PlayerProgress withScan(String key, AspectBag gained) {
        Set<String> newScanned = new HashSet<>(scanned);
        newScanned.add(key);
        Map<ResourceLocation, Integer> newPoints = new HashMap<>(points);
        gained.amounts().forEach((aspect, amount) -> newPoints.merge(aspect, amount, Integer::sum));
        return new PlayerProgress(newScanned, newPoints);
    }
}
