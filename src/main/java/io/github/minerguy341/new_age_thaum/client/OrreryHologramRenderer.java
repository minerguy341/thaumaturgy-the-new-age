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

import java.util.HashMap;
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

        // Every cell (and every current) is its OWN sort unit at its own true camera
        // distance. Coarser groupings all have a wrong region: hemisphere-by-facing
        // breaks at the LIMB, where silhouette cells are nearer than the sphere's
        // center (sqrt(d²−R²)) yet a facing split labels half of them "far" — so
        // another hologram behind the sphere blended over its own edge. Cells tile
        // the sphere without overlapping each other, so per-cell order only has to be
        // right relative to OTHER holograms, which exact distances guarantee.
        Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        Vec3 center = Vec3.atLowerCornerOf(orrery.getBlockPos()).add(0.5, HEIGHT, 0.5);
        Vector3f rotated = new Vector3f();

        Map<Integer, ResourceLocation> effective = placed;
        if (puzzle != null) {
            effective = new HashMap<>(placed);
            effective.putAll(puzzle.endpoints());
        }
        SphereLinks links = SphereLinks.compute(grid, effective, puzzle);
        double time = now / 1000.0;

        // Cells enqueue as OCCLUDERS: the LateHolograms depth prepass stamps the shell's
        // nearest surface, and the color pass's depth test then hides the far side per
        // pixel — no facing math, no fade. The front face runs full-strength to a crisp
        // silhouette, and gap holes (never stamped) still reveal the interior far wall.
        for (GoldbergGrid.Cell cell : grid.cells()) {
            if (puzzle != null && puzzle.isGap(cell.index())) {
                continue; // gaps are holes in the sphere
            }
            orientation.transform((float) cell.x(), (float) cell.y(), (float) cell.z(), rotated);
            double dx = center.x + rotated.x * SCALE - camera.x;
            double dy = center.y + rotated.y * SCALE - camera.y;
            double dz = center.z + rotated.z * SCALE - camera.z;
            LateHolograms.enqueue(dx * dx + dy * dy + dz * dz, true,
                    buffer -> drawCell(buffer, pose, cell, puzzle, placed, breath));
        }
        // Each current sorts at its lifted arc midpoint — marginally nearer than the
        // cells it connects, so it draws after them and rides on top. Not an occluder:
        // its glow-under-core layering depends on blending.
        //
        // Visibility is ALL-OR-NOTHING per current: a partial ribbon always reads as
        // broken (a tail amputated at the limb, or a floating scrap where an arc
        // between two hidden cells crests back over the horizon). A current draws iff
        // BOTH its cells are on the visible cap — dot(cellDir, camLocal) > 1 in the
        // sphere's local frame — and since a sub-hemisphere cap is geodesically
        // convex, the whole arc between two visible cells is provably visible: nothing
        // can clip it. A short fade below the threshold turns the pop into a dissolve.
        Quaternionf inverse = new Quaternionf(orientation).conjugate();
        Vector3f camLocal = new Vector3f(
                (float) ((camera.x - center.x) / SCALE),
                (float) ((camera.y - center.y) / SCALE),
                (float) ((camera.z - center.z) / SCALE));
        inverse.transform(camLocal);
        for (int[] pair : links.pairs()) {
            GoldbergGrid.Cell a = grid.cell(pair[0]);
            GoldbergGrid.Cell b = grid.cell(pair[1]);
            float dotA = (float) (a.x() * camLocal.x + a.y() * camLocal.y + a.z() * camLocal.z);
            float dotB = (float) (b.x() * camLocal.x + b.y() * camLocal.y + b.z() * camLocal.z);
            float minDot = Math.min(dotA, dotB);
            if (minDot <= 1.0f) {
                continue; // a cell at or past the horizon: the whole current is gone
            }
            float fade = Math.min(1.0f, (minDot - 1.0f) / 0.35f);
            rotated.set((float) (a.x() + b.x()), (float) (a.y() + b.y()), (float) (a.z() + b.z()))
                    .normalize(LIFT * SCALE);
            orientation.transform(rotated);
            double dx = center.x + rotated.x - camera.x;
            double dy = center.y + rotated.y - camera.y;
            double dz = center.z + rotated.z - camera.z;
            LateHolograms.enqueue(dx * dx + dy * dy + dz * dz,
                    buffer -> drawCurrent(buffer, pose, grid, links, pair, solved, breath, time, fade));
        }
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
            SphereLinks links, int[] pair, boolean solved, double breath, double time,
            float fade) {
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
        ribbon(buffer, pose, grid.cell(from), grid.cell(to), c1, c2, 3.8f * widthScale,
                Math.round((solved ? 110 : 70) * fade), time, phase, depth, lift);            // soft glow
        ribbon(buffer, pose, grid.cell(from), grid.cell(to), c1, c2, 1.6f * widthScale,
                Math.round((solved ? 255 : 235) * fade), time, phase, depth, lift + 0.004f);  // bright core
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
        int borderRgb;
        int borderAlpha;
        // Filled cells are opaque, the empty-cell veil see-through — the hologram read.
        // The far side needs no alpha treatment at all: the depth prepass stamped the
        // shell's nearest surface, so back cells depth-fail per pixel (except through
        // gap holes, where seeing the interior far wall is intentional). Borders match
        // the screen: a full-size polygon in the divider color underneath, the fill
        // shrunk by the config's border width on top (coplanar is fine — the color
        // pass has no depth write, and the border layer keeps the occluder crack-free).
        if (endpoint) {
            rgb = SphereColors.colorOf(aspect);
            alpha = 0xFF;
            borderRgb = SphereColors.GOLD; // endpoints wear gold, like the screen
            borderAlpha = 0xEE;
        } else if (aspect != null) {
            rgb = SphereColors.colorOf(aspect);
            alpha = 0xFF;
            borderRgb = SphereColors.CELL_BORDER;
            borderAlpha = 0xFF;
        } else {
            rgb = SphereColors.EMPTY_CELL;
            alpha = 0x60;
            borderRgb = SphereColors.CELL_BORDER;
            borderAlpha = 0x78; // a shade stronger than the veil: a subtle lattice
        }
        if (breath > 0) {
            rgb = SphereColors.blend(rgb, SphereColors.GOLD, breath);
        }

        // Border and fill NEVER overlap: the border is a ring of trapezoids between the
        // full and shrunk outlines, the fill the inner fan only. Layering them coplanar
        // z-fought under the depth prepass — the two layers' interpolated depths differ
        // by float noise, so the color pass lost one or the other pixel by pixel.
        double shrink = SphereColors.cellShrink();
        ring(buffer, pose, cell, shrink, borderRgb, borderAlpha);
        fan(buffer, pose, cell, shrink, rgb, alpha);
    }

    /** The border: trapezoid quads between the cell's full and shrunk outlines. */
    private static void ring(VertexConsumer buffer, Matrix4f pose, GoldbergGrid.Cell cell,
            double shrink, int rgb, int alpha) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        double[][] corners = cell.corners();
        float cx = (float) cell.x();
        float cy = (float) cell.y();
        float cz = (float) cell.z();
        for (int i = 0; i < corners.length; i++) {
            double[] p1 = corners[i];
            double[] p2 = corners[(i + 1) % corners.length];
            buffer.addVertex(pose, (float) p1[0], (float) p1[1], (float) p1[2]).setColor(r, g, b, alpha);
            buffer.addVertex(pose, (float) p2[0], (float) p2[1], (float) p2[2]).setColor(r, g, b, alpha);
            buffer.addVertex(pose, shrunk(p2[0], cx, shrink), shrunk(p2[1], cy, shrink), shrunk(p2[2], cz, shrink))
                    .setColor(r, g, b, alpha);
            buffer.addVertex(pose, shrunk(p1[0], cx, shrink), shrunk(p1[1], cy, shrink), shrunk(p1[2], cz, shrink))
                    .setColor(r, g, b, alpha);
        }
    }

    /**
     * The fill: a center-fan of degenerate quads (QUADS mode; repeating the last corner
     * turns each fan triangle into a valid quad) over the outline pulled toward the
     * cell center by {@code shrink} — it stops exactly where the border ring begins.
     */
    private static void fan(VertexConsumer buffer, Matrix4f pose, GoldbergGrid.Cell cell,
            double shrink, int rgb, int alpha) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        double[][] corners = cell.corners();
        float cx = (float) cell.x();
        float cy = (float) cell.y();
        float cz = (float) cell.z();
        for (int i = 0; i < corners.length; i++) {
            double[] p1 = corners[i];
            double[] p2 = corners[(i + 1) % corners.length];
            buffer.addVertex(pose, cx, cy, cz).setColor(r, g, b, alpha);
            buffer.addVertex(pose, shrunk(p1[0], cx, shrink), shrunk(p1[1], cy, shrink), shrunk(p1[2], cz, shrink))
                    .setColor(r, g, b, alpha);
            buffer.addVertex(pose, shrunk(p2[0], cx, shrink), shrunk(p2[1], cy, shrink), shrunk(p2[2], cz, shrink))
                    .setColor(r, g, b, alpha);
            buffer.addVertex(pose, shrunk(p2[0], cx, shrink), shrunk(p2[1], cy, shrink), shrunk(p2[2], cz, shrink))
                    .setColor(r, g, b, alpha);
        }
    }

    private static float shrunk(double corner, float center, double shrink) {
        return (float) (center + (corner - center) * shrink);
    }

}
