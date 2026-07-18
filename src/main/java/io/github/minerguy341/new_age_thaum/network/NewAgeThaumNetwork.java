package io.github.minerguy341.new_age_thaum.network;

import dev.architectury.networking.NetworkManager;
import dev.architectury.platform.Platform;
import dev.architectury.utils.Env;
import io.github.minerguy341.new_age_thaum.content.ArcaneOrreryBlockEntity;
import io.github.minerguy341.new_age_thaum.content.ArcaneOrreryMenu;
import io.github.minerguy341.new_age_thaum.core.aspect.Aspect;
import io.github.minerguy341.new_age_thaum.core.aspect.AspectAssignments;
import io.github.minerguy341.new_age_thaum.core.aspect.AspectRegistry;
import io.github.minerguy341.new_age_thaum.core.casting.WandMaterial;
import io.github.minerguy341.new_age_thaum.core.casting.WandMaterialRegistry;
import io.github.minerguy341.new_age_thaum.core.codex.CodexEntry;
import io.github.minerguy341.new_age_thaum.core.codex.CodexRegistry;
import io.github.minerguy341.new_age_thaum.core.player.PlayerProgress;
import io.github.minerguy341.new_age_thaum.core.player.PlayerProgressService;
import io.github.minerguy341.new_age_thaum.core.research.LinkingPuzzle;
import io.github.minerguy341.new_age_thaum.core.research.PuzzleGenerator;
import io.github.minerguy341.new_age_thaum.core.research.ResearchPuzzle;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * All payload registration plus the server-authoritative orrery handlers. S2C payloads:
 * the physical client registers a receiver; a dedicated server only registers the
 * payload type so it can send.
 *
 * <p>Import policy: {@code client.*} classes are referenced fully qualified, and ONLY
 * inside the {@code Env.CLIENT} registration branch — this common class must never load
 * them on a dedicated server. Everything else is imported normally.
 */
public final class NewAgeThaumNetwork {
    private NewAgeThaumNetwork() {
    }

