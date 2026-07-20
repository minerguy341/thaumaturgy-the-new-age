package io.github.minerguy341.new_age_thaum.core.casting;

import io.github.minerguy341.new_age_thaum.content.CastingImplementItem;
import io.github.minerguy341.new_age_thaum.core.ModComponents;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

/**
 * Read/write helpers for the vis reservoir stored on a casting implement (the WAND_VIS
 * data component). Kept separate from {@link WandStats} because capacity is derived and
 * immutable, whereas stored vis is mutable per-stack state. All clamping to the stack's
 * capacity happens here so no caller can over-fill a wand.
 */
public final class WandVis {
    private WandVis() {
    }

    /** Current stored vis; 0 if the stack has never been charged (or isn't a wand). */
    public static float get(ItemStack stack) {
        Float v = stack.get(ModComponents.WAND_VIS.get());
        return v == null ? 0f : Math.max(0f, v);
    }

    /** The stack's derived vis capacity (0 for an unassembled or non-wand stack). */
    public static float capacity(ItemStack stack) {
        if (stack.getItem() instanceof CastingImplementItem impl) {
            WandComponent component = CastingImplementItem.componentOf(stack);
            if (component != null) {
                return (float) WandStats.compute(component, impl.form()).capacity();
            }
        }
        return 0f;
    }

    /** Sets stored vis, clamped to [0, capacity]. A capacity of 0 stores nothing. */
    public static void set(ItemStack stack, float value) {
        float cap = capacity(stack);
        if (cap <= 0f) {
            return;
        }
        stack.set(ModComponents.WAND_VIS.get(), Mth.clamp(value, 0f, cap));
    }

    /** Adds (or drains, negative) vis; returns the amount actually applied after clamping. */
    public static float add(ItemStack stack, float delta) {
        float before = get(stack);
        set(stack, before + delta);
        return get(stack) - before;
    }

    /** True when this stack is a wand/stave that can hold vis (assembled, capacity &gt; 0). */
    public static boolean isReservoir(ItemStack stack) {
        return stack.getItem() instanceof CastingImplementItem && capacity(stack) > 0f;
    }
}
