package io.github.minerguy341.new_age_thaum.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.minerguy341.new_age_thaum.content.AuraNodeBlockEntity;
import io.github.minerguy341.new_age_thaum.core.ModRegistries;
import io.github.minerguy341.new_age_thaum.core.aura.AuraField;
import io.github.minerguy341.new_age_thaum.core.aura.NodePersonality;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/**
 * The aura node's in-world look, TC4-flavored: a layered, pulsing orb of camera-facing
 * discs in the node's aspect color (no textures, no core shaders — the PLAN §5
 * Iris/Sodium rule), plus a holographic aura visualizer while the local player holds
 * the Aetherlens: a 5x5 grid of columns over the surrounding chunks whose height and
 * color show each chunk's vis, from the snapshot the server refreshes on the block
 * entity.
 */
public class AuraNodeRenderer implements BlockEntityRenderer<AuraNodeBlockEntity> {
    private static final int LOW_COLOR = 0x2A3550;   // starved chunk: dim slate blue
    private static final int HIGH_COLOR = 0x7FE8D8;  // saturated: the magic accent teal
    private static final int TAINT_TINT = 0x50337A;  // tainted node: a sick murky violet
    private static final float COLUMN_MAX_HEIGHT = 3.0f;
    private static final float COLUMN_HALF_WIDTH = 0.6f;

