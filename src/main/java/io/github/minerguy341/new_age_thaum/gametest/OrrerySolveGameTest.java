package io.github.minerguy341.new_age_thaum.gametest;

import io.github.minerguy341.new_age_thaum.NewAgeThaum;
import io.github.minerguy341.new_age_thaum.content.ArcaneOrreryBlockEntity;
import io.github.minerguy341.new_age_thaum.core.ModComponents;
import io.github.minerguy341.new_age_thaum.core.ModRegistries;
import io.github.minerguy341.new_age_thaum.core.aspect.AspectBag;
import io.github.minerguy341.new_age_thaum.core.player.PlayerProgressService;
import io.github.minerguy341.new_age_thaum.core.research.AspectGraph;
import io.github.minerguy341.new_age_thaum.core.research.PuzzleGenerator;
import io.github.minerguy341.new_age_thaum.core.research.ResearchPuzzle;
import io.github.minerguy341.new_age_thaum.core.research.grid.GoldbergGrid;
import io.github.minerguy341.new_age_thaum.network.NewAgeThaumNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The solved condition end to end (m2-gameplay-spec §D): joining every endpoint into one
 * linked web through the real server edit path flips the puzzle to solved and seals the
 * paper against all further edits.
 */
//? if neoforge {
@net.neoforged.neoforge.gametest.GameTestHolder(NewAgeThaum.MOD_ID)
@net.neoforged.neoforge.gametest.PrefixGameTestTemplate(false)
//?}
public class OrrerySolveGameTest {

    //? if neoforge {
    @GameTest(template = "empty")
    //?} else {
    /*@GameTest(template = "new_age_thaum:empty")
    *///?}
    public void joiningEndpointsSealsThePaper(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();

        // A derivation pair from the live registry: `end` at both endpoints, `bridge`
        // (related to it) placed between them closes the circuit.
        AspectGraph graph = AspectGraph.snapshot();
        ResourceLocation end = null;
        ResourceLocation bridge = null;
        for (ResourceLocation candidate : graph.aspectsUpToDepth(Integer.MAX_VALUE)) {
            List<ResourceLocation> related = graph.neighborsOf(candidate);
            if (!related.isEmpty()) {
                end = candidate;
                bridge = related.get(0);
                break;
            }
        }
        helper.assertTrue(end != null, "The aspect registry should offer at least one derivation pair");

        // Three cells in a row: endpoint, empty bridge cell, endpoint.
        GoldbergGrid grid = PuzzleGenerator.gridFor(3);
        int cellA = 0;
        int cellB = grid.cell(cellA).neighbors()[0];
        int cellC = -1;
        for (int neighbor : grid.cell(cellB).neighbors()) {
            if (neighbor != cellA) {
                cellC = neighbor;
                break;
            }
        }

        ItemStack paper = new ItemStack(ModRegistries.PAPER_FLEDGLING.get());
        paper.set(ModComponents.RESEARCH_PUZZLE.get(),
                new ResearchPuzzle(3, Map.of(cellA, end, cellC, end), Set.of()));
        ArcaneOrreryBlockEntity orrery = new ArcaneOrreryBlockEntity(BlockPos.ZERO,
                ModRegistries.ARCANE_ORRERY.get().defaultBlockState());
        orrery.setPaper(paper);
        PlayerProgressService.scan(player, "test/solve", new AspectBag(Map.of(bridge, 5)));

        helper.assertFalse(orrery.puzzle().orElseThrow().solved(), "A fresh puzzle starts unsolved");

        helper.assertTrue(NewAgeThaumNetwork.applyOrreryEdit(player, orrery, cellB, Optional.of(bridge)),
                "The bridging placement should be accepted");
        helper.assertTrue(orrery.puzzle().orElseThrow().solved(),
                "Joining every endpoint must seal the paper as solved");

        // Sealed means sealed: no new placements, no clears, and no points charged.
        int pointsAfterSolve = PlayerProgressService.get(player).points(bridge);
        helper.assertFalse(NewAgeThaumNetwork.applyOrreryEdit(player, orrery, cellB, Optional.empty()),
                "A solved paper rejects clears");
        helper.assertFalse(NewAgeThaumNetwork.applyOrreryEdit(player, orrery, 30, Optional.of(bridge)),
                "A solved paper rejects new placements");
        helper.assertTrue(PlayerProgressService.get(player).points(bridge) == pointsAfterSolve,
                "Rejected edits on a sealed paper must not charge points");

        // The research travels with the paper, solved state included.
        ItemStack taken = orrery.removeItemNoUpdate(0);
        ResearchPuzzle carried = taken.get(ModComponents.RESEARCH_PUZZLE.get());
        helper.assertTrue(carried != null && carried.solved(), "The removed paper stays sealed");
        helper.succeed();
    }
}
