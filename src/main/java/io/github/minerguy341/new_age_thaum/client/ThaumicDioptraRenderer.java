package io.github.minerguy341.new_age_thaum.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.minerguy341.new_age_thaum.content.DioptraGroup;
import io.github.minerguy341.new_age_thaum.content.ThaumicDioptraBlockEntity;
import io.github.minerguy341.new_age_thaum.core.aura.AuraField;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import org.joml.Matrix4f;

/**
 * The dioptra's holographic vis map: a 13x13-cell terrain heightmap floating over the
 * pedestal, one cell per chunk of the block's window, cell height and color driven by
 * that chunk's vis. Always on for anyone in range (no Aetherlens gate — the dioptra IS
 * the reading instrument). Position-color quads only (PLAN §5 Iris/Sodium rule); the
 * emission is deferred through {@link LateHolograms} like every other hologram. Cell
 * tops are inset so the dark gaps between them read as the map's grid lines, and
 * height steps between neighboring cells get vertical skirts so the surface reads as
 * stepped terrain. The block's own chunk cell is tinted toward white — it orients the
 * player and makes group tiling visible (each dioptra of a slab highlights a
 * different cell, or none when its own chunk falls outside its window).
 */
public class ThaumicDioptraRenderer implements BlockEntityRenderer<ThaumicDioptraBlockEntity> {
    private static final int GRID = ThaumicDioptraBlockEntity.GRID;
    private static final int HALF = DioptraGroup.HALF;
    private static final int LOW_COLOR = 0x14331F;   // starved chunk: deep moss shadow
    private static final int HIGH_COLOR = 0x66FFAA;  // saturated: bright hologram green
    private static final int TAINT_STAIN = 0x7A2E8A; // flux pollution: creeping violet rot
    /** Map footprint over the block top, block-local units. */
    private static final float FOOT = 0.85f;
    private static final float MARGIN = (1f - FOOT) / 2f;
    private static final float CELL = FOOT / GRID;
    /** Top-quad inset per side; the uncovered strip between tops is the grid line. */
    private static final float INSET = CELL * 0.08f;
    /** Base plane of the hologram, floating just above the pedestal cap. */
    private static final float BASE_Y = 1.08f;
    private static final float MIN_HEIGHT = 0.04f;
    private static final float VAR_HEIGHT = 0.5f;

    public ThaumicDioptraRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(ThaumicDioptraBlockEntity dioptra, float partialTick, PoseStack poseStack,
            MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        // COPY — the emission below is deferred past this BER call (LateHolograms).
        Matrix4f pose = new Matrix4f(poseStack.last().pose());

        var camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        BlockPos origin = dioptra.getBlockPos();
        float toCamX = (float) (camera.getPosition().x - (origin.getX() + 0.5));
        float toCamY = (float) (camera.getPosition().y - (origin.getY() + BASE_Y + 0.3));
        float toCamZ = (float) (camera.getPosition().z - (origin.getZ() + 0.5));
        double distSqr = toCamX * toCamX + toCamY * toCamY + toCamZ * toCamZ;

        // Snapshot the vis and flux fractions at BER time; the draw runs later this frame.
        float[] window = dioptra.visWindow();
        float[] fluxWindow = dioptra.fluxWindow();
        float[] fracs = new float[GRID * GRID];
        float[] fluxFracs = new float[GRID * GRID];
        for (int i = 0; i < fracs.length; i++) {
            fracs[i] = Mth.clamp(window[i] / AuraField.CHUNK_CAP, 0f, 1f);
            fluxFracs[i] = Mth.clamp(fluxWindow[i] / AuraField.FLUX_CAP, 0f, 1f);
        }
        ChunkPos own = new ChunkPos(origin);
        ChunkPos center = dioptra.windowCenter();
        int ownDx = own.x - center.x;
        int ownDz = own.z - center.z;
        int ownIndex = Math.abs(ownDx) <= HALF && Math.abs(ownDz) <= HALF
                ? (ownDz + HALF) * GRID + (ownDx + HALF) : -1;

        // One sort unit: the whole map is compact (< 1 block), one camera distance
        // orders it correctly against other holograms. Not occluding: the map is a
        // stack of translucent layers whose look depends on blending.
        LateHolograms.enqueue(distSqr, buffer -> drawMap(buffer, pose, fracs, fluxFracs, ownIndex));
    }