    public static void init() {
        if (Platform.getEnvironment() == Env.CLIENT) {
            NetworkManager.registerReceiver(NetworkManager.s2c(), AspectSyncPayload.TYPE, AspectSyncPayload.STREAM_CODEC,
                    (payload, context) -> {
                        Map<ResourceLocation, Aspect> incoming = new HashMap<>();
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
                        Map<ResourceLocation, CodexEntry> incoming = new LinkedHashMap<>();
                        for (var entry : payload.entries()) {
                            incoming.put(entry.id(), entry);
                        }
                        CodexRegistry.reload(incoming);
                    });
            NetworkManager.registerReceiver(NetworkManager.s2c(), OrreryOrientationPayload.TYPE, OrreryOrientationPayload.STREAM_CODEC,
                    (payload, context) -> context.queue(() ->
                            io.github.minerguy341.new_age_thaum.client.NewAgeThaumClient.applyOrreryOrientation(payload)));
            NetworkManager.registerReceiver(NetworkManager.s2c(), WandMaterialSyncPayload.TYPE, WandMaterialSyncPayload.STREAM_CODEC,
                    (payload, context) -> {
                        Map<ResourceLocation, WandMaterial> incoming = new HashMap<>();
                        for (var material : payload.materials()) {
                            incoming.put(material.id(), material);
                        }
                        WandMaterialRegistry.reload(incoming);
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

    /**
     * The shared C2S authorization for orrery packets: the sender must be a real player,
     * within reach, AND have this orrery's menu open — without the open-menu check,
     * anyone nearby could wipe another player's sphere. Null when not authorized.
     */
    private static ArcaneOrreryBlockEntity authorizedOrrery(NetworkManager.PacketContext context, BlockPos pos) {
        if (!(context.getPlayer() instanceof ServerPlayer player)) {
            return null;
        }
        if (player.distanceToSqr(Vec3.atCenterOf(pos)) > 64.0) {
            return null;
        }
        if (!(player.containerMenu instanceof ArcaneOrreryMenu menu) || !menu.pos().equals(pos)) {
            return null;
        }
        return player.level().getBlockEntity(pos) instanceof ArcaneOrreryBlockEntity orrery ? orrery : null;
    }

    private static void handleOrreryEdit(OrreryEditPayload payload, NetworkManager.PacketContext context) {
        var orrery = authorizedOrrery(context, payload.pos());
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
    public static boolean applyOrreryEdit(ServerPlayer player, ArcaneOrreryBlockEntity orrery,
            int cell, Optional<ResourceLocation> aspect) {
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
                || cell < 0 || cell >= PuzzleGenerator.gridFor(puzzle.frequency()).size()) {
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
            if (!PlayerProgressService.trySpend(player, aspect.get(), 1)) {
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
        if (player.getRandom().nextDouble() < PlayerProgressService.refundChance(player)) {
            PlayerProgressService.scanlessGrant(player, cleared, 1);
        }
        return true;
    }

    /**
     * After a successful placement: if every endpoint now shares one linked web, the
     * puzzle is complete — seal the paper and let the orrery hum (m2-gameplay-spec §D).
     */
    private static void maybeCompletePuzzle(ServerPlayer player, ArcaneOrreryBlockEntity orrery,
            ResearchPuzzle puzzle) {
        if (puzzle == null || puzzle.solved()) {
            return;
        }
        var grid = PuzzleGenerator.gridFor(puzzle.frequency());
        Map<Integer, ResourceLocation> cells = new HashMap<>(orrery.sphereCells());
        cells.putAll(puzzle.endpoints());
        // Papers written before edit validation existed can carry out-of-range cells;
        // LinkingPuzzle calls grid.cell() on every key, so drop them or the BFS crashes.
        cells.keySet().removeIf(index -> index < 0 || index >= grid.size());
        if (LinkingPuzzle.allEndpointsLinked(grid, cells, puzzle.endpoints().keySet())) {
            orrery.markSolved();
            player.serverLevel().playSound(null, orrery.getBlockPos(),
                    SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 0.8f, 1.2f);
        }
    }

    public static void sendOrreryEdit(BlockPos pos, int cell, Optional<ResourceLocation> aspect) {
        NetworkManager.sendToServer(new OrreryEditPayload(pos, cell, aspect));
    }

    private static void handleOrreryRotate(OrreryRotatePayload payload, NetworkManager.PacketContext context) {
        var orrery = authorizedOrrery(context, payload.pos());
        if (orrery == null || !(context.getPlayer() instanceof ServerPlayer player)) {
            return;
        }
        if (!applyOrreryRotation(orrery, payload.frame())) {
            return;
        }
        // Mirror the frame to other nearby players so they play the same deterministic
        // coast; the sender's client already wrote through.
        OrreryOrientationPayload out = new OrreryOrientationPayload(payload.pos(), payload.frame());
        for (ServerPlayer other : player.serverLevel().players()) {
            if (other != player && other.distanceToSqr(Vec3.atCenterOf(payload.pos())) < 64.0 * 64.0) {
                sendIfPossible(other, out, OrreryOrientationPayload.TYPE);
            }
        }
    }

    public static boolean applyOrreryRotation(ArcaneOrreryBlockEntity orrery, float x, float y, float z, float w) {
        return applyOrreryRotation(orrery, new OrientationFrame(x, y, z, w, 0, 0, 0,
                ArcaneOrreryBlockEntity.COAST_TAU_MS));
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
    public static boolean applyOrreryRotation(ArcaneOrreryBlockEntity orrery, OrientationFrame frame) {
        if (!Float.isFinite(frame.x()) || !Float.isFinite(frame.y()) || !Float.isFinite(frame.z())
                || !Float.isFinite(frame.w()) || !Float.isFinite(frame.wx()) || !Float.isFinite(frame.wy())
                || !Float.isFinite(frame.wz()) || !Float.isFinite(frame.coastTau())) {
            return false;
        }
        float lengthSq = frame.x() * frame.x() + frame.y() * frame.y()
                + frame.z() * frame.z() + frame.w() * frame.w();
        if (lengthSq < 1.0e-6f) {
            return false;
        }
        Quaternionf pose = new Quaternionf(frame.x(), frame.y(), frame.z(), frame.w()).normalize();
        float speed = (float) Math.sqrt(frame.wx() * frame.wx() + frame.wy() * frame.wy()
                + frame.wz() * frame.wz());
        // The flicking player's configured friction rides in the packet (clamped), so
        // every party integrates with the SAME tau and converges on the same rest pose.
        float tau = Mth.clamp(frame.coastTau(),
                ArcaneOrreryBlockEntity.MIN_COAST_TAU_MS, ArcaneOrreryBlockEntity.MAX_COAST_TAU_MS);
        float totalAngle = Math.min(speed, ArcaneOrreryBlockEntity.MAX_COAST_SPEED) * tau;
        if (speed <= 0 || totalAngle < 0.005f) {
            orrery.setOrientation(pose);
            return true;
        }
        Quaternionf rest = new Quaternionf()
                .rotationAxis(totalAngle, frame.wx() / speed, frame.wy() / speed, frame.wz() / speed).mul(pose);
        orrery.setOrientation(rest);
        orrery.startCoast(frame.wx() / speed, frame.wy() / speed, frame.wz() / speed, totalAngle, tau);
        return true;
    }

    public static void sendOrreryRotation(BlockPos pos, Quaternionf orientation,
            float wx, float wy, float wz, float tauMs) {
        NetworkManager.sendToServer(new OrreryRotatePayload(pos,
                OrientationFrame.flick(orientation, wx, wy, wz, tauMs)));
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

    private static void syncToAll(MinecraftServer server, Consumer<ServerPlayer> sync) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            sync.accept(player);
        }
    }

    public static void syncProgressTo(ServerPlayer player, PlayerProgress progress) {
        sendIfPossible(player, new PlayerProgressSyncPayload(progress), PlayerProgressSyncPayload.TYPE);
    }

    public static void syncCodexTo(ServerPlayer player) {
        sendIfPossible(player, new CodexSyncPayload(List.copyOf(CodexRegistry.all())), CodexSyncPayload.TYPE);
    }

    public static void syncCodexToAll(MinecraftServer server) {
        syncToAll(server, NewAgeThaumNetwork::syncCodexTo);
    }

    public static void syncWandMaterialsTo(ServerPlayer player) {
        sendIfPossible(player, new WandMaterialSyncPayload(List.copyOf(WandMaterialRegistry.all())),
                WandMaterialSyncPayload.TYPE);
    }

    public static void syncWandMaterialsToAll(MinecraftServer server) {
        syncToAll(server, NewAgeThaumNetwork::syncWandMaterialsTo);
    }

    public static void syncAspectsTo(ServerPlayer player) {
        sendIfPossible(player, new AspectSyncPayload(List.copyOf(AspectRegistry.all())), AspectSyncPayload.TYPE);
    }

    public static void syncAspectsToAll(MinecraftServer server) {
        syncToAll(server, NewAgeThaumNetwork::syncAspectsTo);
    }

    public static void syncAssignmentsTo(ServerPlayer player) {
        sendIfPossible(player,
                new AssignmentSyncPayload(AspectAssignments.itemAssignments(), AspectAssignments.tagAssignments()),
                AssignmentSyncPayload.TYPE);
    }

    public static void syncAssignmentsToAll(MinecraftServer server) {
        syncToAll(server, NewAgeThaumNetwork::syncAssignmentsTo);
    }
}
