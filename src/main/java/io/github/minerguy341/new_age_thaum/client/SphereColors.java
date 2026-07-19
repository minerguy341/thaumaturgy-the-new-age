package io.github.minerguy341.new_age_thaum.client;

import io.github.minerguy341.new_age_thaum.core.NewAgeThaumConfig;
import io.github.minerguy341.new_age_thaum.core.aspect.Aspect;
import io.github.minerguy341.new_age_thaum.core.aspect.AspectRegistry;
import net.minecraft.resources.ResourceLocation;

/**
 * Shared colors and color math for the two sphere renderers ({@link ResearchSphereScreen}
 * and {@link OrreryHologramRenderer}) — they draw the SAME sphere and must agree, so the
 * palette and helpers live once here instead of as parallel literals.
 */
final class SphereColors {
    /** Fill of a cell with nothing painted on it. */
    static final int EMPTY_CELL = 0x2A2438;
    /** Endpoint/seal gold (also the Grandmaster tier tint). */
    static final int GOLD = 0xE8C86A;
    /** Fallback for unknown aspect ids. */
    static final int UNKNOWN = 0x888888;
    /** The dark divider between cells (the screen's sphere backdrop color). */
    static final int CELL_BORDER = 0x0B0713;

    /**
     * Fill shrink derived from the configurable border width (1.0 config = classic
     * 0.86) — both renderers shrink cell fills by this toward the cell center so the
     * border shows between them.
     */
    static double cellShrink() {
        double width = NewAgeThaumConfig.cellBorderWidth;
        return Math.max(0.5, Math.min(1.0, 1.0 - 0.14 * width));
    }

    private SphereColors() {
    }

    static int colorOf(ResourceLocation aspectId) {
        return aspectId == null ? UNKNOWN
                : AspectRegistry.get(aspectId).map(Aspect::color).orElse(UNKNOWN);
    }

    static int blend(int from, int to, double factor) {
        int r = (int) (((from >> 16) & 0xFF) * (1 - factor) + ((to >> 16) & 0xFF) * factor);
        int g = (int) (((from >> 8) & 0xFF) * (1 - factor) + ((to >> 8) & 0xFF) * factor);
        int b = (int) ((from & 0xFF) * (1 - factor) + (to & 0xFF) * factor);
        return (r << 16) | (g << 8) | b;
    }

    /** Gold-breathing intensity once a puzzle is solved; 0 when unsolved. */
    static double solvedBreath(boolean solved, long millis) {
        return solved ? 0.30 + 0.20 * Math.sin(millis / 1000.0 * 2.4) : 0;
    }

    /** Wavelength of the currents' travelling pulse, measured in links of the chain. */
    private static final double GLINT_WAVELENGTH = 2.6;

    /**
     * A bright pulse travelling in a current's flow direction. The wave lives in global
     * chain coordinates (depth + t), so a pulse leaves one link exactly as it enters the
     * next — a continuous relay along the whole web. No per-link jitter here.
     * In custom color mode the pulse itself grades pulseFrom→pulseTo with intensity.
     */
    static int glinted(int rgb, double t, double time, double speed, int chainDepth) {
        double s = (chainDepth + t) / GLINT_WAVELENGTH;
        double wave = Math.sin(2 * Math.PI * (s - time * speed * 0.5));
        double strength = Math.pow(Math.max(0, wave), 3);
        if (strength <= 0) {
            return rgb;
        }
        int pulseColor;
        if (NewAgeThaumConfig.customCurrentColors()) {
            pulseColor = blend(NewAgeThaumConfig.currentPulseFrom,
                    NewAgeThaumConfig.currentPulseTo, strength);
        } else {
            pulseColor = 0xFFFFFF;
        }
        return blend(rgb, pulseColor, strength * 0.55);
    }
}
