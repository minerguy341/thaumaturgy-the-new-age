package io.github.minerguy341.new_age_thaum.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import org.joml.Quaternionf;

/**
 * The wire form of one sphere pose update: quaternion plus residual angular velocity
 * ({@code wx,wy,wz}, radians/ms — zero for plain drags, non-zero for a flick coast) and
 * the sender's friction time constant. Shared by {@link OrreryRotatePayload} (C2S) and
 * {@link OrreryOrientationPayload} (S2C) so the field lists and codecs cannot drift.
 */
public record OrientationFrame(float x, float y, float z, float w,
        float wx, float wy, float wz, float coastTau) {

    /** A plain pose update with no coast. */
    public static OrientationFrame still(Quaternionf pose) {
        return new OrientationFrame(pose.x, pose.y, pose.z, pose.w, 0f, 0f, 0f, 0f);
    }

    /** A flick: pose at release plus the residual spin the coast plays out. */
    public static OrientationFrame flick(Quaternionf pose, float wx, float wy, float wz, float tauMs) {
        return new OrientationFrame(pose.x, pose.y, pose.z, pose.w, wx, wy, wz, tauMs);
    }

    static void write(RegistryFriendlyByteBuf buf, OrientationFrame frame) {
        buf.writeFloat(frame.x);
        buf.writeFloat(frame.y);
        buf.writeFloat(frame.z);
        buf.writeFloat(frame.w);
        buf.writeFloat(frame.wx);
        buf.writeFloat(frame.wy);
        buf.writeFloat(frame.wz);
        buf.writeFloat(frame.coastTau);
    }

    static OrientationFrame read(RegistryFriendlyByteBuf buf) {
        return new OrientationFrame(buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(),
                buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat());
    }
}
