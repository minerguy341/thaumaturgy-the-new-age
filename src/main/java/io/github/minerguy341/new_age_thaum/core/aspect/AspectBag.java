package io.github.minerguy341.new_age_thaum.core.aspect;

import com.mojang.serialization.Codec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
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

    public static final StreamCodec<RegistryFriendlyByteBuf, AspectBag> STREAM_CODEC = StreamCodec.of(
            (buf, bag) -> {
                buf.writeVarInt(bag.amounts.size());
                bag.amounts.forEach((id, amount) -> {
                    ResourceLocation.STREAM_CODEC.encode(buf, id);
                    buf.writeVarInt(amount);
                });
            },
            buf -> {
                int n = buf.readVarInt();
                Map<ResourceLocation, Integer> map = new HashMap<>();
                for (int i = 0; i < n; i++) {
                    ResourceLocation id = ResourceLocation.STREAM_CODEC.decode(buf);
                    map.put(id, buf.readVarInt());
                }
                return new AspectBag(Map.copyOf(map));
            });

    public boolean isEmpty() {
        return amounts.isEmpty();
    }

    /** Amount of one aspect, 0 if absent. */
    public int amountOf(ResourceLocation id) {
        return amounts.getOrDefault(id, 0);
    }

    /** A copy with {@code id} set to {@code amount} (amount &le; 0 removes it). */
    public AspectBag with(ResourceLocation id, int amount) {
        Map<ResourceLocation, Integer> map = new HashMap<>(amounts);
        if (amount <= 0) {
            map.remove(id);
        } else {
            map.put(id, amount);
        }
        return new AspectBag(Map.copyOf(map));
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
