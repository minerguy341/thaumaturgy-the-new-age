package io.github.minerguy341.new_age_thaum.client;

import io.github.minerguy341.new_age_thaum.content.ArcaneWorktableMenu;
import io.github.minerguy341.new_age_thaum.core.ModRegistries;
import io.github.minerguy341.new_age_thaum.core.aspect.AspectNames;
import io.github.minerguy341.new_age_thaum.core.aspect.Primals;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Arcane Worktable screen — "augment don't replace" in TC4's Arcane Worktable idiom: a centred
 * 3×3 grid + wand slot + result, with the six PRIMAL aspects displayed as a hexagon of glyphs
 * (our own primal symbols) ENCIRCLING the grid. Each primal the recipe needs lights up with its
 * cost — teal if the wand can pay, red if short; primals the recipe doesn't use are dimmed. The
 * panel is the mod's dark-purple house style; slots are stamped from a real vanilla slot cell.
 */
public class ArcaneWorktableScreen extends AbstractContainerScreen<ArcaneWorktableMenu> {
    private static final ResourceLocation VANILLA =
            ResourceLocation.withDefaultNamespace("textures/gui/container/crafting_table.png");
    private static final int SLOT_CELL_U = 29, SLOT_CELL_V = 16;

    private static final int PANEL = 0xF0100A18;
    private static final int CHROME = 0xFF241B33;
    private static final int TEAL_TEXT = 0x7FE8D8;
    private static final int WARN_TEXT = 0xE08A8A;
    private static final int DIM_GLYPH = 0x90100A18;   // overlay to fade an unused primal glyph
    private static final int RING_RADIUS = 44;
    // Flat-top hexagon vertex angles (deg), index-aligned with Primals.LIST.
    private static final int[] RING_ANGLES = {240, 300, 0, 60, 120, 180};

    private ItemStack ghostWand = ItemStack.EMPTY;

    public ArcaneWorktableScreen(ArcaneWorktableMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 194;
        this.imageHeight = 208;
        this.inventoryLabelY = ArcaneWorktableMenu.INV_Y - 11;
    }

    /** Screen-space top-left of primal ring-index {@code i}'s 16×16 glyph (flat-top hexagon). */
    private int[] glyphPos(int i) {
        double angle = Math.toRadians(RING_ANGLES[i]);
        int cx = leftPos + ArcaneWorktableMenu.GRID_CX;
        int cy = topPos + ArcaneWorktableMenu.GRID_CY;
        int gx = cx + (int) Math.round(Math.cos(angle) * RING_RADIUS) - 8;
        int gy = cy + (int) Math.round(Math.sin(angle) * RING_RADIUS) - 8;
        return new int[]{gx, gy};
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        g.fill(x, y, x + imageWidth, y + imageHeight, PANEL);
        g.fill(x, y, x + imageWidth, y + 14, CHROME);
        for (Slot slot : menu.slots) {
            g.blit(VANILLA, x + slot.x - 1, y + slot.y - 1, SLOT_CELL_U, SLOT_CELL_V, 18, 18);
        }
        drawPrimalRing(g);

        if (!menu.hasWand()) {
            if (ghostWand.isEmpty()) {
                ghostWand = new ItemStack(ModRegistries.WAND.get());
            }
            int wx = x + ArcaneWorktableMenu.WAND_X;
            int wy = y + ArcaneWorktableMenu.WAND_Y;
            g.renderFakeItem(ghostWand, wx, wy);
            g.fill(wx, wy, wx + 16, wy + 16, DIM_GLYPH);
        }
    }

    /** The six primal glyphs ringing the grid, each with its cost, coloured by affordability. */
    private void drawPrimalRing(GuiGraphics g) {
        int status = menu.status();
        boolean arcane = status == ArcaneWorktableMenu.STATUS_ARCANE_READY
                || status == ArcaneWorktableMenu.STATUS_INSUFFICIENT
                || status == ArcaneWorktableMenu.STATUS_NEED_WAND;
        for (int i = 0; i < Primals.ORDER.size(); i++) {
            int[] p = glyphPos(i);
            g.blit(Primals.glyph(i), p[0], p[1], 0, 0, 16, 16, 16, 16);
            int cost = menu.costOf(i);
            if (!arcane || cost <= 0) {
                g.fill(p[0], p[1], p[0] + 16, p[1] + 16, DIM_GLYPH); // fade primals not in play
                continue;
            }
            boolean paid = menu.availOf(i) >= cost || status == ArcaneWorktableMenu.STATUS_ARCANE_READY;
            g.drawString(font, Integer.toString(cost), p[0] + 5, p[1] + 16,
                    paid ? TEAL_TEXT : WARN_TEXT, false);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        super.renderLabels(g, mouseX, mouseY); // title + "Inventory"
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick); // hovered-slot item tooltips
        // Per-primal tooltip on glyph hover: "Ventus  need X / have Y".
        for (int i = 0; i < Primals.ORDER.size(); i++) {
            int[] p = glyphPos(i);
            if (mouseX >= p[0] && mouseX < p[0] + 16 && mouseY >= p[1] && mouseY < p[1] + 16) {
                List<Component> lines = new ArrayList<>();
                lines.add(AspectNames.colored(Primals.ORDER.get(i)));
                int cost = menu.costOf(i);
                if (cost > 0) {
                    lines.add(Component.translatable("screen.new_age_thaum.vis_need", cost, menu.availOf(i))
                            .withStyle(menu.availOf(i) >= cost ? ChatFormatting.AQUA : ChatFormatting.RED));
                } else {
                    lines.add(Component.translatable("screen.new_age_thaum.vis_unused")
                            .withStyle(ChatFormatting.DARK_GRAY));
                }
                g.renderComponentTooltip(font, lines, mouseX, mouseY);
                return;
            }
        }
        // "Why is this locked" tooltip on the empty result slot.
        Slot result = menu.getSlot(ArcaneWorktableMenu.RESULT_SLOT);
        if (!result.hasItem() && isHovering(result.x, result.y, 16, 16, mouseX, mouseY)) {
            if (menu.status() == ArcaneWorktableMenu.STATUS_NEED_WAND) {
                g.renderTooltip(font, Component.translatable("screen.new_age_thaum.need_wand"), mouseX, mouseY);
            } else if (menu.status() == ArcaneWorktableMenu.STATUS_INSUFFICIENT) {
                g.renderTooltip(font, Component.translatable("screen.new_age_thaum.insufficient_primal"), mouseX, mouseY);
            }
        }
    }
}
