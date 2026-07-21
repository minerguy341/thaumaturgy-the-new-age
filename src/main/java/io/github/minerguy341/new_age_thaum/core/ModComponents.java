package io.github.minerguy341.new_age_thaum.core;

import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import io.github.minerguy341.new_age_thaum.NewAgeThaum;
import io.github.minerguy341.new_age_thaum.core.casting.WandComponent;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;

/** Custom data components. The wand component carries the core + cap material ids. */
public final class ModComponents {
    public static final DeferredRegister<DataComponentType<?>> COMPONENTS =
            DeferredRegister.create(NewAgeThaum.MOD_ID, Registries.DATA_COMPONENT_TYPE);

    public static final RegistrySupplier<DataComponentType<WandComponent>> WAND = COMPONENTS.register("wand",
            () -> DataComponentType.<WandComponent>builder()
                    .persistent(WandComponent.CODEC)
                    .networkSynchronized(WandComponent.STREAM_CODEC)
                    .build());

    /** Per-primal vis stored on a wand/stave (sibling of WAND so shipped wands keep loading). */
    public static final RegistrySupplier<DataComponentType<io.github.minerguy341.new_age_thaum.core.casting.WandVis>> WAND_VIS =
            COMPONENTS.register("wand_vis",
                    () -> DataComponentType.<io.github.minerguy341.new_age_thaum.core.casting.WandVis>builder()
                            .persistent(io.github.minerguy341.new_age_thaum.core.casting.WandVis.CODEC)
                            .networkSynchronized(io.github.minerguy341.new_age_thaum.core.casting.WandVis.STREAM_CODEC)
                            .build());

    /** The generated puzzle definition (frequency, endpoints, gaps) on a research paper. */
    public static final RegistrySupplier<DataComponentType<io.github.minerguy341.new_age_thaum.core.research.ResearchPuzzle>> RESEARCH_PUZZLE =
            COMPONENTS.register("research_puzzle",
                    () -> DataComponentType.<io.github.minerguy341.new_age_thaum.core.research.ResearchPuzzle>builder()
                            .persistent(io.github.minerguy341.new_age_thaum.core.research.ResearchPuzzle.CODEC)
                            .networkSynchronized(io.github.minerguy341.new_age_thaum.core.research.ResearchPuzzle.STREAM_CODEC)
                            .build());

    /** The painted research sphere, carried by the research paper itself. */
    public static final RegistrySupplier<DataComponentType<io.github.minerguy341.new_age_thaum.core.research.ResearchSphereData>> RESEARCH_SPHERE =
            COMPONENTS.register("research_sphere",
                    () -> DataComponentType.<io.github.minerguy341.new_age_thaum.core.research.ResearchSphereData>builder()
                            .persistent(io.github.minerguy341.new_age_thaum.core.research.ResearchSphereData.CODEC)
                            .networkSynchronized(io.github.minerguy341.new_age_thaum.core.research.ResearchSphereData.STREAM_CODEC)
                            .build());

    private ModComponents() {
    }

    public static void init() {
        COMPONENTS.register();
    }
}
