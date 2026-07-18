package io.github.minerguy341.new_age_thaum.gametest;

import io.github.minerguy341.new_age_thaum.NewAgeThaum;
import io.github.minerguy341.new_age_thaum.content.AuraNodeBlockEntity;
import io.github.minerguy341.new_age_thaum.core.ModRegistries;
import io.github.minerguy341.new_age_thaum.core.aura.AuraField;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;

/**
 * The aura hybrid (PLAN §4.3): field math (clamp, budgeted diffusion, persistence),
 * nodes as regeneration sources through the exact production pump, and the worldgen
 * definitions actually loading on both loaders.
 */
//? if neoforge {
@net.neoforged.neoforge.gametest.GameTestHolder(NewAgeThaum.MOD_ID)
@net.neoforged.neoforge.gametest.PrefixGameTestTemplate(false)
//?}
public class AuraGameTest {

    //? if neoforge {
    @GameTest(template = "empty")
    //?} else {
    /*@GameTest(template = "new_age_thaum:empty")
    *///?}
    public void fieldClampsDiffusesAndPersists(GameTestHelper helper) {
        AuraField field = new AuraField();
        long center = new ChunkPos(0, 0).toLong();
        long east = new ChunkPos(1, 0).toLong();

        helper.assertTrue(field.add(center, AuraField.CHUNK_CAP * 2) == AuraField.CHUNK_CAP,
                "Vis must clamp at the chunk cap");
        helper.assertTrue(field.add(center, -AuraField.CHUNK_CAP * 3) == 0f,
                "Vis must clamp at zero");

        field.add(center, 80f);
        field.diffuse();
        float here = field.vis(center);
        helper.assertTrue(field.vis(east) > 0f, "Diffusion must bleed vis into an empty neighbor");
        helper.assertTrue(here < 80f, "Diffusion must drain the source chunk");
        float total = here + field.vis(east)
                + field.vis(new ChunkPos(-1, 0).toLong())
                + field.vis(new ChunkPos(0, 1).toLong())
                + field.vis(new ChunkPos(0, -1).toLong());
        helper.assertTrue(Math.abs(total - 80f) < 1.0e-3f,
                "Diffusion conserves vis below the cap, got total " + total);

        var registries = helper.getLevel().registryAccess();
        AuraField reloaded = AuraField.load(field.save(new CompoundTag(), registries), registries);
        helper.assertTrue(Math.abs(reloaded.vis(center) - here) < 1.0e-5f,
                "Vis must survive the SavedData round trip");
        helper.succeed();
    }

    //? if neoforge {
    @GameTest(template = "empty")
    //?} else {
    /*@GameTest(template = "new_age_thaum:empty")
    *///?}
    public void nodeFeedsItsChunkAndNeighbors(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 2, 1);
        helper.setBlock(pos, ModRegistries.AURA_NODE.get());
        if (!(helper.getBlockEntity(pos) instanceof AuraNodeBlockEntity node)) {
            helper.fail("Placed aura node has no block entity");
            return;
        }
        var level = helper.getLevel();
        AuraField aura = AuraField.get(level);
        ChunkPos center = new ChunkPos(helper.absolutePos(pos));
        long centerKey = center.toLong();
        long neighborKey = new ChunkPos(center.x + 1, center.z).toLong();

        node.serverTick(level); // first tick rolls the node's aspect identity (and may pump)
        helper.assertTrue(node.aspect() != null, "A ticked node must roll an aspect identity");
        helper.assertTrue(node.size() > 0f, "A ticked node must roll a positive size");

        // Baselines AFTER serverTick — it pumps on its own cadence; the explicit pump
        // below is the measured one.
        float centerBefore = aura.vis(centerKey);
        float neighborBefore = aura.vis(neighborKey);
        node.pump(level);
        float centerGain = aura.vis(centerKey) - centerBefore;
        float neighborGain = aura.vis(neighborKey) - neighborBefore;
        helper.assertTrue(Math.abs(centerGain - node.size()) < 1.0e-4f,
                "The node's own chunk gains the full rate, got " + centerGain);
        helper.assertTrue(Math.abs(neighborGain - node.size() * 0.25f) < 1.0e-4f,
                "Neighbor chunks gain a quarter rate, got " + neighborGain);
        helper.succeed();
    }

    //? if neoforge {
    @GameTest(template = "empty")
    //?} else {
    /*@GameTest(template = "new_age_thaum:empty")
    *///?}
    public void worldgenDefinitionsLoad(GameTestHelper helper) {
        var registries = helper.getLevel().registryAccess();
        helper.assertTrue(registries.registryOrThrow(Registries.CONFIGURED_FEATURE)
                        .containsKey(NewAgeThaum.id("aura_node")),
                "Configured feature aura_node failed to load from the datapack");
        helper.assertTrue(registries.registryOrThrow(Registries.PLACED_FEATURE)
                        .containsKey(NewAgeThaum.id("aura_nodes")),
                "Placed feature aura_nodes failed to load from the datapack");
        var biomes = registries.registryOrThrow(Registries.BIOME);
        var tag = net.minecraft.tags.TagKey.create(Registries.BIOME, NewAgeThaum.id("has_aura_nodes"));
        helper.assertTrue(biomes.getTag(tag).map(set -> set.size() > 0).orElse(false),
                "Biome tag has_aura_nodes should resolve to at least one biome");
        helper.succeed();
    }
}
