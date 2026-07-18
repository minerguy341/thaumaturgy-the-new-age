//? if fabric {
/*package io.github.minerguy341.new_age_thaum.platform.fabric;

import io.github.minerguy341.new_age_thaum.NewAgeThaum;
import io.github.minerguy341.new_age_thaum.core.player.PlayerProgressService;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.levelgen.GenerationStep;

public final class NewAgeThaumFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        PlayerProgressService.setBridge(new FabricPlayerData());
        NewAgeThaum.init(new FabricPlatformInfo());
        // Homage-tree worldgen: NeoForge injects via data/neoforge/biome_modifier JSON;
        // Fabric has no datapack equivalent, so the same biome tags + placed features
        // are wired here in code.
        addTrees("has_greatwood", "greatwood_trees");
        addTrees("has_silverwood", "silverwood_trees");
    }

    private static void addTrees(String biomeTag, String placedFeature) {
        BiomeModifications.addFeature(
                BiomeSelectors.tag(TagKey.create(Registries.BIOME,
                        ResourceLocation.fromNamespaceAndPath(NewAgeThaum.MOD_ID, biomeTag))),
                GenerationStep.Decoration.VEGETAL_DECORATION,
                ResourceKey.create(Registries.PLACED_FEATURE,
                        ResourceLocation.fromNamespaceAndPath(NewAgeThaum.MOD_ID, placedFeature)));
    }
}
*///?}
