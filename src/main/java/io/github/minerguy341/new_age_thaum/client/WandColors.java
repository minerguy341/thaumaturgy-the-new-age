package io.github.minerguy341.new_age_thaum.client;

import dev.architectury.registry.client.rendering.ColorHandlerRegistry;
import io.github.minerguy341.new_age_thaum.core.ModComponents;
import io.github.minerguy341.new_age_thaum.core.ModRegistries;
import io.github.minerguy341.new_age_thaum.content.WandPartItem;
import io.github.minerguy341.new_age_thaum.core.casting.WandComponent;
import io.github.minerguy341.new_age_thaum.core.casting.WandMaterial;
import io.github.minerguy341.new_age_thaum.core.casting.WandMaterialRegistry;
import net.minecraft.resources.ResourceLocation;

/**
 * Composited appearance without custom models: the wand/stave textures are grayscale
 * layers (rod, cap tip, cap base) and this tints each layer by its material's color,
 * so any core + cap combination renders distinctly. Cross-loader via Architectury's
 * ColorHandlerRegistry. Client-only; registered from {@link NewAgeThaumClient}.
 */
public final class WandColors {
    private static final int UNTINTED = 0xFFFFFF;

    private WandColors() {
    }

    public static void register() {
        ColorHandlerRegistry.registerItemColors((stack, tintIndex) -> {
            WandComponent component = stack.get(ModComponents.WAND.get());
            if (component == null) {
                return UNTINTED;
            }
            ResourceLocation materialId = switch (tintIndex) {
                case 0 -> component.core();
                case 1 -> component.capA();
                case 2 -> component.capB();
                default -> null;
            };
            return colorOf(materialId);
        }, ModRegistries.WAND.get(), ModRegistries.STAVE.get());

        ColorHandlerRegistry.registerItemColors((stack, tintIndex) -> {
            if (tintIndex != 0 || !(stack.getItem() instanceof WandPartItem part)) {
                return UNTINTED;
            }
            return colorOf(part.materialId());
        }, ModRegistries.GREATWOOD_ROD.get(), ModRegistries.SILVERWOOD_ROD.get(),
                ModRegistries.BRASS_CAP.get(), ModRegistries.AETHERIUM_CAP.get());
    }

    private static int colorOf(ResourceLocation materialId) {
        if (materialId == null) {
            return UNTINTED;
        }
        return WandMaterialRegistry.get(materialId).map(WandMaterial::color).orElse(UNTINTED);
    }
}
