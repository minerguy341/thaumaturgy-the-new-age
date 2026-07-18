package io.github.minerguy341.new_age_thaum.network;

import dev.architectury.networking.NetworkManager;
import dev.architectury.platform.Platform;
import dev.architectury.utils.Env;
import io.github.minerguy341.new_age_thaum.core.aspect.Aspect;
import io.github.minerguy341.new_age_thaum.core.aspect.AspectAssignments;
import io.github.minerguy341.new_age_thaum.core.aspect.AspectRegistry;
import io.github.minerguy341.new_age_thaum.core.codex.CodexRegistry;
import io.github.minerguy341.new_age_thaum.core.player.PlayerProgress;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
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
            NetworkManager.registerReceiver(NetworkManager.s2c(), CodexSyncPayload.TYPE, CodexSyncPayload.STREAM_CODEC,
                    (payload, context) -> {
                        Map<ResourceLocation, io.github.minerguy341.new_age_thaum.core.codex.CodexEntry> incoming = new HashMap<>();
                        for (var entry : payload.entries()) {
                            incoming.put(entry.id(), entry);
                        }
                        io.github.minerguy341.new_age_thaum.core.codex.CodexRegistry.reload(incoming);
                    });
            NetworkManager.registerReceiver(NetworkManager.s2c(), WandMaterialSyncPayload.TYPE, WandMaterialSyncPayload.STREAM_CODEC,
                    (payload, context) -> {
                        Map<ResourceLocation, io.github.minerguy341.new_age_thaum.core.casting.WandMaterial> incoming = new HashMap<>();
                        for (var material : payload.materials()) {
                            incoming.put(material.id(), material);
                        }
                        io.github.minerguy341.new_age_thaum.core.casting.WandMaterialRegistry.reload(incoming);
                    });
        } else {
            NetworkManager.registerS2CPayloadType(AspectSyncPayload.TYPE, AspectSyncPayload.STREAM_CODEC);
            NetworkManager.registerS2CPayloadType(AssignmentSyncPayload.TYPE, AssignmentSyncPayload.STREAM_CODEC);
            NetworkManager.registerS2CPayloadType(PlayerProgressSyncPayload.TYPE, PlayerProgressSyncPayload.STREAM_CODEC);
            NetworkManager.registerS2CPayloadType(CodexSyncPayload.TYPE, CodexSyncPayload.STREAM_CODEC);
            NetworkManager.registerS2CPayloadType(WandMaterialSyncPayload.TYPE, WandMaterialSyncPayload.STREAM_CODEC);
        }

        // C2S: registered on both sides (client needs the type to send; the handler only
        // runs server-side). Reads happen on the game thread via context.queue.
        // The paper slot needs no custom packet: it is a real menu slot now.
        NetworkManager.registerReceiver(NetworkManager.c2s(), OrreryEditPayload.TYPE, OrreryEditPayload.STREAM_CODEC,
                (payload, context) -> context.queue(() -> handleOrreryEdit(payload, context)));
    }

    private static io.github.minerguy341.new_age_thaum.content.ArcaneOrreryBlockEntity orreryInReach(
            NetworkManager.PacketContext context, net.minecraft.core.BlockPos pos) {
        if (!(context.getPlayer() instanceof ServerPlayer player)) {
            return null;
        }
        if (player.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(pos)) > 64.0) {
            return null;
        }
        return player.level().getBlockEntity(pos)
                instanceof io.github.minerguy341.new_age_thaum.content.ArcaneOrreryBlockEntity orrery ? orrery : null;
    }

    private static void handleOrreryEdit(OrreryEditPayload payload, NetworkManager.PacketContext context) {
        var orrery = orreryInReach(context, payload.pos());
        if (orrery == null || !(context.getPlayer() instanceof ServerPlayer player)) {
            return;
        }
        if (!applyOrreryEdit(player, orrery, payload.cell(), payload.aspect())) {
            // Rejected (can't afford / no paper / unknown aspect): the client painted
            // optimistically, so force a full menu resync to roll it back.
            player.containerMenu.broadcastFullState();
        }
    }

    /**
     * The authoritative edit: placing costs 1 observation point of the placed aspect
     * (m2-gameplay-spec §A — repainting pays for the new aspect, the old is lost);
     * clearing refunds only via the future research seam ({@code refundChance}, 0 now).
     * Public so gametests can drive the exact server path.
     */
    public static boolean applyOrreryEdit(ServerPlayer player,
            io.github.minerguy341.new_age_thaum.content.ArcaneOrreryBlockEntity orrery,
            int cell, java.util.Optional<ResourceLocation> aspect) {
        if (!orrery.canEditSphere()) {
            return false;
        }
        var puzzle = orrery.puzzle().orElse(null);
        if (puzzle != null) {
            // Endpoints are locked pre-placed cells; gaps are holes in the sphere.
            if (puzzle.isEndpoint(cell) || puzzle.isGap(cell)
                    || cell < 0 || cell >= io.github.minerguy341.new_age_thaum.core.research.PuzzleGenerator
                            .gridFor(puzzle.frequency()).size()) {
                return false;
            }
        }
        if (aspect.isPresent()) {
            if (!AspectRegistry.exists(aspect.get())) {
                return false;
            }
            if (!io.github.minerguy341.new_age_thaum.core.player.PlayerProgressService.trySpend(player, aspect.get(), 1)) {
                return false;
            }
            orrery.editSphere(cell, aspect);
            return true;
        }
        ResourceLocation cleared = orrery.aspectAt(cell);
        orrery.editSphere(cell, aspect);
        if (cleared != null && player.getRandom().nextDouble()
                < io.github.minerguy341.new_age_thaum.core.player.PlayerProgressService.refundChance(player)) {
            io.github.minerguy341.new_age_thaum.core.player.PlayerProgressService.scanlessGrant(player, cleared, 1);
        }
        return true;
    }

    public static void sendOrreryEdit(net.minecraft.core.BlockPos pos, int cell, java.util.Optional<ResourceLocation> aspect) {
        NetworkManager.sendToServer(new OrreryEditPayload(pos, cell, aspect));
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

    public static void syncCodexTo(ServerPlayer player) {
        sendIfPossible(player, new CodexSyncPayload(List.copyOf(CodexRegistry.all())), CodexSyncPayload.TYPE);
    }

    public static void syncCodexToAll(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            syncCodexTo(player);
        }
    }

    public static void syncWandMaterialsTo(ServerPlayer player) {
        sendIfPossible(player,
                new WandMaterialSyncPayload(List.copyOf(
                        io.github.minerguy341.new_age_thaum.core.casting.WandMaterialRegistry.all())),
                WandMaterialSyncPayload.TYPE);
    }

    public static void syncWandMaterialsToAll(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            syncWandMaterialsTo(player);
        }
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
