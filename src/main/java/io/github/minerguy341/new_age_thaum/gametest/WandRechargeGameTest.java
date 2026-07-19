package io.github.minerguy341.new_age_thaum.gametest;

import io.github.minerguy341.new_age_thaum.NewAgeThaum;
import io.github.minerguy341.new_age_thaum.content.AuraNodeBlockEntity;
import io.github.minerguy341.new_age_thaum.content.CastingImplementItem;
import io.github.minerguy341.new_age_thaum.content.WandRecharge;
import io.github.minerguy341.new_age_thaum.core.ModComponents;
import io.github.minerguy341.new_age_thaum.core.ModRegistries;
import io.github.minerguy341.new_age_thaum.core.NewAgeThaumConfig;
import io.github.minerguy341.new_age_thaum.core.aspect.Primals;
import io.github.minerguy341.new_age_thaum.core.aura.AuraField;
import io.github.minerguy341.new_age_thaum.core.casting.WandComponent;
import io.github.minerguy341.new_age_thaum.core.casting.WandVis;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;

import java.util.HashMap;
import java.util.Map;

/**
 * Wand vis recharge (PLAN §4.4): ambient draw stops at the floor (raised on affinity
 * primals) and conserves chunk aura, nodes lift the floor to full capacity with a bonus
 * on their own primal, an empty chunk gives nothing, and the WandVis component
 * round-trips its codec. All through the production {@link WandRecharge} entry points.
 */
//? if neoforge {
@net.neoforged.neoforge.gametest.GameTestHolder(NewAgeThaum.MOD_ID)
@net.neoforged.neoforge.gametest.PrefixGameTestTemplate(false)
//?}
public class WandRechargeGameTest {

    /** Greatwood core (capacity 50, affinity silva = tellus+unda) with both caps. */
    private static ItemStack assembledWand() {
        ItemStack stack = new ItemStack(ModRegistries.WAND.get());
        stack.set(ModComponents.WAND.get(), new WandComponent(
                NewAgeThaum.id("greatwood"), NewAgeThaum.id("brass"), NewAgeThaum.id("aetherium")));
        return stack;
    }

