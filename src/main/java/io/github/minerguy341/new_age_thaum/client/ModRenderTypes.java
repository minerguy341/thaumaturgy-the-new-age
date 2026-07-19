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
     * Hologram COLOR pass: vanilla position-color shader, translucent, no-cull, depth
     * test on but depth write OFF and no upload sorting — blending follows the
     * renderers' hand-ordered painter's emission (back cells, back currents, front
     * cells, front currents) exactly. Writing depth here let the hologram depth-test
     * against ITSELF: wherever wave-bent ribbon quads or the core/glow layers overlap
     * and the quad sort drew the nearer one first, the rest depth-failed — holes
     * straight through to the sky. Flushed only by {@link LateHolograms#renderAll()},
     * AFTER translucent terrain, so water (already drawn) shows through; the paired
     * {@link #HOLOGRAM_DEPTH} stamp is what keeps later passes (clouds, weather)
     * behind the hologram.
     */
    public static final RenderType HOLOGRAM = create("new_age_thaum_hologram",
            DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 1536, false, false,
            RenderType.CompositeState.builder()
                    .setShaderState(POSITION_COLOR_SHADER)
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setCullState(NO_CULL)
                    .setWriteMaskState(COLOR_WRITE)
                    .createCompositeState(false));

    /**
     * Hologram DEPTH pass: the same quads re-emitted with the color mask off, run after
     * the color pass. Stamps the holograms' silhouette into the depth buffer so clouds
     * and weather — drawn later still — depth-test behind them, without ever letting
     * the color pass fight itself over depth.
     */
    public static final RenderType HOLOGRAM_DEPTH = create("new_age_thaum_hologram_depth",
            DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 1536, false, false,
            RenderType.CompositeState.builder()
                    .setShaderState(POSITION_COLOR_SHADER)
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setCullState(NO_CULL)
                    .setWriteMaskState(DEPTH_WRITE)
                    .createCompositeState(false));

    private ModRenderTypes(String name, VertexFormat format, VertexFormat.Mode mode, int bufferSize,
            boolean affectsCrumbling, boolean sortOnUpload, Runnable setup, Runnable clear) {
        super(name, format, mode, bufferSize, affectsCrumbling, sortOnUpload, setup, clear);
        throw new AssertionError("shard-access subclass; never instantiated");
    }
}
