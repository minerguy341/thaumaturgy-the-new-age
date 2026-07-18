package io.github.minerguy341.new_age_thaum.gametest;

import io.github.minerguy341.new_age_thaum.NewAgeThaum;
import io.github.minerguy341.new_age_thaum.content.ArcaneOrreryBlockEntity;
import io.github.minerguy341.new_age_thaum.core.ModComponents;
import io.github.minerguy341.new_age_thaum.core.ModRegistries;
import io.github.minerguy341.new_age_thaum.core.research.ResearchSphereData;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Optional;

/**
 * The research must travel with the paper: sphere edits made at the orrery live in the
 * paper's data component, survive the block's NBT round trip, and leave with the paper
 * when it is removed. A fresh paper starts blank.
 */
//? if neoforge {
@net.neoforged.neoforge.gametest.GameTestHolder(NewAgeThaum.MOD_ID)
@net.neoforged.neoforge.gametest.PrefixGameTestTemplate(false)
//?}
public class ArcaneOrreryPersistenceGameTest {

    //? if neoforge {
    @GameTest(template = "empty")
    //?} else {
    /*@GameTest(template = "new_age_thaum:empty")
    *///?}
    public void researchTravelsWithThePaper(GameTestHelper helper) {
        var registries = helper.getLevel().registryAccess();
        BlockState state = ModRegistries.ARCANE_ORRERY.get().defaultBlockState();
        ResourceLocation flamma = ResourceLocation.fromNamespaceAndPath(NewAgeThaum.MOD_ID, "flamma");

        ArcaneOrreryBlockEntity orrery = new ArcaneOrreryBlockEntity(BlockPos.ZERO, state);
        orrery.setPaper(new ItemStack(ModRegistries.PAPER_FLEDGLING.get()));
        orrery.editSphere(7, Optional.of(flamma));

        // Survives the block entity's NBT round trip (world save/load) …
        CompoundTag tag = orrery.saveWithFullMetadata(registries);
        BlockEntity loaded = BlockEntity.loadStatic(BlockPos.ZERO, state, tag, registries);
        helper.assertTrue(loaded instanceof ArcaneOrreryBlockEntity, "Loaded BE should be an orrery");
        ArcaneOrreryBlockEntity copy = (ArcaneOrreryBlockEntity) loaded;
        ResearchSphereData onPaper = copy.paper().getOrDefault(ModComponents.RESEARCH_SPHERE.get(), ResearchSphereData.EMPTY);
        helper.assertTrue(flamma.equals(onPaper.cells().get(7)),
                "Painted cell 7 should reload as Flamma from the paper component, got " + onPaper.cells());

        // … and leaves with the paper when it is taken out.
        ItemStack taken = copy.removeItemNoUpdate(0);
        ResearchSphereData carried = taken.getOrDefault(ModComponents.RESEARCH_SPHERE.get(), ResearchSphereData.EMPTY);
        helper.assertTrue(flamma.equals(carried.cells().get(7)), "The removed paper should carry the research");

        // A fresh paper starts blank, and edits without a paper are rejected.
        copy.setPaper(new ItemStack(ModRegistries.PAPER_FLEDGLING.get()));
        helper.assertTrue(copy.paper().getOrDefault(ModComponents.RESEARCH_SPHERE.get(), ResearchSphereData.EMPTY)
                .cells().isEmpty(), "A fresh paper must start blank");
        copy.setPaper(ItemStack.EMPTY);
        copy.editSphere(3, Optional.of(flamma));
        helper.assertTrue(copy.paper().isEmpty(), "Editing with no paper must be a no-op");
        helper.succeed();
    }
}
