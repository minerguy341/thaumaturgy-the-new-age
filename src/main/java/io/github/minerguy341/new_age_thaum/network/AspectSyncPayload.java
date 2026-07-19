package io.github.minerguy341.new_age_thaum.network;

import io.github.minerguy341.new_age_thaum.NewAgeThaum;
import io.github.minerguy341.new_age_thaum.core.aspect.Aspect;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/** Full aspect-set sync, sent on player join and after datapack reloads. */
public record AspectSyncPayload(List<Aspect> aspects) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<AspectSyncPayload> TYPE =
            new CustomPacketPayload.Type<>(NewAgeThaum.id("aspect_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AspectSyncPayload> STREAM_CODEC =
            StreamCodec.of(AspectSyncPayload::write, AspectSyncPayload::read);

    private static void write(RegistryFriendlyByteBuf buf, AspectSyncPayload payload) {
        buf.writeVarInt(payload.aspects.size());
        for (Aspect aspect : payload.aspects) {
            buf.writeResourceLocation(aspect.id());
            buf.writeInt(aspect.color());
            buf.writeVarInt(aspect.components().size());
            for (ResourceLocation component : aspect.components()) {
                buf.writeResourceLocation(component);
            }
        }
    }

    private static AspectSyncPayload read(RegistryFriendlyByteBuf buf) {
        int count = buf.readVarInt();
        List<Aspect> aspects = new ArrayList<>(NetworkLimits.safeCapacity(count));
        for (int i = 0; i < count; i++) {
            ResourceLocation id = buf.readResourceLocation();
            int color = buf.readInt();
            int componentCount = buf.readVarInt();
            List<ResourceLocation> components = new ArrayList<>(NetworkLimits.safeCapacity(componentCount));
            for (int j = 0; j < componentCount; j++) {
                components.add(buf.readResourceLocation());
            }
            aspects.add(new Aspect(id, color, List.copyOf(components)));
        }
        return new AspectSyncPayload(aspects);
    }

    @Override
    public CustomPacketPayload.Type<AspectSyncPayload> type() {
        return TYPE;
    }
}
