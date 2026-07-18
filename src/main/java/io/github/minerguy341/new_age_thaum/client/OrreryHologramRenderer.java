package io.github.minerguy341.new_age_thaum.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.minerguy341.new_age_thaum.content.ArcaneOrreryBlockEntity;
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
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Holographic projection of the research sphere above the orrery, shown only while a
 * paper sits in the slot (M2 prototype). Rendered with {@code RenderType.debugQuads} —
 * a vanilla position-color, translucent, no-cull type, so no core-shader dependence
 * (PLAN.md §5 Iris/Sodium rule). Geometry, colors, and puzzle state all come from the
 * same sources the screen uses; the block entity's sync keeps them live, and the
 * hologram mirrors the block entity's stored orientation — so it turns in world as a
 * viewing player drags the sphere around in the screen.
 */
public class OrreryHologramRenderer implements BlockEntityRenderer<ArcaneOrreryBlockEntity> {
    private static final float HEIGHT = 1.55f;
    private static final float SCALE = 0.45f;

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
        boolean solved = puzzle != null && puzzle.solved();
        // Same breathing pulse the screen uses once the circuit has closed.
        double breath = SphereColors.solvedBreath(solved, now);
        // The screen's drag orientation streamed to the BE, including any in-flight
        // flick coast (displayOrientation converges on the stored rest pose). If
        // in-game testing shows the world rotation vertically mirrored versus the
        // screen (screen space is y-down), the first knob to try is rendering
        // `orientation.conjugate()` here.
        Quaternionf orientation = orrery.displayOrientation();

        poseStack.pushPose();
        poseStack.translate(0.5, HEIGHT, 0.5);
        poseStack.mulPose(orientation);
        poseStack.scale(SCALE, SCALE, SCALE);
        Matrix4f pose = poseStack.last().pose();
        VertexConsumer buffer = bufferSource.getBuffer(RenderType.debugQuads());

        // Translucency blends in emission order, so draw the camera-facing-away
        // hemisphere first — facing computed against each cell's rotated normal.
        Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        Vec3 center = Vec3.atLowerCornerOf(orrery.getBlockPos()).add(0.5, HEIGHT, 0.5);
        Vec3 toCamera = camera.subtract(center);
        Vector3f rotated = new Vector3f();

        List<GoldbergGrid.Cell> back = new ArrayList<>();
        List<GoldbergGrid.Cell> front = new ArrayList<>();
        for (GoldbergGrid.Cell cell : grid.cells()) {
            if (puzzle != null && puzzle.isGap(cell.index())) {
                continue; // gaps are holes in the sphere
            }
            orientation.transform((float) cell.x(), (float) cell.y(), (float) cell.z(), rotated);
            (rotated.x * toCamera.x + rotated.y * toCamera.y + rotated.z * toCamera.z > 0 ? front : back).add(cell);
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
        // Alphas tuned brighter after in-game feedback: the first pass was hard to read.
        if (endpoint) {
            rgb = SphereColors.blend(SphereColors.colorOf(aspect), SphereColors.GOLD, 0.5);
            alpha = 0xEE;
        } else if (aspect != null) {
            rgb = SphereColors.colorOf(aspect);
            alpha = 0xD4;
        } else {
            rgb = SphereColors.EMPTY_CELL;
            alpha = 0x60;
        }
        if (breath > 0) {
            rgb = SphereColors.blend(rgb, SphereColors.GOLD, breath);
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

}
