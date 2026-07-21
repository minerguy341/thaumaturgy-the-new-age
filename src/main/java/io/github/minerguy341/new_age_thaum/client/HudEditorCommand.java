package io.github.minerguy341.new_age_thaum.client;

import dev.architectury.event.events.client.ClientCommandRegistrationEvent;
import net.minecraft.client.Minecraft;

/**
 * Registers {@code /thaum hud}, which opens the {@link HudTransformScreen} HUD-layout editor.
 * Merges into the same {@code /thaum} tree as {@code /thaum debug} (Brigadier merges same-named
 * root literals). Opening the screen is deferred to the client event loop so the chat screen
 * closes first.
 */
public final class HudEditorCommand {
    private HudEditorCommand() {
    }

    public static void register() {
        ClientCommandRegistrationEvent.EVENT.register((dispatcher, context) ->
                dispatcher.register(ClientCommandRegistrationEvent.literal("thaum")
                        .then(ClientCommandRegistrationEvent.literal("hud")
                                .executes(ctx -> {
                                    Minecraft mc = Minecraft.getInstance();
                                    mc.execute(() -> mc.setScreen(new HudTransformScreen()));
                                    return 1;
                                }))));
    }
}
