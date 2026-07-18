package io.github.minerguy341.new_age_thaum.core.research;

import io.github.minerguy341.new_age_thaum.core.aspect.Aspect;
import io.github.minerguy341.new_age_thaum.core.aspect.AspectRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A snapshot view of the loaded aspects as an undirected graph whose edges are the
 * derivation steps the linking rules accept (compound ↔ its two components). Provides
 * the generator's math: per-aspect depth (distance from the primal layer) and
 * exact-length walks found by dynamic programming.
 */
public final class AspectGraph {
    private final Map<ResourceLocation, List<ResourceLocation>> neighbors = new HashMap<>();
    private final Map<ResourceLocation, Integer> depth = new HashMap<>();

    private AspectGraph() {
    }

    /** Builds from the current {@link AspectRegistry} contents. */
    public static AspectGraph snapshot() {
        AspectGraph graph = new AspectGraph();
        for (Aspect aspect : AspectRegistry.all()) {
            for (ResourceLocation component : aspect.components()) {
                graph.neighbors.computeIfAbsent(aspect.id(), k -> new ArrayList<>()).add(component);
                graph.neighbors.computeIfAbsent(component, k -> new ArrayList<>()).add(aspect.id());
            }
            graph.neighbors.computeIfAbsent(aspect.id(), k -> new ArrayList<>());
        }
        for (Aspect aspect : AspectRegistry.all()) {
            graph.depth.put(aspect.id(), computeDepth(aspect.id(), new HashSet<>()));
        }
        return graph;
    }

    private static int computeDepth(ResourceLocation id, Set<ResourceLocation> visiting) {
        Aspect aspect = AspectRegistry.get(id).orElse(null);
        if (aspect == null || aspect.isPrimal() || !visiting.add(id)) {
            return 0;
        }
        int max = 0;
        for (ResourceLocation component : aspect.components()) {
            max = Math.max(max, computeDepth(component, visiting));
        }
        visiting.remove(id);
        return max + 1;
    }

    public List<ResourceLocation> neighborsOf(ResourceLocation aspect) {
        return neighbors.getOrDefault(aspect, List.of());
    }

    public int depthOf(ResourceLocation aspect) {
        return depth.getOrDefault(aspect, 0);
    }

    /** All aspects with derivation depth at most {@code maxDepth} and at least one edge. */
    public List<ResourceLocation> aspectsUpToDepth(int maxDepth) {
        List<ResourceLocation> result = new ArrayList<>();
        for (Map.Entry<ResourceLocation, Integer> entry : depth.entrySet()) {
            if (entry.getValue() <= maxDepth && !neighborsOf(entry.getKey()).isEmpty()) {
                result.add(entry.getKey());
            }
        }
        result.sort(java.util.Comparator.comparing(ResourceLocation::toString));
        return result;
    }

    /**
     * Dynamic programming over walk length: {@code reachable[k]} is the set of aspects
     * reachable from {@code start} in exactly {@code k} derivation steps (walks may
     * revisit aspects — only consecutive equality is impossible by construction).
     */
    public List<Set<ResourceLocation>> reachableByStep(ResourceLocation start, int steps) {
        List<Set<ResourceLocation>> layers = new ArrayList<>(steps + 1);
        layers.add(Set.of(start));
        for (int k = 1; k <= steps; k++) {
            Set<ResourceLocation> next = new HashSet<>();
            for (ResourceLocation aspect : layers.get(k - 1)) {
                next.addAll(neighborsOf(aspect));
            }
            layers.add(next);
        }
        return layers;
    }

    /**
     * A uniformly random walk of exactly {@code steps} edges from {@code start} to
     * {@code end}, reconstructed backwards through the DP layers; null if none exists.
     * The walk is the hidden solution chain of a generated path — the guarantee that
     * the puzzle can be completed, discarded after generation.
     */
    public List<ResourceLocation> walk(ResourceLocation start, ResourceLocation end, int steps, RandomSource random) {
        List<Set<ResourceLocation>> layers = reachableByStep(start, steps);
        if (!layers.get(steps).contains(end)) {
            return null;
        }
        List<ResourceLocation> walk = new ArrayList<>();
        walk.add(end);
        ResourceLocation current = end;
        for (int k = steps - 1; k >= 0; k--) {
            List<ResourceLocation> options = new ArrayList<>();
            for (ResourceLocation candidate : neighborsOf(current)) {
                if (layers.get(k).contains(candidate)) {
                    options.add(candidate);
                }
            }
            if (options.isEmpty()) {
                return null;
            }
            current = options.get(random.nextInt(options.size()));
            walk.add(current);
        }
        java.util.Collections.reverse(walk);
        return walk;
    }
}
