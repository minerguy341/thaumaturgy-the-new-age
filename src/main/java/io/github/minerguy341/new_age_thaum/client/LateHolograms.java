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

    private record Entry(double distSqr, boolean occludes, Consumer<VertexConsumer> draw) {
    }

    private LateHolograms() {
    }

    /** As {@link #enqueue(double, boolean, Consumer)} with {@code occludes = false}. */
    public static void enqueue(double distSqr, Consumer<VertexConsumer> draw) {
        enqueue(distSqr, false, draw);
    }

    /**
     * Called from a BER's render(): the draw runs later in the frame, so capture pose
     * matrices by COPY — the dispatcher's pose stack is reused after the BER returns.
     * {@code distSqr} is the geometry's squared distance to the camera; the color pass
     * blends entries far-to-near by it, so nearer always draws over farther regardless
     * of block-entity visit order. {@code occludes} opts the geometry into the depth
     * PREPASS: its nearest surface then hides everything behind it — its own far side
     * included — per pixel. Use it for closed shells (the orrery sphere); leave it off
     * for effects whose look depends on stacked layers blending (the aura orb's discs).
     */
    public static void enqueue(double distSqr, boolean occludes, Consumer<VertexConsumer> draw) {
        if (QUEUE.size() < MAX_QUEUED) {
            QUEUE.add(new Entry(distSqr, occludes, draw));
        }
    }

    /** Drains the frame's queue; called once per frame by the loader render-stage hook. */
    public static void renderAll() {
        if (QUEUE.isEmpty()) {
            return;
        }
        // Farthest first — per-ENTRY painter's order for the color blend. (Per-quad
        // sorting caused the earlier self-overlap holes; compact entries sort cleanly.)
        QUEUE.sort(Comparator.comparingDouble(Entry::distSqr).reversed());
        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();

        // Pass 1 — depth PREPASS of self-occluding geometry: stamps each shell's
        // nearest surface so the color pass's depth test culls its far side per pixel.
        // This replaces geometric back-face culling/fading outright: occlusion is a
        // rendered result, so the front stays full-strength to a crisp silhouette.
        VertexConsumer prepass = buffers.getBuffer(ModRenderTypes.HOLOGRAM_DEPTH);
        for (Entry entry : QUEUE) {
            if (entry.occludes()) {
                entry.draw().accept(prepass);
            }
        }
        buffers.endBatch(ModRenderTypes.HOLOGRAM_DEPTH);

        // Pass 2 — color, no depth write: blends in sorted order over the already-drawn
        // world; the prepass depth hides occluded-shell backsides and anything behind a
        // stamped shell. Water/terrain/sky still show through (they're already drawn).
        VertexConsumer color = buffers.getBuffer(ModRenderTypes.HOLOGRAM);
        for (Entry entry : QUEUE) {
            entry.draw().accept(color);
        }
        buffers.endBatch(ModRenderTypes.HOLOGRAM);

        // Pass 3 — depth stamp for the NON-prepassed geometry (orb discs, currents,
        // columns), so the later cloud/weather passes stay behind every hologram; the
        // prepassed shells are already in the depth buffer.
        VertexConsumer depth = buffers.getBuffer(ModRenderTypes.HOLOGRAM_DEPTH);
        for (Entry entry : QUEUE) {
            if (!entry.occludes()) {
                entry.draw().accept(depth);
            }
        }
        QUEUE.clear();
        buffers.endBatch(ModRenderTypes.HOLOGRAM_DEPTH);
    }
}
