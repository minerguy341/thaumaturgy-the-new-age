package io.github.minerguy341.new_age_thaum.client;

import dev.architectury.event.events.client.ClientTooltipEvent;
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
            for (Map.Entry<ResourceLocation, Integer> entry : bag.amounts().entrySet()) {
                lines.add(Component.literal("  ")
                        .append(AspectNames.colored(entry.getKey()))
                        .append(Component.literal(" ×" + entry.getValue()).withStyle(ChatFormatting.DARK_GRAY)));
            }
        });
    }
}
