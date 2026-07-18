package io.github.minerguy341.new_age_thaum.network;

import io.github.minerguy341.new_age_thaum.NewAgeThaum;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * C2S: the viewing player rotated the sphere in the orrery screen. The server validates
 * the {@link OrientationFrame}, stores the analytic rest pose, and mirrors the same
 * frame to other nearby players via {@link OrreryOrientationPayload}.
 */
public record OrreryRotatePayload(BlockPos pos, OrientationFrame frame) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<OrreryRotatePayload> TYPE =
            new CustomPacketPayload.Type<>(NewAgeThaum.id("orrery_rotate"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OrreryRotatePayload> STREAM_CODEC =
            StreamCodec.of(OrreryRotatePayload::write, OrreryRotatePayload::read);

    private static void write(RegistryFriendlyByteBuf buf, OrreryRotatePayload payload) {
        buf.writeBlockPos(payload.pos);
        OrientationFrame.write(buf, payload.frame);
    }

    private static OrreryRotatePayload read(RegistryFriendlyByteBuf buf) {
        return new OrreryRotatePayload(buf.readBlockPos(), OrientationFrame.read(buf));
    }

    @Override
    public CustomPacketPayload.Type<OrreryRotatePayload> type() {
        return TYPE;
    }
}
