package io.github.minerguy341.new_age_thaum.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.minerguy341.new_age_thaum.content.AuraNodeBlockEntity;
import io.github.minerguy341.new_age_thaum.core.ModRegistries;
import io.github.minerguy341.new_age_thaum.core.aura.AuraField;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
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
    private static final float COLUMN_MAX_HEIGHT = 3.0f;
    private static final float COLUMN_HALF_WIDTH = 0.6f;

    public AuraNodeRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(AuraNodeBlockEntity node, float partialTick, PoseStack poseStack,
            MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        long now = Util.getMillis();
        int color = SphereColors.colorOf(node.aspect()); // grey until the server rolls it
        VertexConsumer buffer = bufferSource.getBuffer(RenderType.debugQuads());
        Quaternionf facing = Minecraft.getInstance().gameRenderer.getMainCamera().rotation();

        poseStack.pushPose();
        poseStack.translate(0.5, 0.5, 0.5);
        poseStack.mulPose(facing);
        double seconds = now / 1000.0;
        float pulse = 1.0f + 0.08f * (float) Math.sin(seconds * 2.1);
        float base = 0.28f * (0.7f + 0.3f * node.size());
        // Outer halo breathes, the mid swirl and bright core counter-rotate.
        disc(buffer, poseStack, base * 1.7f * pulse, SphereColors.blend(color, 0xFFFFFF, 0.10), 0x2E,
                (float) (seconds * 0.35));
        disc(buffer, poseStack, base * 1.15f, SphereColors.blend(color, 0xFFFFFF, 0.25), 0x78,
                (float) (seconds * 0.9));
        disc(buffer, poseStack, base * 0.55f * (2.0f - pulse), SphereColors.blend(color, 0xFFFFFF, 0.65), 0xE6,
                (float) (-seconds * 1.6));
        poseStack.popPose();

        if (holdingAetherlens()) {
            renderAuraGrid(node, poseStack, buffer);
        }
    }

    private static boolean holdingAetherlens() {
        LocalPlayer player = Minecraft.getInstance().player;
        return player != null && (player.getMainHandItem().is(ModRegistries.AETHERLENS.get())
                || player.getOffhandItem().is(ModRegistries.AETHERLENS.get()));
    }

    /** Crossed translucent columns over each surrounding chunk's center, height = vis. */
    private static void renderAuraGrid(AuraNodeBlockEntity node, PoseStack poseStack, VertexConsumer buffer) {
        float[] snapshot = node.auraSnapshot();
        BlockPos origin = node.getBlockPos();
        ChunkPos center = new ChunkPos(origin);
        int half = AuraNodeBlockEntity.GRID / 2;

        poseStack.pushPose();
        Matrix4f pose = poseStack.last().pose();
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
                column(buffer, pose, x, y, z, height, rgb, alpha);
            }
        }
        poseStack.popPose();
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

    /** A flat hexagon fan in the billboarded view plane, spun by {@code spin} radians. */
    private static void disc(VertexConsumer buffer, PoseStack poseStack,
            float radius, int rgb, int alpha, float spin) {
        Matrix4f pose = poseStack.last().pose();
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        for (int i = 0; i < 6; i++) {
            float a1 = spin + (float) (i * Math.PI / 3.0);
            float a2 = spin + (float) ((i + 1) * Math.PI / 3.0);
            // Degenerate quads (last vertex repeated) — debugQuads draws QUADS.
            buffer.addVertex(pose, 0, 0, 0).setColor(r, g, b, alpha);
            buffer.addVertex(pose, radius * Mth.cos(a1), radius * Mth.sin(a1), 0).setColor(r, g, b, alpha);
            buffer.addVertex(pose, radius * Mth.cos(a2), radius * Mth.sin(a2), 0).setColor(r, g, b, alpha);
            buffer.addVertex(pose, radius * Mth.cos(a2), radius * Mth.sin(a2), 0).setColor(r, g, b, alpha);
        }
    }

    @Override
    public int getViewDistance() {
        // The visualizer grid spans two chunks outward from the node.
        return 96;
    }
}
