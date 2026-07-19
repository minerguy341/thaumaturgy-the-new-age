package io.github.minerguy341.new_age_thaum.core;

import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import io.github.minerguy341.new_age_thaum.NewAgeThaum;
import io.github.minerguy341.new_age_thaum.content.ArcaneOrreryBlockEntity;
import io.github.minerguy341.new_age_thaum.content.AuraNodeBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;

/** Block entities. The orrery's holds the research paper + painted sphere state. */
public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(NewAgeThaum.MOD_ID, Registries.BLOCK_ENTITY_TYPE);

    public static final RegistrySupplier<BlockEntityType<ArcaneOrreryBlockEntity>> ARCANE_ORRERY =
            BLOCK_ENTITIES.register("arcane_orrery", () -> BlockEntityType.Builder.of(
                    ArcaneOrreryBlockEntity::new, ModRegistries.ARCANE_ORRERY.get()).build(null));

    public static final RegistrySupplier<BlockEntityType<AuraNodeBlockEntity>> AURA_NODE =
            BLOCK_ENTITIES.register("aura_node", () -> BlockEntityType.Builder.of(
                    AuraNodeBlockEntity::new,
                    ModRegistries.AURA_NODE.get()).build(null));

    private ModBlockEntities() {
    }

    public static void init() {
        BLOCK_ENTITIES.register();
    }
}
