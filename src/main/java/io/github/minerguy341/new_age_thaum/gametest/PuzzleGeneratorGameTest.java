package io.github.minerguy341.new_age_thaum.gametest;

import io.github.minerguy341.new_age_thaum.NewAgeThaum;
import io.github.minerguy341.new_age_thaum.content.ArcaneOrreryBlockEntity;
import io.github.minerguy341.new_age_thaum.content.ResearchPaperItem;
import io.github.minerguy341.new_age_thaum.core.ModComponents;
import io.github.minerguy341.new_age_thaum.core.ModRegistries;
import io.github.minerguy341.new_age_thaum.core.NewAgeThaumConfig;
import io.github.minerguy341.new_age_thaum.core.aspect.AspectBag;
import io.github.minerguy341.new_age_thaum.core.player.PlayerProgressService;
import io.github.minerguy341.new_age_thaum.core.research.LinkingPuzzle;
import io.github.minerguy341.new_age_thaum.core.research.PuzzleGenerator;
import io.github.minerguy341.new_age_thaum.core.research.ResearchPuzzle;
import io.github.minerguy341.new_age_thaum.core.research.grid.GoldbergGrid;
import io.github.minerguy341.new_age_thaum.network.NewAgeThaumNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The solvability guarantee (m2-gameplay-spec §C): every generated puzzle must carry a
 * hidden solution that actually links all endpoints, gaps must never sit on an endpoint
 * or on the solution, and the server edit path must lock endpoint/gap/out-of-range cells.
 */
//? if neoforge {
@net.neoforged.neoforge.gametest.GameTestHolder(NewAgeThaum.MOD_ID)
@net.neoforged.neoforge.gametest.PrefixGameTestTemplate(false)
//?}
public class PuzzleGeneratorGameTest {

    private static final int RUNS_PER_TIER = 6;

    //? if neoforge {
    @GameTest(template = "empty")
    //?} else {
    /*@GameTest(template = "new_age_thaum:empty")
    *///?}
    public void everyTierGeneratesSolvablePuzzles(GameTestHelper helper) {
        RandomSource random = RandomSource.create(20260717L);
        for (ResearchPaperItem.Tier tier : ResearchPaperItem.Tier.values()) {
            int expectedFrequency = NewAgeThaumConfig.tierScaledSpheres ? tier.scaledFrequency : 3;
            for (int run = 0; run < RUNS_PER_TIER; run++) {
                PuzzleGenerator.Generated generated = PuzzleGenerator.generateWithSolution(tier, random);
                ResearchPuzzle puzzle = generated.puzzle();
                Map<Integer, ResourceLocation> solution = generated.solution();
                GoldbergGrid grid = PuzzleGenerator.gridFor(puzzle.frequency());
                String label = tier + " run " + run + ": ";

                helper.assertTrue(puzzle.frequency() == expectedFrequency,
                        label + "frequency should be " + expectedFrequency + ", got " + puzzle.frequency());
                helper.assertTrue(puzzle.endpoints().size() >= 2,
                        label + "at least two endpoints, got " + puzzle.endpoints().size());
                for (int cell : puzzle.endpoints().keySet()) {
                    helper.assertTrue(cell >= 0 && cell < grid.size(), label + "endpoint cell in range");
                }

                // Gaps never on an endpoint and never on the hidden solution.
                for (int gap : puzzle.gaps()) {
                    helper.assertTrue(gap >= 0 && gap < grid.size(), label + "gap cell in range");
                    helper.assertFalse(puzzle.isEndpoint(gap), label + "gap " + gap + " sits on an endpoint");
                    helper.assertFalse(solution.containsKey(gap), label + "gap " + gap + " sits on the solution");
                }

                // The solution includes every endpoint with its fixed aspect …
                for (Map.Entry<Integer, ResourceLocation> endpoint : puzzle.endpoints().entrySet()) {
                    helper.assertTrue(endpoint.getValue().equals(solution.get(endpoint.getKey())),
                            label + "solution disagrees with endpoint " + endpoint.getKey());
                }
                // … and replaying it links all endpoints: the puzzle is solvable as shipped.
                helper.assertTrue(LinkingPuzzle.allEndpointsLinked(grid, solution, puzzle.endpoints().keySet()),
                        label + "solution does not link all endpoints");

                // Component codec round trip (what the paper persists).
                var encoded = ResearchPuzzle.CODEC.encodeStart(NbtOps.INSTANCE, puzzle)
                        .getOrThrow(message -> new IllegalStateException(label + message));
                ResearchPuzzle decoded = ResearchPuzzle.CODEC.parse(NbtOps.INSTANCE, encoded)
                        .getOrThrow(message -> new IllegalStateException(label + message));
                helper.assertTrue(puzzle.equals(decoded), label + "codec round trip changed the puzzle");
            }
        }
        helper.succeed();
    }

