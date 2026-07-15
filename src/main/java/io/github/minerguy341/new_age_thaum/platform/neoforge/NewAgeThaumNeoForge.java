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
    }
}
//?}
