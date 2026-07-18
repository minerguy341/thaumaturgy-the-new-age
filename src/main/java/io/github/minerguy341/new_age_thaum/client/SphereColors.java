package io.github.minerguy341.new_age_thaum.client;

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
}
