package io.github.minerguy341.new_age_thaum.gametest;

import io.github.minerguy341.new_age_thaum.NewAgeThaum;
import io.github.minerguy341.new_age_thaum.content.ArcaneOrreryBlockEntity;
import io.github.minerguy341.new_age_thaum.content.ResearchPaperItem;
import io.github.minerguy341.new_age_thaum.core.ModComponents;
import io.github.minerguy341.new_age_thaum.core.ModRegistries;
import io.github.minerguy341.new_age_thaum.core.aspect.Aspect;
import io.github.minerguy341.new_age_thaum.core.aspect.AspectBag;
import io.github.minerguy341.new_age_thaum.core.aspect.AspectRegistry;
import io.github.minerguy341.new_age_thaum.core.casting.WandMaterial;
import io.github.minerguy341.new_age_thaum.core.player.PlayerProgressService;
import io.github.minerguy341.new_age_thaum.core.research.AspectGraph;
import io.github.minerguy341.new_age_thaum.core.research.PuzzleGenerator;
import io.github.minerguy341.new_age_thaum.core.research.ResearchPuzzle;
import io.github.minerguy341.new_age_thaum.core.research.ResearchSphereData;
import io.github.minerguy341.new_age_thaum.network.NewAgeThaumNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The validation hardening around orrery edits and research data: unstamped papers are
 * inert, no-op edits are free, breaking the orrery spills the paper, corrupt component
 * data fails soft instead of crashing deserialization, deep derivation ladders resolve
 * instantly (the depth memo), and hidden solutions respect the paper tier's aspect pool.
 */
//? if neoforge {
@net.neoforged.neoforge.gametest.GameTestHolder(NewAgeThaum.MOD_ID)
@net.neoforged.neoforge.gametest.PrefixGameTestTemplate(false)
//?}
public class OrreryValidationGameTest {

    private static ResourceLocation aspect(String path) {
        return ResourceLocation.fromNamespaceAndPath(NewAgeThaum.MOD_ID, path);
    }

    /** Endpoints far from the cells under test and mutually unconnectable (same aspect). */
    private static ItemStack stampedPaper(ResourceLocation endpointAspect) {
        ItemStack paper = new ItemStack(ModRegistries.PAPER_FLEDGLING.get());
        paper.set(ModComponents.RESEARCH_PUZZLE.get(),
                new ResearchPuzzle(3, Map.of(0, endpointAspect, 30, endpointAspect), Set.of()));
        return paper;
    }

    //? if neoforge {
    @GameTest(template = "empty")
    //?} else {
    /*@GameTest(template = "new_age_thaum:empty")
    *///?}
    public void unstampedPaperRejectsEditsAndStampsOnLevelJoin(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        ResourceLocation flamma = aspect("flamma");
        PlayerProgressService.scan(player, "test/unstamped", new AspectBag(Map.of(flamma, 5)));

        ArcaneOrreryBlockEntity orrery = new ArcaneOrreryBlockEntity(BlockPos.ZERO,
                ModRegistries.ARCANE_ORRERY.get().defaultBlockState());
        orrery.setPaper(new ItemStack(ModRegistries.PAPER_FLEDGLING.get()));
        helper.assertTrue(orrery.puzzle().isEmpty(), "Without a level the paper stays unstamped");

        helper.assertFalse(NewAgeThaumNetwork.applyOrreryEdit(player, orrery, 4, Optional.of(flamma)),
                "An unstamped paper must reject placements (no bounds exist to validate against)");
        helper.assertFalse(NewAgeThaumNetwork.applyOrreryEdit(player, orrery, Integer.MAX_VALUE, Optional.of(flamma)),
                "An unstamped paper must reject wild cell indices");
        helper.assertTrue(PlayerProgressService.get(player).points(flamma) == 5,
                "Rejected edits must not charge points");
        helper.assertTrue(orrery.sphereCells().isEmpty(), "Nothing may persist onto an unstamped paper");

        // The legacy-paper path: joining a level stamps the puzzle retroactively.
        orrery.setLevel(helper.getLevel());
        helper.assertTrue(orrery.puzzle().isPresent(), "Joining a level must stamp a legacy paper");
        helper.succeed();
    }

