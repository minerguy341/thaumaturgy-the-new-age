package io.github.minerguy341.new_age_thaum.network;

import io.github.minerguy341.new_age_thaum.NewAgeThaum;
import io.github.minerguy341.new_age_thaum.core.casting.WandMaterial;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Full wand-material sync, sent on player join and after datapack reloads. */
public record WandMaterialSyncPayload(List<WandMaterial> materials) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<WandMaterialSyncPayload> TYPE =
            new CustomPacketPayload.Type<>(NewAgeThaum.id("wand_material_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, WandMaterialSyncPayload> STREAM_CODEC =
            StreamCodec.of(WandMaterialSyncPayload::write, WandMaterialSyncPayload::read);

    private static void write(RegistryFriendlyByteBuf buf, WandMaterialSyncPayload payload) {
        buf.writeVarInt(payload.materials.size());
        for (WandMaterial material : payload.materials) {
            buf.writeResourceLocation(material.id());
            buf.writeEnum(material.kind());
            buf.writeInt(material.color());
            buf.writeDouble(material.capacity());
            buf.writeDouble(material.discount());
            buf.writeDouble(material.potency());
            buf.writeOptional(material.rechargeAffinity(), FriendlyByteBuf::writeResourceLocation);
        }
    }

    private static WandMaterialSyncPayload read(RegistryFriendlyByteBuf buf) {
        int count = buf.readVarInt();
        List<WandMaterial> materials = new ArrayList<>(NetworkLimits.safeCapacity(count));
        for (int i = 0; i < count; i++) {
            ResourceLocation id = buf.readResourceLocation();
            WandMaterial.Kind kind = buf.readEnum(WandMaterial.Kind.class);
            int color = buf.readInt();
            double capacity = buf.readDouble();
            double discount = buf.readDouble();
            double potency = buf.readDouble();
            Optional<ResourceLocation> affinity = buf.readOptional(FriendlyByteBuf::readResourceLocation);
            materials.add(new WandMaterial(id, kind, color, capacity, discount, potency, affinity));
        }
        return new WandMaterialSyncPayload(materials);
    }

    @Override
    public CustomPacketPayload.Type<WandMaterialSyncPayload> type() {
        return TYPE;
    }
}
