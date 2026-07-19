package io.github.minerguy341.new_age_thaum.client;

import io.github.minerguy341.new_age_thaum.core.NewAgeThaumConfig;
import io.github.minerguy341.new_age_thaum.network.CastingConfigSyncPayload;

/**
 * Client mirror of the server's recharge-governing config, populated by
 * {@link CastingConfigSyncPayload} on join and reset when leaving a world. Until a sync
 * arrives (singleplayer's integrated server sends it too; a vanilla or channel-less
 * server never will) every getter falls back to the client's OWN config file — a sane
 * default that also keeps singleplayer correct without special-casing. The wand HUD
 * reads its floor markers from here so they match the server the player is actually on.
 */
public final class ClientCastingConfig {
    private static boolean synced;
    private static float ambientFloor;
    private static float affinityFloor;
    private static float rechargeRate;
    private static float nodeChargePerUse;

    private ClientCastingConfig() {
    }

    public static void accept(CastingConfigSyncPayload payload) {
        ambientFloor = payload.ambientFloor();
        affinityFloor = payload.affinityFloor();
        rechargeRate = payload.rechargeRate();
        nodeChargePerUse = payload.nodeChargePerUse();
        synced = true;
    }

    /** Drop the server's values when leaving the world, so the next server's sync wins. */
    public static void reset() {
        synced = false;
    }

    public static float ambientFloor() {
        return synced ? ambientFloor : (float) NewAgeThaumConfig.wandAmbientFloor;
    }

    public static float affinityFloor() {
        return synced ? affinityFloor : (float) NewAgeThaumConfig.wandAffinityFloor;
    }

    public static float rechargeRate() {
        return synced ? rechargeRate : (float) NewAgeThaumConfig.wandRechargeRate;
    }

    public static float nodeChargePerUse() {
        return synced ? nodeChargePerUse : (float) NewAgeThaumConfig.wandNodeChargePerUse;
    }
}