    private static void drawMap(VertexConsumer buffer, Matrix4f pose, float[] fracs, float[] fluxFracs, int ownIndex) {
        // Faint base plate: a zero-vis map is still visibly "on".
        quadY(buffer, pose, MARGIN, MARGIN, 1f - MARGIN, 1f - MARGIN, BASE_Y, LOW_COLOR, 0x18);

        for (int cz = 0; cz < GRID; cz++) {
            for (int cx = 0; cx < GRID; cx++) {
                int index = cz * GRID + cx;
                float frac = fracs[index];
                float x0 = MARGIN + cx * CELL;
                float z0 = MARGIN + cz * CELL;
                float x1 = x0 + CELL;
                float z1 = z0 + CELL;
                float top = height(frac);
                int rgb = SphereColors.blend(LOW_COLOR, HIGH_COLOR, frac);
                int alpha = 0x38 + (int) (0x70 * frac);
                // Flux stains the cell toward a taint violet — pollution reads as purple
                // rot creeping over the green vis terrain.
                float flux = fluxFracs[index];
                if (flux > 0f) {
                    rgb = SphereColors.blend(rgb, TAINT_STAIN, Math.min(0.75f, flux * 0.85f));
                    alpha = Math.min(0xFF, alpha + (int) (0x40 * flux));
                }
                if (index == ownIndex) {
                    rgb = SphereColors.blend(rgb, 0xFFFFFF, 0.35);
                    alpha = Math.min(0xFF, alpha + 0x30);
                }

                quadY(buffer, pose, x0 + INSET, z0 + INSET, x1 - INSET, z1 - INSET, top, rgb, alpha);

                int skirtAlpha = Math.max(0x18, alpha - 0x18);
                // Interior steps: emit from the higher side only, along the shared edge.
                if (cx + 1 < GRID) {
                    emitStep(buffer, pose, fracs[cz * GRID + cx + 1], top, rgb, skirtAlpha,
                            x1, z0, x1, z1);
                }
                if (cz + 1 < GRID) {
                    emitStep(buffer, pose, fracs[(cz + 1) * GRID + cx], top, rgb, skirtAlpha,
                            x0, z1, x1, z1);
                }
                // Outer rim: skirt down to the base plane for a crisp silhouette.
                int rimAlpha = Math.max(0x14, skirtAlpha - 0x14);
                if (cx == 0) {
                    quadWall(buffer, pose, x0, z0, x0, z1, BASE_Y, top, rgb, rimAlpha);
                }
                if (cx == GRID - 1) {
                    quadWall(buffer, pose, x1, z0, x1, z1, BASE_Y, top, rgb, rimAlpha);
                }
                if (cz == 0) {
                    quadWall(buffer, pose, x0, z0, x1, z0, BASE_Y, top, rgb, rimAlpha);
                }
                if (cz == GRID - 1) {
                    quadWall(buffer, pose, x0, z1, x1, z1, BASE_Y, top, rgb, rimAlpha);
                }
            }
        }
    }

    private static float height(float frac) {
        return BASE_Y + MIN_HEIGHT + VAR_HEIGHT * frac;
    }

    /** Wall from the lower neighbor's top up to this cell's, only if this cell is higher. */
    private static void emitStep(VertexConsumer buffer, Matrix4f pose, float neighborFrac,
            float top, int rgb, int alpha, float ax, float az, float bx, float bz) {
        float neighborTop = height(neighborFrac);
        if (top > neighborTop + 1.0e-4f) {
            quadWall(buffer, pose, ax, az, bx, bz, neighborTop, top, rgb, alpha);
        }
    }

    /** Horizontal quad at height {@code y} spanning (x0,z0)-(x1,z1). No-cull type. */
    private static void quadY(VertexConsumer buffer, Matrix4f pose,
            float x0, float z0, float x1, float z1, float y, int rgb, int alpha) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        buffer.addVertex(pose, x0, y, z0).setColor(r, g, b, alpha);
        buffer.addVertex(pose, x1, y, z0).setColor(r, g, b, alpha);
        buffer.addVertex(pose, x1, y, z1).setColor(r, g, b, alpha);
        buffer.addVertex(pose, x0, y, z1).setColor(r, g, b, alpha);
    }

    /** Vertical quad along the segment (ax,az)-(bx,bz), from y0 up to y1. */
    private static void quadWall(VertexConsumer buffer, Matrix4f pose,
            float ax, float az, float bx, float bz, float y0, float y1, int rgb, int alpha) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        buffer.addVertex(pose, ax, y0, az).setColor(r, g, b, alpha);
        buffer.addVertex(pose, bx, y0, bz).setColor(r, g, b, alpha);
        buffer.addVertex(pose, bx, y1, bz).setColor(r, g, b, alpha);
        buffer.addVertex(pose, ax, y1, az).setColor(r, g, b, alpha);
    }

    @Override
    public int getViewDistance() {
        return 48;
    }
}
