package io.github.minerguy341.new_age_thaum.network;

import io.github.minerguy341.new_age_thaum.NewAgeThaum;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * C2S: the viewing player rotated the sphere in the orrery screen. Quaternion as four
 * raw floats; the server validates (finite, non-degenerate) and normalizes before
 * storing, then mirrors to other nearby players via {@link OrreryOrientationPayload}.
 */
public record OrreryRotatePayload(BlockPos pos, float x, float y, float z, float w)
        implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<OrreryRotatePayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(NewAgeThaum.MOD_ID, "orrery_rotate"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OrreryRotatePayload> STREAM_CODEC =
            StreamCodec.of(OrreryRotatePayload::write, OrreryRotatePayload::read);

    private static void write(RegistryFriendlyByteBuf buf, OrreryRotatePayload payload) {
        buf.writeBlockPos(payload.pos);
        buf.writeFloat(payload.x);
        buf.writeFloat(payload.y);
        buf.writeFloat(payload.z);
        buf.writeFloat(payload.w);
    }

    private static OrreryRotatePayload read(RegistryFriendlyByteBuf buf) {
        return new OrreryRotatePayload(buf.readBlockPos(),
                buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat());
    }

    @Override
    public CustomPacketPayload.Type<OrreryRotatePayload> type() {
        return TYPE;
    }
}
