package io.github.minerguy341.new_age_thaum.core.aspect;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.minerguy341.new_age_thaum.NewAgeThaum;
import io.github.minerguy341.new_age_thaum.network.NewAgeThaumNetwork;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Loads {@code data/<ns>/aspect_assignments/*.json}. Each file holds entries whose
 * {@code matches} are item ids or {@code #tag} ids; entries carry an aspect bag
 * (an empty bag opts the item out of inference). Item matches beat tag matches;
 * multiple matching tags merge per-aspect by max (see {@link AspectResolver}).
 */
public final class AspectAssignments extends SimpleJsonResourceReloadListener {
    public static final String DIRECTORY = "aspect_assignments";

    private record Entry(List<String> matches, AspectBag aspects) {
        static final Codec<Entry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.listOf().fieldOf("matches").forGetter(Entry::matches),
                AspectBag.CODEC.fieldOf("aspects").forGetter(Entry::aspects)
        ).apply(instance, Entry::new));
    }

    private static final Codec<List<Entry>> FILE_CODEC = Entry.CODEC.listOf().fieldOf("entries").codec();

    private static volatile Map<ResourceLocation, AspectBag> byItem = Map.of();
    private static volatile Map<ResourceLocation, AspectBag> byTag = Map.of();

    public AspectAssignments() {
        super(new Gson(), DIRECTORY);
    }

    public static Optional<AspectBag> forItemId(ResourceLocation itemId) {
        return Optional.ofNullable(byItem.get(itemId));
    }

    public static Optional<AspectBag> forTagId(ResourceLocation tagId) {
        return Optional.ofNullable(byTag.get(tagId));
    }

    public static Map<ResourceLocation, AspectBag> itemAssignments() {
        return byItem;
    }

    public static Map<ResourceLocation, AspectBag> tagAssignments() {
        return byTag;
    }

    /** Replace contents from a reload or a server sync; invalidates the resolver cache. */
    public static void accept(Map<ResourceLocation, AspectBag> items, Map<ResourceLocation, AspectBag> tags) {
        byItem = Map.copyOf(items);
        byTag = Map.copyOf(tags);
        AspectResolver.invalidate();
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager resourceManager, ProfilerFiller profiler) {
        Map<ResourceLocation, AspectBag> items = new HashMap<>();
        Map<ResourceLocation, AspectBag> tags = new HashMap<>();
        // Sorted file order: scanDirectory hands over a plain HashMap, so duplicate
        // matches across files would otherwise resolve by hash order — nondeterministic
        // across JVMs. Later file (by id) wins, and conflicts get a log line.
        for (Map.Entry<ResourceLocation, JsonElement> file : files.entrySet().stream()
                .sorted(Map.Entry.comparingByKey()).toList()) {
            List<Entry> entries = FILE_CODEC.parse(JsonOps.INSTANCE, file.getValue())
                    .resultOrPartial(error ->
                            NewAgeThaum.LOGGER.warn("Skipping malformed aspect assignments {}: {}", file.getKey(), error))
                    .orElse(List.of());
            for (Entry entry : entries) {
                for (String match : entry.matches()) {
                    if (match.startsWith("#")) {
                        ResourceLocation tagId = ResourceLocation.tryParse(match.substring(1));
                        if (tagId != null) {
                            if (tags.put(tagId, entry.aspects()) != null) {
                                NewAgeThaum.LOGGER.warn("Duplicate aspect assignment for tag #{} — {} wins", tagId, file.getKey());
                            }
                        } else {
                            NewAgeThaum.LOGGER.warn("Bad tag id '{}' in {}", match, file.getKey());
                        }
                    } else {
                        ResourceLocation itemId = ResourceLocation.tryParse(match);
                        if (itemId != null) {
                            if (items.put(itemId, entry.aspects()) != null) {
                                NewAgeThaum.LOGGER.warn("Duplicate aspect assignment for item {} — {} wins", itemId, file.getKey());
                            }
                        } else {
                            NewAgeThaum.LOGGER.warn("Bad item id '{}' in {}", match, file.getKey());
                        }
                    }
                }
            }
        }
        accept(items, tags);
        NewAgeThaum.LOGGER.info("Loaded aspect assignments: {} item entries, {} tag entries", items.size(), tags.size());

        MinecraftServer server = dev.architectury.utils.GameInstance.getServer();
        if (server != null) {
            NewAgeThaumNetwork.syncAssignmentsToAll(server);
        }
    }
}
