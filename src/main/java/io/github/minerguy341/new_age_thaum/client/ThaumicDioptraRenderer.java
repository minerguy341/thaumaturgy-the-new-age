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
 * The dioptra's holographic vis map (TC6 Thaumic Dioptra look): a 13x13-chunk terrain
 * surface sitting in the block's basin, one cell per chunk, height and color driven by
 * that chunk's vis. The surface is a CONTINUOUS mesh — cell corners share a smoothed
 * height, so chunks angle together into ridges and valleys instead of blocky steps — and
 * it spans the FULL block top, so dioptras placed edge to edge form one seamless map with
 * no gap between them. Always on for anyone in range (no Aetherlens gate — the dioptra IS
 * the reading instrument). Position-color quads only (PLAN §5 Iris/Sodium rule), deferred
 * through {@link LateHolograms}. Flux stains cells violet; the block's own chunk cell is
 * tinted white to orient the player and show group tiling.
 */
public class ThaumicDioptraRenderer implements BlockEntityRenderer<ThaumicDioptraBlockEntity> {
    private static final int GRID = ThaumicDioptraBlockEntity.GRID;
    private static final int HALF = DioptraGroup.HALF;
    private static final int LOW_COLOR = 0x246B44;   // starved chunk: still-legible moss green
    private static final int HIGH_COLOR = 0x8CFFC0;  // saturated: bright hologram green
    private static final int TAINT_STAIN = 0x8A3AA0; // flux pollution: creeping violet rot
    /** One cell per chunk, spanning the full block top so adjacent maps meet with no gap. */
    private static final float CELL = 1f / GRID;
    /** Base plane of the terrain — just above the basin rim (block is 15/16 tall). */
    private static final float BASE_Y = 0.98f;
    private static final float MIN_HEIGHT = 0.03f;
    private static final float VAR_HEIGHT = 0.72f;

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
        // Smoothed corner heights: each of the (GRID+1)^2 grid corners averages the vis of
        // the up-to-4 cells touching it, so neighboring chunks share corner heights and the
        // surface angles continuously — no vertical cliffs, no cracks between cells.
        float[][] cornerY = new float[GRID + 1][GRID + 1];
        for (int cx = 0; cx <= GRID; cx++) {
            for (int cz = 0; cz <= GRID; cz++) {
                float sum = 0f;
                int count = 0;
                for (int dx = -1; dx <= 0; dx++) {
                    for (int dz = -1; dz <= 0; dz++) {
                        int ix = cx + dx;
                        int iz = cz + dz;
                        if (ix >= 0 && ix < GRID && iz >= 0 && iz < GRID) {
                            sum += fracs[iz * GRID + ix];
                            count++;
                        }
                    }
                }
                cornerY[cx][cz] = height(count > 0 ? sum / count : 0f);
            }
        }

        // Base plate across the whole basin floor so a zero-vis map still reads as "on".
        quadY(buffer, pose, 0f, 0f, 1f, 1f, BASE_Y, LOW_COLOR, 0x30);

        for (int cz = 0; cz < GRID; cz++) {
            for (int cx = 0; cx < GRID; cx++) {
                int index = cz * GRID + cx;
                float frac = fracs[index];
                int rgb = SphereColors.blend(LOW_COLOR, HIGH_COLOR, frac);
                int alpha = 0x66 + (int) (0x93 * frac);
                // Flux stains the cell toward a taint violet — pollution reads as purple
                // rot creeping over the green vis terrain.
                float flux = fluxFracs[index];
                if (flux > 0f) {
                    rgb = SphereColors.blend(rgb, TAINT_STAIN, Math.min(0.6f, flux * 0.8f));
                    alpha = Math.min(0xFF, alpha + (int) (0x30 * flux));
                }
                if (index == ownIndex) {
                    rgb = SphereColors.blend(rgb, 0xFFFFFF, 0.42);
                    alpha = Math.min(0xFF, alpha + 0x28);
                }

                float x0 = cx * CELL;
                float x1 = x0 + CELL;
                float z0 = cz * CELL;
                float z1 = z0 + CELL;
                // The cell is a single flat-shaded quad warped to its 4 (shared) corner
                // heights — faceted, low-poly terrain that catches the eye at every angle.
                quad(buffer, pose,
                        x0, cornerY[cx][cz], z0,
                        x1, cornerY[cx + 1][cz], z0,
                        x1, cornerY[cx + 1][cz + 1], z1,
                        x0, cornerY[cx][cz + 1], z1,
                        rgb, alpha);

                // Outer rim: skirt the border cells down to the base plane so the map has
                // sides sitting in the basin, not a floating sheet.
                int skirt = Math.max(0x30, alpha - 0x18);
                if (cx == 0) {
                    quadWall(buffer, pose, x0, z0, x0, z1, BASE_Y, cornerY[cx][cz], cornerY[cx][cz + 1], rgb, skirt);
                }
                if (cx == GRID - 1) {
                    quadWall(buffer, pose, x1, z0, x1, z1, BASE_Y, cornerY[cx + 1][cz], cornerY[cx + 1][cz + 1], rgb, skirt);
                }
                if (cz == 0) {
                    quadWall(buffer, pose, x0, z0, x1, z0, BASE_Y, cornerY[cx][cz], cornerY[cx + 1][cz], rgb, skirt);
                }
                if (cz == GRID - 1) {
                    quadWall(buffer, pose, x0, z1, x1, z1, BASE_Y, cornerY[cx][cz + 1], cornerY[cx + 1][cz + 1], rgb, skirt);
                }
            }
        }
    }

    private static float height(float frac) {
        return BASE_Y + MIN_HEIGHT + VAR_HEIGHT * frac;
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

    /** An arbitrary quad through four positioned corners (a warped terrain facet). */
    private static void quad(VertexConsumer buffer, Matrix4f pose,
            float ax, float ay, float az, float bx, float by, float bz,
            float cx, float cy, float cz, float dx, float dy, float dz, int rgb, int alpha) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        buffer.addVertex(pose, ax, ay, az).setColor(r, g, b, alpha);
        buffer.addVertex(pose, bx, by, bz).setColor(r, g, b, alpha);
        buffer.addVertex(pose, cx, cy, cz).setColor(r, g, b, alpha);
        buffer.addVertex(pose, dx, dy, dz).setColor(r, g, b, alpha);
    }

    /** Vertical wall along (ax,az)-(bx,bz), from {@code yBottom} up to two top heights. */
    private static void quadWall(VertexConsumer buffer, Matrix4f pose,
            float ax, float az, float bx, float bz, float yBottom, float yTopA, float yTopB, int rgb, int alpha) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        buffer.addVertex(pose, ax, yBottom, az).setColor(r, g, b, alpha);
        buffer.addVertex(pose, bx, yBottom, bz).setColor(r, g, b, alpha);
        buffer.addVertex(pose, bx, yTopB, bz).setColor(r, g, b, alpha);
        buffer.addVertex(pose, ax, yTopA, az).setColor(r, g, b, alpha);
    }

    @Override
    public int getViewDistance() {
        return 48;
    }
}
