package io.github.minerguy341.new_age_thaum.gametest;

import io.github.minerguy341.new_age_thaum.NewAgeThaum;
import io.github.minerguy341.new_age_thaum.core.aspect.AspectBag;
import io.github.minerguy341.new_age_thaum.core.aspect.AspectResolver;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/** Covers all three resolution paths: direct item, tag, and recipe inference. */
//? if neoforge {
@net.neoforged.neoforge.gametest.GameTestHolder(NewAgeThaum.MOD_ID)
@net.neoforged.neoforge.gametest.PrefixGameTestTemplate(false)
//?}
public class AspectResolutionGameTest {

    private static ResourceLocation aspect(String path) {
        return NewAgeThaum.id(path);
    }

    private AspectBag resolve(GameTestHelper helper, Item item) {
        return AspectResolver.resolve(item, helper.getLevel());
    }

    //? if neoforge {
    @GameTest(template = "empty")
    //?} else {
    /*@GameTest(template = "new_age_thaum:empty")
    *///?}
    public void directItemAssignmentResolves(GameTestHelper helper) {
        AspectBag bag = resolve(helper, Items.STONE);
        helper.assertTrue(bag.amounts().getOrDefault(aspect("tellus"), 0) == 2,
                "Stone should carry Tellus x2 from its direct assignment, got " + bag.amounts());
        helper.succeed();
    }

    //? if neoforge {
    @GameTest(template = "empty")
    //?} else {
    /*@GameTest(template = "new_age_thaum:empty")
    *///?}
    public void tagAssignmentResolves(GameTestHelper helper) {
        AspectBag bag = resolve(helper, Items.OAK_LOG);
        helper.assertTrue(bag.amounts().getOrDefault(aspect("silva"), 0) == 3,
                "Oak log should carry Silva x3 via #minecraft:logs, got " + bag.amounts());
        helper.succeed();
    }

    //? if neoforge {
    @GameTest(template = "empty")
    //?} else {
    /*@GameTest(template = "new_age_thaum:empty")
    *///?}
    public void recipeInferenceResolves(GameTestHelper helper) {
        // Oak planks have no explicit assignment; aspects come from the log recipe, dampened.
        AspectBag planks = resolve(helper, Items.OAK_PLANKS);
        helper.assertFalse(planks.isEmpty(), "Oak planks should infer aspects from their recipe");
        helper.assertTrue(planks.amounts().containsKey(aspect("silva")),
                "Inferred oak planks should inherit Silva from oak logs, got " + planks.amounts());
        AspectBag log = resolve(helper, Items.OAK_LOG);
        helper.assertTrue(planks.total() < log.total(),
                "Dampening should make planks weaker than the log they come from");
        helper.succeed();
    }
}
