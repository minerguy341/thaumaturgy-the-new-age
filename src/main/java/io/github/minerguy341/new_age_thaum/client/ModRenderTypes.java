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
     * {@code RenderType.debugQuads} minus the depth WRITE (depth test stays on): vanilla
     * position-color shader, translucent, no-cull, quads sorted far-to-near on upload.
     * Depth write is what broke the first pass with debugQuads: block entities render
     * before translucent terrain, so holograms stamped their depth and the water surface
     * behind them failed the test — a hole in every lake behind an aura node. It also
     * z-fights any coplanar layers (aura disc stack, current glow under core). Without
     * it, translucency layers purely in emission/sort order and water simply blends
     * over the hologram.
     */
    public static final RenderType HOLOGRAM = create("new_age_thaum_hologram",
            DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 1536, false, true,
            RenderType.CompositeState.builder()
                    .setShaderState(POSITION_COLOR_SHADER)
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setCullState(NO_CULL)
                    .setWriteMaskState(COLOR_WRITE)
                    .createCompositeState(false));

    private ModRenderTypes(String name, VertexFormat format, VertexFormat.Mode mode, int bufferSize,
            boolean affectsCrumbling, boolean sortOnUpload, Runnable setup, Runnable clear) {
        super(name, format, mode, bufferSize, affectsCrumbling, sortOnUpload, setup, clear);
        throw new AssertionError("shard-access subclass; never instantiated");
    }
}
