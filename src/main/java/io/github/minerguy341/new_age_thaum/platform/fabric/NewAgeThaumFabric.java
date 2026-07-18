//? if fabric {
/*package io.github.minerguy341.new_age_thaum.platform.fabric;

import io.github.minerguy341.new_age_thaum.NewAgeThaum;
import io.github.minerguy341.new_age_thaum.core.player.PlayerProgressService;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.CommonLifecycleEvents;

public final class NewAgeThaumFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        PlayerProgressService.setBridge(new FabricPlayerData());
        NewAgeThaum.init(new FabricPlatformInfo());
        // Tags rebind AFTER the reload listeners run (and after the client applies the
        // tag packet), so the resolver must drop its cache again once the new bindings
        // are live — the assignment-time invalidation alone leaves stale-tag bags stuck.
        CommonLifecycleEvents.TAGS_LOADED.register((registries, client) ->
                io.github.minerguy341.new_age_thaum.core.aspect.AspectResolver.invalidate());
    }
}
*///?}
