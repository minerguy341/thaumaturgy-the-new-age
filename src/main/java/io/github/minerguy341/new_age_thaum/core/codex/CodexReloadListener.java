package io.github.minerguy341.new_age_thaum.core.codex;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import io.github.minerguy341.new_age_thaum.NewAgeThaum;
import io.github.minerguy341.new_age_thaum.network.NewAgeThaumNetwork;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.LinkedHashMap;
import java.util.Map;

/** Loads {@code data/<ns>/codex_entries/*.json}; the file name is the entry id. */
public final class CodexReloadListener extends SimpleJsonResourceReloadListener {
    public static final String DIRECTORY = "codex_entries";

    public CodexReloadListener() {
        super(new Gson(), DIRECTORY);
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager resourceManager, ProfilerFiller profiler) {
        Map<ResourceLocation, CodexEntry> parsed = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, JsonElement> entry : files.entrySet()) {
            CodexEntry.Data.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, entry.getValue())
                    .resultOrPartial(error ->
                            NewAgeThaum.LOGGER.warn("Skipping malformed codex entry {}: {}", entry.getKey(), error))
                    .ifPresent(data -> parsed.put(entry.getKey(),
                            new CodexEntry(entry.getKey(), data.category(), data.titleKey(), data.icon(), data.x(), data.y())));
        }
        int accepted = CodexRegistry.reload(parsed);
        NewAgeThaum.LOGGER.info("Loaded {} codex entries ({} files)", accepted, files.size());

        MinecraftServer server = dev.architectury.utils.GameInstance.getServer();
        if (server != null) {
            NewAgeThaumNetwork.syncCodexToAll(server);
        }
    }
}
