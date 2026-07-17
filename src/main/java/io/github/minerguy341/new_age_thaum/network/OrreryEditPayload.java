package io.github.minerguy341.new_age_thaum.network;

import io.github.minerguy341.new_age_thaum.NewAgeThaum;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;

/** C2S: paint (aspect present) or clear (aspect empty) a cell on the orrery's sphere. */
public record OrreryEditPayload(BlockPos pos, int cell, Optional<ResourceLocation> aspect) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<OrreryEditPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(NewAgeThaum.MOD_ID, "orrery_edit"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OrreryEditPayload> STREAM_CODEC =
            StreamCodec.of(OrreryEditPayload::write, OrreryEditPayload::read);

    private static void write(RegistryFriendlyByteBuf buf, OrreryEditPayload payload) {
        buf.writeBlockPos(payload.pos);
        buf.writeVarInt(payload.cell);
        buf.writeOptional(payload.aspect, FriendlyByteBuf::writeResourceLocation);
    }

    private static OrreryEditPayload read(RegistryFriendlyByteBuf buf) {
        return new OrreryEditPayload(buf.readBlockPos(), buf.readVarInt(),
                buf.readOptional(FriendlyByteBuf::readResourceLocation));
    }

    @Override
    public CustomPacketPayload.Type<OrreryEditPayload> type() {
        return TYPE;
    }
}
