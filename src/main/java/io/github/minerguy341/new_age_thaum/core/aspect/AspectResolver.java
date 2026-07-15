package io.github.minerguy341.new_age_thaum.core.aspect;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves an item's aspect bag: explicit item assignment, else merged tag
 * assignments, else TC4-style recipe inference (dampened sum of a crafting
 * recipe's ingredient aspects), lazily memoized per side. Lazy evaluation
 * sidesteps reload-listener ordering: by the time anything asks, tags and
 * recipes are bound. Runs identically on server and client (recipes and
 * assignments are both synced), so tooltips never need a round trip.
 */
public final class AspectResolver {
    private static final double DAMPENING = 0.75;
    private static final int PER_ASPECT_CAP = 512;

    private static final Map<Item, AspectBag> CACHE = new ConcurrentHashMap<>();
    private static volatile Map<Item, List<RecipeHolder<CraftingRecipe>>> recipesByResult;

    private AspectResolver() {
    }

    public static void invalidate() {
        CACHE.clear();
        recipesByResult = null;
    }

    public static AspectBag resolve(Item item, Level level) {
        AspectBag cached = CACHE.get(item);
        if (cached != null) {
            return cached;
        }

        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item);
        AspectBag explicit = AspectAssignments.forItemId(itemId).orElse(null);
        if (explicit != null) {
            CACHE.put(item, explicit);
            return explicit;
        }

        AspectBag fromTags = resolveFromTags(item);
        if (fromTags != null) {
            CACHE.put(item, fromTags);
            return fromTags;
        }

        // Sentinel guards recipe cycles: anything re-entering for this item sees EMPTY.
        CACHE.put(item, AspectBag.EMPTY);
        AspectBag inferred = infer(item, level);
        CACHE.put(item, inferred);
        return inferred;
    }

    private static AspectBag resolveFromTags(Item item) {
        AspectBag merged = null;
        for (var tagKey : item.builtInRegistryHolder().tags().toList()) {
            AspectBag bag = AspectAssignments.forTagId(tagKey.location()).orElse(null);
            if (bag != null) {
                merged = merged == null ? bag : merged.max(bag);
            }
        }
        return merged;
    }

    private static AspectBag infer(Item item, Level level) {
        AspectBag best = AspectBag.EMPTY;
        for (RecipeHolder<CraftingRecipe> holder : recipesFor(item, level)) {
            CraftingRecipe recipe = holder.value();
            ItemStack result = recipe.getResultItem(level.registryAccess());
            AspectBag sum = AspectBag.EMPTY;
            for (Ingredient ingredient : recipe.getIngredients()) {
                ItemStack[] options = ingredient.getItems();
                if (options.length == 0) {
                    continue;
                }
                sum = sum.add(resolve(options[0].getItem(), level));
            }
            if (sum.isEmpty()) {
                continue;
            }
            AspectBag candidate = sum.dampen(DAMPENING, Math.max(1, result.getCount()), PER_ASPECT_CAP);
            if (best.isEmpty() || candidate.total() < best.total()) {
                best = candidate;
            }
        }
        return best;
    }

    private static List<RecipeHolder<CraftingRecipe>> recipesFor(Item item, Level level) {
        Map<Item, List<RecipeHolder<CraftingRecipe>>> index = recipesByResult;
        if (index == null) {
            index = new HashMap<>();
            for (RecipeHolder<CraftingRecipe> holder : level.getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING)) {
                ItemStack result = holder.value().getResultItem(level.registryAccess());
                if (!result.isEmpty()) {
                    index.computeIfAbsent(result.getItem(), key -> new ArrayList<>()).add(holder);
                }
            }
            recipesByResult = index;
        }
        return index.getOrDefault(item, List.of());
    }
}
