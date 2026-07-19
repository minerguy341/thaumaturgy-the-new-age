package io.github.minerguy341.new_age_thaum.client;

import dev.architectury.event.events.client.ClientCommandRegistrationEvent;
import io.github.minerguy341.new_age_thaum.content.ArcaneOrreryBlockEntity;
import io.github.minerguy341.new_age_thaum.core.research.PuzzleGenerator;
import io.github.minerguy341.new_age_thaum.core.research.ResearchPuzzle;
import io.github.minerguy341.new_age_thaum.core.research.grid.GoldbergGrid;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * {@code /thaum debug} (client side of the shared {@code /thaum} root) — snapshots
 * exactly what the hologram renderer computes for the orrery you're looking at (or the
 * nearest one): camera pose, sphere orientation, and for every current the SAME
 * visibility numbers the renderer uses (line-of-sight value per endpoint, fade,
 * verdict). Chat gets a one-line summary; the full dump goes to
 * {@code new_age_thaum_debug.txt} in the game directory for pasting into a bug report.
 * Reads {@link OrreryHologramRenderer}'s own constants and math so it can never drift
 * from what actually rendered.
 */
public final class OrreryDebugCommand {
    private OrreryDebugCommand() {
    }

    public static void register() {
        ClientCommandRegistrationEvent.EVENT.register((dispatcher, context) ->
                dispatcher.register(ClientCommandRegistrationEvent.literal("thaum")
                        .then(ClientCommandRegistrationEvent.literal("debug")
                                .executes(ctx -> snapshot()))));
    }

