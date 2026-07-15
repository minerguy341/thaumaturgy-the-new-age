//? if fabric {
/*package io.github.minerguy341.new_age_thaum.platform.fabric;

import io.github.minerguy341.new_age_thaum.NewAgeThaum;
import io.github.minerguy341.new_age_thaum.core.player.PlayerProgressService;
import net.fabricmc.api.ModInitializer;

public final class NewAgeThaumFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        PlayerProgressService.setBridge(new FabricPlayerData());
        NewAgeThaum.init(new FabricPlatformInfo());
    }
}
*///?}
