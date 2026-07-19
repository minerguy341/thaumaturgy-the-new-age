//? if neoforge {
package io.github.minerguy341.new_age_thaum.platform.neoforge;

import io.github.minerguy341.new_age_thaum.client.LateHolograms;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Client-only NeoForge hooks, isolated in their own class so a dedicated server never
 * loads client classes — the entry class calls {@link #register()} behind an
 * {@code FMLEnvironment.dist} check.
 */
final class NeoForgeClientHooks {
    private NeoForgeClientHooks() {
    }

    static void register() {
        // Drain the deferred hologram quads AFTER translucent terrain: water (already
        // drawn) shows through them, clouds/weather (drawn later) depth-test behind.
        NeoForge.EVENT_BUS.addListener((RenderLevelStageEvent event) -> {
            if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
                LateHolograms.renderAll();
            }
        });
    }
}
//?}
