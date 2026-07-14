package io.github.minerguy341.new_age_thaum.gametest;

import io.github.minerguy341.new_age_thaum.NewAgeThaum;
import io.github.minerguy341.new_age_thaum.core.ModRegistries;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;

/**
 * Walking-skeleton test harness: asserts the proof item actually reached the item registry.
 * Fabric discovers this class via the {@code fabric-gametest} entrypoint in fabric.mod.json;
 * NeoForge discovers it via the {@code @GameTestHolder} annotation scan. Both loaders load
 * the shared empty template from {@code data/new_age_thaum/structure/empty.nbt}.
 */
//? if neoforge {
@net.neoforged.neoforge.gametest.GameTestHolder(NewAgeThaum.MOD_ID)
@net.neoforged.neoforge.gametest.PrefixGameTestTemplate(false)
//?}
public class ProofOfForgeGameTest {

    // NeoForge always prepends the GameTestHolder namespace to the template; with
    // PrefixGameTestTemplate(false) suppressing the classname segment, "empty"
    // resolves to new_age_thaum:empty. Fabric needs the fully qualified id.
    //? if neoforge {
    @GameTest(template = "empty")
    //?} else {
    /*@GameTest(template = "new_age_thaum:empty")*/
    //?}
    public void proofItemIsRegistered(GameTestHelper helper) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(NewAgeThaum.MOD_ID, "proof_of_forge");
        helper.assertTrue(BuiltInRegistries.ITEM.containsKey(id),
                "Proof item " + id + " is missing from the item registry");
        helper.assertTrue(ModRegistries.PROOF_OF_FORGE.isPresent(),
                "Architectury RegistrySupplier for proof_of_forge did not resolve");
        helper.succeed();
    }
}
