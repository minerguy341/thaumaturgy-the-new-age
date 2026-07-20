package io.github.minerguy341.new_age_thaum.core.aspect;

import io.github.minerguy341.new_age_thaum.NewAgeThaum;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * The six primal aspects — the vis currencies a wand collects and an arcane recipe spends.
 * Fixed order (used for the worktable's aspect ring and its data-slot layout) with each
 * primal's canonical colour (mirrors the datapack {@code aspects/*.json}). These six are the
 * datapack aspects with no components; listing them here is a UI/vis convenience, not a
 * second source of truth for the aspect graph itself.
 */
public final class Primals {
    public static final ResourceLocation VENTUS = NewAgeThaum.id("ventus");
    public static final ResourceLocation TELLUS = NewAgeThaum.id("tellus");
    public static final ResourceLocation FLAMMA = NewAgeThaum.id("flamma");
    public static final ResourceLocation UNDA = NewAgeThaum.id("unda");
    public static final ResourceLocation FORMA = NewAgeThaum.id("forma");
    public static final ResourceLocation DISCORDIA = NewAgeThaum.id("discordia");

    /** Ring/display order (also the worktable data-slot order). */
    public static final List<ResourceLocation> LIST = List.of(VENTUS, TELLUS, FLAMMA, UNDA, FORMA, DISCORDIA);

    /** Canonical colours, index-aligned with {@link #LIST}. */
    public static final int[] COLORS = {0xCDE8F5, 0x6BA84F, 0xF0552B, 0x3D9BE0, 0xEDE9DC, 0x4A3459};

    public static final int COUNT = 6;

    private Primals() {
    }

    /** The GUI glyph texture for the primal at ring index {@code i}. */
    public static ResourceLocation glyph(int i) {
        return NewAgeThaum.id("textures/gui/aspect/" + LIST.get(i).getPath() + ".png");
    }
}