    public AuraNodeRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(AuraNodeBlockEntity node, float partialTick, PoseStack poseStack,
            MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        long now = Util.getMillis();
        int aspectColor = SphereColors.colorOf(node.aspect()); // grey until the server rolls it
        // Personality reads at a glance WITHOUT washing out the aspect hue — the color is
        // what tells you WHICH aspect the node is, so brightness is no longer the main tell.
        // Instead: bright orbs swell, pulse hard and read a touch more opaque; pale ones
        // shrink, calm down, fade and desaturate; hungry ones flicker and spin fast; tainted
        // ones curdle toward a sick violet (a hue shift); pure ones sit a little larger and
        // cleaner. Modifiers are local to this frame's emission.
        NodePersonality nature = node.personality(); // null for a client frame before sync
        float sizeBoost = 1.0f;
        float pulseAmp = 0.08f;
        float spinScale = 1.0f;   // hungry nodes churn faster
        float alphaScale = 1.0f;  // luminosity via opacity, not white-blend — keeps the hue
        float coreGlow = 0f;      // a small extra white on the hot core only
        int hue = aspectColor;    // personality hue shifts (taint violet, pale desaturate)
        if (nature != null) {
            switch (nature) {
                case BRIGHT -> { sizeBoost = 1.25f; pulseAmp = 0.13f; alphaScale = 1.18f; coreGlow = 0.08f; }
                case PALE -> { sizeBoost = 0.78f; pulseAmp = 0.05f; alphaScale = 0.72f;
                        hue = SphereColors.blend(aspectColor, 0x8A8F9C, 0.22); }
                case HUNGRY -> { sizeBoost = 0.90f; pulseAmp = 0.22f; spinScale = 1.8f; }
                case TAINTED -> { hue = SphereColors.blend(aspectColor, TAINT_TINT, 0.55); pulseAmp = 0.10f; }
                case PURE -> { sizeBoost = 1.08f; pulseAmp = 0.09f; alphaScale = 1.10f; coreGlow = 0.10f; }
            }
        }
        final int color = hue;             // final copies for the deferred lambda below
        final float glowCore = coreGlow;
        final float alphaMul = alphaScale;
        final float spinMul = spinScale;
        // Point-at-camera billboard, not screen-aligned: each orb's disc plane must be
        // perpendicular to the line from THIS node to the camera. With the shared
        // camera.rotation() plane, nodes off the view axis tilt relative to their own
        // sight line at close range and their edges/layers clip terrain and each other.
        var camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        BlockPos origin = node.getBlockPos();
        float toCamX = (float) (camera.getPosition().x - (origin.getX() + 0.5));
        float toCamY = (float) (camera.getPosition().y - (origin.getY() + 0.5));
        float toCamZ = (float) (camera.getPosition().z - (origin.getZ() + 0.5));
        float distSqr = toCamX * toCamX + toCamY * toCamY + toCamZ * toCamZ;
        Quaternionf facing;
        if (distSqr > 1.0e-6f) {
            // Roll-free look-at: yaw about Y, then pitch about X, up stays world-up.
            // (rotationTo's shortest-arc solution carries an arbitrary roll that drifts
            // with the view direction — the orb visibly twisted as you walked around it.)
            // Local +Z still ends up pointing away from the camera, so the far-to-near
            // layer depths below hold.
            float yaw = (float) Math.atan2(-toCamX, -toCamZ);
            float pitch = (float) Math.atan2(toCamY, Math.sqrt(toCamX * toCamX + toCamZ * toCamZ));
            facing = new Quaternionf().rotationYXZ(yaw, pitch, 0f);
        } else {
            facing = camera.rotation(); // camera inside the node; any facing works
        }

        poseStack.pushPose();
        poseStack.translate(0.5, 0.5, 0.5);
        poseStack.mulPose(facing);
        // COPIES — the emission below is deferred past this BER call (LateHolograms).
        Matrix4f discPose = new Matrix4f(poseStack.last().pose());
        poseStack.popPose();
        Matrix4f gridPose = holdingAetherlens() ? new Matrix4f(poseStack.last().pose()) : null;

        double seconds = now / 1000.0;
        float pulse = 1.0f + pulseAmp * (float) Math.sin(seconds * 2.1);
        float base = 0.28f * (0.7f + 0.3f * node.size()) * sizeBoost;
        // The orb is one sort unit: the billboarded discs are paper-thin along the view
        // axis, so a single camera distance orders it correctly against other holograms.
        LateHolograms.enqueue(distSqr, buffer -> {
            // Outer halo breathes, the mid swirl and bright core counter-rotate. Each
            // layer sits at its own depth along the local view axis (+Z points away
            // from the camera) so the depth-stamp pass isn't coplanar; blend order is
            // the far-to-near emission order. White-blends are kept low — the halo and
            // swirl stay near full aspect saturation and only the hot core lightens — so
            // the aspect hue reads at a glance. Luminosity differences between
            // personalities ride on alpha (alphaMul), not on bleaching the color.
            disc(buffer, discPose, base * 1.7f * pulse,
                    SphereColors.blend(color, 0xFFFFFF, 0.05), scaleAlpha(0x2E, alphaMul),
                    (float) (seconds * 0.35 * spinMul), 0.04f);
            disc(buffer, discPose, base * 1.15f,
                    SphereColors.blend(color, 0xFFFFFF, 0.16), scaleAlpha(0x78, alphaMul),
                    (float) (seconds * 0.9 * spinMul), 0f);
            disc(buffer, discPose, base * 0.55f * (2.0f - pulse),
                    SphereColors.blend(color, 0xFFFFFF, Mth.clamp(0.40 + glowCore, 0.0, 1.0)), scaleAlpha(0xE6, alphaMul),
                    (float) (-seconds * 1.6 * spinMul), -0.04f);
        });
        if (gridPose != null) {
            enqueueAuraGrid(node, gridPose, toCamX + 0.5f, toCamY + 0.5f, toCamZ + 0.5f);
        }
    }

    private static boolean holdingAetherlens() {
        LocalPlayer player = Minecraft.getInstance().player;
        return player != null && (player.getMainHandItem().is(ModRegistries.AETHERLENS.get())
                || player.getOffhandItem().is(ModRegistries.AETHERLENS.get()));
    }

