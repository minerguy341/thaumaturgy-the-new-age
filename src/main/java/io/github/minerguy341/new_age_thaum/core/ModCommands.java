package io.github.minerguy341.new_age_thaum.core;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import dev.architectury.event.events.common.CommandRegistrationEvent;
import io.github.minerguy341.new_age_thaum.core.aspect.Aspect;
import io.github.minerguy341.new_age_thaum.core.aspect.AspectRegistry;
import io.github.minerguy341.new_age_thaum.core.player.PlayerProgress;
import io.github.minerguy341.new_age_thaum.core.player.PlayerProgressService;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Dev/testing commands, all under the one {@code /thaum} root. Server side here:
 * {@code /thaum aspects [amount]} grants the executing player {@code amount}
 * (default 100) observation points of EVERY registered aspect — compounds included,
 * which also discovers them all in the orrery list. Requires op level 2.
 * (The client-side {@code /thaum debug} lives in the client's OrreryDebugCommand.)
 */
public final class ModCommands {
    private static final int DEFAULT_AMOUNT = 100;

    private ModCommands() {
    }

    public static void init() {
        CommandRegistrationEvent.EVENT.register((dispatcher, context, selection) ->
                dispatcher.register(root("thaum")));
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> root(String literal) {
        return Commands.literal(literal)
                .requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("aspects")
                        .executes(ctx -> grantAll(ctx.getSource().getPlayerOrException(), DEFAULT_AMOUNT))
                        .then(Commands.argument("amount", IntegerArgumentType.integer(1, 100000))
                                .executes(ctx -> grantAll(ctx.getSource().getPlayerOrException(),
                                        IntegerArgumentType.getInteger(ctx, "amount")))));
    }

    /**
     * Grants {@code amount} points of every registered aspect in a single progress
     * write (one sync packet). Returns the number of aspects granted.
     */
    public static int grantAll(ServerPlayer player, int amount) {
        PlayerProgress progress = PlayerProgressService.get(player);
        int granted = 0;
        for (Aspect aspect : AspectRegistry.all()) {
            progress = progress.withGained(aspect.id(), amount);
            granted++;
        }
        PlayerProgressService.set(player, progress);
        player.sendSystemMessage(Component.translatable("command.new_age_thaum.aspects.granted", amount, granted));
        return granted;
    }
}
