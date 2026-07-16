package io.github.minerguy341.new_age_thaum.core.casting;

/**
 * The two casting-implement form factors (PLAN §4.4). A stave is the larger build —
 * more vis capacity for the same core — assembled from two rods instead of one.
 */
public enum WandForm {
    WAND(1.0),
    STAVE(1.6);

    private final double capacityMultiplier;

    WandForm(double capacityMultiplier) {
        this.capacityMultiplier = capacityMultiplier;
    }

    public double capacityMultiplier() {
        return capacityMultiplier;
    }
}
