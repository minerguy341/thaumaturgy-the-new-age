package io.github.minerguy341.new_age_thaum.core;

import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import io.github.minerguy341.new_age_thaum.NewAgeThaum;
import io.github.minerguy341.new_age_thaum.content.NodeTreeFeature;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.feature.Feature;

/**
 * Custom worldgen features. Registered at mod construction so the datapack's
 * configured_feature JSON can resolve {@code new_age_thaum:node_tree} when it loads.
 */
public final class ModFeatures {
    public static final DeferredRegister<Feature<?>> FEATURES =
            DeferredRegister.create(NewAgeThaum.MOD_ID, Registries.FEATURE);

    /** Grows a tree, then rarely embeds an aura node in the trunk (silverwood groves). */
    public static final RegistrySupplier<Feature<?>> NODE_TREE =
            FEATURES.register("node_tree", NodeTreeFeature::new);

    private ModFeatures() {
    }

    public static void init() {
        FEATURES.register();
    }
}
