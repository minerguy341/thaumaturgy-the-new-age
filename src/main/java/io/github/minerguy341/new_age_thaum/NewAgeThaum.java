package io.github.minerguy341.new_age_thaum;

import dev.architectury.event.events.common.PlayerEvent;
import dev.architectury.event.events.common.TickEvent;
import dev.architectury.registry.ReloadListenerRegistry;
import io.github.minerguy341.new_age_thaum.core.ModBlockEntities;
import io.github.minerguy341.new_age_thaum.core.ModCommands;
import io.github.minerguy341.new_age_thaum.core.ModMenus;
import io.github.minerguy341.new_age_thaum.core.ModRecipes;
import io.github.minerguy341.new_age_thaum.core.ModRegistries;
import io.github.minerguy341.new_age_thaum.core.NewAgeThaumConfig;
import io.github.minerguy341.new_age_thaum.core.aspect.AspectAssignments;
import io.github.minerguy341.new_age_thaum.core.aspect.AspectReloadListener;
import io.github.minerguy341.new_age_thaum.core.casting.WandMaterialReloadListener;
import io.github.minerguy341.new_age_thaum.core.codex.CodexReloadListener;
import io.github.minerguy341.new_age_thaum.core.player.PlayerProgressService;
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

    /** A ResourceLocation in this mod's namespace — the single spelling of the mod id. */
    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    public static void init(PlatformInfo platform) {
        //? if fabric {
        /*LOGGER.info("Loader-constant branch active: FABRIC");
        *///?} else {
        LOGGER.info("Loader-constant branch active: NEOFORGE");
        //?}
        LOGGER.info("PlatformInfo resolved: loader={}, developmentEnvironment={}",
                platform.loaderName(), platform.isDevelopmentEnvironment());

        NewAgeThaumConfig.load();
        ModRegistries.init();
        ModBlockEntities.init();
        ModMenus.init();
        ModRecipes.init();
        ModCommands.init();
        NewAgeThaumNetwork.init();
        ReloadListenerRegistry.register(PackType.SERVER_DATA, new AspectReloadListener(), id("aspects"));
        ReloadListenerRegistry.register(PackType.SERVER_DATA, new AspectAssignments(), id("aspect_assignments"));
        ReloadListenerRegistry.register(PackType.SERVER_DATA, new CodexReloadListener(), id("codex_entries"));
        ReloadListenerRegistry.register(PackType.SERVER_DATA, new WandMaterialReloadListener(), id("wand_materials"));
        // Aura diffusion: budgeted pass every 2 seconds per dimension (PLAN §4.3/§5).
        TickEvent.SERVER_LEVEL_POST.register(level -> {
            if (level.getGameTime() % 40 == 0) {
                io.github.minerguy341.new_age_thaum.core.aura.AuraField.get(level).diffuse();
            }
        });
        PlayerEvent.PLAYER_JOIN.register(NewAgeThaum::syncAllTo);
        // Respawn rebuilds the client player: resync so the mirrors can't go stale
        // (and so any client-side reset around the respawn edge heals immediately).
        PlayerEvent.PLAYER_RESPAWN.register((player, conqueredEnd) -> syncAllTo(player));
        dev.architectury.utils.EnvExecutor.runInEnv(dev.architectury.utils.Env.CLIENT,
                () -> io.github.minerguy341.new_age_thaum.client.NewAgeThaumClient::init);
    }

    /** Everything a client mirror needs, sent on join and respawn. */
    private static void syncAllTo(net.minecraft.server.level.ServerPlayer player) {
        NewAgeThaumNetwork.syncAspectsTo(player);
        NewAgeThaumNetwork.syncAssignmentsTo(player);
        NewAgeThaumNetwork.syncCodexTo(player);
        NewAgeThaumNetwork.syncWandMaterialsTo(player);
        PlayerProgressService.syncOnJoin(player);
    }
}
