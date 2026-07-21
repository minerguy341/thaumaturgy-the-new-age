package io.github.minerguy341.new_age_thaum.client;

import dev.architectury.event.events.client.ClientGuiEvent;
import io.github.minerguy341.new_age_thaum.content.AuraNodeBlockEntity;
import io.github.minerguy341.new_age_thaum.core.ModRegistries;
import io.github.minerguy341.new_age_thaum.core.aspect.AspectNames;
import io.github.minerguy341.new_age_thaum.core.aura.AuraField;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * The Aetherlens readout: a compact panel shown while the player holds the lens and looks
 * at an aura node, "reading" the node the way the Thaumometer does. Mirrors the right-click
 * {@code reportNode} chat message as a live HUD — aspect (with a color swatch), temperament,
 * strength, and the node's own-chunk vis — from the state the block entity already syncs
 * (aspect/personality/size and the vis snapshot whose centre cell is this chunk). Flux is
 * not client-synced, so it stays on the right-click report; the tainted/pure temperament is
 * the visible tell here.
 */
public final class AetherlensHud {
    // Dark-purple UI palette (shared with the mod's screens; see minecraft-ui-design skill).
    private static final int PANEL = 0xF0100A18;
    private static final int CHROME = 0xFF241B33;
    private static final int WELL = 0xFF0B0713;
    private static final int TEXT = 0xFFE8D9FF;
    private static final int TEXT_DIM = 0xFF9A8CBF;

    private static final int PAD = 5;
    private static final int SWATCH = 7;
    private static final int BAR_W = 72;
    private static final int BAR_H = 5;
    private static final int TOP_MARGIN = 8;

    private AetherlensHud() {
    }

    public static void register() {
        ClientGuiEvent.RENDER_HUD.register(AetherlensHud::render);
    }

    private static void render(GuiGraphics graphics, DeltaTracker tickDelta) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.options.hideGui || !holdingLens(player)) {
            return;
        }
        HitResult hit = minecraft.hitResult;
        Level level = minecraft.level;
        if (level == null || hit == null || hit.getType() != HitResult.Type.BLOCK
                || !(hit instanceof BlockHitResult blockHit)) {
            return;
        }
        if (!(level.getBlockEntity(blockHit.getBlockPos()) instanceof AuraNodeBlockEntity node)) {
            return;
        }
        Font font = minecraft.font;
        if (node.aspect() == null || node.personality() == null) {
            panel(graphics, font, Component.translatable("message.new_age_thaum.node.dormant")
                    .withStyle(ChatFormatting.GRAY), null, null, -1f, 0);
            return;
        }
        Component title = AspectNames.colored(node.aspect());
        Component detail = Component.translatable(node.personality().translationKey())
                .withStyle(ChatFormatting.AQUA)
                .append(Component.literal("  ·  ").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.translatable("message.new_age_thaum.node.strength",
                        String.format("%.1f", node.size())).withStyle(ChatFormatting.GRAY));
        int half = AuraNodeBlockEntity.GRID / 2;
        float vis = node.auraSnapshot()[half * AuraNodeBlockEntity.GRID + half];
        float frac = Mth.clamp(vis / AuraField.CHUNK_CAP, 0f, 1f);
        int aspectColor = 0xFF000000 | SphereColors.colorOf(node.aspect());
        panel(graphics, font, title, detail, aspectColor, frac,
                Math.round(vis));
    }

    private static boolean holdingLens(LocalPlayer player) {
        ItemStack main = player.getMainHandItem();
        ItemStack off = player.getOffhandItem();
        return main.is(ModRegistries.AETHERLENS.get()) || off.is(ModRegistries.AETHERLENS.get());
    }

    /**
     * Draw the readout panel, top-centre. {@code detail}/{@code swatchColor} may be null and
     * {@code frac < 0} to render just a single title line (the dormant-node hint).
     */
    private static void panel(GuiGraphics graphics, Font font, Component title, Component detail,
                              Integer swatchColor, float frac, int visAmount) {
        boolean hasBar = frac >= 0f;
        int cap = (int) AuraField.CHUNK_CAP;
        Component visText = hasBar
                ? Component.translatable("hud.new_age_thaum.node.vis", visAmount, cap)
                : null;

        int titleX = swatchColor != null ? SWATCH + 4 : 0;
        int titleW = titleX + font.width(title);
        int contentW = titleW;
        if (detail != null) {
            contentW = Math.max(contentW, font.width(detail));
        }
        if (hasBar) {
            contentW = Math.max(contentW, BAR_W + 6 + font.width(visText));
        }

        int panelW = contentW + PAD * 2;
        int rows = 1 + (detail != null ? 1 : 0) + (hasBar ? 1 : 0);
        int panelH = PAD * 2 + rows * 9 + (rows - 1) * 2;
        int x = (graphics.guiWidth() - panelW) / 2;
        int y = TOP_MARGIN;

        graphics.fill(x, y, x + panelW, y + panelH, PANEL);
        graphics.fill(x, y, x + panelW, y + PAD + 9, CHROME);   // title strip

        int cx = x + PAD;
        int cy = y + PAD;
        if (swatchColor != null) {
            graphics.fill(cx - 1, cy - 1, cx + SWATCH + 1, cy + SWATCH + 1, WELL);
            graphics.fill(cx, cy, cx + SWATCH, cy + SWATCH, swatchColor);
        }
        graphics.drawString(font, title, cx + titleX, cy, TEXT, true);
        cy += 11;
        if (detail != null) {
            graphics.drawString(font, detail, x + PAD, cy, TEXT_DIM, true);
            cy += 11;
        }
        if (hasBar) {
            int barX = x + PAD;
            graphics.fill(barX - 1, cy, barX + BAR_W + 1, cy + BAR_H + 2, WELL);
            int fill = Math.max(1, (int) (BAR_W * frac));
            graphics.fill(barX, cy + 1, barX + fill, cy + 1 + BAR_H, swatchColor);
            graphics.drawString(font, visText, barX + BAR_W + 6, cy, TEXT_DIM, true);
        }
    }
}
