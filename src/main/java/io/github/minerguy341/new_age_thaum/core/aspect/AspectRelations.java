package io.github.minerguy341.new_age_thaum.core.aspect;

import net.minecraft.resources.ResourceLocation;

/**
 * The relatedness rule the research linking puzzle enforces between adjacent cells:
 * two aspects link iff one is a direct component of the other (a compound next to one
 * of the two aspects it is built from). Identical aspects do NOT link (Jacob,
 * 2026-07-16) — a link is a derivation step, not repetition. This walks only the
 * one-step component edges of the aspect graph.
 */
public final class AspectRelations {
    private AspectRelations() {
    }

    public static boolean related(ResourceLocation a, ResourceLocation b) {
        if (a.equals(b)) {
            return false;
        }
        return isComponentOf(a, b) || isComponentOf(b, a);
    }

    /** True if {@code maybeComponent} is one of the two components of the compound {@code compound}. */
    private static boolean isComponentOf(ResourceLocation maybeComponent, ResourceLocation compound) {
        return AspectRegistry.get(compound)
                .map(aspect -> aspect.components().contains(maybeComponent))
                .orElse(false);
    }
}
