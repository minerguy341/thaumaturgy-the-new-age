//? if fabric {
/*package io.github.minerguy341.new_age_thaum.platform.fabric;

import io.github.minerguy341.new_age_thaum.NewAgeThaum;
import io.github.minerguy341.new_age_thaum.core.player.PlayerProgressService;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.event.lifecycle.v1.CommonLifecycleEvents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.levelgen.GenerationStep;

public final class NewAgeThaumFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        PlayerProgressService.setBridge(new FabricPlayerData());
        NewAgeThaum.init(new FabricPlatformInfo());
        // Tags rebind AFTER the reload listeners run (and after the client applies the
        // tag packet), so the resolver must drop its cache again once the new bindings
        // are live — the assignment-time invalidation alone leaves stale-tag bags stuck.
        CommonLifecycleEvents.TAGS_LOADED.register((registries, client) ->
                io.github.minerguy341.new_age_thaum.core.aspect.AspectResolver.invalidate());
        // Aura-node worldgen: NeoForge injects via data/neoforge/biome_modifier JSON;
        // Fabric has no datapack equivalent, so the same tag + placed feature wire here.
        BiomeModifications.addFeature(
                BiomeSelectors.tag(TagKey.create(Registries.BIOME, NewAgeThaum.id("has_aura_nodes"))),
                GenerationStep.Decoration.VEGETAL_DECORATION,
                ResourceKey.create(Registries.PLACED_FEATURE, NewAgeThaum.id("aura_nodes")));
    }
}
*///?}