    //? if neoforge {
    @GameTest(template = "empty")
    //?} else {
    /*@GameTest(template = "new_age_thaum:empty")
    *///?}
    public void ambientChargesToFloorConservingAura(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));
        AuraField aura = AuraField.get(level);
        long chunk = new ChunkPos(pos).toLong();
        aura.add(chunk, -AuraField.CHUNK_CAP * 2);
        aura.add(chunk, AuraField.CHUNK_CAP); // exactly 100

        ItemStack wand = assembledWand();
        float capacity = 50f;
        float baseTarget = capacity * (float) NewAgeThaumConfig.wandAmbientFloor;
        float affinityTarget = capacity * (float) NewAgeThaumConfig.wandAffinityFloor;
        float perPass = (float) NewAgeThaumConfig.wandRechargeRate;
        int passes = (int) Math.ceil(Math.max(baseTarget, affinityTarget) / Math.max(perPass, 0.01f)) + 4;
        for (int i = 0; i < passes; i++) {
            WandRecharge.charge(level, pos, wand, null);
        }

        WandVis vis = wand.getOrDefault(ModComponents.WAND_VIS.get(), WandVis.EMPTY);
        ResourceLocation tellus = NewAgeThaum.id("tellus");
        ResourceLocation unda = NewAgeThaum.id("unda");
        float totalDrawn = 0f;
        for (ResourceLocation primal : Primals.ORDER) {
            boolean affinity = primal.equals(tellus) || primal.equals(unda);
            float expected = affinity ? affinityTarget : baseTarget;
            float stored = vis.get(primal);
            helper.assertTrue(Math.abs(stored - expected) < 1.0e-3f,
                    "Ambient recharge of " + primal + " must stop at "
                            + expected + " (affinity=" + affinity + "), got " + stored);
            totalDrawn += stored;
        }
        float remaining = aura.vis(chunk);
        helper.assertTrue(Math.abs(remaining - (AuraField.CHUNK_CAP - totalDrawn)) < 1.0e-3f,
                "Recharge must conserve chunk aura: drew " + totalDrawn + ", chunk kept " + remaining);
        helper.succeed();
    }

    //? if neoforge {
    @GameTest(template = "empty")
    //?} else {
    /*@GameTest(template = "new_age_thaum:empty")
    *///?}
    public void nodeLiftsFloorToFullCapacity(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos nodePos = new BlockPos(1, 2, 1);
        helper.setBlock(nodePos, ModRegistries.AURA_NODE.get());
        if (!(helper.getBlockEntity(nodePos) instanceof AuraNodeBlockEntity node)) {
            helper.fail("Placed aura node has no block entity");
            return;
        }
        node.serverTick(level); // rolls the node's primal identity
        helper.assertTrue(node.aspect() != null, "A ticked node must roll an aspect identity");

        BlockPos holderPos = helper.absolutePos(new BlockPos(2, 2, 1));
        helper.assertTrue(WandRecharge.findNode(level, holderPos) == node,
                "findNode must see an aura node one block away");

        AuraField aura = AuraField.get(level);
        long chunk = new ChunkPos(holderPos).toLong();
        ItemStack wand = assembledWand();
        float capacity = 50f;

        // A few passes in, the node's own primal must lead any other primal (its rate
        // is doubled; every primal's target is full capacity near a node).
        aura.add(chunk, AuraField.CHUNK_CAP);
        for (int i = 0; i < 3; i++) {
            WandRecharge.charge(level, holderPos, wand, node);
        }
        WandVis early = wand.getOrDefault(ModComponents.WAND_VIS.get(), WandVis.EMPTY);
        ResourceLocation other = Primals.ORDER.stream()
                .filter(id -> !id.equals(node.aspect())).findFirst().orElseThrow();
        helper.assertTrue(early.get(node.aspect()) > early.get(other),
                "The node's own primal must charge fastest, got " + early.get(node.aspect())
                        + " vs " + early.get(other));

        float nodePerPass = (float) (NewAgeThaumConfig.wandRechargeRate
                * NewAgeThaumConfig.wandNodeRechargeMultiplier);
        int passes = (int) Math.ceil(capacity / Math.max(nodePerPass, 0.01f)) + 8;
        for (int i = 0; i < passes; i++) {
            aura.add(chunk, AuraField.CHUNK_CAP); // keep the chunk topped up
            WandRecharge.charge(level, holderPos, wand, node);
        }
        WandVis full = wand.getOrDefault(ModComponents.WAND_VIS.get(), WandVis.EMPTY);
        for (ResourceLocation primal : Primals.ORDER) {
            helper.assertTrue(Math.abs(full.get(primal) - capacity) < 1.0e-3f,
                    "Near a node every primal must reach full capacity, " + primal
                            + " got " + full.get(primal));
        }
        helper.succeed();
    }

    //? if neoforge {
    @GameTest(template = "empty")
    //?} else {
    /*@GameTest(template = "new_age_thaum:empty")
    *///?}
    public void emptyChunkGivesNothing(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));
        AuraField aura = AuraField.get(level);
        aura.add(new ChunkPos(pos).toLong(), -AuraField.CHUNK_CAP * 2);

        ItemStack wand = assembledWand();
        for (int i = 0; i < 5; i++) {
            WandRecharge.charge(level, pos, wand, null);
        }
        helper.assertTrue(wand.get(ModComponents.WAND_VIS.get()) == null,
                "An empty chunk must charge nothing (component never set)");
        helper.succeed();
    }

    //? if neoforge {
    @GameTest(template = "empty")
    //?} else {
    /*@GameTest(template = "new_age_thaum:empty")
    *///?}
    public void visComponentRoundTripsAndSanitizes(GameTestHelper helper) {
        WandVis sample = WandVis.EMPTY
                .with(NewAgeThaum.id("tellus"), 12.5f)
                .with(NewAgeThaum.id("flamma"), 3.25f);
        var encoded = WandVis.CODEC.encodeStart(NbtOps.INSTANCE, sample).getOrThrow();
        WandVis decoded = WandVis.CODEC.parse(NbtOps.INSTANCE, encoded).getOrThrow();
        helper.assertTrue(decoded.equals(sample),
                "WandVis must survive a codec round trip, got " + decoded + " from " + sample);

        Map<ResourceLocation, Float> dirty = new HashMap<>();
        dirty.put(NewAgeThaum.id("tellus"), Float.NaN);
        dirty.put(NewAgeThaum.id("unda"), -4f);
        dirty.put(NewAgeThaum.id("forma"), 7f);
        WandVis sanitized = new WandVis(dirty);
        helper.assertTrue(sanitized.get(NewAgeThaum.id("tellus")) == 0f
                        && sanitized.get(NewAgeThaum.id("unda")) == 0f
                        && sanitized.get(NewAgeThaum.id("forma")) == 7f,
                "WandVis must drop non-finite and non-positive entries, got " + sanitized);

        ItemStack wand = assembledWand();
        helper.assertTrue(CastingImplementItem.componentOf(wand) != null,
                "Test wand must carry a wand component");
        helper.succeed();
    }
}
