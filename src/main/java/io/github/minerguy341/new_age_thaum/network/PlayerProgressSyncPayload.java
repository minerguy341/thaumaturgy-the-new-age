package io.github.minerguy341.new_age_thaum.network;

import io.github.minerguy341.new_age_thaum.NewAgeThaum;
import io.github.minerguy341.new_age_thaum.core.player.PlayerProgress;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Sends the owning player their own {@link PlayerProgress} on join and after each change. */
public record PlayerProgressSyncPayload(PlayerProgress progress) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<PlayerProgressSyncPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(NewAgeThaum.MOD_ID, "player_progress_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PlayerProgressSyncPayload> STREAM_CODEC =
            StreamCodec.of(PlayerProgressSyncPayload::write, PlayerProgressSyncPayload::read);

    private static void write(RegistryFriendlyByteBuf buf, PlayerProgressSyncPayload payload) {
        PlayerProgress progress = payload.progress();
        buf.writeVarInt(progress.scanned().size());
        progress.scanned().forEach(buf::writeUtf);
        buf.writeVarInt(progress.points().size());
        progress.points().forEach((aspect, amount) -> {
            buf.writeResourceLocation(aspect);
            buf.writeVarInt(amount);
        });
    }

    private static PlayerProgressSyncPayload read(RegistryFriendlyByteBuf buf) {
        int scannedCount = buf.readVarInt();
        Set<String> scanned = new HashSet<>(scannedCount);
        for (int i = 0; i < scannedCount; i++) {
            scanned.add(buf.readUtf());
        }
        int pointCount = buf.readVarInt();
        Map<ResourceLocation, Integer> points = new HashMap<>(pointCount);
        for (int i = 0; i < pointCount; i++) {
            ResourceLocation aspect = buf.readResourceLocation();
            points.put(aspect, buf.readVarInt());
        }
        return new PlayerProgressSyncPayload(new PlayerProgress(scanned, points));
    }

    @Override
    public CustomPacketPayload.Type<PlayerProgressSyncPayload> type() {
        return TYPE;
    }
}
