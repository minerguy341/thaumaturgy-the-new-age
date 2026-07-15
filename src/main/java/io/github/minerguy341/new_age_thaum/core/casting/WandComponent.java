package io.github.minerguy341.new_age_thaum.core.casting;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

/**
 * The data component stamped on a wand or stave: which core rod and which two end caps
 * it was assembled from (all wand-material ids). Everything visible about the item — its
 * tinted appearance and its {@link WandStats} — is derived from these three ids.
 */
public record WandComponent(ResourceLocation core, ResourceLocation capA, ResourceLocation capB) {

    public static final Codec<WandComponent> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("core").forGetter(WandComponent::core),
            ResourceLocation.CODEC.fieldOf("cap_a").forGetter(WandComponent::capA),
            ResourceLocation.CODEC.fieldOf("cap_b").forGetter(WandComponent::capB)
    ).apply(instance, WandComponent::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, WandComponent> STREAM_CODEC = StreamCodec.composite(
            ResourceLocation.STREAM_CODEC, WandComponent::core,
            ResourceLocation.STREAM_CODEC, WandComponent::capA,
            ResourceLocation.STREAM_CODEC, WandComponent::capB,
            WandComponent::new);
}
