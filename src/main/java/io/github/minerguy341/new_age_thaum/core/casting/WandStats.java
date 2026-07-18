package io.github.minerguy341.new_age_thaum.core.casting;

import net.minecraft.resources.ResourceLocation;

import java.util.Optional;

/**
 * Stats derived from a wand's materials and form (PLAN §4.4: the cap + core matrix).
 * Pure and deterministic, so it is fully unit-testable. The numbers are inert until the
 * casting/aura systems read them — for now they surface on the tooltip.
 */
public record WandStats(double capacity, double discount, double potency,
                        Optional<ResourceLocation> rechargeAffinity) {

    /** Discount is additive across both caps but never exceeds this fraction. */
    public static final double MAX_DISCOUNT = 0.5;

    public static final WandStats EMPTY = new WandStats(0, 0, 0, Optional.empty());

    public static WandStats compute(WandComponent component, WandForm form) {
        WandMaterial core = WandMaterialRegistry.get(component.core()).orElse(null);
        WandMaterial capA = WandMaterialRegistry.get(component.capA()).orElse(null);
        WandMaterial capB = WandMaterialRegistry.get(component.capB()).orElse(null);

        double capacity = core != null && core.isCore() ? core.capacity() * form.capacityMultiplier() : 0;
        double discount = Math.min(MAX_DISCOUNT, capDiscount(capA) + capDiscount(capB));
        double potency = capPotency(capA) + capPotency(capB);
        Optional<ResourceLocation> affinity = core != null && core.isCore() ? core.rechargeAffinity() : Optional.empty();
        return new WandStats(capacity, discount, potency, affinity);
    }

    private static double capDiscount(WandMaterial cap) {
        return cap != null && cap.isCap() ? cap.discount() : 0;
    }

    private static double capPotency(WandMaterial cap) {
        return cap != null && cap.isCap() ? cap.potency() : 0;
    }
}
