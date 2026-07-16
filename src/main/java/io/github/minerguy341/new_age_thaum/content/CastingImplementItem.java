package io.github.minerguy341.new_age_thaum.content;

import io.github.minerguy341.new_age_thaum.core.ModComponents;
import io.github.minerguy341.new_age_thaum.core.aspect.AspectNames;
import io.github.minerguy341.new_age_thaum.core.casting.WandComponent;
import io.github.minerguy341.new_age_thaum.core.casting.WandForm;
import io.github.minerguy341.new_age_thaum.core.casting.WandStats;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * A wand or stave. Its materials live in the {@link WandComponent} data component; the
 * tooltip surfaces the derived {@link WandStats}. Casting behaviour arrives with the
 * M3 aura/vis systems — for now this is an assembled, inspectable implement.
 */
public class CastingImplementItem extends Item {
    private final WandForm form;

    public CastingImplementItem(Properties properties, WandForm form) {
        // Do NOT reference ModComponents here: the data-component registry may not be
        // populated yet while items are being constructed (client-side registration
        // order). The WAND component is optional and set at assembly time.
        super(properties.stacksTo(1));
        this.form = form;
    }

    public WandForm form() {
        return form;
    }

    public static WandComponent componentOf(ItemStack stack) {
        return stack.get(ModComponents.WAND.get());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        WandComponent component = componentOf(stack);
        if (component == null) {
            tooltip.add(Component.translatable("tooltip.new_age_thaum.wand.unassembled").withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        WandStats stats = WandStats.compute(component, form);
        tooltip.add(Component.translatable("tooltip.new_age_thaum.wand.core",
                Component.translatable(materialKey(component.core()))).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.new_age_thaum.wand.caps",
                Component.translatable(materialKey(component.capA())),
                Component.translatable(materialKey(component.capB()))).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.new_age_thaum.wand.capacity",
                (int) Math.round(stats.capacity())).withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("tooltip.new_age_thaum.wand.discount",
                (int) Math.round(stats.discount() * 100)).withStyle(ChatFormatting.GOLD));
        stats.rechargeAffinity().ifPresent(affinity -> tooltip.add(
                Component.translatable("tooltip.new_age_thaum.wand.affinity", AspectNames.colored(affinity))
                        .withStyle(ChatFormatting.GRAY)));
    }

    private static String materialKey(net.minecraft.resources.ResourceLocation id) {
        return "wand_material." + id.getNamespace() + "." + id.getPath();
    }
}
