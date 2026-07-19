package io.github.minerguy341.new_age_thaum.gametest;

import io.github.minerguy341.new_age_thaum.NewAgeThaum;
import io.github.minerguy341.new_age_thaum.content.AuraNodeBlockEntity;
import io.github.minerguy341.new_age_thaum.core.ModRegistries;
import io.github.minerguy341.new_age_thaum.core.aura.AuraField;
import io.github.minerguy341.new_age_thaum.core.aura.NodePersonality;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
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
        helper.assertTrue(node.personality() != null, "A ticked node must roll a personality");

        // Baselines AFTER serverTick — it pumps on its own cadence; the explicit pump
        // below is the measured one. Output scales by the rolled personality's multiplier.
        float expectedCenter = node.size() * node.personality().visMultiplier();
        float centerBefore = aura.vis(centerKey);
        float neighborBefore = aura.vis(neighborKey);
        node.pump(level);
        float centerGain = aura.vis(centerKey) - centerBefore;
        float neighborGain = aura.vis(neighborKey) - neighborBefore;
        helper.assertTrue(Math.abs(centerGain - expectedCenter) < 1.0e-4f,
                "The node's own chunk gains size*personality, expected " + expectedCenter + " got " + centerGain);
        helper.assertTrue(Math.abs(neighborGain - expectedCenter * 0.25f) < 1.0e-4f,
                "Neighbor chunks gain a quarter rate, expected " + (expectedCenter * 0.25f) + " got " + neighborGain);
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

    //? if neoforge {
    @GameTest(template = "empty")
    //?} else {
    /*@GameTest(template = "new_age_thaum:empty")
    *///?}
    public void fluxDiffusesConservesAndPersistsBesideVis(GameTestHelper helper) {
        AuraField field = new AuraField();
        long center = new ChunkPos(0, 0).toLong();
        long east = new ChunkPos(1, 0).toLong();

        helper.assertTrue(field.addFlux(center, AuraField.FLUX_CAP * 2) == AuraField.FLUX_CAP,
                "Flux must clamp at the cap");
        helper.assertTrue(field.addFlux(center, -AuraField.FLUX_CAP * 3) == 0f,
                "Flux must clamp at zero");

        field.add(center, 30f);       // vis and flux coexist on the same chunk
        field.addFlux(center, 80f);
        field.diffuse();
        helper.assertTrue(field.flux(east) > 0f, "Diffusion must bleed flux into an empty neighbor");
        helper.assertTrue(field.flux(center) < 80f, "Diffusion must drain the flux source");
        float total = field.flux(center) + field.flux(east)
                + field.flux(new ChunkPos(-1, 0).toLong())
                + field.flux(new ChunkPos(0, 1).toLong())
                + field.flux(new ChunkPos(0, -1).toLong());
        helper.assertTrue(Math.abs(total - 80f) < 1.0e-3f, "Diffusion conserves flux, got total " + total);

        var registries = helper.getLevel().registryAccess();
        AuraField reloaded = AuraField.load(field.save(new CompoundTag(), registries), registries);
        helper.assertTrue(Math.abs(reloaded.flux(center) - field.flux(center)) < 1.0e-5f,
                "Flux must survive the SavedData round trip");
        helper.assertTrue(Math.abs(reloaded.vis(center) - field.vis(center)) < 1.0e-5f,
                "Vis on a flux-carrying chunk must also survive the round trip");
        helper.succeed();
    }

    //? if neoforge {
    @GameTest(template = "empty")
    //?} else {
    /*@GameTest(template = "new_age_thaum:empty")
    *///?}
    public void nodePersonalityDrivesFluxOnPump(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 2, 1);
        helper.setBlock(pos, ModRegistries.AURA_NODE.get());
        if (!(helper.getBlockEntity(pos) instanceof AuraNodeBlockEntity node)) {
            helper.fail("Placed aura node has no block entity");
            return;
        }
        var level = helper.getLevel();
        node.serverTick(level); // rolls aspect + personality
        NodePersonality nature = node.personality();
        helper.assertTrue(nature != null, "A ticked node must have a personality");

        AuraField aura = AuraField.get(level);
        long chunk = new ChunkPos(helper.absolutePos(pos)).toLong();
        aura.addFlux(chunk, 50f); // headroom for tainted, something for pure to burn
        float before = aura.flux(chunk);
        node.pump(level);
        float after = aura.flux(chunk);
        float expected = Mth.clamp(before + node.size() * nature.fluxPerPump(), 0f, AuraField.FLUX_CAP);
        helper.assertTrue(Math.abs(after - expected) < 1.0e-3f,
                "Pump flux for " + nature + " expected " + expected + " got " + after);
        // Tainted raises it, pure lowers it, the plain temperaments leave it flat.
        if (nature == NodePersonality.TAINTED) {
            helper.assertTrue(after > before, "A tainted node must raise flux");
        } else if (nature == NodePersonality.PURE) {
            helper.assertTrue(after < before, "A pure node must burn flux down");
        } else {
            helper.assertTrue(after == before, nature + " must not change flux, got " + after);
        }
        helper.succeed();
    }

    //? if neoforge {
    @GameTest(template = "empty")
    //?} else {
    /*@GameTest(template = "new_age_thaum:empty")
    *///?}
    public void personalityIdRoundTrips(GameTestHelper helper) {
        for (NodePersonality personality : NodePersonality.values()) {
            helper.assertTrue(NodePersonality.byId(personality.id()) == personality,
                    "Personality id must round-trip for " + personality);
        }
        helper.assertTrue(NodePersonality.byId("nonsense") == NodePersonality.PALE,
                "An unknown personality id must fall back to PALE");
        helper.succeed();
    }
}
