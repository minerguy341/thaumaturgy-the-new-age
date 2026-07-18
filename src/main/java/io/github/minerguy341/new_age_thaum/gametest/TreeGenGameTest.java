package io.github.minerguy341.new_age_thaum.gametest;

import io.github.minerguy341.new_age_thaum.NewAgeThaum;
import io.github.minerguy341.new_age_thaum.core.ModRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.GenerationStep;

import java.util.List;

/**
 * The homage trees end to end: the datapack worldgen definitions must actually load
 * (malformed JSON is only a log line otherwise), and a sapling must grow into its tree
 * through the vanilla TreeGrower -> configured-feature path.
 */
//? if neoforge {
@net.neoforged.neoforge.gametest.GameTestHolder(NewAgeThaum.MOD_ID)
@net.neoforged.neoforge.gametest.PrefixGameTestTemplate(false)
//?}
public class TreeGenGameTest {

    //? if neoforge {
    @GameTest(template = "empty")
    //?} else {
    /*@GameTest(template = "new_age_thaum:empty")
    *///?}
    public void worldgenDefinitionsLoad(GameTestHelper helper) {
        var registries = helper.getLevel().registryAccess();
        var configured = registries.registryOrThrow(Registries.CONFIGURED_FEATURE);
        // The same keys the saplings' TreeGrowers consume — not re-derived strings.
        for (var tree : List.of(ModRegistries.GREATWOOD_TREE, ModRegistries.SILVERWOOD_TREE)) {
            helper.assertTrue(configured.containsKey(tree.location()),
                    "Configured feature " + tree.location() + " failed to load from the datapack");
        }

        // Injection is the one piece wired per loader (NeoForge biome_modifier JSON vs
        // Fabric BiomeModifications), so assert past the ingredients to the effect:
        // every TREE_PLACEMENTS pair must be present in its tagged biomes' generation
        // settings. This is what catches one loader silently generating no trees.
        var placed = registries.registryOrThrow(Registries.PLACED_FEATURE);
        var biomes = registries.registryOrThrow(Registries.BIOME);
        int step = GenerationStep.Decoration.VEGETAL_DECORATION.ordinal();
        for (ModRegistries.TreePlacement placement : ModRegistries.TREE_PLACEMENTS) {
            helper.assertTrue(placed.containsKey(placement.feature().location()),
                    "Placed feature " + placement.feature().location() + " failed to load from the datapack");
            var tagged = biomes.getTag(placement.biomes()).orElse(null);
            helper.assertTrue(tagged != null && tagged.size() > 0,
                    "Biome tag " + placement.biomes().location() + " should resolve to at least one biome");
            for (var biome : tagged) {
                var steps = biome.value().getGenerationSettings().features();
                boolean injected = steps.size() > step
                        && steps.get(step).stream().anyMatch(feature -> feature.is(placement.feature()));
                helper.assertTrue(injected, "Placed feature " + placement.feature().location()
                        + " was not injected into " + biome.unwrapKey().map(k -> k.location().toString()).orElse("?")
                        + " — this loader's biome wiring is broken");
            }
        }
        helper.succeed();
    }

    // tree_pad is a 9x12x9 empty template: a grown tree must stay inside this test's
    // own bounds instead of bleeding into neighboring tests on the gametest grid.
    //? if neoforge {
    @GameTest(template = "tree_pad")
    //?} else {
    /*@GameTest(template = "new_age_thaum:tree_pad")
    *///?}
    public void silverwoodSaplingGrowsIntoATree(GameTestHelper helper) {
        BlockPos soil = new BlockPos(4, 0, 4);
        BlockPos saplingPos = soil.above();
        helper.setBlock(soil, Blocks.DIRT);
        helper.setBlock(saplingPos, ModRegistries.SILVERWOOD_SAPLING.get());

        BlockPos absolute = helper.absolutePos(saplingPos);
        var level = helper.getLevel();
        Block saplingBlock = ModRegistries.SILVERWOOD_SAPLING.get();
        // Force growth through the real sapling path (stage 0 -> 1 -> tree); a few
        // attempts cover the random stage advance.
        for (int i = 0; i < 8; i++) {
            BlockState state = level.getBlockState(absolute);
            if (!state.is(saplingBlock)) {
                break;
            }
            if (state.getBlock() instanceof BonemealableBlock sapling) {
                sapling.performBonemeal(level, level.random, absolute, state);
            }
        }

        // The trunk grows in place: the sapling cell should now hold a silverwood log.
        BlockState grown = level.getBlockState(absolute);
        helper.assertTrue(grown.is(ModRegistries.SILVERWOOD_LOG.get()),
                "Sapling should have grown into a silverwood trunk, got " + grown);
        helper.succeed();
    }
}
