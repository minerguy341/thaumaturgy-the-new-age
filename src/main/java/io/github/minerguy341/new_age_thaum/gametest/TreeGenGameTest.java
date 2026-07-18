package io.github.minerguy341.new_age_thaum.gametest;

import io.github.minerguy341.new_age_thaum.NewAgeThaum;
import io.github.minerguy341.new_age_thaum.core.ModRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.state.BlockState;

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

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(NewAgeThaum.MOD_ID, path);
    }

    //? if neoforge {
    @GameTest(template = "empty")
    //?} else {
    /*@GameTest(template = "new_age_thaum:empty")
    *///?}
    public void worldgenDefinitionsLoad(GameTestHelper helper) {
        var registries = helper.getLevel().registryAccess();
        var configured = registries.registryOrThrow(Registries.CONFIGURED_FEATURE);
        var placed = registries.registryOrThrow(Registries.PLACED_FEATURE);
        for (String tree : new String[]{"greatwood_tree", "silverwood_tree"}) {
            helper.assertTrue(configured.containsKey(id(tree)),
                    "Configured feature " + tree + " failed to load from the datapack");
        }
        for (String placement : new String[]{"greatwood_trees", "silverwood_trees"}) {
            helper.assertTrue(placed.containsKey(id(placement)),
                    "Placed feature " + placement + " failed to load from the datapack");
        }
        var biomes = registries.registryOrThrow(Registries.BIOME);
        for (String tag : new String[]{"has_greatwood", "has_silverwood"}) {
            var tagKey = net.minecraft.tags.TagKey.create(Registries.BIOME, id(tag));
            helper.assertTrue(biomes.getTag(tagKey).map(set -> set.size() > 0).orElse(false),
                    "Biome tag " + tag + " should resolve to at least one biome");
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
        // Force growth through the real sapling path (stage 0 -> 1 -> tree); a few
        // attempts cover the random stage advance.
        for (int i = 0; i < 8 && level.getBlockState(absolute).is(ModRegistries.SILVERWOOD_SAPLING.get()); i++) {
            BlockState state = level.getBlockState(absolute);
            if (state.getBlock() instanceof BonemealableBlock sapling) {
                sapling.performBonemeal(level, level.random, absolute, state);
            }
        }

        // The trunk grows in place: the sapling cell should now hold a silverwood log.
        helper.assertTrue(level.getBlockState(absolute).is(ModRegistries.SILVERWOOD_LOG.get()),
                "Sapling should have grown into a silverwood trunk, got "
                        + level.getBlockState(absolute));
        helper.succeed();
    }
}
