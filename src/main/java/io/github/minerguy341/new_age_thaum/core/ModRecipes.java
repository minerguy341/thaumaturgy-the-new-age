package io.github.minerguy341.new_age_thaum.core;

import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import io.github.minerguy341.new_age_thaum.NewAgeThaum;
import io.github.minerguy341.new_age_thaum.content.WandAssemblyRecipe;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;

/** Custom recipe serializers. */
public final class ModRecipes {
    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS =
            DeferredRegister.create(NewAgeThaum.MOD_ID, Registries.RECIPE_SERIALIZER);

    public static final RegistrySupplier<SimpleCraftingRecipeSerializer<WandAssemblyRecipe>> WAND_ASSEMBLY =
            SERIALIZERS.register("wand_assembly",
                    () -> new SimpleCraftingRecipeSerializer<>(WandAssemblyRecipe::new));

    private ModRecipes() {
    }

    public static void init() {
        SERIALIZERS.register();
    }
}
