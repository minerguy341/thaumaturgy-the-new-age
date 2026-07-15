package io.github.minerguy341.new_age_thaum.core.aspect;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import io.github.minerguy341.new_age_thaum.NewAgeThaum;
import io.github.minerguy341.new_age_thaum.network.NewAgeThaumNetwork;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.HashMap;
import java.util.Map;

/** Loads {@code data/<ns>/aspects/*.json}; the file name is the aspect id. */
public final class AspectReloadListener extends SimpleJsonResourceReloadListener {
    public static final String DIRECTORY = "aspects";

    public AspectReloadListener() {
        super(new Gson(), DIRECTORY);
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager resourceManager, ProfilerFiller profiler) {
        Map<ResourceLocation, Aspect> parsed = new HashMap<>();
        for (Map.Entry<ResourceLocation, JsonElement> entry : files.entrySet()) {
            Aspect.Data.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, entry.getValue())
                    .resultOrPartial(error ->
                            NewAgeThaum.LOGGER.warn("Skipping malformed aspect {}: {}", entry.getKey(), error))
                    .ifPresent(data ->
                            parsed.put(entry.getKey(), new Aspect(entry.getKey(), data.color(), data.components())));
        }
        int accepted = AspectRegistry.reload(parsed);
        NewAgeThaum.LOGGER.info("Loaded {} aspects ({} files)", accepted, files.size());

        MinecraftServer server = dev.architectury.utils.GameInstance.getServer();
        if (server != null) {
            NewAgeThaumNetwork.syncAspectsToAll(server);
        }
    }
}
