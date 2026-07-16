package io.github.minerguy341.new_age_thaum.gametest;

import io.github.minerguy341.new_age_thaum.NewAgeThaum;
import io.github.minerguy341.new_age_thaum.content.CastingImplementItem;
import io.github.minerguy341.new_age_thaum.content.WandAssemblyRecipe;
import io.github.minerguy341.new_age_thaum.core.ModRegistries;
import io.github.minerguy341.new_age_thaum.core.casting.WandComponent;
import io.github.minerguy341.new_age_thaum.core.casting.WandForm;
import io.github.minerguy341.new_age_thaum.core.casting.WandStats;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;

import java.util.List;

/** Drives the wand-assembly recipe: mix-and-match rods/caps produce the right item and stats. */
//? if neoforge {
@net.neoforged.neoforge.gametest.GameTestHolder(NewAgeThaum.MOD_ID)
@net.neoforged.neoforge.gametest.PrefixGameTestTemplate(false)
//?}
public class WandAssemblyGameTest {

    private static ResourceLocation material(String path) {
        return ResourceLocation.fromNamespaceAndPath(NewAgeThaum.MOD_ID, path);
    }

    private final WandAssemblyRecipe recipe = new WandAssemblyRecipe(CraftingBookCategory.EQUIPMENT);

    //? if neoforge {
    @GameTest(template = "empty")
    //?} else {
    /*@GameTest(template = "new_age_thaum:empty")
    *///?}
    public void oneRodTwoCapsMakesAWand(GameTestHelper helper) {
        CraftingInput input = CraftingInput.of(3, 1, List.of(
                new ItemStack(ModRegistries.BRASS_CAP.get()),
                new ItemStack(ModRegistries.GREATWOOD_ROD.get()),
                new ItemStack(ModRegistries.AETHERIUM_CAP.get())));

        helper.assertTrue(recipe.matches(input, helper.getLevel()), "Rod + two caps should match");
        ItemStack result = recipe.assemble(input, helper.getLevel().registryAccess());
        helper.assertTrue(result.is(ModRegistries.WAND.get()), "Result should be a wand");

        WandComponent component = CastingImplementItem.componentOf(result);
        helper.assertTrue(component != null && component.core().equals(material("greatwood")),
                "Wand core should be greatwood");
        WandStats stats = WandStats.compute(component, WandForm.WAND);
        helper.assertTrue(Math.round(stats.capacity()) == 50, "Greatwood wand capacity should be 50, got " + stats.capacity());
        helper.assertTrue(Math.abs(stats.discount() - 0.15) < 1.0e-6,
                "Brass(0.10) + Aetherium(0.05) discount should be 0.15, got " + stats.discount());
        helper.succeed();
    }

    //? if neoforge {
    @GameTest(template = "empty")
    //?} else {
    /*@GameTest(template = "new_age_thaum:empty")
    *///?}
    public void twoMatchingRodsMakeAStave(GameTestHelper helper) {
        CraftingInput input = CraftingInput.of(2, 2, List.of(
                new ItemStack(ModRegistries.SILVERWOOD_ROD.get()), new ItemStack(ModRegistries.SILVERWOOD_ROD.get()),
                new ItemStack(ModRegistries.BRASS_CAP.get()), new ItemStack(ModRegistries.AETHERIUM_CAP.get())));

        helper.assertTrue(recipe.matches(input, helper.getLevel()), "Two matching rods + two caps should match");
        ItemStack result = recipe.assemble(input, helper.getLevel().registryAccess());
        helper.assertTrue(result.is(ModRegistries.STAVE.get()), "Result should be a stave");

        WandStats stats = WandStats.compute(CastingImplementItem.componentOf(result), WandForm.STAVE);
        // Silverwood 75 * stave 1.6 = 120.
        helper.assertTrue(Math.round(stats.capacity()) == 120, "Silverwood stave capacity should be 120, got " + stats.capacity());
        helper.succeed();
    }

    //? if neoforge {
    @GameTest(template = "empty")
    //?} else {
    /*@GameTest(template = "new_age_thaum:empty")
    *///?}
    public void mismatchedRodsAndBadCountsRejected(GameTestHelper helper) {
        CraftingInput oneCap = CraftingInput.of(2, 1, List.of(
                new ItemStack(ModRegistries.GREATWOOD_ROD.get()), new ItemStack(ModRegistries.BRASS_CAP.get())));
        helper.assertFalse(recipe.matches(oneCap, helper.getLevel()), "One cap is not enough");

        CraftingInput mixedRods = CraftingInput.of(2, 2, List.of(
                new ItemStack(ModRegistries.GREATWOOD_ROD.get()), new ItemStack(ModRegistries.SILVERWOOD_ROD.get()),
                new ItemStack(ModRegistries.BRASS_CAP.get()), new ItemStack(ModRegistries.AETHERIUM_CAP.get())));
        helper.assertFalse(recipe.matches(mixedRods, helper.getLevel()), "A stave needs two rods of the same core");
        helper.succeed();
    }
}
