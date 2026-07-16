package io.github.minerguy341.new_age_thaum.content;

import io.github.minerguy341.new_age_thaum.core.ModComponents;
import io.github.minerguy341.new_age_thaum.core.ModRecipes;
import io.github.minerguy341.new_age_thaum.core.ModRegistries;
import io.github.minerguy341.new_age_thaum.core.casting.WandComponent;
import io.github.minerguy341.new_age_thaum.core.casting.WandForm;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;

import java.util.ArrayList;
import java.util.List;

/**
 * Assembles any rod + any two caps into a wand (or two matching rods + two caps into a
 * stave). A special (shapeless, dynamic) recipe rather than one recipe per combination:
 * it reads the actual {@link WandPartItem}s placed and stamps the result's {@link WandComponent}.
 */
public class WandAssemblyRecipe extends CustomRecipe {
    public WandAssemblyRecipe(CraftingBookCategory category) {
        super(category);
    }

    private record Parts(ResourceLocation core, ResourceLocation capA, ResourceLocation capB, WandForm form) {
    }

    /** Returns the assembled parts if the grid holds exactly a valid rod/cap set, else null. */
    private static Parts parse(CraftingInput input) {
        List<ResourceLocation> rods = new ArrayList<>();
        List<ResourceLocation> caps = new ArrayList<>();
        for (ItemStack stack : input.items()) {
            if (stack.isEmpty()) {
                continue;
            }
            if (stack.getItem() instanceof WandPartItem part && part.isRod()) {
                rods.add(part.materialId());
            } else if (stack.getItem() instanceof WandPartItem part && part.isCap()) {
                caps.add(part.materialId());
            } else {
                return null; // any foreign item invalidates the assembly
            }
        }

        if (caps.size() != 2 || rods.isEmpty() || rods.size() > 2) {
            return null;
        }
        if (rods.size() == 2 && !rods.get(0).equals(rods.get(1))) {
            return null; // a stave needs two rods of the same core
        }

        caps.sort(java.util.Comparator.comparing(ResourceLocation::toString));
        WandForm form = rods.size() == 2 ? WandForm.STAVE : WandForm.WAND;
        return new Parts(rods.get(0), caps.get(0), caps.get(1), form);
    }

    @Override
    public boolean matches(CraftingInput input, net.minecraft.world.level.Level level) {
        return parse(input) != null;
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        Parts parts = parse(input);
        if (parts == null) {
            return ItemStack.EMPTY;
        }
        ItemStack result = new ItemStack(parts.form() == WandForm.STAVE ? ModRegistries.STAVE.get() : ModRegistries.WAND.get());
        result.set(ModComponents.WAND.get(), new WandComponent(parts.core(), parts.capA(), parts.capB()));
        return result;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 3;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipes.WAND_ASSEMBLY.get();
    }
}
