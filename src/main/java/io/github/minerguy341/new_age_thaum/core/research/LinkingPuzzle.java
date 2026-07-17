package io.github.minerguy341.new_age_thaum.core.research;

import io.github.minerguy341.new_age_thaum.core.aspect.AspectRelations;
import io.github.minerguy341.new_age_thaum.core.research.grid.GoldbergGrid;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * The research linking puzzle, surface-agnostic (PLAN.md §4.2): a {@link GoldbergGrid}
 * seeded with fixed endpoint aspects that the player fills in, where every pair of
 * adjacent filled cells must be {@link AspectRelations#related related}. The globe
 * renderer and the flat-grid MVP both drive this same model — it holds no UI state.
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

    /** True if no two adjacent filled cells hold unrelated aspects. */
    public boolean isValidPlacement() {
        return conflictingCells().isEmpty();
    }

    /** True once every cell is filled and the whole board is conflict-free. */
    public boolean isComplete() {
        for (GoldbergGrid.Cell cell : grid.cells()) {
            if (aspectAt(cell.index()) == null) {
                return false;
            }
        }
        return isValidPlacement();
    }

    /** Every cell that participates in at least one unrelated-adjacent pair. */
    public Set<Integer> conflictingCells() {
        Set<Integer> conflicts = new HashSet<>();
        for (GoldbergGrid.Cell cell : grid.cells()) {
            ResourceLocation here = aspectAt(cell.index());
            if (here == null) {
                continue;
            }
            for (int neighbor : cell.neighbors()) {
                if (neighbor <= cell.index()) {
                    continue; // consider each edge once
                }
                ResourceLocation there = aspectAt(neighbor);
                if (there != null && !AspectRelations.related(here, there)) {
                    conflicts.add(cell.index());
                    conflicts.add(neighbor);
                }
            }
        }
        return conflicts;
    }
}
