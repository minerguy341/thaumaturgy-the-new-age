package io.github.minerguy341.new_age_thaum.core.research;

import io.github.minerguy341.new_age_thaum.NewAgeThaum;
import io.github.minerguy341.new_age_thaum.content.ResearchPaperItem;
import io.github.minerguy341.new_age_thaum.core.NewAgeThaumConfig;
import io.github.minerguy341.new_age_thaum.core.research.grid.GoldbergGrid;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Generates provably solvable puzzles by building a hidden solution first
 * (m2-gameplay-spec §C): endpoint cells are placed far apart, vertex-disjoint paths are
 * carved between consecutive endpoints, each path is filled with an exact-length walk
 * through the aspect graph (so a valid chain exists), gaps are sprinkled only on cells
 * no solution path uses, and the solution is discarded. Falls back to fewer gaps and
 * endpoints rather than ever producing an unsolvable paper.
 */
public final class PuzzleGenerator {
    private static final Map<Integer, GoldbergGrid> GRIDS = new ConcurrentHashMap<>();
    private static final int ATTEMPTS = 60;
    private static final int MIN_ENDPOINT_DISTANCE = 3;

    /** The puzzle plus its hidden solution — the solution is only used by tests. */
    public record Generated(ResearchPuzzle puzzle, Map<Integer, ResourceLocation> solution) {
    }

    private PuzzleGenerator() {
    }

    public static GoldbergGrid gridFor(int frequency) {
        return GRIDS.computeIfAbsent(frequency, GoldbergGrid::generate);
    }

    public static ResearchPuzzle generate(ResearchPaperItem.Tier tier, RandomSource random) {
        return generateWithSolution(tier, random).puzzle();
    }

