package io.github.minerguy341.new_age_thaum.core.casting;

import io.github.minerguy341.new_age_thaum.content.CastingImplementItem;
import io.github.minerguy341.new_age_thaum.core.ModComponents;
import io.github.minerguy341.new_age_thaum.core.aspect.AspectBag;
import io.github.minerguy341.new_age_thaum.core.aspect.Primals;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * Read/write helpers for the per-primal vis reservoir stored on a casting implement (the
 * WAND_VIS {@link AspectBag} component). Capacity is a single derived number
 * ({@link WandStats#capacity()}) applied as the per-primal cap, so a wand holds up to that
 * much of EACH primal. All clamping lives here so no caller can over-fill a wand.
 */
public final class WandVis {
    private WandVis() {
    }

    /** The full per-primal reservoir; empty bag if never charged (or not a wand). */
    public static AspectBag get(ItemStack stack) {
        AspectBag bag = stack.get(ModComponents.WAND_VIS.get());
        return bag == null ? AspectBag.EMPTY : bag;
    }

    /** Stored vis of one primal, 0 if none. */
    public static int amountOf(ItemStack stack, ResourceLocation primal) {
        return get(stack).amountOf(primal);
    }

    /** The per-primal capacity (0 for an unassembled or non-wand stack). */
    public static int capacity(ItemStack stack) {
        if (stack.getItem() instanceof CastingImplementItem impl) {
            WandComponent component = CastingImplementItem.componentOf(stack);
            if (component != null) {
                return (int) Math.round(WandStats.compute(component, impl.form()).capacity());
            }
        }
        return 0;
    }

    /** Sets one primal's stored vis, clamped to [0, capacity]. */
    public static void set(ItemStack stack, ResourceLocation primal, int value) {
        int cap = capacity(stack);
        if (cap <= 0) {
            return;
        }
        int clamped = Math.max(0, Math.min(value, cap));
        stack.set(ModComponents.WAND_VIS.get(), get(stack).with(primal, clamped));
    }

    /** Adds (or drains, negative) one primal's vis; returns the amount actually applied. */
    public static int add(ItemStack stack, ResourceLocation primal, int delta) {
        int before = amountOf(stack, primal);
        set(stack, primal, before + delta);
        return amountOf(stack, primal) - before;
    }

    /**
     * Tops every primal up by {@code amount} (clamped per primal). Used by the aura-node
     * charge — a testing simplification (real per-aspect sourcing is a follow-up). Returns the
     * largest single-primal gain, so the caller knows whether anything was actually added.
     */
    public static int chargeAll(ItemStack stack, int amount) {
        int cap = capacity(stack);
        if (cap <= 0 || amount <= 0) {
            return 0;
        }
        AspectBag bag = get(stack);
        int maxGain = 0;
        for (ResourceLocation primal : Primals.LIST) {
            int before = bag.amountOf(primal);
            int next = Math.min(cap, before + amount);
            bag = bag.with(primal, next);
            maxGain = Math.max(maxGain, next - before);
        }
        stack.set(ModComponents.WAND_VIS.get(), bag);
        return maxGain;
    }

    /** True when this stack is a wand/stave that can hold vis (assembled, capacity &gt; 0). */
    public static boolean isReservoir(ItemStack stack) {
        return stack.getItem() instanceof CastingImplementItem && capacity(stack) > 0;
    }
}
