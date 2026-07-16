package io.github.minerguy341.new_age_thaum.core.casting;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.minerguy341.new_age_thaum.core.aspect.Aspect;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;

/**
 * A wand-crafting material: either a rod {@code CORE} or an end {@code CAP} (PLAN §4.4,
 * the cap + core matrix). Cores set vis capacity and a recharge-affinity aspect; caps set
 * a cost discount and potency. Identity is the datapack file name; {@link Data} is the body.
 * Stats are inert data until the M3 casting/aura systems consume them.
 */
public record WandMaterial(ResourceLocation id, Kind kind, int color,
                           double capacity, double discount, double potency,
                           Optional<ResourceLocation> rechargeAffinity) {

    public enum Kind {
        CORE, CAP;

        public static final Codec<Kind> CODEC = Codec.STRING.xmap(
                s -> Kind.valueOf(s.toUpperCase(java.util.Locale.ROOT)),
                k -> k.name().toLowerCase(java.util.Locale.ROOT));
    }

    /** File body: {@code {"kind":"core","color":"#7A5B3C","capacity":50,"recharge_affinity":"new_age_thaum:silva"}}. */
    public record Data(Kind kind, int color, double capacity, double discount, double potency,
                       Optional<ResourceLocation> rechargeAffinity) {
        public static final Codec<Data> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Kind.CODEC.fieldOf("kind").forGetter(Data::kind),
                Aspect.HEX_COLOR.fieldOf("color").forGetter(Data::color),
                Codec.DOUBLE.optionalFieldOf("capacity", 0.0).forGetter(Data::capacity),
                Codec.DOUBLE.optionalFieldOf("discount", 0.0).forGetter(Data::discount),
                Codec.DOUBLE.optionalFieldOf("potency", 0.0).forGetter(Data::potency),
                ResourceLocation.CODEC.optionalFieldOf("recharge_affinity").forGetter(Data::rechargeAffinity)
        ).apply(instance, Data::new));
    }

    public boolean isCore() {
        return kind == Kind.CORE;
    }

    public boolean isCap() {
        return kind == Kind.CAP;
    }
}
