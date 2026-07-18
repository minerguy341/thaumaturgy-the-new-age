package io.github.minerguy341.new_age_thaum.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import io.github.minerguy341.new_age_thaum.content.ArcaneOrreryBlockEntity;
import io.github.minerguy341.new_age_thaum.core.aspect.Aspect;
import io.github.minerguy341.new_age_thaum.core.aspect.AspectRegistry;
import io.github.minerguy341.new_age_thaum.core.research.PuzzleGenerator;
import io.github.minerguy341.new_age_thaum.core.research.ResearchPuzzle;
import io.github.minerguy341.new_age_thaum.core.research.grid.GoldbergGrid;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Holographic projection of the research sphere above the orrery, shown only while a
 * paper sits in the slot (M2 prototype). Rendered with {@code RenderType.debugQuads} —
 * a vanilla position-color, translucent, no-cull type, so no core-shader dependence
 * (PLAN.md §5 Iris/Sodium rule). Geometry, colors, and puzzle state all come from the
 * same sources the screen uses; the block entity's sync keeps them live.
 */
public class OrreryHologramRenderer implements BlockEntityRenderer<ArcaneOrreryBlockEntity> {
    private static final float HEIGHT = 1.55f;
    private static final float SCALE = 0.45f;
    private static final long SPIN_MILLIS = 24_000L;
    private static final int GOLD = 0xE8C86A;
    private static final int EMPTY_CELL = 0x2A2438;

    public OrreryHologramRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(ArcaneOrreryBlockEntity orrery, float partialTick, PoseStack poseStack,
            MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        if (orrery.paper().isEmpty()) {
            return;
        }
        ResearchPuzzle puzzle = orrery.puzzle().orElse(null);
        GoldbergGrid grid = PuzzleGenerator.gridFor(puzzle != null ? puzzle.frequency() : 3);
        Map<Integer, ResourceLocation> placed = orrery.sphereCells();

        long now = Util.getMillis();
        float angle = (now % SPIN_MILLIS) / (float) SPIN_MILLIS * ((float) Math.PI * 2f);
        boolean solved = puzzle != null && puzzle.solved();
        // Same breathing pulse the screen uses once the circuit has closed.
        double breath = solved ? 0.30 + 0.20 * Math.sin(now / 1000.0 * 2.4) : 0;

        poseStack.pushPose();
        poseStack.translate(0.5, HEIGHT, 0.5);
        poseStack.mulPose(Axis.YP.rotation(angle));
        poseStack.scale(SCALE, SCALE, SCALE);
        Matrix4f pose = poseStack.last().pose();
        VertexConsumer buffer = bufferSource.getBuffer(RenderType.debugQuads());

        // Translucency blends in emission order, so draw the camera-facing-away
        // hemisphere first. The facing test manually applies the same Y spin the pose
        // does: for JOML rotateY, x' = x cos + z sin, z' = -x sin + z cos.
        Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        Vec3 center = Vec3.atLowerCornerOf(orrery.getBlockPos()).add(0.5, HEIGHT, 0.5);
        Vec3 toCamera = camera.subtract(center);
        float cos = (float) Math.cos(angle);
        float sin = (float) Math.sin(angle);

        List<GoldbergGrid.Cell> back = new ArrayList<>();
        List<GoldbergGrid.Cell> front = new ArrayList<>();
        for (GoldbergGrid.Cell cell : grid.cells()) {
            if (puzzle != null && puzzle.isGap(cell.index())) {
                continue; // gaps are holes in the sphere
            }
            double nx = cell.x() * cos + cell.z() * sin;
            double nz = -cell.x() * sin + cell.z() * cos;
            (nx * toCamera.x + cell.y() * toCamera.y + nz * toCamera.z > 0 ? front : back).add(cell);
        }
        for (GoldbergGrid.Cell cell : back) {
            drawCell(buffer, pose, cell, puzzle, placed, breath);
        }
        for (GoldbergGrid.Cell cell : front) {
            drawCell(buffer, pose, cell, puzzle, placed, breath);
        }
        poseStack.popPose();
    }

    private static void drawCell(VertexConsumer buffer, Matrix4f pose, GoldbergGrid.Cell cell,
            ResearchPuzzle puzzle, Map<Integer, ResourceLocation> placed, double breath) {
        boolean endpoint = puzzle != null && puzzle.isEndpoint(cell.index());
        ResourceLocation aspect = endpoint ? puzzle.endpoints().get(cell.index()) : placed.get(cell.index());

        int rgb;
        int alpha;
        if (endpoint) {
            rgb = blend(colorOf(aspect), GOLD, 0.5);
            alpha = 0xC0;
        } else if (aspect != null) {
            rgb = colorOf(aspect);
            alpha = 0x90;
        } else {
            rgb = EMPTY_CELL;
            alpha = 0x30;
        }
        if (breath > 0) {
            rgb = blend(rgb, GOLD, breath);
        }
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;

        // Center-fan as degenerate quads (debugQuads draws QUADS; repeating the last
        // corner turns each fan triangle into a valid quad).
        double[][] corners = cell.corners();
        float cx = (float) cell.x();
        float cy = (float) cell.y();
        float cz = (float) cell.z();
        for (int i = 0; i < corners.length; i++) {
            double[] p1 = corners[i];
            double[] p2 = corners[(i + 1) % corners.length];
            buffer.addVertex(pose, cx, cy, cz).setColor(r, g, b, alpha);
            buffer.addVertex(pose, (float) p1[0], (float) p1[1], (float) p1[2]).setColor(r, g, b, alpha);
            buffer.addVertex(pose, (float) p2[0], (float) p2[1], (float) p2[2]).setColor(r, g, b, alpha);
            buffer.addVertex(pose, (float) p2[0], (float) p2[1], (float) p2[2]).setColor(r, g, b, alpha);
        }
    }

    private static int colorOf(ResourceLocation aspectId) {
        return aspectId == null ? 0x888888
                : AspectRegistry.get(aspectId).map(Aspect::color).orElse(0x888888);
    }

    private static int blend(int from, int to, double factor) {
        int r = (int) (((from >> 16) & 0xFF) * (1 - factor) + ((to >> 16) & 0xFF) * factor);
        int g = (int) (((from >> 8) & 0xFF) * (1 - factor) + ((to >> 8) & 0xFF) * factor);
        int b = (int) ((from & 0xFF) * (1 - factor) + (to & 0xFF) * factor);
        return (r << 16) | (g << 8) | b;
    }
}
