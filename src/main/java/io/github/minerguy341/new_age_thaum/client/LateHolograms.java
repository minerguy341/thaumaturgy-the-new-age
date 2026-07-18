package io.github.minerguy341.new_age_thaum.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Deferred draw queue for the mod's holographic quads. Block-entity renderers run
 * BEFORE translucent terrain, so anything they draw either stamps the depth buffer
 * (holes in the water surface behind an aura node) or skips the depth write and gets
 * overdrawn by everything vanilla renders later (night clouds through the sphere).
 * Instead the renderers do their math at BER time and enqueue the vertex emission here;
 * a per-loader hook (Fabric {@code WorldRenderEvents.AFTER_TRANSLUCENT}, NeoForge
 * {@code RenderLevelStageEvent} at {@code AFTER_TRANSLUCENT_BLOCKS}) drains the queue
 * AFTER water, with depth write on — water shows through the hologram, and the later
 * cloud/weather passes depth-test behind it.
 */
public final class LateHolograms {
    /** Safety valve if a loader hook ever fails to fire: drop quads, don't leak. */
    private static final int MAX_QUEUED = 4096;
    private static final List<Consumer<VertexConsumer>> QUEUE = new ArrayList<>();

    private LateHolograms() {
    }

    /**
     * Called from a BER's render(): the draw runs later in the frame, so capture pose
     * matrices by COPY — the dispatcher's pose stack is reused after the BER returns.
     */
    public static void enqueue(Consumer<VertexConsumer> draw) {
        if (QUEUE.size() < MAX_QUEUED) {
            QUEUE.add(draw);
        }
    }

    /** Drains the frame's queue; called once per frame by the loader render-stage hook. */
    public static void renderAll() {
        if (QUEUE.isEmpty()) {
            return;
        }
        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        VertexConsumer buffer = buffers.getBuffer(ModRenderTypes.HOLOGRAM);
        for (Consumer<VertexConsumer> draw : QUEUE) {
            draw.accept(buffer);
        }
        QUEUE.clear();
        buffers.endBatch(ModRenderTypes.HOLOGRAM);
    }
}
