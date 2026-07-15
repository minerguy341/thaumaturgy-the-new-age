package io.github.minerguy341.new_age_thaum.network;

import dev.architectury.networking.NetworkManager;
import dev.architectury.platform.Platform;
import dev.architectury.utils.Env;
import io.github.minerguy341.new_age_thaum.core.aspect.Aspect;
import io.github.minerguy341.new_age_thaum.core.aspect.AspectAssignments;
import io.github.minerguy341.new_age_thaum.core.aspect.AspectRegistry;
import io.github.minerguy341.new_age_thaum.core.player.PlayerProgress;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * All payload registration. S2C payloads: the physical client registers a
 * receiver; a dedicated server only registers the payload type so it can send.
 */
public final class NewAgeThaumNetwork {
    private NewAgeThaumNetwork() {
    }

    public static void init() {
        if (Platform.getEnvironment() == Env.CLIENT) {
            NetworkManager.registerReceiver(NetworkManager.s2c(), AspectSyncPayload.TYPE, AspectSyncPayload.STREAM_CODEC,
                    (payload, context) -> {
                        Map<net.minecraft.resources.ResourceLocation, Aspect> incoming = new HashMap<>();
                        for (Aspect aspect : payload.aspects()) {
                            incoming.put(aspect.id(), aspect);
                        }
                        AspectRegistry.reload(incoming);
                    });
            NetworkManager.registerReceiver(NetworkManager.s2c(), AssignmentSyncPayload.TYPE, AssignmentSyncPayload.STREAM_CODEC,
                    (payload, context) -> AspectAssignments.accept(payload.byItem(), payload.byTag()));
            NetworkManager.registerReceiver(NetworkManager.s2c(), PlayerProgressSyncPayload.TYPE, PlayerProgressSyncPayload.STREAM_CODEC,
                    (payload, context) -> io.github.minerguy341.new_age_thaum.client.ClientPlayerProgress.set(payload.progress()));
        } else {
            NetworkManager.registerS2CPayloadType(AspectSyncPayload.TYPE, AspectSyncPayload.STREAM_CODEC);
            NetworkManager.registerS2CPayloadType(AssignmentSyncPayload.TYPE, AssignmentSyncPayload.STREAM_CODEC);
            NetworkManager.registerS2CPayloadType(PlayerProgressSyncPayload.TYPE, PlayerProgressSyncPayload.STREAM_CODEC);
        }
    }

    /**
     * Sends only if the player's connection can actually receive the payload.
     * Guards against clients without our channel and against mock/fake players
     * (e.g. gametest players), which NeoForge otherwise rejects with an exception.
     */
    private static void sendIfPossible(ServerPlayer player, CustomPacketPayload payload,
            CustomPacketPayload.Type<?> type) {
        if (NetworkManager.canPlayerReceive(player, type)) {
            NetworkManager.sendToPlayer(player, payload);
        }
    }

    public static void syncProgressTo(ServerPlayer player, PlayerProgress progress) {
        sendIfPossible(player, new PlayerProgressSyncPayload(progress), PlayerProgressSyncPayload.TYPE);
    }

    public static void syncAspectsTo(ServerPlayer player) {
        sendIfPossible(player, new AspectSyncPayload(List.copyOf(AspectRegistry.all())), AspectSyncPayload.TYPE);
    }

    public static void syncAspectsToAll(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            syncAspectsTo(player);
        }
    }

    public static void syncAssignmentsTo(ServerPlayer player) {
        sendIfPossible(player,
                new AssignmentSyncPayload(AspectAssignments.itemAssignments(), AspectAssignments.tagAssignments()),
                AssignmentSyncPayload.TYPE);
    }

    public static void syncAssignmentsToAll(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            syncAssignmentsTo(player);
        }
    }
}
