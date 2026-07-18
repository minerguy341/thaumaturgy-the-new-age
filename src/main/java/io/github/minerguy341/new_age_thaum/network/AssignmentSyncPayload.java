package io.github.minerguy341.new_age_thaum.network;

import io.github.minerguy341.new_age_thaum.NewAgeThaum;
import io.github.minerguy341.new_age_thaum.core.aspect.AspectBag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

/** Item- and tag-keyed aspect assignments, synced on join and after datapack reloads. */
public record AssignmentSyncPayload(Map<ResourceLocation, AspectBag> byItem,
                                    Map<ResourceLocation, AspectBag> byTag) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<AssignmentSyncPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(NewAgeThaum.MOD_ID, "assignment_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AssignmentSyncPayload> STREAM_CODEC =
            StreamCodec.of(AssignmentSyncPayload::write, AssignmentSyncPayload::read);

    private static void write(RegistryFriendlyByteBuf buf, AssignmentSyncPayload payload) {
        writeMap(buf, payload.byItem);
        writeMap(buf, payload.byTag);
    }

    private static AssignmentSyncPayload read(RegistryFriendlyByteBuf buf) {
        return new AssignmentSyncPayload(readMap(buf), readMap(buf));
    }

    private static void writeMap(RegistryFriendlyByteBuf buf, Map<ResourceLocation, AspectBag> map) {
        buf.writeVarInt(map.size());
        map.forEach((key, bag) -> {
            buf.writeResourceLocation(key);
            buf.writeVarInt(bag.amounts().size());
            bag.amounts().forEach((aspect, amount) -> {
                buf.writeResourceLocation(aspect);
                buf.writeVarInt(amount);
            });
        });
    }

    private static Map<ResourceLocation, AspectBag> readMap(RegistryFriendlyByteBuf buf) {
        int count = buf.readVarInt();
        Map<ResourceLocation, AspectBag> map = new HashMap<>(NetworkLimits.safeCapacity(count));
        for (int i = 0; i < count; i++) {
            ResourceLocation key = buf.readResourceLocation();
            int aspectCount = buf.readVarInt();
            Map<ResourceLocation, Integer> amounts = new HashMap<>(NetworkLimits.safeCapacity(aspectCount));
            for (int j = 0; j < aspectCount; j++) {
                ResourceLocation aspect = buf.readResourceLocation();
                amounts.put(aspect, buf.readVarInt());
            }
            map.put(key, new AspectBag(Map.copyOf(amounts)));
        }
        return map;
    }

    @Override
    public CustomPacketPayload.Type<AssignmentSyncPayload> type() {
        return TYPE;
    }
}
