package io.github.minerguy341.new_age_thaum.core.aspect;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * One aspect. Primal aspects have no components; every compound has exactly two
 * (primal or compound), forming the relatedness graph the research systems walk.
 * Identity (id) comes from the datapack file name; {@link Data} is the file body.
 */
public record Aspect(ResourceLocation id, int color, List<ResourceLocation> components) {

    public static final Codec<Integer> HEX_COLOR = Codec.STRING.comapFlatMap(s -> {
        String hex = s.startsWith("#") ? s.substring(1) : s;
        try {
            return DataResult.success(Integer.parseInt(hex, 16));
        } catch (NumberFormatException e) {
            return DataResult.error(() -> "Invalid hex color '" + s + "'");
        }
    }, c -> String.format("#%06X", c));

    /** The file body: {@code {"color": "#RRGGBB", "components": ["ns:a", "ns:b"]}}. */
    public record Data(int color, List<ResourceLocation> components) {
        public static final Codec<Data> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                HEX_COLOR.fieldOf("color").forGetter(Data::color),
                ResourceLocation.CODEC.listOf()
                        .optionalFieldOf("components", List.of())
                        .forGetter(Data::components)
        ).apply(instance, Data::new));
    }

    public boolean isPrimal() {
        return components.isEmpty();
    }
}
