package io.github.minerguy341.new_age_thaum.core.aspect;

import com.mojang.serialization.Codec;
import net.minecraft.resources.ResourceLocation;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Immutable bag of aspect amounts. The empty bag doubles as an explicit opt-out. */
public record AspectBag(Map<ResourceLocation, Integer> amounts) {
    public static final AspectBag EMPTY = new AspectBag(Map.of());

    public static final Codec<AspectBag> CODEC = Codec
            .unboundedMap(ResourceLocation.CODEC, Codec.intRange(1, 65536))
            .xmap(map -> new AspectBag(Map.copyOf(map)), AspectBag::amounts);

    public boolean isEmpty() {
        return amounts.isEmpty();
    }

    /**
     * Entries in a stable display order — largest amount first, ties broken by aspect id.
     * The backing map is unordered, so tooltips and the scan HUD use this for consistency.
     */
    public List<Map.Entry<ResourceLocation, Integer>> ordered() {
        return amounts.entrySet().stream()
                .sorted(Comparator.<Map.Entry<ResourceLocation, Integer>>comparingInt(Map.Entry::getValue).reversed()
                        .thenComparing(entry -> entry.getKey().toString()))
                .toList();
    }

    public int total() {
        int sum = 0;
        for (int amount : amounts.values()) {
            sum += amount;
        }
        return sum;
    }

    public AspectBag add(AspectBag other) {
        if (other.isEmpty()) {
            return this;
        }
        if (isEmpty()) {
            return other;
        }
        Map<ResourceLocation, Integer> merged = new HashMap<>(amounts);
        other.amounts.forEach((id, amount) -> merged.merge(id, amount, Integer::sum));
        return new AspectBag(Map.copyOf(merged));
    }

    /** Per-aspect max of both bags; used when several tag assignments match one item. */
    public AspectBag max(AspectBag other) {
        Map<ResourceLocation, Integer> merged = new HashMap<>(amounts);
        other.amounts.forEach((id, amount) -> merged.merge(id, amount, Integer::max));
        return new AspectBag(Map.copyOf(merged));
    }

    /**
     * Recipe-inference dampening (m1 spec): {@code floor(amount * factor / divisor)},
     * clamped to at least 1 while the source aspect survives, capped per aspect.
     */
    public AspectBag dampen(double factor, int divisor, int cap) {
        Map<ResourceLocation, Integer> result = new HashMap<>();
        amounts.forEach((id, amount) -> {
            int dampened = (int) Math.floor(amount * factor / Math.max(1, divisor));
            if (dampened < 1 && amount > 0) {
                dampened = 1;
            }
            result.put(id, Math.min(dampened, cap));
        });
        return new AspectBag(Map.copyOf(result));
    }
}
