package io.github.minerguy341.new_age_thaum.core.codex;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * One node in the Codex. Identity is the file name; {@link Data} is the file body.
 * M1 has no gating or entry pages yet (M2 scope) — an entry is just a positioned,
 * titled, icon-bearing node in a category.
 */
public record CodexEntry(ResourceLocation id, String category, String titleKey, Item icon, int x, int y) {

    public record Data(String category, String titleKey, Item icon, int x, int y) {
        public static final Codec<Data> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("category").forGetter(Data::category),
                Codec.STRING.fieldOf("title").forGetter(Data::titleKey),
                BuiltInRegistries.ITEM.byNameCodec().optionalFieldOf("icon", Items.BOOK).forGetter(Data::icon),
                Codec.INT.fieldOf("x").forGetter(Data::x),
                Codec.INT.fieldOf("y").forGetter(Data::y)
        ).apply(instance, Data::new));
    }
}
