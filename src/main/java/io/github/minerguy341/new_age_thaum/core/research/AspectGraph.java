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
        Set<ResourceLocation> visiting = new HashSet<>();
        for (Aspect aspect : AspectRegistry.all()) {
            graph.computeDepth(aspect.id(), visiting);
        }
        return graph;
    }

    /**
     * Memoized into {@link #depth}: without the memo a diamond-shaped derivation ladder
     * (each layer's compounds built from the previous layer's) is re-explored once per
     * path — O(2^depth) — which a datapack can weaponize into a server hang.
     */
    private int computeDepth(ResourceLocation id, Set<ResourceLocation> visiting) {
        Integer known = depth.get(id);
        if (known != null) {
            return known;
        }
        Aspect aspect = AspectRegistry.get(id).orElse(null);
        if (aspect == null || aspect.isPrimal() || !visiting.add(id)) {
            // Missing/primal/cycle-backedge: depth 0, but only settled results are
            // memoized (a backedge return is not this aspect's true depth).
            if (aspect != null && aspect.isPrimal()) {
                depth.put(id, 0);
            }
            return 0;
        }
        int max = 0;
        for (ResourceLocation component : aspect.components()) {
            max = Math.max(max, computeDepth(component, visiting));
        }
        visiting.remove(id);
        depth.put(id, max + 1);
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
     * As above, but every step lands inside {@code allowed} (null = the whole graph).
     * The generator passes the tier pool so a hidden solution never routes through an
     * aspect deeper than the paper's tier permits — the player must be able to place
     * every intermediate, or "provably solvable" is void for their progression.
     */
    public List<Set<ResourceLocation>> reachableByStep(ResourceLocation start, int steps, Set<ResourceLocation> allowed) {
        List<Set<ResourceLocation>> layers = new ArrayList<>(steps + 1);
        layers.add(Set.of(start));
        for (int k = 1; k <= steps; k++) {
            Set<ResourceLocation> next = new HashSet<>();
            for (ResourceLocation aspect : layers.get(k - 1)) {
                for (ResourceLocation neighbor : neighborsOf(aspect)) {
                    if (allowed == null || allowed.contains(neighbor)) {
                        next.add(neighbor);
                    }
                }
            }
            layers.add(next);
        }
        return layers;
    }

    /** Walk reconstruction over precomputed layers, so callers don't pay the DP twice. */
    public List<ResourceLocation> walk(List<Set<ResourceLocation>> layers, ResourceLocation end, RandomSource random) {
        int steps = layers.size() - 1;
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
