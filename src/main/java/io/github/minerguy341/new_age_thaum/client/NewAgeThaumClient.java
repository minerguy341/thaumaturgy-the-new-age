package io.github.minerguy341.new_age_thaum.client;

import dev.architectury.event.events.client.ClientTooltipEvent;
import dev.architectury.registry.menu.MenuRegistry;
import io.github.minerguy341.new_age_thaum.core.ModMenus;
import io.github.minerguy341.new_age_thaum.core.aspect.AspectBag;
import io.github.minerguy341.new_age_thaum.core.aspect.AspectNames;
import io.github.minerguy341.new_age_thaum.core.aspect.AspectResolver;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;

/**
 * Client-only setup. Loaded exclusively through {@code EnvExecutor.runInEnv(Env.CLIENT, ...)}
 * so its client-class references never resolve on a dedicated server.
 */
public final class NewAgeThaumClient {
    private NewAgeThaumClient() {
    }

    public static void init() {
        WandColors.register();
        // listen() fires the moment the menu type is actually registered: late enough to
        // avoid the NeoForge early-.get() crash, early enough for RegisterMenuScreensEvent
        // (CLIENT_SETUP would be too late — the screen factory would never register).
        ModMenus.ARCANE_ORRERY.listen(type ->
                MenuRegistry.registerScreenFactory(type, ResearchSphereScreen::new));
        ClientTooltipEvent.ITEM.register((stack, lines, context, flag) -> {
            var level = Minecraft.getInstance().level;
            if (level == null) {
                return;
            }
            AspectBag bag = AspectResolver.resolve(stack.getItem(), level);
            if (bag.isEmpty()) {
                return;
            }
            lines.add(Component.translatable("tooltip.new_age_thaum.aspects").withStyle(ChatFormatting.GRAY));
            for (Map.Entry<ResourceLocation, Integer> entry : bag.ordered()) {
                lines.add(Component.literal("  ")
                        .append(AspectNames.colored(entry.getKey()))
                        .append(Component.literal(" ×" + entry.getValue()).withStyle(ChatFormatting.DARK_GRAY)));
            }
        });
    }
}
