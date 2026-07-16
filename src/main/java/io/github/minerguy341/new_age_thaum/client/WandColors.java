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
    // Full-alpha white. Tint colors MUST carry alpha 0xFF: the 1.21 item renderer reads
    // the tint as ARGB, so a color like 0x007A5B3C (alpha 0) renders the face fully
    // transparent — the whole tinted model turns invisible with no error logged.
    private static final int UNTINTED = 0xFFFFFFFF;
    private static final int OPAQUE = 0xFF000000;

    private WandColors() {
    }

    public static void register() {
        // Pass the RegistrySuppliers, not .get(): the Supplier overload resolves the
        // items lazily at the color-registration event, which fires AFTER item
        // registration. Resolving eagerly here crashes on NeoForge (items are still
        // being registered while this runs during client setup).
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
        }, ModRegistries.WAND, ModRegistries.STAVE);

        ColorHandlerRegistry.registerItemColors((stack, tintIndex) -> {
            if (tintIndex != 0 || !(stack.getItem() instanceof WandPartItem part)) {
                return UNTINTED;
            }
            return colorOf(part.materialId());
        }, ModRegistries.GREATWOOD_ROD, ModRegistries.SILVERWOOD_ROD,
                ModRegistries.BRASS_CAP, ModRegistries.AETHERIUM_CAP);
    }

    private static int colorOf(ResourceLocation materialId) {
        if (materialId == null) {
            return UNTINTED;
        }
        return WandMaterialRegistry.get(materialId).map(m -> OPAQUE | m.color()).orElse(UNTINTED);
    }
}
