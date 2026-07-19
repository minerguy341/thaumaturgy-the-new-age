package io.github.minerguy341.new_age_thaum.network;

import io.github.minerguy341.new_age_thaum.NewAgeThaum;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * S2C: an orrery's sphere orientation changed — nearby clients apply the same
 * {@link OrientationFrame} (deterministic coast included) so every hologram converges
 * on the rest pose the server stored. The dragging player is excluded (their client
 * wrote through optimistically).
 */
public record OrreryOrientationPayload(BlockPos pos, OrientationFrame frame) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<OrreryOrientationPayload> TYPE =
            new CustomPacketPayload.Type<>(NewAgeThaum.id("orrery_orientation"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OrreryOrientationPayload> STREAM_CODEC =
            StreamCodec.of(OrreryOrientationPayload::write, OrreryOrientationPayload::read);

    private static void write(RegistryFriendlyByteBuf buf, OrreryOrientationPayload payload) {
        buf.writeBlockPos(payload.pos);
        OrientationFrame.write(buf, payload.frame);
    }

    private static OrreryOrientationPayload read(RegistryFriendlyByteBuf buf) {
        return new OrreryOrientationPayload(buf.readBlockPos(), OrientationFrame.read(buf));
    }

    @Override
    public CustomPacketPayload.Type<OrreryOrientationPayload> type() {
        return TYPE;
    }
}
