//? if fabric {
/*package io.github.minerguy341.new_age_thaum.platform.fabric;

import io.github.minerguy341.new_age_thaum.NewAgeThaum;
import io.github.minerguy341.new_age_thaum.core.ModRegistries;
import io.github.minerguy341.new_age_thaum.core.player.PlayerProgressService;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.minecraft.world.level.levelgen.GenerationStep;

public final class NewAgeThaumFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        PlayerProgressService.setBridge(new FabricPlayerData());
        NewAgeThaum.init(new FabricPlatformInfo());
        // Homage-tree worldgen: NeoForge injects via data/neoforge/biome_modifier JSON;
        // Fabric has no datapack equivalent, so the shared wiring list is applied here.
        for (ModRegistries.TreePlacement placement : ModRegistries.TREE_PLACEMENTS) {
            BiomeModifications.addFeature(BiomeSelectors.tag(placement.biomes()),
                    GenerationStep.Decoration.VEGETAL_DECORATION, placement.feature());
        }
    }
}
*///?}