    /**
     * Crossed translucent columns over each surrounding chunk's center, height = vis.
     * Each column is enqueued as its OWN sort unit at its own camera distance: the grid
     * spans two chunks in every direction, so a single distance for the whole grid
     * would blend columns wrongly against other holograms (and each other).
     */
    private static void enqueueAuraGrid(AuraNodeBlockEntity node, Matrix4f pose,
            float camX, float camY, float camZ) {
        float[] snapshot = node.auraSnapshot();
        BlockPos origin = node.getBlockPos();
        ChunkPos center = new ChunkPos(origin);
        int half = AuraNodeBlockEntity.GRID / 2;

        for (int dz = -half; dz <= half; dz++) {
            for (int dx = -half; dx <= half; dx++) {
                float vis = snapshot[(dz + half) * AuraNodeBlockEntity.GRID + (dx + half)];
                float frac = Mth.clamp(vis / AuraField.CHUNK_CAP, 0f, 1f);
                // Chunk center in node-relative coordinates.
                float x = (((center.x + dx) << 4) + 8) - origin.getX();
                float z = (((center.z + dz) << 4) + 8) - origin.getZ();
                float y = 1.5f;
                float height = 0.25f + COLUMN_MAX_HEIGHT * frac;
                int rgb = SphereColors.blend(LOW_COLOR, HIGH_COLOR, frac);
                int alpha = 0x30 + (int) (0x70 * frac);
                float ddx = x - camX;
                float ddy = y + height * 0.5f - camY;
                float ddz = z - camZ;
                LateHolograms.enqueue(ddx * ddx + ddy * ddy + ddz * ddz,
                        buffer -> column(buffer, pose, x, y, z, height, rgb, alpha));
            }
        }
    }

    /** Two crossed vertical quads — readable from every angle without billboarding. */
    private static void column(VertexConsumer buffer, Matrix4f pose,
            float x, float y, float z, float height, int rgb, int alpha) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        float w = COLUMN_HALF_WIDTH;
        buffer.addVertex(pose, x - w, y, z).setColor(r, g, b, alpha);
        buffer.addVertex(pose, x + w, y, z).setColor(r, g, b, alpha);
        buffer.addVertex(pose, x + w, y + height, z).setColor(r, g, b, alpha);
        buffer.addVertex(pose, x - w, y + height, z).setColor(r, g, b, alpha);
        buffer.addVertex(pose, x, y, z - w).setColor(r, g, b, alpha);
        buffer.addVertex(pose, x, y, z + w).setColor(r, g, b, alpha);
        buffer.addVertex(pose, x, y + height, z + w).setColor(r, g, b, alpha);
        buffer.addVertex(pose, x, y + height, z - w).setColor(r, g, b, alpha);
    }

    /** Scales a base disc alpha by a personality's luminosity multiplier, clamped 0–255. */
    private static int scaleAlpha(int base, float scale) {
        return Mth.clamp(Math.round(base * scale), 0, 255);
    }

    /** A flat hexagon fan in the billboarded view plane, spun by {@code spin} radians. */
    private static void disc(VertexConsumer buffer, Matrix4f pose,
            float radius, int rgb, int alpha, float spin, float depth) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        for (int i = 0; i < 6; i++) {
            float a1 = spin + (float) (i * Math.PI / 3.0);
            float a2 = spin + (float) ((i + 1) * Math.PI / 3.0);
            // Degenerate quads (last vertex repeated) — debugQuads draws QUADS.
            buffer.addVertex(pose, 0, 0, depth).setColor(r, g, b, alpha);
            buffer.addVertex(pose, radius * Mth.cos(a1), radius * Mth.sin(a1), depth).setColor(r, g, b, alpha);
            buffer.addVertex(pose, radius * Mth.cos(a2), radius * Mth.sin(a2), depth).setColor(r, g, b, alpha);
            buffer.addVertex(pose, radius * Mth.cos(a2), radius * Mth.sin(a2), depth).setColor(r, g, b, alpha);
        }
    }

    @Override
    public int getViewDistance() {
        // The visualizer grid spans two chunks outward from the node.
        return 96;
    }
}
