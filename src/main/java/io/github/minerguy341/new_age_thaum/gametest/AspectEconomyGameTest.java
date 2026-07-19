package io.github.minerguy341.new_age_thaum.gametest;

import io.github.minerguy341.new_age_thaum.NewAgeThaum;
import io.github.minerguy341.new_age_thaum.content.ArcaneOrreryBlockEntity;
import io.github.minerguy341.new_age_thaum.core.ModRegistries;
import io.github.minerguy341.new_age_thaum.core.aspect.AspectBag;
import io.github.minerguy341.new_age_thaum.core.player.PlayerProgress;
import io.github.minerguy341.new_age_thaum.core.player.PlayerProgressService;
import io.github.minerguy341.new_age_thaum.network.NewAgeThaumNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.Optional;

/** The placement economy, driven through the exact server edit path. */
//? if neoforge {
@net.neoforged.neoforge.gametest.GameTestHolder(NewAgeThaum.MOD_ID)
@net.neoforged.neoforge.gametest.PrefixGameTestTemplate(false)
//?}
public class AspectEconomyGameTest {

    private static ResourceLocation aspect(String path) {
        return NewAgeThaum.id(path);
    }

    //? if neoforge {
    @GameTest(template = "empty")
    //?} else {
    /*@GameTest(template = "new_age_thaum:empty")
    *///?}
    public void spendSemantics(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        ResourceLocation tellus = aspect("tellus");

        PlayerProgressService.scan(player, "test/economy", new AspectBag(Map.of(tellus, 2)));
        helper.assertTrue(PlayerProgressService.trySpend(player, tellus, 1), "First spend should succeed");
        helper.assertTrue(PlayerProgressService.trySpend(player, tellus, 1), "Second spend should succeed");
        helper.assertFalse(PlayerProgressService.trySpend(player, tellus, 1), "Third spend must fail at 0");

        PlayerProgress after = PlayerProgressService.get(player);
        helper.assertTrue(after.points(tellus) == 0, "Balance should rest at 0, got " + after.points(tellus));
        helper.assertTrue(after.hasDiscovered(tellus), "Spending to 0 must not un-discover the aspect");
        helper.succeed();
    }

    //? if neoforge {
    @GameTest(template = "empty")
    //?} else {
    /*@GameTest(template = "new_age_thaum:empty")
    *///?}
    public void serverEditPathChargesAndRefuses(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        ResourceLocation flamma = aspect("flamma");
        ArcaneOrreryBlockEntity orrery = new ArcaneOrreryBlockEntity(BlockPos.ZERO,
                ModRegistries.ARCANE_ORRERY.get().defaultBlockState());
        // Hand-stamped puzzle (unstamped papers reject all edits): endpoints far from
        // the cells under test, unconnectable, so no edit can accidentally solve it.
        ItemStack paper = new ItemStack(ModRegistries.PAPER_FLEDGLING.get());
        paper.set(io.github.minerguy341.new_age_thaum.core.ModComponents.RESEARCH_PUZZLE.get(),
                new io.github.minerguy341.new_age_thaum.core.research.ResearchPuzzle(
                        3, Map.of(0, flamma, 30, flamma), java.util.Set.of()));
        orrery.setPaper(paper);

        PlayerProgressService.scan(player, "test/economy2", new AspectBag(Map.of(flamma, 1)));

        helper.assertTrue(NewAgeThaumNetwork.applyOrreryEdit(player, orrery, 4, Optional.of(flamma)),
                "Placement with 1 point should be accepted");
        helper.assertTrue(flamma.equals(orrery.aspectAt(4)), "Cell 4 should hold Flamma");
        helper.assertTrue(PlayerProgressService.get(player).points(flamma) == 0, "The point was spent");

        helper.assertFalse(NewAgeThaumNetwork.applyOrreryEdit(player, orrery, 5, Optional.of(flamma)),
                "Placement with 0 points must be rejected");
        helper.assertTrue(orrery.aspectAt(5) == null, "Rejected placement must not paint");

        helper.assertTrue(NewAgeThaumNetwork.applyOrreryEdit(player, orrery, 4, Optional.empty()),
                "Clearing is always allowed");
        helper.assertTrue(orrery.aspectAt(4) == null, "Cell 4 should be cleared");
        helper.assertTrue(PlayerProgressService.get(player).points(flamma) == 0,
                "No refund on clear (refundChance is 0 until the research unlock exists)");

        orrery.setPaper(ItemStack.EMPTY);
        helper.assertFalse(NewAgeThaumNetwork.applyOrreryEdit(player, orrery, 6, Optional.of(flamma)),
                "Edits without a paper must be rejected");
        helper.succeed();
    }
}
