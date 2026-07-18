package io.github.minerguy341.new_age_thaum.network;

import io.github.minerguy341.new_age_thaum.NewAgeThaum;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * S2C: an orrery's sphere orientation changed — nearby clients update their block
 * entity so the hologram mirrors the rotating player's drag live. The dragging player
 * is excluded (their client wrote through optimistically).
 */
public record OrreryOrientationPayload(BlockPos pos, float x, float y, float z, float w)
        implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<OrreryOrientationPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(NewAgeThaum.MOD_ID, "orrery_orientation"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OrreryOrientationPayload> STREAM_CODEC =
            StreamCodec.of(OrreryOrientationPayload::write, OrreryOrientationPayload::read);

    private static void write(RegistryFriendlyByteBuf buf, OrreryOrientationPayload payload) {
        buf.writeBlockPos(payload.pos);
        buf.writeFloat(payload.x);
        buf.writeFloat(payload.y);
        buf.writeFloat(payload.z);
        buf.writeFloat(payload.w);
    }

    private static OrreryOrientationPayload read(RegistryFriendlyByteBuf buf) {
        return new OrreryOrientationPayload(buf.readBlockPos(),
                buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat());
    }

    @Override
    public CustomPacketPayload.Type<OrreryOrientationPayload> type() {
        return TYPE;
    }
}
