package io.github.minerguy341.new_age_thaum.core.aspect;

import io.github.minerguy341.new_age_thaum.NewAgeThaum;
import net.minecraft.resources.ResourceLocation;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The six primal aspects in canonical display order (the TC element order: air, earth,
 * fire, water, order, entropy). Wand vis storage and the HUD iterate this list so both
 * sides agree on slots; the registry stays the authority on colors and existence.
 * A datapack-added seventh primal would not gain a wand slot — deliberate for now.
 */
public final class Primals {
    public static final ResourceLocation VENTUS = NewAgeThaum.id("ventus");
    public static final ResourceLocation TELLUS = NewAgeThaum.id("tellus");
    public static final ResourceLocation FLAMMA = NewAgeThaum.id("flamma");
    public static final ResourceLocation UNDA = NewAgeThaum.id("unda");
    public static final ResourceLocation FORMA = NewAgeThaum.id("forma");
    public static final ResourceLocation DISCORDIA = NewAgeThaum.id("discordia");

    public static final List<ResourceLocation> ORDER = List.of(VENTUS, TELLUS, FLAMMA, UNDA, FORMA, DISCORDIA);

    private Primals() {
    }

    /** The 16×16 GUI glyph texture for the primal at ORDER index {@code i} (worktable ring). */
    public static ResourceLocation glyph(int i) {
        return NewAgeThaum.id("textures/gui/aspect/" + ORDER.get(i).getPath() + ".png");
    }

    /**
     * The primal leaves of an aspect's composition tree (the aspect itself if primal).
     * Wand cores name a compound recharge affinity (greatwood: silva); the recharge
     * floor boost applies to the primals it decomposes into. Bounded and cycle-safe
     * even against a malformed registry.
     */
    public static Set<ResourceLocation> primalsOf(ResourceLocation aspectId) {
        Set<ResourceLocation> primals = new HashSet<>();
        collect(aspectId, primals, new HashSet<>(), 0);
        return primals;
    }

    private static void collect(ResourceLocation id, Set<ResourceLocation> primals,
            Set<ResourceLocation> visited, int depth) {
        if (depth > 16 || !visited.add(id)) {
            return;
        }
        Optional<Aspect> aspect = AspectRegistry.get(id);
        if (aspect.isEmpty()) {
            return;
        }
        if (aspect.get().isPrimal()) {
            primals.add(id);
            return;
        }
        for (ResourceLocation component : aspect.get().components()) {
            collect(component, primals, visited, depth + 1);
        }
    }
}