    public static Generated generateWithSolution(ResearchPaperItem.Tier tier, RandomSource random) {
        int frequency = NewAgeThaumConfig.tierScaledSpheres ? tier.scaledFrequency : 3;
        GoldbergGrid grid = gridFor(frequency);
        AspectGraph aspects = AspectGraph.snapshot();
        List<ResourceLocation> pool = aspects.aspectsUpToDepth(tier.maxAspectDepth);

        if (!pool.isEmpty()) {
            for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
                Generated result = tryGenerate(grid, aspects, pool, tier.endpointCount, tier.gapFraction, frequency, random);
                if (result != null) {
                    return result;
                }
            }
            // Fallback: same sphere, minimal difficulty — solvable beats faithful.
            NewAgeThaum.LOGGER.warn("Puzzle generation fell back to minimal parameters for tier {}", tier);
            for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
                Generated result = tryGenerate(grid, aspects, pool, 2, 0.0, frequency, random);
                if (result != null) {
                    return result;
                }
            }
        }
        // Last resort (empty registry or pathological grid): a derivation pair on
        // adjacent cells — identical aspects never link, so the pair must be a compound
        // and its component. With no relations at all, a lone endpoint is still solvable.
        NewAgeThaum.LOGGER.error("Puzzle generation exhausted attempts; issuing trivial puzzle");
        Map<Integer, ResourceLocation> endpoints = new HashMap<>();
        ResourceLocation related = null;
        ResourceLocation partner = null;
        for (ResourceLocation candidate : pool) {
            List<ResourceLocation> linked = aspects.neighborsOf(candidate);
            if (!linked.isEmpty()) {
                related = candidate;
                partner = linked.get(0);
                break;
            }
        }
        if (related != null) {
            endpoints.put(0, related);
            endpoints.put(grid.cell(0).neighbors()[0], partner);
        } else {
            endpoints.put(0, pool.isEmpty()
                    ? ResourceLocation.fromNamespaceAndPath(NewAgeThaum.MOD_ID, "flamma") : pool.get(0));
        }
        return new Generated(new ResearchPuzzle(frequency, endpoints, Set.of()), Map.copyOf(endpoints));
    }

    private static Generated tryGenerate(GoldbergGrid grid, AspectGraph aspects, List<ResourceLocation> pool,
            int endpointCount, double gapFraction, int frequency, RandomSource random) {
        // 1. Endpoint cells, pairwise at least MIN_ENDPOINT_DISTANCE apart.
        List<Integer> endpointCells = pickEndpointCells(grid, endpointCount, random);
        if (endpointCells == null) {
            return null;
        }

        // 2. Vertex-disjoint paths chaining the endpoints in order.
        Set<Integer> used = new HashSet<>(endpointCells);
        List<List<Integer>> cellPaths = new ArrayList<>();
        for (int i = 0; i + 1 < endpointCells.size(); i++) {
            int from = endpointCells.get(i);
            int to = endpointCells.get(i + 1);
            Set<Integer> blocked = new HashSet<>(used);
            blocked.remove(from);
            blocked.remove(to);
            List<Integer> path = bfsPath(grid, from, to, blocked);
            if (path == null) {
                return null;
            }
            used.addAll(path);
            cellPaths.add(path);
        }

        // 3. Aspect chains: walk the aspect graph for exactly each path's length.
        Map<Integer, ResourceLocation> solution = new HashMap<>();
        Map<Integer, ResourceLocation> endpoints = new HashMap<>();
        ResourceLocation first = pool.get(random.nextInt(pool.size()));
        endpoints.put(endpointCells.get(0), first);
        solution.put(endpointCells.get(0), first);
        for (int i = 0; i < cellPaths.size(); i++) {
            List<Integer> path = cellPaths.get(i);
            int steps = path.size() - 1;
            ResourceLocation from = endpoints.get(path.get(0));
            Set<ResourceLocation> reachable = aspects.reachableByStep(from, steps).get(steps);
            List<ResourceLocation> targets = new ArrayList<>();
            for (ResourceLocation candidate : pool) {
                if (reachable.contains(candidate)) {
                    targets.add(candidate);
                }
            }
            if (targets.isEmpty()) {
                return null;
            }
            ResourceLocation to = targets.get(random.nextInt(targets.size()));
            List<ResourceLocation> walk = aspects.walk(from, to, steps, random);
            if (walk == null) {
                return null;
            }
            for (int j = 0; j < path.size(); j++) {
                solution.put(path.get(j), walk.get(j));
            }
            endpoints.put(path.get(path.size() - 1), to);
        }

        // 4. Gaps only where no solution cell sits.
        List<Integer> gapCandidates = new ArrayList<>();
        for (GoldbergGrid.Cell cell : grid.cells()) {
            if (!used.contains(cell.index())) {
                gapCandidates.add(cell.index());
            }
        }
        shuffle(gapCandidates, random);
        int gapCount = Math.min(gapCandidates.size(), (int) Math.floor(grid.size() * gapFraction));
        Set<Integer> gaps = new HashSet<>(gapCandidates.subList(0, gapCount));

        return new Generated(new ResearchPuzzle(frequency, endpoints, gaps), solution);
    }

    private static List<Integer> pickEndpointCells(GoldbergGrid grid, int count, RandomSource random) {
        List<Integer> all = new ArrayList<>();
        for (int i = 0; i < grid.size(); i++) {
            all.add(i);
        }
        shuffle(all, random);
        List<Integer> chosen = new ArrayList<>();
        for (int candidate : all) {
            boolean farEnough = true;
            for (int existing : chosen) {
                if (sphereDistance(grid, candidate, existing) < MIN_ENDPOINT_DISTANCE) {
                    farEnough = false;
                    break;
                }
            }
            if (farEnough) {
                chosen.add(candidate);
                if (chosen.size() == count) {
                    return chosen;
                }
            }
        }
        return null;
    }

    private static int sphereDistance(GoldbergGrid grid, int from, int to) {
        if (from == to) {
            return 0;
        }
        Map<Integer, Integer> distance = new HashMap<>();
        distance.put(from, 0);
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        queue.add(from);
        while (!queue.isEmpty()) {
            int current = queue.poll();
            for (int next : grid.cell(current).neighbors()) {
                if (!distance.containsKey(next)) {
                    distance.put(next, distance.get(current) + 1);
                    if (next == to) {
                        return distance.get(next);
                    }
                    queue.add(next);
                }
            }
        }
        return Integer.MAX_VALUE;
    }

    /** Shortest path from {@code from} to {@code to} avoiding {@code blocked}; includes both ends. */
    private static List<Integer> bfsPath(GoldbergGrid grid, int from, int to, Set<Integer> blocked) {
        Map<Integer, Integer> parent = new HashMap<>();
        parent.put(from, from);
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        queue.add(from);
        while (!queue.isEmpty()) {
            int current = queue.poll();
            if (current == to) {
                List<Integer> path = new ArrayList<>();
                for (int at = to; at != from; at = parent.get(at)) {
                    path.add(at);
                }
                path.add(from);
                Collections.reverse(path);
                return path;
            }
            for (int next : grid.cell(current).neighbors()) {
                if (!parent.containsKey(next) && !blocked.contains(next)) {
                    parent.put(next, current);
                    queue.add(next);
                }
            }
        }
        return null;
    }

    private static void shuffle(List<Integer> list, RandomSource random) {
        for (int i = list.size() - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            Collections.swap(list, i, j);
        }
    }
}
