//? if neoforge {
package io.github.minerguy341.new_age_thaum.platform.neoforge;

import io.github.minerguy341.new_age_thaum.NewAgeThaum;
import net.neoforged.fml.common.Mod;

@Mod(NewAgeThaum.MOD_ID)
public final class NewAgeThaumNeoForge {
    public NewAgeThaumNeoForge() {
        NewAgeThaum.init(new NeoForgePlatformInfo());
    }
}
//?}