    private static int snapshot() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return 0;
        }
        ArcaneOrreryBlockEntity orrery = findOrrery(mc);
        if (orrery == null) {
            chat(mc, "thaum debug: no orrery with a paper within 16 blocks (or under the crosshair).",
                    ChatFormatting.RED);
            return 0;
        }

        StringBuilder out = new StringBuilder();
        Vec3 camera = mc.gameRenderer.getMainCamera().getPosition();
        BlockPos pos = orrery.getBlockPos();
        Vec3 center = Vec3.atLowerCornerOf(pos).add(0.5, OrreryHologramRenderer.HEIGHT, 0.5);
        Quaternionf orientation = orrery.displayOrientation();

        out.append("=== new_age_thaum orrery hologram debug ===\n");
        out.append(String.format(Locale.ROOT, "gameTime: %d%n", mc.level.getGameTime()));
        out.append(String.format(Locale.ROOT, "camera(world): %.4f %.4f %.4f%n", camera.x, camera.y, camera.z));
        out.append(String.format(Locale.ROOT, "orrery block: %s   sphere center: %.4f %.4f %.4f%n",
                pos.toShortString(), center.x, center.y, center.z));
        out.append(String.format(Locale.ROOT, "camera->center distance: %.4f blocks%n", camera.distanceTo(center)));
        Quaternionf rest = orrery.orientation();
        out.append(String.format(Locale.ROOT, "orientation(rest):    %.5f %.5f %.5f %.5f%n",
                rest.x, rest.y, rest.z, rest.w));
        out.append(String.format(Locale.ROOT, "orientation(display): %.5f %.5f %.5f %.5f   coasting: %s%n",
                orientation.x, orientation.y, orientation.z, orientation.w, orrery.isCoasting()));
        out.append(String.format(Locale.ROOT, "constants: HEIGHT=%.3f SCALE=%.3f LIFT=%.3f fadeBand(closestSq)=0.15%n",
                OrreryHologramRenderer.HEIGHT, OrreryHologramRenderer.SCALE, OrreryHologramRenderer.LIFT));

        ResearchPuzzle puzzle = orrery.puzzle().orElse(null);
        GoldbergGrid grid = PuzzleGenerator.gridFor(puzzle != null ? puzzle.frequency() : 3);
        Map<Integer, ResourceLocation> placed = orrery.sphereCells();
        Map<Integer, ResourceLocation> effective = placed;
        if (puzzle != null) {
            effective = new HashMap<>(placed);
            effective.putAll(puzzle.endpoints());
        }
        out.append(String.format(Locale.ROOT, "puzzle: %s   gridSize: %d   placed(+endpoints): %d%n",
                puzzle == null ? "none" : ("freq=" + puzzle.frequency() + " solved=" + puzzle.solved()
                        + " endpoints=" + puzzle.endpoints().keySet() + " gaps=" + puzzle.gaps()),
                grid.size(), effective.size()));

        // The exact frame the renderer uses.
        Quaternionf inverse = new Quaternionf(orientation).conjugate();
        Vector3f camLocal = new Vector3f(
                (float) ((camera.x - center.x) / OrreryHologramRenderer.SCALE),
                (float) ((camera.y - center.y) / OrreryHologramRenderer.SCALE),
                (float) ((camera.z - center.z) / OrreryHologramRenderer.SCALE));
        inverse.transform(camLocal);
        out.append(String.format(Locale.ROOT, "camLocal: %.5f %.5f %.5f   |camLocal|=%.5f (1.0 = sphere surface)%n",
                camLocal.x, camLocal.y, camLocal.z, camLocal.length()));

        SphereLinks links = SphereLinks.compute(grid, effective, puzzle);
        out.append(String.format(Locale.ROOT, "currents: %d%n", links.pairs().size()));
        int full = 0;
        int faded = 0;
        int culled = 0;
        for (int[] pair : links.pairs()) {
            GoldbergGrid.Cell a = grid.cell(pair[0]);
            GoldbergGrid.Cell b = grid.cell(pair[1]);
            float visA = OrreryHologramRenderer.endpointVisibility(
                    a.x() * OrreryHologramRenderer.LIFT, a.y() * OrreryHologramRenderer.LIFT,
                    a.z() * OrreryHologramRenderer.LIFT, camLocal);
            float visB = OrreryHologramRenderer.endpointVisibility(
                    b.x() * OrreryHologramRenderer.LIFT, b.y() * OrreryHologramRenderer.LIFT,
                    b.z() * OrreryHologramRenderer.LIFT, camLocal);
            float fade = Math.min(visA, visB);
            String verdict = fade <= 0f ? "CULLED" : fade >= 1f ? "FULL" : String.format(Locale.ROOT, "FADED %.0f%%", fade * 100);
            if (fade <= 0f) {
                culled++;
            } else if (fade >= 1f) {
                full++;
            } else {
                faded++;
            }
            // dotSurf is the old horizon-cap metric, kept for comparison in reports.
            float dotA = (float) (a.x() * camLocal.x + a.y() * camLocal.y + a.z() * camLocal.z);
            float dotB = (float) (b.x() * camLocal.x + b.y() * camLocal.y + b.z() * camLocal.z);
            out.append(String.format(Locale.ROOT,
                    "  %3d(%s) -> %3d(%s): visA=%.3f visB=%.3f fade=%.3f [%s]  dotSurfA=%.3f dotSurfB=%.3f%n",
                    pair[0], name(effective.get(pair[0])), pair[1], name(effective.get(pair[1])),
                    visA, visB, fade, verdict, dotA, dotB));
            out.append(String.format(Locale.ROOT,
                    "        cellA dir=(%.4f %.4f %.4f)  cellB dir=(%.4f %.4f %.4f)%n",
                    a.x(), a.y(), a.z(), b.x(), b.y(), b.z()));
        }

        Path file = mc.gameDirectory.toPath().resolve("new_age_thaum_debug.txt");
        try {
            Files.writeString(file, out.toString());
        } catch (IOException e) {
            chat(mc, "thaum debug: could not write " + file + ": " + e.getMessage(), ChatFormatting.RED);
            return 0;
        }
        chat(mc, String.format(Locale.ROOT,
                "thaum debug: %d currents — %d full, %d faded, %d culled. Wrote %s",
                links.pairs().size(), full, faded, culled, file), ChatFormatting.AQUA);
        return 1;
    }

    private static String name(ResourceLocation aspect) {
        return aspect == null ? "?" : aspect.getPath();
    }

    /** The orrery under the crosshair if it has a paper, else the nearest one in 16 blocks. */
    private static ArcaneOrreryBlockEntity findOrrery(Minecraft mc) {
        if (mc.hitResult instanceof BlockHitResult hit && mc.hitResult.getType() == HitResult.Type.BLOCK
                && mc.level.getBlockEntity(hit.getBlockPos()) instanceof ArcaneOrreryBlockEntity looked
                && !looked.paper().isEmpty()) {
            return looked;
        }
        BlockPos around = mc.player.blockPosition();
        ArcaneOrreryBlockEntity best = null;
        double bestDistSqr = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(around.offset(-16, -16, -16), around.offset(16, 16, 16))) {
            if (mc.level.getBlockEntity(pos) instanceof ArcaneOrreryBlockEntity candidate
                    && !candidate.paper().isEmpty()) {
                double distSqr = pos.distSqr(around);
                if (distSqr < bestDistSqr) {
                    bestDistSqr = distSqr;
                    best = candidate;
                }
            }
        }
        return best;
    }

    private static void chat(Minecraft mc, String message, ChatFormatting color) {
        if (mc.player != null) {
            mc.player.displayClientMessage(Component.literal(message).withStyle(color), false);
        }
    }
}
