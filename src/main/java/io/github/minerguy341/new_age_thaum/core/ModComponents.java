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

    private ModComponents() {
    }

    public static void init() {
        COMPONENTS.register();
    }
}
