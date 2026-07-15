package io.github.minerguy341.new_age_thaum.core.casting;

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

/** Loads {@code data/<ns>/wand_materials/*.json}; the file name is the material id. */
public final class WandMaterialReloadListener extends SimpleJsonResourceReloadListener {
    public static final String DIRECTORY = "wand_materials";

    public WandMaterialReloadListener() {
        super(new Gson(), DIRECTORY);
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager resourceManager, ProfilerFiller profiler) {
        Map<ResourceLocation, WandMaterial> parsed = new HashMap<>();
        for (Map.Entry<ResourceLocation, JsonElement> entry : files.entrySet()) {
            WandMaterial.Data.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, entry.getValue())
                    .resultOrPartial(error ->
                            NewAgeThaum.LOGGER.warn("Skipping malformed wand material {}: {}", entry.getKey(), error))
                    .ifPresent(data -> parsed.put(entry.getKey(), new WandMaterial(entry.getKey(), data.kind(),
                            data.color(), data.capacity(), data.discount(), data.potency(), data.rechargeAffinity())));
        }
        int accepted = WandMaterialRegistry.reload(parsed);
        NewAgeThaum.LOGGER.info("Loaded {} wand materials ({} files)", accepted, files.size());

        MinecraftServer server = dev.architectury.utils.GameInstance.getServer();
        if (server != null) {
            NewAgeThaumNetwork.syncWandMaterialsToAll(server);
        }
    }
}
