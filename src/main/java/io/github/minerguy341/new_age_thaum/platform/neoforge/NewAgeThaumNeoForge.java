//? if neoforge {
package io.github.minerguy341.new_age_thaum.platform.neoforge;

import io.github.minerguy341.new_age_thaum.NewAgeThaum;
import io.github.minerguy341.new_age_thaum.core.player.PlayerProgressService;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

@Mod(NewAgeThaum.MOD_ID)
public final class NewAgeThaumNeoForge {
    public NewAgeThaumNeoForge(IEventBus modEventBus) {
        NeoForgePlayerData playerData = new NeoForgePlayerData();
        playerData.register(modEventBus);
        PlayerProgressService.setBridge(playerData);
        NewAgeThaum.init(new NeoForgePlatformInfo());
        // Tags rebind AFTER the reload listeners run (and after the client applies the
        // tag packet), so the resolver must drop its cache again once the new bindings
        // are live — the assignment-time invalidation alone leaves stale-tag bags stuck.
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.event.TagsUpdatedEvent event) ->
                        io.github.minerguy341.new_age_thaum.core.aspect.AspectResolver.invalidate());
        if (net.neoforged.fml.loading.FMLEnvironment.dist == net.neoforged.api.distmarker.Dist.CLIENT) {
            NeoForgeClientHooks.register();
        }
    }
}
//?}
