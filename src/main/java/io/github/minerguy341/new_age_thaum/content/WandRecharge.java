package io.github.minerguy341.new_age_thaum.content;

import io.github.minerguy341.new_age_thaum.core.ModComponents;
import io.github.minerguy341.new_age_thaum.core.NewAgeThaumConfig;
import io.github.minerguy341.new_age_thaum.core.aspect.Primals;
import io.github.minerguy341.new_age_thaum.core.aura.AuraField;
import io.github.minerguy341.new_age_thaum.core.casting.WandComponent;
import io.github.minerguy341.new_age_thaum.core.casting.WandStats;
import io.github.minerguy341.new_age_thaum.core.casting.WandVis;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.Set;

/**
 * Wand recharge (PLAN §4.4): implements draw vis from the ambient chunk aura — conserved,
 * so charging visibly drains the chunk on a dioptra. Away from nodes each primal only
 * fills to a floor fraction of capacity (raised for the primals in the core's
 * recharge-affinity decomposition); within range of an aura node the floor lifts to full
 * capacity and the rate multiplies, with the node's own primal doubled again. The node's
 * pumping replenishes what the wand takes, closing the loop. All entry points are
 * public statics so gametests drive the exact production path.
 */
public final class WandRecharge {
    /** Recharge cadence; inventoryTick gates on this so the cost is 1 pass/second. */
    public static final int INTERVAL_TICKS = 20;
    private static final double NODE_RANGE = 10.0;
    private static final float NODE_MATCH_BONUS = 2.0f;

    private WandRecharge() {
    }

    /** One recharge pass for a held/carried implement. Called on the tick cadence. */
    public static void tick(ServerLevel level, BlockPos holderPos, ItemStack stack) {
        charge(level, holderPos, stack, findNode(level, holderPos));
    }

    /**
     * One recharge pass with an explicit (nullable) nearby node. Draws from the chunk
     * aura at {@code holderPos}: never more than the target leaves room for, never more
     * than the chunk holds.
     */
    public static void charge(ServerLevel level, BlockPos holderPos, ItemStack stack,
            AuraNodeBlockEntity node) {
        if (!(stack.getItem() instanceof CastingImplementItem implement)) {
            return;
        }
        WandComponent component = CastingImplementItem.componentOf(stack);
        if (component == null) {
            return;
        }
        WandStats stats = WandStats.compute(component, implement.form());
        float capacity = (float) stats.capacity();
        if (capacity <= 0f) {
            return;
        }
        Set<ResourceLocation> affinityPrimals =
                stats.rechargeAffinity().map(Primals::primalsOf).orElse(Set.of());
        AuraField aura = AuraField.get(level);
        long chunkKey = new ChunkPos(holderPos).toLong();
        float perPass = (float) NewAgeThaumConfig.wandRechargeRate * (INTERVAL_TICKS / 20f);

        WandVis vis = stack.getOrDefault(ModComponents.WAND_VIS.get(), WandVis.EMPTY);
        boolean changed = false;
        for (ResourceLocation primal : Primals.ORDER) {
            float floor = (float) (affinityPrimals.contains(primal)
                    ? NewAgeThaumConfig.wandAffinityFloor : NewAgeThaumConfig.wandAmbientFloor);
            float target = node != null ? capacity : capacity * floor;
            float current = vis.get(primal);
            if (current >= target) {
                continue;
            }
            float draw = perPass;
            if (node != null) {
                draw *= (float) NewAgeThaumConfig.wandNodeRechargeMultiplier;
                if (primal.equals(node.aspect())) {
                    draw *= NODE_MATCH_BONUS;
                }
            }
            draw = Math.min(draw, target - current);
            draw = Math.min(draw, aura.vis(chunkKey));
            if (draw <= 0f) {
                continue;
            }
            aura.add(chunkKey, -draw);
            vis = vis.with(primal, current + draw);
            changed = true;
        }
        if (changed) {
            stack.set(ModComponents.WAND_VIS.get(), vis);
        }
    }

    /**
     * Nearest aura node within {@link #NODE_RANGE}, scanning the 3x3 loaded chunks'
     * block-entity maps — bounded, infrequent (once per {@link #INTERVAL_TICKS}), and
     * never loads a chunk. There is no node position index yet; revisit if node counts
     * per chunk ever grow beyond worldgen's 0–3.
     */
    public static AuraNodeBlockEntity findNode(ServerLevel level, BlockPos pos) {
        AuraNodeBlockEntity nearest = null;
        double nearestSqr = NODE_RANGE * NODE_RANGE;
        ChunkPos center = new ChunkPos(pos);
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(center.x + dx, center.z + dz);
                if (chunk == null) {
                    continue;
                }
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    if (be instanceof AuraNodeBlockEntity node) {
                        double distSqr = node.getBlockPos().distSqr(pos);
                        if (distSqr <= nearestSqr) {
                            nearestSqr = distSqr;
                            nearest = node;
                        }
                    }
                }
            }
        }
        return nearest;
    }
}
