package io.github.minerguy341.new_age_thaum.gametest;

import io.github.minerguy341.new_age_thaum.NewAgeThaum;
import io.github.minerguy341.new_age_thaum.core.research.grid.GoldbergGrid;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * Topology invariants for the Goldberg grid, checked against the known formulas for
 * a class-I Goldberg polyhedron GP(f,0): F = 10f²+2 faces, E = 30f² edges, exactly
 * 12 pentagons, every other cell a hexagon. These hold or they don't — no client needed.
 */
//? if neoforge {
@net.neoforged.neoforge.gametest.GameTestHolder(NewAgeThaum.MOD_ID)
@net.neoforged.neoforge.gametest.PrefixGameTestTemplate(false)
//?}
public class GoldbergGridGameTest {

    //? if neoforge {
    @GameTest(template = "empty")
    //?} else {
    /*@GameTest(template = "new_age_thaum:empty")
    *///?}
    public void gridInvariantsHoldForFrequencies1To4(GameTestHelper helper) {
        for (int f = 1; f <= 4; f++) {
            GoldbergGrid grid = GoldbergGrid.generate(f);

            int expectedCells = 10 * f * f + 2;
            helper.assertTrue(grid.size() == expectedCells,
                    "f=" + f + " expected " + expectedCells + " cells, got " + grid.size());

            int pentagons = 0;
            long degreeSum = 0;
            for (GoldbergGrid.Cell cell : grid.cells()) {
                int degree = cell.neighbors().length;
                degreeSum += degree;

                if (cell.pentagon()) {
                    pentagons++;
                    helper.assertTrue(degree == 5, "Pentagon " + cell.index() + " must have 5 neighbors, got " + degree);
                } else {
                    helper.assertTrue(degree == 6, "Hexagon " + cell.index() + " must have 6 neighbors, got " + degree);
                }

                for (int neighbor : cell.neighbors()) {
                    helper.assertTrue(neighbor != cell.index(), "Cell " + cell.index() + " is its own neighbor");
                    helper.assertTrue(isNeighborOf(grid, neighbor, cell.index()),
                            "Adjacency not symmetric between " + cell.index() + " and " + neighbor);
                }
            }

            helper.assertTrue(pentagons == 12, "f=" + f + " must have exactly 12 pentagons, got " + pentagons);
            helper.assertTrue(degreeSum / 2 == 30L * f * f,
                    "f=" + f + " expected " + (30L * f * f) + " edges, got " + (degreeSum / 2));
        }
        helper.succeed();
    }

    private static boolean isNeighborOf(GoldbergGrid grid, int cellIndex, int wanted) {
        for (int neighbor : grid.cell(cellIndex).neighbors()) {
            if (neighbor == wanted) {
                return true;
            }
        }
        return false;
    }

    //? if neoforge {
    @GameTest(template = "empty")
    //?} else {
    /*@GameTest(template = "new_age_thaum:empty")
    *///?}
    public void gridIsFullyConnected(GameTestHelper helper) {
        // A breadth-first walk from any cell must reach every cell — a subdivision bug
        // could leave a locally-valid but disconnected island, which the count/degree
        // invariants alone would not catch.
        for (int f = 1; f <= 4; f++) {
            GoldbergGrid grid = GoldbergGrid.generate(f);
            boolean[] seen = new boolean[grid.size()];
            java.util.ArrayDeque<Integer> queue = new java.util.ArrayDeque<>();
            seen[0] = true;
            queue.add(0);
            int reached = 1;
            while (!queue.isEmpty()) {
                for (int neighbor : grid.cell(queue.poll()).neighbors()) {
                    if (!seen[neighbor]) {
                        seen[neighbor] = true;
                        reached++;
                        queue.add(neighbor);
                    }
                }
            }
            helper.assertTrue(reached == grid.size(),
                    "f=" + f + " grid is disconnected: reached " + reached + " of " + grid.size() + " cells");
        }
        helper.succeed();
    }
}
