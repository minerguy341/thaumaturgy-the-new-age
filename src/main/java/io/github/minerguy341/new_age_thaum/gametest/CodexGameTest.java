package io.github.minerguy341.new_age_thaum.gametest;

import io.github.minerguy341.new_age_thaum.NewAgeThaum;
import io.github.minerguy341.new_age_thaum.core.codex.CodexRegistry;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/** Asserts the datapack codex entries load into their category. */
//? if neoforge {
@net.neoforged.neoforge.gametest.GameTestHolder(NewAgeThaum.MOD_ID)
@net.neoforged.neoforge.gametest.PrefixGameTestTemplate(false)
//?}
public class CodexGameTest {

    //? if neoforge {
    @GameTest(template = "empty")
    //?} else {
    /*@GameTest(template = "new_age_thaum:empty")
    *///?}
    public void codexEntriesLoadIntoCategory(GameTestHelper helper) {
        helper.assertTrue(CodexRegistry.categories().contains("fundamenta"),
                "Expected a 'fundamenta' category, got " + CodexRegistry.categories());
        helper.assertTrue(CodexRegistry.byCategory("fundamenta").size() == 3,
                "Expected 3 fundamenta entries, got " + CodexRegistry.byCategory("fundamenta").size());
        helper.succeed();
    }
}
