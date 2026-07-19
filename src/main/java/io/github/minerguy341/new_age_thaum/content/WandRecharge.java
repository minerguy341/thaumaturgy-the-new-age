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

import java.util.Set;

/**
 * Wand recharge (PLAN §4.4). Two paths, both drawing conserved vis from the chunk aura
 * (so charging visibly drains the chunk on a dioptra, and a node's pumping replenishes
 * what a wand takes):
 * <ul>
 *   <li><b>Ambient</b> — automatic once a second while carried: each primal fills only to
 *       a floor fraction of capacity (raised for the primals in the core's recharge
 *       affinity), drawn from the holder's own chunk. This is the free background trickle.
 *   <li><b>Node</b> — a manual right-click on an aura node ({@link #chargeFromNode}): fills
 *       every primal toward FULL capacity, the node's own primal fastest, drawn from the
 *       node's chunk. Nodes "provide the rest" of the charge, but you have to go tap them.
 * </ul>
 * All entry points are public statics so gametests drive the exact production path.
 */
public final class WandRecharge {
    /** Ambient recharge cadence; inventoryTick gates on this so the cost is 1 pass/second. */
    public static final int INTERVAL_TICKS = 20;
    /** The node's own primal charges at this multiple of the per-click budget. */
    private static final float NODE_MATCH_BONUS = 2.0f;

    private WandRecharge() {
    }

    /**
     * One ambient recharge pass: fills each primal only to its floor fraction of
     * capacity, drawn (conserved) from the holder's own chunk aura. Called on the tick
     * cadence from {@link CastingImplementItem#inventoryTick}.
     */
    public static void tick(ServerLevel level, BlockPos holderPos, ItemStack stack) {
        WandStats stats = statsOf(stack);
        if (stats == null) {
            return;
        }
        float capacity = (float) stats.capacity();
        Set<ResourceLocation> affinity = stats.rechargeAffinity().map(Primals::primalsOf).orElse(Set.of());
        AuraField aura = AuraField.get(level);
        long chunkKey = new ChunkPos(holderPos).toLong();
        float perPass = (float) NewAgeThaumConfig.wandRechargeRate * (INTERVAL_TICKS / 20f);

        WandVis vis = stack.getOrDefault(ModComponents.WAND_VIS.get(), WandVis.EMPTY);
        boolean changed = false;
        for (ResourceLocation primal : Primals.ORDER) {
            float floor = (float) (affinity.contains(primal)
                    ? NewAgeThaumConfig.wandAffinityFloor : NewAgeThaumConfig.wandAmbientFloor);
            float current = vis.get(primal);
            float draw = Math.min(Math.min(perPass, capacity * floor - current), aura.vis(chunkKey));
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
     * One manual node-charge (a right-click on the node): fills every primal toward full
     * capacity, the node's own primal at {@link #NODE_MATCH_BONUS} rate, drawn conserved
     * from the node's chunk aura. Returns the total vis moved so the interaction can give
     * proportional feedback (and gametests can assert it). The chunk aura is the natural
     * rate limit — a click can't pull more than the chunk holds, and the node re-pumps
     * over the following seconds — so no artificial cooldown is needed.
     */
    public static float chargeFromNode(ServerLevel level, BlockPos nodePos, ItemStack stack,
            AuraNodeBlockEntity node) {
        WandStats stats = statsOf(stack);
        if (stats == null) {
            return 0f;
        }
        float capacity = (float) stats.capacity();
        AuraField aura = AuraField.get(level);
        long chunkKey = new ChunkPos(nodePos).toLong();
        float perUse = (float) NewAgeThaumConfig.wandNodeChargePerUse;
        ResourceLocation nodeAspect = node.aspect();

        WandVis vis = stack.getOrDefault(ModComponents.WAND_VIS.get(), WandVis.EMPTY);
        float moved = 0f;
        for (ResourceLocation primal : Primals.ORDER) {
            float budget = primal.equals(nodeAspect) ? perUse * NODE_MATCH_BONUS : perUse;
            float current = vis.get(primal);
            float draw = Math.min(Math.min(budget, capacity - current), aura.vis(chunkKey));
            if (draw <= 0f) {
                continue;
            }
            aura.add(chunkKey, -draw);
            vis = vis.with(primal, current + draw);
            moved += draw;
        }
        if (moved > 0f) {
            stack.set(ModComponents.WAND_VIS.get(), vis);
        }
        return moved;
    }

    /** Stats of an assembled implement stack, or null if it isn't one / has no capacity. */
    private static WandStats statsOf(ItemStack stack) {
        if (!(stack.getItem() instanceof CastingImplementItem implement)) {
            return null;
        }
        WandComponent component = CastingImplementItem.componentOf(stack);
        if (component == null) {
            return null;
        }
        WandStats stats = WandStats.compute(component, implement.form());
        return stats.capacity() > 0 ? stats : null;
    }
}
