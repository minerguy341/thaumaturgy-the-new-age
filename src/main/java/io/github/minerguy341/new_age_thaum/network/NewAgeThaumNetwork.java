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
                        // LinkedHashMap: the payload list carries the server's category/entry
                        // order, and byCategory/categories() promise stable insertion order.
                        Map<ResourceLocation, io.github.minerguy341.new_age_thaum.core.codex.CodexEntry> incoming =
                                new java.util.LinkedHashMap<>();
                        for (var entry : payload.entries()) {
                            incoming.put(entry.id(), entry);
                        }
                        io.github.minerguy341.new_age_thaum.core.codex.CodexRegistry.reload(incoming);
                    });
            NetworkManager.registerReceiver(NetworkManager.s2c(), OrreryOrientationPayload.TYPE, OrreryOrientationPayload.STREAM_CODEC,
                    (payload, context) -> context.queue(() ->
                            io.github.minerguy341.new_age_thaum.client.NewAgeThaumClient.applyOrreryOrientation(payload)));
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
            NetworkManager.registerS2CPayloadType(OrreryOrientationPayload.TYPE, OrreryOrientationPayload.STREAM_CODEC);
        }

        // C2S: registered on both sides (client needs the type to send; the handler only
        // runs server-side). Reads happen on the game thread via context.queue.
        // The paper slot needs no custom packet: it is a real menu slot now.
        NetworkManager.registerReceiver(NetworkManager.c2s(), OrreryEditPayload.TYPE, OrreryEditPayload.STREAM_CODEC,
                (payload, context) -> context.queue(() -> handleOrreryEdit(payload, context)));
        NetworkManager.registerReceiver(NetworkManager.c2s(), OrreryRotatePayload.TYPE, OrreryRotatePayload.STREAM_CODEC,
                (payload, context) -> context.queue(() -> handleOrreryRotate(payload, context)));
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
        // Only the player actually working at this orrery may edit it — without the
        // open-menu check, anyone within reach could wipe another player's sphere.
        if (!(player.containerMenu instanceof io.github.minerguy341.new_age_thaum.content.ArcaneOrreryMenu menu)
                || !menu.pos().equals(payload.pos())) {
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
        // Unstamped papers are never editable: every paper is stamped on insertion or on
        // chunk load, so a missing puzzle means broken data — and skipping the checks
        // below would let arbitrary cell indices persist into the item component.
        var puzzle = orrery.puzzle().orElse(null);
        if (puzzle == null) {
            return false;
        }
        // A solved paper is sealed; endpoints are locked pre-placed cells; gaps are
        // holes in the sphere.
        if (puzzle.solved() || puzzle.isEndpoint(cell) || puzzle.isGap(cell)
                || cell < 0 || cell >= io.github.minerguy341.new_age_thaum.core.research.PuzzleGenerator
                        .gridFor(puzzle.frequency()).size()) {
            return false;
        }
        if (aspect.isPresent()) {
            if (aspect.get().equals(orrery.aspectAt(cell))) {
                // Repainting a cell with the aspect it already holds changes nothing —
                // succeed without charging the point or rebroadcasting.
                return true;
            }
            if (!AspectRegistry.exists(aspect.get())) {
                return false;
            }
            if (!io.github.minerguy341.new_age_thaum.core.player.PlayerProgressService.trySpend(player, aspect.get(), 1)) {
                return false;
            }
            orrery.editSphere(cell, aspect);
            maybeCompletePuzzle(player, orrery, puzzle);
            return true;
        }
        ResourceLocation cleared = orrery.aspectAt(cell);
        if (cleared == null) {
            return true;
        }
        orrery.editSphere(cell, aspect);
        if (cleared != null && player.getRandom().nextDouble()
                < io.github.minerguy341.new_age_thaum.core.player.PlayerProgressService.refundChance(player)) {
            io.github.minerguy341.new_age_thaum.core.player.PlayerProgressService.scanlessGrant(player, cleared, 1);
        }
        return true;
    }

    /**
     * After a successful placement: if every endpoint now shares one linked web, the
     * puzzle is complete — seal the paper and let the orrery hum (m2-gameplay-spec §D).
     */
    private static void maybeCompletePuzzle(ServerPlayer player,
            io.github.minerguy341.new_age_thaum.content.ArcaneOrreryBlockEntity orrery,
            io.github.minerguy341.new_age_thaum.core.research.ResearchPuzzle puzzle) {
        if (puzzle == null || puzzle.solved()) {
            return;
        }
        var grid = io.github.minerguy341.new_age_thaum.core.research.PuzzleGenerator.gridFor(puzzle.frequency());
        Map<Integer, ResourceLocation> cells = new HashMap<>(orrery.sphereCells());
        cells.putAll(puzzle.endpoints());
        // Papers written before edit validation existed can carry out-of-range cells;
        // LinkingPuzzle calls grid.cell() on every key, so drop them or the BFS crashes.
        cells.keySet().removeIf(index -> index < 0 || index >= grid.size());
        if (io.github.minerguy341.new_age_thaum.core.research.LinkingPuzzle.allEndpointsLinked(
                grid, cells, puzzle.endpoints().keySet())) {
            orrery.markSolved();
            player.serverLevel().playSound(null, orrery.getBlockPos(),
                    net.minecraft.sounds.SoundEvents.BEACON_ACTIVATE,
                    net.minecraft.sounds.SoundSource.BLOCKS, 0.8f, 1.2f);
        }
    }

    public static void sendOrreryEdit(net.minecraft.core.BlockPos pos, int cell, java.util.Optional<ResourceLocation> aspect) {
        NetworkManager.sendToServer(new OrreryEditPayload(pos, cell, aspect));
    }

    private static void handleOrreryRotate(OrreryRotatePayload payload, NetworkManager.PacketContext context) {
        var orrery = orreryInReach(context, payload.pos());
        if (orrery == null || !(context.getPlayer() instanceof ServerPlayer player)) {
            return;
        }
        // Same authorization as edits: only the player working at this orrery may spin it.
        if (!(player.containerMenu instanceof io.github.minerguy341.new_age_thaum.content.ArcaneOrreryMenu menu)
                || !menu.pos().equals(payload.pos())) {
            return;
        }
        if (!applyOrreryRotation(orrery, payload.x(), payload.y(), payload.z(), payload.w(),
                payload.wx(), payload.wy(), payload.wz())) {
            return;
        }
        // Mirror pose + velocity to other nearby players so they play the same
        // deterministic coast; the sender's client already wrote through.
        OrreryOrientationPayload out = new OrreryOrientationPayload(payload.pos(),
                payload.x(), payload.y(), payload.z(), payload.w(),
                payload.wx(), payload.wy(), payload.wz());
        for (ServerPlayer other : player.serverLevel().players()) {
            if (other != player
                    && other.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(payload.pos())) < 64.0 * 64.0) {
                sendIfPossible(other, out, OrreryOrientationPayload.TYPE);
            }
        }
    }

    /** Hard cap on flick speed a peer may claim, radians/ms (~11 turns of total coast). */
    private static final float MAX_COAST_SPEED = 0.1f;

    public static boolean applyOrreryRotation(
            io.github.minerguy341.new_age_thaum.content.ArcaneOrreryBlockEntity orrery,
            float x, float y, float z, float w) {
        return applyOrreryRotation(orrery, x, y, z, w, 0, 0, 0);
    }

    /**
     * The authoritative rotation: rejects non-finite or degenerate quaternions (a hacked
     * client must not park NaN in world save data) and normalizes. A non-zero angular
     * velocity is a flick: friction decays it as {@code e^(-t/tau)}, so the total
     * remaining travel is exactly {@code speed * tau} radians about a fixed axis — the
     * rest pose is computed analytically and stored, and the coast itself is layered on
     * as display-only state every client derives identically. Public so gametests drive
     * the exact server path.
     */
    public static boolean applyOrreryRotation(
            io.github.minerguy341.new_age_thaum.content.ArcaneOrreryBlockEntity orrery,
            float x, float y, float z, float w, float wx, float wy, float wz) {
        if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z) || !Float.isFinite(w)
                || !Float.isFinite(wx) || !Float.isFinite(wy) || !Float.isFinite(wz)) {
            return false;
        }
        if (x * x + y * y + z * z + w * w < 1.0e-6f) {
            return false;
        }
        org.joml.Quaternionf pose = new org.joml.Quaternionf(x, y, z, w).normalize();
        float speed = (float) Math.sqrt(wx * wx + wy * wy + wz * wz);
        float totalAngle = Math.min(speed, MAX_COAST_SPEED)
                * io.github.minerguy341.new_age_thaum.content.ArcaneOrreryBlockEntity.COAST_TAU_MS;
        if (speed <= 0 || totalAngle < 0.005f) {
            orrery.setOrientation(pose);
            return true;
        }
        org.joml.Quaternionf rest = new org.joml.Quaternionf()
                .rotationAxis(totalAngle, wx / speed, wy / speed, wz / speed).mul(pose);
        orrery.setOrientation(rest);
        orrery.startCoast(wx / speed, wy / speed, wz / speed, totalAngle);
        return true;
    }

    public static void sendOrreryRotation(net.minecraft.core.BlockPos pos, org.joml.Quaternionf orientation,
            float wx, float wy, float wz) {
        NetworkManager.sendToServer(new OrreryRotatePayload(pos,
                orientation.x, orientation.y, orientation.z, orientation.w, wx, wy, wz));
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
