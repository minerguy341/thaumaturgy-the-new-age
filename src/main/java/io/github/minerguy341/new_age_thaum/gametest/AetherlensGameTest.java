package io.github.minerguy341.new_age_thaum.gametest;

import io.github.minerguy341.new_age_thaum.NewAgeThaum;
import io.github.minerguy341.new_age_thaum.core.ModRegistries;
import io.github.minerguy341.new_age_thaum.core.player.PlayerProgress;
import io.github.minerguy341.new_age_thaum.core.player.PlayerProgressService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Drives the Aetherlens against a placed block and checks the grant-once scan loop. */
//? if neoforge {
@net.neoforged.neoforge.gametest.GameTestHolder(NewAgeThaum.MOD_ID)
@net.neoforged.neoforge.gametest.PrefixGameTestTemplate(false)
//?}
public class AetherlensGameTest {

    private static ResourceLocation aspect(String path) {
        return NewAgeThaum.id(path);
    }

    //? if neoforge {
    @GameTest(template = "empty")
    //?} else {
    /*@GameTest(template = "new_age_thaum:empty")
    *///?}
    public void scanningStoneGrantsTellusOnce(GameTestHelper helper) {
        BlockPos stonePos = new BlockPos(1, 1, 1);
        helper.setBlock(stonePos, Blocks.STONE);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        ItemStack lens = new ItemStack(ModRegistries.AETHERLENS.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, lens);

        BlockPos absolute = helper.absolutePos(stonePos);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(absolute), Direction.UP, absolute, false);
        UseOnContext context = new UseOnContext(player, InteractionHand.MAIN_HAND, hit);

        String key = "block/" + BuiltInRegistries.BLOCK.getKey(Blocks.STONE);
        lens.getItem().useOn(context);

        PlayerProgress afterFirst = PlayerProgressService.get(player);
        helper.assertTrue(afterFirst.hasScanned(key), "Stone should be marked scanned");
        helper.assertTrue(afterFirst.points(aspect("tellus")) == 2,
                "First scan should grant Tellus 2, got " + afterFirst.points(aspect("tellus")));

        lens.getItem().useOn(context);
        PlayerProgress afterSecond = PlayerProgressService.get(player);
        helper.assertTrue(afterSecond.points(aspect("tellus")) == 2,
                "Re-scanning stone must not grant again, got " + afterSecond.points(aspect("tellus")));
        helper.succeed();
    }
}