    //? if neoforge {
    @GameTest(template = "empty")
    //?} else {
    /*@GameTest(template = "new_age_thaum:empty")
    *///?}
    public void insertingPaperStampsPuzzleOnce(GameTestHelper helper) {
        ArcaneOrreryBlockEntity orrery = new ArcaneOrreryBlockEntity(BlockPos.ZERO,
                ModRegistries.ARCANE_ORRERY.get().defaultBlockState());
        orrery.setLevel(helper.getLevel());

        orrery.setPaper(new ItemStack(ModRegistries.PAPER_SCHOLAR.get()));
        Optional<ResearchPuzzle> stamped = orrery.puzzle();
        helper.assertTrue(stamped.isPresent(), "Inserting a tier paper server-side must stamp a puzzle");
        helper.assertTrue(stamped.get().endpoints().size() >= 2, "Stamped puzzle should carry endpoints");

        // Re-inserting the same paper keeps the puzzle it already has.
        ItemStack taken = orrery.removeItemNoUpdate(0);
        orrery.setPaper(taken);
        helper.assertTrue(stamped.get().equals(orrery.puzzle().orElse(null)),
                "Re-inserting the paper must not reroll its puzzle");
        helper.succeed();
    }

    //? if neoforge {
    @GameTest(template = "empty")
    //?} else {
    /*@GameTest(template = "new_age_thaum:empty")
    *///?}
    public void serverRejectsEndpointGapAndOutOfRangeEdits(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        ResourceLocation flamma = NewAgeThaum.id("flamma");
        PlayerProgressService.scan(player, "test/puzzle_locks", new AspectBag(Map.of(flamma, 10)));

        // A hand-stamped puzzle so the locked cells are known: endpoint 0, gap 5.
        ItemStack paper = new ItemStack(ModRegistries.PAPER_FLEDGLING.get());
        paper.set(ModComponents.RESEARCH_PUZZLE.get(),
                new ResearchPuzzle(3, Map.of(0, flamma), Set.of(5)));
        ArcaneOrreryBlockEntity orrery = new ArcaneOrreryBlockEntity(BlockPos.ZERO,
                ModRegistries.ARCANE_ORRERY.get().defaultBlockState());
        orrery.setPaper(paper);

        helper.assertFalse(NewAgeThaumNetwork.applyOrreryEdit(player, orrery, 0, Optional.of(flamma)),
                "Painting an endpoint cell must be rejected");
        helper.assertFalse(NewAgeThaumNetwork.applyOrreryEdit(player, orrery, 0, Optional.empty()),
                "Clearing an endpoint cell must be rejected");
        helper.assertFalse(NewAgeThaumNetwork.applyOrreryEdit(player, orrery, 5, Optional.of(flamma)),
                "Painting a gap cell must be rejected");
        helper.assertFalse(NewAgeThaumNetwork.applyOrreryEdit(player, orrery, 9999, Optional.of(flamma)),
                "Painting outside the sphere must be rejected");
        helper.assertTrue(PlayerProgressService.get(player).points(flamma) == 10,
                "Rejected edits must not charge points");

        helper.assertTrue(NewAgeThaumNetwork.applyOrreryEdit(player, orrery, 7, Optional.of(flamma)),
                "An ordinary cell still accepts placements");
        helper.assertTrue(flamma.equals(orrery.aspectAt(7)), "Cell 7 should hold Flamma");
        helper.succeed();
    }
}
