package io.github.minerguy341.new_age_thaum.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.minerguy341.new_age_thaum.content.ArcaneOrreryBlockEntity;
import io.github.minerguy341.new_age_thaum.core.NewAgeThaumConfig;
import io.github.minerguy341.new_age_thaum.core.research.PuzzleGenerator;
import io.github.minerguy341.new_age_thaum.core.research.ResearchPuzzle;
import io.github.minerguy341.new_age_thaum.core.research.grid.GoldbergGrid;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Holographic projection of the research sphere above the orrery, shown only while a
 * paper sits in the slot (M2 prototype). Rendered with {@link ModRenderTypes#HOLOGRAM} —
 * vanilla position-color shader, translucent, no-cull, no depth write, so no core-shader
 * dependence (PLAN.md §5 Iris/Sodium rule). Geometry, colors, and puzzle state all come from the
 * same sources the screen uses; the block entity's sync keeps them live, and the
 * hologram mirrors the block entity's stored orientation — so it turns in world as a
 * viewing player drags the sphere around in the screen.
 */
public class OrreryHologramRenderer implements BlockEntityRenderer<ArcaneOrreryBlockEntity> {
    private static final float HEIGHT = 1.55f;
    private static final float SCALE = 0.45f;
    /**
     * The screen tunes its currents in pixels on a ~100px-radius sphere; the hologram
     * sphere has radius 1 pre-{@link #SCALE}, so 1px maps to 0.01 units and the config's
     * amplitude/width values read the same in both renderers.
     */
    private static final float PX = 0.01f;
    /** Currents float just above the cell fills so they never z-fight the surface. */
    private static final float LIFT = 1.02f;

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
        // COPY — the emission below is deferred past this BER call (LateHolograms).
        Matrix4f pose = new Matrix4f(poseStack.last().pose());

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

        // The energy currents between linked cells, same link web as the screen's
        // (endpoints count as painted cells). Split by hemisphere like the fills and
        // interleaved so a far-side current never blends over a near-side cell.
        Map<Integer, ResourceLocation> effective = placed;
        if (puzzle != null) {
            effective = new HashMap<>(placed);
            effective.putAll(puzzle.endpoints());
        }
        SphereLinks links = SphereLinks.compute(grid, effective, puzzle);
        List<int[]> backPairs = new ArrayList<>();
        List<int[]> frontPairs = new ArrayList<>();
        for (int[] pair : links.pairs()) {
            GoldbergGrid.Cell a = grid.cell(pair[0]);
            GoldbergGrid.Cell b = grid.cell(pair[1]);
            orientation.transform((float) (a.x() + b.x()), (float) (a.y() + b.y()),
                    (float) (a.z() + b.z()), rotated);
            (rotated.x * toCamera.x + rotated.y * toCamera.y + rotated.z * toCamera.z > 0
                    ? frontPairs : backPairs).add(pair);
        }

        double time = now / 1000.0;
        LateHolograms.enqueue(toCamera.lengthSqr(), buffer -> {
            for (GoldbergGrid.Cell cell : back) {
                drawCell(buffer, pose, cell, puzzle, placed, breath);
            }
            for (int[] pair : backPairs) {
                drawCurrent(buffer, pose, grid, links, pair, solved, breath, time);
            }
            for (GoldbergGrid.Cell cell : front) {
                drawCell(buffer, pose, cell, puzzle, placed, breath);
            }
            for (int[] pair : frontPairs) {
                drawCurrent(buffer, pose, grid, links, pair, solved, breath, time);
            }
        });
        poseStack.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen(ArcaneOrreryBlockEntity orrery) {
        // The sphere floats 1.5 blocks above the orrery: vanilla frustum-culls a block
        // entity by its block's chunk section, so looking slightly up in front of the
        // block made the projection vanish. Render off-screen like the beacon beam does.
        return true;
    }

    /**
     * One current: the same two-layer (soft glow + bright core) travelling-wave ribbon
     * the screen draws, in 3D on the hologram surface. Flow orientation, gradient,
     * chain phase, jitter, and pulse all mirror {@link ResearchSphereScreen}.
     */
    private static void drawCurrent(VertexConsumer buffer, Matrix4f pose, GoldbergGrid grid,
            SphereLinks links, int[] pair, boolean solved, double breath, double time) {
        // Orient the link downstream: the current flows from lower chain depth to higher.
        int from = pair[0];
        int to = pair[1];
        if (links.depth().getOrDefault(to, 0) < links.depth().getOrDefault(from, 0)) {
            from = pair[1];
            to = pair[0];
        }
        boolean custom = NewAgeThaumConfig.customCurrentColors();
        int c1 = custom ? NewAgeThaumConfig.currentBaseColor : SphereColors.colorOf(links.sane().get(from));
        int c2 = custom ? NewAgeThaumConfig.currentBaseColor : SphereColors.colorOf(links.sane().get(to));
        if (solved) {
            c1 = SphereColors.blend(c1, SphereColors.GOLD, breath);
            c2 = SphereColors.blend(c2, SphereColors.GOLD, breath);
        }
        int depth = links.depth().getOrDefault(from, 0);
        double jitter = (pair[0] * 31 + pair[1] * 17) % 97 / 97.0 * 0.9;
        double phase = depth * 2.2 + jitter;
        float widthScale = (float) NewAgeThaumConfig.currentWidth;
        // Per-current lift jitter and core-above-glow keep the depth-stamp pass from
        // being exactly coplanar where ribbons overlap; the color pass blends purely in
        // emission order and doesn't care.
        float lift = LIFT + (float) jitter * 0.006f;
        ribbon(buffer, pose, grid.cell(from), grid.cell(to), c1, c2,
                3.8f * widthScale, solved ? 110 : 70, time, phase, depth, lift);            // soft glow
        ribbon(buffer, pose, grid.cell(from), grid.cell(to), c1, c2,
                1.6f * widthScale, solved ? 255 : 235, time, phase, depth, lift + 0.004f);  // bright core
    }

    /**
     * A travelling-wave ribbon between two adjacent cell centers, lying on the sphere
     * just above the fills: sampled along the (near-)great-circle arc, displaced and
     * widened along the surface-tangent perpendicular to the path. Width is given in
     * screen pixels ({@link #PX} converts) so the config values match the screen.
     */
    private static void ribbon(VertexConsumer buffer, Matrix4f pose,
            GoldbergGrid.Cell fromCell, GoldbergGrid.Cell toCell,
            int rgb1, int rgb2, float width, int alpha, double time, double phase, int chainDepth,
            float lift) {
        final int segments = 10;
        Vector3f a = new Vector3f((float) fromCell.x(), (float) fromCell.y(), (float) fromCell.z());
        Vector3f b = new Vector3f((float) toCell.x(), (float) toCell.y(), (float) toCell.z());
        Vector3f chord = new Vector3f(b).sub(a);
        Vector3f point = new Vector3f();
        Vector3f side = new Vector3f();
        Vector3f[] left = new Vector3f[segments + 1];
        Vector3f[] right = new Vector3f[segments + 1];
        double amp = NewAgeThaumConfig.currentAmplitude * PX;
        double speed = NewAgeThaumConfig.currentSpeed;
        for (int i = 0; i <= segments; i++) {
            float t = (float) i / segments;
            // Adjacent cells subtend a small angle, so normalized lerp ≈ the arc.
            point.set(a).lerp(b, t).normalize();
            side.set(point).cross(chord).normalize();
            double envelope = Math.sin(Math.PI * t);
            double disp = envelope * amp * (1.9 * Math.sin(2 * Math.PI * 1.3 * t - time * speed * 2.4 + phase)
                    + 1.1 * Math.sin(2 * Math.PI * 2.7 * t - time * speed * 3.3 + phase * 1.7));
            float half = (float) (width * PX * (0.55 + 0.45 * envelope) / 2.0);
            point.mul(lift);
            float cx = point.x + side.x * (float) disp;
            float cy = point.y + side.y * (float) disp;
            float cz = point.z + side.z * (float) disp;
            left[i] = new Vector3f(cx + side.x * half, cy + side.y * half, cz + side.z * half);
            right[i] = new Vector3f(cx - side.x * half, cy - side.y * half, cz - side.z * half);
        }
        for (int i = 0; i < segments; i++) {
            int colA = SphereColors.glinted(SphereColors.blend(rgb1, rgb2, (double) i / segments),
                    (double) i / segments, time, speed, chainDepth);
            int colB = SphereColors.glinted(SphereColors.blend(rgb1, rgb2, (double) (i + 1) / segments),
                    (double) (i + 1) / segments, time, speed, chainDepth);
            int rA = (colA >> 16) & 0xFF;
            int gA = (colA >> 8) & 0xFF;
            int bA = colA & 0xFF;
            int rB = (colB >> 16) & 0xFF;
            int gB = (colB >> 8) & 0xFF;
            int bB = colB & 0xFF;
            buffer.addVertex(pose, left[i].x, left[i].y, left[i].z).setColor(rA, gA, bA, alpha);
            buffer.addVertex(pose, right[i].x, right[i].y, right[i].z).setColor(rA, gA, bA, alpha);
            buffer.addVertex(pose, right[i + 1].x, right[i + 1].y, right[i + 1].z).setColor(rB, gB, bB, alpha);
            buffer.addVertex(pose, left[i + 1].x, left[i + 1].y, left[i + 1].z).setColor(rB, gB, bB, alpha);
        }
    }

    private static void drawCell(VertexConsumer buffer, Matrix4f pose, GoldbergGrid.Cell cell,
            ResearchPuzzle puzzle, Map<Integer, ResourceLocation> placed, double breath) {
        boolean endpoint = puzzle != null && puzzle.isEndpoint(cell.index());
        ResourceLocation aspect = endpoint ? puzzle.endpoints().get(cell.index()) : placed.get(cell.index());

        int rgb;
        int alpha;
        // Filled cells are OPAQUE: with no depth writes in the color pass, anything
        // translucent lets the back hemisphere bleed through as misaligned grey patches.
        // Only the empty-cell veil stays see-through — that's the "hologram" read, and
        // the far side showing faintly through unpainted glass is intentional.
        if (endpoint) {
            rgb = SphereColors.blend(SphereColors.colorOf(aspect), SphereColors.GOLD, 0.5);
            alpha = 0xFF;
        } else if (aspect != null) {
            rgb = SphereColors.colorOf(aspect);
            alpha = 0xFF;
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
