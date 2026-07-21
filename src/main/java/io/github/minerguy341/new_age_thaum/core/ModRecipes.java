package io.github.minerguy341.new_age_thaum.core;

import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import io.github.minerguy341.new_age_thaum.NewAgeThaum;
import io.github.minerguy341.new_age_thaum.content.ArcaneCraftingRecipe;
import io.github.minerguy341.new_age_thaum.content.WandAssemblyRecipe;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;

/** Custom recipe serializers and types. */
public final class ModRecipes {
    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS =
            DeferredRegister.create(NewAgeThaum.MOD_ID, Registries.RECIPE_SERIALIZER);
    public static final DeferredRegister<RecipeType<?>> TYPES =
            DeferredRegister.create(NewAgeThaum.MOD_ID, Registries.RECIPE_TYPE);

    public static final RegistrySupplier<SimpleCraftingRecipeSerializer<WandAssemblyRecipe>> WAND_ASSEMBLY =
            SERIALIZERS.register("wand_assembly",
                    () -> new SimpleCraftingRecipeSerializer<>(WandAssemblyRecipe::new));

    /** Vis-gated worktable crafting: matched only at the Arcane Worktable, never a plain table. */
    public static final RegistrySupplier<ArcaneCraftingRecipe.Serializer> ARCANE_CRAFTING =
            SERIALIZERS.register("arcane_crafting", ArcaneCraftingRecipe.Serializer::new);

    public static final RegistrySupplier<RecipeType<ArcaneCraftingRecipe>> ARCANE_CRAFTING_TYPE =
            TYPES.register("arcane_crafting", () -> new RecipeType<>() {
                @Override
                public String toString() {
                    return NewAgeThaum.MOD_ID + ":arcane_crafting";
                }
            });

    private ModRecipes() {
    }

    public static void init() {
        SERIALIZERS.register();
        TYPES.register();
    }
}
