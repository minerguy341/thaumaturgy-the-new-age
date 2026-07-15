package io.github.minerguy341.new_age_thaum.gametest;

import io.github.minerguy341.new_age_thaum.NewAgeThaum;
import io.github.minerguy341.new_age_thaum.core.aspect.AspectRelations;
import io.github.minerguy341.new_age_thaum.core.research.LinkingPuzzle;
import io.github.minerguy341.new_age_thaum.core.research.grid.GoldbergGrid;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;

/**
 * Puzzle rules over the real loaded aspect set. Lumen = Flamma + Ventus, so Lumen is
 * related to each of its components but the two primals are not related to each other —
 * the canonical shape of the adjacency constraint.
 */
//? if neoforge {
@net.neoforged.neoforge.gametest.GameTestHolder(NewAgeThaum.MOD_ID)
@net.neoforged.neoforge.gametest.PrefixGameTestTemplate(false)
//?}
public class LinkingPuzzleGameTest {

    private static ResourceLocation aspect(String path) {
        return ResourceLocation.fromNamespaceAndPath(NewAgeThaum.MOD_ID, path);
    }

    //? if neoforge {
    @GameTest(template = "empty")
    //?} else {
    /*@GameTest(template = "new_age_thaum:empty")
    *///?}
    public void relatednessFollowsComponentGraph(GameTestHelper helper) {
        ResourceLocation lumen = aspect("lumen");
        ResourceLocation flamma = aspect("flamma");
        ResourceLocation ventus = aspect("ventus");

        helper.assertTrue(AspectRelations.related(flamma, flamma), "An aspect is related to itself");
        helper.assertTrue(AspectRelations.related(lumen, flamma), "Lumen is related to its component Flamma");
        helper.assertTrue(AspectRelations.related(ventus, lumen), "Relatedness is symmetric (Ventus/Lumen)");
        helper.assertFalse(AspectRelations.related(flamma, ventus), "Sibling primals are not related");
        helper.succeed();
    }

    //? if neoforge {
    @GameTest(template = "empty")
    //?} else {
    /*@GameTest(template = "new_age_thaum:empty")
    *///?}
    public void adjacentUnrelatedAspectsConflict(GameTestHelper helper) {
        GoldbergGrid grid = GoldbergGrid.generate(1);
        int a = 0;
        int b = grid.cell(a).neighbors()[0];
        LinkingPuzzle puzzle = new LinkingPuzzle(grid, Map.of());

        puzzle.place(a, aspect("flamma"));
        puzzle.place(b, aspect("lumen"));
        helper.assertTrue(puzzle.isValidPlacement(), "Flamma next to its compound Lumen is a valid link");

        puzzle.place(b, aspect("ventus"));
        helper.assertFalse(puzzle.isValidPlacement(), "Flamma next to unrelated Ventus is a conflict");
        helper.assertTrue(puzzle.conflictingCells().contains(a) && puzzle.conflictingCells().contains(b),
                "Both endpoints of the bad link should be flagged");
        helper.succeed();
    }

    //? if neoforge {
    @GameTest(template = "empty")
    //?} else {
    /*@GameTest(template = "new_age_thaum:empty")
    *///?}
    public void filledBoardOfOneAspectIsComplete(GameTestHelper helper) {
        GoldbergGrid grid = GoldbergGrid.generate(1);
        LinkingPuzzle puzzle = new LinkingPuzzle(grid, Map.of());
        helper.assertFalse(puzzle.isComplete(), "An empty board is not complete");

        for (GoldbergGrid.Cell cell : grid.cells()) {
            puzzle.place(cell.index(), aspect("flamma"));
        }
        helper.assertTrue(puzzle.isComplete(), "A board filled with one aspect links to itself everywhere");
        helper.succeed();
    }

    //? if neoforge {
    @GameTest(template = "empty")
    //?} else {
    /*@GameTest(template = "new_age_thaum:empty")
    *///?}
    public void fixedEndpointsCannotBeOverwritten(GameTestHelper helper) {
        GoldbergGrid grid = GoldbergGrid.generate(1);
        LinkingPuzzle puzzle = new LinkingPuzzle(grid, Map.of(0, aspect("flamma")));

        helper.assertFalse(puzzle.place(0, aspect("ventus")), "Placing on a fixed endpoint must be rejected");
        helper.assertTrue(puzzle.aspectAt(0).equals(aspect("flamma")), "The endpoint aspect is unchanged");
        helper.succeed();
    }
}
