package io.github.minerguy341.new_age_thaum.gametest;

import io.github.minerguy341.new_age_thaum.NewAgeThaum;
import io.github.minerguy341.new_age_thaum.content.ArcaneCraftingRecipe;
import io.github.minerguy341.new_age_thaum.core.ModComponents;
import io.github.minerguy341.new_age_thaum.core.ModRegistries;
import io.github.minerguy341.new_age_thaum.core.casting.WandComponent;
import io.github.minerguy341.new_age_thaum.core.casting.WandForm;
import io.github.minerguy341.new_age_thaum.core.casting.WandStats;
import io.github.minerguy341.new_age_thaum.core.casting.WandVis;
import net.minecraft.core.NonNullList;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.List;

/**
 * Server-side logic for the Arcane Worktable: the vis-gated recipe matches/assembles, the wand
 * vis reservoir clamps to capacity, and the cap discount lowers the effective cost. The menu's
 * click-to-craft flow is UI-driven and lives in the human smoke test.
 */
//? if neoforge {
@net.neoforged.neoforge.gametest.GameTestHolder(NewAgeThaum.MOD_ID)
@net.neoforged.neoforge.gametest.PrefixGameTestTemplate(false)
//?}
public class ArcaneWorktableGameTest {

    private static ArcaneCraftingRecipe stoneRecipe() {
        NonNullList<Ingredient> ingredients = NonNullList.withSize(4, Ingredient.of(Items.STONE));
        return new ArcaneCraftingRecipe(ingredients,
                new ItemStack(ModRegistries.ARCANE_STONE_ITEM.get(), 4), 20);
    }

    private static CraftingInput grid(ItemStack... first) {
        java.util.List<ItemStack> items = new java.util.ArrayList<>(List.of(first));
        while (items.size() < 9) {
            items.add(ItemStack.EMPTY);
        }
        return CraftingInput.of(3, 3, items);
    }

    //? if neoforge {
    @GameTest(template = "empty")
    //?} else {
    /*@GameTest(template = "new_age_thaum:empty")
    *///?}
    public void fourStoneMatchesAndAssembles(GameTestHelper helper) {
        ArcaneCraftingRecipe recipe = stoneRecipe();
        CraftingInput ok = grid(new ItemStack(Items.STONE), new ItemStack(Items.STONE),
                new ItemStack(Items.STONE), new ItemStack(Items.STONE));
        helper.assertTrue(recipe.matches(ok, helper.getLevel()), "Four stone should match the arcane recipe");
        ItemStack result = recipe.assemble(ok, helper.getLevel().registryAccess());
        helper.assertTrue(result.is(ModRegistries.ARCANE_STONE_ITEM.get()) && result.getCount() == 4,
                "Should assemble 4 arcane stone, got " + result);
        helper.assertTrue(recipe.visCost() == 20, "Recipe vis cost should be 20");
        helper.succeed();
    }

    //? if neoforge {
    @GameTest(template = "empty")
    //?} else {
    /*@GameTest(template = "new_age_thaum:empty")
    *///?}
    public void wrongCountAndForeignItemRejected(GameTestHelper helper) {
        ArcaneCraftingRecipe recipe = stoneRecipe();
        CraftingInput three = grid(new ItemStack(Items.STONE), new ItemStack(Items.STONE),
                new ItemStack(Items.STONE));
        helper.assertFalse(recipe.matches(three, helper.getLevel()), "Three stone must not match (count)");
        CraftingInput withDirt = grid(new ItemStack(Items.STONE), new ItemStack(Items.STONE),
                new ItemStack(Items.STONE), new ItemStack(Items.DIRT));
        helper.assertFalse(recipe.matches(withDirt, helper.getLevel()), "A foreign item must invalidate the match");
        helper.succeed();
    }

    //? if neoforge {
    @GameTest(template = "empty")
    //?} else {
    /*@GameTest(template = "new_age_thaum:empty")
    *///?}
    public void wandReservoirClampsToCapacity(GameTestHelper helper) {
        ItemStack wand = new ItemStack(ModRegistries.WAND.get());
        wand.set(ModComponents.WAND.get(),
                new WandComponent(NewAgeThaum.id("greatwood"), NewAgeThaum.id("brass"), NewAgeThaum.id("aetherium")));
        float cap = WandVis.capacity(wand);
        helper.assertTrue(Math.round(cap) == 50, "Greatwood wand capacity should be 50, got " + cap);

        WandVis.set(wand, 999f);
        helper.assertTrue(Math.round(WandVis.get(wand)) == 50, "Vis must clamp to capacity (50)");
        WandVis.set(wand, 30f);
        WandVis.add(wand, -40f);
        helper.assertTrue(WandVis.get(wand) == 0f, "Draining below 0 must clamp to 0");
        helper.assertTrue(WandVis.isReservoir(wand), "An assembled wand is a vis reservoir");
        helper.succeed();
    }

    //? if neoforge {
    @GameTest(template = "empty")
    //?} else {
    /*@GameTest(template = "new_age_thaum:empty")
    *///?}
    public void capDiscountLowersEffectiveCost(GameTestHelper helper) {
        WandComponent component = new WandComponent(
                NewAgeThaum.id("greatwood"), NewAgeThaum.id("brass"), NewAgeThaum.id("aetherium"));
        double discount = WandStats.compute(component, WandForm.WAND).discount();
        helper.assertTrue(Math.abs(discount - 0.15) < 1.0e-6,
                "Brass(0.10)+Aetherium(0.05) discount should be 0.15, got " + discount);
        int base = 20;
        int effective = Math.max(0, (int) Math.ceil(base * (1.0 - discount)));
        helper.assertTrue(effective == 17, "20 vis at 15% discount should round up to 17, got " + effective);
        helper.succeed();
    }
}
