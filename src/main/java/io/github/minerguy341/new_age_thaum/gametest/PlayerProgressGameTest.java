package io.github.minerguy341.new_age_thaum.gametest;

import io.github.minerguy341.new_age_thaum.NewAgeThaum;
import io.github.minerguy341.new_age_thaum.core.aspect.AspectBag;
import io.github.minerguy341.new_age_thaum.core.player.PlayerProgress;
import io.github.minerguy341.new_age_thaum.core.player.PlayerProgressService;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;

/**
 * Exercises the platform data-attachment bridge end to end on whichever loader is
 * running: a scan grants points once, a repeat scan is a no-op, and the attachment
 * read-back returns what was written (proving the loader impl is wired correctly).
 */
//? if neoforge {
@net.neoforged.neoforge.gametest.GameTestHolder(NewAgeThaum.MOD_ID)
@net.neoforged.neoforge.gametest.PrefixGameTestTemplate(false)
//?}
public class PlayerProgressGameTest {

    private static ResourceLocation aspect(String path) {
        return NewAgeThaum.id(path);
    }

    //? if neoforge {
    @GameTest(template = "empty")
    //?} else {
    /*@GameTest(template = "new_age_thaum:empty")
    *///?}
    public void scanGrantsPointsOnceAndPersistsThroughAttachment(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        AspectBag reward = new AspectBag(Map.of(aspect("tellus"), 2));

        boolean first = PlayerProgressService.scan(player, "block/minecraft:stone", reward);
        helper.assertTrue(first, "First scan should grant points");

        PlayerProgress afterFirst = PlayerProgressService.get(player);
        helper.assertTrue(afterFirst.points(aspect("tellus")) == 2,
                "Attachment read-back should show Tellus 2, got " + afterFirst.points(aspect("tellus")));

        boolean second = PlayerProgressService.scan(player, "block/minecraft:stone", reward);
        helper.assertFalse(second, "Repeat scan of the same key should not grant again");

        PlayerProgress afterSecond = PlayerProgressService.get(player);
        helper.assertTrue(afterSecond.points(aspect("tellus")) == 2,
                "Points must not double on a repeat scan, got " + afterSecond.points(aspect("tellus")));
        helper.succeed();
    }

    //? if neoforge {
    @GameTest(template = "empty")
    //?} else {
    /*@GameTest(template = "new_age_thaum:empty")
    *///?}
    public void grantAllCommandGivesEveryAspect(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        int granted = io.github.minerguy341.new_age_thaum.core.ModCommands.grantAll(player, 100);
        helper.assertTrue(granted > 0, "The aspect registry should not be empty");

        PlayerProgress after = PlayerProgressService.get(player);
        for (io.github.minerguy341.new_age_thaum.core.aspect.Aspect aspect
                : io.github.minerguy341.new_age_thaum.core.aspect.AspectRegistry.all()) {
            helper.assertTrue(after.points(aspect.id()) >= 100,
                    "Every aspect should hold at least 100 points, " + aspect.id()
                            + " has " + after.points(aspect.id()));
        }
        helper.succeed();
    }
}
