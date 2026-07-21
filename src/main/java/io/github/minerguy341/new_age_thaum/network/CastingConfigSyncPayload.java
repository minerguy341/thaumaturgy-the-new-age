package io.github.minerguy341.new_age_thaum.network;

import io.github.minerguy341.new_age_thaum.NewAgeThaum;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Sends the server's recharge-governing config to each client on join. The wand HUD
 * draws floor markers from these values; without the sync a multiplayer client would
 * mark them from its OWN config file, which need not match the server's mechanic.
 * Client-only display preferences (e.g. whether the HUD shows at all) are NOT synced —
 * those stay each player's own choice.
 */
public record CastingConfigSyncPayload(float ambientFloor, float affinityFloor,
        float rechargeRate, float nodeChargePerUse) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<CastingConfigSyncPayload> TYPE =
            new CustomPacketPayload.Type<>(NewAgeThaum.id("casting_config_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CastingConfigSyncPayload> STREAM_CODEC =
            StreamCodec.of(CastingConfigSyncPayload::write, CastingConfigSyncPayload::read);

    private static void write(RegistryFriendlyByteBuf buf, CastingConfigSyncPayload payload) {
        buf.writeFloat(payload.ambientFloor());
        buf.writeFloat(payload.affinityFloor());
        buf.writeFloat(payload.rechargeRate());
        buf.writeFloat(payload.nodeChargePerUse());
    }

    private static CastingConfigSyncPayload read(RegistryFriendlyByteBuf buf) {
        return new CastingConfigSyncPayload(buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat());
    }

    @Override
    public CustomPacketPayload.Type<CastingConfigSyncPayload> type() {
        return TYPE;
    }
}
