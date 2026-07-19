package io.github.minerguy341.new_age_thaum.gametest;

import io.github.minerguy341.new_age_thaum.NewAgeThaum;
import io.github.minerguy341.new_age_thaum.core.aspect.AspectRelations;
import io.github.minerguy341.new_age_thaum.core.research.LinkingPuzzle;
import io.github.minerguy341.new_age_thaum.core.research.grid.GoldbergGrid;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.Set;

/**
 * Rule model: unrelated adjacency is ignored (not an error); a filled cell is "linked"
 * iff at least one neighbor holds a related aspect; unlinked cells are what surfaces
 * grey out. Lumen = Flamma + Ventus is the canonical relatedness triple.
 */
//? if neoforge {
@net.neoforged.neoforge.gametest.GameTestHolder(NewAgeThaum.MOD_ID)
@net.neoforged.neoforge.gametest.PrefixGameTestTemplate(false)
//?}
public class LinkingPuzzleGameTest {

    private static ResourceLocation aspect(String path) {
        return NewAgeThaum.id(path);
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

        helper.assertFalse(AspectRelations.related(flamma, flamma),
                "Identical aspects do not link — a link is a derivation step, not repetition");
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
    public void unrelatedNeighborsAreIgnoredNotErrors(GameTestHelper helper) {
        GoldbergGrid grid = GoldbergGrid.generate(1);
        int a = 0;
        int b = grid.cell(a).neighbors()[0];

        // Flamma next to unrelated Ventus: no link either way — both merely unlinked.
        Set<Integer> unlinked = LinkingPuzzle.unlinked(grid, Map.of(a, aspect("flamma"), b, aspect("ventus")));
        helper.assertTrue(unlinked.contains(a) && unlinked.contains(b) && unlinked.size() == 2,
                "Unrelated neighbors should both be unlinked (ignored, not errors), got " + unlinked);

        // Replace Ventus with Lumen: a valid link — both light up.
        helper.assertTrue(LinkingPuzzle.unlinked(grid, Map.of(a, aspect("flamma"), b, aspect("lumen"))).isEmpty(),
                "A compound next to its component links both cells");
        helper.succeed();
    }

    //? if neoforge {
    @GameTest(template = "empty")
    //?} else {
    /*@GameTest(template = "new_age_thaum:empty")
    *///?}
    public void oneValidLinkIsEnough(GameTestHelper helper) {
        // Jacob's scenario: a cell with one unrelated neighbor AND one related neighbor
        // counts as linked; only the cell with no valid connection greys out.
        GoldbergGrid grid = GoldbergGrid.generate(1);
        int a = 0;
        int b = grid.cell(a).neighbors()[0];
        int c = -1;
        for (int candidate : grid.cell(a).neighbors()) {
            boolean adjacentToB = false;
            for (int n : grid.cell(candidate).neighbors()) {
                if (n == b) {
                    adjacentToB = true;
                    break;
                }
            }
            if (candidate != b && !adjacentToB) {
                c = candidate;
                break;
            }
        }
        helper.assertTrue(c >= 0, "Grid should offer a neighbor of a that is not adjacent to b");

        Set<Integer> unlinked = LinkingPuzzle.unlinked(grid,
                Map.of(a, aspect("flamma"), b, aspect("ventus"), c, aspect("lumen")));
        helper.assertTrue(!unlinked.contains(a), "Flamma links via Lumen despite the unrelated Ventus neighbor");
        helper.assertTrue(!unlinked.contains(c), "Lumen links via Flamma");
        helper.assertTrue(unlinked.contains(b), "Ventus has no valid connection and should grey out");
        helper.succeed();
    }

    //? if neoforge {
    @GameTest(template = "empty")
    //?} else {
    /*@GameTest(template = "new_age_thaum:empty")
    *///?}
    public void solitaryAndPredictiveChecks(GameTestHelper helper) {
        GoldbergGrid grid = GoldbergGrid.generate(1);
        int a = 0;
        int b = grid.cell(a).neighbors()[0];

        helper.assertTrue(LinkingPuzzle.unlinked(grid, Map.of(a, aspect("flamma"))).contains(a),
                "A solitary aspect has no valid connection yet");
        helper.assertFalse(LinkingPuzzle.wouldLink(grid, Map.of(b, aspect("ventus")), a, aspect("flamma")),
                "wouldLink is false next to only-unrelated neighbors");
        helper.assertTrue(LinkingPuzzle.wouldLink(grid, Map.of(b, aspect("lumen")), a, aspect("flamma")),
                "wouldLink is true next to a related neighbor");
        helper.succeed();
    }

    //? if neoforge {
    @GameTest(template = "empty")
    //?} else {
    /*@GameTest(template = "new_age_thaum:empty")
    *///?}
    public void sameAspectNeighborsDoNotLink(GameTestHelper helper) {
        GoldbergGrid grid = GoldbergGrid.generate(1);
        int a = 0;
        int b = grid.cell(a).neighbors()[0];

        Set<Integer> unlinked = LinkingPuzzle.unlinked(grid, Map.of(a, aspect("flamma"), b, aspect("flamma")));
        helper.assertTrue(unlinked.contains(a) && unlinked.contains(b),
                "Two adjacent identical aspects must both stay unlinked, got " + unlinked);
        helper.assertFalse(LinkingPuzzle.wouldLink(grid, Map.of(b, aspect("flamma")), a, aspect("flamma")),
                "wouldLink must be false next to the same aspect");

        // A board of one repeated aspect has no derivation links anywhere — every cell
        // stays unlinked. (Endpoint-lock enforcement lives in the server edit path and
        // is covered by OrreryValidationGameTest/PuzzleGeneratorGameTest.)
        Map<Integer, ResourceLocation> board = new java.util.HashMap<>();
        for (GoldbergGrid.Cell cell : grid.cells()) {
            board.put(cell.index(), aspect("flamma"));
        }
        helper.assertTrue(LinkingPuzzle.unlinked(grid, board).size() == grid.size(),
                "A board of one repeated aspect must be entirely unlinked");
        helper.succeed();
    }
}
