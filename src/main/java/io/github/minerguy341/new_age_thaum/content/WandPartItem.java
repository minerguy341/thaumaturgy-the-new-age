package io.github.minerguy341.new_age_thaum.content;

import io.github.minerguy341.new_age_thaum.core.casting.WandMaterial;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

/**
 * A physical wand part — a rod (core) or an end cap — bound to a wand-material id. The
 * assembly recipe reads {@link #materialId()} to stamp the finished wand's component.
 * The {@code kind} lets the recipe tell rods from caps without a registry lookup.
 */
public class WandPartItem extends Item {
    private final ResourceLocation materialId;
    private final WandMaterial.Kind kind;

    public WandPartItem(Properties properties, ResourceLocation materialId, WandMaterial.Kind kind) {
        super(properties);
        this.materialId = materialId;
        this.kind = kind;
    }

    public ResourceLocation materialId() {
        return materialId;
    }

    public WandMaterial.Kind kind() {
        return kind;
    }

    public boolean isRod() {
        return kind == WandMaterial.Kind.CORE;
    }

    public boolean isCap() {
        return kind == WandMaterial.Kind.CAP;
    }
}
