package io.github.minerguy341.new_age_thaum.core.player;

import io.github.minerguy341.new_age_thaum.core.aspect.AspectBag;
import io.github.minerguy341.new_age_thaum.network.NewAgeThaumNetwork;
import io.github.minerguy341.new_age_thaum.platform.PlayerDataBridge;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * Common entry point for player progress. Each loader installs its
 * {@link PlayerDataBridge} at init; gameplay code only ever calls this holder.
 */
public final class PlayerProgressService {
    private static PlayerDataBridge bridge;

    private PlayerProgressService() {
    }

    public static void setBridge(PlayerDataBridge impl) {
        bridge = impl;
    }

    public static PlayerProgress get(Player player) {
        return bridge == null ? PlayerProgress.EMPTY : bridge.get(player);
    }

    public static void set(ServerPlayer player, PlayerProgress progress) {
        bridge.set(player, progress);
        NewAgeThaumNetwork.syncProgressTo(player, progress);
    }

    /**
     * Records a scan of {@code key}, granting {@code aspects} as observation points.
     * Returns true if this was the first scan of that key (points were granted),
     * false if already known (no change).
     */
    public static boolean scan(ServerPlayer player, String key, AspectBag aspects) {
        PlayerProgress current = get(player);
        if (current.hasScanned(key)) {
            return false;
        }
        set(player, current.withScan(key, aspects));
        return true;
    }

    public static void syncOnJoin(ServerPlayer player) {
        NewAgeThaumNetwork.syncProgressTo(player, get(player));
    }

    /**
     * Spends observation points if the balance allows. Returns false (no change)
     * when the player cannot afford it.
     */
    public static boolean trySpend(ServerPlayer player, net.minecraft.resources.ResourceLocation aspect, int amount) {
        PlayerProgress spent = get(player).withSpent(aspect, amount);
        if (spent == null) {
            return false;
        }
        set(player, spent);
        return true;
    }

    /**
     * Design seam for the future research unlock that grants a small chance to refund
     * a cleared aspect (m2-gameplay-spec §A). Always 0 until that research exists.
     */
    public static double refundChance(ServerPlayer player) {
        return 0.0;
    }

    /** Grants points without a scan (refunds, future rewards). */
    public static void scanlessGrant(ServerPlayer player, net.minecraft.resources.ResourceLocation aspect, int amount) {
        set(player, get(player).withGained(aspect, amount));
    }
}
