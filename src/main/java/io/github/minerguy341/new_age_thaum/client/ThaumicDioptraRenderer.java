package io.github.minerguy341.new_age_thaum.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.minerguy341.new_age_thaum.content.DioptraGroup;
import io.github.minerguy341.new_age_thaum.content.ThaumicDioptraBlock;
import io.github.minerguy341.new_age_thaum.content.ThaumicDioptraBlockEntity;
import io.github.minerguy341.new_age_thaum.core.aura.AuraField;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import org.joml.Matrix4f;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The dioptra's holographic vis map (TC6 look): a terrain surface sitting in the block's
 * basin, one cell per chunk, height and color driven by that chunk's vis. Cell corners
 * share a smoothed height, so chunks angle together into ridges and valleys.
 *
 * <p><b>One hologram per group.</b> A run of adjacent dioptras renders as a SINGLE draw
 * from one block (the group's min-corner "leader"); every other member draws nothing.
 * The leader assembles all members' windows into one combined heightmap — smoothed
 * across the seams, with skirts only on the group's outer silhouette — and enqueues it
 * as one sort unit. That removes the per-dioptra translucent sort fighting that made
 * adjacent maps flicker at their seams, and makes a slab read as one continuous map.
 */
public class ThaumicDioptraRenderer implements BlockEntityRenderer<ThaumicDioptraBlockEntity> {
    private static final int GRID = ThaumicDioptraBlockEntity.GRID;
    private static final int HALF = DioptraGroup.HALF;
    /** Chebyshev cap on the client-side group scan; mirrors DioptraGroup's server cap. */
    private static final int MAX_REACH = 7;
    private static final int LOW_COLOR = 0x246B44;   // starved chunk: still-legible moss green
    private static final int HIGH_COLOR = 0x8CFFC0;  // saturated: bright hologram green
    private static final int TAINT_STAIN = 0x8A3AA0; // flux pollution: creeping violet rot
    /** One cell per chunk, spanning the full block top so adjacent maps meet with no gap. */
    private static final float CELL = 1f / GRID;
    /** Base plane of the terrain — sits at the 15px block top (1px above the basin floor). */
    private static final float BASE_Y = 0.9375f;
    private static final float MIN_HEIGHT = 0.03f;
    private static final float VAR_HEIGHT = 0.72f;
    /**
     * How far the hologram stays visible. Because the BER is global (see
     * {@link #shouldRenderOffScreen}), vanilla's frustum and distance culls no longer
     * gate it, so this is the only distance bound — we apply it by hand below.
     */
    private static final int VIEW_DISTANCE = 64;
    private static final double VIEW_SQR = (double) VIEW_DISTANCE * VIEW_DISTANCE;

    public ThaumicDioptraRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(ThaumicDioptraBlockEntity dioptra, float partialTick, PoseStack poseStack,
            MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        Level level = dioptra.getLevel();
        if (level == null) {
            return;
        }
        BlockPos self = dioptra.getBlockPos();
        // Global BEs skip vanilla's distance cull, so bound it ourselves — otherwise every
        // loaded dioptra in the dimension would rebuild its combined mesh each frame.
        var cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        double dcx = cam.x - (self.getX() + 0.5);
        double dcy = cam.y - (self.getY() + 0.5);
        double dcz = cam.z - (self.getZ() + 0.5);
        if (dcx * dcx + dcy * dcy + dcz * dcz > VIEW_SQR) {
            return;
        }
        // Fast bail: the leader is the group's min (x, then z) member, which never has a
        // dioptra to its west or north. Every other block returns here after two reads.
        if (isDioptra(level, self.getX() - 1, self.getY(), self.getZ())
                || isDioptra(level, self.getX(), self.getY(), self.getZ() - 1)) {
            return;
        }
        List<BlockPos> members = discover(level, self);
        // Confirm this really is the lex-min member (an L/U shape can have two NW corners;
        // both scan the same group here, but only the true min renders it).
        BlockPos leader = self;
        for (BlockPos member : members) {
            if (member.getX() < leader.getX()
                    || (member.getX() == leader.getX() && member.getZ() < leader.getZ())) {
                leader = member;
            }
        }
        if (!leader.equals(self)) {
            return;
        }

        // Combined grid over the group's bounding box, in cells (GRID per block).
        int minX = self.getX();
        int minZ = self.getZ();
        int maxX = self.getX();
        int maxZ = self.getZ();
        for (BlockPos member : members) {
            minX = Math.min(minX, member.getX());
            minZ = Math.min(minZ, member.getZ());
            maxX = Math.max(maxX, member.getX());
            maxZ = Math.max(maxZ, member.getZ());
        }
        int blocksW = maxX - minX + 1;
        int blocksD = maxZ - minZ + 1;
        int gw = blocksW * GRID;
        int gd = blocksD * GRID;
        float[] fracs = new float[gw * gd];
        float[] fluxFracs = new float[gw * gd];
        boolean[] present = new boolean[gw * gd];
        Set<Integer> ownCells = new HashSet<>();
        for (BlockPos member : members) {
            if (!(level.getBlockEntity(member) instanceof ThaumicDioptraBlockEntity be)) {
                continue;
            }
            int obx = (member.getX() - minX) * GRID;
            int obz = (member.getZ() - minZ) * GRID;
            float[] vis = be.visWindow();
            float[] flux = be.fluxWindow();
            for (int cz = 0; cz < GRID; cz++) {
                for (int cx = 0; cx < GRID; cx++) {
                    int gi = (obz + cz) * gw + (obx + cx);
                    fracs[gi] = Mth.clamp(vis[cz * GRID + cx] / AuraField.CHUNK_CAP, 0f, 1f);
                    fluxFracs[gi] = Mth.clamp(flux[cz * GRID + cx] / AuraField.FLUX_CAP, 0f, 1f);
                    present[gi] = true;
                }
            }
            // This member's own chunk highlights inside its own window.
            ChunkPos own = new ChunkPos(member);
            ChunkPos center = be.windowCenter();
            int odx = own.x - center.x;
            int odz = own.z - center.z;
            if (Math.abs(odx) <= HALF && Math.abs(odz) <= HALF) {
                ownCells.add((obz + odz + HALF) * gw + (obx + odx + HALF));
            }
        }

        // Pose is at the leader block's origin; the group extends in +x/+z, so local
        // coords span [0, blocksW] x [0, blocksD], matching the world footprint.
        Matrix4f pose = new Matrix4f(poseStack.last().pose());
        double toCamX = cam.x - (self.getX() + blocksW * 0.5);
        double toCamY = cam.y - (self.getY() + BASE_Y + 0.3);
        double toCamZ = cam.z - (self.getZ() + blocksD * 0.5);
        double distSqr = toCamX * toCamX + toCamY * toCamY + toCamZ * toCamZ;

        LateHolograms.enqueue(distSqr, buffer -> drawMap(buffer, pose, gw, gd, fracs, fluxFracs, present, ownCells));
    }

    private static void drawMap(VertexConsumer buffer, Matrix4f pose, int gw, int gd,
            float[] fracs, float[] fluxFracs, boolean[] present, Set<Integer> ownCells) {
        // Smoothed corner heights over the whole combined grid — averaging across seams
        // so a slab is one continuous surface, not tiled sheets.
        float[][] cornerY = new float[gw + 1][gd + 1];
        for (int cx = 0; cx <= gw; cx++) {
            for (int cz = 0; cz <= gd; cz++) {
                float sum = 0f;
                int count = 0;
                for (int dx = -1; dx <= 0; dx++) {
                    for (int dz = -1; dz <= 0; dz++) {
                        int ix = cx + dx;
                        int iz = cz + dz;
                        if (ix >= 0 && ix < gw && iz >= 0 && iz < gd && present[iz * gw + ix]) {
                            sum += fracs[iz * gw + ix];
                            count++;
                        }
                    }
                }
                cornerY[cx][cz] = height(count > 0 ? sum / count : 0f);
            }
        }

        for (int cz = 0; cz < gd; cz++) {
            for (int cx = 0; cx < gw; cx++) {
                int index = cz * gw + cx;
                if (!present[index]) {
                    continue;
                }
                float frac = fracs[index];
                int rgb = SphereColors.blend(LOW_COLOR, HIGH_COLOR, frac);
                int alpha = 0x66 + (int) (0x93 * frac);
                float flux = fluxFracs[index];
                if (flux > 0f) {
                    rgb = SphereColors.blend(rgb, TAINT_STAIN, Math.min(0.6f, flux * 0.8f));
                    alpha = Math.min(0xFF, alpha + (int) (0x30 * flux));
                }
                if (ownCells.contains(index)) {
                    rgb = SphereColors.blend(rgb, 0xFFFFFF, 0.42);
                    alpha = Math.min(0xFF, alpha + 0x28);
                }

                float x0 = cx * CELL;
                float x1 = x0 + CELL;
                float z0 = cz * CELL;
                float z1 = z0 + CELL;
                quad(buffer, pose,
                        x0, cornerY[cx][cz], z0,
                        x1, cornerY[cx + 1][cz], z0,
                        x1, cornerY[cx + 1][cz + 1], z1,
                        x0, cornerY[cx][cz + 1], z1,
                        rgb, alpha);

                // Skirt only where the map's silhouette actually is — an interior seam
                // (a present neighbor) gets no skirt, so no coplanar double-draw.
                int skirt = Math.max(0x30, alpha - 0x18);
                if (cx == 0 || !present[cz * gw + (cx - 1)]) {
                    quadWall(buffer, pose, x0, z0, x0, z1, BASE_Y, cornerY[cx][cz], cornerY[cx][cz + 1], rgb, skirt);
                }
                if (cx == gw - 1 || !present[cz * gw + (cx + 1)]) {
                    quadWall(buffer, pose, x1, z0, x1, z1, BASE_Y, cornerY[cx + 1][cz], cornerY[cx + 1][cz + 1], rgb, skirt);
                }
                if (cz == 0 || !present[(cz - 1) * gw + cx]) {
                    quadWall(buffer, pose, x0, z0, x1, z0, BASE_Y, cornerY[cx][cz], cornerY[cx + 1][cz], rgb, skirt);
                }
                if (cz == gd - 1 || !present[(cz + 1) * gw + cx]) {
                    quadWall(buffer, pose, x0, z1, x1, z1, BASE_Y, cornerY[cx][cz + 1], cornerY[cx + 1][cz + 1], rgb, skirt);
                }
            }
        }
    }

    private static boolean isDioptra(Level level, int x, int y, int z) {
        return level.getBlockState(new BlockPos(x, y, z)).getBlock() instanceof ThaumicDioptraBlock;
    }

    /** 4-way flood over same-Y dioptra blocks, bounded by {@link #MAX_REACH} from origin. */
    private static List<BlockPos> discover(Level level, BlockPos origin) {
        List<BlockPos> members = new ArrayList<>();
        Set<BlockPos> visited = new HashSet<>();
        Deque<BlockPos> frontier = new ArrayDeque<>();
        visited.add(origin);
        frontier.add(origin);
        while (!frontier.isEmpty()) {
            BlockPos pos = frontier.poll();
            if (!(level.getBlockState(pos).getBlock() instanceof ThaumicDioptraBlock)) {
                continue;
            }
            members.add(pos);
            for (int[] step : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                BlockPos next = pos.offset(step[0], 0, step[1]);
                if (Math.max(Math.abs(next.getX() - origin.getX()), Math.abs(next.getZ() - origin.getZ())) <= MAX_REACH
                        && visited.add(next)) {
                    frontier.add(next);
                }
            }
        }
        return members;
    }

    private static float height(float frac) {
        return BASE_Y + MIN_HEIGHT + VAR_HEIGHT * frac;
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
        return VIEW_DISTANCE;
    }

    /**
     * Render regardless of whether the anchor block's chunk section is in the camera
     * frustum. The hologram floats above the block and spans a whole group, so tying its
     * visibility to the single anchor section made it blink out when that section left
     * frame even though the projection was still in view. Treating it as a global (beacon
     * -style) BE keeps it drawn from any angle; the distance guard in {@link #render} and
     * the leader check keep that from being expensive.
     */
    @Override
    public boolean shouldRenderOffScreen(ThaumicDioptraBlockEntity blockEntity) {
        return true;
    }
}
