package io.github.minerguy341.new_age_thaum.core.aspect;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;

/** Shared display helpers so tooltips, the Codex, and the scan HUD read aspects the same way. */
public final class AspectNames {
    private AspectNames() {
    }

    public static String translationKey(ResourceLocation aspectId) {
        return "aspect." + aspectId.getNamespace() + "." + aspectId.getPath();
    }

    public static Component displayName(ResourceLocation aspectId) {
        return Component.translatable(translationKey(aspectId));
    }

    /** Aspect name tinted with its own color (falls back to white if the aspect is unknown). */
    public static MutableComponent colored(ResourceLocation aspectId) {
        int color = AspectRegistry.get(aspectId).map(Aspect::color).orElse(0xFFFFFF);
        return Component.translatable(translationKey(aspectId))
                .withStyle(style -> style.withColor(color));
    }
}
