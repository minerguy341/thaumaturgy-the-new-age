package io.github.minerguy341.new_age_thaum.client;

import dev.architectury.event.events.client.ClientGuiEvent;
import io.github.minerguy341.new_age_thaum.content.CastingImplementItem;
import io.github.minerguy341.new_age_thaum.core.ModComponents;
import io.github.minerguy341.new_age_thaum.core.NewAgeThaumConfig;
import io.github.minerguy341.new_age_thaum.core.aspect.Aspect;
import io.github.minerguy341.new_age_thaum.core.aspect.AspectRegistry;
import io.github.minerguy341.new_age_thaum.core.aspect.Primals;
import io.github.minerguy341.new_age_thaum.core.casting.WandComponent;
import io.github.minerguy341.new_age_thaum.core.casting.WandStats;
import io.github.minerguy341.new_age_thaum.core.casting.WandVis;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

import java.util.Set;

/**
 * The M3 "HUD readout" (PLAN §6): six per-primal vis bars shown while an assembled
 * wand/stave is in either hand. Each row is an aspect-colored swatch, a fill bar out
 * of the wand's capacity, the ambient-recharge floor as a white tick mark (higher on
 * the core's affinity primals), and the stored amount as text. Anchored to the left
 * edge, vertically centered — clear of the hotbar, chat, and effect icons. Colors come
 * from the synced aspect registry mirror; stored vis reaches the client through the
 * WAND_VIS component's own network sync, so the HUD needs no extra packets.
 */
public final class WandVisHud {
    private static final int ROW_HEIGHT = 11;
    private static final int BAR_WIDTH = 50;
    private static final int BAR_HEIGHT = 8;
    private static final int SWATCH = 6;

    private WandVisHud() {
    }

    public static void register() {
        ClientGuiEvent.RENDER_HUD.register(WandVisHud::render);
    }

    private static void render(GuiGraphics graphics, DeltaTracker tickDelta) {
        if (!NewAgeThaumConfig.wandHudEnabled) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.options.hideGui) {
            return;
        }
        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof CastingImplementItem)) {
            stack = player.getOffhandItem();
        }
        if (!(stack.getItem() instanceof CastingImplementItem implement)) {
            return;
        }
        WandComponent component = CastingImplementItem.componentOf(stack);
        if (component == null) {
            return; // unassembled implements have nothing to read
        }
        WandStats stats = WandStats.compute(component, implement.form());
        float capacity = (float) stats.capacity();
        if (capacity <= 0f) {
            return;
        }
        Set<ResourceLocation> affinityPrimals =
                stats.rechargeAffinity().map(Primals::primalsOf).orElse(Set.of());
        WandVis vis = stack.getOrDefault(ModComponents.WAND_VIS.get(), WandVis.EMPTY);

        int x = 6;
        int y = (graphics.guiHeight() - Primals.ORDER.size() * ROW_HEIGHT) / 2;
        for (ResourceLocation primal : Primals.ORDER) {
            int color = 0xFF000000 | AspectRegistry.get(primal).map(Aspect::color).orElse(0x888888);
            float stored = vis.get(primal);
            float frac = Mth.clamp(stored / capacity, 0f, 1f);

            int barX = x + SWATCH + 3;
            graphics.fill(x, y + 1, x + SWATCH, y + 1 + SWATCH, color);
            graphics.fill(barX - 1, y, barX + BAR_WIDTH + 1, y + BAR_HEIGHT, 0xA0000000);
            if (frac > 0f) {
                graphics.fill(barX, y + 1, barX + Math.max(1, (int) (BAR_WIDTH * frac)),
                        y + BAR_HEIGHT - 1, color);
            }
            // Ambient floor tick: where the automatic trickle stops (a node right-click
            // fills beyond it). Read from the server-synced mirror so the mark is correct
            // on multiplayer, not from this client's own config file.
            float floor = Mth.clamp(affinityPrimals.contains(primal)
                    ? ClientCastingConfig.affinityFloor() : ClientCastingConfig.ambientFloor(), 0f, 1f);
            int floorX = barX + (int) (BAR_WIDTH * floor);
            graphics.fill(floorX, y - 1, floorX + 1, y + BAR_HEIGHT + 1, 0xC0FFFFFF);

            graphics.drawString(minecraft.font, String.valueOf(Mth.floor(stored)),
                    barX + BAR_WIDTH + 5, y, 0xFFFFFFFF, true);
            y += ROW_HEIGHT;
        }
    }
}
