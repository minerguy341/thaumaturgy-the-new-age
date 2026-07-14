package io.github.minerguy341.new_age_thaum.gametest;

import io.github.minerguy341.new_age_thaum.NewAgeThaum;
import io.github.minerguy341.new_age_thaum.core.ModRegistries;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
//? if fabric {
/*import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;*/
//?}

/**
 * Walking-skeleton test harness: asserts the proof item actually reached the item registry.
 * Fabric discovers this class via the {@code fabric-gametest} entrypoint in fabric.mod.json;
 * NeoForge discovers it via the {@code @GameTestHolder} annotation scan.
 */
//? if neoforge {
@net.neoforged.neoforge.gametest.GameTestHolder(NewAgeThaum.MOD_ID)
//?}
public class ProofOfForgeGameTest /*? if fabric {*/ /*implements FabricGameTest*/ /*?}*/ {

    //? if neoforge {
    @GameTest
    @net.neoforged.neoforge.gametest.EmptyTemplate
    //?} else {
    /*@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)*/
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
