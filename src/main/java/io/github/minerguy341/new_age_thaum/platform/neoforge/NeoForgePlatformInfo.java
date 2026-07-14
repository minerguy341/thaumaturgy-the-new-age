//? if neoforge {
package io.github.minerguy341.new_age_thaum.platform.neoforge;

import io.github.minerguy341.new_age_thaum.platform.PlatformInfo;
import net.neoforged.fml.loading.FMLLoader;

public final class NeoForgePlatformInfo implements PlatformInfo {
    @Override
    public String loaderName() {
        return "NeoForge";
    }

    @Override
    public boolean isDevelopmentEnvironment() {
        return !FMLLoader.isProduction();
    }
}
//?}
