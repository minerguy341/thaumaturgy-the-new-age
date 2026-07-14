package io.github.minerguy341.new_age_thaum;

import io.github.minerguy341.new_age_thaum.core.ModRegistries;
import io.github.minerguy341.new_age_thaum.platform.PlatformInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Common entrypoint. Loader entrypoints in {@code platform/} call {@link #init(PlatformInfo)}
 * exactly once during mod construction, handing over their loader's {@link PlatformInfo}.
 */
public final class NewAgeThaum {
    public static final String MOD_ID = "new_age_thaum";
    public static final Logger LOGGER = LoggerFactory.getLogger("Thaumaturgy: The New Age");

    private NewAgeThaum() {
    }

    public static void init(PlatformInfo platform) {
        //? if fabric {
        /*LOGGER.info("Loader-constant branch active: FABRIC");*/
        //?} else {
        LOGGER.info("Loader-constant branch active: NEOFORGE");
        //?}
        LOGGER.info("PlatformInfo resolved: loader={}, developmentEnvironment={}",
                platform.loaderName(), platform.isDevelopmentEnvironment());

        ModRegistries.init();
    }
}
