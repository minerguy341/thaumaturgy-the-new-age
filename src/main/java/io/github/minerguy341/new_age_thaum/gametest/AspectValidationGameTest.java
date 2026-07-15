package io.github.minerguy341.new_age_thaum.gametest;

import io.github.minerguy341.new_age_thaum.NewAgeThaum;
import io.github.minerguy341.new_age_thaum.core.aspect.Aspect;
import io.github.minerguy341.new_age_thaum.core.aspect.AspectRegistry;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Fail-soft validation of the aspect graph via the pure {@code AspectRegistry.filterValid},
 * which does not touch the live registry, so these run without disturbing the 41 loaded aspects.
 */
//? if neoforge {
@net.neoforged.neoforge.gametest.GameTestHolder(NewAgeThaum.MOD_ID)
@net.neoforged.neoforge.gametest.PrefixGameTestTemplate(false)
//?}
public class AspectValidationGameTest {

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("test", path);
    }

    private static Aspect primal(String path) {
        return new Aspect(id(path), 0xFFFFFF, List.of());
    }

    private static Aspect compound(String path, String... components) {
        return new Aspect(id(path), 0xFFFFFF, Arrays.stream(components).map(AspectValidationGameTest::id).toList());
    }

    private static Map<ResourceLocation, Aspect> mapOf(Aspect... aspects) {
        Map<ResourceLocation, Aspect> map = new HashMap<>();
        for (Aspect aspect : aspects) {
            map.put(aspect.id(), aspect);
        }
        return map;
    }

    //? if neoforge {
    @GameTest(template = "empty")
    //?} else {
    /*@GameTest(template = "new_age_thaum:empty")
    *///?}
    public void validGraphIsKept(GameTestHelper helper) {
        Map<ResourceLocation, Aspect> valid = AspectRegistry.filterValid(
                mapOf(primal("a"), primal("b"), compound("c", "a", "b")));
        helper.assertTrue(valid.size() == 3, "A valid two-primal + compound graph should survive, got " + valid.size());
        helper.succeed();
    }

    //? if neoforge {
    @GameTest(template = "empty")
    //?} else {
    /*@GameTest(template = "new_age_thaum:empty")
    *///?}
    public void wrongComponentCountIsDropped(GameTestHelper helper) {
        Map<ResourceLocation, Aspect> valid = AspectRegistry.filterValid(
                mapOf(primal("a"), primal("b"), compound("one", "a"), compound("three", "a", "b", "a")));
        helper.assertTrue(valid.containsKey(id("a")) && valid.containsKey(id("b")), "Primals should survive");
        helper.assertFalse(valid.containsKey(id("one")), "A 1-component compound must be dropped");
        helper.assertFalse(valid.containsKey(id("three")), "A 3-component compound must be dropped");
        helper.succeed();
    }

    //? if neoforge {
    @GameTest(template = "empty")
    //?} else {
    /*@GameTest(template = "new_age_thaum:empty")
    *///?}
    public void missingComponentCascades(GameTestHelper helper) {
        // 'a' is absent, so c1 (needs a) drops, which makes c2 (needs c1) drop too.
        Map<ResourceLocation, Aspect> valid = AspectRegistry.filterValid(
                mapOf(primal("b"), compound("c1", "a", "b"), compound("c2", "c1", "b")));
        helper.assertTrue(valid.size() == 1 && valid.containsKey(id("b")),
                "Only 'b' should survive the cascade, got " + valid.keySet());
        helper.succeed();
    }

    //? if neoforge {
    @GameTest(template = "empty")
    //?} else {
    /*@GameTest(template = "new_age_thaum:empty")
    *///?}
    public void cyclesAreDropped(GameTestHelper helper) {
        Map<ResourceLocation, Aspect> valid = AspectRegistry.filterValid(
                mapOf(primal("a"), compound("x", "y", "a"), compound("y", "x", "a")));
        helper.assertTrue(valid.containsKey(id("a")), "The primal is not on a cycle and should survive");
        helper.assertFalse(valid.containsKey(id("x")), "Cyclic aspect x must be dropped");
        helper.assertFalse(valid.containsKey(id("y")), "Cyclic aspect y must be dropped");
        helper.succeed();
    }
}
