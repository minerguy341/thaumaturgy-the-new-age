package io.github.minerguy341.new_age_thaum.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;

/**
 * Render types for the mod's holographic effects. Subclassing {@link RenderType} is the
 * standard no-mixin way to reach the protected state shards; nothing here adds a shader —
 * PLAN.md §5's Iris/Sodium rule (vanilla core shaders only) still holds.
 */
public final class ModRenderTypes extends RenderType {

    /**
     * The mod's own {@code RenderType.debugQuads} twin: vanilla position-color shader,
     * translucent, no-cull, depth test AND depth write, quads sorted far-to-near on
     * upload. The pipeline trick is not the state but the TIMING: this type is only ever
     * flushed by {@link LateHolograms#renderAll()}, AFTER translucent terrain — so water
     * (already drawn) shows through the holograms, while clouds and weather (drawn
     * later) depth-test behind them. A separate instance also keeps the flush ours
     * alone: {@code endBatch(HOLOGRAM)} can't drop someone else's debugQuads batch.
     */
    public static final RenderType HOLOGRAM = create("new_age_thaum_hologram",
            DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 1536, false, true,
            RenderType.CompositeState.builder()
                    .setShaderState(POSITION_COLOR_SHADER)
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setCullState(NO_CULL)
                    .createCompositeState(false));

    private ModRenderTypes(String name, VertexFormat format, VertexFormat.Mode mode, int bufferSize,
            boolean affectsCrumbling, boolean sortOnUpload, Runnable setup, Runnable clear) {
        super(name, format, mode, bufferSize, affectsCrumbling, sortOnUpload, setup, clear);
        throw new AssertionError("shard-access subclass; never instantiated");
    }
}