    //? if neoforge {
    @GameTest(template = "empty")
    //?} else {
    /*@GameTest(template = "new_age_thaum:empty")
    *///?}
    public void noOpEditsAreFree(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        ResourceLocation flamma = aspect("flamma");
        PlayerProgressService.scan(player, "test/noop", new AspectBag(Map.of(flamma, 2)));

        ArcaneOrreryBlockEntity orrery = new ArcaneOrreryBlockEntity(BlockPos.ZERO,
                ModRegistries.ARCANE_ORRERY.get().defaultBlockState());
        orrery.setPaper(stampedPaper(flamma));

        helper.assertTrue(NewAgeThaumNetwork.applyOrreryEdit(player, orrery, 4, Optional.of(flamma)),
                "Initial placement should be accepted");
        helper.assertTrue(PlayerProgressService.get(player).points(flamma) == 1,
                "Initial placement costs 1 point");

        helper.assertTrue(NewAgeThaumNetwork.applyOrreryEdit(player, orrery, 4, Optional.of(flamma)),
                "Repainting the same aspect is a successful no-op");
        helper.assertTrue(PlayerProgressService.get(player).points(flamma) == 1,
                "A no-op repaint must not charge a point, got " + PlayerProgressService.get(player).points(flamma));

        helper.assertTrue(NewAgeThaumNetwork.applyOrreryEdit(player, orrery, 9, Optional.empty()),
                "Clearing an empty cell is a successful no-op");
        helper.assertTrue(orrery.aspectAt(4) != null, "The painted cell is untouched by the no-ops");
        helper.succeed();
    }

    //? if neoforge {
    @GameTest(template = "empty")
    //?} else {
    /*@GameTest(template = "new_age_thaum:empty")
    *///?}
    public void breakingTheOrreryDropsThePaper(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, ModRegistries.ARCANE_ORRERY.get());
        if (!(helper.getBlockEntity(pos) instanceof ArcaneOrreryBlockEntity orrery)) {
            helper.fail("Placed orrery has no block entity");
            return;
        }
        orrery.setPaper(new ItemStack(ModRegistries.PAPER_FLEDGLING.get()));

