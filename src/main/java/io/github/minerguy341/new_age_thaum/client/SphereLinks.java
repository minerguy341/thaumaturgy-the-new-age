package io.github.minerguy341.new_age_thaum.client;

import io.github.minerguy341.new_age_thaum.core.aspect.AspectRelations;
import io.github.minerguy341.new_age_thaum.core.research.ResearchPuzzle;
import io.github.minerguy341.new_age_thaum.core.research.grid.GoldbergGrid;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The link web of a research sphere, shared by the two renderers ({@link
 * ResearchSphereScreen} and {@link OrreryHologramRenderer}) so the screen's currents and
 * the hologram's currents agree edge-for-edge: {@code sane} is the placement map with
 * out-of-range cell indices dropped, {@code pairs} the validly linked adjacent cells,
 * and {@code depth} each linked cell's BFS distance from the puzzle's endpoints — the
 * flow direction and pulse phase of the currents.
 */
record SphereLinks(Map<Integer, ResourceLocation> sane, List<int[]> pairs, Map<Integer, Integer> depth) {

    /** {@code placed} must already include the puzzle's endpoint aspects. */
    static SphereLinks compute(GoldbergGrid grid, Map<Integer, ResourceLocation> placed, ResearchPuzzle puzzle) {
        // A crafted/corrupt component can carry out-of-range cell indices; every filled
        // key goes through grid.cell() below (and inside LinkingPuzzle), so drop them
        // here or one bad paper crashes the render loop of every viewer.
        Map<Integer, ResourceLocation> sane = new HashMap<>();
        for (Map.Entry<Integer, ResourceLocation> entry : placed.entrySet()) {
            if (entry.getKey() >= 0 && entry.getKey() < grid.size()) {
                sane.put(entry.getKey(), entry.getValue());
            }
        }

        List<int[]> pairs = new ArrayList<>();
        Map<Integer, List<Integer>> adjacency = new HashMap<>();
        for (Map.Entry<Integer, ResourceLocation> entry : sane.entrySet()) {
            for (int neighbor : grid.cell(entry.getKey()).neighbors()) {
                if (neighbor <= entry.getKey()) {
                    continue; // each edge once
                }
                ResourceLocation there = sane.get(neighbor);
                if (there != null && AspectRelations.related(entry.getValue(), there)) {
                    pairs.add(new int[]{entry.getKey(), neighbor});
                    adjacency.computeIfAbsent(entry.getKey(), k -> new ArrayList<>()).add(neighbor);
                    adjacency.computeIfAbsent(neighbor, k -> new ArrayList<>()).add(entry.getKey());
                }
            }
        }

        // Flow depths: the puzzle's endpoints are the springs — multi-source BFS from
        // every endpoint in the web, so the current visibly flows outward from the fixed
        // cells. Components touching no endpoint fall back to their lowest cell index.
        Map<Integer, Integer> depth = new HashMap<>();
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        if (puzzle != null) {
            for (Integer endpoint : puzzle.endpoints().keySet()) {
                if (adjacency.containsKey(endpoint) && !depth.containsKey(endpoint)) {
                    depth.put(endpoint, 0);
                    queue.add(endpoint);
                }
            }
            drainDepths(queue, depth, adjacency);
        }
        for (Integer start : adjacency.keySet().stream().sorted().toList()) {
            if (!depth.containsKey(start)) {
                depth.put(start, 0);
                queue.add(start);
                drainDepths(queue, depth, adjacency);
            }
        }
        return new SphereLinks(sane, pairs, depth);
    }

    private static void drainDepths(ArrayDeque<Integer> queue, Map<Integer, Integer> depth,
            Map<Integer, List<Integer>> adjacency) {
        while (!queue.isEmpty()) {
            int current = queue.poll();
            for (int next : adjacency.get(current)) {
                if (!depth.containsKey(next)) {
                    depth.put(next, depth.get(current) + 1);
                    queue.add(next);
                }
            }
        }
    }
}
