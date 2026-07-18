//? if fabric {
/*package io.github.minerguy341.new_age_thaum.platform.fabric;

import io.github.minerguy341.new_age_thaum.client.LateHolograms;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

// Client entrypoint (fabric.mod.json "client"): render hooks only — all loader-neutral
// client setup stays in NewAgeThaumClient, reached through EnvExecutor from common init.
public final class NewAgeThaumFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        // Drain the deferred hologram quads AFTER translucent terrain: water (already
        // drawn) shows through them, clouds/weather (drawn later) depth-test behind.
        WorldRenderEvents.AFTER_TRANSLUCENT.register(context -> LateHolograms.renderAll());
    }
}
*///?}
