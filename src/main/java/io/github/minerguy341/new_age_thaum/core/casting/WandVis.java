package io.github.minerguy341.new_age_thaum.core.casting;

import com.mojang.serialization.Codec;
import io.github.minerguy341.new_age_thaum.network.NetworkLimits;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

/**
 * Per-primal vis stored on a wand or stave (TC4-style: the implement holds each of the
 * six primals separately, up to {@link WandStats#capacity} of each). A sibling
 * component to {@link WandComponent} rather than a new field on it, so already-shipped
 * wands keep loading unchanged. Immutable; absent/zero entries are dropped so an empty
 * store is exactly {@link #EMPTY}.
 */
public record WandVis(Map<ResourceLocation, Float> stored) {
    public static final WandVis EMPTY = new WandVis(Map.of());

    public WandVis {
        // Defensive normalization (also the codec's validation): drop non-finite and
        // non-positive entries so hand-edited NBT can't smuggle NaN into the math.
        Map<ResourceLocation, Float> clean = new HashMap<>();
        for (Map.Entry<ResourceLocation, Float> entry : stored.entrySet()) {
            float value = entry.getValue();
            if (Float.isFinite(value) && value > 0f) {
                clean.put(entry.getKey(), value);
            }
        }
        stored = Map.copyOf(clean);
    }

    public float get(ResourceLocation primal) {
        return stored.getOrDefault(primal, 0f);
    }

    /** Copy with one primal's amount replaced (dropped when zero or below). */
    public WandVis with(ResourceLocation primal, float amount) {
        Map<ResourceLocation, Float> next = new HashMap<>(stored);
        if (amount <= 0f) {
            next.remove(primal);
        } else {
            next.put(primal, amount);
        }
        return new WandVis(next);
    }

    public boolean isEmpty() {
        return stored.isEmpty();
    }

    // xmap is total: the compact constructor normalizes rather than throws.
    public static final Codec<WandVis> CODEC =
            Codec.unboundedMap(ResourceLocation.CODEC, Codec.FLOAT).xmap(WandVis::new, WandVis::stored);

    public static final StreamCodec<RegistryFriendlyByteBuf, WandVis> STREAM_CODEC =
            StreamCodec.of(WandVis::write, WandVis::read);

    private static void write(RegistryFriendlyByteBuf buf, WandVis value) {
        buf.writeVarInt(value.stored.size());
        for (Map.Entry<ResourceLocation, Float> entry : value.stored.entrySet()) {
            buf.writeResourceLocation(entry.getKey());
            buf.writeFloat(entry.getValue());
        }
    }

    private static WandVis read(RegistryFriendlyByteBuf buf) {
        int count = buf.readVarInt();
        Map<ResourceLocation, Float> map = new HashMap<>(NetworkLimits.safeCapacity(count));
        for (int i = 0; i < count; i++) {
            map.put(buf.readResourceLocation(), buf.readFloat());
        }
        return new WandVis(map);
    }
}
