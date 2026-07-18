package io.github.minerguy341.new_age_thaum.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;

import java.util.ArrayList;
import java.util.Comparator;
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
    private static final List<Entry> QUEUE = new ArrayList<>();

    private record Entry(double distSqr, Consumer<VertexConsumer> draw) {
    }

    private LateHolograms() {
    }

    /**
     * Called from a BER's render(): the draw runs later in the frame, so capture pose
     * matrices by COPY — the dispatcher's pose stack is reused after the BER returns.
     * {@code distSqr} is the hologram's squared distance to the camera; the color pass
     * blends whole holograms far-to-near by it, so a nearer hologram always draws over
     * a farther one regardless of block-entity visit order.
     */
    public static void enqueue(double distSqr, Consumer<VertexConsumer> draw) {
        if (QUEUE.size() < MAX_QUEUED) {
            QUEUE.add(new Entry(distSqr, draw));
        }
    }

    /** Drains the frame's queue; called once per frame by the loader render-stage hook. */
    public static void renderAll() {
        if (QUEUE.isEmpty()) {
            return;
        }
        // Farthest hologram first — per-HOLOGRAM painter's order (per-quad sorting is
        // what caused the self-overlap holes; whole compact objects sort cleanly).
        QUEUE.sort(Comparator.comparingDouble(Entry::distSqr).reversed());
        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        // Color first (no depth write — blends in pure emission order, can't hole
        // itself), then the same quads again as a depth-only silhouette stamp so the
        // later cloud/weather passes stay behind the holograms. Switching buffer types
        // flushes the color batch before the depth batch starts.
        VertexConsumer color = buffers.getBuffer(ModRenderTypes.HOLOGRAM);
        for (Entry entry : QUEUE) {
            entry.draw().accept(color);
        }
        buffers.endBatch(ModRenderTypes.HOLOGRAM);
        VertexConsumer depth = buffers.getBuffer(ModRenderTypes.HOLOGRAM_DEPTH);
        for (Entry entry : QUEUE) {
            entry.draw().accept(depth);
        }
        QUEUE.clear();
        buffers.endBatch(ModRenderTypes.HOLOGRAM_DEPTH);
    }
}
