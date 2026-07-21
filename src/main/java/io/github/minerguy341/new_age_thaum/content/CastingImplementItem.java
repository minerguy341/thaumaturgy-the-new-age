package io.github.minerguy341.new_age_thaum.content;

import io.github.minerguy341.new_age_thaum.core.ModComponents;
import io.github.minerguy341.new_age_thaum.core.aspect.AspectNames;
import io.github.minerguy341.new_age_thaum.core.aspect.Primals;
import io.github.minerguy341.new_age_thaum.core.casting.WandComponent;
import io.github.minerguy341.new_age_thaum.core.casting.WandForm;
import io.github.minerguy341.new_age_thaum.core.casting.WandStats;
import io.github.minerguy341.new_age_thaum.core.casting.WandVis;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Locale;

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
    public void inventoryTick(ItemStack stack, Level level, net.minecraft.world.entity.Entity entity,
            int slotId, boolean isSelected) {
        // Ambient recharge anywhere in a player's inventory (TC-style trickle), once a
        // second. Charging beyond the ambient floor is a manual node right-click (useOn).
        if (level instanceof net.minecraft.server.level.ServerLevel serverLevel
                && entity instanceof net.minecraft.world.entity.player.Player
                && level.getGameTime() % WandRecharge.INTERVAL_TICKS == 0) {
            WandRecharge.tick(serverLevel, entity.blockPosition(), stack);
        }
    }

    @Override
    public net.minecraft.world.InteractionResult useOn(net.minecraft.world.item.context.UseOnContext context) {
        Level level = context.getLevel();
        net.minecraft.core.BlockPos pos = context.getClickedPos();
        // Right-clicking an aura node channels its chunk's vis into the wand. The node's
        // block entity exists on both sides (it syncs), but only the server moves vis.
        if (!(level.getBlockEntity(pos) instanceof AuraNodeBlockEntity node)) {
            return net.minecraft.world.InteractionResult.PASS;
        }
        if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            float moved = WandRecharge.chargeFromNode(serverLevel, pos, context.getItemInHand(), node);
            boolean gained = moved > 0f;
            // Feedback: a chime when vis flows, a dull step when the wand's full or the
            // node's chunk is spent — a silent no-op reads as a broken interaction.
            level.playSound(null, pos,
                    gained ? net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_CHIME
                            : net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_STEP,
                    net.minecraft.sounds.SoundSource.BLOCKS, 0.6f,
                    gained ? 0.9f + level.random.nextFloat() * 0.2f : 0.7f);
        }
        return net.minecraft.world.InteractionResult.sidedSuccess(level.isClientSide);
    }

    /**
     * The implement's display name is built from its materials — a Brass-capped Greatwood
     * rod reads "Brass Capped Greatwood Wand", two distinct caps read "Aetherium and Brass
     * Capped …". Unassembled implements fall back to the plain "Wand"/"Stave" item name.
     */
    @Override
    public Component getName(ItemStack stack) {
        WandComponent component = componentOf(stack);
        if (component == null) {
            return super.getName(stack);
        }
        return Component.translatable(
                "item.new_age_thaum." + form.name().toLowerCase(Locale.ROOT) + ".named",
                capsName(component),
                Component.translatable(materialKey(component.core())));
    }

    /** "Brass" for matched caps, "Aetherium and Brass" for two — ordered stably by id. */
    private static Component capsName(WandComponent component) {
        ResourceLocation a = component.capA();
        ResourceLocation b = component.capB();
        if (a.equals(b)) {
            return Component.translatable(materialKey(a));
        }
        ResourceLocation first = a.compareTo(b) <= 0 ? a : b;
        ResourceLocation second = a.compareTo(b) <= 0 ? b : a;
        return Component.translatable("tooltip.new_age_thaum.wand.caps_two",
                Component.translatable(materialKey(first)), Component.translatable(materialKey(second)));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        WandComponent component = componentOf(stack);
        if (component == null) {
            tooltip.add(Component.translatable("tooltip.new_age_thaum.wand.unassembled").withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        // Core and caps are no longer spelled out here — the item name already carries them.
        WandStats stats = WandStats.compute(component, form);
        float perPrimal = (float) stats.capacity();
        tooltip.add(Component.translatable("tooltip.new_age_thaum.wand.capacity",
                Mth.floor(perPrimal)).withStyle(ChatFormatting.AQUA));
        // Vis stored "at a glance": the total across all six primals over the total the
        // implement can hold. The HUD is the per-primal breakdown; this is the one number.
        WandVis vis = stack.getOrDefault(ModComponents.WAND_VIS.get(), WandVis.EMPTY);
        float stored = 0f;
        for (ResourceLocation primal : Primals.ORDER) {
            stored += vis.get(primal);
        }
        int total = Mth.floor(perPrimal) * Primals.ORDER.size();
        tooltip.add(Component.translatable("tooltip.new_age_thaum.wand.vis_stored",
                Mth.floor(stored), total).withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltip.add(Component.translatable("tooltip.new_age_thaum.wand.discount",
                (int) Math.round(stats.discount() * 100)).withStyle(ChatFormatting.GOLD));
        stats.rechargeAffinity().ifPresent(affinity -> tooltip.add(
                Component.translatable("tooltip.new_age_thaum.wand.affinity", AspectNames.colored(affinity))
                        .withStyle(ChatFormatting.GRAY)));
    }

    private static String materialKey(ResourceLocation id) {
        return "wand_material." + id.getNamespace() + "." + id.getPath();
    }
}
