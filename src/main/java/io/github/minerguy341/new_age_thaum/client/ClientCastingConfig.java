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
    // volatile: written on the network thread (the S2C receiver), read on the render
    // thread — the same cross-thread discipline the other synced client mirrors use
    // (AspectRegistry, WandMaterialRegistry). A one-frame tear on the floats is harmless
    // (a HUD tick a pixel off for a frame); the synced flag gates the whole switch.
    private static volatile boolean synced;
    private static volatile float ambientFloor;
    private static volatile float affinityFloor;
    private static volatile float rechargeRate;
    private static volatile float nodeChargePerUse;

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
