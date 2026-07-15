package io.github.minerguy341.new_age_thaum.gametest;

import io.github.minerguy341.new_age_thaum.NewAgeThaum;
import io.github.minerguy341.new_age_thaum.core.aspect.AspectBag;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;

/** Arithmetic on aspect bags: merge (add), tag reconciliation (max), and inference dampening. */
//? if neoforge {
@net.neoforged.neoforge.gametest.GameTestHolder(NewAgeThaum.MOD_ID)
@net.neoforged.neoforge.gametest.PrefixGameTestTemplate(false)
//?}
public class AspectBagGameTest {

    private static final ResourceLocation A = ResourceLocation.fromNamespaceAndPath("test", "a");
    private static final ResourceLocation B = ResourceLocation.fromNamespaceAndPath("test", "b");

    //? if neoforge {
    @GameTest(template = "empty")
    //?} else {
    /*@GameTest(template = "new_age_thaum:empty")
    *///?}
    public void addMergesAndSumsAmounts(GameTestHelper helper) {
        AspectBag sum = new AspectBag(Map.of(A, 2)).add(new AspectBag(Map.of(A, 3, B, 1)));
        helper.assertTrue(sum.amounts().get(A) == 5, "A should sum to 5, got " + sum.amounts().get(A));
        helper.assertTrue(sum.amounts().get(B) == 1, "B should be 1, got " + sum.amounts().get(B));
        helper.assertTrue(AspectBag.EMPTY.add(sum) == sum, "EMPTY.add(x) returns x unchanged");
        helper.succeed();
    }

    //? if neoforge {
    @GameTest(template = "empty")
    //?} else {
    /*@GameTest(template = "new_age_thaum:empty")
    *///?}
    public void maxTakesPerAspectMaximum(GameTestHelper helper) {
        AspectBag merged = new AspectBag(Map.of(A, 2, B, 5)).max(new AspectBag(Map.of(A, 4, B, 1)));
        helper.assertTrue(merged.amounts().get(A) == 4, "max A should be 4, got " + merged.amounts().get(A));
        helper.assertTrue(merged.amounts().get(B) == 5, "max B should be 5, got " + merged.amounts().get(B));
        helper.succeed();
    }

    //? if neoforge {
    @GameTest(template = "empty")
    //?} else {
    /*@GameTest(template = "new_age_thaum:empty")
    *///?}
    public void dampenClampsToOneAndCaps(GameTestHelper helper) {
        // floor(4 * 0.75 / 4) = 0, clamped up to 1 while the source aspect survives.
        AspectBag clamped = new AspectBag(Map.of(A, 4)).dampen(0.75, 4, 512);
        helper.assertTrue(clamped.amounts().get(A) == 1, "Dampen should clamp to 1, got " + clamped.amounts().get(A));

        AspectBag capped = new AspectBag(Map.of(A, 1000)).dampen(1.0, 1, 512);
        helper.assertTrue(capped.amounts().get(A) == 512, "Dampen should cap at 512, got " + capped.amounts().get(A));

        AspectBag plain = new AspectBag(Map.of(A, 8)).dampen(0.75, 1, 512);
        helper.assertTrue(plain.amounts().get(A) == 6, "floor(8*0.75)=6, got " + plain.amounts().get(A));
        helper.succeed();
    }
}
