package io.github.minerguy341.new_age_thaum.gametest;

import io.github.minerguy341.new_age_thaum.NewAgeThaum;
import io.github.minerguy341.new_age_thaum.core.aspect.Aspect;
import io.github.minerguy341.new_age_thaum.core.aspect.AspectRegistry;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;

/** Asserts the shipped aspect datapack loads completely with a valid compound graph. */
//? if neoforge {
@net.neoforged.neoforge.gametest.GameTestHolder(NewAgeThaum.MOD_ID)
@net.neoforged.neoforge.gametest.PrefixGameTestTemplate(false)
//?}
public class AspectGameTest {

    private static final String[] CANONICAL = {
            "ventus", "tellus", "flamma", "unda", "forma", "discordia",
            "lumen", "impetus", "inane", "procella", "glacies", "toxicum", "vita", "gemma", "vigor", "mutatio",
            "letum", "anima", "umbra", "arcanum", "aviditas", "remedium", "flora", "fera", "aes", "via", "ala",
            "mens", "acies", "aether", "macula", "silva", "caro", "larva", "persona",
            "artificium", "automata", "ensis", "praesidium", "opes", "barathrum"
    };

    //? if neoforge {
    @GameTest(template = "empty")
    //?} else {
    /*@GameTest(template = "new_age_thaum:empty")
    *///?}
    public void allCanonicalAspectsLoad(GameTestHelper helper) {
        helper.assertTrue(CANONICAL.length == 41, "Test data drifted: expected 41 canonical ids");
        for (String name : CANONICAL) {
            ResourceLocation id = NewAgeThaum.id(name);
            helper.assertTrue(AspectRegistry.exists(id), "Aspect " + id + " did not load");
        }
        int primals = 0;
        for (Aspect aspect : AspectRegistry.all()) {
            if (aspect.isPrimal()) {
                primals++;
            } else {
                helper.assertTrue(aspect.components().size() == 2,
                        "Compound " + aspect.id() + " survived validation with bad component count");
                for (ResourceLocation component : aspect.components()) {
                    helper.assertTrue(AspectRegistry.exists(component),
                            "Compound " + aspect.id() + " references missing " + component);
                }
            }
        }
        helper.assertTrue(primals == 6, "Expected exactly 6 primal aspects, found " + primals);
        helper.succeed();
    }
}
