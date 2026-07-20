package io.github.minerguy341.new_age_thaum.content;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.minerguy341.new_age_thaum.core.ModRecipes;
import io.github.minerguy341.new_age_thaum.core.aspect.AspectBag;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * A shapeless crafting recipe that ALSO carries a vis cost, matched only at the Arcane
 * Worktable (a separate {@link RecipeType} — deliberately NOT a vanilla crafting recipe so
 * these never resolve in a plain crafting table). The grid stays a familiar 3×3; the vis
 * cost is the augment, gated on the wand in the worktable's tool slot.
 *
 * <p>Matching is strict-shapeless: the number of non-empty grid stacks must equal the
 * ingredient count, and a one-to-one assignment of ingredients to stacks must exist. Because
 * of that, crafting consumes exactly one item from each non-empty grid slot (see the menu).
 */
public record ArcaneCraftingRecipe(NonNullList<Ingredient> ingredients, ItemStack result, AspectBag visCost)
        implements Recipe<CraftingInput> {

    @Override
    public boolean matches(CraftingInput input, Level level) {
        if (input.ingredientCount() != ingredients.size()) {
            return false;
        }
        // Greedy one-to-one assignment of ingredients to the non-empty stacks. Sufficient
        // for our recipes (no overlapping-predicate ingredients); vanilla uses StackedContents
        // for the fully general case, which we can adopt if a future recipe needs it.
        List<ItemStack> stacks = new java.util.ArrayList<>();
        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (!stack.isEmpty()) {
                stacks.add(stack);
            }
        }
        if (stacks.size() != ingredients.size()) {
            return false;
        }
        boolean[] used = new boolean[stacks.size()];
        for (Ingredient ingredient : ingredients) {
            boolean matched = false;
            for (int i = 0; i < stacks.size(); i++) {
                if (!used[i] && ingredient.test(stacks.get(i))) {
                    used[i] = true;
                    matched = true;
                    break;
                }
            }
            if (!matched) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        return result.copy();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= ingredients.size();
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        return result;
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        return ingredients;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipes.ARCANE_CRAFTING.get();
    }

    @Override
    public RecipeType<?> getType() {
        return ModRecipes.ARCANE_CRAFTING_TYPE.get();
    }

    /** Serializer: shapeless ingredient list + a result stack + an integer vis cost. */
    public static final class Serializer implements RecipeSerializer<ArcaneCraftingRecipe> {
        public static final MapCodec<ArcaneCraftingRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Ingredient.CODEC_NONEMPTY.listOf().fieldOf("ingredients").forGetter(r -> r.ingredients),
                ItemStack.STRICT_CODEC.fieldOf("result").forGetter(ArcaneCraftingRecipe::result),
                AspectBag.CODEC.optionalFieldOf("vis", AspectBag.EMPTY).forGetter(ArcaneCraftingRecipe::visCost)
        ).apply(instance, (ingredients, result, vis) -> new ArcaneCraftingRecipe(toNonNull(ingredients), result, vis)));

        public static final StreamCodec<RegistryFriendlyByteBuf, ArcaneCraftingRecipe> STREAM_CODEC =
                StreamCodec.of(Serializer::toNetwork, Serializer::fromNetwork);

        private static NonNullList<Ingredient> toNonNull(List<Ingredient> list) {
            NonNullList<Ingredient> out = NonNullList.create();
            out.addAll(list);
            return out;
        }

        private static void toNetwork(RegistryFriendlyByteBuf buf, ArcaneCraftingRecipe recipe) {
            buf.writeVarInt(recipe.ingredients.size());
            for (Ingredient ingredient : recipe.ingredients) {
                Ingredient.CONTENTS_STREAM_CODEC.encode(buf, ingredient);
            }
            ItemStack.STREAM_CODEC.encode(buf, recipe.result);
            AspectBag.STREAM_CODEC.encode(buf, recipe.visCost);
        }

        private static ArcaneCraftingRecipe fromNetwork(RegistryFriendlyByteBuf buf) {
            int count = buf.readVarInt();
            NonNullList<Ingredient> ingredients = NonNullList.withSize(count, Ingredient.EMPTY);
            for (int i = 0; i < count; i++) {
                ingredients.set(i, Ingredient.CONTENTS_STREAM_CODEC.decode(buf));
            }
            ItemStack result = ItemStack.STREAM_CODEC.decode(buf);
            AspectBag vis = AspectBag.STREAM_CODEC.decode(buf);
            return new ArcaneCraftingRecipe(ingredients, result, vis);
        }

        @Override
        public MapCodec<ArcaneCraftingRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, ArcaneCraftingRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
