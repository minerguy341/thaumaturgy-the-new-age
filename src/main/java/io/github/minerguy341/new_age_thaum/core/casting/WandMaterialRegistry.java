package io.github.minerguy341.new_age_thaum.core.casting;

import net.minecraft.resources.ResourceLocation;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/** Datapack-reloadable store of wand materials, rebuilt each reload and synced to clients. */
public final class WandMaterialRegistry {
    private static volatile Map<ResourceLocation, WandMaterial> materials = Map.of();

    private WandMaterialRegistry() {
    }

    public static Collection<WandMaterial> all() {
        return materials.values();
    }

    public static Optional<WandMaterial> get(ResourceLocation id) {
        return Optional.ofNullable(materials.get(id));
    }

    public static int reload(Map<ResourceLocation, WandMaterial> incoming) {
        materials = Map.copyOf(incoming);
        return materials.size();
    }
}
