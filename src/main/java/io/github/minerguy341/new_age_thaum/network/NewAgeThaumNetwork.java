package io.github.minerguy341.new_age_thaum.network;

import dev.architectury.networking.NetworkManager;
import dev.architectury.platform.Platform;
import dev.architectury.utils.Env;
import io.github.minerguy341.new_age_thaum.core.aspect.Aspect;
import io.github.minerguy341.new_age_thaum.core.aspect.AspectRegistry;
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
        } else {
            NetworkManager.registerS2CPayloadType(AspectSyncPayload.TYPE, AspectSyncPayload.STREAM_CODEC);
        }
    }

    public static void syncAspectsTo(ServerPlayer player) {
        NetworkManager.sendToPlayer(player, new AspectSyncPayload(List.copyOf(AspectRegistry.all())));
    }

    public static void syncAspectsToAll(MinecraftServer server) {
        AspectSyncPayload payload = new AspectSyncPayload(List.copyOf(AspectRegistry.all()));
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            NetworkManager.sendToPlayer(player, payload);
        }
    }
}