        helper.destroyBlock(pos);
        helper.assertItemEntityPresent(ModRegistries.PAPER_FLEDGLING.get(), pos, 2.0);
        helper.succeed();
    }

    //? if neoforge {
    @GameTest(template = "empty")
    //?} else {
    /*@GameTest(template = "new_age_thaum:empty")
    *///?}
    public void corruptComponentDataFailsSoft(GameTestHelper helper) {
        // A non-numeric cell key must yield a codec error, not throw out of parse —
        // an escaped NumberFormatException here is a chunk-load crash loop.
        CompoundTag badSphere = new CompoundTag();
        badSphere.putString("3a", "new_age_thaum:flamma");
        helper.assertTrue(ResearchSphereData.CODEC.parse(NbtOps.INSTANCE, badSphere).isError(),
                "Non-numeric sphere cell keys should decode to an error");

        CompoundTag badPuzzle = new CompoundTag();
        badPuzzle.putInt("frequency", 3);
        CompoundTag badEndpoints = new CompoundTag();
        badEndpoints.putString("not_a_number", "new_age_thaum:flamma");
        badPuzzle.put("endpoints", badEndpoints);
        badPuzzle.put("gaps", new net.minecraft.nbt.ListTag());
        helper.assertTrue(ResearchPuzzle.CODEC.parse(NbtOps.INSTANCE, badPuzzle).isError(),
                "Non-numeric endpoint keys should decode to an error");

        com.google.gson.JsonObject badKind = new com.google.gson.JsonObject();
        badKind.addProperty("kind", "rod");
        badKind.addProperty("color", "#123456");
        helper.assertTrue(WandMaterial.Data.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, badKind).isError(),
                "An unknown wand material kind should decode to an error, not fail the reload");
        helper.succeed();
    }

    //? if neoforge {
    @GameTest(template = "empty")
    //?} else {
    /*@GameTest(template = "new_age_thaum:empty")
    *///?}
    public void deepDerivationLaddersResolveInstantly(GameTestHelper helper) {
        // A 32-level diamond ladder (each level's two compounds built from the previous
        // level's pair) has ~2^32 root-to-leaf paths. Without the depth memo this test
        // times out; with it the snapshot is linear in the aspect count.
        Map<ResourceLocation, Aspect> original = new HashMap<>();
        for (Aspect aspect : AspectRegistry.all()) {
            original.put(aspect.id(), aspect);
        }
        try {
            Map<ResourceLocation, Aspect> ladder = new HashMap<>();
            ResourceLocation left = aspect("test_p");
            ResourceLocation right = aspect("test_q");
            ladder.put(left, new Aspect(left, 0x111111, List.of()));
            ladder.put(right, new Aspect(right, 0x222222, List.of()));
            int levels = 32;
            for (int i = 1; i <= levels; i++) {
                ResourceLocation a = aspect("test_a" + i);
                ResourceLocation b = aspect("test_b" + i);
                ladder.put(a, new Aspect(a, 0x333333, List.of(left, right)));
                ladder.put(b, new Aspect(b, 0x444444, List.of(left, right)));
                left = a;
                right = b;
            }
            int accepted = AspectRegistry.reload(ladder);
            helper.assertTrue(accepted == ladder.size(),
                    "The ladder should validate whole, accepted " + accepted + " of " + ladder.size());

            AspectGraph graph = AspectGraph.snapshot();
            helper.assertTrue(graph.depthOf(left) == levels,
                    "Top of the ladder should sit at depth " + levels + ", got " + graph.depthOf(left));
        } finally {
            AspectRegistry.reload(original);
        }
        helper.succeed();
    }

    //? if neoforge {
    @GameTest(template = "empty")
    //?} else {
    /*@GameTest(template = "new_age_thaum:empty")
    *///?}
    public void hiddenSolutionsStayInsideTheTierPool(GameTestHelper helper) {
        AspectGraph graph = AspectGraph.snapshot();
        ResearchPaperItem.Tier tier = ResearchPaperItem.Tier.FLEDGLING;
        RandomSource random = RandomSource.create(20260718L);
        for (int run = 0; run < 10; run++) {
            PuzzleGenerator.Generated generated = PuzzleGenerator.generateWithSolution(tier, random);
            for (Map.Entry<Integer, ResourceLocation> cell : generated.solution().entrySet()) {
                int depth = graph.depthOf(cell.getValue());
                helper.assertTrue(depth <= tier.maxAspectDepth,
                        "Run " + run + ": solution aspect " + cell.getValue() + " at depth " + depth
                                + " exceeds the tier limit " + tier.maxAspectDepth);
            }
        }
        helper.succeed();
    }

    //? if neoforge {
    @GameTest(template = "empty")
    //?} else {
    /*@GameTest(template = "new_age_thaum:empty")
    *///?}
    public void duplicateComponentCompoundsAreRejected(GameTestHelper helper) {
        ResourceLocation primal = aspect("test_dup_p");
        ResourceLocation bad = aspect("test_dup_x");
        Map<ResourceLocation, Aspect> incoming = Map.of(
                primal, new Aspect(primal, 0x111111, List.of()),
                bad, new Aspect(bad, 0x222222, List.of(primal, primal)));
        Map<ResourceLocation, Aspect> valid = AspectRegistry.filterValid(incoming);
        helper.assertTrue(valid.containsKey(primal), "The primal survives validation");
        helper.assertFalse(valid.containsKey(bad), "[X, X] compounds must be dropped");
        helper.succeed();
    }
}
