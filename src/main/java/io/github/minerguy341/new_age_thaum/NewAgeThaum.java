package io.github.minerguy341.new_age_thaum;

import dev.architectury.event.events.common.PlayerEvent;
import dev.architectury.registry.ReloadListenerRegistry;
import io.github.minerguy341.new_age_thaum.core.ModRegistries;
import io.github.minerguy341.new_age_thaum.core.aspect.AspectAssignments;
import io.github.minerguy341.new_age_thaum.core.aspect.AspectReloadListener;
import io.github.minerguy341.new_age_thaum.core.codex.CodexReloadListener;
import io.github.minerguy341.new_age_thaum.network.NewAgeThaumNetwork;
import io.github.minerguy341.new_age_thaum.platform.PlatformInfo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
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
        /*LOGGER.info("Loader-constant branch active: FABRIC");
        *///?} else {
        LOGGER.info("Loader-constant branch active: NEOFORGE");
        //?}
        LOGGER.info("PlatformInfo resolved: loader={}, developmentEnvironment={}",
                platform.loaderName(), platform.isDevelopmentEnvironment());

        io.github.minerguy341.new_age_thaum.core.NewAgeThaumConfig.load();
        ModRegistries.init();
        io.github.minerguy341.new_age_thaum.core.ModBlockEntities.init();
        io.github.minerguy341.new_age_thaum.core.ModMenus.init();
        io.github.minerguy341.new_age_thaum.core.ModRecipes.init();
        NewAgeThaumNetwork.init();
        ReloadListenerRegistry.register(PackType.SERVER_DATA, new AspectReloadListener(),
                ResourceLocation.fromNamespaceAndPath(MOD_ID, "aspects"));
        ReloadListenerRegistry.register(PackType.SERVER_DATA, new AspectAssignments(),
                ResourceLocation.fromNamespaceAndPath(MOD_ID, "aspect_assignments"));
        ReloadListenerRegistry.register(PackType.SERVER_DATA, new CodexReloadListener(),
                ResourceLocation.fromNamespaceAndPath(MOD_ID, "codex_entries"));
        ReloadListenerRegistry.register(PackType.SERVER_DATA,
                new io.github.minerguy341.new_age_thaum.core.casting.WandMaterialReloadListener(),
                ResourceLocation.fromNamespaceAndPath(MOD_ID, "wand_materials"));
        PlayerEvent.PLAYER_JOIN.register(player -> {
            NewAgeThaumNetwork.syncAspectsTo(player);
            NewAgeThaumNetwork.syncAssignmentsTo(player);
            NewAgeThaumNetwork.syncCodexTo(player);
            NewAgeThaumNetwork.syncWandMaterialsTo(player);
            io.github.minerguy341.new_age_thaum.core.player.PlayerProgressService.syncOnJoin(player);
        });
        dev.architectury.utils.EnvExecutor.runInEnv(dev.architectury.utils.Env.CLIENT,
                () -> io.github.minerguy341.new_age_thaum.client.NewAgeThaumClient::init);
    }
}
