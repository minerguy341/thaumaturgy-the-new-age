package io.github.minerguy341.new_age_thaum.core.research;

import io.github.minerguy341.new_age_thaum.core.aspect.AspectRelations;
import io.github.minerguy341.new_age_thaum.core.research.grid.GoldbergGrid;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * The research linking puzzle, surface-agnostic (PLAN.md §4.2). Rule model (Jacob,
 * 2026-07-16): adjacency between unrelated aspects is simply NOT a link — it is ignored,
 * not an error. A placed aspect is "linked" when at least one neighbor holds a
 * {@link AspectRelations#related related} aspect; cells with no valid connection are the
 * ones a surface should grey out. The globe renderer and any flat-grid MVP both drive
 * this same model — it holds no UI state.
 */
public final class LinkingPuzzle {
    private final GoldbergGrid grid;
    private final Map<Integer, ResourceLocation> fixed;
    private final Map<Integer, ResourceLocation> placed = new HashMap<>();

    public LinkingPuzzle(GoldbergGrid grid, Map<Integer, ResourceLocation> endpoints) {
        this.grid = grid;
        this.fixed = Map.copyOf(endpoints);
    }

    public GoldbergGrid grid() {
        return grid;
    }

    public boolean isFixed(int cellIndex) {
        return fixed.containsKey(cellIndex);
    }

    public ResourceLocation aspectAt(int cellIndex) {
        ResourceLocation endpoint = fixed.get(cellIndex);
        return endpoint != null ? endpoint : placed.get(cellIndex);
    }

    /** Places an aspect in a non-fixed cell. Returns false if the cell is a locked endpoint. */
    public boolean place(int cellIndex, ResourceLocation aspect) {
        if (fixed.containsKey(cellIndex)) {
            return false;
        }
        placed.put(cellIndex, aspect);
        return true;
    }

    public boolean clear(int cellIndex) {
        return placed.remove(cellIndex) != null;
    }

    /** Filled cells (fixed + placed) that have no valid connection to any neighbor. */
    public Set<Integer> unlinkedCells() {
        return unlinked(grid, combined());
    }

    /** True when every filled cell participates in at least one valid link. */
    public boolean isFullyLinked() {
        return unlinkedCells().isEmpty();
    }

    /** True once every cell on the sphere is filled and fully linked. */
    public boolean isComplete() {
        for (GoldbergGrid.Cell cell : grid.cells()) {
            if (aspectAt(cell.index()) == null) {
                return false;
            }
        }
        return isFullyLinked();
    }

    private Map<Integer, ResourceLocation> combined() {
        Map<Integer, ResourceLocation> combined = new HashMap<>(placed);
        combined.putAll(fixed);
        return combined;
    }

    // --- pure rule API (any surface can consume a raw cell map) -----------------

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
}
