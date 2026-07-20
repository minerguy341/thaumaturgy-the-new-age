package io.github.minerguy341.new_age_thaum.gametest;

import io.github.minerguy341.new_age_thaum.NewAgeThaum;
import io.github.minerguy341.new_age_thaum.content.ArcaneCraftingRecipe;
import io.github.minerguy341.new_age_thaum.core.ModComponents;
import io.github.minerguy341.new_age_thaum.core.ModRegistries;
import io.github.minerguy341.new_age_thaum.core.aspect.AspectBag;
import io.github.minerguy341.new_age_thaum.core.aspect.Primals;
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
import java.util.Map;

/**
 * Server-side logic for the Arcane Worktable's per-primal vis: the recipe matches/assembles and
 * carries per-primal costs, the wand reservoir stores/clamps vis per primal, and the cap discount
 * lowers each primal's cost. The menu's click-to-craft flow is UI-driven (human smoke test).
 */
//? if neoforge {
@net.neoforged.neoforge.gametest.GameTestHolder(NewAgeThaum.MOD_ID)
@net.neoforged.neoforge.gametest.PrefixGameTestTemplate(false)
//?}
public class ArcaneWorktableGameTest {

    private static ArcaneCraftingRecipe stoneRecipe() {
        NonNullList<Ingredient> ingredients = NonNullList.withSize(4, Ingredient.of(Items.STONE));
        AspectBag cost = new AspectBag(Map.of(Primals.TELLUS, 8, Primals.FORMA, 6));
        return new ArcaneCraftingRecipe(ingredients, new ItemStack(ModRegistries.ARCANE_STONE_ITEM.get(), 4), cost);
    }

    private static CraftingInput grid(ItemStack... first) {
        java.util.List<ItemStack> items = new java.util.ArrayList<>(List.of(first));
        while (items.size() < 9) {
            items.add(ItemStack.EMPTY);
        }
        return CraftingInput.of(3, 3, items);
    }

    private static ItemStack greatwoodWand() {
        ItemStack wand = new ItemStack(ModRegistries.WAND.get());
        wand.set(ModComponents.WAND.get(),
                new WandComponent(NewAgeThaum.id("greatwood"), NewAgeThaum.id("brass"), NewAgeThaum.id("aetherium")));
        return wand;
    }

    //? if neoforge {
    @GameTest(template = "empty")
    //?} else {
    /*@GameTest(template = "new_age_thaum:empty")
    *///?}
    public void fourStoneMatchesAndCarriesPerPrimalCost(GameTestHelper helper) {
        ArcaneCraftingRecipe recipe = stoneRecipe();
        CraftingInput ok = grid(new ItemStack(Items.STONE), new ItemStack(Items.STONE),
                new ItemStack(Items.STONE), new ItemStack(Items.STONE));
        helper.assertTrue(recipe.matches(ok, helper.getLevel()), "Four stone should match the arcane recipe");
        ItemStack result = recipe.assemble(ok, helper.getLevel().registryAccess());
        helper.assertTrue(result.is(ModRegistries.ARCANE_STONE_ITEM.get()) && result.getCount() == 4,
                "Should assemble 4 arcane stone, got " + result);
        helper.assertTrue(recipe.visCost().amountOf(Primals.TELLUS) == 8, "Recipe should cost 8 Tellus");
        helper.assertTrue(recipe.visCost().amountOf(Primals.FORMA) == 6, "Recipe should cost 6 Forma");
        helper.assertTrue(recipe.visCost().amountOf(Primals.FLAMMA) == 0, "Recipe should not cost Flamma");
        helper.succeed();
    }

    //? if neoforge {
    @GameTest(template = "empty")
    //?} else {
    /*@GameTest(template = "new_age_thaum:empty")
    *///?}
    public void wrongCountAndForeignItemRejected(GameTestHelper helper) {
        ArcaneCraftingRecipe recipe = stoneRecipe();
        helper.assertFalse(recipe.matches(grid(new ItemStack(Items.STONE), new ItemStack(Items.STONE),
                new ItemStack(Items.STONE)), helper.getLevel()), "Three stone must not match (count)");
        helper.assertFalse(recipe.matches(grid(new ItemStack(Items.STONE), new ItemStack(Items.STONE),
                new ItemStack(Items.STONE), new ItemStack(Items.DIRT)), helper.getLevel()),
                "A foreign item must invalidate the match");
        helper.succeed();
    }

    //? if neoforge {
    @GameTest(template = "empty")
    //?} else {
    /*@GameTest(template = "new_age_thaum:empty")
    *///?}
    public void wandReservoirIsPerPrimalAndClamps(GameTestHelper helper) {
        ItemStack wand = greatwoodWand();
        helper.assertTrue(WandVis.capacity(wand) == 50, "Greatwood per-primal capacity should be 50");

        WandVis.set(wand, Primals.TELLUS, 999);
        helper.assertTrue(WandVis.amountOf(wand, Primals.TELLUS) == 50, "Tellus must clamp to capacity (50)");
        helper.assertTrue(WandVis.amountOf(wand, Primals.FORMA) == 0, "Setting Tellus must not touch Forma");
        WandVis.set(wand, Primals.TELLUS, 30);
        WandVis.add(wand, Primals.TELLUS, -40);
        helper.assertTrue(WandVis.amountOf(wand, Primals.TELLUS) == 0, "Draining below 0 must clamp to 0");

        WandVis.chargeAll(wand, 25);
        for (var primal : Primals.LIST) {
            helper.assertTrue(WandVis.amountOf(wand, primal) == 25, "chargeAll should fill every primal to 25");
        }
        helper.succeed();
    }

    //? if neoforge {
    @GameTest(template = "empty")
    //?} else {
    /*@GameTest(template = "new_age_thaum:empty")
    *///?}
    public void capDiscountLowersEachPrimalCost(GameTestHelper helper) {
        double discount = WandStats.compute(new WandComponent(
                NewAgeThaum.id("greatwood"), NewAgeThaum.id("brass"), NewAgeThaum.id("aetherium")),
                WandForm.WAND).discount();
        helper.assertTrue(Math.abs(discount - 0.15) < 1.0e-6, "Discount should be 0.15, got " + discount);
        // ceil(8 * 0.85) = 7 Tellus, ceil(6 * 0.85) = 6 Forma.
        helper.assertTrue((int) Math.ceil(8 * (1 - discount)) == 7, "8 Tellus at 15% off should be 7");
        helper.assertTrue((int) Math.ceil(6 * (1 - discount)) == 6, "6 Forma at 15% off should be 6");
        helper.succeed();
    }
}
