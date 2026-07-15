package io.github.minerguy341.new_age_thaum.core.codex;

import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Datapack-reloadable store of Codex entries, rebuilt each reload and synced to clients. */
public final class CodexRegistry {
    private static volatile Map<ResourceLocation, CodexEntry> entries = Map.of();

    private CodexRegistry() {
    }

    public static java.util.Collection<CodexEntry> all() {
        return entries.values();
    }

    /** Entries in one category, in stable insertion order. */
    public static List<CodexEntry> byCategory(String category) {
        List<CodexEntry> result = new ArrayList<>();
        for (CodexEntry entry : entries.values()) {
            if (entry.category().equals(category)) {
                result.add(entry);
            }
        }
        return result;
    }

    public static List<String> categories() {
        List<String> result = new ArrayList<>();
        for (CodexEntry entry : entries.values()) {
            if (!result.contains(entry.category())) {
                result.add(entry.category());
            }
        }
        return result;
    }

    public static int reload(Map<ResourceLocation, CodexEntry> incoming) {
        entries = new LinkedHashMap<>(incoming);
        return entries.size();
    }
}
