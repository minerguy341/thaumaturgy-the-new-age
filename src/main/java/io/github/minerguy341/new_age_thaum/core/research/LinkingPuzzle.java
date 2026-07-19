package io.github.minerguy341.new_age_thaum.core.research;

import io.github.minerguy341.new_age_thaum.core.aspect.AspectRelations;
import io.github.minerguy341.new_age_thaum.core.research.grid.GoldbergGrid;
import net.minecraft.resources.ResourceLocation;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * The research linking-puzzle rules, surface-agnostic (PLAN.md §4.2). Rule model (Jacob,
 * 2026-07-16): adjacency between unrelated aspects is simply NOT a link — it is ignored,
 * not an error. A placed aspect is "linked" when at least one neighbor holds a
 * {@link AspectRelations#related related} aspect; cells with no valid connection are the
 * ones a surface should grey out. Pure static rule API over raw cell maps: the screen,
 * the hologram, and the server solve-check all consume it; state (placements, endpoint
 * locks) lives on the paper's components and in the server edit handler, never here.
 */
public final class LinkingPuzzle {
    private LinkingPuzzle() {
    }

    /**
     * Every filled cell of {@code cells} with no related filled neighbor — including
     * solitary cells with no filled neighbors at all. Unrelated adjacencies are ignored.
     */
    public static Set<Integer> unlinked(GoldbergGrid grid, Map<Integer, ResourceLocation> cells) {
        Set<Integer> unlinked = new HashSet<>();
        for (Map.Entry<Integer, ResourceLocation> entry : cells.entrySet()) {
            if (!isLinked(grid, cells, entry.getKey(), entry.getValue())) {
                unlinked.add(entry.getKey());
            }
        }
        return unlinked;
    }

    /** Would placing {@code aspect} at {@code cellIndex} immediately have a valid link? */
    public static boolean wouldLink(GoldbergGrid grid, Map<Integer, ResourceLocation> cells,
            int cellIndex, ResourceLocation aspect) {
        return isLinked(grid, cells, cellIndex, aspect);
    }

    private static boolean isLinked(GoldbergGrid grid, Map<Integer, ResourceLocation> cells,
            int cellIndex, ResourceLocation aspect) {
        for (int neighbor : grid.cell(cellIndex).neighbors()) {
            ResourceLocation there = cells.get(neighbor);
            if (there != null && AspectRelations.related(aspect, there)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The puzzle-solved condition: every endpoint cell belongs to one connected web of
     * valid links. BFS over the link graph of {@code cells} from the first endpoint.
     */
    public static boolean allEndpointsLinked(GoldbergGrid grid, Map<Integer, ResourceLocation> cells,
            Set<Integer> endpointCells) {
        if (endpointCells.size() < 2) {
            return !endpointCells.isEmpty() && cells.keySet().containsAll(endpointCells);
        }
        Integer start = endpointCells.iterator().next();
        if (!cells.containsKey(start)) {
            return false;
        }
        Set<Integer> reached = new HashSet<>();
        reached.add(start);
        java.util.ArrayDeque<Integer> queue = new java.util.ArrayDeque<>();
        queue.add(start);
        while (!queue.isEmpty()) {
            int current = queue.poll();
            ResourceLocation here = cells.get(current);
            for (int neighbor : grid.cell(current).neighbors()) {
                ResourceLocation there = cells.get(neighbor);
                if (there != null && !reached.contains(neighbor) && AspectRelations.related(here, there)) {
                    reached.add(neighbor);
                    queue.add(neighbor);
                }
            }
        }
        return reached.containsAll(endpointCells);
    }
}
